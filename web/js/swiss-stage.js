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

  // Global Capture-phase Listener for Quick Mode Clicks to PREVENT POPUP (Winner Score = 1)
  document.addEventListener('click', function(e) {
    if (!window.TourmaQuickMode && !isQuickMode) return;

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

    // Quick Mode: Winning Score = 1, Losing Score = 0
    if (isT2) {
      m.team1Score = 0;
      m.team2Score = 1;
    } else {
      m.team1Score = 1;
      m.team2Score = 0;
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
          newMatches.forEach(function (nm) {
            // Find existing TBD match slot in nextRoundNumber with matching recordPool
            var targetKey = null;
            Object.keys(matchesMap).forEach(function(k) {
              var existing = matchesMap[k];
              if (existing && existing.roundIndex === nextRoundNumber && 
                  existing.recordPool === nm.recordPool && 
                  (existing.team1.name === 'TBD' || existing.status === 'PENDING') &&
                  !targetKey) {
                targetKey = k;
              }
            });

            if (targetKey && matchesMap[targetKey]) {
              matchesMap[targetKey].team1 = nm.team1;
              matchesMap[targetKey].team2 = nm.team2;
              matchesMap[targetKey].team1Score = 0;
              matchesMap[targetKey].team2Score = 0;
              matchesMap[targetKey].status = 'READY';
            } else {
              matchesMap[nm.matchKey] = nm;
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

    // Check if round is already fully completed
    var uncompletedCount = 0;
    Object.keys(matchesMap).forEach(function (k) {
      var m = matchesMap[k];
      if (m && m.roundIndex === rNum) {
        var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
        var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
        if (t1Name !== 'TBD' && t2Name !== 'TBD' && t1Name !== 'BYE' && t2Name !== 'BYE') {
          if (m.status !== 'COMPLETED' && m.status !== 'DONE') {
            uncompletedCount++;
          }
        }
      }
    });

    if (uncompletedCount === 0) return;

    Object.keys(matchesMap).forEach(function (k) {
      var m = matchesMap[k];
      if (m && m.roundIndex === rNum && m.status !== 'COMPLETED' && m.status !== 'DONE') {
        var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
        var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
        if (t1Name !== 'TBD' && t2Name !== 'TBD' && t1Name !== 'BYE' && t2Name !== 'BYE') {
          var isT1Win = Math.random() < 0.5;
          var wScore, lScore;

          if (customScore > 0) {
            wScore = customScore;
            lScore = Math.floor(Math.random() * customScore);
          } else {
            var res = getRandomDecisiveScores();
            wScore = res.winScore;
            lScore = res.loseScore;
          }

          m.team1Score = isT1Win ? wScore : lScore;
          m.team2Score = isT1Win ? lScore : wScore;
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

    var uncompletedCount = 0;
    Object.keys(matchesMap).forEach(function (k) {
      var m = matchesMap[k];
      if (m && m.roundIndex === rNum && (m.recordPool === poolKey || (!m.recordPool && poolKey === '0-0'))) {
        var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
        var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
        if (t1Name !== 'TBD' && t2Name !== 'TBD' && t1Name !== 'BYE' && t2Name !== 'BYE') {
          if (m.status !== 'COMPLETED' && m.status !== 'DONE') {
            uncompletedCount++;
          }
        }
      }
    });

    if (uncompletedCount === 0) return;

    Object.keys(matchesMap).forEach(function (k) {
      var m = matchesMap[k];
      if (m && m.roundIndex === rNum && (m.recordPool === poolKey || (!m.recordPool && poolKey === '0-0'))) {
        if (m.status !== 'COMPLETED' && m.status !== 'DONE') {
          var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
          var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
          if (t1Name !== 'TBD' && t2Name !== 'TBD' && t1Name !== 'BYE' && t2Name !== 'BYE') {
            var isT1Win = Math.random() < 0.5;
            var wScore, lScore;

            if (customScore > 0) {
              wScore = customScore;
              lScore = Math.floor(Math.random() * customScore);
            } else {
              var res = getRandomDecisiveScores();
              wScore = res.winScore;
              lScore = res.loseScore;
            }

            m.team1Score = isT1Win ? wScore : lScore;
            m.team2Score = isT1Win ? lScore : wScore;
            m.status = 'COMPLETED';
          }
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
      var rawTeams = localStorage.getItem("tourma_teams_" + tournamentId);
      if (rawTeams) {
        var parsed = JSON.parse(rawTeams);
        if (Array.isArray(parsed) && parsed.length > 0) {
          teamsList = parsed;
        }
      }
    } catch (e) {}

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

    // 2. Team Count Validation: MUST BE EXACTLY 16 TEAMS!
    var alertBox = document.getElementById('swissInvalidTeamAlert');
    var mainWrapper = document.getElementById('swissMainContentWrapper');
    var descEl = document.getElementById('swissInvalidTeamDesc');

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
      var rawM = localStorage.getItem("tourma_swiss_matches_" + tournamentId);
      if (rawM) {
        matchesMap = JSON.parse(rawM);
      }
    } catch (e) {}

    // Auto-heal / repair matchesMap structure
    validateAndRepairMatchesMap();

    // Render initial view mode
    switchSwissViewMode(currentViewMode);
  }

  function saveSwissMatches() {
    try {
      checkAndAutoAdvanceRound(); // AUTO GENERATE NEXT ROUND UPON COMPLETION!
      localStorage.setItem("tourma_swiss_matches_" + tournamentId, JSON.stringify(matchesMap));
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
      rInp.value = '';
      rInp.placeholder = '-';
      rInp.min = '1';
      if (isRoundCompleted || !hasPlayableMatches) rInp.disabled = true;

      var rBtn = document.createElement('button');
      rBtn.type = 'button';
      rBtn.className = 'btn-round-random';
      rBtn.innerText = 'Random';
      if (isRoundCompleted || !hasPlayableMatches) rBtn.disabled = true;

      var rResetBtn = document.createElement('button');
      rResetBtn.type = 'button';
      rResetBtn.className = 'btn-round-reset';
      rResetBtn.innerHTML = '<i class="fa-solid fa-arrows-rotate"></i>';

      (function(rNumber, inp) {
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
        poolInp.value = '';
        poolInp.placeholder = '-';
        poolInp.min = '1';
        if (isPoolCompleted || !hasPoolPlayable) poolInp.disabled = true;

        var poolRandBtn = document.createElement('button');
        poolRandBtn.type = 'button';
        poolRandBtn.className = 'btn-pool-random';
        poolRandBtn.title = 'Random tỷ số cho nhóm ' + pKey;
        poolRandBtn.innerHTML = '<i class="fa-solid fa-dice"></i> Random';
        if (isPoolCompleted || !hasPoolPlayable) poolRandBtn.disabled = true;

        (function(rNumber, keyStr, inp) {
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
        rInp.value = '';
        rInp.placeholder = '-';
        rInp.min = '1';
        if (isRoundCompleted || !hasPlayableMatches) rInp.disabled = true;

        var rBtn = document.createElement('button');
        rBtn.type = 'button';
        rBtn.className = 'btn-round-random';
        rBtn.innerText = 'Random';
        if (isRoundCompleted || !hasPlayableMatches) rBtn.disabled = true;

        var rResetBtn = document.createElement('button');
        rResetBtn.type = 'button';
        rResetBtn.className = 'btn-round-reset';
        rResetBtn.innerHTML = '<i class="fa-solid fa-arrows-rotate"></i>';

        (function(rNum, inp) {
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
          poolInp.value = '';
          poolInp.placeholder = '-';
          poolInp.min = '1';
          if (isPoolCompleted || !hasPoolPlayable) poolInp.disabled = true;

          var poolRandBtn = document.createElement('button');
          poolRandBtn.type = 'button';
          poolRandBtn.className = 'btn-pool-random';
          poolRandBtn.title = 'Random tỷ số cho nhóm ' + pKey;
          poolRandBtn.innerHTML = '<i class="fa-solid fa-dice"></i> Random';
          if (isPoolCompleted || !hasPoolPlayable) poolRandBtn.disabled = true;

          (function(rNumber, keyStr, inp) {
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

      // Quick Mode Winner Score = 1, Loser Score = 0
      if (targetTeam === 'team1') {
        m.team1Score = 1;
        m.team2Score = 0;
      } else {
        m.team1Score = 0;
        m.team2Score = 1;
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

  window.resetSwissMatches = function() {
    if (!confirm('Bạn có chắc chắn muốn xóa toàn bộ tỷ số và đặt lại giai đoạn Swiss về Vòng 1 ban đầu?')) {
      return;
    }
    localStorage.removeItem("tourma_swiss_matches_" + tournamentId);
    initFullSwissMatchesStructure();
    if (currentViewMode === 'LIST') renderListView();
    else renderBracketView();
  };

  document.addEventListener('DOMContentLoaded', function () {
    initSwissEngine();
  });

})();
