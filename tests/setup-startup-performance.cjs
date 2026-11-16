const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
let ready, storageChanged, calculations = 0, moved = 0, fail = false;
let requests = 0, alerts = 0, installed = false;
const modal = {style:{}};
const countInput = {value:''};
const item = {
  getAttribute(name) { return name === 'data-team-name' ? 'Alpha' : ''; },
  setAttribute(){}, querySelector(){ return null; }
};
const list = {querySelectorAll(){return [item];}, appendChild(){moved++;}};
const window = {
  location:{search:'?id=qa',href:'http://localhost/Tourma/rolling/tournament-teams?id=qa'},
  currentSubTourneyId:'qa', seriesPartners:[{name:'Alpha'}], seriesSubTournaments:[],
  seriesPhaseSize:10, serverTourneyPoints:[], serverTourneyParticipation:[],
  addEventListener(type, fn) { if(type === 'storage') storageChanged = fn; },
  TourmaRollingStandingsEngine:{calculateSeriesStandings(){
    calculations++;
    return fail ? null : {rankMap:{alpha:{rank:1}},teamDataArray:[{name:'Alpha',rank:1,totalPts:10}]};
  }}
};
const payload = {seriesSubTournaments:[],seriesPartners:window.seriesPartners,serverTourneyPoints:[],
  serverTourneyParticipation:[],seriesPhaseSize:10,seriesStandingsRankMap:{}};
const host = {replaceChildren(){installed=true;},querySelectorAll(){return [];}};
const document = {
  createElement(){return {content:{querySelector(){return {textContent:JSON.stringify(payload),remove(){}};}}};},
  addEventListener(type, fn){if(type === 'DOMContentLoaded') ready = fn;},
  getElementById(id){return ({partnerPickerHost:host, partnerModalCheckboxList:installed ? list : null, partnerSelectModal:installed ? modal : null,
    quickSelectNum:countInput, targetTeamCount:{value:'4'}})[id] || null;},
  querySelector(){return null;}, querySelectorAll(){return [];}
};
vm.runInNewContext(fs.readFileSync('web/js/rolling/rolling-tournament-teams.js','utf8'), {
  window,document,localStorage:{getItem(){return null;},setItem(){}},URLSearchParams,URL,Set,alert(){alerts++;},
  async fetch(url){
    assert.equal(url.searchParams.get("id"),"qa");
    assert.equal(url.searchParams.get("view"),"partner-picker");
    requests++;
    return {ok:requests > 1,text:async()=>"<div></div>"};
  }
});
// Test the actual startup callback while isolating unrelated seed/storage controls.
window.initSubtourneyDragAndDrop = function(){};
window.initHideSeedConfig = function(){};
ready();
assert.equal(calculations,0,'Opening setup must not calculate Partner standings');
async function verifyPartnerPicker() {
  assert.equal(requests,0,'Opening setup must not fetch Partner data');
  await window.openPartnerSelectModal();
  assert.equal(alerts,1);
  assert.equal(installed,false);
  await Promise.all([window.openPartnerSelectModal(), window.openPartnerSelectModal()]);
  assert.equal(requests,2,'Concurrent retries must share one request');
  assert.equal(calculations,1);
  assert.equal(modal.style.display,'flex');
  assert.equal(countInput.value,4,'Copy target still initializes quick selection');
  window.closePartnerSelectModal();
  await window.openPartnerSelectModal();
  assert.equal(requests,2,"Reopening must reuse the Partner fragment");
  assert.equal(calculations,1,'Reopening must reuse the prepared snapshot');
  assert.equal(moved,1,'Reopening must not reorder every Partner DOM row again');
  window.serverTourneyPoints = [{}];
  window.calculateAndSyncModalStandings();
  assert.equal(calculations,2,'Replacing the server snapshot invalidates preparation');
  storageChanged();
  fail = true;
  window.calculateAndSyncModalStandings();
  fail = false;
  window.calculateAndSyncModalStandings();
  assert.equal(calculations,4,'Failed calculation must not poison the cached result');
  console.log('PASS: setup defers Partner work, reuses standings, invalidates snapshots, retries failures');
}
verifyPartnerPicker().catch(error=>{console.error(error);process.exitCode=1;});
