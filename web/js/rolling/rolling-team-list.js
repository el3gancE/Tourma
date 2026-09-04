/**
 * TOURMA - ROLLING WINDOW SERIES PARTNER TEAM LIST SCRIPT
 * Modal and search filter management
 */

(function () {
  'use strict';

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

  window.filterPartnerTeams = function (query) {
    var q = (query || '').toLowerCase().trim();
    var rows = document.querySelectorAll('#partnerTeamTable tbody tr');

    rows.forEach(function (row) {
      var txt = row.innerText.toLowerCase();
      if (!q || txt.includes(q)) {
        row.style.display = '';
      } else {
        row.style.display = 'none';
      }
    });
  };

})();
