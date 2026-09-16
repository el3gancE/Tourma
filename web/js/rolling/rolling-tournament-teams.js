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

  function normalizeStr(str) {
    if (!str) return '';
    try {
      return String(str).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
    } catch (e) {
      return String(str).toLowerCase().trim();
    }
  }

  function isTeamSelf(name, targetKey) {
    if (!name || !targetKey) return false;
    var n = normalizeStr(name);
    var t = normalizeStr(targetKey);
    if (n === t) return true;
    var cTarget = t.replace(/[^a-z0-9]/g, '');
    var c = n.replace(/[^a-z0-9]/g, '');
    if (c && cTarget && c === cTarget) return true;

    var d1 = n.replace(/[^0-9]/g, '');
    var d2 = t.replace(/[^0-9]/g, '');
    if (d1 !== d2) {
      if (d1 !== '' || d2 !== '') return false;
    }

    if (c.length >= 6 && cTarget.length >= 6) {
      if (Math.abs(c.length - cTarget.length) <= 3) {
        if (c.indexOf(cTarget) !== -1 || cTarget.indexOf(c) !== -1) {
          return true;
        }
      }
    }
    return false;
  }

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

    // 2. Stage 1 Loser Bracket / Qualification matching
    if (positionKey === "s1_lb_cut" || altKey === "s1_lb_cut" || positionKey === "Loser's Qualification" || altKey === "Loser's Qualification") {
      if (ptsCfg["s1_lb_cut"] !== undefined) return parseInt(ptsCfg["s1_lb_cut"], 10) || 0;
      for (var r = 10; r >= 1; r--) {
        if (ptsCfg["s1_lb_r" + r] !== undefined) {
          return parseInt(ptsCfg["s1_lb_r" + r], 10) || 0;
        }
      }
      if (ptsCfg["stage1_eliminated"] !== undefined) return parseInt(ptsCfg["stage1_eliminated"], 10) || 0;
    }

    if (String(positionKey).startsWith("s1_lb_r") || (altKey && String(altKey).startsWith("s1_lb_r"))) {
      var kStr = String(positionKey).startsWith("s1_lb_r") ? String(positionKey) : String(altKey);
      var rNum = parseInt(kStr.replace("s1_lb_r", ""), 10);
      if (ptsCfg["s1_lb_r" + rNum] !== undefined) return parseInt(ptsCfg["s1_lb_r" + rNum], 10) || 0;
      if (rNum === 1) return 0;
      if (ptsCfg["s1_lb_cut"] !== undefined) return parseInt(ptsCfg["s1_lb_cut"], 10) || 0;
      if (ptsCfg["stage1_eliminated"] !== undefined) return parseInt(ptsCfg["stage1_eliminated"], 10) || 0;
    }

    if (positionKey === "stage1_eliminated" || altKey === "stage1_eliminated") {
      if (ptsCfg["stage1_eliminated"] !== undefined) return parseInt(ptsCfg["stage1_eliminated"], 10) || 0;
      if (ptsCfg["s1_lb_cut"] !== undefined) return parseInt(ptsCfg["s1_lb_cut"], 10) || 0;
      for (var r = 10; r >= 1; r--) {
        if (ptsCfg["s1_lb_r" + r] !== undefined) return parseInt(ptsCfg["s1_lb_r" + r], 10) || 0;
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
      } else if (pMin >= 65 && pMin <= 96) {
        if (ptsCfg["65-96"] !== undefined) return parseInt(ptsCfg["65-96"], 10) || 0;
        if (ptsCfg["s1_lb_r2"] !== undefined) return parseInt(ptsCfg["s1_lb_r2"], 10) || 0;
        if (ptsCfg["s1_lb_cut"] !== undefined) return parseInt(ptsCfg["s1_lb_cut"], 10) || 0;
        if (ptsCfg["65-128"] !== undefined) return parseInt(ptsCfg["65-128"], 10) || 0;
        if (ptsCfg["stage1_eliminated"] !== undefined) return parseInt(ptsCfg["stage1_eliminated"], 10) || 0;
      } else if (pMin >= 97) {
        if (ptsCfg["s1_lb_r1"] !== undefined) return parseInt(ptsCfg["s1_lb_r1"], 10) || 0;
        if (ptsCfg["97-128"] !== undefined) return parseInt(ptsCfg["97-128"], 10) || 0;
        if (ptsCfg["65-128"] !== undefined) return parseInt(ptsCfg["65-128"], 10) || 0;
      }
    }
    return 0;
  }

  function getStorageData(prefixList, id) {
    if (!prefixList || prefixList.length === 0 || !id) return null;
    for (var i = 0; i < prefixList.length; i++) {
      var p = prefixList[i];
      var val = localStorage.getItem(p + id);
      if (val) return val;
      val = localStorage.getItem(p + 'tournament_' + id);
      if (val) return val;
    }
    return null;
  }

  // Calculate realtime standings across completed sub-tournaments and update modal + rank map
  window.calculateAndSyncModalStandings = function () {
    var modalList = document.getElementById('partnerModalCheckboxList');
    if (!modalList) return;

    var items = Array.from(modalList.querySelectorAll('.partner-checkbox-item'));
    if (items.length === 0) return;

    var curTid = (typeof window.currentSubTourneyId !== 'undefined' && window.currentSubTourneyId) ? window.currentSubTourneyId : getTourneyId();

    // Extract partner list
    var partners = window.seriesPartners || [];
    if (!partners || partners.length === 0) {
      partners = items.map(function(item) {
        var rawName = item.getAttribute('data-team-name') || '';
        if (!rawName) {
          var nameSpan = item.querySelector('div > span:not(.rank-badge)');
          if (nameSpan) rawName = nameSpan.textContent.replace('(Đã thêm)', '').trim();
        }
        return { name: rawName, id: item.getAttribute('data-team-id') || rawName };
      });
    }

    var result = null;
    if (window.TourmaRollingStandingsEngine && typeof window.TourmaRollingStandingsEngine.calculateSeriesStandings === 'function') {
      result = window.TourmaRollingStandingsEngine.calculateSeriesStandings({
        subTourneys: window.seriesSubTournaments,
        partners: partners,
        phaseSize: window.seriesPhaseSize,
        serverTourneyPoints: window.serverTourneyPoints,
        serverTourneyParticipation: window.serverTourneyParticipation,
        excludeTourneyId: curTid
      });
    }

    if (result && result.rankMap && result.teamDataArray) {
      window.seriesStandingsRankMap = result.rankMap;

      // Map items by team name
      var itemMap = {};
      items.forEach(function (item) {
        var rawName = item.getAttribute('data-team-name') || '';
        if (!rawName) {
          var nameSpan = item.querySelector('div > span:not(.rank-badge)');
          if (nameSpan) rawName = nameSpan.textContent.replace('(Đã thêm)', '').trim();
        }
        itemMap[rawName.trim().toLowerCase()] = item;
      });

      // Sort & update DOM elements in modal according to exact BXH teamDataArray
      result.teamDataArray.forEach(function (data, rankIdx) {
        var teamKey = data.name.trim().toLowerCase();
        var item = itemMap[teamKey];
        if (!item) {
          var itemKeys = Object.keys(itemMap);
          for (var i = 0; i < itemKeys.length; i++) {
            if (isTeamSelf(data.name, itemKeys[i])) {
              item = itemMap[itemKeys[i]];
              break;
            }
          }
        }
        if (!item) return;

        var rank = data.rank;
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
    }
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

      var dataA = rankMap[nameA];
      if (!dataA) {
        var keysA = Object.keys(rankMap);
        for (var i = 0; i < keysA.length; i++) {
          if (isTeamSelf(nameA, keysA[i])) { dataA = rankMap[keysA[i]]; break; }
        }
      }

      var dataB = rankMap[nameB];
      if (!dataB) {
        var keysB = Object.keys(rankMap);
        for (var j = 0; j < keysB.length; j++) {
          if (isTeamSelf(nameB, keysB[j])) { dataB = rankMap[keysB[j]]; break; }
        }
      }

      var rankA = (dataA && dataA.rank) ? dataA.rank : 99999;
      var rankB = (dataB && dataB.rank) ? dataB.rank : 99999;

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
    if (typeof window.saveHideSeedConfig === 'function') {
      window.saveHideSeedConfig();
    }
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
