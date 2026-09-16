/**
 * TOURMA - REUSABLE EDIT SERIES POPUP SCRIPT
 * Modal open, close, and phase size adjustment handlers for Series configuration
 */

(function () {
  'use strict';

  window.openEditSeriesPopup = function (seriesData) {
    var modal = document.getElementById('modalEditSeriesPopup');
    if (!modal) return;

    if (seriesData) {
      if (seriesData.id) {
        var idInp = document.getElementById('editSeriesIdInput');
        if (idInp) idInp.value = seriesData.id;
      }
      if (seriesData.name !== undefined) {
        var nameInp = document.getElementById('editSeriesNameInput');
        if (nameInp) nameInp.value = seriesData.name;
      }
      if (seriesData.phaseSize !== undefined) {
        var phaseInp = document.getElementById('editSeriesPhaseSizeInput');
        if (phaseInp) phaseInp.value = seriesData.phaseSize;
      }
      if (seriesData.status) {
        var statusSel = document.getElementById('editSeriesStatusSelect');
        if (statusSel) statusSel.value = seriesData.status;
      }
      if (seriesData.actionUrl) {
        var form = document.getElementById('editSeriesForm');
        if (form) form.action = seriesData.actionUrl;
      }
    }

    modal.style.display = 'flex';
    var nameField = document.getElementById('editSeriesNameInput');
    if (nameField) {
      setTimeout(function () {
        nameField.focus();
      }, 100);
    }
  };

  window.closeEditSeriesPopup = function () {
    var modal = document.getElementById('modalEditSeriesPopup');
    if (modal) {
      modal.style.display = 'none';
    }
  };

  window.adjustPhaseSize = function (delta) {
    var input = document.getElementById('editSeriesPhaseSizeInput');
    if (!input) return;
    var current = parseInt(input.value, 10) || 10;
    var next = current + delta;
    if (next < 1) next = 1;
    if (next > 100) next = 100;
    input.value = next;
  };

  window.setPresetPhaseSize = function (val) {
    var input = document.getElementById('editSeriesPhaseSizeInput');
    if (input) {
      input.value = val;
    }
  };

  document.addEventListener('DOMContentLoaded', function () {
    var modal = document.getElementById('modalEditSeriesPopup');
    if (modal) {
      modal.addEventListener('click', function (e) {
        if (e.target === modal) {
          window.closeEditSeriesPopup();
        }
      });
    }
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') {
        window.closeEditSeriesPopup();
      }
    });
  });

})();
