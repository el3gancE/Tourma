const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const fields = {
  targetTeamCount: {value: '128'},
  quickSelectNum: {value: '4', max: 64},
  partnerSelectModal: {style: {}}
};
const window = {targetTeamCount: 4};
const document = {
  getElementById(id) {return fields[id] || null;},
  addEventListener() {}
};
vm.runInNewContext(fs.readFileSync('web/js/rolling/rolling-tournament-teams.js', 'utf8'), {window, document});
window.calculateAndSyncModalStandings = () => {};
window.applyPartnerExclusions = () => {};
window.openPartnerSelectModal();
assert.equal(fields.quickSelectNum.value, 128, 'Use the copied source count even when the initial global or pool limit differs');
assert.equal(fields.partnerSelectModal.style.display, 'flex');
fields.targetTeamCount.value = '32';
window.openPartnerSelectModal();
assert.equal(fields.quickSelectNum.value, 32, 'Switching copied sources refreshes the count');
fields.targetTeamCount.value = '';
fields.quickSelectNum.value = '12';
window.openPartnerSelectModal();
assert.equal(fields.quickSelectNum.value, '12', 'Unlinking preserves manual choice instead of restoring stale copy state');
delete fields.targetTeamCount;
window.targetTeamCount = 16;
window.openPartnerSelectModal();
assert.equal(fields.quickSelectNum.value, 16, 'Support the legacy page without the embedded editor');
console.log('PASS: Partner popup follows copied source count, source changes, unlink and legacy state');
