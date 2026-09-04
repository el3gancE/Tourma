/**
 * TOURMA - UNIFIED TEAM PROFILE SCRIPT (team-profile.js)
 * Synchronizes Server + LocalStorage sub-tournament bracket placements
 * Calculates exact live team stats, W-L records, achievements, and tournament performance
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
    var bestRank = 999;
    var bestRankTourneyName = "";

    var championTourneysList = [];
    var tourneyPerformances = [];

    // Helper to find partner key
    function findPartnerKey(name) {
      if (!name) return null;
      var k = name.trim().toLowerCase();
      if (teamDataMap[k]) return k;
      var matchK = null;
      Object.keys(teamDataMap).forEach(function (pk) {
        if (matchK) return;
        if (isTeamSelf(pk, name)) {
          matchK = pk;
        }
      });
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

        var pts = resolvePointsFromConfig(ptsCfg, positionKey, altKey);
        if (pts <= 0) return;

        var prevPts = tourneyTeamPoints[key] || 0;
        if (pts <= prevPts) return;

        var diff = pts - prevPts;
        tourneyTeamPoints[key] = pts;
        tourneyTeamPositions[key] = positionKey;

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

      var tourneyWinsThisTeam = 0;
      var tourneyLossesThisTeam = 0;
      var playedThisTourney = false;
      var formatName = (t.format || '').toUpperCase();

      // =========================================================================
      // 1. SINGLE ELIMINATION BRACKET
      // =========================================================================
      var rawBracket = getStorageData(['tourma_bracket_stage2_', 'tourma_bracket_'], t.id);
      var bracketData = null;
      var matchesMap = null;

      if (rawBracket) {
        try {
          bracketData = JSON.parse(rawBracket);
          if (bracketData) matchesMap = bracketData.matchesMap || bracketData;
        } catch (e) {}
      }

      if (!matchesMap || (typeof matchesMap === 'object' && Object.keys(matchesMap).length === 0)) {
        var rawM = getStorageData(['tourma_matches_stage2_', 'tourma_matches_', 'tourma_bracket_matches_'], t.id);
        if (rawM) {
          try {
            matchesMap = JSON.parse(rawM);
            if (!bracketData) bracketData = { matchesMap: matchesMap };
          } catch (e) {}
        }
      }

      if (matchesMap && typeof matchesMap === 'object') {
        var mKeys = Object.keys(matchesMap);

        // Determine total rounds dynamically from actual matches & roundsList
        var maxR = 1;
        mKeys.forEach(function (mk) {
          var m = matchesMap[mk];
          var r = m ? (m.roundNumber || m.round || m.roundIndex || 0) : 0;
          if (r > maxR) maxR = r;
        });
        if (bracketData && bracketData.roundsList && Array.isArray(bracketData.roundsList) && bracketData.roundsList.length > maxR) {
          maxR = bracketData.roundsList.length;
        }
        var estR = Math.ceil(Math.log2(mKeys.length + 1));
        if (estR > maxR) maxR = estR;
        var totalFullRounds = maxR;

        // Build parent-child tree mapping to accurately determine round distance to Final
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
          var t1Name = extractName(m.team1) || (m.team1Name ? extractName(m.team1Name) : null);
          var t2Name = extractName(m.team2) || (m.team2Name ? extractName(m.team2Name) : null);

          // Check if target team played this match
          var isT1 = isTeamSelf(t1Name, targetKey);
          var isT2 = isTeamSelf(t2Name, targetKey);

          var res = resolveWinnerAndLoser(m);

          if (isT1 || isT2) {
            playedThisTourney = true;
            if (res.winner) {
              if (isTeamSelf(res.winner, targetKey)) {
                tourneyWinsThisTeam++;
              } else {
                tourneyLossesThisTeam++;
              }
            }
          }

          if (res.winner || res.loser) {
            var roundDiff = 0;
            if (matchDistToFinal[mk] !== undefined) {
              roundDiff = matchDistToFinal[mk];
            } else {
              var rNum = m.roundNumber || m.round || 1;
              roundDiff = Math.max(0, totalFullRounds - rNum);
            }

            var posKey = "";
            if (roundDiff === 0) {
              posKey = "2"; // Runner up
            } else if (roundDiff === 1) {
              posKey = "3-4"; // Semifinalist
            } else if (roundDiff === 2) {
              posKey = "5-8"; // Quarterfinalist
            } else if (roundDiff === 3) {
              posKey = "9-16";
            } else if (roundDiff === 4) {
              posKey = "17-32";
            } else if (roundDiff === 5) {
              posKey = "33-64";
            } else if (roundDiff === 6) {
              posKey = "65-128";
            } else {
              var sP = Math.pow(2, roundDiff) + 1;
              var eP = Math.pow(2, roundDiff + 1);
              posKey = sP + "-" + eP;
            }

            if (res.loser) {
              awardTeamPoints(res.loser, posKey, "stage1_eliminated");
            }
            if (roundDiff === 0 && res.winner) {
              awardTeamPoints(res.winner, "1");
            }
          }
        });
      }

      // =========================================================================
      // 2. DOUBLE ELIMINATION BRACKET
      // =========================================================================
      var rawDE = getStorageData(['tourma_de_matches_stage2_', 'tourma_de_matches_'], t.id);
      if (rawDE) {
        try {
          var deData = JSON.parse(rawDE);
          var lowerRounds = deData.lowerRounds || [];
          var upperRounds = deData.upperRounds || [];
          var gfRound = deData.grandFinalsRound;

          // Upper bracket matches W/L
          if (upperRounds && upperRounds.length > 0) {
            upperRounds.forEach(function (ur) {
              if (ur && ur.matches) {
                ur.matches.forEach(function (m) {
                  var t1Name = extractName(m.team1);
                  var t2Name = extractName(m.team2);
                  var isT1 = isTeamSelf(t1Name, targetKey);
                  var isT2 = isTeamSelf(t2Name, targetKey);
                  var res = resolveWinnerAndLoser(m);
                  if (isT1 || isT2) {
                    playedThisTourney = true;
                    if (res.winner) {
                      if (isTeamSelf(res.winner, targetKey)) tourneyWinsThisTeam++;
                      else tourneyLossesThisTeam++;
                    }
                  }
                });
              }
            });
          }

          // Lower bracket matches W/L & placements
          if (lowerRounds && lowerRounds.length > 0) {
            var totalLbR = lowerRounds.length;
            for (var lrIdx = totalLbR - 1; lrIdx >= 0; lrIdx--) {
              var offset = totalLbR - 1 - lrIdx;
              var posKey = (offset === 0) ? "3" : ((offset === 1) ? "4" : "5-8");
              var roundObj = lowerRounds[lrIdx];
              if (roundObj && roundObj.matches) {
                roundObj.matches.forEach(function (m) {
                  var t1Name = extractName(m.team1);
                  var t2Name = extractName(m.team2);
                  var isT1 = isTeamSelf(t1Name, targetKey);
                  var isT2 = isTeamSelf(t2Name, targetKey);
                  var res = resolveWinnerAndLoser(m);
                  if (isT1 || isT2) {
                    playedThisTourney = true;
                    if (res.winner) {
                      if (isTeamSelf(res.winner, targetKey)) tourneyWinsThisTeam++;
                      else tourneyLossesThisTeam++;
                    }
                  }
                  if (res.loser) awardTeamPoints(res.loser, posKey);
                });
              }
            }
          }

          // Grand Finals
          if (gfRound && gfRound.matches && gfRound.matches.length > 0) {
            var gfMatches = gfRound.matches;
            var gfFinal = (gfMatches.length > 1 && (gfMatches[1].winnerId || gfMatches[1].winner)) ? gfMatches[1] : gfMatches[0];
            var t1Name = extractName(gfFinal.team1);
            var t2Name = extractName(gfFinal.team2);
            var isT1 = isTeamSelf(t1Name, targetKey);
            var isT2 = isTeamSelf(t2Name, targetKey);
            var resGF = resolveWinnerAndLoser(gfFinal);
            if (isT1 || isT2) {
              playedThisTourney = true;
              if (resGF.winner) {
                if (isTeamSelf(resGF.winner, targetKey)) tourneyWinsThisTeam++;
                else tourneyLossesThisTeam++;
              }
            }
            if (resGF.winner) awardTeamPoints(resGF.winner, "1");
            if (resGF.loser) awardTeamPoints(resGF.loser, "2");
          }
        } catch (e) {}
      }

      // =========================================================================
      // 3. CHAMPION OVERRIDE (e.g. tourma_champion_TOURNEY_...)
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
            }
          }
        } catch (e) {}
      }

      // =========================================================================
      // 4. RECORD TARGET TEAM'S PERFORMANCE FOR THIS TOURNAMENT
      // =========================================================================
      var targetEntryKey = findPartnerKey(targetTeamName);
      var ptsEarned = targetEntryKey ? (tourneyTeamPoints[targetEntryKey] || 0) : 0;
      var posKeyEarned = targetEntryKey ? (tourneyTeamPositions[targetEntryKey] || null) : null;

      if (isExplicitChamp) {
        posKeyEarned = "1";
        if (ptsEarned <= 0) {
          ptsEarned = resolvePointsFromConfig(ptsCfg, "1");
        }
      }

      if (playedThisTourney || ptsEarned > 0 || posKeyEarned !== null) {
        targetPlayedTourneys++;
        targetWins += tourneyWinsThisTeam;
        targetLosses += tourneyLossesThisTeam;

        var achievement = "Vòng Bảng";
        var posNum = parseInt(posKeyEarned, 10) || 16;

        if (isExplicitChamp || posKeyEarned === "1" || posNum === 1) {
          achievement = "Vô Địch";
          champCount++;
          semiCount++;
          quarterCount++;
          posNum = 1;
          championTourneysList.push({
            id: t.id,
            name: t.name,
            tier: (t.tierName || "A").toUpperCase()
          });
        } else if (posKeyEarned === "2" || posNum === 2) {
          achievement = "Á Quân";
          runnerUpCount++;
          semiCount++;
          quarterCount++;
          posNum = 2;
        } else if (posKeyEarned === "3-4" || posKeyEarned === "3" || posKeyEarned === "4" || (posNum >= 3 && posNum <= 4)) {
          achievement = "Bán Kết";
          semiCount++;
          quarterCount++;
          posNum = 4;
        } else if (posKeyEarned === "5-8" || (posNum >= 5 && posNum <= 8)) {
          achievement = "Tứ Kết";
          quarterCount++;
          posNum = 8;
        } else if (posKeyEarned === "9-16" || (posNum >= 9 && posNum <= 16)) {
          achievement = "Vòng 16";
        } else if (posKeyEarned === "17-32" || (posNum >= 17 && posNum <= 32)) {
          achievement = "Vòng 32";
        } else if (posKeyEarned === "33-64" || (posNum >= 33 && posNum <= 64)) {
          achievement = "Vòng 64";
        } else if (posKeyEarned === "65-128" || (posNum >= 65 && posNum <= 128)) {
          achievement = "Vòng 128";
        } else {
          achievement = "Vòng Bảng";
        }

        if (posNum < bestRank) {
          bestRank = posNum;
          bestRankTourneyName = t.name;
        }

        var fmtLabel = "Single Elimination";
        if (t.format) {
          if (t.format.indexOf("DOUBLE") !== -1) fmtLabel = "Double Elimination";
          else if (t.format.indexOf("SWISS") !== -1) fmtLabel = "Swiss System";
          else if (t.format.indexOf("ROUND") !== -1) fmtLabel = "Round Robin";
          else if (t.format.indexOf("GROUP") !== -1) fmtLabel = "Group Stage";
        }

        tourneyPerformances.push({
          id: t.id,
          name: t.name,
          format: fmtLabel,
          achievement: achievement,
          pointsEarned: ptsEarned,
          stt: targetPlayedTourneys
        });
      }
    });

    // =========================================================================
    // 5. DETERMINE CURRENT LEADERBOARD RANKING & ACTIVE POINTS
    // =========================================================================
    var partnerList = Object.values(teamDataMap);
    partnerList.sort(function (a, b) {
      if (b.totalPts !== a.totalPts) return b.totalPts - a.totalPts;
      return b.lastPts - a.lastPts;
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

    tourneyPerformances.forEach(function (perf) {
      totalAccumPoints += perf.pointsEarned;
    });
    if (totalAccumPoints < currentActivePoints) {
      totalAccumPoints = currentActivePoints;
    }

    if (bestRank === 999) {
      bestRank = (currentRank > 0 && currentActivePoints > 0) ? currentRank : 0;
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
      var bestText = (bestRank > 0 ? ("#" + bestRank) : "-");
      if (bestRankTourneyName && bestRank > 0) {
        bestText += " (" + bestRankTourneyName + ")";
      }
      elBestRank.textContent = bestText;
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

    // Update Champion Badges Section (Only if champCount > 0, otherwise completely blank / display: none)
    var elChampSection = document.getElementById('profChampionSection');
    var elChampBadges = document.getElementById('profChampionBadges');
    if (elChampSection) {
      if (champCount > 0 && championTourneysList.length > 0) {
        elChampSection.style.display = '';
        if (elChampBadges) {
          var badgesHtml = '';
          var reversedChampList = championTourneysList.slice().reverse();
          reversedChampList.forEach(function (ct) {
            badgesHtml += '<div class="champion-badge-pill">' +
              '<i class="fa-solid fa-crown"></i> ' + ct.name + ' <span class="tier-tag">[' + ct.tier + ']</span>' +
              '</div>';
          });
          elChampBadges.innerHTML = badgesHtml;
        }
      } else {
        elChampSection.style.display = 'none';
      }
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

          rowsHtml += '<tr>' +
            '<td style="font-weight: 700; color: var(--text-muted);">' + sttVal + '</td>' +
            '<td style="font-weight: 700; color: #ffffff;">' + perf.name + '</td>' +
            '<td style="color: var(--text-muted); font-size: 0.82rem;">' + perf.format + '</td>' +
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

  // Auto-run on DOM ready and with retry intervals to guarantee synchronization
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', calculateAndSyncTeamProfile);
  } else {
    calculateAndSyncTeamProfile();
  }
  setTimeout(calculateAndSyncTeamProfile, 50);
  setTimeout(calculateAndSyncTeamProfile, 200);
  setTimeout(calculateAndSyncTeamProfile, 500);

  if (typeof window !== 'undefined' && window.addEventListener) {
    window.addEventListener('storage', calculateAndSyncTeamProfile);
  }

})();
