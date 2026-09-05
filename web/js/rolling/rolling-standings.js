/**
 * TOURMA - ROLLING WINDOW SERIES STANDINGS SCRIPT (rolling-standings.js)
 * Synchronizes Server + LocalStorage sub-tournament bracket placements
 * Supports Milestone Dropdown (historical & latest standings), Manual Sync, and Rank Fluctuations
 */

(function () {
  'use strict';

  function normalizeStr(str) {
    if (!str) return '';
    try {
      return String(str).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
    } catch (e) {
      return String(str).toLowerCase().trim();
    }
  }

  function extractName(obj) {
    if (!obj) return null;
    if (typeof obj === 'string') {
      var s = obj.trim().replace(/^\[\d+\]\s*/, '').replace(/^\(\d+\)\s*/, '').replace(/^\d+\.\s*/, '').replace(/^#\d+\s*/, '');
      if (s && s !== 'BYE' && s !== 'TBD' && !s.startsWith('W #') && !s.startsWith('L #') && !s.startsWith('Winner ') && !s.startsWith('Loser ')) {
        return s;
      }
      return null;
    }
    if (typeof obj === 'object') {
      var name = obj.name || obj.rawName || obj.normalizedName || (obj.team && (typeof obj.team === 'object' ? (obj.team.name || obj.team.rawName) : obj.team));
      if (name && typeof name === 'string') {
        var s = name.trim().replace(/^\[\d+\]\s*/, '').replace(/^\(\d+\)\s*/, '').replace(/^\d+\.\s*/, '').replace(/^#\d+\s*/, '');
        if (s && s !== 'BYE' && s !== 'TBD' && !s.startsWith('W #') && !s.startsWith('L #') && !s.startsWith('Winner ') && !s.startsWith('Loser ')) {
          return s;
        }
      }
    }
    return null;
  }

  function isTeamSelf(name, targetKey) {
    if (!name || !targetKey) return false;
    var n = normalizeStr(name);
    var t = normalizeStr(targetKey);
    if (n === t) return true;
    var cTarget = t.replace(/[^a-z0-9]/g, '');
    var c = n.replace(/[^a-z0-9]/g, '');
    if (c && cTarget && c === cTarget) return true;

    // Strict number check: if either or both have digits, digits MUST match exactly
    var d1 = n.replace(/[^0-9]/g, '');
    var d2 = t.replace(/[^0-9]/g, '');
    if (d1 !== d2) {
      if (d1 !== '' || d2 !== '') return false;
    }

    // Substring match only for long strings with close length difference
    if (c.length >= 6 && cTarget.length >= 6) {
      if (Math.abs(c.length - cTarget.length) <= 3) {
        if (c.indexOf(cTarget) !== -1 || cTarget.indexOf(c) !== -1) {
          return true;
        }
      }
    }
    return false;
  }

  function resolveWinnerAndLoser(match) {
    if (!match) return { winner: null, loser: null };
    var t1Name = extractName(match.team1) || (match.team1Name ? extractName(match.team1Name) : null);
    var t2Name = extractName(match.team2) || (match.team2Name ? extractName(match.team2Name) : null);

    var wId = null;
    if (match.winnerId !== undefined && match.winnerId !== null && match.winnerId !== '') {
      wId = match.winnerId;
    } else if (match.winnerTeamId !== undefined && match.winnerTeamId !== null && match.winnerTeamId !== '') {
      wId = match.winnerTeamId;
    } else if (match.winner !== undefined && match.winner !== null && match.winner !== '') {
      wId = match.winner;
    }

    if (wId && typeof wId === 'object') {
      wId = extractName(wId) || wId.id || wId.name || null;
    }
    if (wId) wId = String(wId).trim();

    var s1 = NaN;
    if (match.team1 && match.team1.score !== undefined && match.team1.score !== null && match.team1.score !== '') {
      s1 = parseInt(match.team1.score, 10);
    } else if (match.team1Score !== undefined && match.team1Score !== null && match.team1Score !== '') {
      s1 = parseInt(match.team1Score, 10);
    } else if (match.score1 !== undefined && match.score1 !== null && match.score1 !== '') {
      s1 = parseInt(match.score1, 10);
    }

    var s2 = NaN;
    if (match.team2 && match.team2.score !== undefined && match.team2.score !== null && match.team2.score !== '') {
      s2 = parseInt(match.team2.score, 10);
    } else if (match.team2Score !== undefined && match.team2Score !== null && match.team2Score !== '') {
      s2 = parseInt(match.team2Score, 10);
    } else if (match.score2 !== undefined && match.score2 !== null && match.score2 !== '') {
      s2 = parseInt(match.score2, 10);
    }

    if (!wId && !isNaN(s1) && !isNaN(s2) && s1 !== s2) {
      wId = (s1 > s2) ? 'team1' : 'team2';
    }

    if ((match.status === 'COMPLETED' || match.status === 'DONE') && !wId && !isNaN(s1) && !isNaN(s2) && s1 !== s2) {
      wId = (s1 > s2) ? 'team1' : 'team2';
    }

    if (!wId) return { winner: null, loser: null };

    var winnerName = null;
    var loserName = null;

    if (wId === 'team1' || wId === '1' || (match.team1 && (String(match.team1.id) === wId || String(match.team1.name) === wId))) {
      winnerName = t1Name;
      loserName = t2Name;
    } else if (wId === 'team2' || wId === '2' || (match.team2 && (String(match.team2.id) === wId || String(match.team2.name) === wId))) {
      winnerName = t2Name;
      loserName = t1Name;
    } else {
      if (t1Name && (wId.toLowerCase() === t1Name.toLowerCase() || isTeamSelf(t1Name, wId))) {
        winnerName = t1Name;
        loserName = t2Name;
      } else if (t2Name && (wId.toLowerCase() === t2Name.toLowerCase() || isTeamSelf(t2Name, wId))) {
        winnerName = t2Name;
        loserName = t1Name;
      }
    }
    return { winner: winnerName, loser: loserName };
  }

  function resolvePointsFromConfig(ptsCfg, positionKey, altKey) {
    if (!ptsCfg || typeof ptsCfg !== 'object') return 0;

    // 1. Direct exact key match
    if (ptsCfg[positionKey] !== undefined) {
      return parseInt(ptsCfg[positionKey], 10) || 0;
    }
    if (altKey && ptsCfg[altKey] !== undefined) {
      return parseInt(ptsCfg[altKey], 10) || 0;
    }
    if (ptsCfg[String(positionKey)] !== undefined) {
      return parseInt(ptsCfg[String(positionKey)], 10) || 0;
    }

    // 2. Direct Stage 1 elimination match
    if (positionKey === "stage1_eliminated" || altKey === "stage1_eliminated") {
      if (ptsCfg["stage1_eliminated"] !== undefined) {
        return parseInt(ptsCfg["stage1_eliminated"], 10) || 0;
      }
    }

    // 3. Fallback for champion
    if ((positionKey === "1" || positionKey === 1) && ptsCfg["champPoints"] !== undefined) {
      return parseInt(ptsCfg["champPoints"], 10) || 0;
    }

    // 4. Range matching for bracket placements (3-4, 5-8, 9-16, etc.)
    var pMin = 0, pMax = 0;
    var posStr = String(positionKey);
    if (posStr.indexOf('-') !== -1) {
      var parts = posStr.split('-');
      pMin = parseInt(parts[0], 10) || 0;
      pMax = parseInt(parts[1], 10) || 0;
    } else {
      pMin = parseInt(positionKey, 10) || 0;
      pMax = pMin;
    }

    if (pMin > 0 && pMax >= pMin) {
      if (pMin === 3 || pMin === 4) {
        if (ptsCfg["3-4"] !== undefined) return parseInt(ptsCfg["3-4"], 10) || 0;
        if (pMin === 3 && ptsCfg["3"] !== undefined) return parseInt(ptsCfg["3"], 10) || 0;
        if (pMin === 4 && ptsCfg["4"] !== undefined) return parseInt(ptsCfg["4"], 10) || 0;
      } else if (pMin >= 5 && pMin <= 8) {
        if (ptsCfg["5-8"] !== undefined) return parseInt(ptsCfg["5-8"], 10) || 0;
        if (ptsCfg["5-6"] !== undefined && pMin <= 6) return parseInt(ptsCfg["5-6"], 10) || 0;
        if (ptsCfg["7-8"] !== undefined && pMin >= 7) return parseInt(ptsCfg["7-8"], 10) || 0;
        if (ptsCfg[String(pMin)] !== undefined) return parseInt(ptsCfg[String(pMin)], 10) || 0;
      } else if (pMin >= 9 && pMin <= 16) {
        if (ptsCfg["9-16"] !== undefined) return parseInt(ptsCfg["9-16"], 10) || 0;
        if (ptsCfg["9-12"] !== undefined && pMin <= 12) return parseInt(ptsCfg["9-12"], 10) || 0;
        if (ptsCfg["13-16"] !== undefined && pMin >= 13) return parseInt(ptsCfg["13-16"], 10) || 0;
        if (ptsCfg[String(pMin)] !== undefined) return parseInt(ptsCfg[String(pMin)], 10) || 0;
      } else if (pMin >= 17 && pMin <= 32) {
        if (ptsCfg["17-32"] !== undefined) return parseInt(ptsCfg["17-32"], 10) || 0;
      } else if (pMin >= 33 && pMin <= 64) {
        if (ptsCfg["33-64"] !== undefined) return parseInt(ptsCfg["33-64"], 10) || 0;
      } else if (pMin >= 65 && pMin <= 128) {
        if (ptsCfg["65-128"] !== undefined) return parseInt(ptsCfg["65-128"], 10) || 0;
      }
    }

    return 0;
  }

  var storageDataCache = {};
  function getStorageData(prefixList, id) {
    if (!prefixList || prefixList.length === 0 || !id) return null;
    var cacheKey = prefixList.join('|') + '__' + id;
    if (storageDataCache[cacheKey] !== undefined) {
      return storageDataCache[cacheKey];
    }
    for (var i = 0; i < prefixList.length; i++) {
      var p = prefixList[i];
      var val = localStorage.getItem(p + id);
      if (val) {
        storageDataCache[cacheKey] = val;
        return val;
      }
      val = localStorage.getItem(p + 'tournament_' + id);
      if (val) {
        storageDataCache[cacheKey] = val;
        return val;
      }
    }
    storageDataCache[cacheKey] = null;
    return null;
  }

  window.filterRollingStandings = function (query) {
    var q = (query || '').toLowerCase().trim();
    var rows = document.querySelectorAll('#rollingStandingsTable tbody tr');

    rows.forEach(function (row) {
      var txt = row.innerText.toLowerCase();
      if (!q || txt.includes(q)) {
        row.style.display = '';
      } else {
        row.style.display = 'none';
      }
    });
  };

  var parsedTournamentResultsCache = {};
  var teamKeyLookupCache = {};

  // Parse points and participation for a single tournament identically to team-profile.js
  function parseTournamentResults(t, teamDataMap) {
    if (!t) return { pointsMap: {}, participatedMap: {} };
    var tCacheKey = t.id || t.name;
    if (tCacheKey && parsedTournamentResultsCache[tCacheKey]) {
      return parsedTournamentResultsCache[tCacheKey];
    }

    var localPtsCfg = {};
    try {
      var localPtsRaw = getStorageData(['tourma_points_config_'], t.id);
      if (localPtsRaw) {
        localPtsCfg = JSON.parse(localPtsRaw) || {};
      }
    } catch (e) {}

    // Priority: DB pointsConfig first, fallback to localPtsCfg
    var ptsCfg = Object.assign({}, localPtsCfg || {}, t.pointsConfig || {});
    var teamPointsAwarded = {}; // teamKey -> max points
    var teamParticipated = {};  // teamKey -> true

    function findTeamKey(name) {
      if (!name) return null;
      var raw = String(name).trim();
      if (teamKeyLookupCache[raw] !== undefined) return teamKeyLookupCache[raw];
      var key = raw.toLowerCase();
      if (teamDataMap[key]) {
        teamKeyLookupCache[raw] = key;
        return key;
      }
      var foundKey = null;
      var mapKeys = Object.keys(teamDataMap);
      for (var kIdx = 0; kIdx < mapKeys.length; kIdx++) {
        var k = mapKeys[kIdx];
        if (isTeamSelf(raw, k)) {
          foundKey = k;
          break;
        }
      }
      teamKeyLookupCache[raw] = foundKey;
      return foundKey;
    }

    function markTeamParticipated(name) {
      var key = findTeamKey(name);
      if (key) {
        teamParticipated[key] = true;
      }
    }

    function awardTeamPoints(name, positionKey, altKey) {
      var key = findTeamKey(name);
      if (!key) return;
      teamParticipated[key] = true;

      var pts = resolvePointsFromConfig(ptsCfg, positionKey, altKey);
      var prev = teamPointsAwarded[key] || 0;
      if (pts > prev || teamPointsAwarded[key] === undefined) {
        teamPointsAwarded[key] = pts;
      }
    }

    // Check registered team list
    try {
      var rawTList = getStorageData(['tourma_teams_'], t.id);
      if (rawTList) {
        var tArr = JSON.parse(rawTList);
        if (Array.isArray(tArr)) {
          tArr.forEach(function (tm) {
            var n = extractName(tm);
            if (n) markTeamParticipated(n);
          });
        }
      }
    } catch (e) {}

    // Check Multi-Stage config
    var rawMultiCfg = getStorageData(['tourma_multi_config_'], t.id);
    var multiConfig = null;
    if (rawMultiCfg) {
      try { multiConfig = JSON.parse(rawMultiCfg); } catch (e) {}
    }

    var isMultiStage = !!(multiConfig && multiConfig.stage2Format) || (t.isMultiStage === true) || (t.tournamentType === 'MULTI_STAGE');
    var s1Format = (multiConfig && multiConfig.stage1Format) ? multiConfig.stage1Format.toUpperCase() : (t.stage1Format ? t.stage1Format.toUpperCase() : (t.format || 'SINGLE_ELIMINATION'));
    var s2Format = (multiConfig && multiConfig.stage2Format) ? multiConfig.stage2Format.toUpperCase() : (t.stage2Format ? t.stage2Format.toUpperCase() : 'SINGLE_ELIMINATION');

    // 1. Stage 1 Swiss System
    if (s1Format.indexOf('SWISS') !== -1 || (!isMultiStage && (t.format || '').indexOf('SWISS') !== -1)) {
      var rawSwiss = getStorageData(['tourma_swiss_matches_'], t.id);
      if (rawSwiss) {
        try {
          var swissMatches = JSON.parse(rawSwiss);
          var sTeamsList = [];
          try { sTeamsList = JSON.parse(getStorageData(['tourma_teams_'], t.id)) || []; } catch (e) {}

          var swissStats = {};
          if (Array.isArray(sTeamsList) && sTeamsList.length > 0) {
            sTeamsList.forEach(function (tm) {
              var tmName = extractName(tm);
              if (tmName) {
                swissStats[tmName.toLowerCase().trim()] = { name: tmName, wins: 0, losses: 0, draws: 0, points: 0, scoresFor: 0, scoresAgainst: 0, diff: 0, buchholz: 0, opponents: [] };
                markTeamParticipated(tmName);
              }
            });
          }

          if (swissMatches && typeof swissMatches === 'object') {
            Object.keys(swissMatches).forEach(function (mk) {
              var m = swissMatches[mk];
              if (!m) return;
              var t1Name = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
              var t2Name = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);
              if (t1Name) markTeamParticipated(t1Name);
              if (t2Name) markTeamParticipated(t2Name);
              if (!t1Name) return;

              var k1 = t1Name.toLowerCase().trim();
              if (!swissStats[k1]) swissStats[k1] = { name: t1Name, wins: 0, losses: 0, draws: 0, points: 0, scoresFor: 0, scoresAgainst: 0, diff: 0, buchholz: 0, opponents: [] };

              if (t2Name) {
                var k2 = t2Name.toLowerCase().trim();
                if (!swissStats[k2]) swissStats[k2] = { name: t2Name, wins: 0, losses: 0, draws: 0, points: 0, scoresFor: 0, scoresAgainst: 0, diff: 0, buchholz: 0, opponents: [] };

                swissStats[k1].opponents.push(k2);
                swissStats[k2].opponents.push(k1);

                var s1 = parseInt(m.team1Score !== undefined ? m.team1Score : m.score1, 10);
                var s2 = parseInt(m.team2Score !== undefined ? m.team2Score : m.score2, 10);
                if (!isNaN(s1) && !isNaN(s2) && (m.status === 'COMPLETED' || m.status === 'DONE' || s1 > 0 || s2 > 0)) {
                  swissStats[k1].scoresFor += s1;
                  swissStats[k1].scoresAgainst += s2;
                  swissStats[k2].scoresFor += s2;
                  swissStats[k2].scoresAgainst += s1;

                  if (s1 > s2) {
                    swissStats[k1].wins += 1;
                    swissStats[k1].points += 3;
                    swissStats[k2].losses += 1;
                  } else if (s2 > s1) {
                    swissStats[k2].wins += 1;
                    swissStats[k2].points += 3;
                    swissStats[k1].losses += 1;
                  } else {
                    swissStats[k1].draws += 1;
                    swissStats[k2].draws += 1;
                    swissStats[k1].points += 1;
                    swissStats[k2].points += 1;
                  }
                }
              } else {
                swissStats[k1].wins += 1;
                swissStats[k1].points += 3;
              }
            });
          }

          Object.keys(swissStats).forEach(function (k) {
            var st = swissStats[k];
            st.diff = st.scoresFor - st.scoresAgainst;
            var bSum = 0;
            st.opponents.forEach(function (oppK) {
              if (swissStats[oppK]) bSum += swissStats[oppK].points;
            });
            st.buchholz = bSum;
          });

          var swissList = Object.keys(swissStats).map(function (k) { return swissStats[k]; });
          swissList.sort(function (a, b) {
            if (a.wins !== b.wins) return b.wins - a.wins;
            if (a.losses !== b.losses) return a.losses - b.losses;
            if (b.buchholz !== a.buchholz) return b.buchholz - a.buchholz;
            if (b.diff !== a.diff) return b.diff - a.diff;
            return b.scoresFor - a.scoresFor;
          });

          if (!isMultiStage) {
            swissList.forEach(function (st, idx) {
              awardTeamPoints(st.name, String(idx + 1));
            });
          } else {
            swissList.forEach(function (st) {
              if (st.losses >= 3 || st.wins < 3) {
                var recKey = "swiss_" + st.wins + "-" + st.losses;
                var altRec = (st.wins === 2) ? "swiss_2-3" : ((st.wins === 1) ? "swiss_1-3" : "swiss_0-3");
                awardTeamPoints(st.name, recKey, altRec);
              }
            });
          }
        } catch (e) {}
      }
    }

    // 2. Stage 1 Round Robin
    if (s1Format === 'ROUND_ROBIN' || (!isMultiStage && t.format === 'ROUND_ROBIN')) {
      var rawRR = getStorageData(['tourma_rr_matches_'], t.id);
      if (rawRR) {
        try {
          var rrData = JSON.parse(rawRR);
          var rrMatchesMap = rrData.matchesMap || rrData;
          var teamsList = rrData.teamsList || [];
          if (!teamsList || teamsList.length === 0) {
            try { teamsList = JSON.parse(getStorageData(['tourma_teams_'], t.id)) || []; } catch (e) {}
          }
          teamsList.forEach(function (tm) {
            var n = extractName(tm);
            if (n) markTeamParticipated(n);
          });

          var rrStats = {};
          teamsList.forEach(function (tm) {
            var tmN = extractName(tm);
            if (tmN) rrStats[tmN.toLowerCase().trim()] = { name: tmN, pts: 0, gf: 0, ga: 0, diff: 0 };
          });

          if (rrMatchesMap && typeof rrMatchesMap === 'object') {
            Object.keys(rrMatchesMap).forEach(function (mk) {
              var m = rrMatchesMap[mk];
              if (!m) return;
              var t1N = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
              var t2N = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);
              if (t1N) markTeamParticipated(t1N);
              if (t2N) markTeamParticipated(t2N);
              if (!t1N || !t2N) return;
              var k1 = t1N.toLowerCase().trim();
              var k2 = t2N.toLowerCase().trim();
              if (!rrStats[k1]) rrStats[k1] = { name: t1N, pts: 0, gf: 0, ga: 0, diff: 0 };
              if (!rrStats[k2]) rrStats[k2] = { name: t2N, pts: 0, gf: 0, ga: 0, diff: 0 };

              var s1 = parseInt(m.team1Score !== undefined ? m.team1Score : m.score1, 10);
              var s2 = parseInt(m.team2Score !== undefined ? m.team2Score : m.score2, 10);
              if (!isNaN(s1) && !isNaN(s2) && (m.status === 'COMPLETED' || m.status === 'DONE' || s1 > 0 || s2 > 0)) {
                rrStats[k1].gf += s1; rrStats[k1].ga += s2;
                rrStats[k2].gf += s2; rrStats[k2].ga += s1;
                if (s1 > s2) rrStats[k1].pts += 3;
                else if (s2 > s1) rrStats[k2].pts += 3;
                else { rrStats[k1].pts += 1; rrStats[k2].pts += 1; }
              }
            });
          }

          var rrList = Object.keys(rrStats).map(function (k) {
            rrStats[k].diff = rrStats[k].gf - rrStats[k].ga;
            return rrStats[k];
          });
          rrList.sort(function (a, b) {
            if (b.pts !== a.pts) return b.pts - a.pts;
            if (b.diff !== a.diff) return b.diff - a.diff;
            return b.gf - a.gf;
          });

          if (rrList.length > 0) {
            rrList.forEach(function (row, idx) {
              var name = row.name;
              if (!isMultiStage) {
                awardTeamPoints(name, String(idx + 1));
              } else {
                awardTeamPoints(name, "stage1_eliminated", String(idx + 1));
              }
            });
          }
        } catch (e) {}
      }
    }

    // 3. Stage 1 Group Stage
    if (s1Format === 'GROUP_STAGE' || (!isMultiStage && t.format === 'GROUP_STAGE')) {
      var rawGroupAssignments = getStorageData(['tourma_group_assignments_'], t.id);
      var rawGroupMatches = getStorageData(['tourma_group_matches_'], t.id);
      if (rawGroupAssignments || rawGroupMatches) {
        try {
          var gAssignments = rawGroupAssignments ? JSON.parse(rawGroupAssignments) : null;
          var gMatches = rawGroupMatches ? JSON.parse(rawGroupMatches) : null;

          if (gAssignments && typeof gAssignments === 'object') {
            Object.keys(gAssignments).forEach(function (grpKey) {
              var grpTeams = gAssignments[grpKey] || [];
              if (Array.isArray(grpTeams) && grpTeams.length > 0) {
                var grpStats = {};
                grpTeams.forEach(function (tm) {
                  var tName = extractName(tm);
                  if (tName) {
                    markTeamParticipated(tName);
                    grpStats[tName.toLowerCase().trim()] = { name: tName, pts: 0, diff: 0, gf: 0 };
                  }
                });

                if (gMatches && typeof gMatches === 'object') {
                  Object.keys(gMatches).forEach(function (mk) {
                    var m = gMatches[mk];
                    if (!m) return;
                    if (m.groupKey && m.groupKey !== grpKey) return;
                    var t1N = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
                    var t2N = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);
                    if (t1N) markTeamParticipated(t1N);
                    if (t2N) markTeamParticipated(t2N);
                    if (!t1N || !t2N) return;
                    var k1 = t1N.toLowerCase().trim();
                    var k2 = t2N.toLowerCase().trim();
                    if (!grpStats[k1] || !grpStats[k2]) return;

                    var s1 = parseInt(m.team1Score !== undefined ? m.team1Score : m.score1, 10);
                    var s2 = parseInt(m.team2Score !== undefined ? m.team2Score : m.score2, 10);
                    if (!isNaN(s1) && !isNaN(s2) && (m.status === 'COMPLETED' || m.status === 'DONE' || s1 > 0 || s2 > 0)) {
                      grpStats[k1].gf += s1;
                      grpStats[k1].diff += (s1 - s2);
                      grpStats[k2].gf += s2;
                      grpStats[k2].diff += (s2 - s1);
                      if (s1 > s2) grpStats[k1].pts += 3;
                      else if (s2 > s1) grpStats[k2].pts += 3;
                      else { grpStats[k1].pts += 1; grpStats[k2].pts += 1; }
                    }
                  });
                }

                var grpList = Object.keys(grpStats).map(function (k) { return grpStats[k]; });
                grpList.sort(function (a, b) {
                  if (b.pts !== a.pts) return b.pts - a.pts;
                  if (b.diff !== a.diff) return b.diff - a.diff;
                  return b.gf - a.gf;
                });

                if (!isMultiStage) {
                  grpList.forEach(function (st, rIdx) {
                    var rNum = rIdx + 1;
                    if (rNum === 1) awardTeamPoints(st.name, "1", "group_rank_1");
                    else if (rNum === 2) awardTeamPoints(st.name, "2", "group_rank_2");
                    else if (rNum === 3 || rNum === 4) awardTeamPoints(st.name, "3-4", "group_rank_" + rNum);
                    else awardTeamPoints(st.name, "stage1_eliminated", "group_rank_" + rNum);
                  });
                } else {
                  grpList.forEach(function (st) {
                    awardTeamPoints(st.name, "stage1_eliminated");
                  });
                }
              }
            });
          }
        } catch (e) {}
      }
    }

    // 4. Stage 1 Single Elimination
    if (s1Format === 'SINGLE_ELIMINATION' || (!isMultiStage && t.format === 'SINGLE_ELIMINATION')) {
      var rawBracket = getStorageData(['tourma_bracket_', 'tourma_bracket_matches_', 'tourma_matches_'], t.id);
      if (rawBracket) {
        try {
          var bracketData = JSON.parse(rawBracket);
          var matchesMap = bracketData.matchesMap || bracketData;
          var roundsList = bracketData.roundsList || [];

          if (roundsList && Array.isArray(roundsList) && roundsList.length > 0) {
            var totalRounds = roundsList.length;
            for (var rIdx = 0; rIdx < totalRounds; rIdx++) {
              var rObj = roundsList[rIdx];
              var distFromFinal = totalRounds - 1 - rIdx;
              var roundMatches = (rObj && rObj.matches) ? rObj.matches : [];

              var posKey = "";
              if (distFromFinal === 0) {
                posKey = "2";
              } else if (distFromFinal === 1) {
                posKey = "3-4";
              } else if (distFromFinal === 2) {
                posKey = "5-8";
              } else {
                var teamsInRound = Math.pow(2, distFromFinal + 1);
                var sP = Math.pow(2, distFromFinal) + 1;
                var eP = teamsInRound;
                posKey = sP + "-" + eP;
              }

              roundMatches.forEach(function (m) {
                if (!m) return;
                var t1N = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
                var t2N = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);
                if (t1N) markTeamParticipated(t1N);
                if (t2N) markTeamParticipated(t2N);

                if (m.winnerId || m.winner || m.status === 'COMPLETED' || m.status === 'DONE') {
                  var mRes = resolveWinnerAndLoser(m);
                  if (mRes.loser) {
                    awardTeamPoints(mRes.loser, posKey, "stage1_eliminated");
                  }
                  if (mRes.winner && distFromFinal === 0 && !isMultiStage) {
                    awardTeamPoints(mRes.winner, "1");
                  }
                }
              });
            }
          } else if (matchesMap && typeof matchesMap === 'object') {
            var mKeys = Object.keys(matchesMap);
            var totalTeams = 0;
            try {
              var sTeams = JSON.parse(getStorageData(['tourma_teams_'], t.id));
              if (Array.isArray(sTeams) && sTeams.length > 0) totalTeams = sTeams.length;
            } catch (e) {}
            if (!totalTeams || totalTeams < 2) totalTeams = 16;
            var totalFullRounds = Math.round(Math.log2(totalTeams));

            var finalMatchKey = null;
            mKeys.forEach(function (mk) {
              var m = matchesMap[mk];
              if (m && (!m.nextMatchId || m.nextMatchId === 'null' || m.nextMatchId === '') && ((m.roundNumber || m.round) === totalFullRounds || totalFullRounds === 1 || !finalMatchKey)) {
                finalMatchKey = mk;
              }
            });

            var matchDistToFinal = {};
            if (finalMatchKey) {
              matchDistToFinal[finalMatchKey] = 0;
              var queue = [finalMatchKey];
              while (queue.length > 0) {
                var currId = queue.shift();
                var currDist = matchDistToFinal[currId];
                mKeys.forEach(function (mk) {
                  var m = matchesMap[mk];
                  if (m && String(m.nextMatchId) === String(currId) && matchDistToFinal[mk] === undefined) {
                    matchDistToFinal[mk] = currDist + 1;
                    queue.push(mk);
                  }
                });
              }
            }

            mKeys.forEach(function (mk) {
              var m = matchesMap[mk];
              if (!m) return;
              var t1N = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
              var t2N = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);
              if (t1N) markTeamParticipated(t1N);
              if (t2N) markTeamParticipated(t2N);

              if (m.winnerId || m.winner || m.status === 'COMPLETED' || m.status === 'DONE') {
                var mRes = resolveWinnerAndLoser(m);
                var roundDiff = 0;
                if (matchDistToFinal[mk] !== undefined) {
                  roundDiff = matchDistToFinal[mk];
                } else {
                  var rNum = m.roundNumber || m.round || 1;
                  roundDiff = Math.max(0, totalFullRounds - rNum);
                }

                var posKey2 = "";
                if (roundDiff === 0) {
                  posKey2 = "2";
                } else if (roundDiff === 1) {
                  posKey2 = "3-4";
                } else if (roundDiff === 2) {
                  posKey2 = "5-8";
                } else {
                  var teamsInRound2 = Math.pow(2, roundDiff + 1);
                  var sP2 = Math.pow(2, roundDiff) + 1;
                  var eP2 = teamsInRound2;
                  posKey2 = sP2 + "-" + eP2;
                }

                if (mRes.loser) {
                  awardTeamPoints(mRes.loser, posKey2, "stage1_eliminated");
                }
                if (mRes.winner && roundDiff === 0 && !isMultiStage) {
                  awardTeamPoints(mRes.winner, "1");
                }
              }
            });
          }
        } catch (e) {}
      }
    }

    // 5. Stage 1 Double Elimination
    if (s1Format === 'DOUBLE_ELIMINATION' || (!isMultiStage && t.format === 'DOUBLE_ELIMINATION')) {
      var rawDE = getStorageData(['tourma_de_matches_'], t.id);
      if (rawDE) {
        try {
          var deData = JSON.parse(rawDE);
          var upperRounds = deData.upperRounds || [];
          var lowerRounds = deData.lowerRounds || [];
          var gfRound = deData.grandFinalsRound;

          if (upperRounds && upperRounds.length > 0) {
            upperRounds.forEach(function (ur) {
              if (ur && ur.matches) {
                ur.matches.forEach(function (m) {
                  if (m) {
                    var t1N = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
                    var t2N = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);
                    if (t1N) markTeamParticipated(t1N);
                    if (t2N) markTeamParticipated(t2N);
                  }
                });
              }
            });
          }

          if (lowerRounds && lowerRounds.length > 0) {
            var totalLbR = lowerRounds.length;
            for (var lrIdx = 0; lrIdx < totalLbR; lrIdx++) {
              var lrNum = lrIdx + 1;
              var offset = totalLbR - 1 - lrIdx;
              var roundObj = lowerRounds[lrIdx];
              var rawTitle = (roundObj && roundObj.title) ? roundObj.title.trim() : ('LB Round ' + lrNum);

              var isLbCut = isMultiStage && (lrIdx === totalLbR - 1 || rawTitle.toLowerCase().indexOf("qualification") !== -1 || rawTitle.toLowerCase().indexOf("lb cut") !== -1);

              var posKey = (offset === 0) ? "3" : ((offset === 1) ? "4" : "5-8");
              if (isMultiStage) {
                posKey = isLbCut ? "s1_lb_cut" : ("s1_lb_r" + lrNum);
              } else if (offset >= 2) {
                var k = Math.floor((offset - 2) / 2);
                var tStart = Math.pow(2, k + 2) + 1;
                var hSize = Math.pow(2, k + 1);
                var tEnd = Math.pow(2, k + 3);
                posKey = (offset % 2 === 0) ? (tStart + "-" + (tStart + hSize - 1)) : ((tStart + hSize) + "-" + tEnd);
              }

              if (roundObj && roundObj.matches) {
                roundObj.matches.forEach(function (m) {
                  if (m) {
                    var t1N = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
                    var t2N = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);
                    if (t1N) markTeamParticipated(t1N);
                    if (t2N) markTeamParticipated(t2N);

                    if (m.winnerId || m.winner || m.status === 'COMPLETED' || m.status === 'DONE') {
                      var res = resolveWinnerAndLoser(m);
                      if (res.loser) {
                        awardTeamPoints(res.loser, posKey, "stage1_eliminated");
                      }
                    }
                  }
                });
              }
            }
          }

          if (!isMultiStage && gfRound && gfRound.matches && gfRound.matches.length > 0) {
            var gfMatches = gfRound.matches;
            var gfFinal = (gfMatches.length > 1 && (gfMatches[1].winnerId || gfMatches[1].winner || gfMatches[1].status === 'COMPLETED')) ? gfMatches[1] : gfMatches[0];
            if (gfFinal) {
              var t1N = extractName(gfFinal.team1) || (gfFinal.team1Name ? extractName(gfFinal.team1Name) : null);
              var t2N = extractName(gfFinal.team2) || (gfFinal.team2Name ? extractName(gfFinal.team2Name) : null);
              if (t1N) markTeamParticipated(t1N);
              if (t2N) markTeamParticipated(t2N);

              if (gfFinal.winnerId || gfFinal.winner || gfFinal.status === 'COMPLETED' || gfFinal.status === 'DONE') {
                var resGF = resolveWinnerAndLoser(gfFinal);
                if (resGF.winner) awardTeamPoints(resGF.winner, "1");
                if (resGF.loser) awardTeamPoints(resGF.loser, "2");
              }
            }
          }
        } catch (e) {}
      }
    }

    // =========================================================================
    // STAGE 2 (If Multi-Stage)
    // =========================================================================
    if (isMultiStage) {
      // Stage 2 Round Robin
      if (s2Format === 'ROUND_ROBIN') {
        var rawRRS2 = getStorageData(['tourma_rr_matches_stage2_', 'tourma_matches_stage2_'], t.id);
        var teamsListS2 = [];
        try { teamsListS2 = JSON.parse(getStorageData(['tourma_stage2_teams_'], t.id)) || []; } catch (e) {}

        if (rawRRS2) {
          try {
            var rrDataS2 = JSON.parse(rawRRS2);
            var rrMatchesMapS2 = rrDataS2.matchesMap || rrDataS2;
            var rrStatsS2 = {};

            teamsListS2.forEach(function (tm) {
              var tmN = extractName(tm);
              if (tmN) {
                markTeamParticipated(tmN);
                rrStatsS2[tmN.toLowerCase().trim()] = { name: tmN, pts: 0, gf: 0, ga: 0, diff: 0 };
              }
            });

            if (rrMatchesMapS2 && typeof rrMatchesMapS2 === 'object') {
              Object.keys(rrMatchesMapS2).forEach(function (mk) {
                var m = rrMatchesMapS2[mk];
                if (!m) return;
                var t1N = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
                var t2N = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);
                if (t1N) markTeamParticipated(t1N);
                if (t2N) markTeamParticipated(t2N);
                if (!t1N || !t2N) return;
                var k1 = t1N.toLowerCase().trim();
                var k2 = t2N.toLowerCase().trim();
                if (!rrStatsS2[k1]) rrStatsS2[k1] = { name: t1N, pts: 0, gf: 0, ga: 0, diff: 0 };
                if (!rrStatsS2[k2]) rrStatsS2[k2] = { name: t2N, pts: 0, gf: 0, ga: 0, diff: 0 };

                var s1 = parseInt(m.team1Score !== undefined ? m.team1Score : m.score1, 10);
                var s2 = parseInt(m.team2Score !== undefined ? m.team2Score : m.score2, 10);
                if (!isNaN(s1) && !isNaN(s2) && (m.status === 'COMPLETED' || m.status === 'DONE' || s1 > 0 || s2 > 0)) {
                  rrStatsS2[k1].gf += s1; rrStatsS2[k1].ga += s2;
                  rrStatsS2[k2].gf += s2; rrStatsS2[k2].ga += s1;
                  if (s1 > s2) rrStatsS2[k1].pts += 3;
                  else if (s2 > s1) rrStatsS2[k2].pts += 3;
                  else { rrStatsS2[k1].pts += 1; rrStatsS2[k2].pts += 1; }
                }
              });
            }

            var rrListS2 = Object.keys(rrStatsS2).map(function (k) {
              rrStatsS2[k].diff = rrStatsS2[k].gf - rrStatsS2[k].ga;
              return rrStatsS2[k];
            });
            rrListS2.sort(function (a, b) {
              if (b.pts !== a.pts) return b.pts - a.pts;
              if (b.diff !== a.diff) return b.diff - a.diff;
              return b.gf - a.gf;
            });

            if (rrListS2.length > 0) {
              rrListS2.forEach(function (row, idx) {
                var rNum = idx + 1;
                awardTeamPoints(row.name, String(rNum));
              });
            }
          } catch (e) {}
        }
      }

      // Stage 2 Double Elimination
      else if (s2Format === 'DOUBLE_ELIMINATION') {
        var rawDES2 = getStorageData(['tourma_de_matches_stage2_'], t.id);
        if (rawDES2) {
          try {
            var deDataS2 = JSON.parse(rawDES2);
            var upperRoundsS2 = deDataS2.upperRounds || [];
            var lowerRoundsS2 = deDataS2.lowerRounds || [];
            var gfRoundS2 = deDataS2.grandFinalsRound;

            if (upperRoundsS2 && upperRoundsS2.length > 0) {
              upperRoundsS2.forEach(function (ur) {
                if (ur && ur.matches) {
                  ur.matches.forEach(function (m) {
                    if (m) {
                      var t1N = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
                      var t2N = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);
                      if (t1N) markTeamParticipated(t1N);
                      if (t2N) markTeamParticipated(t2N);
                    }
                  });
                }
              });
            }

            if (lowerRoundsS2 && lowerRoundsS2.length > 0) {
              var totalLbR2 = lowerRoundsS2.length;
              for (var lrIdx2 = 0; lrIdx2 < totalLbR2; lrIdx2++) {
                var lrNum2 = lrIdx2 + 1;
                var offset2 = totalLbR2 - 1 - lrIdx2;
                var roundObj2 = lowerRoundsS2[lrIdx2];
                var rawTitle2 = (roundObj2 && roundObj2.title) ? roundObj2.title.trim() : ('LB Round ' + lrNum2);

                var posKey2 = (offset2 === 0) ? "3" : ((offset2 === 1) ? "4" : "5-8");
                if (offset2 >= 2) {
                  var k2 = Math.floor((offset2 - 2) / 2);
                  var tStart2 = Math.pow(2, k2 + 2) + 1;
                  var hSize2 = Math.pow(2, k2 + 1);
                  var tEnd2 = Math.pow(2, k2 + 3);
                  posKey2 = (offset2 % 2 === 0) ? (tStart2 + "-" + (tStart2 + hSize2 - 1)) : ((tStart2 + hSize2) + "-" + tEnd2);
                }

                if (roundObj2 && roundObj2.matches) {
                  roundObj2.matches.forEach(function (m) {
                    if (m) {
                      var t1N = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
                      var t2N = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);
                      if (t1N) markTeamParticipated(t1N);
                      if (t2N) markTeamParticipated(t2N);

                      if (m.winnerId || m.winner || m.status === 'COMPLETED' || m.status === 'DONE') {
                        var res2 = resolveWinnerAndLoser(m);
                        if (res2.loser) {
                          awardTeamPoints(res2.loser, posKey2, "s2_lb_r" + lrNum2);
                        }
                      }
                    }
                  });
                }
              }
            }

            if (gfRoundS2 && gfRoundS2.matches && gfRoundS2.matches.length > 0) {
              var gfMatchesS2 = gfRoundS2.matches;
              var gfFinalS2 = (gfMatchesS2.length > 1 && (gfMatchesS2[1].winnerId || gfMatchesS2[1].winner || gfMatchesS2[1].status === 'COMPLETED')) ? gfMatchesS2[1] : gfMatches[0];
              if (gfFinalS2) {
                var t1N = extractName(gfFinalS2.team1) || (gfFinalS2.team1Name ? extractName(gfFinalS2.team1Name) : null);
                var t2N = extractName(gfFinalS2.team2) || (gfFinalS2.team2Name ? extractName(gfFinalS2.team2Name) : null);
                if (t1N) markTeamParticipated(t1N);
                if (t2N) markTeamParticipated(t2N);

                if (gfFinalS2.winnerId || gfFinalS2.winner || gfFinalS2.status === 'COMPLETED' || gfFinalS2.status === 'DONE') {
                  var resGFS2 = resolveWinnerAndLoser(gfFinalS2);
                  if (resGFS2.winner) awardTeamPoints(resGFS2.winner, "1");
                  if (resGFS2.loser) awardTeamPoints(resGFS2.loser, "2");
                }
              }
            }
          } catch (e) {}
        }
      }

      // Stage 2 Single Elimination
      else {
        var rawBracketS2 = getStorageData(['tourma_bracket_stage2_', 'tourma_matches_stage2_'], t.id);
        if (rawBracketS2) {
          try {
            var bracketDataS2 = JSON.parse(rawBracketS2);
            var matchesMapS2 = bracketDataS2.matchesMap || bracketDataS2;
            var roundsListS2 = bracketDataS2.roundsList || [];

            if (roundsListS2 && Array.isArray(roundsListS2) && roundsListS2.length > 0) {
              var totalRoundsS2 = roundsListS2.length;
              for (var rIdxS2 = 0; rIdxS2 < totalRoundsS2; rIdxS2++) {
                var rObjS2 = roundsListS2[rIdxS2];
                var distS2 = totalRoundsS2 - 1 - rIdxS2;
                var roundMatchesS2 = (rObjS2 && rObjS2.matches) ? rObjS2.matches : [];

                var posKeyS2 = "";
                if (distS2 === 0) {
                  posKeyS2 = "2";
                } else if (distS2 === 1) {
                  posKeyS2 = "3-4";
                } else if (distS2 === 2) {
                  posKeyS2 = "5-8";
                } else {
                  var teamsInRS2 = Math.pow(2, distS2 + 1);
                  var sPS2 = Math.pow(2, distS2) + 1;
                  var ePS2 = teamsInRS2;
                  posKeyS2 = sPS2 + "-" + ePS2;
                }

                roundMatchesS2.forEach(function (m) {
                  if (!m) return;
                  var t1N = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
                  var t2N = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);
                  if (t1N) markTeamParticipated(t1N);
                  if (t2N) markTeamParticipated(t2N);

                  if (m.winnerId || m.winner || m.status === 'COMPLETED' || m.status === 'DONE') {
                    var resS2 = resolveWinnerAndLoser(m);
                    if (distS2 === 0 && resS2.winner) {
                      awardTeamPoints(resS2.winner, "1");
                    }
                    if (resS2.loser) {
                      awardTeamPoints(resS2.loser, posKeyS2);
                    }
                  }
                });
              }
            } else if (matchesMapS2 && typeof matchesMapS2 === 'object') {
              var mKeysS2 = Object.keys(matchesMapS2);
              var s2TeamsCount = 0;
              try {
                var st2 = JSON.parse(getStorageData(['tourma_stage2_teams_'], t.id));
                if (Array.isArray(st2) && st2.length > 0) s2TeamsCount = st2.length;
              } catch (e) {}
              if (!s2TeamsCount || s2TeamsCount < 2) s2TeamsCount = 4;
              var totalS2FullRounds = Math.round(Math.log2(s2TeamsCount));

              var finalMatchKeyS2 = null;
              mKeysS2.forEach(function (mk) {
                var m = matchesMapS2[mk];
                if (m && (!m.nextMatchId || m.nextMatchId === 'null' || m.nextMatchId === '') && ((m.roundNumber || m.round) === totalS2FullRounds || totalS2FullRounds === 1 || !finalMatchKeyS2)) {
                  finalMatchKeyS2 = mk;
                }
              });

              var matchDistToFinalS2 = {};
              if (finalMatchKeyS2) {
                matchDistToFinalS2[finalMatchKeyS2] = 0;
                var queueS2 = [finalMatchKeyS2];
                while (queueS2.length > 0) {
                  var currIdS2 = queueS2.shift();
                  var currDistS2 = matchDistToFinalS2[currIdS2];
                  mKeysS2.forEach(function (mk) {
                    var m = matchesMapS2[mk];
                    if (m && String(m.nextMatchId) === String(currIdS2) && matchDistToFinalS2[mk] === undefined) {
                      matchDistToFinalS2[mk] = currDistS2 + 1;
                      queueS2.push(mk);
                    }
                  });
                }
              }

              mKeysS2.forEach(function (mk) {
                var m = matchesMapS2[mk];
                if (!m) return;
                var t1N = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
                var t2N = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);
                if (t1N) markTeamParticipated(t1N);
                if (t2N) markTeamParticipated(t2N);

                if (m.winnerId || m.winner || m.status === 'COMPLETED' || m.status === 'DONE') {
                  var resS2 = resolveWinnerAndLoser(m);
                  var roundDiffS2 = 0;
                  if (matchDistToFinalS2[mk] !== undefined) {
                    roundDiffS2 = matchDistToFinalS2[mk];
                  } else {
                    var rNumS2 = m.roundNumber || m.round || 1;
                    roundDiffS2 = Math.max(0, totalS2FullRounds - rNumS2);
                  }

                  var posKeyS2_2 = "";
                  if (roundDiffS2 === 0) {
                    posKeyS2_2 = "2";
                  } else if (roundDiffS2 === 1) {
                    posKeyS2_2 = "3-4";
                  } else if (roundDiffS2 === 2) {
                    posKeyS2_2 = "5-8";
                  } else {
                    var teamsInRoundS2 = Math.pow(2, roundDiffS2 + 1);
                    var sPos = Math.floor(teamsInRoundS2 / 2) + 1;
                    var ePos = teamsInRoundS2;
                    posKeyS2_2 = sPos + "-" + ePos;
                  }

                  if (roundDiffS2 === 0 && resS2.winner) {
                    awardTeamPoints(resS2.winner, "1");
                  }
                  if (resS2.loser) {
                    awardTeamPoints(resS2.loser, posKeyS2_2);
                  }
                }
              });
            }
          } catch (e) {}
        }
      }
    }

    // Champion override
    var rawChamp = getStorageData(['tourma_champion_'], t.id);
    if (rawChamp) {
      try {
        var cName = extractName(rawChamp) || (typeof rawChamp === 'string' ? rawChamp.trim() : null);
        if (cName) {
          awardTeamPoints(cName, "1");
        }
      } catch (e) {}
    }

    var resObj = { pointsMap: teamPointsAwarded, participatedMap: teamParticipated };
    if (tCacheKey) {
      parsedTournamentResultsCache[tCacheKey] = resObj;
    }
    return resObj;
  }

  // =========================================================================
  // MAIN STANDINGS RECALCULATION BY MILESTONE
  // =========================================================================
  function computeStandingsForMilestone(milestoneVal) {
    storageDataCache = {};
    parsedTournamentResultsCache = {};
    teamKeyLookupCache = {};

    var standingsTable = document.getElementById('rollingStandingsTable');
    if (!standingsTable) return;

    var tbody = standingsTable.querySelector('tbody');
    if (!tbody) return;

    var teamDataMap = {};

    // 1. Populate official partner participants
    var partners = window.seriesPartners || [];
    partners.forEach(function (p) {
      if (p && p.name) {
        var k = p.name.toLowerCase().trim();
        teamDataMap[k] = {
          name: p.name.trim(),
          totalPts: 0,
          lastPts: 0,
          droppedPts: 0,
          activeTourneys: 0
        };
      }
    });

    var subTourneys = window.seriesSubTournaments || [];
    var totalTourneys = subTourneys.length;
    var phaseSize = window.seriesPhaseSize || 3;

    if (totalTourneys === 0) return;

    var targetIdx = (milestoneVal === 'LATEST' || milestoneVal === undefined || milestoneVal === null) ? (totalTourneys - 1) : parseInt(milestoneVal, 10);
    if (isNaN(targetIdx) || targetIdx < 0) targetIdx = 0;
    if (targetIdx >= totalTourneys) targetIdx = totalTourneys - 1;

    var targetTourney = subTourneys[targetIdx];

    // Update Header Title & Milestone Label
    var tableTitle = document.getElementById('standingsTableTitle');
    var thLastPts = document.getElementById('thLastPts');

    if (tableTitle) {
      if (milestoneVal === 'LATEST') {
        tableTitle.textContent = 'Bảng Xếp Hạng Chi Tiết (Cửa Sổ Trượt W = ' + phaseSize + ' Giải)';
      } else {
        tableTitle.textContent = 'Bảng Xếp Hạng Tính Đến: ' + targetTourney.name + ' (Giải #' + (targetIdx + 1) + ')';
      }
    }

    if (thLastPts) {
      if (milestoneVal === 'LATEST') {
        thLastPts.textContent = 'Điểm giải gần nhất';
      } else {
        thLastPts.textContent = 'Điểm giải #' + (targetIdx + 1);
      }
    }

    // Parse results for all sub-tournaments from 0 to totalTourneys - 1
    var tourneyPointsArray = [];
    var tourneyParticipatedArray = [];

    for (var tIdx = 0; tIdx < totalTourneys; tIdx++) {
      var serverPts = (window.serverTourneyPoints && window.serverTourneyPoints[tIdx]) ? Object.assign({}, window.serverTourneyPoints[tIdx]) : {};
      var serverPart = (window.serverTourneyParticipation && window.serverTourneyParticipation[tIdx]) ? Object.assign({}, window.serverTourneyParticipation[tIdx]) : {};

      var localRes = parseTournamentResults(subTourneys[tIdx], teamDataMap);

      // Merge local live results on top of server data
      var mergedPts = Object.assign({}, serverPts);
      if (localRes && localRes.pointsMap) {
        Object.keys(localRes.pointsMap).forEach(function (k) {
          if (localRes.pointsMap[k] > 0 || mergedPts[k] === undefined) {
            mergedPts[k] = localRes.pointsMap[k];
          }
        });
      }

      var mergedPart = Object.assign({}, serverPart);
      if (localRes && localRes.participatedMap) {
        Object.keys(localRes.participatedMap).forEach(function (k) {
          if (localRes.participatedMap[k]) {
            mergedPart[k] = true;
          }
        });
      }

      tourneyPointsArray[tIdx] = mergedPts;
      tourneyParticipatedArray[tIdx] = mergedPart;
    }

    // 1. Calculate Previous Milestone Standings (for Rank Fluctuation comparison)
    var prevRankMap = {};
    if (targetIdx >= 1) {
      var prevTargetIdx = targetIdx - 1;
      var prevWindowStart = Math.max(0, prevTargetIdx - phaseSize + 1);
      var prevWindowEnd = prevTargetIdx;
      var prevScores = [];

      Object.keys(teamDataMap).forEach(function (pk) {
        var sumPts = 0;
        for (var pIdx = prevWindowStart; pIdx <= prevWindowEnd; pIdx++) {
          var pMap = tourneyPointsArray[pIdx] || {};
          if (pMap[pk] !== undefined) {
            sumPts += pMap[pk];
          }
        }
        var prevLastPts = (tourneyPointsArray[prevTargetIdx] && tourneyPointsArray[prevTargetIdx][pk]) ? tourneyPointsArray[prevTargetIdx][pk] : 0;
        prevScores.push({
          key: pk,
          name: teamDataMap[pk].name,
          pts: sumPts,
          lastPts: prevLastPts
        });
      });

      prevScores.sort(function (a, b) {
        if (b.pts !== a.pts) return b.pts - a.pts;
        if (b.lastPts !== a.lastPts) return b.lastPts - a.lastPts;
        return a.name.localeCompare(b.name);
      });

      prevScores.forEach(function (st, prIdx) {
        prevRankMap[st.key] = prIdx + 1;
      });
    }

    // 2. Calculate Current Milestone Standings
    var currentWindowStart = Math.max(0, targetIdx - phaseSize + 1);
    var currentWindowEnd = targetIdx;
    var droppedIdx = targetIdx - phaseSize;

    Object.keys(teamDataMap).forEach(function (k) {
      var entry = teamDataMap[k];
      var totalPts = 0;
      var playedCount = 0;

      for (var wIdx = currentWindowStart; wIdx <= currentWindowEnd; wIdx++) {
        var ptsMap = tourneyPointsArray[wIdx] || {};
        var partMap = tourneyParticipatedArray[wIdx] || {};
        if (ptsMap[k] !== undefined) {
          totalPts += ptsMap[k];
        }
        if (partMap[k] || (ptsMap[k] !== undefined && ptsMap[k] > 0)) {
          playedCount += 1;
        }
      }

      var lastTourneyPts = (tourneyPointsArray[targetIdx] && tourneyPointsArray[targetIdx][k]) || 0;
      var droppedTourneyPts = (droppedIdx >= 0 && tourneyPointsArray[droppedIdx] && tourneyPointsArray[droppedIdx][k]) || 0;

      entry.totalPts = totalPts;
      entry.activeTourneys = playedCount;
      entry.lastPts = lastTourneyPts;
      entry.droppedPts = droppedTourneyPts;
    });

    // 3. Sort Teams for Current Milestone (Exact same comparator as team-profile.js)
    var teamDataArray = Object.keys(teamDataMap).map(function (k) { return teamDataMap[k]; });
    teamDataArray.sort(function (a, b) {
      if (b.totalPts !== a.totalPts) return b.totalPts - a.totalPts;
      if (b.lastPts !== a.lastPts) return b.lastPts - a.lastPts;
      return a.name.localeCompare(b.name);
    });

    // 4. Render Table DOM cleanly with full exact rankings & fluctuations
    tbody.innerHTML = '';
    var ctx = (typeof window.appContextPath !== 'undefined' && window.appContextPath) ? window.appContextPath : '';
    var seriesId = (typeof window.seriesIdVal !== 'undefined' && window.seriesIdVal) ? window.seriesIdVal : '';

    var frag = document.createDocumentFragment();

    teamDataArray.forEach(function (data, rankIdx) {
      var rank = rankIdx + 1;
      var teamKey = data.name.toLowerCase().trim();

      var prevRank = (targetIdx >= 1 && prevRankMap[teamKey] !== undefined) ? prevRankMap[teamKey] : rank;
      var rankChange = (targetIdx >= 1) ? (prevRank - rank) : 0;

      var rankChangeHtml = '';
      if (targetIdx >= 1) {
        if (rankChange > 0) {
          rankChangeHtml = '<span class="rank-change up" title="Tăng ' + rankChange + ' bậc"><i class="fa-solid fa-arrow-up"></i> ' + rankChange + '</span>';
        } else if (rankChange < 0) {
          rankChangeHtml = '<span class="rank-change down" title="Giảm ' + Math.abs(rankChange) + ' bậc"><i class="fa-solid fa-arrow-down"></i> ' + Math.abs(rankChange) + '</span>';
        } else {
          rankChangeHtml = '<span class="rank-change same" title="Không đổi bậc">-</span>';
        }
      } else {
        rankChangeHtml = '<span class="rank-change same" title="Giải đầu tiên">-</span>';
      }

      var row = document.createElement('tr');
      row.setAttribute('data-team-name', data.name);

      var lastPtsText = data.lastPts > 0 ? ('<span style="color: #2dd4bf; font-weight: 700;">+' + data.lastPts + ' pts</span>') : '<span style="color: #94a3b8;">0 pts</span>';

      var netChange = data.lastPts - (data.droppedPts || 0);
      var changeText = '0 pts';
      var changeColor = '#94a3b8';
      if (netChange > 0) {
        changeText = '+' + netChange + ' pts';
        changeColor = '#2dd4bf';
      } else if (netChange < 0) {
        changeText = netChange + ' pts';
        changeColor = '#ef4444';
      }

      row.innerHTML = 
        '<td style="font-weight: 800; color: #ffffff;">' +
          '<div class="rank-wrap">' +
            '<span class="rank-badge rank-' + rank + '">#' + rank + '</span>' +
            rankChangeHtml +
          '</div>' +
        '</td>' +
        '<td style="font-weight: 700; color: #ffffff;">' +
          '<a href="' + ctx + '/team-profile?seriesId=' + encodeURIComponent(seriesId) + '&teamName=' + encodeURIComponent(data.name) + '" style="color: #ffffff; text-decoration: none; transition: color 0.18s ease;" onmouseover="this.style.color=\'#2dd4bf\'" onmouseout="this.style.color=\'#ffffff\'">' +
            data.name +
          '</a>' +
        '</td>' +
        '<td style="text-align: center; font-weight: 700; color: var(--rolling-text-muted);">' +
          data.activeTourneys +
        '</td>' +
        '<td style="text-align: right; font-weight: 800; font-size: 1.05rem; color: #fbbf24;">' +
          data.totalPts + ' pts' +
        '</td>' +
        '<td style="text-align: right; font-weight: 700;">' +
          lastPtsText +
        '</td>' +
        '<td style="text-align: right; font-weight: 700; color: ' + changeColor + ';">' +
          changeText +
        '</td>';

      frag.appendChild(row);
    });

    tbody.appendChild(frag);
  }

  // =========================================================================
  // PUBLIC EVENT HANDLERS & AUTO-UPDATE
  // =========================================================================
  window.onMilestoneChange = function (milestoneVal) {
    computeStandingsForMilestone(milestoneVal);
  };

  window.syncLatestStandings = function () {
    var milestoneSelect = document.getElementById('milestoneSelect');
    if (milestoneSelect) milestoneSelect.value = 'LATEST';
    computeStandingsForMilestone('LATEST');
  };

  window.refreshRollingStandings = function () {
    var milestoneSelect = document.getElementById('milestoneSelect');
    var curVal = milestoneSelect ? milestoneSelect.value : 'LATEST';
    computeStandingsForMilestone(curVal);
  };

  // Automatically compute and sync on page load and lifecycle events so standings are always 100% up-to-date
  function triggerAutoUpdate() {
    var milestoneSelect = document.getElementById('milestoneSelect');
    var curVal = milestoneSelect ? milestoneSelect.value : 'LATEST';
    computeStandingsForMilestone(curVal);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', triggerAutoUpdate);
  } else {
    triggerAutoUpdate();
  }

  window.addEventListener('pageshow', triggerAutoUpdate);
  window.addEventListener('focus', triggerAutoUpdate);
  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'visible') {
      triggerAutoUpdate();
    }
  });
  window.addEventListener('storage', triggerAutoUpdate);

})();
