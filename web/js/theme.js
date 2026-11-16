(function () {
    'use strict';
    var root = document.documentElement;
    var key = 'tourma-ui-theme';
    var accentKey = 'tourma-ui-accent';
    var preference, accent;
    var colorsKey = 'tourma-ui-special-colors';
    var specialColors = 'on';
    try { if (localStorage.getItem(colorsKey) === 'off') specialColors = 'off'; } catch (e) { }
    root.dataset.specialColors = specialColors;
    try { preference = localStorage.getItem(key); accent = localStorage.getItem(accentKey); } catch (e) { }
    var system = window.matchMedia('(prefers-color-scheme: dark)');
    var hasPreference = preference === 'dark' || preference === 'light';
    root.dataset.theme = hasPreference ? preference : (system.matches ? 'dark' : 'light');

    var ACCENTS = ['wine', 'blue', 'green', 'orange'];
    var hasAccent = ACCENTS.indexOf(accent) !== -1;
    root.dataset.accent = hasAccent ? accent : (root.dataset.theme === 'dark' ? 'blue' : 'wine');

    var ACCENT_NAMES = {
        wine: 'Đỏ',
        blue: 'Xanh dương',
        green: 'Xanh lá',
        orange: 'Cam'
    };

    function updateControls() {
        var dark = root.dataset.theme === 'dark';
        var currAccent = root.dataset.accent || 'wine';
        document.querySelectorAll('[data-special-colors-toggle]').forEach(function (button) { button.setAttribute('aria-checked', String(root.dataset.specialColors !== 'off')); });
        document.querySelectorAll('[data-accent-label]').forEach(function (label) { label.textContent = ACCENT_NAMES[currAccent] || 'Đỏ'; });
        document.querySelectorAll('[data-mode-label]').forEach(function (label) { label.textContent = dark ? 'Tối' : 'Sáng'; });
        document.querySelectorAll('[data-mode-switch]').forEach(function (button) { button.setAttribute('aria-checked', String(dark)); });
        document.querySelectorAll('[data-accent-switch]').forEach(function (button) {
            button.setAttribute('data-accent-current', currAccent);
            button.setAttribute('aria-label', 'Đổi màu: ' + (ACCENT_NAMES[currAccent] || 'Đỏ'));
            button.setAttribute('title', 'Đổi màu (Đỏ / Xanh dương / Xanh lá / Cam) - Hiện tại: ' + (ACCENT_NAMES[currAccent] || 'Đỏ'));
        });
        document.querySelectorAll('[data-accent-val]').forEach(function (button) {
            var isCurrent = button.dataset.accentVal === currAccent;
            button.setAttribute('aria-checked', String(isCurrent));
            button.classList.toggle('is-active', isCurrent);
        });
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
        try { localStorage.setItem(key, theme); localStorage.setItem(accentKey, color); } catch (e) { }
        updateControls();
    }
    document.addEventListener('DOMContentLoaded', function () {
        updateControls();
        document.querySelectorAll('[data-special-colors-toggle]').forEach(function (button) {
            button.addEventListener('click', function () {
                root.dataset.specialColors = root.dataset.specialColors === 'off' ? 'on' : 'off';
                try { localStorage.setItem(colorsKey, root.dataset.specialColors); } catch (e) { }
                updateControls();
            });
        });
        document.addEventListener('click', function (event) {
            document.querySelectorAll('.display-settings[open]').forEach(function (menu) { if (!menu.contains(event.target)) menu.open = false; });
        });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') document.querySelectorAll('.display-settings[open]').forEach(function (menu) { menu.open = false; menu.querySelector('summary').focus(); });
        });
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
            button.addEventListener('click', function () {
                var curr = root.dataset.accent || 'wine';
                var idx = ACCENTS.indexOf(curr);
                var next = ACCENTS[(idx + 1) % ACCENTS.length];
                select(root.dataset.theme, next);
            });
        });
        document.querySelectorAll('[data-accent-val]').forEach(function (button) {
            button.addEventListener('click', function () {
                select(root.dataset.theme, button.dataset.accentVal);
            });
        });
    });
    if (system.addEventListener) system.addEventListener('change', function (event) {
        if (!hasPreference) { root.dataset.theme = event.matches ? 'dark' : 'light'; updateControls(); }
    });
})();