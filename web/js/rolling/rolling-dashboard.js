/**
 * TOURMA - ROLLING WINDOW SERIES DASHBOARD INTERACTIVE SCRIPT
 * Manages smart boundary-aware tooltip alignment, native tooltip suppression, and touch interactions
 */

(function () {
  'use strict';

  function adjustTooltipPosition(pill) {
    if (!pill) return;
    var tooltip = pill.querySelector('.tourma-badge-tooltip');
    if (!tooltip) return;

    // Proactively remove any title attribute on the pill or its children to prevent native browser tooltip
    if (pill.hasAttribute('title')) pill.removeAttribute('title');
    var elementsWithTitle = pill.querySelectorAll('[title]');
    elementsWithTitle.forEach(function (el) { el.removeAttribute('title'); });

    var pillRect = pill.getBoundingClientRect();
    var windowWidth = window.innerWidth || document.documentElement.clientWidth;
    var tooltipWidth = tooltip.offsetWidth > 0 ? tooltip.offsetWidth : 240;

    // Find parent container or section card
    var container = pill.closest('.dashboard-section-card') || document.body;
    var containerRect = container.getBoundingClientRect();

    // Check if positioning left: 0 (extending to the right) causes overflow past container right edge or screen right edge
    var overflowRight = (pillRect.left + tooltipWidth > containerRect.right - 10) || (pillRect.left + tooltipWidth > windowWidth - 16);

    if (overflowRight) {
      tooltip.classList.add('align-right');
      tooltip.classList.remove('align-left');
      tooltip.style.left = 'auto';
      tooltip.style.right = '0px';
    } else {
      tooltip.classList.add('align-left');
      tooltip.classList.remove('align-right');
      tooltip.style.left = '0px';
      tooltip.style.right = 'auto';
    }
  }

  function initAllTooltips() {
    var tagItems = document.querySelectorAll('.tourma-badge-pill');
    tagItems.forEach(function (item) {
      // Strip any native tooltip title
      item.removeAttribute('title');
      var allChilds = item.querySelectorAll('*');
      allChilds.forEach(function (el) { el.removeAttribute('title'); });

      // Initial alignment computation
      adjustTooltipPosition(item);

      // Dynamic listeners
      item.addEventListener('mouseenter', function () {
        adjustTooltipPosition(item);
      });
      item.addEventListener('pointerenter', function () {
        adjustTooltipPosition(item);
      });
      item.addEventListener('focus', function () {
        adjustTooltipPosition(item);
      });
      item.addEventListener('click', function (e) {
        var wasActive = item.classList.contains('active-touch');
        tagItems.forEach(function (t) { t.classList.remove('active-touch'); });
        if (!wasActive) {
          adjustTooltipPosition(item);
          item.classList.add('active-touch');
        }
      });
    });

    document.addEventListener('click', function (e) {
      if (!e.target.closest('.tourma-badge-pill')) {
        tagItems.forEach(function (t) { t.classList.remove('active-touch'); });
      }
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initAllTooltips);
  } else {
    initAllTooltips();
  }

  window.addEventListener('resize', function () {
    var tagItems = document.querySelectorAll('.tourma-badge-pill');
    tagItems.forEach(function (item) {
      adjustTooltipPosition(item);
    });
  });

})();
