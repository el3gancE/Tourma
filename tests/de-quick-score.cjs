const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const requests = [];
const alerts = [];
const cards = new Map();
let locked = false;
let fullRenders = 0;
let connectorRedraws = 0;
const events = { addEventListener() {} };
const window = {
    ...events,
    TourmaScoreModal: { isLocked: () => locked },
    TourmaBracketCard: { createNodeElement: match => makeCard(match.matchId) }
};
const document = {
    ...events,
    getElementById: () => null,
    querySelectorAll: selector => selector.includes('bracket-node-card') ? [...cards.values()] : []
};
const context = vm.createContext({
    window, document, URLSearchParams, alert: message => alerts.push(message),
    console: { error() {} },
    fetch: (url, options) => new Promise(resolve => {
        requests.push({ url, payload: new URLSearchParams(options.body), resolve });
    })
});
for (const name of ['double-elimination-algorithm', 'double-elimination', 'round-control-helper']) {
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../web/js', name + '.js'), 'utf8'), context);
}
const engine = window.TourmaDoubleElimination;
engine.tournamentId = 'DE_TEST';
engine.isQuickMode = true;
engine.render = engine.renderListView = engine.renderUpperBracket = engine.renderLowerBracket = () => fullRenders++;
engine.drawUpperSvgConnectors = engine.drawLowerSvgConnectors = () => connectorRedraws++;

function makeCard(id) {
    return {
        dataset: { matchId: id },
        classList: { contains: () => false, add() {} },
        addEventListener() {},
        replaceWith: card => cards.set(id, card)
    };
}

function match(id, roundNumber, bracketType, left, right, links = {}) {
    return { matchId: id, roundNumber, bracketType, status: 'READY',
        team1: { name: left, score: '' }, team2: { name: right, score: '' }, ...links };
}

function snapshot() {
    return JSON.parse(JSON.stringify([...new Set(Object.values(engine.matchesMap))]));
}

function reply(request, data, ok = true) {
    request.resolve({ ok, json: async () => data });
}

const flush = () => new Promise(resolve => setImmediate(resolve));

(async () => {
    engine.hydrateBracketModel([
        match('M_UB_1', 1, 'WINNER_BRACKET', 'A', 'B',
            { matchNumber: 1, nextMatchId: 'M_UB_3', loserNextMatchId: 'M_LB_1' }),
        match('M_UB_2', 1, 'WINNER_BRACKET', 'C', 'D',
            { matchNumber: 2, nextMatchId: 'M_UB_3', nextMatchSlot: 2, loserNextMatchId: 'M_LB_1', loserNextSlot: 2 }),
        match('M_UB_3', 2, 'WINNER_BRACKET', '', '', { matchNumber: 3 }),
        match('M_LB_1', 1, 'LOSER_BRACKET', '', '', { matchNumber: 4 }),
        match('M_UB_4', 1, 'WINNER_BRACKET', 'E', 'F', { matchNumber: 5 })
    ]);
    for (const model of snapshot()) cards.set(model.matchId, makeCard(model.matchId));
    const untouched = cards.get('M_UB_4');
    const firstCard = cards.get('M_UB_1');
    const loserCard = cards.get('M_LB_1');
    window.TourmaRoundControls.handleQuickWinner(engine, 'M_UB_1', 1, 9);
    window.TourmaRoundControls.handleQuickWinner(engine, 'M_UB_2', 2, 9);
    await flush();
    assert.equal(requests.length, 1);
    assert.equal(engine.findMatch('M_UB_3').team1.name, 'A');
    assert.equal(engine.findMatch('M_LB_1').team1.name, 'B');
    assert.notEqual(cards.get('M_UB_1'), firstCard);
    assert.notEqual(cards.get('M_LB_1'), loserCard);
    const confirmedCard = cards.get('M_UB_1');
    reply(requests[0], { status: 'success', matchesData: snapshot() });
    await flush();
    assert.equal(requests.length, 2);
    assert.equal(requests[0].payload.get('matchId'), 'M_UB_1');
    assert.equal(requests[1].payload.get('matchId'), 'M_UB_2');
    assert.equal(cards.get('M_UB_1'), confirmedCard);
    assert.equal(engine.findMatch('M_LB_1').team2.name, 'C');
    reply(requests[1], { status: 'success', matchesData: snapshot() });
    await flush();

    // Changing a settled result updates both destinations, then rolls back on rejection.
    window.TourmaRoundControls.handleQuickWinner(engine, 'M_UB_1', 2, 9);
    await flush();
    assert.equal(engine.findMatch('M_UB_3').team1.name, 'B');
    assert.equal(engine.findMatch('M_LB_1').team1.name, 'A');
    reply(requests[2], { status: 'error', message: 'Rejected in test' }, false);
    await flush();
    assert.equal(engine.findMatch('M_UB_1').winnerId, 'team1');
    assert.equal(engine.findMatch('M_UB_3').team1.name, 'A');
    assert.equal(engine.findMatch('M_LB_1').team1.name, 'B');
    assert.equal(engine.findMatch('M_UB_2').winnerId, 'team2');
    assert.equal(alerts.length, 1);
    assert.equal(fullRenders, 0);
    assert.equal(connectorRedraws, 0);
    assert.equal(cards.get('M_UB_4'), untouched);

    locked = true;
    engine.saveMatchScore('M_UB_4', 9, 0, null, null, 'team1');
    await flush();
    assert.equal(requests.length, 3);
    console.log('DE Quick updates, save order, rollback and stage lock passed.');
})().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
