/**
 * TOURMA - ROLLING SUB-TOURNAMENT LIST SCRIPT
 * Instant search & status filter for sub-tournaments
 */

(function () {
  'use strict';

  window.filterTournaments = function (query) {
    var q = (query || '').toLowerCase().trim();
    var cards = document.querySelectorAll('.subtourney-item-card');

    cards.forEach(function (card) {
      var txt = card.innerText.toLowerCase();
      if (!q || txt.includes(q)) {
        card.style.display = 'flex';
      } else {
        card.style.display = 'none';
      }
    });
  };

  window.filterByStatus = function (status, btnElement) {
    var btns = document.querySelectorAll('.status-filter-btn');
    btns.forEach(function (b) { b.classList.remove('active'); });
    if (btnElement) btnElement.classList.add('active');

    var cards = document.querySelectorAll('.subtourney-item-card');
    cards.forEach(function (card) {
      var cardStatus = card.getAttribute('data-status') || '';
      if (status === 'ALL' || cardStatus.toUpperCase() === status.toUpperCase()) {
        card.style.display = 'flex';
      } else {
        card.style.display = 'none';
      }
    });
  };

})();
