/**
 * TOURMA - LIST VIEW MATCH CARD ALGORITHM (match-card.js)
 * Handles rendering match list items (#1, #2 match headers + accent bar, 2 separate score boxes, DONE/PENDING status).
 */

(function () {
    'use strict';

    window.TourmaMatchCard = {
        /**
         * Render a list view match card HTML element dynamically
         * @param {Object} data - { matchId, matchNumber, status, team1, team2, winnerId }
         * @returns {HTMLElement}
         */
        createCardElement: function (data) {
            if (!data) return null;

            var matchId = data.matchId || data.id || '1';
            var matchNum = data.matchNumber || matchId.toString().replace(/[^0-9]/g, '') || '1';
            var matchHeaderLabel = '#' + matchNum;

            var status = (data.status || 'PENDING').toLowerCase();
            var isDone = status === 'completed' || status === 'done';
            var statusClass = isDone ? 'done' : 'pending';
            var statusLabel = isDone ? 'DONE' : 'PENDING';

            var t1 = data.team1 || { name: 'Đội 1', seed: '1', score: 0 };
            var t2 = data.team2 || { name: 'Đội 2', seed: '2', score: 0 };

            var seed1 = (t1.seed || '1').toString().replace('#', '');
            var seed2 = (t2.seed || '2').toString().replace('#', '');

            var isT1Winner = data.winnerId === 'team1' || (isDone && Number(t1.score) > Number(t2.score));
            var isT2Winner = data.winnerId === 'team2' || (isDone && Number(t2.score) > Number(t1.score));

            var card = document.createElement('div');
            card.className = 'match-card-item';
            card.dataset.matchId = matchId;
            card.dataset.status = isDone ? 'COMPLETED' : 'SCHEDULED';

            card.innerHTML =
                '<div class="match-card-meta">' +
                    '<div class="match-card-accent-bar"></div>' +
                    '<span class="match-card-id">' + matchHeaderLabel + '</span>' +
                '</div>' +

                '<div class="match-card-versus">' +
                    '<!-- Far Left Seed Badge -->' +
                    '<span class="match-list-seed">' + seed1 + '</span>' +

                    '<div class="match-team-side team-left ' + (isT1Winner ? 'winner' : '') + '">' +
                        '<span class="match-list-name" title="' + t1.name + '">' + t1.name + '</span>' +
                    '</div>' +

                    '<!-- Two Separate Score Boxes -->' +
                    '<div class="match-score-container">' +
                        '<span class="match-score-single-box ' + (isT1Winner ? 'winner' : '') + '">' + (t1.score !== undefined ? t1.score : 0) + '</span>' +
                        '<span class="match-score-dash">-</span>' +
                        '<span class="match-score-single-box ' + (isT2Winner ? 'winner' : '') + '">' + (t2.score !== undefined ? t2.score : 0) + '</span>' +
                    '</div>' +

                    '<div class="match-team-side team-right ' + (isT2Winner ? 'winner' : '') + '">' +
                        '<span class="match-list-name" title="' + t2.name + '">' + t2.name + '</span>' +
                    '</div>' +

                    '<!-- Far Right Seed Badge -->' +
                    '<span class="match-list-seed">' + seed2 + '</span>' +
                '</div>' +

                '<div class="match-card-actions">' +
                    '<span class="match-list-status ' + statusClass + '">' + statusLabel + '</span>' +
                    '<button type="button" class="btn-edit-match-list" title="Sửa Tỷ Số">' +
                        '<i class="fa-solid fa-pen-to-square"></i>' +
                    '</button>' +
                '</div>';

            // Attach Click Handler to Edit Button
            var btnEdit = card.querySelector('.btn-edit-match-list');
            if (btnEdit) {
                btnEdit.addEventListener('click', function (e) {
                    e.stopPropagation();
                    if (window.TourmaScoreModal && typeof window.TourmaScoreModal.open === 'function') {
                        window.TourmaScoreModal.open({
                            matchId: matchId,
                            roundName: 'Trận ' + matchHeaderLabel,
                            team1Name: t1.name,
                            team1Seed: seed1,
                            team1Score: t1.score,
                            team2Name: t2.name,
                            team2Seed: seed2,
                            team2Score: t2.score,
                            winnerId: isT1Winner ? 'team1' : (isT2Winner ? 'team2' : null),
                            status: isDone ? 'COMPLETED' : 'SCHEDULED'
                        });
                    }
                });
            }

            return card;
        }
    };

    // Listen for custom match update events to refresh match list item state dynamically
    document.addEventListener('tourmaMatchUpdated', function (e) {
        var detail = e.detail;
        if (!detail || !detail.matchId) return;

        var card = document.querySelector('.match-card-item[data-match-id="' + detail.matchId + '"]');
        if (card) {
            var isDone = detail.status === 'COMPLETED' || detail.status === 'DONE';

            // Update score boxes
            var scores = card.querySelectorAll('.match-score-single-box');
            if (scores.length >= 2) {
                scores[0].innerText = detail.team1Score;
                scores[1].innerText = detail.team2Score;
                scores[0].classList.toggle('winner', detail.winner === 'team1');
                scores[1].classList.toggle('winner', detail.winner === 'team2');
            }

            var teamLeft = card.querySelector('.team-left');
            if (teamLeft) teamLeft.classList.toggle('winner', detail.winner === 'team1');

            var teamRight = card.querySelector('.team-right');
            if (teamRight) teamRight.classList.toggle('winner', detail.winner === 'team2');

            // Update Status Badge
            var statusBadge = card.querySelector('.match-list-status');
            if (statusBadge) {
                statusBadge.className = 'match-list-status ' + (isDone ? 'done' : 'pending');
                statusBadge.innerText = isDone ? 'DONE' : 'PENDING';
            }
        }
    });

})();
