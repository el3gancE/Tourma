(function () {
    'use strict';

    var dialog = document.getElementById('tourmaH2h');
    if (!dialog) return;
    var firstName = dialog.querySelector('[data-h2h-first]');
    var secondName = dialog.querySelector('[data-h2h-second]');
    var status = dialog.querySelector('[data-h2h-status]');
    var history = dialog.querySelector('[data-h2h-history]');
    var retry = dialog.querySelector('[data-h2h-retry]');
    var results = dialog.querySelector('[data-h2h-results]');
    var firstWins = dialog.querySelector('[data-h2h-first-wins]');
    var secondWins = dialog.querySelector('[data-h2h-second-wins]');
    var total = dialog.querySelector('[data-h2h-total]');
    var draws = dialog.querySelector('[data-h2h-draws]');
    var activeRequest;
    var selection;

    function render(result) {
        if (!result.matches.length) {
            status.textContent = 'Chưa có trận đối đầu đã hoàn thành trong ' + result.scope + '.';
            return;
        }
        firstWins.textContent = result.firstWins + ' thắng';
        secondWins.textContent = result.secondWins + ' thắng';
        total.textContent = result.matches.length + ' trận';
        draws.textContent = result.draws + ' hòa';
        status.textContent = result.scope === 'Series' ? 'Trong cùng Series' : 'Trong giải này';
        var rows = document.createDocumentFragment();
        result.matches.forEach(function (match) {
            var row = document.createElement('li');
            var details = document.createElement('div');
            details.className = 'h2h-match-details';
            var tournament = document.createElement('span');
            tournament.textContent = match.tournament;
            var round = document.createElement('small');
            round.textContent = match.round;
            details.append(tournament, round);
            var matchup = document.createElement('div');
            matchup.className = 'h2h-matchup';
            var left = document.createElement('span');
            left.textContent = selection.first;
            var score = document.createElement('strong');
            var points = document.createElement('span');
            points.textContent = match.firstScore + ' – ' + match.secondScore;
            score.appendChild(points);
            if (match.firstPenalty != null && match.secondPenalty != null) {
                var penalty = document.createElement('small');
                penalty.textContent = match.firstPenalty + '–' + match.secondPenalty + ' pen';
                score.appendChild(penalty);
            }
            var right = document.createElement('span');
            right.textContent = selection.second;
            var winner = match.winnerPosition === 1 ? left : match.winnerPosition === 2 ? right : null;
            if (winner) {
                winner.classList.add('h2h-winner');
                winner.setAttribute('aria-label', winner.textContent + ' thắng');
            }
            matchup.append(left, score, right);
            row.append(details, matchup);
            rows.appendChild(row);
        });
        history.replaceChildren(rows);
        results.hidden = false;
        history.scrollTop = 0;
    }

    async function load() {
        if (activeRequest) activeRequest.abort();
        var request = new AbortController();
        activeRequest = request;
        history.replaceChildren();
        results.hidden = true;
        firstWins.textContent = secondWins.textContent = draws.textContent = '';
        total.textContent = 'vs';
        retry.hidden = true;
        status.textContent = 'Đang tải lịch sử đối đầu…';
        try {
            var query = new URLSearchParams(selection);
            var response = await fetch(dialog.dataset.endpoint + '?' + query, { signal: request.signal });
            if (!response.ok) throw new Error('H2H unavailable');
            var result = await response.json();
            if (!request.signal.aborted) render(result);
        } catch (error) {
            if (request.signal.aborted) return;
            status.textContent = 'Không tải được lịch sử đối đầu.';
            retry.hidden = false;
        }
    }

    retry.addEventListener('click', load);
    dialog.addEventListener('close', function () {
        if (!dialog.open && activeRequest) activeRequest.abort();
    });

    window.TourmaH2h = {
        addButton: function (card, first, second, tournamentId) {
            var id = tournamentId || new URLSearchParams(window.location.search).get('id');
            if (!id || first.trim().toLowerCase() === second.trim().toLowerCase()) return;
            var button = document.createElement('button');
            button.type = 'button';
            button.className = 'h2h-button h2h-trigger';
            button.textContent = 'H2H';
            button.setAttribute('aria-label', 'Xem đối đầu: ' + first + ' và ' + second);
            button.setAttribute('aria-haspopup', 'dialog');
            button.addEventListener('click', function (event) {
                event.stopPropagation();
                selection = { id: id, first: first, second: second };
                firstName.textContent = first;
                secondName.textContent = second;
                dialog.showModal();
                load();
            });
            var matchLine = card.querySelector('.bracket-node-header, .match-card-meta');
            if (matchLine) {
                matchLine.classList.add('h2h-match-line');
                matchLine.appendChild(button);
            }
        }
    };
})();
