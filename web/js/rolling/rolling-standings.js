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
      var name = obj.name || obj.rawName || obj.normalizedName;
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
    if (!match || !match.winnerId) return { winner: null, loser: null };
    var wId = String(match.winnerId).trim();
    var t1Name = extractName(match.team1);
    var t2Name = extractName(match.team2);

    var winnerName = null;
    var loserName = null;

    if (wId === 'team1' || (match.team1 && (match.team1.id === wId || match.team1.name === wId))) {
      winnerName = t1Name;
      loserName = t2Name;
    } else if (wId === 'team2' || (match.team2 && (match.team2.id === wId || match.team2.name === wId))) {
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

      var ptsCfg = t.pointsConfig || {};

        // Helper to award points to a team
      function awardTeamPoints(name, positionKey, altKey) {
        if (!name) return;
        var key = name.toLowerCase().trim();
        var targetEntry = teamDataMap[key];
        if (!targetEntry) {
          var cleanKey = key.replace(/[^a-z0-9]/g, '');
          Object.keys(teamDataMap).forEach(function(k) {
            if (targetEntry) return;
            var cK = k.replace(/[^a-z0-9]/g, '');
            if (cK === cleanKey || (cK.length >= 3 && cleanKey.length >= 3 && (cK.indexOf(cleanKey) !== -1 || cleanKey.indexOf(cK) !== -1))) {
              targetEntry = teamDataMap[k];
            }
          });
        }
        if (!targetEntry) return;

        var pts = 0;
        if (ptsCfg && typeof ptsCfg === 'object') {
          // 1. Direct exact key match
          if (ptsCfg[positionKey] !== undefined) {
            pts = parseInt(ptsCfg[positionKey], 10) || 0;
          } else if (altKey && ptsCfg[altKey] !== undefined) {
            pts = parseInt(ptsCfg[altKey], 10) || 0;
          } else if (ptsCfg[String(positionKey)] !== undefined) {
            pts = parseInt(ptsCfg[String(positionKey)], 10) || 0;
          }

          var pMin = 0, pMax = 0;
          if (String(positionKey).indexOf('-') !== -1) {
            var parts = String(positionKey).split('-');
            pMin = parseInt(parts[0], 10) || 0;
            pMax = parseInt(parts[1], 10) || 0;
          } else {
            pMin = parseInt(positionKey, 10) || 0;
            pMax = pMin;
          }

          // Case A: positionKey is a range like "3-4" or "5-8"
          if (pts === 0 && pMax > pMin) {
            for (var p = pMax; p >= pMin; p--) {
              if (ptsCfg[String(p)] !== undefined) {
                pts = parseInt(ptsCfg[String(p)], 10) || 0;
                break;
              }
            }
          }

          // Case B: positionKey is a single rank like "3", "4", "5", "6", "7", "8"
          if (pts === 0 && pMax === pMin) {
            if ((pMin === 3 || pMin === 4) && ptsCfg["3-4"] !== undefined) {
              pts = parseInt(ptsCfg["3-4"], 10) || 0;
            } else if (pMin >= 5 && pMin <= 8 && ptsCfg["5-8"] !== undefined) {
              pts = parseInt(ptsCfg["5-8"], 10) || 0;
            } else if (pMin >= 9 && pMin <= 16 && ptsCfg["9-16"] !== undefined) {
              pts = parseInt(ptsCfg["9-16"], 10) || 0;
            } else if (pMin >= 17 && pMin <= 32 && ptsCfg["17-32"] !== undefined) {
              pts = parseInt(ptsCfg["17-32"], 10) || 0;
            }
          }

          // Case C: Dynamic range matching
          if (pts === 0) {
            var cfgKeys = Object.keys(ptsCfg);
            for (var ck = 0; ck < cfgKeys.length; ck++) {
              var cfgKey = cfgKeys[ck];
              if (cfgKey.indexOf('-') !== -1) {
                var cParts = cfgKey.split('-');
                var cMin = parseInt(cParts[0], 10) || 0;
                var cMax = parseInt(cParts[1], 10) || 0;
                if ((pMin >= cMin && pMax <= cMax) || (pMin <= cMax && pMax >= cMin)) {
                  pts = parseInt(ptsCfg[cfgKey], 10) || 0;
                  if (pts > 0) break;
                }
              }
            }
          }

          if (pts === 0 && ptsCfg["stage1_eliminated"] !== undefined) {
            pts = parseInt(ptsCfg["stage1_eliminated"], 10) || 0;
          }
        }

        // Fallback default points if ptsCfg is empty or unconfigured
        if (pts <= 0) {
          var pNum = parseInt(positionKey, 10);
          if (positionKey === "1" || pNum === 1) pts = 6;
          else if (positionKey === "2" || pNum === 2) pts = 5;
          else if (positionKey === "3" || pNum === 3) pts = 4;
          else if (positionKey === "4" || pNum === 4) pts = 3;
          else if (positionKey === "5-6" || (pNum >= 5 && pNum <= 6)) pts = 2;
          else if (positionKey === "7-8" || (pNum >= 7 && pNum <= 8)) pts = 1;
          else if (positionKey === "3-4") pts = 4;
          else if (positionKey === "5-8") pts = 2;
        }

        if (pts <= 0) return;

        if (!targetEntry.hasLocalUpdates) {
          targetEntry.hasLocalUpdates = true;
          targetEntry.totalPts = 0;
          targetEntry.lastPts = 0;
        }

        if (isActiveWindow) {
          targetEntry.totalPts += pts;
          targetEntry.activeTourneys += 1;
          if (isLatestTourney) {
            targetEntry.lastPts = pts;
          }
        } else {
          targetEntry.expiredPts += pts;
        }

        if (isDroppedTourney) {
          targetEntry.droppedPts = pts;
        }
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

      var awardedTeamsInTourney = {};
      var originalAward = awardTeamPoints;
      awardTeamPoints = function(name, positionKey, altKey) {
        if (!name) return;
        var k = name.toLowerCase().trim();
        awardedTeamsInTourney[k] = true;
        originalAward(name, positionKey, altKey);
      };

      // =========================================================================
      // STEP A: PARSE STAGE 2 (If present / Multi-Stage)
      // =========================================================================
      
      // 1. Single Elimination Stage 2
      var rawBracketS2 = getStorageData(['tourma_bracket_stage2_', 'tourma_matches_stage2_'], t.id);
      if (rawBracketS2) {
        try {
          var bracketDataS2 = JSON.parse(rawBracketS2);
          var matchesMapS2 = bracketDataS2.matchesMap || bracketDataS2;
          if (matchesMapS2 && typeof matchesMapS2 === 'object') {
            var mKeysS2 = Object.keys(matchesMapS2);
            var maxRoundS2 = 0;
            mKeysS2.forEach(function (mk) {
              var m = matchesMapS2[mk];
              if (m && m.roundNumber > maxRoundS2) maxRoundS2 = m.roundNumber;
            });

            if (maxRoundS2 > 0) {
              var finalMatchS2 = null;
              mKeysS2.forEach(function (mk) {
                var m = matchesMapS2[mk];
                if (m && m.roundNumber === maxRoundS2) finalMatchS2 = m;
              });

              if (finalMatchS2 && finalMatchS2.winnerId) {
                var resS2 = resolveWinnerAndLoser(finalMatchS2);
                if (resS2.winner) awardTeamPoints(resS2.winner, "1");
                if (resS2.loser) awardTeamPoints(resS2.loser, "2");
              }

              if (maxRoundS2 > 1) {
                mKeysS2.forEach(function (mk) {
                  var m = matchesMapS2[mk];
                  if (m && m.roundNumber === maxRoundS2 - 1 && m.winnerId) {
                    var sfRes = resolveWinnerAndLoser(m);
                    if (sfRes.loser) awardTeamPoints(sfRes.loser, "3-4");
                  }
                });
              }

              if (maxRoundS2 > 2) {
                mKeysS2.forEach(function (mk) {
                  var m = matchesMapS2[mk];
                  if (m && m.roundNumber === maxRoundS2 - 2 && m.winnerId) {
                    var qfRes = resolveWinnerAndLoser(m);
                    if (qfRes.loser) awardTeamPoints(qfRes.loser, "5-8");
                  }
                });
              }
            }
          }
        } catch (e) {}
      }

      // 2. Double Elimination Stage 2
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
              var posKey2 = "4";
              if (offset2 === 0) {
                posKey2 = "3";
              } else if (offset2 === 1) {
                posKey2 = "4";
              } else {
                var k2 = Math.floor((offset2 - 2) / 2);
                var tierStart2 = Math.pow(2, k2 + 2) + 1;
                var tierEnd2 = Math.pow(2, k2 + 3);
                var halfSize2 = Math.pow(2, k2 + 1);
                posKey2 = (offset2 % 2 === 0) ? (tierStart2 + "-" + (tierStart2 + halfSize2 - 1)) : ((tierStart2 + halfSize2) + "-" + tierEnd2);
              }

              var roundObj2 = lowerRoundsS2[lrIdx2];
              if (roundObj2 && roundObj2.matches) {
                roundObj2.matches.forEach(function(m) {
                  if (m && m.winnerId) {
                    var res2 = resolveWinnerAndLoser(m);
                    if (res2.loser) {
                      awardTeamPoints(res2.loser, posKey2, "s2_lb_r" + (lrIdx2 + 1));
                    }
                  }
                });
              }
            }
          }
        } catch (e) {}
      }

      // 3. Round Robin Stage 2
      var rawRRS2 = getStorageData(['tourma_rr_matches_stage2_'], t.id);
      if (rawRRS2) {
        try {
          var rrDataS2 = JSON.parse(rawRRS2);
          var teamsListS2 = rrDataS2.teamsList || [];
          var rrMatchesMapS2 = rrDataS2.matchesMap || rrDataS2;
          if (teamsListS2.length > 0 && window.TourmaRoundRobinAlgorithm) {
            var standingsS2 = window.TourmaRoundRobinAlgorithm.calculateStandings(teamsListS2, rrMatchesMapS2, rrDataS2.config || {});
            if (standingsS2 && standingsS2.length > 0) {
              standingsS2.forEach(function(row, idx) {
                var name = (typeof row === 'string') ? row : (row.team ? (typeof row.team === 'object' ? row.team.name : row.team) : row.name);
                if (name) awardTeamPoints(name, String(idx + 1));
              });
            }
          }
        } catch (e) {}
      }

      // 4. Guaranteed points for all teams qualified to Stage 2 who haven't completed final match yet
      try {
        var s2TeamsRaw = localStorage.getItem('tourma_stage2_teams_' + t.id);
        if (s2TeamsRaw) {
          var s2TeamsList = JSON.parse(s2TeamsRaw);
          if (Array.isArray(s2TeamsList) && s2TeamsList.length > 0) {
            var defaultPosKey = (s2TeamsList.length <= 4) ? "4" : ((s2TeamsList.length <= 8) ? "5-8" : "9-16");
            s2TeamsList.forEach(function(tm) {
              var tmName = (typeof tm === 'object') ? (tm.name || tm.rawName) : String(tm);
              if (tmName && !awardedTeamsInTourney[tmName.toLowerCase().trim()]) {
                awardTeamPoints(tmName, defaultPosKey, "3-4");
              }
            });
          }
        }
      } catch (e) {}

      // =========================================================================
      // STEP B: PARSE STAGE 1 (Or Single Stage)
      // =========================================================================

      // 1. Stage 1 Double Elimination
      var rawDE = getStorageData(['tourma_de_matches_'], t.id);
      if (rawDE) {
        try {
          var deData = JSON.parse(rawDE);
          var lowerRounds = deData.lowerRounds || [];
          var gfRound = deData.grandFinalsRound;

          // If Single Stage DE (has Grand Final): parse Champion & Runner-up
          if (gfRound && gfRound.matches && gfRound.matches.length > 0) {
            var gfMatches = gfRound.matches;
            var gfFinal = (gfMatches.length > 1 && gfMatches[1].winnerId) ? gfMatches[1] : gfMatches[0];
            if (gfFinal && gfFinal.winnerId) {
              var resGF = resolveWinnerAndLoser(gfFinal);
              if (resGF.winner) awardTeamPoints(resGF.winner, "1");
              if (resGF.loser) awardTeamPoints(resGF.loser, "2");
            }
          }

          // Parse Lower Bracket Eliminations
          if (lowerRounds && lowerRounds.length > 0) {
            var totalLbR = lowerRounds.length;
            for (var lrIdx = totalLbR - 1; lrIdx >= 0; lrIdx--) {
              var offset = totalLbR - 1 - lrIdx;
              var posKey = "stage1_eliminated";
              if (offset === 0) {
                posKey = "3";
              } else if (offset === 1) {
                posKey = "4";
              } else {
                var k = Math.floor((offset - 2) / 2);
                var tierStart = Math.pow(2, k + 2) + 1;
                var tierEnd = Math.pow(2, k + 3);
                var halfSize = Math.pow(2, k + 1);

                if (offset % 2 === 0) {
                  posKey = tierStart + "-" + (tierStart + halfSize - 1);
                } else {
                  posKey = (tierStart + halfSize) + "-" + tierEnd;
                }
              }

              var roundObj = lowerRounds[lrIdx];
              if (roundObj && roundObj.matches) {
                roundObj.matches.forEach(function(m) {
                  if (m && m.winnerId) {
                    var res = resolveWinnerAndLoser(m);
                    if (res.loser) {
                      var altKey = "s1_lb_r" + (lrIdx + 1);
                      awardTeamPoints(res.loser, posKey, altKey);
                    }
                  }
                });
              }
            }
          }
        } catch (e) {}
      }

      // 2. Stage 1 Single Elimination
      var rawBracket = getStorageData(['tourma_bracket_', 'tourma_matches_'], t.id);
      if (rawBracket) {
        try {
          var bracketData = JSON.parse(rawBracket);
          var matchesMap = bracketData.matchesMap || bracketData;
          if (matchesMap && typeof matchesMap === 'object') {
            var mKeys = Object.keys(matchesMap);
            var maxRound = 0;
            var round1MatchCount = 0;
            mKeys.forEach(function (mk) {
              var m = matchesMap[mk];
              if (m) {
                if (m.roundNumber > maxRound) maxRound = m.roundNumber;
                if (m.roundNumber === 1) round1MatchCount++;
              }
            });

            if (maxRound > 0) {
              // If SINGLE STAGE SE (no Stage 2):
              if (!rawBracketS2 && !rawDES2 && !rawRRS2) {
                var finalMatch = null;
                mKeys.forEach(function (mk) {
                  var m = matchesMap[mk];
                  if (m && m.roundNumber === maxRound) finalMatch = m;
                });

                if (finalMatch && finalMatch.winnerId) {
                  var res = resolveWinnerAndLoser(finalMatch);
                  if (res.winner) awardTeamPoints(res.winner, "1");
                  if (res.loser) awardTeamPoints(res.loser, "2");
                }

                if (maxRound > 1) {
                  mKeys.forEach(function (mk) {
                    var m = matchesMap[mk];
                    if (m && m.roundNumber === maxRound - 1 && m.winnerId) {
                      var sfRes = resolveWinnerAndLoser(m);
                      if (sfRes.loser) awardTeamPoints(sfRes.loser, "3-4");
                    }
                  });
                }
              }

              // Parse eliminated losers across ALL rounds in Stage 1 SE
              var estTotalTeams = Math.max(8, Math.pow(2, Math.ceil(Math.log2(Math.max(4, round1MatchCount * 2)))));
              for (var r = 1; r <= maxRound; r++) {
                var curRoundTeams = Math.pow(2, Math.round(Math.log2(estTotalTeams)) - r + 1);
                var startTier = Math.floor(curRoundTeams / 2) + 1;
                var endTier = curRoundTeams;
                var roundPosKey = startTier + "-" + endTier; // e.g. "5-8", "9-16"

                mKeys.forEach(function (mk) {
                  var m = matchesMap[mk];
                  if (m && m.roundNumber === r && m.winnerId) {
                    var mRes = resolveWinnerAndLoser(m);
                    if (mRes.loser) {
                      awardTeamPoints(mRes.loser, roundPosKey, "stage1_eliminated");
                    }
                  }
                });
              }
            }
          }
        } catch (e) {}
      }

      // 3. Stage 1 Round Robin / Group Stage
      var rawRR = getStorageData(['tourma_rr_matches_'], t.id);
      if (rawRR && !rawBracketS2 && !rawDES2) {
        try {
          var rrData = JSON.parse(rawRR);
          var rrMatchesMap = rrData.matchesMap || rrData;
          var teamsList = rrData.teamsList || [];

          if (!teamsList || teamsList.length === 0) {
            try {
              teamsList = JSON.parse(localStorage.getItem('tourma_teams_' + t.id)) || [];
            } catch (e) {}
          }

          var standings = null;
          if (teamsList.length > 0 && window.TourmaRoundRobinAlgorithm && typeof window.TourmaRoundRobinAlgorithm.calculateStandings === 'function') {
            standings = window.TourmaRoundRobinAlgorithm.calculateStandings(teamsList, rrMatchesMap, rrData.config || {});
          }

          if (standings && standings.length > 0) {
            standings.forEach(function(row, idx) {
              var name = (typeof row === 'string') ? row : (row.team ? (typeof row.team === 'object' ? row.team.name : row.team) : row.name);
              if (name) awardTeamPoints(name, String(idx + 1));
            });
          }
        } catch (e) {}
      }

    });

    // Re-sort team rows by totalPts descending
    var teamDataArray = Object.keys(teamDataMap).map(function (k) { return teamDataMap[k]; });
    teamDataArray.sort(function (a, b) {
      return b.totalPts - a.totalPts;
    });

    var tbody = standingsTable.querySelector('tbody');
    if (!tbody) return;

    teamDataArray.forEach(function (data, rankIdx) {
      var row = data.row;
      var rank = rankIdx + 1;

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
        if (netChange > 0) {
          row.cells[5].innerHTML = '<span style="color: #2dd4bf; font-weight: 700;">+' + netChange + ' pts</span>';
        } else if (netChange < 0) {
          row.cells[5].innerHTML = '<span style="color: #ef4444; font-weight: 700;">' + netChange + ' pts</span>';
        } else {
          row.cells[5].innerHTML = '<span style="color: #94a3b8;">0 pts</span>';
        }
      }

      tbody.appendChild(row);
    });
  }

  document.addEventListener('DOMContentLoaded', calculateAndSyncRollingStandings);

})();
