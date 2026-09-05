/**
 * TOURMA - ROLLING SERIES HALL OF FAME (BẢNG VÀNG) SCRIPT (rolling-hof.js)
 * Synchronizes Server + LocalStorage match results to display tournament champions,
 * direct stage links, and Top Champions Leaderboard with Tier classifications.
 * Supports Pagination (15 rows per page) for both left and right tables.
 */

(function () {
  'use strict';

  // Global state for Tier filtering & Pagination (15 rows per page)
  window.HOF_PAGE_SIZE = 15;
  window.currentLeftTier = 'ALL';
  window.leftCurrentPage = 1;

  window.activeRightTiers = {
    'S': true,
    'A': true,
    'B': true,
    'C': true,
    'D': true
  };
  window.rightCurrentPage = 1;
  window.globalChampionsMap = {};

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

  // =========================================================================
  // PAGINATION RENDER HELPER
  // =========================================================================
  function renderPagination(infoElemId, btnsElemId, currentPage, totalPages, totalItems, unitText, jumpFuncName) {
    var infoElem = document.getElementById(infoElemId);
    var btnsElem = document.getElementById(btnsElemId);

    if (infoElem) {
      if (totalItems === 0) {
        infoElem.textContent = '0 ' + unitText;
      } else {
        infoElem.textContent = 'Trang ' + currentPage + ' / ' + totalPages + ' (' + totalItems + ' ' + unitText + ')';
      }
    }

    if (!btnsElem) return;

    if (totalPages <= 1) {
      btnsElem.innerHTML = '';
      return;
    }

    var html = '';

    // Previous Button
    var prevDisabled = (currentPage <= 1) ? ' disabled' : '';
    html += '<button type="button" class="hof-page-btn"' + prevDisabled + ' onclick="' + jumpFuncName + '(' + (currentPage - 1) + ')" title="Trang trước"><i class="fa-solid fa-chevron-left"></i></button>';

    // Page Numbers (up to 5 buttons with smart ellipses)
    var startPage = Math.max(1, currentPage - 2);
    var endPage = Math.min(totalPages, startPage + 4);
    if (endPage - startPage < 4) {
      startPage = Math.max(1, endPage - 4);
    }

    if (startPage > 1) {
      html += '<button type="button" class="hof-page-btn" onclick="' + jumpFuncName + '(1)">1</button>';
      if (startPage > 2) {
        html += '<span class="hof-page-ellipsis">...</span>';
      }
    }

    for (var p = startPage; p <= endPage; p++) {
      var activeClass = (p === currentPage) ? ' active' : '';
      html += '<button type="button" class="hof-page-btn' + activeClass + '" onclick="' + jumpFuncName + '(' + p + ')">' + p + '</button>';
    }

    if (endPage < totalPages) {
      if (endPage < totalPages - 1) {
        html += '<span class="hof-page-ellipsis">...</span>';
      }
      html += '<button type="button" class="hof-page-btn" onclick="' + jumpFuncName + '(' + totalPages + ')">' + totalPages + '</button>';
    }

    // Next Button
    var nextDisabled = (currentPage >= totalPages) ? ' disabled' : '';
    html += '<button type="button" class="hof-page-btn"' + nextDisabled + ' onclick="' + jumpFuncName + '(' + (currentPage + 1) + ')" title="Trang sau"><i class="fa-solid fa-chevron-right"></i></button>';

    btnsElem.innerHTML = html;
  }

  // =========================================================================
  // LEFT TABLE FILTERING & PAGINATION (SINGLE-SELECT TIER + SEARCH)
  // =========================================================================
  window.goToLeftPage = function (p) {
    window.leftCurrentPage = p;
    window.filterLeftTable(false);
  };

  window.setLeftTierFilter = function (tier) {
    window.currentLeftTier = (tier || 'ALL').toUpperCase();
    window.leftCurrentPage = 1;

    // Update active class on left tier buttons
    var btns = document.querySelectorAll('#leftTierFilters .hof-tier-btn');
    btns.forEach(function (btn) {
      var btnTier = (btn.getAttribute('data-tier') || '').toUpperCase();
      if (btnTier === window.currentLeftTier) {
        btn.classList.add('active');
      } else {
        btn.classList.remove('active');
      }
    });

    window.filterLeftTable(true);
  };

  window.filterLeftTable = function (resetPage) {
    if (resetPage === true) {
      window.leftCurrentPage = 1;
    }

    var searchInput = document.getElementById('leftTourneySearch');
    var q = searchInput ? searchInput.value.toLowerCase().trim() : '';
    var selectedTier = window.currentLeftTier || 'ALL';

    var allRows = Array.from(document.querySelectorAll('#hofTourneyTable tbody tr'));
    var matchingRows = [];

    allRows.forEach(function (row) {
      var rowTier = (row.getAttribute('data-tier') || 'A').toUpperCase();
      var txt = row.innerText.toLowerCase();

      var matchTier = (selectedTier === 'ALL' || rowTier === selectedTier);
      var matchQuery = (!q || txt.indexOf(q) !== -1);

      if (matchTier && matchQuery) {
        matchingRows.push(row);
      }
    });

    var totalItems = matchingRows.length;
    var pageSize = window.HOF_PAGE_SIZE || 15;
    var totalPages = Math.ceil(totalItems / pageSize) || 1;

    if (window.leftCurrentPage > totalPages) {
      window.leftCurrentPage = totalPages;
    }
    if (window.leftCurrentPage < 1) {
      window.leftCurrentPage = 1;
    }

    var startIndex = (window.leftCurrentPage - 1) * pageSize;
    var endIndex = startIndex + pageSize;

    // Hide all rows first
    allRows.forEach(function (row) {
      row.style.display = 'none';
    });

    // Show only rows in current page slice
    matchingRows.slice(startIndex, endIndex).forEach(function (row) {
      row.style.display = '';
    });

    // Update count badge
    var countBadge = document.getElementById('hofTourneysCount');
    if (countBadge) {
      countBadge.textContent = totalItems;
    }

    // Render Left Pagination
    renderPagination(
      'leftPageInfo',
      'leftPageBtns',
      window.leftCurrentPage,
      totalPages,
      totalItems,
      'giải',
      'goToLeftPage'
    );
  };

  // Backwards compatibility alias
  window.filterHofTournaments = window.filterLeftTable;

  // =========================================================================
  // RIGHT TABLE FILTERING & PAGINATION (MULTI-SELECT TIER + RECALC + SEARCH)
  // =========================================================================
  window.goToRightPage = function (p) {
    window.rightCurrentPage = p;
    window.filterRightTable(false);
  };

  window.toggleRightTierFilter = function (tier) {
    tier = (tier || 'ALL').toUpperCase();
    var allTiers = ['S', 'A', 'B', 'C', 'D'];

    if (tier === 'ALL') {
      // If clicking ALL, activate all tiers
      allTiers.forEach(function (t) {
        window.activeRightTiers[t] = true;
      });
    } else {
      // Toggle the clicked tier
      window.activeRightTiers[tier] = !window.activeRightTiers[tier];
    }

    // Check if all tiers are active
    var allActive = allTiers.every(function (t) {
      return window.activeRightTiers[t];
    });

    // Update button states in UI
    var btns = document.querySelectorAll('#rightTierFilters .hof-tier-btn');
    btns.forEach(function (btn) {
      var btnTier = (btn.getAttribute('data-tier') || '').toUpperCase();
      if (btnTier === 'ALL') {
        if (allActive) {
          btn.classList.add('active');
        } else {
          btn.classList.remove('active');
        }
      } else {
        if (window.activeRightTiers[btnTier]) {
          btn.classList.add('active');
        } else {
          btn.classList.remove('active');
        }
      }
    });

    window.rightCurrentPage = 1;
    window.filterRightTable(true);
  };

  window.filterRightTable = function (resetPage) {
    if (resetPage === true) {
      window.rightCurrentPage = 1;
    }

    var searchInput = document.getElementById('rightChampSearch');
    var q = searchInput ? searchInput.value.toLowerCase().trim() : '';

    var ctx = (typeof window.appContextPath !== 'undefined' && window.appContextPath) ? window.appContextPath : '';
    var seriesId = (typeof window.seriesIdVal !== 'undefined' && window.seriesIdVal) ? window.seriesIdVal : '';

    var champArray = [];
    var map = window.globalChampionsMap || {};

    Object.keys(map).forEach(function (key) {
      var c = map[key];
      // Compute championships won ONLY in the active tiers
      var count = 0;
      if (window.activeRightTiers.S) count += (c.tierS || 0);
      if (window.activeRightTiers.A) count += (c.tierA || 0);
      if (window.activeRightTiers.B) count += (c.tierB || 0);
      if (window.activeRightTiers.C) count += (c.tierC || 0);
      if (window.activeRightTiers.D) count += (c.tierD || 0);

      if (count > 0) {
        champArray.push({
          teamName: c.teamName,
          filteredChamps: count,
          tierS: c.tierS || 0,
          tierA: c.tierA || 0,
          tierB: c.tierB || 0,
          tierC: c.tierC || 0,
          tierD: c.tierD || 0
        });
      }
    });

    // Sort by filteredChamps desc, then tierS, tierA, tierB, tierC, tierD, then teamName
    champArray.sort(function (a, b) {
      if (b.filteredChamps !== a.filteredChamps) return b.filteredChamps - a.filteredChamps;
      if (b.tierS !== a.tierS) return b.tierS - a.tierS;
      if (b.tierA !== a.tierA) return b.tierA - a.tierA;
      if (b.tierB !== a.tierB) return b.tierB - a.tierB;
      if (b.tierC !== a.tierC) return b.tierC - a.tierC;
      if (b.tierD !== a.tierD) return b.tierD - a.tierD;
      return a.teamName.localeCompare(b.teamName);
    });

    // Apply search filter
    if (q) {
      champArray = champArray.filter(function (c) {
        return c.teamName.toLowerCase().indexOf(q) !== -1;
      });
    }

    var totalItems = champArray.length;
    var pageSize = window.HOF_PAGE_SIZE || 15;
    var totalPages = Math.ceil(totalItems / pageSize) || 1;

    if (window.rightCurrentPage > totalPages) {
      window.rightCurrentPage = totalPages;
    }
    if (window.rightCurrentPage < 1) {
      window.rightCurrentPage = 1;
    }

    var startIndex = (window.rightCurrentPage - 1) * pageSize;
    var endIndex = startIndex + pageSize;
    var pageSlice = champArray.slice(startIndex, endIndex);

    // Render page slice into table
    var champTbody = document.querySelector('#hofChampionsTable tbody');
    if (champTbody) {
      if (pageSlice.length > 0) {
        var html = '';
        pageSlice.forEach(function (c) {
          html += '<tr>' +
            '<td class="text-left" style="text-align: left !important;">' +
              '<a href="' + ctx + '/team-profile?seriesId=' + encodeURIComponent(seriesId) + '&teamName=' + encodeURIComponent(c.teamName) + '" class="hof-team-link" title="Xem hồ sơ đội ' + c.teamName + '">' +
                c.teamName +
              '</a>' +
            '</td>' +
            '<td class="text-right" style="text-align: right !important;">' +
              '<span class="hof-win-count-text">' + c.filteredChamps + '</span>' +
            '</td>' +
            '</tr>';
        });
        champTbody.innerHTML = html;
      } else {
        champTbody.innerHTML = '<tr><td colspan="2" style="text-align: center; padding: 2rem; color: #64748b;">Chưa có đội bóng nào vô địch cho phân hạng đã chọn.</td></tr>';
      }
    }

    // Update Champions count badge
    var countBadge = document.getElementById('hofChampsCount');
    if (countBadge) {
      countBadge.textContent = totalItems;
    }

    // Render Right Pagination
    renderPagination(
      'rightPageInfo',
      'rightPageBtns',
      window.rightCurrentPage,
      totalPages,
      totalItems,
      'đội',
      'goToRightPage'
    );
  };

  // Backwards compatibility alias
  window.filterHofChampions = window.filterRightTable;

  // =========================================================================
  // MAIN CALCULATION & LOCALSTORAGE SYNCHRONIZATION
  // =========================================================================
  function calculateAndSyncHallOfFame() {
    var subTourneys = window.seriesSubTournaments || [];
    var ctx = (typeof window.appContextPath !== 'undefined' && window.appContextPath) ? window.appContextPath : '';
    var seriesId = (typeof window.seriesIdVal !== 'undefined' && window.seriesIdVal) ? window.seriesIdVal : '';

    var championsMap = {};
    var teamCumulativeChamps = {};

    // 1. Process server & localStorage data for each tournament (chronological order)
    subTourneys.forEach(function (t) {
      var champName = null;

      // Check localStorage explicit championship
      var savedChampKey = "tourma_champion_" + t.id;
      if (localStorage.getItem(savedChampKey)) {
        champName = extractName(localStorage.getItem(savedChampKey));
      }

      // Check Multi-Stage config in localStorage
      var rawMultiCfg = getStorageData(['tourma_multi_config_'], t.id);
      var multiConfig = null;
      if (rawMultiCfg) {
        try { multiConfig = JSON.parse(rawMultiCfg); } catch (e) {}
      }

      var isMultiStage = !!(multiConfig && multiConfig.stage2Format) || (t.stageCount > 1) || (t.isMultiStage);
      var s1Format = (multiConfig && multiConfig.stage1Format) ? multiConfig.stage1Format.toUpperCase() : (t.stage1Format || (t.format || 'SINGLE_ELIMINATION'));
      var s2Format = (multiConfig && multiConfig.stage2Format) ? multiConfig.stage2Format.toUpperCase() : (t.stage2Format || 'SINGLE_ELIMINATION');

      // Check Stage 2 if Multi-Stage
      if (!champName && isMultiStage) {
        if (s2Format === 'SINGLE_ELIMINATION') {
          var rawS2SE = getStorageData(['tourma_bracket_matches_stage2_', 'tourma_matches_stage2_'], t.id);
          if (rawS2SE) {
            try {
              var s2Data = JSON.parse(rawS2SE);
              var mObj = s2Data.matchesMap || s2Data;
              if (mObj && typeof mObj === 'object') {
                var maxR = 0, finalMatch = null;
                Object.keys(mObj).forEach(function (k) {
                  var m = mObj[k];
                  if (m && m.roundNumber && m.roundNumber > maxR) {
                    maxR = m.roundNumber;
                    finalMatch = m;
                  }
                });
                if (finalMatch && finalMatch.winnerId) {
                  var res = resolveWinnerAndLoser(finalMatch);
                  if (res.winner) champName = res.winner;
                }
              }
            } catch (e) {}
          }
        } else if (s2Format === 'DOUBLE_ELIMINATION') {
          var rawS2DE = getStorageData(['tourma_de_matches_stage2_'], t.id);
          if (rawS2DE) {
            try {
              var s2DEData = JSON.parse(rawS2DE);
              var gfRound = s2DEData.grandFinalsRound;
              if (gfRound && gfRound.matches && gfRound.matches.length > 0) {
                var gfMatches = gfRound.matches;
                var gfFinal = (gfMatches.length > 1 && gfMatches[1].winnerId) ? gfMatches[1] : gfMatches[0];
                if (gfFinal && gfFinal.winnerId) {
                  var resGF = resolveWinnerAndLoser(gfFinal);
                  if (resGF.winner) champName = resGF.winner;
                }
              }
            } catch (e) {}
          }
        } else if (s2Format === 'ROUND_ROBIN') {
          var rawS2RR = getStorageData(['tourma_rr_matches_stage2_'], t.id);
          var s2Teams = [];
          try { s2Teams = JSON.parse(getStorageData(['tourma_stage2_teams_'], t.id)) || []; } catch (e) {}
          if (rawS2RR && s2Teams.length > 0 && window.TourmaRoundRobinAlgorithm) {
            try {
              var rrData = JSON.parse(rawS2RR);
              var s2Standings = window.TourmaRoundRobinAlgorithm.calculateStandings(s2Teams, rrData.matchesMap || rrData, (multiConfig && multiConfig.stage2Config) || {});
              if (s2Standings && s2Standings.length > 0) {
                var topTeam = s2Standings[0];
                var topN = extractName(topTeam) || (topTeam.team ? extractName(topTeam.team) : null);
                if (topN) champName = topN;
              }
            } catch (e) {}
          }
        }
      }

      // Check Single Stage if not multi-stage
      if (!champName && !isMultiStage) {
        if (s1Format === 'SINGLE_ELIMINATION') {
          var rawSE = getStorageData(['tourma_bracket_matches_', 'tourma_matches_', 'tourma_bracket_'], t.id);
          if (rawSE) {
            try {
              var bData = JSON.parse(rawSE);
              var mObj = bData.matchesMap || bData;
              if (mObj && typeof mObj === 'object') {
                var maxR = 0, finalMatch = null;
                Object.keys(mObj).forEach(function (k) {
                  var m = mObj[k];
                  if (m && m.roundNumber && m.roundNumber > maxR) {
                    maxR = m.roundNumber;
                    finalMatch = m;
                  }
                });
                if (finalMatch && finalMatch.winnerId) {
                  var res = resolveWinnerAndLoser(finalMatch);
                  if (res.winner) champName = res.winner;
                }
              }
            } catch (e) {}
          }
        } else if (s1Format === 'DOUBLE_ELIMINATION') {
          var rawDE = getStorageData(['tourma_de_matches_'], t.id);
          if (rawDE) {
            try {
              var deData = JSON.parse(rawDE);
              var gfRound = deData.grandFinalsRound;
              if (gfRound && gfRound.matches && gfRound.matches.length > 0) {
                var gfMatches = gfRound.matches;
                var gfFinal = (gfMatches.length > 1 && gfMatches[1].winnerId) ? gfMatches[1] : gfMatches[0];
                if (gfFinal && gfFinal.winnerId) {
                  var resGF = resolveWinnerAndLoser(gfFinal);
                  if (resGF.winner) champName = resGF.winner;
                }
              }
            } catch (e) {}
          }
        } else if (s1Format === 'ROUND_ROBIN') {
          var rawRR = getStorageData(['tourma_rr_matches_'], t.id);
          var s1Teams = [];
          try { s1Teams = JSON.parse(getStorageData(['tourma_teams_'], t.id)) || []; } catch (e) {}
          if (rawRR && s1Teams.length > 0 && window.TourmaRoundRobinAlgorithm) {
            try {
              var rrData = JSON.parse(rawRR);
              var rrStandings = window.TourmaRoundRobinAlgorithm.calculateStandings(s1Teams, rrData.matchesMap || rrData, rrData.config || {});
              if (rrStandings && rrStandings.length > 0) {
                var topTeam = rrStandings[0];
                var topN = extractName(topTeam) || (topTeam.team ? extractName(topTeam.team) : null);
                if (topN) champName = topN;
              }
            } catch (e) {}
          }
        }
      }

      // Check Server-rendered DOM row fallback if not found in localStorage
      var tourneyRow = document.querySelector('#hofTourneyTable tbody tr[data-tourney-id="' + t.id + '"]');
      if (!champName && tourneyRow) {
        var existingLink = tourneyRow.querySelector('.hof-champ-name-link');
        if (existingLink && existingLink.textContent) {
          champName = extractName(existingLink.textContent.trim());
        }
      }

      var champOrdinal = 0;
      if (champName) {
        var cLower = champName.toLowerCase().trim();
        champOrdinal = (teamCumulativeChamps[cLower] || 0) + 1;
        teamCumulativeChamps[cLower] = champOrdinal;
      }

      // Update Left Table row
      var tier = (t.tierName || t.tier || 'A').toUpperCase();

      if (tourneyRow) {
        var champCell = tourneyRow.querySelector('.hof-tourney-champ-cell');
        var ordinalCell = tourneyRow.querySelector('.hof-tourney-ordinal-cell');

        if (champName) {
          if (champCell) {
            champCell.className = "hof-tourney-champ-cell text-left";
            champCell.style.textAlign = "left";
            champCell.innerHTML = '<a href="' + ctx + '/team-profile?seriesId=' + encodeURIComponent(seriesId) + '&teamName=' + encodeURIComponent(champName) + '" class="hof-champ-name-link" title="Xem hồ sơ đội ' + champName + '">' +
              champName +
              '</a>';
          }
          if (ordinalCell) {
            ordinalCell.className = "hof-tourney-ordinal-cell text-right";
            ordinalCell.style.textAlign = "right";
            ordinalCell.innerHTML = '<span class="hof-ordinal-text">' + champOrdinal + '</span>';
          }
        } else {
          if (champCell) {
            champCell.className = "hof-tourney-champ-cell text-left";
            champCell.style.textAlign = "left";
            champCell.innerHTML = '<span class="hof-champ-empty">-</span>';
          }
          if (ordinalCell) {
            ordinalCell.className = "hof-tourney-ordinal-cell text-right";
            ordinalCell.style.textAlign = "right";
            ordinalCell.innerHTML = '<span class="hof-ordinal-empty">-</span>';
          }
        }
      }

      // Record in aggregate map
      if (champName) {
        var cKey = champName.toLowerCase().trim();
        if (!championsMap[cKey]) {
          championsMap[cKey] = {
            teamName: champName,
            totalChamps: 0,
            tierS: 0,
            tierA: 0,
            tierB: 0,
            tierC: 0,
            tierD: 0,
            tourneys: []
          };
        }
        championsMap[cKey].totalChamps += 1;
        championsMap[cKey].tourneys.push({ name: t.name, tier: tier, id: t.id });

        if (tier === 'S') championsMap[cKey].tierS += 1;
        else if (tier === 'A') championsMap[cKey].tierA += 1;
        else if (tier === 'B') championsMap[cKey].tierB += 1;
        else if (tier === 'C') championsMap[cKey].tierC += 1;
        else if (tier === 'D') championsMap[cKey].tierD += 1;
      }
    });

    // Also check if server-rendered rows in right table had any champions that we should incorporate
    if (Object.keys(championsMap).length === 0) {
      var serverChampRows = document.querySelectorAll('#hofChampionsTable tbody tr');
      serverChampRows.forEach(function (r) {
        var tName = r.getAttribute('data-team-name');
        if (tName) {
          var k = tName.toLowerCase().trim();
          championsMap[k] = {
            teamName: tName,
            totalChamps: parseInt(r.getAttribute('data-total') || '0', 10),
            tierS: parseInt(r.getAttribute('data-tier-s') || '0', 10),
            tierA: parseInt(r.getAttribute('data-tier-a') || '0', 10),
            tierB: parseInt(r.getAttribute('data-tier-b') || '0', 10),
            tierC: parseInt(r.getAttribute('data-tier-c') || '0', 10),
            tierD: parseInt(r.getAttribute('data-tier-d') || '0', 10),
            tourneys: []
          };
        }
      });
    }

    window.globalChampionsMap = championsMap;

    // Apply filtering and pagination rendering to both tables
    window.filterLeftTable(true);
    window.filterRightTable(true);
  }

  document.addEventListener('DOMContentLoaded', calculateAndSyncHallOfFame);
  setTimeout(calculateAndSyncHallOfFame, 50);
  setTimeout(calculateAndSyncHallOfFame, 200);
})();
