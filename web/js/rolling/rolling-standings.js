/**
 * TOURMA - ROLLING WINDOW SERIES STANDINGS SCRIPT
 * Synchronizes Server + LocalStorage sub-tournament bracket placements
 * Enforces Rolling Window W Expiry, Deductions & Instant Table Search
 */

(function () {
  'use strict';

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

  function extractName(obj) {
    if (!obj) return null;
    if (typeof obj === 'string') {
      var s = obj.trim().replace(/^\[\d+\]\s*/, '').replace(/^\(\d+\)\s*/, '').replace(/^\d+\.\s*/, '');
      if (s && s !== 'BYE' && s !== 'TBD' && !s.startsWith('W #') && !s.startsWith('L #')) {
        return s;
      }
      return null;
    }
    if (typeof obj === 'object') {
      var name = obj.name || obj.rawName || obj.normalizedName || (obj.team && (typeof obj.team === 'object' ? (obj.team.name || obj.team.rawName) : obj.team));
      if (name && typeof name === 'string') {
        var s = name.trim().replace(/^\[\d+\]\s*/, '').replace(/^\(\d+\)\s*/, '').replace(/^\d+\.\s*/, '');
        if (s && s !== 'BYE' && s !== 'TBD' && !s.startsWith('W #') && !s.startsWith('L #')) {
          return s;
        }
      }
    }
    return null;
  }

  function resolveWinnerAndLoser(match) {
    if (!match) return { winner: null, loser: null };
    var t1Name = extractName(match.team1) || (match.team1Name ? extractName(match.team1Name) : null);
    var t2Name = extractName(match.team2) || (match.team2Name ? extractName(match.team2Name) : null);

    var wId = match.winnerId ? String(match.winnerId).trim() : null;
    var s1 = parseInt(match.team1Score !== undefined ? match.team1Score : match.score1, 10);
    var s2 = parseInt(match.team2Score !== undefined ? match.team2Score : match.score2, 10);

    if (!wId && !isNaN(s1) && !isNaN(s2) && s1 !== s2) {
      if (s1 > s2) wId = 'team1';
      else if (s2 > s1) wId = 'team2';
    }

    if (!wId) return { winner: null, loser: null };

    var winnerName = null;
    var loserName = null;

    if (wId === 'team1' || wId === '1' || (match.team1 && (match.team1.id === wId || match.team1.name === wId))) {
      winnerName = t1Name;
      loserName = t2Name;
    } else if (wId === 'team2' || wId === '2' || (match.team2 && (match.team2.id === wId || match.team2.name === wId))) {
      winnerName = t2Name;
      loserName = t1Name;
    } else {
      if (t1Name && wId.toLowerCase() === t1Name.toLowerCase()) {
        winnerName = t1Name;
        loserName = t2Name;
      } else if (t2Name && wId.toLowerCase() === t2Name.toLowerCase()) {
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
        if (ptsCfg[String(pMin)] !== undefined) return parseInt(ptsCfg[String(pMin)], 10) || 0;
      } else if (pMin >= 9 && pMin <= 16) {
        if (ptsCfg["9-16"] !== undefined) return parseInt(ptsCfg["9-16"], 10) || 0;
      } else if (pMin >= 17 && pMin <= 32) {
        if (ptsCfg["17-32"] !== undefined) return parseInt(ptsCfg["17-32"], 10) || 0;
      }
    }

    return 0;
  }

  function getStorageData(prefixList, id) {
    if (!prefixList || prefixList.length === 0) return null;
    if (id) {
      for (var i = 0; i < prefixList.length; i++) {
        var key = prefixList[i] + id;
        var val = localStorage.getItem(key);
        if (val) return val;
      }
      var allKeys = Object.keys(localStorage);
      for (var j = 0; j < allKeys.length; j++) {
        var k = allKeys[j];
        for (var p = 0; p < prefixList.length; p++) {
          if (k.indexOf(prefixList[p]) === 0 && (k.indexOf(id) !== -1 || k.slice(-id.length) === id)) {
            var v = localStorage.getItem(k);
            if (v) return v;
          }
        }
      }
    }
    return null;
  }

  function calculateAndSyncRollingStandings() {
    var standingsTable = document.getElementById('rollingStandingsTable');
    if (!standingsTable) return;

    var rows = Array.from(standingsTable.querySelectorAll('tbody tr'));
    if (rows.length === 0) return;

    // Build Partner Team Data Map (key: normalized lowercase team name)
    var teamDataMap = {};
    rows.forEach(function (row) {
      var teamNameCell = row.cells[1];
      if (teamNameCell) {
        var teamName = teamNameCell.textContent.trim();
        var key = teamName.toLowerCase();
        var initPts = 0;
        if (row.cells[3]) {
          var ptsText = row.cells[3].textContent.replace(/[^0-9]/g, '');
          if (ptsText) initPts = parseInt(ptsText, 10) || 0;
        }
        var initLastPts = 0;
        if (row.cells[4]) {
          var lastPtsText = row.cells[4].textContent.replace(/[^0-9]/g, '');
          if (lastPtsText) initLastPts = parseInt(lastPtsText, 10) || 0;
        }
        teamDataMap[key] = {
          name: teamName,
          totalPts: initPts,
          lastPts: initLastPts,
          expiredPts: 0,
          droppedPts: 0,
          activeTourneys: 0,
          row: row,
          hasLocalUpdates: false
        };
      }
    });

    var subTourneys = window.seriesSubTournaments || [];
    if (subTourneys.length === 0) return;

    var phaseSize = window.seriesPhaseSize || 3;
    var totalTourneys = subTourneys.length;
    var activeStartIndex = Math.max(0, totalTourneys - phaseSize);
    var droppedIndex = totalTourneys - phaseSize - 1;

    // Process each valid sub-tournament in chronological order
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
      var teamTourneyPoints = {}; // key -> points awarded in this tournament

      // Helper to award points to a team
      function awardTeamPoints(name, positionKey, altKey) {
        if (!name) return;
        var key = name.toLowerCase().trim();
        var targetEntry = teamDataMap[key];
        if (!targetEntry) {
          var cleanKey = key.replace(/[^a-z0-9]/g, '');
          Object.keys(teamDataMap).forEach(function (k) {
            if (targetEntry) return;
            var cK = k.replace(/[^a-z0-9]/g, '');
            if (cK === cleanKey || (cK.length >= 3 && cleanKey.length >= 3 && (cK.indexOf(cleanKey) !== -1 || cleanKey.indexOf(cK) !== -1))) {
              targetEntry = teamDataMap[k];
              key = k;
            }
          });
        }
        if (!targetEntry) return;

        var pts = resolvePointsFromConfig(ptsCfg, positionKey, altKey);
        if (pts <= 0) return;

        var prevPts = teamTourneyPoints[key] || 0;
        if (pts <= prevPts) return; // Keep maximum points earned

        var diff = pts - prevPts;
        teamTourneyPoints[key] = pts;

        if (!targetEntry.hasLocalUpdates) {
          targetEntry.hasLocalUpdates = true;
          targetEntry.totalPts = 0;
          targetEntry.lastPts = 0;
        }

        if (isActiveWindow) {
          targetEntry.totalPts += diff;
          if (prevPts === 0) targetEntry.activeTourneys += 1;
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

      // =========================================================================
      // DETERMINE TOURNAMENT FORMAT & STRUCTURE
      // =========================================================================
      var rawMultiCfg = getStorageData(['tourma_multi_config_'], t.id);
      var multiConfig = null;
      if (rawMultiCfg) {
        try { multiConfig = JSON.parse(rawMultiCfg); } catch (e) {}
      }

      var isMultiStage = !!(multiConfig && multiConfig.stage2Format) || (t.stageCount > 1) || (t.isMultiStage);
      var s1Format = (multiConfig && multiConfig.stage1Format) ? multiConfig.stage1Format.toUpperCase() : (t.stage1Format || (t.format || 'SWISS_LITE'));
      var s2Format = (multiConfig && multiConfig.stage2Format) ? multiConfig.stage2Format.toUpperCase() : (t.stage2Format || 'ROUND_ROBIN');

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
            } catch (e) { }

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
          } catch (e) { }
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
              } catch (e) { }
            }

            var standings = null;
            if (teamsList.length > 0 && window.TourmaRoundRobinAlgorithm && typeof window.TourmaRoundRobinAlgorithm.calculateStandings === 'function') {
              standings = window.TourmaRoundRobinAlgorithm.calculateStandings(teamsList, rrMatchesMap, (multiConfig && multiConfig.stage1Config) || rrData.config || {});
            }

            if (standings && standings.length > 0) {
              standings.forEach(function (row, idx) {
                var name = extractName(row) || (row.team ? extractName(row.team) : null);
                if (name) {
                  if (!isMultiStage) {
                    awardTeamPoints(name, String(idx + 1));
                  } else {
                    awardTeamPoints(name, "stage1_eliminated", String(idx + 1));
                  }
                }
              });
            }
          } catch (e) { }
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
                    });
                  } else {
                    grpList.forEach(function (st) {
                      awardTeamPoints(st.name, "stage1_eliminated");
                    });
                  }
                }
              });
            }
          } catch (e) { }
        }
      }

      // 4. Stage 1 Single Elimination
      if (s1Format === 'SINGLE_ELIMINATION' || (!isMultiStage && t.format === 'SINGLE_ELIMINATION')) {
        var rawBracket = getStorageData(['tourma_bracket_matches_', 'tourma_matches_', 'tourma_bracket_'], t.id);
        if (rawBracket) {
          try {
            var bracketData = JSON.parse(rawBracket);
            var matchesMap = bracketData.matchesMap || bracketData;
            if (matchesMap && typeof matchesMap === 'object') {
              var mKeys = Object.keys(matchesMap);
              var totalTeams = 0;
              try {
                var sTeams = JSON.parse(getStorageData(['tourma_teams_'], t.id));
                if (Array.isArray(sTeams) && sTeams.length > 0) totalTeams = sTeams.length;
              } catch(e) {}
              if (!totalTeams || totalTeams < 2) totalTeams = 16;
              var totalFullRounds = Math.round(Math.log2(totalTeams));

              mKeys.forEach(function (mk) {
                var m = matchesMap[mk];
                if (m && m.winnerId) {
                  var rNum = m.roundNumber || 1;
                  var teamsInRound = totalTeams / Math.pow(2, rNum - 1);
                  var startPos = Math.floor(teamsInRound / 2) + 1;
                  var endPos = teamsInRound;
                  var posKey = (startPos === endPos) ? String(startPos) : (startPos + "-" + endPos);

                  var mRes = resolveWinnerAndLoser(m);
                  if (mRes.loser) {
                    awardTeamPoints(mRes.loser, posKey, "stage1_eliminated");
                  }
                  if (mRes.winner && !isMultiStage && rNum === totalFullRounds) {
                    awardTeamPoints(mRes.winner, "1");
                  }
                  if (mRes.loser && !isMultiStage && rNum === totalFullRounds) {
                    awardTeamPoints(mRes.loser, "2");
                  }
                }
              });
            }
          } catch (e) { }
        }
      }

      // 5. Stage 1 Double Elimination
      if (s1Format === 'DOUBLE_ELIMINATION' || (!isMultiStage && t.format === 'DOUBLE_ELIMINATION')) {
        var rawDE = getStorageData(['tourma_de_matches_'], t.id);
        if (rawDE) {
          try {
            var deData = JSON.parse(rawDE);
            var lowerRounds = deData.lowerRounds || [];
            var gfRound = deData.grandFinalsRound;

            if (gfRound && gfRound.matches && gfRound.matches.length > 0 && !isMultiStage) {
              var gfMatches = gfRound.matches;
              var gfFinal = (gfMatches.length > 1 && gfMatches[1].winnerId) ? gfMatches[1] : gfMatches[0];
              if (gfFinal && gfFinal.winnerId) {
                var resGF = resolveWinnerAndLoser(gfFinal);
                if (resGF.winner) awardTeamPoints(resGF.winner, "1");
                if (resGF.loser) awardTeamPoints(resGF.loser, "2");
              }
            }

            if (lowerRounds && lowerRounds.length > 0) {
              var totalLbR = lowerRounds.length;
              for (var lrIdx = totalLbR - 1; lrIdx >= 0; lrIdx--) {
                var lrNum = lrIdx + 1;
                var offset = totalLbR - 1 - lrIdx;
                var posKey = (offset === 0) ? "3" : ((offset === 1) ? "4" : "5-8");
                if (isMultiStage) {
                  posKey = "s1_lb_r" + lrNum;
                } else if (offset >= 2) {
                  var k = Math.floor((offset - 2) / 2);
                  var tStart = Math.pow(2, k + 2) + 1;
                  var hSize = Math.pow(2, k + 1);
                  var tEnd = Math.pow(2, k + 3);
                  posKey = (offset % 2 === 0) ? (tStart + "-" + (tStart + hSize - 1)) : ((tStart + hSize) + "-" + tEnd);
                }
                var roundObj = lowerRounds[lrIdx];
                if (roundObj && roundObj.matches) {
                  roundObj.matches.forEach(function (m) {
                    if (m && m.winnerId) {
                      var res = resolveWinnerAndLoser(m);
                      if (res.loser) awardTeamPoints(res.loser, posKey, "stage1_eliminated");
                    }
                  });
                }
              }
            }
          } catch (e) { }
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

          var rrMatchesMapS2 = {};
          if (rawRRS2) {
            try {
              var rrDataS2 = JSON.parse(rawRRS2);
              rrMatchesMapS2 = rrDataS2.matchesMap || rrDataS2;
              if (teamsListS2.length === 0 && rrDataS2.teamsList) {
                teamsListS2 = rrDataS2.teamsList;
              }
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

          if (teamsListS2 && teamsListS2.length > 0 && window.TourmaRoundRobinAlgorithm) {
            var standingsS2 = window.TourmaRoundRobinAlgorithm.calculateStandings(teamsListS2, rrMatchesMapS2, (multiConfig && multiConfig.stage2Config) || {});
            if (standingsS2 && standingsS2.length > 0) {
              standingsS2.forEach(function (row, idx) {
                var name = extractName(row) || (row.team ? extractName(row.team) : null);
                if (name) {
                  var rNum = idx + 1;
                  awardTeamPoints(name, String(rNum), (rNum === 1 ? "1" : (rNum === 2 ? "2" : String(rNum))));
                }
              });
            }
          }
        }

        // B. Stage 2 Double Elimination
        else if (s2Format === 'DOUBLE_ELIMINATION') {
          var rawDES2 = getStorageData(['tourma_de_matches_stage2_'], t.id);
          if (rawDES2) {
            try {
              var deDataS2 = JSON.parse(rawDES2);
              var gfRoundS2 = deDataS2.grandFinalsRound;
              if (gfRoundS2 && gfRoundS2.matches && gfRoundS2.matches.length > 0) {
                var gfMatchesS2 = gfRoundS2.matches;
                var gfFinalS2 = (gfMatchesS2.length > 1 && gfMatchesS2[1].winnerId) ? gfMatchesS2[1] : gfMatchesS2[0];
                if (gfFinalS2 && gfFinalS2.winnerId) {
                  var resGFS2 = resolveWinnerAndLoser(gfFinalS2);
                  if (resGFS2.winner) awardTeamPoints(resGFS2.winner, "1");
                  if (resGFS2.loser) awardTeamPoints(resGFS2.loser, "2");
                }
              }

              var lowerRoundsS2 = deDataS2.lowerRounds || [];
              if (lowerRoundsS2 && lowerRoundsS2.length > 0) {
                var totalLbR2 = lowerRoundsS2.length;
                for (var lrIdx2 = totalLbR2 - 1; lrIdx2 >= 0; lrIdx2--) {
                  var offset2 = totalLbR2 - 1 - lrIdx2;
                  var posKey2 = (offset2 === 0) ? "3" : ((offset2 === 1) ? "4" : "5-8");
                  if (offset2 >= 2) {
                    var k2 = Math.floor((offset2 - 2) / 2);
                    var tStart = Math.pow(2, k2 + 2) + 1;
                    var hSize = Math.pow(2, k2 + 1);
                    var tEnd = Math.pow(2, k2 + 3);
                    posKey2 = (offset2 % 2 === 0) ? (tStart + "-" + (tStart + hSize - 1)) : ((tStart + hSize) + "-" + tEnd);
                  }
                  var roundObj2 = lowerRoundsS2[lrIdx2];
                  if (roundObj2 && roundObj2.matches) {
                    roundObj2.matches.forEach(function (m) {
                      if (m && m.winnerId) {
                        var res2 = resolveWinnerAndLoser(m);
                        if (res2.loser) awardTeamPoints(res2.loser, posKey2, "s2_lb_r" + (lrIdx2 + 1));
                      }
                    });
                  }
                }
              }
            } catch (e) { }
          }
        }

        // C. Stage 2 Single Elimination
        else {
          var rawBracketS2 = getStorageData(['tourma_bracket_stage2_', 'tourma_matches_stage2_'], t.id);
          if (rawBracketS2) {
            try {
              var bracketDataS2 = JSON.parse(rawBracketS2);
              var matchesMapS2 = bracketDataS2.matchesMap || bracketDataS2;
              if (matchesMapS2 && typeof matchesMapS2 === 'object') {
                var mKeysS2 = Object.keys(matchesMapS2);
                var s2TeamsCount = 0;
                try {
                  var st2 = JSON.parse(getStorageData(['tourma_stage2_teams_'], t.id));
                  if (Array.isArray(st2) && st2.length > 0) s2TeamsCount = st2.length;
                } catch(e) {}
                if (!s2TeamsCount || s2TeamsCount < 2) s2TeamsCount = 4;
                var totalS2FullRounds = Math.round(Math.log2(s2TeamsCount));

                mKeysS2.forEach(function (mk) {
                  var m = matchesMapS2[mk];
                  if (m && m.winnerId) {
                    var rNumS2 = m.roundNumber || 1;
                    var tInR = s2TeamsCount / Math.pow(2, rNumS2 - 1);
                    var sPos = Math.floor(tInR / 2) + 1;
                    var ePos = tInR;
                    var posKeyS2 = (sPos === ePos) ? String(sPos) : (sPos + "-" + ePos);

                    var resS2 = resolveWinnerAndLoser(m);
                    if (rNumS2 === totalS2FullRounds) {
                      if (resS2.winner) awardTeamPoints(resS2.winner, "1");
                      if (resS2.loser) awardTeamPoints(resS2.loser, "2");
                    } else {
                      if (resS2.loser) awardTeamPoints(resS2.loser, posKeyS2);
                    }
                  }
                });
              }
            } catch (e) { }
          }
        }

      }

    });

    // Re-sort team rows by totalPts descending
    var teamDataArray = Object.keys(teamDataMap).map(function (k) { return teamDataMap[k]; });
    teamDataArray.sort(function (a, b) {
      if (b.totalPts !== a.totalPts) return b.totalPts - a.totalPts;
      return b.lastPts - a.lastPts;
    });

    var tbody = standingsTable.querySelector('tbody');
    if (!tbody) return;

    teamDataArray.forEach(function (data, rankIdx) {
      var row = data.row;
      var rank = rankIdx + 1;

      // Physically re-order row in table DOM according to sorted rank
      tbody.appendChild(row);

      // Update Rank Badge
      if (row.cells[0]) {
        row.cells[0].innerHTML = '<span class="rank-badge rank-' + rank + '">#' + rank + '</span>';
      }
      // Update Played Tournaments
      if (row.cells[2]) {
        row.cells[2].textContent = data.activeTourneys;
      }
      // Update Total Active Points
      if (row.cells[3]) {
        row.cells[3].innerHTML = '<strong style="color: #fbbf24; font-weight: 800; font-size: 1.05rem;">' + data.totalPts + ' pts</strong>';
      }
      // Update Last Tournament Points
      if (row.cells[4]) {
        row.cells[4].innerHTML = data.lastPts > 0 ? ('<span style="color: #2dd4bf; font-weight: 700;">+' + data.lastPts + ' pts</span>') : '<span style="color: #94a3b8;">0 pts</span>';
      }
      // Update Net Fluctuation / Expiry
      if (row.cells[5]) {
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
        row.cells[5].innerHTML = '<span style="color: ' + changeColor + '; font-weight: 700;">' + changeText + '</span>';
      }

      tbody.appendChild(row);
    });
  }

  document.addEventListener('DOMContentLoaded', calculateAndSyncRollingStandings);
  setTimeout(calculateAndSyncRollingStandings, 50);
  setTimeout(calculateAndSyncRollingStandings, 200);

})();
