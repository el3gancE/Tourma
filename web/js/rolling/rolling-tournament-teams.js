/**
 * TOURMA - ROLLING WINDOW SERIES STEP 3: SUB-TOURNAMENT TEAMS SCRIPT
 * Modal handlers, quick partner selection (BXH, Top, Bottom, Random), search filter & HTML5 Drag & Drop
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

  window.openPartnerSelectModal = function () {
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
  });

})();
