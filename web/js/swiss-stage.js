/**
 * TOURMA - Swiss System Stage Engine (swiss-stage.js)
 * Integrates shared TourmaMatchCard, TourmaBracketCard, and TourmaScoreModal modules.
 * Supports 2 View Modes:
 * 1. List View (Dạng Danh Sách): Rendered using TourmaMatchCard
 * 2. Bracket View (Sơ Đồ Bracket): Rendered using TourmaBracketCard in Viewport (No SVG lines, Unboxed)
 * 3. Pre-renders ALL 5 Rounds in advance with TBD placeholders (33 Matches total across all pools)
 */

(function () {
  'use strict';

  var tournamentId = window.swissTournamentId || 'demo';
  var currentViewMode = 'BRACKET'; // 'LIST' or 'BRACKET' (Persisted in localStorage)
  var activeRoundFilter = 'ALL'; // 'ALL', 1, 2, 3...
  var isQuickMode = false;
  var teamsList = [];
  var matchesMap = {};
  var roundRandomScores = {};
  var poolRandomScores = {};

  // Fixed 5-Round Swiss Structure Matrix for 16 Teams (33 Matches total)
  var SWISS_STRUCTURE = [
    { roundIndex: 1, pools: [ { key: '0-0', count: 8 } ] },
    { roundIndex: 2, pools: [ { key: '1-0', count: 4 }, { key: '0-1', count: 4 } ] },
    { roundIndex: 3, pools: [ { key: '2-0', count: 2 }, { key: '1-1', count: 4 }, { key: '0-2', count: 2 } ] },
    { roundIndex: 4, pools: [ { key: '2-1', count: 3 }, { key: '1-2', count: 3 } ] },
    { roundIndex: 5, pools: [ { key: '2-2', count: 3 } ] }
  ];

  // Helper for Record Pool Header Colors (2-0, 2-1 = Green; 0-2, 1-2 = Red; 2-2 = Gold)
  function getPoolColorClass(pKey) {
    if (pKey === '2-0' || pKey === '2-1') return 'swiss-pool-green';
    if (pKey === '0-2' || pKey === '1-2') return 'swiss-pool-red';
    if (pKey === '2-2') return 'swiss-pool-gold';
    return 'swiss-pool-mint';
  }

  // Helper for Match Card Color Classes (2-0, 2-1 = Green Card; 0-2, 1-2 = Red Card; 2-2 = Gold Card)
  function getPoolCardClass(pKey) {
    if (pKey === '2-0' || pKey === '2-1') return 'swiss-card-green';
    if (pKey === '0-2' || pKey === '1-2') return 'swiss-card-red';
    if (pKey === '2-2') return 'swiss-card-gold';
    return 'swiss-card-mint';
  }

  // Helper for Multi-Score Random Generator (Varied scores like 2-1, 3-1, 2-0, 3-2, 1-0)
  function getRandomDecisiveScores() {
    var winScore = Math.floor(Math.random() * 3) + 1; // 1, 2, or 3
    var loseScore = Math.floor(Math.random() * winScore); // 0 to winScore - 1
    return { winScore: winScore, loseScore: loseScore };
  }

  // CASCADING RESET HELPER: Revert all future rounds after targetRoundIndex back to TBD!
  function invalidateFutureRounds(targetRoundIndex) {
    Object.keys(matchesMap).forEach(function (k) {
      var m = matchesMap[k];
      if (m && m.roundIndex > targetRoundIndex) {
        m.team1 = { name: 'TBD' };
        m.team2 = { name: 'TBD' };
        m.team1Score = 0;
        m.team2Score = 0;
        m.status = 'PENDING';
      }
    });
  }

  // Set Quick Mode state with Persistence to localStorage
  function setQuickMode(state) {
    isQuickMode = !!state;
    window.TourmaQuickMode = isQuickMode;

    try {
      localStorage.setItem("tourma_swiss_quick_mode_" + tournamentId, isQuickMode ? "true" : "false");
    } catch(e) {}

    var btn = document.getElementById('btnSwissQuickMode');
    var txt = document.getElementById('quickModeText');
    if (btn && txt) {
      if (isQuickMode) {
        btn.classList.add('active');
        txt.innerText = 'ON';
        document.body.classList.add('tourma-quick-mode-active');
      } else {
        btn.classList.remove('active');
        txt.innerText = 'OFF';
        document.body.classList.remove('tourma-quick-mode-active');
      }
    }
  }

  // Quick Mode Toggle Button Handler
  window.toggleSwissQuickMode = function () {
    setQuickMode(!isQuickMode);
  };

  // Get custom Quick Mode winning score from pool/round input or saved state (defaults to 1)
  function getQuickModeWinScore(cardNode, rNum, pKey) {
    if (cardNode) {
      var poolBox = cardNode.closest('.swiss-pool-card, .swiss-list-pool-box');
      if (poolBox) {
        var poolInp = poolBox.querySelector('.pool-random-input');
        if (poolInp && poolInp.value && parseInt(poolInp.value, 10) > 0) {
          return parseInt(poolInp.value, 10);
        }
      }
      var roundBox = cardNode.closest('.swiss-bracket-column, .swiss-list-round-card');
      if (roundBox) {
        var roundInp = roundBox.querySelector('.round-random-input');
        if (roundInp && roundInp.value && parseInt(roundInp.value, 10) > 0) {
          return parseInt(roundInp.value, 10);
        }
      }
    }
    if (pKey && poolRandomScores[pKey] && parseInt(poolRandomScores[pKey], 10) > 0) {
      return parseInt(poolRandomScores[pKey], 10);
    }
    if (rNum && roundRandomScores[rNum] && parseInt(roundRandomScores[rNum], 10) > 0) {
      return parseInt(roundRandomScores[rNum], 10);
    }
    return 1;
  }

  // Global Capture-phase Listener for Quick Mode Clicks to PREVENT POPUP (Winner Score = 1)
  document.addEventListener('click', function(e) {
    if (!window.TourmaQuickMode && !isQuickMode) return;

    // Do NOT intercept clicks on inputs, buttons, or random controls!
    if (e.target.closest('input, button, select, .pool-header-random-controls, .round-header-random-controls, .btn-pool-random, .btn-round-random, .btn-round-reset, .pool-random-input, .round-random-input')) {
      return;
    }

    var cardNode = e.target.closest('.bracket-node-card, .match-card-item, .swiss-card-item');
    if (!cardNode) return;

    var mKey = cardNode.getAttribute('data-match-id') || cardNode.getAttribute('data-match-key');
    if (!mKey && cardNode.onclick) {
      var matches = Object.keys(matchesMap);
      for (var i = 0; i < matches.length; i++) {
        var item = matchesMap[matches[i]];
        if (item && cardNode.innerText && cardNode.innerText.includes(item.team1 ? item.team1.name : '___')) {
          mKey = item.matchKey;
          break;
        }
      }
    }

    if (!mKey || !matchesMap[mKey]) return;

    var m = matchesMap[mKey];
    var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
    var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
    if (t1Name === 'TBD' || t2Name === 'TBD' || t1Name === 'BYE' || t2Name === 'BYE') return;

    // PREVENT POPUP MODAL FROM OPENING IN QUICK MODE!
    e.preventDefault();
    e.stopPropagation();
    e.stopImmediatePropagation();

    var isT2 = false;
    var teamRow = e.target.closest('.bracket-team-row, .match-card-team-row, .swiss-card-row, .team-row');
    if (teamRow) {
      var txt = teamRow.innerText || '';
      if (txt.includes(t2Name)) isT2 = true;
    }

    var wScoreVal = getQuickModeWinScore(cardNode, m.roundIndex, m.recordPool);
    var lScoreVal = (wScoreVal > 0) ? Math.floor(Math.random() * wScoreVal) : 0;

    if (isT2) {
      m.team1Score = lScoreVal;
      m.team2Score = wScoreVal;
      m.winnerId = 'team2';
    } else {
      m.team1Score = wScoreVal;
      m.team2Score = lScoreVal;
      m.winnerId = 'team1';
    }
    m.status = 'COMPLETED';

    // CASCADING RESET: Invalidate all future rounds beyond this match's round!
    invalidateFutureRounds(m.roundIndex);

    saveSwissMatches();
    if (currentViewMode === 'LIST') renderListView();
    else renderBracketView();
  }, true);

  // Auto Generate Next Round when all matches in current max round are completed!
  function checkAndAutoAdvanceRound() {
    var maxRound = 1;
    Object.keys(matchesMap).forEach(function (k) {
      var m = matchesMap[k];
      if (m && (m.status === 'COMPLETED' || m.status === 'DONE')) {
        if (m.roundIndex > maxRound) maxRound = m.roundIndex;
      }
    });

    if (maxRound >= 5) return; // Swiss maximum 5 rounds

    var allDone = true;
    var countInMaxRound = 0;
    Object.keys(matchesMap).forEach(function (k) {
      var m = matchesMap[k];
      if (m && m.roundIndex === maxRound) {
        countInMaxRound++;
        var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
        var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
        if (t1Name !== 'TBD' && t2Name !== 'TBD' && t1Name !== 'BYE' && t2Name !== 'BYE') {
          if (m.status !== 'COMPLETED' && m.status !== 'DONE') {
            allDone = false;
          }
        } else {
          allDone = false;
        }
      }
    });

    if (countInMaxRound > 0 && allDone) {
      var nextRoundNumber = maxRound + 1;
      if (window.TourmaSwissAlgorithm) {
        var standings = window.TourmaSwissAlgorithm.calculateStandings(teamsList, matchesMap);
        var newMatches = window.TourmaSwissAlgorithm.generateNextRound(standings, matchesMap, nextRoundNumber);
        if (newMatches && newMatches.length > 0) {
          // Group new matches by recordPool
          var byPool = {};
          newMatches.forEach(function (nm) {
            if (!byPool[nm.recordPool]) byPool[nm.recordPool] = [];
            byPool[nm.recordPool].push(nm);
          });

          Object.keys(byPool).forEach(function (pKey) {
            var poolMatches = byPool[pKey];
            var targetSlots = [];
            Object.keys(matchesMap).forEach(function (k) {
              var existing = matchesMap[k];
              if (existing && existing.roundIndex === nextRoundNumber && 
                  (existing.recordPool === pKey || (nextRoundNumber === 1 && pKey === '0-0'))) {
                targetSlots.push(existing);
              }
            });

            // Assign pairings into pre-allocated match slots
            for (var pIdx = 0; pIdx < poolMatches.length; pIdx++) {
              var pMatch = poolMatches[pIdx];
              if (pIdx < targetSlots.length) {
                targetSlots[pIdx].team1 = pMatch.team1;
                targetSlots[pIdx].team2 = pMatch.team2;
                targetSlots[pIdx].team1Score = 0;
                targetSlots[pIdx].team2Score = 0;
                targetSlots[pIdx].status = 'READY';
              } else {
                matchesMap[pMatch.matchKey] = pMatch;
              }
            }
          });
        }
      }
    }
  }

  // Randomize Matches in Current Maximum Round
  window.randomizeSwissMatches = function () {
    var maxRound = 1;
    Object.keys(matchesMap).forEach(function (k) {
      var m = matchesMap[k];
      if (m && m.roundIndex > maxRound && m.status !== 'PENDING') maxRound = m.roundIndex;
    });
    randomizeRoundMatches(maxRound);
  };

  // Randomize Matches in Specific Round
  function randomizeRoundMatches(rNum, winScore) {
    var customScore = (winScore && parseInt(winScore, 10) > 0) ? parseInt(winScore, 10) : 0;

    var playableCount = 0;
    Object.keys(matchesMap).forEach(function (k) {
      var m = matchesMap[k];
      if (m && m.roundIndex === rNum) {
        var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
        var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
        if (t1Name !== 'TBD' && t2Name !== 'TBD' && t1Name !== 'BYE' && t2Name !== 'BYE') {
          playableCount++;
        }
      }
    });

    if (playableCount === 0) return;

    Object.keys(matchesMap).forEach(function (k) {
      var m = matchesMap[k];
      if (m && m.roundIndex === rNum) {
        var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
        var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
        if (t1Name !== 'TBD' && t2Name !== 'TBD' && t1Name !== 'BYE' && t2Name !== 'BYE') {
          var isT1Win = Math.random() < 0.5;
          var wScore, lScore;

          if (customScore > 0) {
            wScore = customScore;
            lScore = Math.floor(Math.random() * customScore);
          } else if (isQuickMode || window.TourmaQuickMode) {
            wScore = 1;
            lScore = 0;
          } else {
            var res = getRandomDecisiveScores();
            wScore = res.winScore;
            lScore = res.loseScore;
          }

          m.team1Score = isT1Win ? wScore : lScore;
          m.team2Score = isT1Win ? lScore : wScore;
          m.winnerId = isT1Win ? 'team1' : 'team2';
          m.status = 'COMPLETED';
        }
      }
    });

    // CASCADING RESET: Invalidate all future rounds beyond rNum!
    invalidateFutureRounds(rNum);

    saveSwissMatches();
    if (currentViewMode === 'LIST') renderListView();
    else renderBracketView();
  }

  // Randomize Matches in Specific Record Pool
  function randomizePoolMatches(rNum, poolKey, winScore) {
    var customScore = (winScore && parseInt(winScore, 10) > 0) ? parseInt(winScore, 10) : 0;

    var playableCount = 0;
    Object.keys(matchesMap).forEach(function (k) {
      var m = matchesMap[k];
      if (m && m.roundIndex === rNum && (m.recordPool === poolKey || (!m.recordPool && poolKey === '0-0'))) {
        var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
        var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
        if (t1Name !== 'TBD' && t2Name !== 'TBD' && t1Name !== 'BYE' && t2Name !== 'BYE') {
          playableCount++;
        }
      }
    });

    if (playableCount === 0) return;

    Object.keys(matchesMap).forEach(function (k) {
      var m = matchesMap[k];
      if (m && m.roundIndex === rNum && (m.recordPool === poolKey || (!m.recordPool && poolKey === '0-0'))) {
        var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
        var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
        if (t1Name !== 'TBD' && t2Name !== 'TBD' && t1Name !== 'BYE' && t2Name !== 'BYE') {
          var isT1Win = Math.random() < 0.5;
          var wScore, lScore;

          if (customScore > 0) {
            wScore = customScore;
            lScore = Math.floor(Math.random() * customScore);
          } else if (isQuickMode || window.TourmaQuickMode) {
            wScore = 1;
            lScore = 0;
          } else {
            var res = getRandomDecisiveScores();
            wScore = res.winScore;
            lScore = res.loseScore;
          }

          m.team1Score = isT1Win ? wScore : lScore;
          m.team2Score = isT1Win ? lScore : wScore;
          m.winnerId = isT1Win ? 'team1' : 'team2';
          m.status = 'COMPLETED';
        }
      }
    });

    // CASCADING RESET: Invalidate all future rounds beyond rNum!
    invalidateFutureRounds(rNum);

    saveSwissMatches();
    if (currentViewMode === 'LIST') renderListView();
    else renderBracketView();
  }

  // Reset Matches in Specific Round (CASCADING RESET ALL FUTURE ROUNDS!)
  function resetRoundMatches(rNum) {
    Object.keys(matchesMap).forEach(function (k) {
      var m = matchesMap[k];
      if (m && m.roundIndex === rNum) {
        var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
        var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
        m.team1Score = 0;
        m.team2Score = 0;
        if (t1Name !== 'TBD' && t2Name !== 'TBD') {
          m.status = 'READY';
        } else {
          m.status = 'PENDING';
        }
      }
    });

    // CASCADING RESET: Revert all matches in rounds > rNum back to TBD!
    invalidateFutureRounds(rNum);

    saveSwissMatches();
    if (currentViewMode === 'LIST') renderListView();
    else renderBracketView();
  }

  // Initialize Full 5-Round Swiss Matches Structure (33 Matches Total with Unique Round Match Counters)
  function initFullSwissMatchesStructure() {
    matchesMap = {};
    if (!teamsList || teamsList.length === 0) return;

    var globalMatchCount = 1;
    var shuffled = teamsList.slice().sort(function() { return 0.5 - Math.random(); });

    SWISS_STRUCTURE.forEach(function(rStruct) {
      var rNum = rStruct.roundIndex;
      var roundMatchCounter = 1;

      rStruct.pools.forEach(function(pStruct) {
        var pKey = pStruct.key;
        var mCount = pStruct.count;

        for (var i = 1; i <= mCount; i++) {
          var mKey = 'R' + rNum + '_M' + roundMatchCounter;
          var t1 = { name: 'TBD' };
          var t2 = { name: 'TBD' };

          // Round 1 matches get the actual 16 teams initially!
          if (rNum === 1) {
            var idx1 = (roundMatchCounter - 1) * 2;
            var idx2 = idx1 + 1;
            var n1 = (typeof shuffled[idx1] === 'object') ? (shuffled[idx1].name || shuffled[idx1].id) : shuffled[idx1];
            var n2 = shuffled[idx2] ? ((typeof shuffled[idx2] === 'object') ? (shuffled[idx2].name || shuffled[idx2].id) : shuffled[idx2]) : 'BYE';
            t1 = { name: n1 };
            t2 = { name: n2 };
          }

          matchesMap[mKey] = {
            matchKey: mKey,
            roundIndex: rNum,
            matchNumber: globalMatchCount++,
            recordPool: pKey,
            team1: t1,
            team2: t2,
            team1Score: 0,
            team2Score: 0,
            status: (rNum === 1) ? 'READY' : 'PENDING'
          };

          roundMatchCounter++;
        }
      });
    });

    saveSwissMatches();
  }

  // Validate and Repair Matches Map Structure to guarantee 33 unique matches exist
  function validateAndRepairMatchesMap() {
    if (!matchesMap || typeof matchesMap !== 'object' || Object.keys(matchesMap).length < 33) {
      initFullSwissMatchesStructure();
      return;
    }

    var isStructureValid = true;
    SWISS_STRUCTURE.forEach(function(rStruct) {
      var rNum = rStruct.roundIndex;
      rStruct.pools.forEach(function(pStruct) {
        var pKey = pStruct.key;
        var found = 0;
        Object.keys(matchesMap).forEach(function(k) {
          var m = matchesMap[k];
          if (m && m.roundIndex === rNum && (m.recordPool === pKey || (rNum === 1 && pKey === '0-0'))) {
            found++;
          }
        });
        if (found < pStruct.count) {
          isStructureValid = false;
        }
      });
    });

    if (!isStructureValid) {
      initFullSwissMatchesStructure();
    }
  }

  // Check Swiss Stage Completion and Trigger Multi-Stage Pipeline or Final Popup
  function checkSwissStageCompletion() {
    if (!window.TourmaSwissAlgorithm || !teamsList || teamsList.length === 0) return;
    var standings = window.TourmaSwissAlgorithm.calculateStandings(teamsList, matchesMap);
    var qualifiedTeams = standings.filter(function(st) { return st.qualified; });

    var stageParam = new URLSearchParams(window.location.search).get('stage');
    var currentStage = (stageParam === '2' || stageParam === 2) ? 2 : 1;

    if (currentStage === 1) {
      // Check if 8 teams qualified — trigger Stage 2 cut pipeline
      if (qualifiedTeams.length === 8) {
        var multiCfgRaw = localStorage.getItem('tourma_multi_config_' + tournamentId);
        if (multiCfgRaw) {
          try {
            var multiCfg = JSON.parse(multiCfgRaw);

            // Group qualified teams strictly by Record Pool:
            // Pool 3-0 (2 teams) -> Seeds 1, 2
            // Pool 3-1 (3 teams) -> Seeds 3, 4, 5
            // Pool 3-2 (3 teams) -> Seeds 6, 7, 8
            var pool30 = standings.filter(function(st) { return st.wins === 3 && st.losses === 0; });
            var pool31 = standings.filter(function(st) { return st.wins === 3 && st.losses === 1; });
            var pool32 = standings.filter(function(st) { return st.wins === 3 && st.losses === 2; });

            // Sort within each pool by Buchholz / Diff DESC
            var sortByBuchholz = function(a, b) {
              if (b.buchholz !== a.buchholz) return b.buchholz - a.buchholz;
              if (b.diff !== a.diff) return b.diff - a.diff;
              return b.scoresFor - a.scoresFor;
            };
            pool30.sort(sortByBuchholz);
            pool31.sort(sortByBuchholz);
            pool32.sort(sortByBuchholz);

            var finalSeededTeams = [].concat(pool30, pool31, pool32);
            if (finalSeededTeams.length !== 8) {
              finalSeededTeams = qualifiedTeams;
            }

            var shuffledStage2Teams = finalSeededTeams.map(function(t, idx) {
              return {
                name: t.name,
                rawName: t.name,
                seed: idx + 1
              };
            });

            // Check if stage2 teams are already saved and match current qualified teams
            var existingS2Raw = localStorage.getItem('tourma_stage2_teams_' + tournamentId);
            var isSameS2Teams = false;
            if (existingS2Raw) {
              try {
                var existingS2 = JSON.parse(existingS2Raw);
                if (Array.isArray(existingS2) && existingS2.length === shuffledStage2Teams.length) {
                  var allMatched = true;
                  for (var si = 0; si < shuffledStage2Teams.length; si++) {
                    var curName = shuffledStage2Teams[si].name;
                    var oldName = (typeof existingS2[si] === 'object') ? (existingS2[si].name || existingS2[si].rawName) : existingS2[si];
                    if (curName !== oldName) {
                      allMatched = false;
                      break;
                    }
                  }
                  if (allMatched) {
                    isSameS2Teams = true;
                  }
                }
              } catch(e) {}
            }

            // Only generate new Stage 2 matches if teams changed or stage 2 matches not yet created
            if (!isSameS2Teams || !multiCfg.stage2MatchesCreated) {
              localStorage.setItem('tourma_stage2_teams_' + tournamentId, JSON.stringify(shuffledStage2Teams));

              var s2Format = multiCfg.stage2Format || 'SINGLE_ELIMINATION';
              if (s2Format === 'SINGLE_ELIMINATION') {
                if (window.TourmaBracketAlgorithm && typeof window.TourmaBracketAlgorithm.generateSingleElimination === 'function') {
                  var seBracket = window.TourmaBracketAlgorithm.generateSingleElimination(shuffledStage2Teams, 0);
                  localStorage.setItem('tourma_bracket_stage2_' + tournamentId, JSON.stringify(seBracket));
                  localStorage.setItem('tourma_matches_stage2_' + tournamentId, JSON.stringify(seBracket.matchesMap || {}));
                }
              } else if (s2Format === 'DOUBLE_ELIMINATION') {
                var doubleEngine = window.TourmaDoubleElimAlgorithm || window.TourmaDoubleEliminationAlgorithm;
                if (doubleEngine && typeof doubleEngine.generateDoubleElimination === 'function') {
                  var deBracket = doubleEngine.generateDoubleElimination(shuffledStage2Teams);
                  localStorage.setItem('tourma_de_matches_' + tournamentId, JSON.stringify(deBracket));
                }
              } else if (s2Format === 'ROUND_ROBIN') {
                if (window.TourmaRoundRobinAlgorithm && typeof window.TourmaRoundRobinAlgorithm.generateRoundRobin === 'function') {
                  var rrBracket = window.TourmaRoundRobinAlgorithm.generateRoundRobin(shuffledStage2Teams, multiCfg.stage2Config);
                  localStorage.setItem('tourma_rr_matches_' + tournamentId, JSON.stringify(rrBracket));
                }
              }

              multiCfg.stage2MatchesCreated = true;
              localStorage.setItem('tourma_multi_config_' + tournamentId, JSON.stringify(multiCfg));
            }
          } catch(e) {}
        }
      }
      // Multi-Stage Stage 1: always refresh StageEndPopup (show confirm / locked banner)
      if (window.StageEndPopup) {
        window.StageEndPopup.update(
          tournamentId,
          'SWISS',
          matchesMap,
          teamsList,
          null,
          null,
          1
        );
      }
    } else {
      // Stage 2 Swiss or Single Stage Swiss -> Check FinalStagePopup
      if (window.FinalStagePopup && standings.length > 0) {
        window.FinalStagePopup.checkAndRender(
          tournamentId,
          'SWISS',
          matchesMap,
          teamsList,
          null,
          null
        );
      }
    }
  }

  // Standalone Reset Matches Handler
  window.resetSwissMatches = function () {
    var stageParam = new URLSearchParams(window.location.search).get('stage');
    var currentStage = (stageParam === '2' || stageParam === 2) ? 2 : 1;
    if (currentStage === 1 && window.StageEndPopup && typeof window.StageEndPopup.isStage1Locked === 'function' && window.StageEndPopup.isStage1Locked(tournamentId)) {
      alert('Vòng 1 đã hoàn tất và đang ở trạng thái khóa. Vui lòng bấm "Mở khóa để sửa" trên thanh thông báo nếu bạn muốn thiết lập lại.');
      return;
    }
    if (window.FinalStagePopup && window.FinalStagePopup.isLocked) {
      alert('Giải đấu đã kết thúc và đang ở trạng thái khóa. Vui lòng bấm "Mở khóa" trên thanh thông báo nếu muốn reset giải.');
      return;
    }
    if (!confirm('Bạn có chắc chắn muốn xóa toàn bộ tỷ số và đặt lại giai đoạn Swiss về Vòng 1 ban đầu?')) {
      return;
    }
    var storageKey = (currentStage === 2) ? ("tourma_swiss_matches_stage2_" + tournamentId) : ("tourma_swiss_matches_" + tournamentId);
    try { localStorage.removeItem(storageKey); } catch(e) {}
    initFullSwissMatchesStructure();
    if (currentStage === 1) {
      try {
        localStorage.removeItem('tourma_stage2_teams_' + tournamentId);
        localStorage.removeItem('tourma_bracket_stage2_' + tournamentId);
        localStorage.removeItem('tourma_matches_stage2_' + tournamentId);
        localStorage.removeItem('tourma_de_matches_' + tournamentId);
        localStorage.removeItem('tourma_rr_matches_' + tournamentId);
        localStorage.removeItem('tourma_stage1_completed_' + tournamentId);
        var mCfgRaw = localStorage.getItem('tourma_multi_config_' + tournamentId);
        if (mCfgRaw) {
          var mCfg = JSON.parse(mCfgRaw);
          mCfg.stage2MatchesCreated = false;
          localStorage.setItem('tourma_multi_config_' + tournamentId, JSON.stringify(mCfg));
        }
      } catch(e) {}
    }
    if (currentViewMode === 'LIST') renderListView();
    else renderBracketView();
  };

  // Initialize Swiss Engine
  function initSwissEngine() {
    if (!tournamentId || tournamentId === 'demo') {
      var urlParams = new URLSearchParams(window.location.search);
      var queryId = urlParams.get('id');
      if (queryId && queryId.trim().length > 0) {
        tournamentId = queryId.trim();
        window.swissTournamentId = tournamentId;
      }
    }

    var stageParam = new URLSearchParams(window.location.search).get('stage');
    var currentStage = (stageParam === '2' || stageParam === 2) ? 2 : 1;
    var storageKeyTeams = (currentStage === 2) ? ("tourma_stage2_teams_" + tournamentId) : ("tourma_teams_" + tournamentId);
    var storageKeyMatches = (currentStage === 2) ? ("tourma_swiss_matches_stage2_" + tournamentId) : ("tourma_swiss_matches_" + tournamentId);

    // Restore persisted View Mode from localStorage
    try {
      var savedViewMode = localStorage.getItem("tourma_swiss_view_mode_" + tournamentId);
      if (savedViewMode === 'LIST' || savedViewMode === 'BRACKET') {
        currentViewMode = savedViewMode;
      }
    } catch(e) {}

    // Restore persisted Quick Mode state from localStorage
    try {
      var savedQM = localStorage.getItem("tourma_swiss_quick_mode_" + tournamentId);
      if (savedQM === 'true') {
        setQuickMode(true);
      } else {
        setQuickMode(false);
      }
    } catch(e) {}

    // 1. Teams List Loading
    teamsList = [];
    
    try {
      var rawTeams = localStorage.getItem(storageKeyTeams);
      if (rawTeams) {
        var parsed = JSON.parse(rawTeams);
        if (Array.isArray(parsed) && parsed.length > 0) {
          teamsList = parsed;
        }
      }
    } catch (e) {}

    if (currentStage === 1 && teamsList.length !== 16 && window.serverTeams && window.serverTeams.length === 16) {
      teamsList = window.serverTeams;
      try { localStorage.setItem(storageKeyTeams, JSON.stringify(teamsList)); } catch (e) {}
    }

    if ((!teamsList || teamsList.length === 0) && window.serverTeams && Array.isArray(window.serverTeams) && window.serverTeams.length > 0) {
      teamsList = window.serverTeams;
    }

    if (!teamsList || teamsList.length === 0) {
      try {
        var genericTeams = localStorage.getItem("tourma_teams");
        if (genericTeams) {
          var parsedGeneric = JSON.parse(genericTeams);
          if (Array.isArray(parsedGeneric) && parsedGeneric.length > 0) {
            teamsList = parsedGeneric;
          }
        }
      } catch(e) {}
    }

    // Default 16 demo teams fallback
    if (!teamsList || teamsList.length === 0) {
      teamsList = [
        "Team Flash", "GAM Esports", "Team Secret", "Saigon Buffalo",
        "CERBERUS Esports", "MGN Box Esports", "Team Whales", "Rainbow 7",
        "T1", "Gen.G", "Fnatic", "G2 Esports",
        "Cloud9", "NRG Esports", "BLG Esports", "JD Gaming"
      ];
    }

    // Normalize team names array
    teamsList = teamsList.map(function(t) {
      if (typeof t === 'object' && t !== null) {
        return t.name || t.rawName || t.id || 'Đội chưa đặt tên';
      }
      return String(t);
    });

    // Update Team Count Badge
    var countBadge = document.getElementById('swissTeamCountBadge');
    if (countBadge) countBadge.innerText = teamsList.length + ' Đội';

    // 2. Check if Stage 2 is locked (Stage 1 not yet completed or not confirmed)
    var alertBox = document.getElementById('swissInvalidTeamAlert');
    var mainWrapper = document.getElementById('swissMainContentWrapper');
    var descEl = document.getElementById('swissInvalidTeamDesc');

    if (window.StageFinishAlert && typeof window.StageFinishAlert.checkAndRender === 'function') {
      var isStage2Locked = window.StageFinishAlert.checkAndRender(tournamentId, currentStage, 'stageFinishAlertContainer');
      if (isStage2Locked) {
        if (mainWrapper) mainWrapper.style.display = 'none';
        if (alertBox) alertBox.style.display = 'none';
        var ctrlBar = document.querySelector('.swiss-control-bar');
        if (ctrlBar) ctrlBar.style.display = 'none';
        return; // Stop rendering Stage 2 Swiss!
      }
    }

    if (teamsList.length !== 16) {
      if (alertBox && mainWrapper) {
        alertBox.style.display = 'block';
        mainWrapper.style.display = 'none';
        if (descEl) {
          descEl.innerHTML = 'Thể thức Swiss System đòi hỏi bắt buộc phải có <strong>đúng 16 đội bóng</strong> tham gia. Hiện tại giải đấu đang có <strong style="color: #f43f5e; font-weight: 800;">' + teamsList.length + ' đội</strong>.<br>Vui lòng thêm hoặc điều chỉnh danh sách cho đủ 16 đội để xem lịch thi đấu Swiss.';
        }
      }
      return;
    } else {
      if (alertBox && mainWrapper) {
        alertBox.style.display = 'none';
        mainWrapper.style.display = 'block';
      }
    }

    // 3. Matches Loading
    try {
      var rawM = localStorage.getItem(storageKeyMatches);
      if (rawM) {
        matchesMap = JSON.parse(rawM);
      }
    } catch (e) {}

    // Auto-heal / repair matchesMap structure
    validateAndRepairMatchesMap();

    // Render initial view mode
    switchSwissViewMode(currentViewMode);
    checkSwissStageCompletion();
  }

  function saveSwissMatches() {
    try {
      checkAndAutoAdvanceRound(); // AUTO GENERATE NEXT ROUND UPON COMPLETION!
      var stageParam = new URLSearchParams(window.location.search).get('stage');
      var currentStage = (stageParam === '2' || stageParam === 2) ? 2 : 1;
      var storageKey = (currentStage === 2) ? ("tourma_swiss_matches_stage2_" + tournamentId) : ("tourma_swiss_matches_" + tournamentId);
      localStorage.setItem(storageKey, JSON.stringify(matchesMap));
      checkSwissStageCompletion();
    } catch (e) {}
  }

  // View Mode Switcher: LIST vs BRACKET (Persisted to localStorage)
  window.switchSwissViewMode = function (mode) {
    currentViewMode = mode;
    try {
      localStorage.setItem("tourma_swiss_view_mode_" + tournamentId, mode);
    } catch(e) {}

    var listView = document.getElementById('swissListView');
    var bracketView = document.getElementById('swissBracketView');
    var btnList = document.getElementById('btnSwissViewList');
    var btnBracket = document.getElementById('btnSwissViewBracket');

    if (mode === 'LIST') {
      if (listView) listView.style.display = 'block';
      if (bracketView) bracketView.style.display = 'none';
      if (btnList) btnList.classList.add('active');
      if (btnBracket) btnBracket.classList.remove('active');
      renderListView();
    } else {
      if (listView) listView.style.display = 'none';
      if (bracketView) bracketView.style.display = 'block';
      if (btnBracket) btnBracket.classList.add('active');
      if (btnList) btnList.classList.remove('active');
      renderBracketView();
      
      // Initialize Viewport Drag & Zoom
      if (window.TourmaViewport && typeof window.TourmaViewport.init === 'function') {
        setTimeout(function() {
          window.TourmaViewport.init('swissViewportContainer', 'swissViewportCanvas', {
            badgeId: 'swissZoomBadge'
          });
        }, 50);
      }
    }
  };

  // Render MODE 1: LIST VIEW (Using TourmaMatchCard)
  function renderListView() {
    try {
      validateAndRepairMatchesMap();
      renderListRoundPills();
      renderListFixtures();
    } catch(err) {
      initFullSwissMatchesStructure();
      renderListRoundPills();
      renderListFixtures();
    }
  }

  function renderListRoundPills() {
    var container = document.getElementById('swissListRoundPillsContainer');
    if (!container) return;
    container.innerHTML = '';

    // Pill: Tất Cả Các Vòng
    var btnAll = document.createElement('button');
    btnAll.type = 'button';
    btnAll.className = 'swiss-round-pill-btn' + (activeRoundFilter === 'ALL' ? ' active' : '');
    btnAll.innerText = 'Tất Cả Các Vòng';
    btnAll.onclick = function() {
      activeRoundFilter = 'ALL';
      renderListView();
    };
    container.appendChild(btnAll);

    // Render all 5 Round Pills ALWAYS
    for (var i = 1; i <= 5; i++) {
      (function(rNum) {
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'swiss-round-pill-btn' + (activeRoundFilter === rNum ? ' active' : '');
        btn.innerText = 'Vòng ' + rNum;
        btn.onclick = function() {
          activeRoundFilter = rNum;
          renderListView();
        };
        container.appendChild(btn);
      })(i);
    }
  }

  function renderListFixtures() {
    var container = document.getElementById('swissListFixturesContainer');
    if (!container) return;
    container.innerHTML = '';

    SWISS_STRUCTURE.forEach(function (rStruct) {
      var rNum = rStruct.roundIndex;
      if (activeRoundFilter !== 'ALL' && activeRoundFilter !== rNum) return;

      var roundCard = document.createElement('div');
      roundCard.className = 'swiss-list-round-card';

      // Check if all matches in round rNum are already completed
      var isRoundCompleted = true;
      var hasPlayableMatches = false;

      Object.keys(matchesMap).forEach(function (k) {
        var m = matchesMap[k];
        if (m && m.roundIndex === rNum) {
          var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
          var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
          if (t1Name !== 'TBD' && t2Name !== 'TBD' && t1Name !== 'BYE' && t2Name !== 'BYE') {
            hasPlayableMatches = true;
            if (m.status !== 'COMPLETED' && m.status !== 'DONE') {
              isRoundCompleted = false;
            }
          } else {
            isRoundCompleted = false;
          }
        }
      });

      // Outer main title in GOLD with SE/DE Round Header Random Controls
      var roundHeader = document.createElement('div');
      roundHeader.className = 'swiss-list-round-header';

      var rTitle = document.createElement('div');
      rTitle.className = 'swiss-list-round-title';
      rTitle.innerText = 'Vòng ' + rNum;
      roundHeader.appendChild(rTitle);

      var rControls = document.createElement('div');
      rControls.className = 'round-header-random-controls';

      var rInp = document.createElement('input');
      rInp.type = 'number';
      rInp.className = 'round-random-input';
      rInp.value = roundRandomScores[rNum] || '';
      rInp.placeholder = '-';
      rInp.min = '1';
      if (!hasPlayableMatches) rInp.disabled = true;

      rInp.oninput = function() {
        roundRandomScores[rNum] = this.value;
      };

      var rBtn = document.createElement('button');
      rBtn.type = 'button';
      rBtn.className = 'btn-round-random';
      rBtn.innerText = 'Random';
      if (!hasPlayableMatches) rBtn.disabled = true;

      var rResetBtn = document.createElement('button');
      rResetBtn.type = 'button';
      rResetBtn.className = 'btn-round-reset';
      rResetBtn.innerHTML = '<i class="fa-solid fa-arrows-rotate"></i>';

      (function(rNumber, inp) {
        inp.onclick = function(e) { e.stopPropagation(); };
        rBtn.onclick = function(e) {
          e.stopPropagation();
          randomizeRoundMatches(rNumber, inp.value);
        };
        rResetBtn.onclick = function(e) {
          e.stopPropagation();
          resetRoundMatches(rNumber);
        };
      })(rNum, rInp);

      rControls.appendChild(rInp);
      rControls.appendChild(rBtn);
      rControls.appendChild(rResetBtn);
      roundHeader.appendChild(rControls);

      roundCard.appendChild(roundHeader);

      // Render pools in fixed structure order
      rStruct.pools.forEach(function (pStruct) {
        var pKey = pStruct.key;
        var poolBox = document.createElement('div');
        poolBox.className = 'swiss-list-pool-box';

        var poolMatches = [];
        Object.keys(matchesMap).forEach(function (k) {
          var m = matchesMap[k];
          if (m && m.roundIndex === rNum && (m.recordPool === pKey || (!m.recordPool && pKey === '0-0'))) {
            poolMatches.push(m);
          }
        });

        // Check if pool matches are playable or completed
        var isPoolCompleted = true;
        var hasPoolPlayable = false;

        poolMatches.forEach(function (pm) {
          var t1Name = pm.team1 ? (pm.team1.name || pm.team1) : 'TBD';
          var t2Name = pm.team2 ? (pm.team2.name || pm.team2) : 'TBD';
          if (t1Name !== 'TBD' && t2Name !== 'TBD' && t1Name !== 'BYE' && t2Name !== 'BYE') {
            hasPoolPlayable = true;
            if (pm.status !== 'COMPLETED' && pm.status !== 'DONE') {
              isPoolCompleted = false;
            }
          } else {
            isPoolCompleted = false;
          }
        });

        var colorClass = getPoolColorClass(pKey);
        var cardColorClass = getPoolCardClass(pKey);

        var poolHeader = document.createElement('div');
        poolHeader.className = 'swiss-list-pool-header ' + colorClass;

        var poolTitle = document.createElement('span');
        poolTitle.innerText = pKey;

        var poolControls = document.createElement('div');
        poolControls.className = 'pool-header-random-controls';

        var poolInp = document.createElement('input');
        poolInp.type = 'number';
        poolInp.className = 'pool-random-input';
        poolInp.value = poolRandomScores[pKey] || '';
        poolInp.placeholder = '-';
        poolInp.min = '1';
        if (!hasPoolPlayable) poolInp.disabled = true;

        poolInp.oninput = function() {
          poolRandomScores[pKey] = this.value;
        };

        var poolRandBtn = document.createElement('button');
        poolRandBtn.type = 'button';
        poolRandBtn.className = 'btn-pool-random';
        poolRandBtn.title = 'Random tỷ số cho nhóm ' + pKey;
        poolRandBtn.innerHTML = '<i class="fa-solid fa-dice"></i> Random';
        if (!hasPoolPlayable) poolRandBtn.disabled = true;

        (function(rNumber, keyStr, inp) {
          inp.onclick = function(e) { e.stopPropagation(); };
          poolRandBtn.onclick = function(e) {
            e.stopPropagation();
            randomizePoolMatches(rNumber, keyStr, inp.value);
          };
        })(rNum, pKey, poolInp);

        poolControls.appendChild(poolInp);
        poolControls.appendChild(poolRandBtn);

        poolHeader.appendChild(poolTitle);
        poolHeader.appendChild(poolControls);
        poolBox.appendChild(poolHeader);

        var cardColorClass = getPoolCardClass(pKey);
        var themeColor = (pKey === '2-0' || pKey === '2-1') ? 'green' : ((pKey === '0-2' || pKey === '1-2') ? 'red' : ((pKey === '2-2') ? 'gold' : 'mint'));

        var grid = document.createElement('div');
        grid.className = 'swiss-list-matches-grid';

        poolMatches.forEach(function (m) {
          var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
          var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';

          var winnerId = null;
          if (m.status === 'COMPLETED' || m.status === 'DONE') {
            if (m.team1Score > m.team2Score) winnerId = 'team1';
            else if (m.team2Score > m.team1Score) winnerId = 'team2';
            else winnerId = 'draw';
          }

          var cardEl = null;
          if (window.TourmaMatchCard && typeof window.TourmaMatchCard.createCardElement === 'function') {
            cardEl = window.TourmaMatchCard.createCardElement({
              matchId: m.matchKey,
              matchNumber: m.matchNumber,
              status: m.status || 'PENDING',
              team1: { name: t1Name, score: m.team1Score },
              team2: { name: t2Name, score: m.team2Score },
              winnerId: winnerId,
              themeColor: themeColor,
              hideSeeds: true
            });
          }

          if (!cardEl) {
            cardEl = document.createElement('div');
            cardEl.className = 'swiss-card-item';
            cardEl.innerText = t1Name + ' vs ' + t2Name;
          }

          cardEl.setAttribute('data-match-key', m.matchKey);
          cardEl.classList.add(cardColorClass);
          if (t1Name === 'TBD' || t2Name === 'TBD') {
            cardEl.classList.add('disabled-unconfirmed');
          } else {
            cardEl.onclick = function(evt) { handleMatchClick(m, evt); };
          }
          grid.appendChild(cardEl);
        });

        poolBox.appendChild(grid);
        roundCard.appendChild(poolBox);
      });

      container.appendChild(roundCard);
    });
  }

  // Render MODE 2: BRACKET VIEWPORT CANVAS (Pre-renders ALL 5 Rounds with TBD Cards)
  function renderBracketView() {
    try {
      validateAndRepairMatchesMap();

      var canvas = document.getElementById('swissViewportCanvas');
      if (!canvas) return;
      canvas.innerHTML = '';

      SWISS_STRUCTURE.forEach(function (rStruct) {
        var r = rStruct.roundIndex;
        var col = document.createElement('div');
        col.className = 'swiss-bracket-column';

        // Collect all matches in round r
        var rMatches = [];
        Object.keys(matchesMap).forEach(function(k) {
          var m = matchesMap[k];
          if (m && m.roundIndex === r) rMatches.push(m);
        });

        // Check if all matches in round r are completed
        var isRoundCompleted = true;
        var hasPlayableMatches = false;

        rMatches.forEach(function (m) {
          var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
          var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
          if (t1Name !== 'TBD' && t2Name !== 'TBD' && t1Name !== 'BYE' && t2Name !== 'BYE') {
            hasPlayableMatches = true;
            if (m.status !== 'COMPLETED' && m.status !== 'DONE') {
              isRoundCompleted = false;
            }
          } else {
            isRoundCompleted = false;
          }
        });

        var header = document.createElement('div');
        header.className = 'swiss-bracket-col-header';
        
        var titleDiv = document.createElement('div');
        titleDiv.className = 'swiss-bracket-col-title';
        titleDiv.innerText = 'VÒNG ' + r;
        header.appendChild(titleDiv);

        // Injected SE/DE Round Header Controls (Input Box + Random + Reset)
        var rControls = document.createElement('div');
        rControls.className = 'round-header-random-controls';

        var rInp = document.createElement('input');
        rInp.type = 'number';
        rInp.className = 'round-random-input';
        rInp.value = roundRandomScores[r] || '';
        rInp.placeholder = '-';
        rInp.min = '1';
        if (!hasPlayableMatches) rInp.disabled = true;

        rInp.oninput = function() {
          roundRandomScores[r] = this.value;
        };

        var rBtn = document.createElement('button');
        rBtn.type = 'button';
        rBtn.className = 'btn-round-random';
        rBtn.innerText = 'Random';
        if (!hasPlayableMatches) rBtn.disabled = true;

        var rResetBtn = document.createElement('button');
        rResetBtn.type = 'button';
        rResetBtn.className = 'btn-round-reset';
        rResetBtn.innerHTML = '<i class="fa-solid fa-arrows-rotate"></i>';

        (function(rNum, inp) {
          inp.onclick = function(e) { e.stopPropagation(); };
          rBtn.onclick = function(e) {
            e.stopPropagation();
            randomizeRoundMatches(rNum, inp.value);
          };
          rResetBtn.onclick = function(e) {
            e.stopPropagation();
            resetRoundMatches(rNum);
          };
        })(r, rInp);

        rControls.appendChild(rInp);
        rControls.appendChild(rBtn);
        rControls.appendChild(rResetBtn);
        header.appendChild(rControls);

        col.appendChild(header);

        // Render pools in fixed structure order
        rStruct.pools.forEach(function (pStruct) {
          var pKey = pStruct.key;
          var poolCard = document.createElement('div');
          poolCard.className = 'swiss-pool-card';

          var poolMatches = [];
          rMatches.forEach(function(m) {
            if (m.recordPool === pKey || (!m.recordPool && pKey === '0-0')) {
              poolMatches.push(m);
            }
          });

          // Check if pool matches are playable or completed
          var isPoolCompleted = true;
          var hasPoolPlayable = false;

          poolMatches.forEach(function (pm) {
            var t1Name = pm.team1 ? (pm.team1.name || pm.team1) : 'TBD';
            var t2Name = pm.team2 ? (pm.team2.name || pm.team2) : 'TBD';
            if (t1Name !== 'TBD' && t2Name !== 'TBD' && t1Name !== 'BYE' && t2Name !== 'BYE') {
              hasPoolPlayable = true;
              if (pm.status !== 'COMPLETED' && pm.status !== 'DONE') {
                isPoolCompleted = false;
              }
            } else {
              isPoolCompleted = false;
            }
          });

          var colorClass = getPoolColorClass(pKey);
          var cardColorClass = getPoolCardClass(pKey);

          var poolHeader = document.createElement('div');
          poolHeader.className = 'swiss-pool-card-header ' + colorClass;

          var poolTitle = document.createElement('span');
          poolTitle.innerText = pKey;

          var poolControls = document.createElement('div');
          poolControls.className = 'pool-header-random-controls';

          var poolInp = document.createElement('input');
          poolInp.type = 'number';
          poolInp.className = 'pool-random-input';
          poolInp.value = poolRandomScores[pKey] || '';
          poolInp.placeholder = '-';
          poolInp.min = '1';
          if (!hasPoolPlayable) poolInp.disabled = true;

          poolInp.oninput = function() {
            poolRandomScores[pKey] = this.value;
          };

          var poolRandBtn = document.createElement('button');
          poolRandBtn.type = 'button';
          poolRandBtn.className = 'btn-pool-random';
          poolRandBtn.title = 'Random tỷ số cho nhóm ' + pKey;
          poolRandBtn.innerHTML = '<i class="fa-solid fa-dice"></i> Random';
          if (!hasPoolPlayable) poolRandBtn.disabled = true;

          (function(rNumber, keyStr, inp) {
            inp.onclick = function(e) { e.stopPropagation(); };
            poolRandBtn.onclick = function(e) {
              e.stopPropagation();
              randomizePoolMatches(rNumber, keyStr, inp.value);
            };
          })(r, pKey, poolInp);

          poolControls.appendChild(poolInp);
          poolControls.appendChild(poolRandBtn);

          poolHeader.appendChild(poolTitle);
          poolHeader.appendChild(poolControls);
          poolCard.appendChild(poolHeader);
          var themeColor = (pKey === '2-0' || pKey === '2-1') ? 'green' : ((pKey === '0-2' || pKey === '1-2') ? 'red' : ((pKey === '2-2') ? 'gold' : 'mint'));

          poolMatches.forEach(function (m) {
            var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
            var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';

            var winnerId = null;
            if (m.status === 'COMPLETED' || m.status === 'DONE') {
              if (m.team1Score > m.team2Score) winnerId = 'team1';
              else if (m.team2Score > m.team1Score) winnerId = 'team2';
              else winnerId = 'draw';
            }

            var cardEl = null;
            if (window.TourmaBracketCard && typeof window.TourmaBracketCard.createNodeElement === 'function') {
              cardEl = window.TourmaBracketCard.createNodeElement({
                matchId: m.matchKey,
                matchNumber: m.matchNumber,
                status: m.status || 'PENDING',
                team1: { name: t1Name, score: m.team1Score },
                team2: { name: t2Name, score: m.team2Score },
                winnerId: winnerId,
                themeColor: themeColor,
                hideSeeds: true
              });
            }

            if (!cardEl) {
              cardEl = document.createElement('div');
              cardEl.className = 'swiss-card-item';
              cardEl.innerText = t1Name + ' vs ' + t2Name;
            }

            cardEl.setAttribute('data-match-key', m.matchKey);
            cardEl.classList.add(cardColorClass);
            if (t1Name === 'TBD' || t2Name === 'TBD') {
              cardEl.classList.add('disabled-unconfirmed');
            } else {
              cardEl.onclick = function(evt) { handleMatchClick(m, evt); };
            }
            poolCard.appendChild(cardEl);
          });

          col.appendChild(poolCard);
        });

        canvas.appendChild(col);
      });
    } catch(err) {
      initFullSwissMatchesStructure();
      renderBracketView();
    }
  }

  // Handle Match Card Click (Quick Mode vs Normal Popup Modal)
  function handleMatchClick(m, event) {
    var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
    var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
    if (t1Name === 'TBD' || t2Name === 'TBD' || t1Name === 'BYE' || t2Name === 'BYE') return;

    if (isQuickMode || window.TourmaQuickMode) {
      var targetTeam = 'team1';
      if (event && event.target) {
        var teamRow = event.target.closest('.team-row, .swiss-card-row, .match-card-team-row, .bracket-team-row');
        if (teamRow) {
          var rowText = teamRow.innerText || '';
          if (rowText.includes(t2Name)) {
            targetTeam = 'team2';
          }
        }
      }

      var cardNode = (event && event.target) ? event.target.closest('.bracket-node-card, .match-card-item, .swiss-card-item') : null;
      var wScoreVal = getQuickModeWinScore(cardNode, m.roundIndex, m.recordPool);
      var lScoreVal = (wScoreVal > 0) ? Math.floor(Math.random() * wScoreVal) : 0;

      // Quick Mode Winner Score = n, Loser Score = random in [0, n-1]
      if (targetTeam === 'team1') {
        m.team1Score = wScoreVal;
        m.team2Score = lScoreVal;
        m.winnerId = 'team1';
      } else {
        m.team1Score = lScoreVal;
        m.team2Score = wScoreVal;
        m.winnerId = 'team2';
      }
      m.status = 'COMPLETED';

      // CASCADING RESET: Invalidate all future rounds beyond this match's round!
      invalidateFutureRounds(m.roundIndex);

      saveSwissMatches();
      if (currentViewMode === 'LIST') renderListView();
      else renderBracketView();
      return;
    }

    openMatchPopup(m);
  }

  // Open Reusable Score Edit Popup (TourmaScoreModal - SWISS MUST HAVE A WINNER, NO DRAWS!)
  function openMatchPopup(m) {
    if (isQuickMode || window.TourmaQuickMode) return; // GUARANTEE NO POPUP IN QUICK MODE!
    if (!window.TourmaScoreModal) return;

    var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
    var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
    if (t1Name === 'TBD' || t2Name === 'TBD' || t1Name === 'BYE' || t2Name === 'BYE') return;

    var winnerId = null;
    if (m.status === 'COMPLETED' || m.status === 'DONE') {
      if (m.team1Score > m.team2Score) winnerId = 'team1';
      else if (m.team2Score > m.team1Score) winnerId = 'team2';
    }

    var popupData = {
      matchId: m.matchKey,
      roundName: 'Vòng ' + m.roundIndex + ' (' + (m.recordPool || 'Swiss Pool') + ')',
      team1Name: t1Name,
      team1Score: m.team1Score || 0,
      team2Name: t2Name,
      team2Score: m.team2Score || 0,
      winnerId: winnerId,
      status: m.status || 'READY',
      allowDraw: false // STRICTLY NO DRAWS IN SWISS SYSTEM!
    };

    window.TourmaScoreModal.open(popupData, function (res) {
      if (!res) return;
      var s1 = parseInt(res.score1, 10) || 0;
      var s2 = parseInt(res.score2, 10) || 0;

      if (s1 === s2) {
        alert('⚠️ Thể thức Swiss bắt buộc phải có 1 đội thắng (không chấp nhận tỷ số hòa)! Vui lòng chọn đội thắng.');
        return;
      }

      m.team1Score = s1;
      m.team2Score = s2;
      m.status = 'COMPLETED';

      // CASCADING RESET: Invalidate all future rounds beyond this match's round!
      invalidateFutureRounds(m.roundIndex);

      saveSwissMatches();
      if (currentViewMode === 'LIST') renderListView();
      else renderBracketView();

      // Sync via AJAX with Servlet
      try {
        var xhr = new XMLHttpRequest();
        xhr.open("POST", window.swissContextPath + "/common/swiss-stage", true);
        xhr.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
        xhr.send("matchId=" + (m.id || 1) + "&team1Score=" + s1 + "&team2Score=" + s2 + "&status=COMPLETED");
      } catch (e) {}
    });
  }

  // Export Swiss Engine to window
  window.TourmaSwiss = {
    tournamentId: tournamentId,
    init: initSwissEngine,
    checkSwissStageCompletion: checkSwissStageCompletion,
    resetSwissMatches: window.resetSwissMatches,
    randomizeSwissMatches: window.randomizeSwissMatches,
    toggleQuickMode: window.toggleSwissQuickMode,
    switchViewMode: window.switchSwissViewMode
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () {
      initSwissEngine();
    });
  } else {
    initSwissEngine();
  }

})();
