(function () {
    'use strict';

    var root = document.documentElement;
    var key = 'tourma-ui-theme';
    var system = window.matchMedia('(prefers-color-scheme: dark)');
    var preference;
    try { preference = localStorage.getItem(key); } catch (e) {}

    var hasPreference = preference === 'dark' || preference === 'light';
    root.dataset.theme = hasPreference ? preference : (system.matches ? 'dark' : 'light');

    function updateControls() {
        var dark = root.dataset.theme === 'dark';
        document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
            button.setAttribute('aria-label', dark ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối');
            var label = button.querySelector('[data-theme-label]');
            if (label) label.textContent = dark ? 'Giao diện sáng' : 'Giao diện tối';
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        updateControls();
        document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
            button.addEventListener('click', function () {
                var next = root.dataset.theme === 'dark' ? 'light' : 'dark';
                root.dataset.theme = next;
                hasPreference = true;
                try { localStorage.setItem(key, next); } catch (e) {}
                updateControls();
            });
        });
    });

    system.addEventListener('change', function (event) {
        if (hasPreference) return;
        root.dataset.theme = event.matches ? 'dark' : 'light';
        updateControls();
    });
})();
