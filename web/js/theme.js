(function () {
    'use strict';
    var root = document.documentElement;
    var key = 'tourma-ui-theme';
    var preference;
    try { preference = localStorage.getItem(key); } catch (e) { /* Storage is optional. */ }
    var system = window.matchMedia('(prefers-color-scheme: dark)');
    var hasPreference = preference === 'dark' || preference === 'light';
    root.dataset.theme = hasPreference ? preference : (system.matches ? 'dark' : 'light');
    function updateControls() {
        var dark = root.dataset.theme === 'dark';
        document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
            button.setAttribute('aria-label', dark ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối');
            button.setAttribute('aria-pressed', String(dark));
            var label = button.querySelector('[data-theme-label]');
            if (label) label.textContent = dark ? 'Giao diện sáng' : 'Giao diện tối';
            var icon = button.querySelector('i');
            if (icon) icon.className = 'fa-solid ' + (dark ? 'fa-sun' : 'fa-moon');
        });
    }
    document.addEventListener('DOMContentLoaded', function () {
        updateControls();
        document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
            button.addEventListener('click', function () {
                root.dataset.theme = root.dataset.theme === 'dark' ? 'light' : 'dark';
                hasPreference = true;
                try { localStorage.setItem(key, root.dataset.theme); } catch (e) {}
                updateControls();
            });
        });
    });
    if (system.addEventListener) system.addEventListener('change', function (event) {
        if (!hasPreference) { root.dataset.theme = event.matches ? 'dark' : 'light'; updateControls(); }
    });
})();
