const assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const window={};vm.runInNewContext(fs.readFileSync('web/js/rolling/rolling-standings-engine.js','utf8'),{window,console,localStorage:{getItem(){return null;}}});
const engine=window.TourmaRollingStandingsEngine;
const options={seriesId:'test',phaseSize:3,partners:[{id:'a',name:'Alpha'},{id:'b',name:'Beta'},{id:'c',name:'Gamma'}],subTourneys:[],serverTourneyPoints:[],serverTourneyParticipation:[],serverTourneyAchievements:[]};
for(let i=0;i<8;i++){
 options.subTourneys.push({id:'t'+i,name:'Event '+i,index:i+1,format:'SINGLE_ELIMINATION',status:'COMPLETED',tierName:'A',pointsConfig:{}});
 options.serverTourneyPoints.push({alpha:i%2?20:10,beta:i%2?10:20,gamma:0});
 options.serverTourneyParticipation.push({alpha:true,beta:true,gamma:i%2===0});
 options.serverTourneyAchievements.push({alpha:i%2?'Champion':'Runner-Up',beta:i%2?'Runner-Up':'Champion'});
}
const all=engine.calculateSeriesStandings(options);
for(const name of ['Alpha','Beta','Gamma']){
 assert.equal(JSON.stringify(engine.getTeamProfile(name,options)),JSON.stringify(all.teamProfileStats[name.toLowerCase()]));
 const scoped=engine.calculateSeriesStandings({...options,profileTeamName:name});
 assert.deepEqual(Object.keys(scoped.teamProfileStats),[name.toLowerCase()]);
 assert.equal(JSON.stringify(scoped.teamDataArray),JSON.stringify(all.teamDataArray));
 assert.equal(JSON.stringify(scoped.phaseWinnersList),JSON.stringify(all.phaseWinnersList));
}
console.log('PASS: scoped histories match full calculation; standings, phase badges, skipped events and zero-point teams preserved');
