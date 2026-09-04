/**
 * TOURMA - ROLLING WINDOW SERIES PARTNER TEAM LIST SCRIPT
 * Modal, search filter, and table sorting
 */

(function () {
  'use strict';

  var currentSortCol = 'name';
  var currentSortDir = 'asc';

  window.openAddPartnerModal = function () {
    var modal = document.getElementById('modalAddPartner') || document.getElementById('modalAddTeamPopup');
    if (modal) {
      modal.style.display = 'flex';
    }
  };

  window.closeAddPartnerModal = function () {
    var modal = document.getElementById('modalAddPartner') || document.getElementById('modalAddTeamPopup');
    if (modal) {
      modal.style.display = 'none';
    }
  };

  window.filterPartnerTeams = function (query) {
    var q = (query || '').toLowerCase().trim();
    var rows = document.querySelectorAll('#partnerTeamTable tbody tr[data-team-name]');

    rows.forEach(function (row) {
      var tName = (row.getAttribute('data-team-name') || '').toLowerCase();
      var txt = row.innerText.toLowerCase();
      if (!q || tName.includes(q) || txt.includes(q)) {
        row.style.display = '';
      } else {
        row.style.display = 'none';
      }
    });

    updateRowSTT();
  };

  function updateRowSTT() {
    var rows = document.querySelectorAll('#partnerTeamTable tbody tr[data-team-name]');
    var stt = 1;
    rows.forEach(function (row) {
      if (row.style.display !== 'none') {
        var sttCell = row.querySelector('.row-stt');
        if (sttCell) {
          sttCell.innerText = stt++;
        }
      }
    });
  }

  function updateSortIcons(col, dir) {
    var icons = {
      name: document.querySelector('.sort-icon-name'),
      tourneys: document.querySelector('.sort-icon-tourneys'),
      date: document.querySelector('.sort-icon-date')
    };

    for (var key in icons) {
      var icon = icons[key];
      if (icon) {
        if (key === col) {
          icon.className = (dir === 'asc') ? 'fa-solid fa-sort-up sort-icon sort-icon-' + key : 'fa-solid fa-sort-down sort-icon sort-icon-' + key;
          icon.style.color = '#2dd4bf';
        } else {
          icon.className = 'fa-solid fa-sort sort-icon sort-icon-' + key;
          icon.style.color = '#64748b';
        }
      }
    }
  }

  window.sortTable = function (col) {
    if (col === currentSortCol) {
      currentSortDir = (currentSortDir === 'asc') ? 'desc' : 'asc';
    } else {
      currentSortCol = col;
      if (col === 'tourneys' || col === 'date') {
        currentSortDir = 'desc'; // High to low or Newest first
      } else {
        currentSortDir = 'asc'; // A-Z first
      }
    }

    applySorting(currentSortCol, currentSortDir);
  };

  function applySorting(col, dir) {
    var tbody = document.querySelector('#partnerTeamTable tbody');
    if (!tbody) return;

    var rows = Array.from(tbody.querySelectorAll('tr[data-team-name]'));
    if (rows.length <= 1) return;

    rows.sort(function (a, b) {
      var res = 0;
      if (col === 'name') {
        var nameA = (a.getAttribute('data-team-name') || '').trim();
        var nameB = (b.getAttribute('data-team-name') || '').trim();
        res = nameA.localeCompare(nameB, 'vi', { sensitivity: 'base' });
      } else if (col === 'tourneys') {
        var elA = a.querySelector('.tourneys-participated-val');
        var elB = b.querySelector('.tourneys-participated-val');
        var countA = elA ? parseInt(elA.innerText, 10) || 0 : (parseInt(a.getAttribute('data-tourney-count'), 10) || 0);
        var countB = elB ? parseInt(elB.innerText, 10) || 0 : (parseInt(b.getAttribute('data-tourney-count'), 10) || 0);
        res = countA - countB;
        if (res === 0) {
          var nameA = (a.getAttribute('data-team-name') || '').trim();
          var nameB = (b.getAttribute('data-team-name') || '').trim();
          return nameA.localeCompare(nameB, 'vi', { sensitivity: 'base' });
        }
      } else if (col === 'date') {
        var dateA = parseInt(a.getAttribute('data-created-at'), 10) || 0;
        var dateB = parseInt(b.getAttribute('data-created-at'), 10) || 0;
        res = dateA - dateB;
        if (res === 0) {
          var nameA = (a.getAttribute('data-team-name') || '').trim();
          var nameB = (b.getAttribute('data-team-name') || '').trim();
          return nameA.localeCompare(nameB, 'vi', { sensitivity: 'base' });
        }
      }

      return (dir === 'desc') ? -res : res;
    });

    rows.forEach(function (row) {
      tbody.appendChild(row);
    });

    updateRowSTT();
    updateSortIcons(col, dir);
  }

  function initTeamList() {
    applySorting('name', 'asc'); // Default A-Z sort
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initTeamList);
  } else {
    initTeamList();
  }

})();
