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
      var s = obj.trim();
      if (s && s !== 'BYE' && s !== 'TBD' && !s.startsWith('W #') && !s.startsWith('L #')) {
        return s;
      }
      return null;
    }
    if (typeof obj === 'object') {
      var name = obj.name || obj.rawName || obj.normalizedName;
      if (name && typeof name === 'string') {
        var s = name.trim();
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
        teamDataMap[key] = {
          name: teamName,
          totalPts: 0,
          lastPts: 0,
          expiredPts: 0,
          activeTourneys: 0,
          row: row
        };
      }
    });

    var subTourneys = window.seriesSubTournaments || [];
    if (subTourneys.length === 0) return;

    var phaseSize = window.seriesPhaseSize || 3;
    var totalTourneys = subTourneys.length;
    var activeStartIndex = Math.max(0, totalTourneys - phaseSize);

    // Process each valid sub-tournament in chronological order
    subTourneys.forEach(function (t, tIdx) {
      var isActiveWindow = (tIdx >= activeStartIndex);
      var isLatestTourney = (tIdx === totalTourneys - 1);

      // Points configuration set for this sub-tournament
      var ptsCfg = t.pointsConfig || { "1": 200, "2": 100, "3-4": 50, "5-8": 0 };

      // Look up bracket data in localStorage
      var lsKey = 'tourma_bracket_' + t.id;
      var lsKeyStage2 = 'tourma_bracket_stage2_' + t.id;
      var rawBracket = localStorage.getItem(lsKeyStage2) || localStorage.getItem(lsKey);

      if (!rawBracket) return;

      try {
        var bracketData = JSON.parse(rawBracket);
        if (!bracketData || !bracketData.matchesMap) return;

        var matchesMap = bracketData.matchesMap;
        var mKeys = Object.keys(matchesMap);
        if (mKeys.length === 0) return;

        // Find max round number (Final Match)
        var maxRound = 0;
        mKeys.forEach(function (mk) {
          var m = matchesMap[mk];
          if (m && m.roundNumber > maxRound) maxRound = m.roundNumber;
        });

        if (maxRound === 0) return;

        var finalMatch = null;
        mKeys.forEach(function (mk) {
          var m = matchesMap[mk];
          if (m && m.roundNumber === maxRound) finalMatch = m;
        });

        var res = resolveWinnerAndLoser(finalMatch);
        var winnerName = res.winner;
        var loserName = res.loser;

        // Trace semi-final losers (3rd-4th place)
        var sfLosers = [];
        if (maxRound > 1) {
          mKeys.forEach(function (mk) {
            var m = matchesMap[mk];
            if (m && m.roundNumber === maxRound - 1 && m.winnerId) {
              var sfRes = resolveWinnerAndLoser(m);
              var sfL = sfRes.loser;
              if (sfL && sfL !== winnerName && sfL !== loserName && !sfLosers.includes(sfL)) {
                sfLosers.push(sfL);
              }
            }
          });
        }

        // Helper to award points to a team
        function awardTeamPoints(name, positionKey) {
          if (!name) return;
          var key = name.toLowerCase();
          if (!teamDataMap[key]) return;

          var pts = 0;
          if (ptsCfg[positionKey] !== undefined) {
            pts = parseInt(ptsCfg[positionKey], 10) || 0;
          } else if (ptsCfg[String(positionKey)] !== undefined) {
            pts = parseInt(ptsCfg[String(positionKey)], 10) || 0;
          }

          if (pts <= 0) return;

          if (isActiveWindow) {
            teamDataMap[key].totalPts += pts;
            teamDataMap[key].activeTourneys += 1;
            if (isLatestTourney) {
              teamDataMap[key].lastPts = pts;
            }
          } else {
            // Expired Points / Deducted due to W rolling window
            teamDataMap[key].expiredPts += pts;
          }
        }

        // Award 1st, 2nd, and 3rd-4th place points
        if (winnerName) awardTeamPoints(winnerName, "1");
        if (loserName) awardTeamPoints(loserName, "2");
        sfLosers.forEach(function (sfName) {
          awardTeamPoints(sfName, "3-4");
        });

      } catch (e) {
        console.error('Error parsing bracket data for tournament ' + t.id, e);
      }
    });

    // Re-sort team rows by totalPts descending
    var teamDataArray = Object.values(teamDataMap);
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
        if (data.expiredPts > 0) {
          var label = (data.lastPts > 0 ? ('+' + data.lastPts) : '0') + ' pts <small style="font-size:0.75rem; color:#ef4444;">(-' + data.expiredPts + ' trượt)</small>';
          row.cells[5].innerHTML = '<span style="color: #ef4444; font-weight: 700;">' + label + '</span>';
        } else if (data.lastPts > 0) {
          row.cells[5].innerHTML = '<span style="color: #2dd4bf; font-weight: 700;">+' + data.lastPts + ' pts</span>';
        } else {
          row.cells[5].innerHTML = '<span style="color: #94a3b8;">0 pts</span>';
        }
      }

      tbody.appendChild(row);
    });
  }

  document.addEventListener('DOMContentLoaded', calculateAndSyncRollingStandings);

})();
