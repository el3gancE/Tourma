/* Presentation only. Existing configuration validation and saving stay in the JSP. */
(function () {
    'use strict';
    var navigation = document.querySelector('.tournament-navigation');
    var desktop = window.matchMedia('(min-width: 1000px)');
    function sizeNavigation() { if (navigation) navigation.open = desktop.matches; }
    sizeNavigation();
    if (desktop.addEventListener) desktop.addEventListener('change', sizeNavigation);
    // Keep accessible selection state in sync with existing .active classes.
    document.querySelectorAll('.stage-toggle-btn,.format-pill-btn,.tier-pill-btn').forEach(function (button) {
        function update() { button.setAttribute('aria-pressed', String(button.classList.contains('active'))); }
        update();
        new MutationObserver(update).observe(button, { attributes: true, attributeFilter: ['class'] });
    });
})();
