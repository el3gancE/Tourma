/* Share the compact format editor while retaining Series roster actions. */
(function () {
    'use strict';
    var editor = document.getElementById('configureFormatForm');
    if (!editor) return;

    function write(form, name, value) {
        var input = form.querySelector('input[name="' + name + '"]');
        if (!input) {
            input = document.createElement('input');
            input.type = 'hidden';
            input.name = name;
            form.appendChild(input);
        }
        input.value = value;
    }

    window.prepareSeriesSetup = function (event) {
        var teams = Array.from(document.querySelectorAll('#subtourneyTeamsTable tbody tr.team-table-row'))
            .map(function (row) { return row.querySelector('td:nth-child(2) span').textContent.trim(); });
        localStorage.setItem('tourma_teams_' + window.currentSubTourneyId, JSON.stringify(teams));
        window.targetTeamCount = Number(document.getElementById('targetTeamCount').value) || 0;
        window.tourneyFormat = document.getElementById('selectedFormat').value;
        if (teams.length < 2) { alert('Cần ít nhất 2 đội để tiếp tục.'); return false; }
        if (window.tourneyFormat === 'SWISS_LITE' && teams.length !== 16) {
            alert('Swiss System yêu cầu chính xác 16 đội.');
            return false;
        }
        return window.validateAndSubmitFormat(event);
    };

    document.addEventListener('submit', function (event) {
        var form = event.target;
        // An input named "action" shadows HTMLFormElement.action in these forms.
        var actionUrl = form.getAttribute('action');
        if (!actionUrl || !new URL(actionUrl, window.location.href).pathname.endsWith('/rolling/tournament-teams')) return;
        if (form.id === 'saveOrderAndProceedForm' && !window.prepareSeriesSetup(event)) {
            event.preventDefault();
            return;
        }
        window.persistFormatSelection();
        editor.querySelectorAll('input[type="hidden"][name]').forEach(function (input) {
            if (input.name !== 'id' && input.name !== 'seriesId') write(form, input.name, input.value);
        });
        write(form, 'selectedFormat', document.getElementById('selectedFormat').value);
        write(form, 'selectedTournamentType', document.getElementById('selectedTournamentType').value);
    }, true);

    // Re-enter the full Series validation after the existing RR confirmation dialog.
    window.submitStandaloneSetup = function () { window.proceedToNextStep(); };
})();
