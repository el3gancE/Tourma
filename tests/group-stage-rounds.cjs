const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const window = {};
vm.runInNewContext(fs.readFileSync('web/js/group-stage.js', 'utf8'), {
    window, document: {readyState:'loading', addEventListener(){}}, Set, URLSearchParams
});
for (const count of [2, 3, 4, 5, 6, 7, 8]) for (const legs of [1, 2]) {
    const teams = Array.from({length:count}, (_, i) => ({id:String(i),name:'Team '+i}));
    const matches = [];
    for (let leg=0;leg<legs;leg++) for (let i=0;i<count;i++) for(let j=i+1;j<count;j++) {
        matches.push({matchId:String(matches.length), team1:{...teams[i],score:'2'},team2:{...teams[j],score:'1'}});
    }
    const original = JSON.stringify(matches);
    const rounds = window.TourmaGroupStage.buildGroupRounds(teams,matches);
    assert.equal(rounds.length, (count%2 ? count : count-1)*legs);
    const seen = new Set();
    for (const round of rounds) {
        const playing = new Set();
        assert.equal(round.matches.length, Math.floor(count/2));
        for (const {match} of round.matches) {
            assert.ok(!seen.has(match)); seen.add(match);
            for (const team of [match.team1,match.team2]) {assert.ok(!playing.has(team.id)); playing.add(team.id);}
        }
        if (count%2) {assert.ok(round.bye);assert.ok(!playing.has(round.bye.id));}
        else assert.equal(round.bye,null);
    }
    assert.equal(seen.size,matches.length);
    assert.equal(JSON.stringify(matches),original);
}
console.log('PASS: GS rounds for 2-8 teams, one/two legs, no team twice per round, byes, all matches and scores preserved');
