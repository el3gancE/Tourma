/**
 * TOURMA - ROLLING WINDOW SERIES STEP 3: SUB-TOURNAMENT TEAMS SCRIPT
 * Modal handlers, quick partner selection (BXH, Top, Bottom, Random), search filter & HTML5 Drag & Drop
 * Automatically sorts partner list & sub-tournament seeds by the latest Rolling Standings (BXH Tổng)
 */

(function () {
  'use strict';

  var dragSrcIndex = null;

  function handleDragStart(e) {
    dragSrcIndex = parseInt(this.getAttribute('data-index'), 10);
    this.classList.add('dragging');
    if (e.dataTransfer) {
      e.dataTransfer.effectAllowed = 'move';
    }
  }

  function handleDragOver(e) {
    if (e.preventDefault) {
      e.preventDefault();
    }
    if (e.dataTransfer) {
      e.dataTransfer.dropEffect = 'move';
    }
    return false;
  }

  function handleDrop(e) {
    if (e.stopPropagation) {
      e.stopPropagation();
    }
    var targetIndex = parseInt(this.getAttribute('data-index'), 10);
    if (dragSrcIndex !== null && dragSrcIndex !== targetIndex && !isNaN(targetIndex)) {
      var tbody = this.parentNode;
      var rows = Array.from(tbody.querySelectorAll('tr.team-table-row'));
      if (dragSrcIndex < rows.length && targetIndex < rows.length) {
        var movedRow = rows[dragSrcIndex];
        if (dragSrcIndex < targetIndex) {
          tbody.insertBefore(movedRow, rows[targetIndex].nextSibling);
        } else {
          tbody.insertBefore(movedRow, rows[targetIndex]);
        }
        reindexSubtourneyRows();
      }
    }
    return false;
  }

  function handleDragEnd() {
    var rows = document.querySelectorAll('.team-table-row');
    rows.forEach(function (r) {
      r.classList.remove('dragging');
    });
    dragSrcIndex = null;
  }

  function reindexSubtourneyRows() {
    var tbody = document.querySelector('#subtourneyTeamsTable tbody');
    if (!tbody) return;
    var rows = Array.from(tbody.querySelectorAll('tr.team-table-row'));
    var teamNamesList = [];

    rows.forEach(function (r, idx) {
      r.setAttribute('data-index', idx);
      var seedBadge = r.querySelector('.seed-badge');
      if (seedBadge) {
        seedBadge.textContent = (idx + 1);
      }
      var nameSpan = r.querySelector('td:nth-child(2) span');
      if (nameSpan) {
        var tName = nameSpan.textContent.trim();
        if (tName) {
          teamNamesList.push({ name: tName, rawName: tName, seed: idx + 1 });
        }
      }
    });

    // Update count badges
    var inputCountEl = document.getElementById('inputCountDisplay');
    if (inputCountEl) inputCountEl.innerText = rows.length + ' Đội';
    var managedCountEl = document.getElementById('managedCountDisplay');
    if (managedCountEl) managedCountEl.innerText = rows.length + ' Đội';

    // Persist to localStorage for subsequent steps
    var tid = getTourneyId();
    if (tid && teamNamesList.length > 0) {
      try {
        localStorage.setItem('tourma_teams_' + tid, JSON.stringify(teamNamesList));
      } catch (e) {}
    }
  }

  function getTourneyId() {
    var inp = document.querySelector('input[name="tournamentId"]');
    if (inp && inp.value) return inp.value;
    var params = new URLSearchParams(window.location.search);
    return params.get('id') || params.get('tournamentId');
  }

  window.initSubtourneyDragAndDrop = function () {
    var rows = document.querySelectorAll('tr.team-table-row');
    rows.forEach(function (row, idx) {
      row.draggable = true;
      row.setAttribute('data-index', idx);
      row.removeEventListener('dragstart', handleDragStart);
      row.removeEventListener('dragover', handleDragOver);
      row.removeEventListener('drop', handleDrop);
      row.removeEventListener('dragend', handleDragEnd);

      row.addEventListener('dragstart', handleDragStart);
      row.addEventListener('dragover', handleDragOver);
      row.addEventListener('drop', handleDrop);
      row.addEventListener('dragend', handleDragEnd);
    });

    reindexSubtourneyRows();
  };

  /* =========================================================================
     STANDINGS ALGORITHM HELPERS & DYNAMIC RE-CALCULATION FROM COMPLETED TOURNAMENTS
     ========================================================================= */

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
    if (ptsCfg[positionKey] !== undefined) return parseInt(ptsCfg[positionKey], 10) || 0;
    if (altKey && ptsCfg[altKey] !== undefined) return parseInt(ptsCfg[altKey], 10) || 0;
    if (ptsCfg[String(positionKey)] !== undefined) return parseInt(ptsCfg[String(positionKey)], 10) || 0;

    if (positionKey === "stage1_eliminated" || altKey === "stage1_eliminated") {
      if (ptsCfg["stage1_eliminated"] !== undefined) return parseInt(ptsCfg["stage1_eliminated"], 10) || 0;
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

  // Calculate realtime standings across completed sub-tournaments and update modal + rank map
  window.calculateAndSyncModalStandings = function () {
    var modalList = document.getElementById('partnerModalCheckboxList');
    if (!modalList) return;

    var items = Array.from(modalList.querySelectorAll('.partner-checkbox-item'));
    if (items.length === 0) return;

    // Initialize team data map from modal items
    var teamDataMap = {};
    items.forEach(function (item) {
      var rawName = item.getAttribute('data-team-name') || '';
      var teamKey = rawName.trim().toLowerCase();
      if (!teamKey) {
        var span = item.querySelector('span');
        if (span) rawName = span.textContent.replace('(Đã thêm)', '').trim();
        teamKey = rawName.toLowerCase();
      }
      var pts = parseInt(item.getAttribute('data-points'), 10) || 0;
      var rank = parseInt(item.getAttribute('data-rank'), 10) || 999;

      if (window.seriesStandingsRankMap && window.seriesStandingsRankMap[teamKey]) {
        pts = window.seriesStandingsRankMap[teamKey].points || pts;
        rank = window.seriesStandingsRankMap[teamKey].rank || rank;
      }

      teamDataMap[teamKey] = {
        name: rawName,
        totalPts: pts,
        lastPts: 0,
        expiredPts: 0,
        droppedPts: 0,
        activeTourneys: 0,
        item: item,
        hasLocalUpdates: false
      };
    });

    var subTourneys = window.seriesSubTournaments || [];
    var phaseSize = window.seriesPhaseSize || 3;
    var totalTourneys = subTourneys.length;
    var activeStartIndex = Math.max(0, totalTourneys - phaseSize);
    var droppedIndex = totalTourneys - phaseSize - 1;

    if (subTourneys.length > 0) {
      subTourneys.forEach(function (t, tIdx) {
        var isActiveWindow = (tIdx >= activeStartIndex);
        var isLatestTourney = (tIdx === totalTourneys - 1);
        var isDroppedTourney = (tIdx === droppedIndex);

        var localPtsCfg = {};
        try {
          var localPtsRaw = getStorageData(['tourma_points_config_'], t.id);
          if (localPtsRaw) localPtsCfg = JSON.parse(localPtsRaw) || {};
        } catch (e) {}

        var ptsCfg = Object.assign({}, t.pointsConfig || {}, localPtsCfg || {});
        var teamTourneyPoints = {};

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
          if (pts <= prevPts) return;

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
            if (isLatestTourney) targetEntry.lastPts = pts;
          } else {
            targetEntry.expiredPts += diff;
          }

          if (isDroppedTourney) targetEntry.droppedPts = pts;
        }

        // Parse matches for this sub-tournament
        var rawMultiCfg = getStorageData(['tourma_multi_config_'], t.id);
        var multiConfig = null;
        if (rawMultiCfg) {
          try { multiConfig = JSON.parse(rawMultiCfg); } catch (e) {}
        }
        var isMultiStage = !!(multiConfig && multiConfig.stage2Format) || (t.stageCount > 1) || (t.isMultiStage);
        var s1Format = (multiConfig && multiConfig.stage1Format) ? multiConfig.stage1Format.toUpperCase() : (t.stage1Format || (t.format || 'SWISS_LITE'));
        var s2Format = (multiConfig && multiConfig.stage2Format) ? multiConfig.stage2Format.toUpperCase() : (t.stage2Format || 'ROUND_ROBIN');

        // Swiss Stage
        if (s1Format.indexOf('SWISS') !== -1 || (!isMultiStage && (t.format || '').indexOf('SWISS') !== -1)) {
          var rawSwiss = getStorageData(['tourma_swiss_matches_'], t.id);
          if (rawSwiss) {
            try {
              var swissMatches = JSON.parse(rawSwiss);
              var sTeamsList = [];
              try { sTeamsList = JSON.parse(getStorageData(['tourma_teams_'], t.id)) || []; } catch (e) {}
              var swissStats = {};
              if (Array.isArray(sTeamsList)) {
                sTeamsList.forEach(function (tm) {
                  var tmName = extractName(tm);
                  if (tmName) swissStats[tmName.toLowerCase().trim()] = { name: tmName, wins: 0, losses: 0, draws: 0, points: 0, scoresFor: 0, scoresAgainst: 0, diff: 0, buchholz: 0, opponents: [] };
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
                  if (!swissStats[k1]) swissStats[k1] = { name: t1Name, wins: 0, losses: 0, draws: 0, points: 0, scoresFor: 0, scoresAgainst: 0, diff: 0, buchholz: 0, opponents: [] };
                  if (t2Name) {
                    var k2 = t2Name.toLowerCase().trim();
                    if (!swissStats[k2]) swissStats[k2] = { name: t2Name, wins: 0, losses: 0, draws: 0, points: 0, scoresFor: 0, scoresAgainst: 0, diff: 0, buchholz: 0, opponents: [] };
                    swissStats[k1].opponents.push(k2);
                    swissStats[k2].opponents.push(k1);
                    var s1 = parseInt(m.team1Score !== undefined ? m.team1Score : m.score1, 10);
                    var s2 = parseInt(m.team2Score !== undefined ? m.team2Score : m.score2, 10);
                    if (!isNaN(s1) && !isNaN(s2) && (m.status === 'COMPLETED' || m.status === 'DONE' || s1 > 0 || s2 > 0)) {
                      swissStats[k1].scoresFor += s1; swissStats[k1].scoresAgainst += s2;
                      swissStats[k2].scoresFor += s2; swissStats[k2].scoresAgainst += s1;
                      if (s1 > s2) { swissStats[k1].wins += 1; swissStats[k1].points += 3; swissStats[k2].losses += 1; }
                      else if (s2 > s1) { swissStats[k2].wins += 1; swissStats[k2].points += 3; swissStats[k1].losses += 1; }
                      else { swissStats[k1].draws += 1; swissStats[k2].draws += 1; swissStats[k1].points += 1; swissStats[k2].points += 1; }
                    }
                  } else {
                    swissStats[k1].wins += 1; swissStats[k1].points += 3;
                  }
                });
              }
              Object.keys(swissStats).forEach(function (k) {
                var st = swissStats[k];
                st.diff = st.scoresFor - st.scoresAgainst;
                var bSum = 0;
                st.opponents.forEach(function (oppK) { if (swissStats[oppK]) bSum += swissStats[oppK].points; });
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
                swissList.forEach(function (st, idx) { awardTeamPoints(st.name, String(idx + 1)); });
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

        // Single Elimination / Bracket Matches
        if (s1Format === 'SINGLE_ELIMINATION' || (!isMultiStage && t.format === 'SINGLE_ELIMINATION')) {
          var rawBracket = getStorageData(['tourma_bracket_matches_', 'tourma_matches_', 'tourma_bracket_'], t.id);
          if (rawBracket) {
            try {
              var bracketData = JSON.parse(rawBracket);
              var matchesMap = bracketData.matchesMap || bracketData;
              if (matchesMap && typeof matchesMap === 'object') {
                var totalTeams = 16;
                try {
                  var sTeams = JSON.parse(getStorageData(['tourma_teams_'], t.id));
                  if (Array.isArray(sTeams) && sTeams.length > 0) totalTeams = sTeams.length;
                } catch (e) {}
                var totalFullRounds = Math.round(Math.log2(totalTeams));
                Object.keys(matchesMap).forEach(function (mk) {
                  var m = matchesMap[mk];
                  if (m && m.winnerId) {
                    var rNum = m.roundNumber || 1;
                    var teamsInRound = totalTeams / Math.pow(2, rNum - 1);
                    var startPos = Math.floor(teamsInRound / 2) + 1;
                    var endPos = teamsInRound;
                    var posKey = (startPos === endPos) ? String(startPos) : (startPos + "-" + endPos);
                    var mRes = resolveWinnerAndLoser(m);
                    if (mRes.loser) awardTeamPoints(mRes.loser, posKey, "stage1_eliminated");
                    if (mRes.winner && !isMultiStage && rNum === totalFullRounds) awardTeamPoints(mRes.winner, "1");
                    if (mRes.loser && !isMultiStage && rNum === totalFullRounds) awardTeamPoints(mRes.loser, "2");
                  }
                });
              }
            } catch (e) {}
          }
        }

        // Round Robin Matches
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
              if (teamsList.length > 0 && window.TourmaRoundRobinAlgorithm) {
                var standings = window.TourmaRoundRobinAlgorithm.calculateStandings(teamsList, rrMatchesMap, (multiConfig && multiConfig.stage1Config) || rrData.config || {});
                if (standings && standings.length > 0) {
                  standings.forEach(function (row, idx) {
                    var name = extractName(row) || (row.team ? extractName(row.team) : null);
                    if (name) awardTeamPoints(name, String(idx + 1));
                  });
                }
              }
            } catch (e) {}
          }
        }

        // Multi-Stage Stage 2 Single Elimination
        if (isMultiStage && s2Format === 'SINGLE_ELIMINATION') {
          var rawBracketS2 = getStorageData(['tourma_bracket_stage2_', 'tourma_matches_stage2_'], t.id);
          if (rawBracketS2) {
            try {
              var bracketDataS2 = JSON.parse(rawBracketS2);
              var matchesMapS2 = bracketDataS2.matchesMap || bracketDataS2;
              if (matchesMapS2 && typeof matchesMapS2 === 'object') {
                var s2TeamsCount = 4;
                try {
                  var st2 = JSON.parse(getStorageData(['tourma_stage2_teams_'], t.id));
                  if (Array.isArray(st2) && st2.length > 0) s2TeamsCount = st2.length;
                } catch (e) {}
                var totalS2FullRounds = Math.round(Math.log2(s2TeamsCount));
                Object.keys(matchesMapS2).forEach(function (mk) {
                  var m = matchesMapS2[mk];
                  if (m && m.winnerId) {
                    var rNumS2 = m.roundNumber || 1;
                    var tInR = s2TeamsCount / Math.pow(2, rNumS2 - 1);
                    var sPos = Math.floor(tInR / 2) + 1;
                    var posKeyS2 = (sPos === tInR) ? String(sPos) : (sPos + "-" + tInR);
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
            } catch (e) {}
          }
        }
      });
    }

    // Sort partner team array by total points descending
    var teamDataArray = Object.keys(teamDataMap).map(function (k) { return teamDataMap[k]; });
    teamDataArray.sort(function (a, b) {
      if (b.totalPts !== a.totalPts) return b.totalPts - a.totalPts;
      return b.lastPts - a.lastPts;
    });

    // Update window.seriesStandingsRankMap & Re-order DOM elements in modal
    window.seriesStandingsRankMap = window.seriesStandingsRankMap || {};

    teamDataArray.forEach(function (data, rankIdx) {
      var item = data.item;
      var rank = rankIdx + 1;
      var teamKey = data.name.trim().toLowerCase();

      window.seriesStandingsRankMap[teamKey] = { rank: rank, points: data.totalPts };

      // Update data attributes
      item.setAttribute('data-index', rankIdx);
      item.setAttribute('data-rank', rank);
      item.setAttribute('data-points', data.totalPts);

      var cb = item.querySelector('.partner-cb-input');
      if (cb) cb.setAttribute('data-index', rankIdx);

      // Update rank badge in modal item
      var badge = item.querySelector('.rank-badge');
      if (badge) {
        badge.className = 'rank-badge rank-' + rank;
        badge.textContent = '#' + rank;
        badge.style.color = (rank === 1) ? '#fbbf24' : (rank === 2 ? '#e2e8f0' : (rank === 3 ? '#f97316' : '#cbd5e1'));
        badge.style.borderColor = (rank === 1) ? 'rgba(251,191,36,0.3)' : (rank === 2 ? 'rgba(226,232,240,0.2)' : (rank === 3 ? 'rgba(249,115,22,0.3)' : 'rgba(255,255,255,0.1)'));
      }

      // Update points display in modal item
      var ptsDiv = item.querySelector('div:last-child');
      if (ptsDiv) {
        ptsDiv.textContent = data.totalPts + ' pts';
      }

      // Physically append to modal container in new sorted order
      modalList.appendChild(item);
    });
  };

  /**
   * Sort the current tournament teams in the seeding table by overall Rolling Standings
   */
  window.sortByRollingStandings = function () {
    var tbody = document.querySelector('#subtourneyTeamsTable tbody');
    if (!tbody) return;

    var rows = Array.from(tbody.querySelectorAll('tr.team-table-row'));
    if (rows.length <= 1) return;

    // Ensure latest standings are calculated
    window.calculateAndSyncModalStandings();

    var rankMap = window.seriesStandingsRankMap || {};

    rows.sort(function (rowA, rowB) {
      var nameA = '', nameB = '';
      var spanA = rowA.querySelector('td:nth-child(2) span');
      if (spanA) nameA = spanA.textContent.trim().toLowerCase();
      var spanB = rowB.querySelector('td:nth-child(2) span');
      if (spanB) nameB = spanB.textContent.trim().toLowerCase();

      var rankA = (rankMap[nameA] && rankMap[nameA].rank) ? rankMap[nameA].rank : 99999;
      var rankB = (rankMap[nameB] && rankMap[nameB].rank) ? rankMap[nameB].rank : 99999;

      if (rankA !== rankB) return rankA - rankB;
      return nameA.localeCompare(nameB);
    });

    rows.forEach(function (row) {
      tbody.appendChild(row);
    });

    reindexSubtourneyRows();
  };

  window.openPartnerSelectModal = function () {
    window.calculateAndSyncModalStandings();
    var modal = document.getElementById('partnerSelectModal');
    if (modal) modal.style.display = 'flex';
  };

  window.closePartnerSelectModal = function () {
    var modal = document.getElementById('partnerSelectModal');
    if (modal) modal.style.display = 'none';
  };

  window.selectAllPartnerCheckboxes = function (selectAll) {
    var checkboxes = document.querySelectorAll('.partner-cb-input');
    checkboxes.forEach(function (cb) {
      if (!cb.disabled) {
        cb.checked = selectAll;
      }
    });
  };

  window.quickSelectTeams = function (mode) {
    var numInput = document.getElementById('quickSelectNum');
    if (!numInput) return;

    var availableCbs = Array.from(document.querySelectorAll('.partner-cb-input:not(:disabled)'));
    var totalPartnerCount = document.querySelectorAll('.partner-cb-input').length;

    var count = parseInt(numInput.value, 10);
    if (isNaN(count) || count < 2) {
      count = 2;
      numInput.value = 2;
    }
    if (count > totalPartnerCount) {
      count = totalPartnerCount;
      numInput.value = totalPartnerCount;
    }

    // Clear selection on enabled checkboxes first
    availableCbs.forEach(function (cb) {
      cb.checked = false;
    });

    if (availableCbs.length === 0) return;

    if (mode === 'TOP') {
      for (var i = 0; i < Math.min(count, availableCbs.length); i++) {
        availableCbs[i].checked = true;
      }
    } else if (mode === 'BOTTOM') {
      var startIndex = Math.max(0, availableCbs.length - count);
      for (var j = startIndex; j < availableCbs.length; j++) {
        availableCbs[j].checked = true;
      }
    } else if (mode === 'RANDOM') {
      var indices = Array.from({ length: availableCbs.length }, function (_, idx) { return idx; });
      for (var k = indices.length - 1; k > 0; k--) {
        var rand = Math.floor(Math.random() * (k + 1));
        var temp = indices[k];
        indices[k] = indices[rand];
        indices[rand] = temp;
      }
      var selectedIndices = indices.slice(0, Math.min(count, availableCbs.length));
      selectedIndices.forEach(function (idx) {
        availableCbs[idx].checked = true;
      });
    }
  };

  window.filterTournamentTeams = function (query) {
    var filter = (query || '').toLowerCase().trim();
    var rows = document.querySelectorAll('#subtourneyTeamsTable tbody tr');
    rows.forEach(function (row) {
      var teamNameCell = row.cells[1];
      if (teamNameCell) {
        var text = teamNameCell.textContent || teamNameCell.innerText;
        row.style.display = text.toLowerCase().indexOf(filter) > -1 ? '' : 'none';
      }
    });
  };

  window.toggleSelectAllSubtourney = function (masterCb) {
    var itemCbs = document.querySelectorAll('.team-item-cb');
    itemCbs.forEach(function (cb) {
      cb.checked = masterCb.checked;
    });
  };

  window.shuffleSubtourneyTeams = function () {
    var tbody = document.querySelector('#subtourneyTeamsTable tbody');
    if (!tbody) return;
    var rows = Array.from(tbody.querySelectorAll('tr.team-table-row'));
    if (rows.length <= 1) return;

    var lockInput = document.getElementById('lockTopSeedsInput');
    var lockCount = lockInput ? parseInt(lockInput.value, 10) : 0;
    if (isNaN(lockCount) || lockCount < 0) lockCount = 0;
    if (lockCount >= rows.length) return;

    for (var i = rows.length - 1; i > lockCount; i--) {
      var j = lockCount + Math.floor(Math.random() * (i - lockCount + 1));
      var temp = rows[i];
      rows[i] = rows[j];
      rows[j] = temp;
    }

    rows.forEach(function (row) {
      tbody.appendChild(row);
    });
    reindexSubtourneyRows();
  };

  window.deleteSelectedSubtourneyTeams = function () {
    var selected = document.querySelectorAll('.team-item-cb:checked');
    if (selected.length === 0) {
      alert('Vui lòng chọn ít nhất một đội để xóa!');
      return;
    }
    if (confirm('Bạn có chắc chắn muốn xóa ' + selected.length + ' đội đã chọn khỏi giải con này?')) {
      var bulkForm = document.getElementById('bulkRemoveTeamsForm');
      if (bulkForm) {
        bulkForm.submit();
      }
    }
  };

  function getSubtourneyId() {
    var params = new URLSearchParams(window.location.search);
    return params.get('id') || 'demo';
  }

  window.initHideSeedConfig = function () {
    var tid = getSubtourneyId();
    var btn = document.getElementById('btnToggleHideSeed');
    var input = document.getElementById('lockTopSeedsInput');
    if (!btn) return;

    var isEnabled = false;
    var visibleCount = '';
    if (tid) {
      try {
        var raw = localStorage.getItem('tourma_hide_seed_config_' + tid);
        if (raw) {
          var cfg = JSON.parse(raw);
          isEnabled = !!cfg.isEnabled;
          visibleCount = (cfg.visibleCount !== undefined && cfg.visibleCount !== null) ? cfg.visibleCount : '';
        }
      } catch (e) {}
    }

    if (input) {
      input.value = visibleCount;
    }
    window.applyHideSeedUI(isEnabled);
  };

  window.applyHideSeedUI = function (isEnabled) {
    var btn = document.getElementById('btnToggleHideSeed');
    if (!btn) return;

    if (isEnabled) {
      btn.dataset.active = 'true';
      btn.style.background = '#10b981';
      btn.style.color = '#ffffff';
      btn.style.borderColor = '#10b981';
    } else {
      btn.dataset.active = 'false';
      btn.style.background = 'rgba(255, 255, 255, 0.06)';
      btn.style.color = '#94a3b8';
      btn.style.borderColor = 'rgba(255, 255, 255, 0.15)';
    }
  };

  window.toggleHideSeedMode = function () {
    var btn = document.getElementById('btnToggleHideSeed');
    var isCurrentlyActive = (btn && btn.dataset.active === 'true');
    var newActive = !isCurrentlyActive;
    window.applyHideSeedUI(newActive);
    window.saveHideSeedConfig();
  };

  window.saveHideSeedConfig = function () {
    var tid = getSubtourneyId();
    var btn = document.getElementById('btnToggleHideSeed');
    var input = document.getElementById('lockTopSeedsInput');
    var isEnabled = (btn && btn.dataset.active === 'true');
    var visibleCount = input ? input.value.trim() : '';

    var cfg = {
      isEnabled: isEnabled,
      visibleCount: visibleCount
    };
    if (tid) {
      try {
        localStorage.setItem('tourma_hide_seed_config_' + tid, JSON.stringify(cfg));
      } catch (e) {}
    }
  };

  window.proceedToNextStep = function (e) {
    if (e && e.preventDefault) e.preventDefault();
    var rows = Array.from(document.querySelectorAll('#subtourneyTeamsTable tbody tr.team-table-row'));
    var teamNames = [];
    rows.forEach(function (r) {
      var nameSpan = r.querySelector('td:nth-child(2) span');
      if (nameSpan) {
        var tName = nameSpan.textContent.trim();
        if (tName) teamNames.push(tName);
      }
    });

    var tid = getSubtourneyId();
    if (tid) {
      try {
        localStorage.removeItem('tourma_matches_' + tid);
        localStorage.removeItem('tourma_matches_stage2_' + tid);
        localStorage.removeItem('tourma_bracket_' + tid);
        localStorage.removeItem('tourma_bracket_stage2_' + tid);
        localStorage.removeItem('tourma_de_matches_' + tid);
        localStorage.removeItem('tourma_de_matches_stage2_' + tid);
        localStorage.removeItem('tourma_rr_matches_' + tid);
        localStorage.removeItem('tourma_group_matches_' + tid);
        localStorage.removeItem('tourma_swiss_matches_' + tid);
        localStorage.setItem('tourma_teams_' + tid, JSON.stringify(teamNames));
      } catch (e) {}
    }

    var inp = document.getElementById('orderedTeamNamesInput');
    if (inp) {
      inp.value = teamNames.join('\n');
    }
    var form = document.getElementById('saveOrderAndProceedForm');
    if (form) {
      form.submit();
    } else {
      var nextBtn = document.getElementById('nextStepBtn');
      if (nextBtn && nextBtn.getAttribute('data-href')) {
        window.location.href = nextBtn.getAttribute('data-href');
      }
    }
    return false;
  };

  document.addEventListener('DOMContentLoaded', function () {
    window.initSubtourneyDragAndDrop();
    window.initHideSeedConfig();
    window.calculateAndSyncModalStandings();
  });

})();
