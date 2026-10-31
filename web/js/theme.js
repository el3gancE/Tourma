(function () {
    'use strict';
    var root = document.documentElement;
    var key = 'tourma-ui-theme';
    var accentKey = 'tourma-ui-accent';
    var preference, accent;
    try { preference = localStorage.getItem(key); accent = localStorage.getItem(accentKey); } catch (e) {}
    var system = window.matchMedia('(prefers-color-scheme: dark)');
    var hasPreference = preference === 'dark' || preference === 'light';
    root.dataset.theme = hasPreference ? preference : (system.matches ? 'dark' : 'light');
    // Preserve the existing light-wine / dark-blue appearance until a palette is chosen.
    root.dataset.accent = accent === 'wine' || accent === 'blue' ? accent : (root.dataset.theme === 'dark' ? 'blue' : 'wine');
    function updateControls() {
        var dark = root.dataset.theme === 'dark';
        document.querySelectorAll('[data-accent-label]').forEach(function (label) { label.textContent = root.dataset.accent === 'blue' ? 'Xanh' : 'Đỏ'; });
        document.querySelectorAll('[data-mode-label]').forEach(function (label) { label.textContent = dark ? 'Tối' : 'Sáng'; });
        document.querySelectorAll('[data-mode-switch]').forEach(function (button) { button.setAttribute('aria-checked', String(dark)); });
        document.querySelectorAll('[data-accent-switch]').forEach(function (button) { button.setAttribute('aria-checked', String(root.dataset.accent === 'blue')); });
        document.querySelectorAll('[data-theme-choice]').forEach(function (button) {
            button.setAttribute('aria-pressed', String(button.dataset.themeChoice === root.dataset.theme && button.dataset.accentChoice === root.dataset.accent));
        });
        document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
            button.setAttribute('aria-label', dark ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối');
            button.setAttribute('aria-pressed', String(dark));
            var label = button.querySelector('[data-theme-label]');
            if (label) label.textContent = dark ? 'Giao diện sáng' : 'Giao diện tối';
        });
    }
    function select(theme, color) {
        root.dataset.themeInteractive = "true";
        root.dataset.theme = theme;
        root.dataset.accent = color;
        hasPreference = true;
        try { localStorage.setItem(key, theme); localStorage.setItem(accentKey, color); } catch (e) {}
        updateControls();
    }
    document.addEventListener('DOMContentLoaded', function () {
        updateControls();
        document.querySelectorAll('[data-theme-choice]').forEach(function (button) {
            button.addEventListener('click', function () { select(button.dataset.themeChoice, button.dataset.accentChoice); });
        });
        document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
            button.addEventListener('click', function () { select(root.dataset.theme === 'dark' ? 'light' : 'dark', root.dataset.accent); });
        });
        document.querySelectorAll('[data-mode-switch]').forEach(function (button) {
            button.addEventListener('click', function () { select(root.dataset.theme === 'dark' ? 'light' : 'dark', root.dataset.accent); });
        });
        document.querySelectorAll('[data-accent-switch]').forEach(function (button) {
            button.addEventListener('click', function () { select(root.dataset.theme, root.dataset.accent === 'blue' ? 'wine' : 'blue'); });
        });
    });
    if (system.addEventListener) system.addEventListener('change', function (event) {
        if (!hasPreference) { root.dataset.theme = event.matches ? 'dark' : 'light'; root.dataset.accent = event.matches ? 'blue' : 'wine'; updateControls(); }
    });
})();