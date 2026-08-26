/**
 * TOURMA - BRACKET CARD ALGORITHM (bracket-card.js)
 * Handles rendering compact bracket node cards (#1, #2 match headers, W #1 / L #2 team placeholders).
 */

(function () {
    'use strict';

    window.TourmaBracketCard = {
        /**
         * Render a compact bracket node card HTML element dynamically
         * @param {Object} data - { matchId, matchNumber, roundName, status, team1, team2, winnerId }
         * @returns {HTMLElement}
         */
        createNodeElement: function (data) {
            if (!data) return null;

            var matchId = data.matchId || data.id || '1';
            // Extract pure match index number, e.g. "M1" or "Tứ Kết 1" -> "#1"
            var matchNum = data.matchNumber || matchId.toString().replace(/[^0-9]/g, '') || '1';
            var matchHeaderLabel = '#' + matchNum;

            var status = (data.status || 'PENDING').toLowerCase();
            var statusLabel = (status === 'completed' || status === 'done') ? 'DONE' : 'PENDING';
            var statusClass = (status === 'completed' || status === 'done') ? 'done' : 'pending';

            // Placeholder logic: if team name is missing or unconfirmed
            var defaultT1Name = data.team1Placeholder || ('W #' + (Number(matchNum) * 2 - 1));
            var defaultT2Name = data.team2Placeholder || ('W #' + (Number(matchNum) * 2));

            var t1 = data.team1 || {};
            var t2 = data.team2 || {};

            var t1Name = t1.name || defaultT1Name;
            var t2Name = t2.name || defaultT2Name;

            var isT1Placeholder = !t1.name || t1Name.startsWith('W #') || t1Name.startsWith('L #');
            var isT2Placeholder = !t2.name || t2Name.startsWith('W #') || t2Name.startsWith('L #');

            var seed1 = (t1.seed || '1').toString().replace('#', '');
            var seed2 = (t2.seed || '2').toString().replace('#', '');

            var isT1Winner = data.winnerId === 'team1' || (statusLabel === 'DONE' && Number(t1.score) > Number(t2.score));
            var isT2Winner = data.winnerId === 'team2' || (statusLabel === 'DONE' && Number(t2.score) > Number(t1.score));

            var card = document.createElement('div');
            card.className = 'bracket-node-card';
            card.dataset.matchId = matchId;

            card.innerHTML =
                '<div class="bracket-connector-in"></div>' +
                '<div class="bracket-node-header">' +
                    '<span class="bracket-match-id">' + matchHeaderLabel + '</span>' +
                    '<span class="bracket-status-badge ' + statusClass + '">' + statusLabel + '</span>' +
                '</div>' +
                '<div class="bracket-teams-box">' +
                    '<div class="bracket-team-row ' + (isT1Winner ? 'winner' : '') + '">' +
                        '<div class="bracket-team-info">' +
                            '<span class="bracket-seed-badge">' + seed1 + '</span>' +
                            '<span class="bracket-team-name ' + (isT1Placeholder ? 'placeholder' : '') + '" title="' + t1Name + '">' + t1Name + '</span>' +
                        '</div>' +
                        '<span class="bracket-score-box">' + (t1.score !== undefined ? t1.score : 0) + '</span>' +
                    '</div>' +
                    '<div class="bracket-team-row ' + (isT2Winner ? 'winner' : '') + '">' +
                        '<div class="bracket-team-info">' +
                            '<span class="bracket-seed-badge">' + seed2 + '</span>' +
                            '<span class="bracket-team-name ' + (isT2Placeholder ? 'placeholder' : '') + '" title="' + t2Name + '">' + t2Name + '</span>' +
                        '</div>' +
                        '<span class="bracket-score-box">' + (t2.score !== undefined ? t2.score : 0) + '</span>' +
                    '</div>' +
                '</div>' +
                '<div class="bracket-connector-out"></div>';

            // Attach Click Event to Launch Score Popup
            card.addEventListener('click', function () {
                if (window.TourmaScoreModal && typeof window.TourmaScoreModal.open === 'function') {
                    window.TourmaScoreModal.open({
                        matchId: matchId,
                        roundName: 'Trận ' + matchHeaderLabel,
                        team1Name: t1Name,
                        team1Seed: seed1,
                        team1Score: t1.score,
                        team2Name: t2Name,
                        team2Seed: seed2,
                        team2Score: t2.score,
                        winnerId: isT1Winner ? 'team1' : (isT2Winner ? 'team2' : null),
                        status: data.status || 'COMPLETED'
                    });
                }
            });

            return card;
        }
    };

    // Listen for custom match update events to refresh node states dynamically
    document.addEventListener('tourmaMatchUpdated', function (e) {
        var detail = e.detail;
        if (!detail || !detail.matchId) return;

        var node = document.querySelector('.bracket-node-card[data-match-id="' + detail.matchId + '"]');
        if (node) {
            // Update node scores & status visually
            var scores = node.querySelectorAll('.bracket-score-box');
            if (scores.length >= 2) {
                scores[0].innerText = detail.team1Score;
                scores[1].innerText = detail.team2Score;
            }

            var rows = node.querySelectorAll('.bracket-team-row');
            if (rows.length >= 2) {
                rows[0].classList.toggle('winner', detail.winner === 'team1');
                rows[1].classList.toggle('winner', detail.winner === 'team2');
            }

            var badge = node.querySelector('.bracket-status-badge');
            if (badge) {
                var isDone = detail.status === 'COMPLETED' || detail.status === 'DONE';
                badge.className = 'bracket-status-badge ' + (isDone ? 'done' : 'pending');
                badge.innerText = isDone ? 'DONE' : 'PENDING';
            }
        }
    });

})();
