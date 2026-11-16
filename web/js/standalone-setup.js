/* The standalone editor submits format settings and teams in one request. */
(function () {
    'use strict';
    var form = document.getElementById('configureTeamsForm');
    var editor = document.getElementById('configureFormatForm');
    if (!form || !editor) return;

    function copySettings() {
        editor.querySelectorAll('input[type="hidden"][name]').forEach(function (source) {
            if (source.name === 'id' || source.name === 'seriesId') return;
            var target = form.querySelector('input[name="' + source.name + '"]');
            if (!target) {
                target = document.createElement('input');
                target.type = 'hidden';
                target.name = source.name;
                form.appendChild(target);
            }
            target.value = source.value;
        });
    }

    window.prepareStandaloneSetup = function (event) {
        // Copy constraints before team validation, including newly copied configurations.
        var count = document.getElementById('targetTeamCount');
        window.targetTeamCount = count ? Number(count.value) || 0 : 0;
        if (typeof window.updateTargetCountBadge === 'function') window.updateTargetCountBadge();
        if (!window.prepareFormSubmission(event)) return false;
        if (!window.validateAndSubmitFormat(event)) return false;
        window.persistFormatSelection();
        copySettings();
        return true;
    };

    // Confirmation dialogs must re-enter both validators, rather than bypassing submit.
    window.submitStandaloneSetup = function () { form.requestSubmit(); };
})();
