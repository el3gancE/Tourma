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
      var textarea = modal.querySelector('textarea[name="bulkTeamNames"]');
      if (textarea) {
        textarea.value = '';
        setTimeout(function () {
          textarea.focus();
        }, 100);
      }
    }
  };

  window.closeAddTeamPopup = function () {
    var modal = document.getElementById('modalAddTeamPopup');
    if (modal) {
      modal.style.display = 'none';
      var textarea = modal.querySelector('textarea[name="bulkTeamNames"]');
      if (textarea) {
        textarea.value = '';
      }
    }
  };

  document.addEventListener('DOMContentLoaded', function () {
    var modal = document.getElementById('modalAddTeamPopup');
    if (modal) {
      modal.addEventListener('click', function (e) {
        if (e.target === modal) {
          window.closeAddTeamPopup();
        }
      });
    }
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') {
        window.closeAddTeamPopup();
      }
    });
  });

})();
