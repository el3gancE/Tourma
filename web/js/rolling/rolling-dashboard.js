/**
 * TOURMA - ROLLING WINDOW SERIES DASHBOARD INTERACTIVE SCRIPT
 * Handles tab switching, search filtering, partner modal, and phase settlement triggers
 */

(function () {
  'use strict';

  window.switchRollingTab = function (tabId, btnEl) {
    var tabs = document.querySelectorAll('.rolling-tab-content');
    tabs.forEach(function (tab) {
      tab.style.display = 'none';
    });

    var btns = document.querySelectorAll('.rolling-tab-btn');
    btns.forEach(function (btn) {
      btn.classList.remove('active');
    });

    var targetTab = document.getElementById(tabId);
    if (targetTab) {
      targetTab.style.display = 'block';
    }

    if (btnEl) {
      btnEl.classList.add('active');
    }
  };

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

  window.openAddPartnerModal = function () {
    var modal = document.getElementById('modalAddPartner');
    if (modal) {
      modal.style.display = 'flex';
    }
  };

  window.closeAddPartnerModal = function () {
    var modal = document.getElementById('modalAddPartner');
    if (modal) {
      modal.style.display = 'none';
    }
  };

  // Check URL parameter for active tab
  document.addEventListener('DOMContentLoaded', function () {
    var urlParams = new URLSearchParams(window.location.search);
    var tab = urlParams.get('tab');
    if (tab === 'partners') {
      var btn = document.getElementById('btnTabPartners');
      window.switchRollingTab('tabPartners', btn);
    }
  });

})();
