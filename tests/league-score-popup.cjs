const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
class Element {
    constructor() { this.dataset = {}; this.listeners = {}; this.style = {}; this.classList = {add(){}, remove(){}, toggle(){}}; this.scrollTop = 0; this.scrollLeft = 0; }
    setAttribute() {}
    addEventListener(type, fn) { (this.listeners[type] ||= []).push(fn); }
    querySelector() { return null; }
    appendChild() {}
    click() { (this.listeners.click || []).forEach(fn => fn({target: this})); }
}
const events = {};
const root = new Element(); root.scrollTop = 420; root.scrollLeft = 80;
const container = new Element(); container.parentElement = root;
Object.defineProperty(container, 'innerHTML', {set() { root.scrollTop = 0; root.scrollLeft = 0; }});
const document = {createElement: () => new Element(), querySelector: () => null,
    getElementById: id => id === 'rrFixturesContainer' ? container : null,
    addEventListener(type, fn) { (events[type] ||= []).push(fn); }};
const window = {location: {search: '?id=test'}};
const ctx = vm.createContext({window, document, URLSearchParams, localStorage: {getItem(){return null;}}, console, Set, Promise});
function load(name) { vm.runInContext(fs.readFileSync('web/js/' + name, 'utf8'), ctx); }
load('match-card.js');
let custom = 0, generic = 0;
window.TourmaScoreModal = {open(){generic++;}, isLocked(){return false;}};
const match = {matchId: 'm1', team1: {name: 'A', seed: 1}, team2: {name: 'B', seed: 2}, roundNumber: 1};
window.TourmaMatchCard.createCardElement({...match, onClick(){custom++;}}).click();
assert.equal(custom, 1); assert.equal(generic, 0);
window.TourmaMatchCard.createCardElement(match).click(); assert.equal(generic, 1);
load('round-robin.js');
const rr = window.TourmaRoundRobin;
rr.rounds = [{roundNumber: 1, matches: [match]}];
rr.renderFixtures(); assert.equal(root.scrollTop, 420); assert.equal(root.scrollLeft, 80);
let saves = 0; rr.saveMatchScore = () => {saves++;}; rr.setupListeners();
for (const fn of events.tourmaMatchUpdated) fn({detail: {matchId: 'm1', handledByCallback: true}});
assert.equal(saves, 0);
for (const fn of events.tourmaMatchUpdated) fn({detail: {matchId: 'm1', team1Score: 2, team2Score: 1}});
assert.equal(saves, 1);
console.log('PASS: custom popup routing, generic fallback, scroll restoration, single RR save ownership');

// GS follows RR: one score API call, local update only on success, no reload.
(async function () {
    document.readyState = 'loading';
    window.groupTournamentId = 'gs-test';
    window.TourmaContextPath = '/Tourma';
    window.location.search = '?id=gs-test&stage=2';
    let requests = [], alerts = [];
    ctx.alert = message => alerts.push(message);
    let response = {status: 'success'};
    ctx.fetch = async (url, options) => {
        requests.push({url, params: new URLSearchParams(options.body)});
        return {json: async () => response};
    };
    load('group-stage.js');
    const gs = window.TourmaGroupStage;
    const m = {matchId: 'gs-m1', groupId: 'group-a', team1: {name:'A',score:''}, team2: {name:'B',score:''}};
    let callback;
    window.TourmaScoreModal.open = (data, save) => { callback = save; assert.equal(data.allowDraw, true); };
    gs.openEditScoreModal(m, 'A');
    await callback({team1Score: 2, team2Score: 2, winner:'draw'});
    assert.equal(requests.length, 1);
    assert.equal(requests[0].url, '/Tourma/api/match-update');
    assert.equal(requests[0].params.get('action'), 'updateMatch');
    assert.equal(requests[0].params.get('stage'), '2');
    assert.equal(requests[0].params.get('groupId'), 'group-a');
    assert.equal(requests[0].params.get('winner'), '');
    assert.equal(m.team1.score, '2'); assert.equal(m.status, 'COMPLETED');
    response = {status:'error',message:'Locked'};
    await gs.saveMatchScore(m, 5, 1, 'team1');
    assert.equal(m.team1.score, '2'); assert.equal(alerts.length, 1);
    await gs.waitForPendingSaves();
    console.log('PASS: GS uses RR score API, supports draws/stage/group, updates only after success');
})().catch(error => {console.error(error); process.exitCode = 1;});
const css = fs.readFileSync('web/css/popup.css', 'utf8');
assert.match(css, /\.tourma-modal-backdrop,\.add-team-modal-overlay,\.stage-end-modal-overlay\s*\{[^}]*position:fixed/);
assert.doesNotMatch(css, /\.tourma-modal-backdrop\.show[^{}]*\{[^}]*position:fixed/);
const gsJsp = fs.readFileSync('web/common/group-stage.jsp', 'utf8');
assert.ok(gsJsp.indexOf('/common/component/popup.jsp') > gsJsp.indexOf('</main></div>'));
assert.equal((gsJsp.match(/\/common\/component\/popup.jsp/g) || []).length, 1);
console.log('PASS: closing popup stays fixed during fade; GS popup is outside workspace');
