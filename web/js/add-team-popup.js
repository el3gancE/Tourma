/**
 * TOURMA - REUSABLE ADD TEAM POPUP SCRIPT
 * Modal open and close handlers for generic team addition across all formats
 */

(function () {
  'use strict';

  window.openAddTeamPopup = function () {
    var modal = document.getElementById('modalAddTeamPopup');
    if (modal) {
      modal.style.display = 'flex';
    }
  };

  window.closeAddTeamPopup = function () {
    var modal = document.getElementById('modalAddTeamPopup');
    if (modal) {
      modal.style.display = 'none';
    }
  };

})();
