/**
 * TOURMA - UNIFIED TEAM PROFILE SCRIPT (team-profile.js)
 * Synchronizes Server + LocalStorage sub-tournament placements and BXH series stats.
 * Computes exact live team stats, W-L records, achievements, and tournament performance.
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
    if (c.length >= 4 && cTarget.length >= 4 && (c.indexOf(cTarget) !== -1 || cTarget.indexOf(c) !== -1)) {
      return true;
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

  function getFormatShortCode(fmt) {
    if (!fmt) return 'SE';
    var f = String(fmt).toUpperCase().trim();
    if (f.indexOf('DOUBLE') !== -1 || f === 'DE') return 'DE';
    if (f.indexOf('ROUND') !== -1 || f === 'RR') return 'RR';
    if (f.indexOf('GROUP') !== -1 || f === 'GS') return 'GS';
    if (f.indexOf('SWISS') !== -1 || f === 'SW') return 'SW';
    return 'SE';
  }

  function getTournamentFinalStageUrl(t) {
    if (!t || !t.id) return '#';
    var isMulti = t.isMultiStage || (t.tournamentType === 'MULTI_STAGE');
    var s1 = t.stage1Format || t.format || 'SINGLE_ELIMINATION';
    var s2 = t.stage2Format || 'SINGLE_ELIMINATION';
    var finalFormat = isMulti ? s2 : s1;
    var f = String(finalFormat).toUpperCase().trim();

    var page = 'single-elimination.jsp';
    if (f.indexOf('DOUBLE') !== -1 || f === 'DE') page = 'double-elimination.jsp';
    else if (f.indexOf('ROUND') !== -1 || f === 'RR') page = 'round-robin.jsp';
    else if (f.indexOf('GROUP') !== -1 || f === 'GS') page = 'group-stage.jsp';
    else if (f.indexOf('SWISS') !== -1 || f === 'SW') page = 'swiss-stage.jsp';

    var ctx = (typeof window.appContextPath !== 'undefined' && window.appContextPath) ? window.appContextPath : '';
    var sId = (typeof window.seriesIdVal !== 'undefined' && window.seriesIdVal) ? window.seriesIdVal : '';
    var seriesParam = sId ? ('&seriesId=' + encodeURIComponent(sId)) : '';
    var stageParam = isMulti ? '&stage=2' : '';
    return ctx + '/common/' + page + '?id=' + encodeURIComponent(t.id) + stageParam + seriesParam;
  }

  function resolvePointsFromConfig(ptsCfg, positionKey, altKey) {
    if (!ptsCfg || typeof ptsCfg !== 'object') return 0;

    if (ptsCfg[positionKey] !== undefined) {
      return parseInt(ptsCfg[positionKey], 10) || 0;
    }
    if (altKey && ptsCfg[altKey] !== undefined) {
      return parseInt(ptsCfg[altKey], 10) || 0;
    }
    if (ptsCfg[String(positionKey)] !== undefined) {
      return parseInt(ptsCfg[String(positionKey)], 10) || 0;
    }

    if (positionKey === "stage1_eliminated" || altKey === "stage1_eliminated") {
      if (ptsCfg["stage1_eliminated"] !== undefined) {
        return parseInt(ptsCfg["stage1_eliminated"], 10) || 0;
      }
    }

    if ((positionKey === "1" || positionKey === 1) && ptsCfg["champPoints"] !== undefined) {
      return parseInt(ptsCfg["champPoints"], 10) || 0;
    }

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
        if (ptsCfg[String(pMin)] !== undefined) return parseInt(ptsCfg[String(pMin)], 10) || 0;
      } else if (pMin >= 9 && pMin <= 16) {
        if (ptsCfg["9-16"] !== undefined) return parseInt(ptsCfg["9-16"], 10) || 0;
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
      var key = prefixList[i] + id;
      var val = localStorage.getItem(key);
      if (val) {
        storageDataCache[cacheKey] = val;
        return val;
      }
    }
    var allKeys = Object.keys(localStorage);
    for (var j = 0; j < allKeys.length; j++) {
      var k = allKeys[j];
      for (var p = 0; p < prefixList.length; p++) {
        if (k.indexOf(prefixList[p]) === 0 && (k.indexOf(id) !== -1 || k.slice(-id.length) === id)) {
          var v = localStorage.getItem(k);
          if (v) {
            storageDataCache[cacheKey] = v;
            return v;
          }
        }
      }
    }
    storageDataCache[cacheKey] = null;
    return null;
  }

  var partnerKeyCache = {};

  function calculateAndSyncTeamProfile() {
    var targetTeamName = (window.profileTeamName || '').trim();
    if (!targetTeamName) return;
    var targetKey = targetTeamName.toLowerCase();

    var subTourneys = window.seriesSubTournaments || [];
    var phaseSize = window.seriesPhaseSize || 3;
    var totalTourneys = subTourneys.length;
    var activeStartIndex = Math.max(0, totalTourneys - phaseSize);
    var droppedIndex = totalTourneys - phaseSize - 1;

    // 1. Build Partner Team Data Map (key: normalized lowercase team name)
    var teamDataMap = {};
    var partnerTeams = window.seriesPartners || [];
    partnerTeams.forEach(function (p) {
      if (p && p.name) {
        var k = p.name.trim().toLowerCase();
        teamDataMap[k] = {
          name: p.name.trim(),
          totalPts: 0,
          lastPts: 0,
          expiredPts: 0,
          droppedPts: 0,
          activeTourneys: 0
        };
      }
    });
    if (!teamDataMap[targetKey]) {
      teamDataMap[targetKey] = {
        name: targetTeamName,
        totalPts: 0,
        lastPts: 0,
        expiredPts: 0,
        droppedPts: 0,
        activeTourneys: 0
      };
    }

    // Target team detailed statistics
    var targetWins = 0;
    var targetLosses = 0;
    var targetPlayedTourneys = 0;
    var champCount = 0;
    var runnerUpCount = 0;
    var semiCount = 0;
    var quarterCount = 0;
    var highestBXHRank = 0;
    var partnerPointsPerTourney = [];

    var championTourneysList = [];
    var tourneyPerformances = [];

    // Helper to find partner key with memoization
    function findPartnerKey(name) {
      if (!name) return null;
      var raw = String(name).trim();
      if (partnerKeyCache[raw] !== undefined) return partnerKeyCache[raw];
      var k = raw.toLowerCase();
      if (teamDataMap[k]) {
        partnerKeyCache[raw] = k;
        return k;
      }
      var matchK = null;
      var mapKeys = Object.keys(teamDataMap);
      for (var idx = 0; idx < mapKeys.length; idx++) {
        var pk = mapKeys[idx];
        if (isTeamSelf(pk, raw)) {
          matchK = pk;
          break;
        }
      }
      partnerKeyCache[raw] = matchK;
      return matchK;
    }

    // Process each sub-tournament in chronological order
    subTourneys.forEach(function (t, tIdx) {
      var isActiveWindow = (tIdx >= activeStartIndex);
      var isLatestTourney = (tIdx === totalTourneys - 1);
      var isDroppedTourney = (tIdx === droppedIndex);

      var localPtsCfg = {};
      try {
        var localPtsRaw = getStorageData(['tourma_points_config_'], t.id);
        if (localPtsRaw) {
          localPtsCfg = JSON.parse(localPtsRaw) || {};
        }
      } catch (e) {}

      var ptsCfg = Object.assign({}, t.pointsConfig || {}, localPtsCfg || {});
      var tourneyTeamPoints = {}; // key -> points
      var tourneyTeamPositions = {}; // key -> posKey

      function awardTeamPoints(name, positionKey, altKey) {
        if (!name) return;
        var key = findPartnerKey(name);
        if (!key) return;
        var targetEntry = teamDataMap[key];
        if (!targetEntry) return;

        if (!tourneyTeamPositions[key] || positionKey === "1" || positionKey === "2" || positionKey === "3-4" || positionKey === "5-8") {
          tourneyTeamPositions[key] = positionKey;
        }

        var pts = resolvePointsFromConfig(ptsCfg, positionKey, altKey);
        var prevPts = tourneyTeamPoints[key] || 0;
        if (pts > prevPts || tourneyTeamPoints[key] === undefined) {
          var diff = Math.max(0, pts - prevPts);
          tourneyTeamPoints[key] = pts;
          tourneyTeamPositions[key] = positionKey;

          if (isActiveWindow) {
            targetEntry.totalPts += diff;
            if (prevPts === 0 && pts > 0) targetEntry.activeTourneys += 1;
            if (isLatestTourney) {
              targetEntry.lastPts = pts;
            }
          } else {
            targetEntry.expiredPts += diff;
          }

          if (isDroppedTourney) {
            targetEntry.droppedPts = pts;
          }
        }
      }

      var tourneyWinsThisTeam = 0;
      var tourneyLossesThisTeam = 0;
      var playedThisTourney = false;
      var targetStage1Round = null;
      var targetStage2Round = null;
      var stage1SwissRank = 0;
      var stage1RRRank = 0;

      function recordMatchStats(m) {
        if (!m) return;
        var t1Name = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
        var t2Name = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);
        var isT1 = isTeamSelf(t1Name, targetKey);
        var isT2 = isTeamSelf(t2Name, targetKey);
        if (isT1 || isT2) {
          playedThisTourney = true;
          var res = resolveWinnerAndLoser(m);
          if (res.winner) {
            if (isTeamSelf(res.winner, targetKey)) {
              tourneyWinsThisTeam++;
            } else {
              tourneyLossesThisTeam++;
            }
          }
        }
      }

      // =========================================================================
      // DETERMINE TOURNAMENT FORMAT & STRUCTURE
      // =========================================================================
      var rawMultiCfg = getStorageData(['tourma_multi_config_'], t.id);
      var multiConfig = null;
      if (rawMultiCfg) {
        try { multiConfig = JSON.parse(rawMultiCfg); } catch (e) {}
      }

      var isMultiStage = !!(multiConfig && multiConfig.stage2Format) || (t.isMultiStage === true) || (t.tournamentType === 'MULTI_STAGE');
      var s1Format = (multiConfig && multiConfig.stage1Format) ? multiConfig.stage1Format.toUpperCase() : (t.stage1Format ? t.stage1Format.toUpperCase() : (t.format || 'SINGLE_ELIMINATION'));
      var s2Format = (multiConfig && multiConfig.stage2Format) ? multiConfig.stage2Format.toUpperCase() : (t.stage2Format ? t.stage2Format.toUpperCase() : 'SINGLE_ELIMINATION');

      var fmtLabel = getFormatShortCode(s1Format);
      if (isMultiStage) {
        fmtLabel = getFormatShortCode(s1Format) + " ➔ " + getFormatShortCode(s2Format);
      }

      // =========================================================================
      // STEP 1: PARSE STAGE 1
      // =========================================================================

      // 1. Stage 1 Swiss System / Swiss Lite
      if (s1Format.indexOf('SWISS') !== -1 || (!isMultiStage && (t.format || '').indexOf('SWISS') !== -1)) {
        var rawSwiss = getStorageData(['tourma_swiss_matches_'], t.id);
        if (rawSwiss) {
          try {
            var swissMatches = JSON.parse(rawSwiss);
            var sTeamsList = [];
            try {
              sTeamsList = JSON.parse(getStorageData(['tourma_teams_'], t.id)) || [];
            } catch (e) {}

            var swissStats = {};
            if (Array.isArray(sTeamsList) && sTeamsList.length > 0) {
              sTeamsList.forEach(function (tm) {
                var tmName = extractName(tm);
                if (tmName) {
                  swissStats[tmName.toLowerCase().trim()] = { name: tmName, wins: 0, losses: 0, draws: 0, points: 0, scoresFor: 0, scoresAgainst: 0, diff: 0, buchholz: 0, opponents: [] };
                }
              });
            }

            if (swissMatches && typeof swissMatches === 'object') {
              Object.keys(swissMatches).forEach(function (mk) {
                var m = swissMatches[mk];
                if (!m) return;
                recordMatchStats(m);

                var t1Name = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
                var t2Name = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);
                if (!t1Name) return;

                var k1 = t1Name.toLowerCase().trim();
                if (!swissStats[k1]) {
                  swissStats[k1] = { name: t1Name, wins: 0, losses: 0, draws: 0, points: 0, scoresFor: 0, scoresAgainst: 0, diff: 0, buchholz: 0, opponents: [] };
                }

                if (t2Name) {
                  var k2 = t2Name.toLowerCase().trim();
                  if (!swissStats[k2]) {
                    swissStats[k2] = { name: t2Name, wins: 0, losses: 0, draws: 0, points: 0, scoresFor: 0, scoresAgainst: 0, diff: 0, buchholz: 0, opponents: [] };
                  }

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

            swissList.forEach(function (st, idx) {
              if (isTeamSelf(st.name, targetKey)) {
                stage1SwissRank = idx + 1;
              }
              if (!isMultiStage) {
                awardTeamPoints(st.name, String(idx + 1));
                if (isTeamSelf(st.name, targetKey)) {
                  if (idx === 0) targetStage1Round = "Vô Địch";
                  else if (idx === 1) targetStage1Round = "Á Quân";
                  else if (idx < 4) targetStage1Round = "Bán Kết";
                  else if (idx < 8) targetStage1Round = "Tứ Kết";
                  else targetStage1Round = "Round of " + (idx + 1 <= 16 ? 16 : 32);
                }
              } else {
                if (st.losses >= 3 || st.wins < 3) {
                  var recKey = "swiss_" + st.wins + "-" + st.losses;
                  var altRec = (st.wins === 2) ? "swiss_2-3" : ((st.wins === 1) ? "swiss_1-3" : "swiss_0-3");
                  awardTeamPoints(st.name, recKey, altRec);
                  if (isTeamSelf(st.name, targetKey)) {
                    targetStage1Round = "Swiss " + st.wins + "-" + st.losses;
                  }
                }
              }
            });
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
              try {
                teamsList = JSON.parse(getStorageData(['tourma_teams_'], t.id)) || [];
              } catch (e) {}
            }

            var rrStats = {};
            teamsList.forEach(function (tm) {
              var tmN = extractName(tm);
              if (tmN) rrStats[tmN.toLowerCase().trim()] = { name: tmN, pts: 0, gf: 0, ga: 0, diff: 0 };
            });

            if (rrMatchesMap && typeof rrMatchesMap === 'object') {
              Object.keys(rrMatchesMap).forEach(function (mk) {
                var m = rrMatchesMap[mk];
                if (!m) return;
                recordMatchStats(m);

                var t1N = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
                var t2N = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);
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
                if (isTeamSelf(name, targetKey)) {
                  stage1RRRank = idx + 1;
                }
                if (!isMultiStage) {
                  awardTeamPoints(name, String(idx + 1));
                  if (isTeamSelf(name, targetKey)) {
                    if (idx === 0) targetStage1Round = "Vô Địch";
                    else if (idx === 1) targetStage1Round = "Á Quân";
                    else if (idx < 4) targetStage1Round = "Bán Kết";
                    else if (idx < 8) targetStage1Round = "Tứ Kết";
                    else targetStage1Round = "Hạng " + (idx + 1) + " Vòng Bảng";
                  }
                } else {
                  awardTeamPoints(name, "stage1_eliminated", String(idx + 1));
                  if (isTeamSelf(name, targetKey)) {
                    targetStage1Round = "Vòng Bảng";
                  }
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

            if (gMatches && typeof gMatches === 'object') {
              Object.keys(gMatches).forEach(function (mk) {
                recordMatchStats(gMatches[mk]);
              });
            }

            if (gAssignments && typeof gAssignments === 'object') {
              Object.keys(gAssignments).forEach(function (grpKey) {
                var grpTeams = gAssignments[grpKey] || [];
                if (Array.isArray(grpTeams) && grpTeams.length > 0) {
                  var grpStats = {};
                  grpTeams.forEach(function (tm) {
                    var tName = extractName(tm);
                    if (tName) {
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

                      if (isTeamSelf(st.name, targetKey)) {
                        if (rNum === 1) targetStage1Round = "Vô Địch";
                        else if (rNum === 2) targetStage1Round = "Á Quân";
                        else if (rNum <= 4) targetStage1Round = "Bán Kết";
                        else targetStage1Round = "Vòng Bảng";
                      }
                    });
                  } else {
                    grpList.forEach(function (st) {
                      awardTeamPoints(st.name, "stage1_eliminated");
                      if (isTeamSelf(st.name, targetKey)) {
                        targetStage1Round = "Vòng Bảng";
                      }
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
                var roundName = "";
                if (distFromFinal === 0) {
                  posKey = "2";
                  roundName = "Á Quân";
                } else if (distFromFinal === 1) {
                  posKey = "3-4";
                  roundName = "Bán Kết";
                } else if (distFromFinal === 2) {
                  posKey = "5-8";
                  roundName = "Tứ Kết";
                } else {
                  var teamsInRound = Math.pow(2, distFromFinal + 1);
                  var sP = Math.pow(2, distFromFinal) + 1;
                  var eP = teamsInRound;
                  posKey = sP + "-" + eP;
                  roundName = "Round of " + teamsInRound;
                }

                roundMatches.forEach(function (m) {
                  if (!m) return;
                  recordMatchStats(m);

                  if (m.winnerId || m.winner || m.status === 'COMPLETED' || m.status === 'DONE') {
                    var mRes = resolveWinnerAndLoser(m);
                    if (mRes.loser) {
                      awardTeamPoints(mRes.loser, posKey, "stage1_eliminated");
                      if (isTeamSelf(mRes.loser, targetKey)) {
                        targetStage1Round = roundName;
                      }
                    }
                    if (mRes.winner) {
                      if (distFromFinal === 0 && !isMultiStage) {
                        awardTeamPoints(mRes.winner, "1");
                        if (isTeamSelf(mRes.winner, targetKey)) {
                          targetStage1Round = "Vô Địch";
                        }
                      } else if (isMultiStage && (rIdx === totalRounds - 1 || m.isCutMatch)) {
                        if (isTeamSelf(mRes.winner, targetKey)) {
                          targetStage1Round = "Vòng 2";
                        }
                      }
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
              } catch(e) {}
              if (!totalTeams || totalTeams < 2) totalTeams = 16;
              var totalFullRounds = Math.round(Math.log2(totalTeams));

              // Find final match
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
                recordMatchStats(m);

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
                  var roundName2 = "";
                  if (roundDiff === 0) {
                    posKey2 = "2";
                    roundName2 = "Á Quân";
                  } else if (roundDiff === 1) {
                    posKey2 = "3-4";
                    roundName2 = "Bán Kết";
                  } else if (roundDiff === 2) {
                    posKey2 = "5-8";
                    roundName2 = "Tứ Kết";
                  } else {
                    var teamsInRound2 = Math.pow(2, roundDiff + 1);
                    var sP2 = Math.pow(2, roundDiff) + 1;
                    var eP2 = teamsInRound2;
                    posKey2 = sP2 + "-" + eP2;
                    roundName2 = "Round of " + teamsInRound2;
                  }

                  if (mRes.loser) {
                    awardTeamPoints(mRes.loser, posKey2, "stage1_eliminated");
                    if (isTeamSelf(mRes.loser, targetKey)) {
                      targetStage1Round = roundName2;
                    }
                  }
                  if (mRes.winner) {
                    if (roundDiff === 0 && !isMultiStage) {
                      awardTeamPoints(mRes.winner, "1");
                      if (isTeamSelf(mRes.winner, targetKey)) {
                        targetStage1Round = "Vô Địch";
                      }
                    } else if (isMultiStage && (m.isCutMatch || roundDiff === 0)) {
                      if (isTeamSelf(mRes.winner, targetKey)) {
                        targetStage1Round = "Vòng 2";
                      }
                    }
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
              var totalUbR = upperRounds.length;
              upperRounds.forEach(function (ur, uIdx) {
                if (ur && ur.matches) {
                  ur.matches.forEach(function (m) {
                    recordMatchStats(m);
                    if (isMultiStage && (uIdx === totalUbR - 1 || (m && m.isCutMatch))) {
                      if (m && (m.winnerId || m.winner || m.status === 'COMPLETED' || m.status === 'DONE')) {
                        var resUB = resolveWinnerAndLoser(m);
                        if (resUB.winner && isTeamSelf(resUB.winner, targetKey)) {
                          targetStage1Round = "Vòng 2";
                        }
                      }
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

                var roundName = "Loser's Round " + lrNum;
                if (isLbCut) {
                  roundName = "Loser's Qualification";
                } else if (!isMultiStage && rawTitle.toLowerCase().indexOf("lb final") !== -1) {
                  roundName = "Bán Kết";
                }

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
                    recordMatchStats(m);
                    if (m && (m.winnerId || m.winner || m.status === 'COMPLETED' || m.status === 'DONE')) {
                      var res = resolveWinnerAndLoser(m);
                      if (res.loser) {
                        awardTeamPoints(res.loser, posKey, "stage1_eliminated");
                        if (isTeamSelf(res.loser, targetKey)) {
                          targetStage1Round = roundName;
                        }
                      }
                      if (res.winner && isLbCut) {
                        if (isTeamSelf(res.winner, targetKey)) {
                          targetStage1Round = "Vòng 2";
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
              recordMatchStats(gfFinal);
              if (gfFinal && (gfFinal.winnerId || gfFinal.winner || gfFinal.status === 'COMPLETED' || gfFinal.status === 'DONE')) {
                var resGF = resolveWinnerAndLoser(gfFinal);
                if (resGF.winner) {
                  awardTeamPoints(resGF.winner, "1");
                  if (isTeamSelf(resGF.winner, targetKey)) {
                    targetStage1Round = "Vô Địch";
                  }
                }
                if (resGF.loser) {
                  awardTeamPoints(resGF.loser, "2");
                  if (isTeamSelf(resGF.loser, targetKey)) {
                    targetStage1Round = "Á Quân";
                  }
                }
              }
            }
          } catch (e) {}
        }
      }

      // =========================================================================
      // STEP 2: PARSE STAGE 2 (If Multi-Stage)
      // =========================================================================
      if (isMultiStage) {

        // A. Stage 2 Round Robin
        if (s2Format === 'ROUND_ROBIN') {
          var rawRRS2 = getStorageData(['tourma_rr_matches_stage2_'], t.id);
          var teamsListS2 = [];
          try {
            teamsListS2 = JSON.parse(getStorageData(['tourma_stage2_teams_'], t.id)) || [];
          } catch (e) {}

          var rrStatsS2 = {};
          teamsListS2.forEach(function (tm) {
            var tmN = extractName(tm);
            if (tmN) rrStatsS2[tmN.toLowerCase().trim()] = { name: tmN, pts: 0, gf: 0, ga: 0, diff: 0 };
          });

          var rrMatchesMapS2 = {};
          if (rawRRS2) {
            try {
              var rrDataS2 = JSON.parse(rawRRS2);
              rrMatchesMapS2 = rrDataS2.matchesMap || rrDataS2;
            } catch (e) {}
          }

          var rawMatchesS2 = getStorageData(['tourma_matches_stage2_'], t.id);
          if (rawMatchesS2) {
            try {
              var parsedM = JSON.parse(rawMatchesS2);
              if (parsedM && typeof parsedM === 'object') {
                rrMatchesMapS2 = Object.assign({}, rrMatchesMapS2, parsedM);
              }
            } catch (e) {}
          }

          if (rrMatchesMapS2 && typeof rrMatchesMapS2 === 'object') {
            Object.keys(rrMatchesMapS2).forEach(function (mk) {
              var m = rrMatchesMapS2[mk];
              if (!m) return;
              recordMatchStats(m);

              var t1N = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
              var t2N = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);
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
              var name = row.name;
              var rNum = idx + 1;
              awardTeamPoints(name, String(rNum), (rNum === 1 ? "1" : (rNum === 2 ? "2" : String(rNum))));
              if (isTeamSelf(name, targetKey)) {
                if (rNum === 1) targetStage2Round = "Vô Địch";
                else if (rNum === 2) targetStage2Round = "Á Quân";
                else if (rNum <= 4) targetStage2Round = "Bán Kết";
                else if (rNum <= 8) targetStage2Round = "Tứ Kết";
                else targetStage2Round = "Round of " + (rNum <= 16 ? 16 : 32);
              }
            });
          }
        }

        // B. Stage 2 Double Elimination
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
                    ur.matches.forEach(function (m) { recordMatchStats(m); });
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

                  var roundNameS2 = "Loser's Round " + lrNum2;
                  if (rawTitle2.toLowerCase().indexOf("lb final") !== -1 || offset2 === 0) {
                    roundNameS2 = "Bán Kết";
                  }

                  var posKey2 = (offset2 === 0) ? "3" : ((offset2 === 1) ? "4" : "5-8");
                  if (offset2 >= 2) {
                    var k2 = Math.floor((offset2 - 2) / 2);
                    var tStart = Math.pow(2, k2 + 2) + 1;
                    var hSize = Math.pow(2, k2 + 1);
                    var tEnd = Math.pow(2, k2 + 3);
                    posKey2 = (offset2 % 2 === 0) ? (tStart + "-" + (tStart + hSize - 1)) : ((tStart + hSize) + "-" + tEnd);
                  }

                  if (roundObj2 && roundObj2.matches) {
                    roundObj2.matches.forEach(function (m) {
                      recordMatchStats(m);
                      if (m && (m.winnerId || m.winner || m.status === 'COMPLETED' || m.status === 'DONE')) {
                        var res2 = resolveWinnerAndLoser(m);
                        if (res2.loser) {
                          awardTeamPoints(res2.loser, posKey2, "s2_lb_r" + lrNum2);
                          if (isTeamSelf(res2.loser, targetKey)) {
                            targetStage2Round = roundNameS2;
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
                recordMatchStats(gfFinalS2);
                if (gfFinalS2 && (gfFinalS2.winnerId || gfFinalS2.winner || gfFinalS2.status === 'COMPLETED' || gfFinalS2.status === 'DONE')) {
                  var resGFS2 = resolveWinnerAndLoser(gfFinalS2);
                  if (resGFS2.winner) {
                    awardTeamPoints(resGFS2.winner, "1");
                    if (isTeamSelf(resGFS2.winner, targetKey)) {
                      targetStage2Round = "Vô Địch";
                    }
                  }
                  if (resGFS2.loser) {
                    awardTeamPoints(resGFS2.loser, "2");
                    if (isTeamSelf(resGFS2.loser, targetKey)) {
                      targetStage2Round = "Á Quân";
                    }
                  }
                }
              }
            } catch (e) {}
          }
        }

        // C. Stage 2 Single Elimination
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
                  var roundNameS2 = "";
                  if (distS2 === 0) {
                    posKeyS2 = "2";
                    roundNameS2 = "Á Quân";
                  } else if (distS2 === 1) {
                    posKeyS2 = "3-4";
                    roundNameS2 = "Bán Kết";
                  } else if (distS2 === 2) {
                    posKeyS2 = "5-8";
                    roundNameS2 = "Tứ Kết";
                  } else {
                    var teamsInRS2 = Math.pow(2, distS2 + 1);
                    var sPS2 = Math.pow(2, distS2) + 1;
                    var ePS2 = teamsInRS2;
                    posKeyS2 = sPS2 + "-" + ePS2;
                    roundNameS2 = "Round of " + teamsInRS2;
                  }

                  roundMatchesS2.forEach(function (m) {
                    if (!m) return;
                    recordMatchStats(m);

                    if (m.winnerId || m.winner || m.status === 'COMPLETED' || m.status === 'DONE') {
                      var resS2 = resolveWinnerAndLoser(m);
                      if (distS2 === 0 && resS2.winner) {
                        awardTeamPoints(resS2.winner, "1");
                        if (isTeamSelf(resS2.winner, targetKey)) {
                          targetStage2Round = "Vô Địch";
                        }
                      }
                      if (resS2.loser) {
                        awardTeamPoints(resS2.loser, posKeyS2);
                        if (isTeamSelf(resS2.loser, targetKey)) {
                          targetStage2Round = roundNameS2;
                        }
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
                } catch(e) {}
                if (!s2TeamsCount || s2TeamsCount < 2) s2TeamsCount = 4;
                var totalS2FullRounds = Math.round(Math.log2(s2TeamsCount));

                // Find final match in stage 2
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
                  recordMatchStats(m);

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
                    var roundNameS2_2 = "";
                    if (roundDiffS2 === 0) {
                      posKeyS2_2 = "2";
                      roundNameS2_2 = "Á Quân";
                    } else if (roundDiffS2 === 1) {
                      posKeyS2_2 = "3-4";
                      roundNameS2_2 = "Bán Kết";
                    } else if (roundDiffS2 === 2) {
                      posKeyS2_2 = "5-8";
                      roundNameS2_2 = "Tứ Kết";
                    } else {
                      var teamsInRoundS2 = Math.pow(2, roundDiffS2 + 1);
                      var sPos = Math.floor(teamsInRoundS2 / 2) + 1;
                      var ePos = teamsInRoundS2;
                      posKeyS2_2 = sPos + "-" + ePos;
                      roundNameS2_2 = "Round of " + teamsInRoundS2;
                    }

                    if (roundDiffS2 === 0 && resS2.winner) {
                      awardTeamPoints(resS2.winner, "1");
                      if (isTeamSelf(resS2.winner, targetKey)) {
                        targetStage2Round = "Vô Địch";
                      }
                    }
                    if (resS2.loser) {
                      awardTeamPoints(resS2.loser, posKeyS2_2);
                      if (isTeamSelf(resS2.loser, targetKey)) {
                        targetStage2Round = roundNameS2_2;
                      }
                    }
                  }
                });
              }
            } catch (e) {}
          }
        }
      }

      // =========================================================================
      // STEP 3: CHAMPION OVERRIDE (e.g. tourma_champion_TOURNEY_...)
      // =========================================================================
      var rawChamp = getStorageData(['tourma_champion_'], t.id);
      var isExplicitChamp = false;
      if (rawChamp) {
        try {
          var cName = extractName(rawChamp) || (typeof rawChamp === 'string' ? rawChamp.trim() : null);
          if (cName) {
            awardTeamPoints(cName, "1");
            if (isTeamSelf(cName, targetKey)) {
              isExplicitChamp = true;
              targetStage2Round = "Vô Địch";
            }
          }
        } catch (e) {}
      }

      // =========================================================================
      // STEP 4: RECORD TARGET TEAM'S PERFORMANCE FOR THIS TOURNAMENT
      // =========================================================================
      var targetEntryKey = findPartnerKey(targetTeamName);
      var ptsEarned = targetEntryKey ? (tourneyTeamPoints[targetEntryKey] || 0) : 0;
      var posKeyEarned = targetEntryKey ? (tourneyTeamPositions[targetEntryKey] || null) : null;

      if (isExplicitChamp) {
        posKeyEarned = "1";
        targetStage2Round = "Vô Địch";
        if (ptsEarned <= 0) {
          ptsEarned = resolvePointsFromConfig(ptsCfg, "1");
        }
      }

      if (playedThisTourney || ptsEarned > 0 || isExplicitChamp || posKeyEarned !== null || targetStage1Round || targetStage2Round) {
        targetPlayedTourneys++;
        targetWins += tourneyWinsThisTeam;
        targetLosses += tourneyLossesThisTeam;

        var achievement = "Round of 16";
        if (isExplicitChamp || posKeyEarned === "1" || targetStage2Round === "Vô Địch") {
          achievement = "Vô Địch";
        } else if (targetStage2Round) {
          achievement = targetStage2Round;
        } else if (targetStage1Round) {
          achievement = targetStage1Round;
        } else if (posKeyEarned === "2") {
          achievement = "Á Quân";
        } else if (posKeyEarned === "3-4" || posKeyEarned === "3" || posKeyEarned === "4") {
          achievement = "Bán Kết";
        } else if (posKeyEarned === "5-8") {
          achievement = "Tứ Kết";
        } else if (posKeyEarned === "s1_lb_cut") {
          achievement = "Loser's Qualification";
        } else if (posKeyEarned === "9-16") {
          achievement = "Round of 16";
        } else if (posKeyEarned === "17-32") {
          achievement = "Round of 32";
        } else if (posKeyEarned === "33-64") {
          achievement = "Round of 64";
        } else if (posKeyEarned === "65-128") {
          achievement = "Round of 128";
        } else {
          if (tourneyWinsThisTeam >= 4 && tourneyLossesThisTeam === 0) {
            achievement = "Vô Địch";
          } else if (tourneyWinsThisTeam >= 3) {
            achievement = "Á Quân";
          } else if (tourneyWinsThisTeam >= 2) {
            achievement = "Bán Kết";
          } else if (tourneyWinsThisTeam >= 1) {
            achievement = "Tứ Kết";
          } else {
            achievement = "Round of 16";
          }
        }

        var posNum = 0;
        // Calculate exact rank/posNum from achievement & posKeyEarned
        if (achievement === "Vô Địch" || isExplicitChamp || posKeyEarned === "1") {
          champCount++;
          semiCount++;
          quarterCount++;
          posNum = 1;
          championTourneysList.push({
            id: t.id,
            name: t.name,
            tier: (t.tierName || "A").toUpperCase()
          });
        } else if (achievement === "Á Quân" || posKeyEarned === "2") {
          runnerUpCount++;
          semiCount++;
          quarterCount++;
          posNum = 2;
        } else if (achievement === "Bán Kết" || posKeyEarned === "3-4" || posKeyEarned === "3" || posKeyEarned === "4") {
          semiCount++;
          quarterCount++;
          posNum = (posKeyEarned === "3") ? 3 : 4;
        } else if (achievement === "Tứ Kết" || posKeyEarned === "5-8") {
          quarterCount++;
          posNum = 8;
        } else if (achievement === "Loser's Qualification" || posKeyEarned === "s1_lb_cut") {
          posNum = 8;
        } else if (achievement === "Loser's Round 4") {
          posNum = 6;
        } else if (achievement === "Loser's Round 3") {
          posNum = 8;
        } else if (achievement === "Loser's Round 2") {
          posNum = 12;
        } else if (achievement === "Loser's Round 1") {
          posNum = 16;
        } else if (achievement.indexOf("Round of ") === 0) {
          var rOf = parseInt(achievement.replace("Round of ", ""), 10);
          posNum = (!isNaN(rOf) && rOf > 0) ? rOf : 16;
        } else if (achievement.indexOf("Swiss ") === 0) {
          if (stage1SwissRank > 0) posNum = stage1SwissRank;
          else if (achievement === "Swiss 3-0") posNum = 1;
          else if (achievement === "Swiss 3-1") posNum = 4;
          else if (achievement === "Swiss 3-2") posNum = 8;
          else if (achievement === "Swiss 2-3") posNum = 11;
          else if (achievement === "Swiss 1-3") posNum = 14;
          else if (achievement === "Swiss 0-3") posNum = 16;
          else posNum = 16;
        } else if (achievement === "Vòng 2") {
          posNum = 4;
        } else if (stage1RRRank > 0) {
          posNum = stage1RRRank;
        } else if (posKeyEarned && !isNaN(parseInt(posKeyEarned, 10)) && parseInt(posKeyEarned, 10) > 0) {
          posNum = parseInt(posKeyEarned, 10);
        } else {
          posNum = 16;
        }

        partnerPointsPerTourney[tIdx] = tourneyTeamPoints;

        tourneyPerformances.push({
          id: t.id,
          name: t.name,
          tier: (t.tierName || "A").toUpperCase(),
          format: fmtLabel,
          achievement: achievement,
          pointsEarned: ptsEarned,
          stt: targetPlayedTourneys
        });
      }
    });

    // =========================================================================
    // 5. DETERMINE CURRENT LEADERBOARD RANKING, HIGHEST RANK & ACTIVE POINTS
    // =========================================================================
    var partnerList = Object.values(teamDataMap);
    partnerList.sort(function (a, b) {
      if (b.totalPts !== a.totalPts) return b.totalPts - a.totalPts;
      if (b.lastPts !== a.lastPts) return b.lastPts - a.lastPts;
      return a.name.localeCompare(b.name);
    });

    var currentRank = 0;
    var currentActivePoints = 0;
    var totalAccumPoints = 0;

    for (var r = 0; r < partnerList.length; r++) {
      if (isTeamSelf(partnerList[r].name, targetKey)) {
        currentRank = r + 1;
        currentActivePoints = partnerList[r].totalPts;
        break;
      }
    }

    // Calculate highest rank achieved on the BXH across all series milestones
    var highestBXHRank = 0;
    var highestBXHTourneyName = "";
    var highestBXHTourneyUrl = "";
    var highestBXHTourneyTier = "";

    for (var step = 0; step < totalTourneys; step++) {
      var stepActiveStart = Math.max(0, step - phaseSize + 1);
      var stepScores = [];
      Object.keys(teamDataMap).forEach(function (pk) {
        var sumPts = 0;
        for (var k = stepActiveStart; k <= step; k++) {
          if (partnerPointsPerTourney[k] && partnerPointsPerTourney[k][pk]) {
            sumPts += partnerPointsPerTourney[k][pk];
          }
        }
        var lastTourneyPts = (partnerPointsPerTourney[step] && partnerPointsPerTourney[step][pk]) ? partnerPointsPerTourney[step][pk] : 0;
        stepScores.push({
          key: pk,
          name: teamDataMap[pk].name,
          pts: sumPts,
          lastPts: lastTourneyPts
        });
      });

      stepScores.sort(function (a, b) {
        if (b.pts !== a.pts) return b.pts - a.pts;
        if (b.lastPts !== a.lastPts) return b.lastPts - a.lastPts;
        return a.name.localeCompare(b.name);
      });

      for (var rankIdx = 0; rankIdx < stepScores.length; rankIdx++) {
        if (isTeamSelf(stepScores[rankIdx].name, targetKey)) {
          var stepRank = rankIdx + 1;
          if (stepScores[rankIdx].pts > 0) {
            if (highestBXHRank === 0 || stepRank < highestBXHRank) {
              highestBXHRank = stepRank;
              var curT = subTourneys[step];
              highestBXHTourneyName = curT ? curT.name : "";
              highestBXHTourneyTier = curT ? (curT.tierName || "A").toUpperCase() : "A";
              highestBXHTourneyUrl = curT ? getTournamentFinalStageUrl(curT) : "#";
            }
          }
          break;
        }
      }
    }

    tourneyPerformances.forEach(function (perf) {
      totalAccumPoints += perf.pointsEarned;
    });
    if (totalAccumPoints < currentActivePoints) {
      totalAccumPoints = currentActivePoints;
    }

    if (currentRank > 0 && currentActivePoints > 0) {
      if (highestBXHRank === 0 || currentRank < highestBXHRank) {
        highestBXHRank = currentRank;
        if (!highestBXHTourneyName && totalTourneys > 0) {
          var lastT = subTourneys[totalTourneys - 1];
          highestBXHTourneyName = lastT ? lastT.name : "";
          highestBXHTourneyTier = lastT ? (lastT.tierName || "A").toUpperCase() : "A";
          highestBXHTourneyUrl = lastT ? getTournamentFinalStageUrl(lastT) : "#";
        }
      }
    }

    // =========================================================================
    // 6. UPDATE DOM ELEMENTS LIVE
    // =========================================================================
    var elCurRank = document.getElementById('profCurrentRank');
    if (elCurRank) {
      elCurRank.textContent = (currentRank > 0 && currentActivePoints > 0) ? ("#" + currentRank) : "-";
      if (currentRank === 1 && currentActivePoints > 0) elCurRank.classList.add('color-gold');
      else elCurRank.classList.remove('color-gold');
    }

    var elBestRank = document.getElementById('profHighestRank');
    if (elBestRank) {
      var bestText = (highestBXHRank > 0 ? ("#" + highestBXHRank) : "-");
      if (highestBXHRank > 0 && highestBXHTourneyName) {
        bestText += ' <span class="rank-tourney-name">(<a href="' + (highestBXHTourneyUrl || '#') + '" class="rank-tourney-link" style="color: inherit; text-decoration: underline; text-underline-offset: 3px;" title="Xem giai đoạn cuối giải ' + highestBXHTourneyName + '">' + highestBXHTourneyName + '</a>)</span>';
      }
      elBestRank.innerHTML = bestText;
      if (highestBXHRank === 1 && currentActivePoints > 0) elBestRank.classList.add('color-gold');
      else elBestRank.classList.remove('color-gold');
    }

    var elCurPts = document.getElementById('profCurrentPoints');
    if (elCurPts) {
      elCurPts.innerHTML = currentActivePoints + '<span class="pts-unit">pts</span>';
    }

    var elTotPts = document.getElementById('profTotalPoints');
    if (elTotPts) {
      elTotPts.innerHTML = totalAccumPoints + '<span class="pts-unit">pts</span>';
    }

    var elWinLoss = document.getElementById('profWinLoss');
    if (elWinLoss) {
      elWinLoss.textContent = targetWins + "W - " + targetLosses + "L";
    }

    var elTourneys = document.getElementById('profTourneysPlayed');
    if (elTourneys) {
      elTourneys.innerHTML = targetPlayedTourneys + '<span class="pts-unit" style="font-size: 0.95rem; font-weight: 700;">Giải</span>';
    }

    var elChamp = document.getElementById('profChampCount');
    if (elChamp) {
      elChamp.innerHTML = champCount + '<span class="pts-unit" style="font-size: 0.95rem; font-weight: 700; color: #ffffff;">Lần</span>';
    }

    var elRunner = document.getElementById('profRunnerUpCount');
    if (elRunner) {
      elRunner.innerHTML = runnerUpCount + '<span class="pts-unit" style="font-size: 0.95rem; font-weight: 700; color: #ffffff;">Lần</span>';
    }

    var elSemi = document.getElementById('profSemiCount');
    if (elSemi) {
      elSemi.innerHTML = semiCount + '<span class="pts-unit" style="font-size: 0.95rem; font-weight: 700; color: #ffffff;">Lần</span>';
    }

    var elQuarter = document.getElementById('profQuarterCount');
    if (elQuarter) {
      elQuarter.innerHTML = quarterCount + '<span class="pts-unit" style="font-size: 0.95rem; font-weight: 700; color: #ffffff;">Lần</span>';
    }

    // Update Champion Badges Section (Only if champCount > 0)
    var elChampSection = document.getElementById('profChampionSection');
    var elChampBadges = document.getElementById('profChampionBadges');
    if (elChampSection) {
      if (champCount > 0 && championTourneysList.length > 0) {
        elChampSection.style.display = '';
        if (elChampBadges) {
          var badgesHtml = '';
          var reversedChampList = championTourneysList.slice().reverse();
          reversedChampList.forEach(function (ct) {
            var tObj = subTourneys.find(function (x) { return x.id === ct.id; }) || { id: ct.id, name: ct.name, tierName: ct.tier };
            var badgeUrl = getTournamentFinalStageUrl(tObj);
            badgesHtml += '<a href="' + badgeUrl + '" class="champion-badge-pill" style="text-decoration: none; cursor: pointer;" title="Xem giai đoạn cuối giải ' + ct.name + '">' +
              '<i class="fa-solid fa-crown"></i> ' + ct.name +
              '</a>';
          });
          elChampBadges.innerHTML = badgesHtml;
        }
      } else {
        elChampSection.style.display = 'none';
      }
    }

    // Render Special Honors & Badges using TeamBadgeEngine
    if (typeof TeamBadgeEngine !== 'undefined' && TeamBadgeEngine.renderBadges) {
      TeamBadgeEngine.renderBadges('teamBadgesContainer', targetTeamName, {
        subTournaments: subTourneys,
        championTourneys: championTourneysList,
        tourneyPerformances: tourneyPerformances,
        teamDataMap: teamDataMap,
        currentRank: currentRank,
        highestRank: highestBXHRank,
        totalAccumulatedPoints: totalAccumPoints
      });
    }

    // Update Performance Table (Most recent tournament at top, highest STT at top)
    var elTbody = document.getElementById('profPerformanceTbody');
    if (elTbody) {
      if (tourneyPerformances.length > 0) {
        var reversedPerformances = tourneyPerformances.slice().reverse();
        var rowsHtml = '';
        reversedPerformances.forEach(function (perf, idx) {
          var achClass = "muted";
          if (perf.achievement === "Vô Địch") achClass = "champ";
          else if (perf.achievement === "Á Quân") achClass = "runner-up";
          else if (perf.achievement === "Bán Kết") achClass = "semi";
          else if (perf.achievement === "Tứ Kết") achClass = "quarter";

          var sttVal = (perf.stt !== undefined && perf.stt !== null && perf.stt > 0) ? perf.stt : (reversedPerformances.length - idx);
          var tObj = subTourneys.find(function (x) { return x.id === perf.id; }) || { id: perf.id, name: perf.name };
          var tourneyUrl = getTournamentFinalStageUrl(tObj);
          var pTier = (perf.tier || (tObj && tObj.tierName) || 'A').toUpperCase();

          rowsHtml += '<tr>' +
            '<td style="font-weight: 700; color: var(--text-muted);">' + sttVal + '</td>' +
            '<td style="font-weight: 700; color: #ffffff;">' +
              '<a href="' + tourneyUrl + '" class="tourney-name-link" style="color: #ffffff; text-decoration: none; font-weight: 700; transition: color 0.18s ease;" onmouseover="this.style.color=\'#2dd4bf\'" onmouseout="this.style.color=\'#ffffff\'" title="Xem giai đoạn cuối giải ' + perf.name + '">' +
                perf.name +
              '</a>' +
              '<span class="tier-tag tier-' + pTier.toLowerCase() + '" style="margin-left: 0.55rem;">[' + pTier + ']</span>' +
            '</td>' +
            '<td style="color: var(--text-muted); font-size: 0.82rem; font-weight: 600;">' + perf.format + '</td>' +
            '<td><span class="achievement-text ' + achClass + '">' + perf.achievement + '</span></td>' +
            '<td style="text-align: right; font-weight: 800; color: #fbbf24;">+' + perf.pointsEarned + ' pts</td>' +
            '</tr>';
        });
        elTbody.innerHTML = rowsHtml;
      } else {
        elTbody.innerHTML = '<tr><td colspan="5" style="text-align: center; padding: 2.5rem; color: var(--text-muted);">Chưa có dữ liệu thành tích thi đấu giải con nào trong Series này.</td></tr>';
      }
    }
  }

  // Debounced runner
  var syncTimeout = null;
  function debouncedSync() {
    if (syncTimeout) clearTimeout(syncTimeout);
    syncTimeout = setTimeout(function () {
      calculateAndSyncTeamProfile();
    }, 20);
  }

  // Auto-run on DOM ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', calculateAndSyncTeamProfile);
  } else {
    calculateAndSyncTeamProfile();
  }

  if (typeof window !== 'undefined' && window.addEventListener) {
    window.addEventListener('storage', function () {
      storageDataCache = {};
      partnerKeyCache = {};
      debouncedSync();
    });
  }

})();
