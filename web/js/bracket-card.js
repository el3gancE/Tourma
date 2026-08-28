/**
 * TOURMA - BRACKET CARD ALGORITHM (bracket-card.js)
 * Handles rendering compact bracket node cards (#1, #2 match headers, W #1 / L #2 team placeholders).
 * Unconfirmed/BYE matches are disabled from clicking.
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
            var matchNum = data.matchNumber || null;

            var status = (data.status || 'PENDING').toLowerCase();
            var isDone = (status === 'completed' || status === 'done');
            var statusLabel = isDone ? 'DONE' : 'PENDING';
            var statusClass = isDone ? 'done' : 'pending';

            var defaultT1Name = data.team1Placeholder || (matchNum ? ('W #' + (Number(matchNum) * 2 - 1)) : '');
            var defaultT2Name = data.team2Placeholder || (matchNum ? ('W #' + (Number(matchNum) * 2)) : '');

            var t1 = data.team1 || {};
            var t2 = data.team2 || {};

            var t1Name = t1.name || defaultT1Name;
            var t2Name = t2.name || defaultT2Name;

            var isT1Placeholder = !t1.name || t1Name.startsWith('W #') || t1Name.startsWith('L #');
            var isT2Placeholder = !t2.name || t2Name.startsWith('W #') || t2Name.startsWith('L #');

            var isT1Bye = (t1Name === 'BYE' || t1.isBye === true);
            var isT2Bye = (t2Name === 'BYE' || t2.isBye === true);
            var hasBye = isT1Bye || isT2Bye;

            var matchHeaderLabel = (matchNum && !hasBye) ? ('#' + matchNum) : '';

            // An editable match must have 2 confirmed teams and NO BYE team
            var isPlayable = !isT1Placeholder && !isT2Placeholder && !hasBye;

            var seed1 = isT1Bye ? '' : (t1.seed || '').toString().replace('#', '');
            var seed2 = isT2Bye ? '' : (t2.seed || '').toString().replace('#', '');

            var t1ScoreDisp = (isDone && !hasBye && t1.score !== undefined && t1.score !== null && t1.score !== '') ? t1.score : '';
            var t2ScoreDisp = (isDone && !hasBye && t2.score !== undefined && t2.score !== null && t2.score !== '') ? t2.score : '';

            var isT1Winner = data.winnerId === 'team1' || (isDone && Number(t1.score) > Number(t2.score));
            var isT2Winner = data.winnerId === 'team2' || (isDone && Number(t2.score) > Number(t1.score));
            var isT1Loser = isDone && !hasBye && !isT1Winner && isT2Winner;
            var isT2Loser = isDone && !hasBye && !isT2Winner && isT1Winner;

            var card = document.createElement('div');
            card.className = 'bracket-node-card' + (!isPlayable ? ' disabled-unconfirmed' : '') + (hasBye ? ' bye-node-card' : '');
            card.dataset.matchId = matchId;

            var t1RowClass = 'bracket-team-row ' + (isT1Winner ? 'winner ' : '') + (isT1Loser ? 'loser ' : '') + (isT1Bye ? 'bye-row ' : '');
            var t2RowClass = 'bracket-team-row ' + (isT2Winner ? 'winner ' : '') + (isT2Loser ? 'loser ' : '') + (isT2Bye ? 'bye-row ' : '');

            var t1SeedHtml = (isT1Bye || !seed1) ? '<span class="bracket-seed-badge" style="visibility: hidden;"></span>' : ('<span class="bracket-seed-badge">' + seed1 + '</span>');
            var t2SeedHtml = (isT2Bye || !seed2) ? '<span class="bracket-seed-badge" style="visibility: hidden;"></span>' : ('<span class="bracket-seed-badge">' + seed2 + '</span>');

            card.innerHTML =
                '<div class="bracket-node-header">' +
                    (matchHeaderLabel ? ('<span class="bracket-match-id">' + matchHeaderLabel + '</span>') : '<span></span>') +
                    (hasBye ? '' : ('<span class="bracket-status-badge ' + statusClass + '">' + statusLabel + '</span>')) +
                '</div>' +
                '<div class="bracket-teams-box">' +
                    '<div class="' + t1RowClass + '" data-team-name="' + t1Name + '">' +
                        '<div class="bracket-team-info">' +
                            t1SeedHtml +
                            '<span class="bracket-team-name ' + (isT1Placeholder ? 'placeholder' : '') + '" title="' + t1Name + '">' + t1Name + '</span>' +
                        '</div>' +
                        '<span class="bracket-score-box">' + t1ScoreDisp + '</span>' +
                    '</div>' +
                    '<div class="' + t2RowClass + '" data-team-name="' + t2Name + '">' +
                        '<div class="bracket-team-info">' +
                            t2SeedHtml +
                            '<span class="bracket-team-name ' + (isT2Placeholder ? 'placeholder' : '') + '" title="' + t2Name + '">' + t2Name + '</span>' +
                        '</div>' +
                        '<span class="bracket-score-box">' + t2ScoreDisp + '</span>' +
                    '</div>' +
                '</div>';

            // Attach Quick Mode Row Handlers & Team Path Tracing Hover Events
            var rows = card.querySelectorAll('.bracket-team-row');
            if (rows.length >= 2) {
                // Team 1 Hover & Click
                rows[0].addEventListener('mouseenter', function () {
                    if (window.TourmaPathTracker) window.TourmaPathTracker.highlightTeam(t1Name);
                });
                rows[0].addEventListener('mouseleave', function () {
                    if (window.TourmaPathTracker) window.TourmaPathTracker.clearHighlight();
                });
                rows[0].addEventListener('click', function (e) {
                    if (window.TourmaQuickMode && isPlayable) {
                        e.stopPropagation();
                        e.preventDefault();
                        var parentCol = card.closest('.single-round-column, .de-round-column, .de-column');
                        var rInp = parentCol ? parentCol.querySelector('.round-random-input') : null;
                        var customScore = (rInp && rInp.value && Number(rInp.value) > 0) ? rInp.value.trim() : null;

                        if (window.SingleEliminationEngine && typeof window.SingleEliminationEngine.handleQuickWinner === 'function') {
                            window.SingleEliminationEngine.handleQuickWinner(matchId, 1, customScore);
                        } else if (window.TourmaDoubleElimination && typeof window.TourmaDoubleElimination.handleQuickWinner === 'function') {
                            window.TourmaDoubleElimination.handleQuickWinner(matchId, 1, customScore);
                        }
                    }
                });

                // Team 2 Hover & Click
                rows[1].addEventListener('mouseenter', function () {
                    if (window.TourmaPathTracker) window.TourmaPathTracker.highlightTeam(t2Name);
                });
                rows[1].addEventListener('mouseleave', function () {
                    if (window.TourmaPathTracker) window.TourmaPathTracker.clearHighlight();
                });
                rows[1].addEventListener('click', function (e) {
                    if (window.TourmaQuickMode && isPlayable) {
                        e.stopPropagation();
                        e.preventDefault();
                        var parentCol = card.closest('.single-round-column, .de-round-column, .de-column');
                        var rInp = parentCol ? parentCol.querySelector('.round-random-input') : null;
                        var customScore = (rInp && rInp.value && Number(rInp.value) > 0) ? rInp.value.trim() : null;

                        if (window.SingleEliminationEngine && typeof window.SingleEliminationEngine.handleQuickWinner === 'function') {
                            window.SingleEliminationEngine.handleQuickWinner(matchId, 2, customScore);
                        } else if (window.TourmaDoubleElimination && typeof window.TourmaDoubleElimination.handleQuickWinner === 'function') {
                            window.TourmaDoubleElimination.handleQuickWinner(matchId, 2, customScore);
                        }
                    }
                });
            }

            // Attach Click Event to Launch Score Popup ONLY if match is playable and NOT in Quick Mode
            card.addEventListener('click', function () {
                if (!isPlayable) {
                    return; // Disabled from clicking
                }

                if (window.TourmaQuickMode) {
                    return; // In Quick Mode, only team row clicks are active
                }

                if (window.TourmaScoreModal && typeof window.TourmaScoreModal.open === 'function') {
                    window.TourmaScoreModal.open({
                        matchId: matchId,
                        roundName: 'Trận ' + matchHeaderLabel,
                        team1Name: t1Name,
                        team1Seed: seed1,
                        team1Score: t1ScoreDisp,
                        team2Name: t2Name,
                        team2Seed: seed2,
                        team2Score: t2ScoreDisp,
                        winnerId: isT1Winner ? 'team1' : (isT2Winner ? 'team2' : null),
                        status: isDone ? 'COMPLETED' : 'SCHEDULED',
                        allowDraw: (typeof window.TourmaRoundRobin !== 'undefined')
                    });
                }
            });

            return card;
        },

        createCardElement: function (data) {
            return this.createNodeElement(data);
        }
    };

    // Listen for custom match update events to refresh node states dynamically
    document.addEventListener('tourmaMatchUpdated', function (e) {
        var detail = e.detail;
        if (!detail || !detail.matchId) return;

        var node = document.querySelector('.bracket-node-card[data-match-id="' + detail.matchId + '"]');
        if (node) {
            var scores = node.querySelectorAll('.bracket-score-box');
            if (scores.length >= 2) {
                scores[0].innerText = (detail.team1Score !== undefined && detail.team1Score !== null) ? detail.team1Score : '';
                scores[1].innerText = (detail.team2Score !== undefined && detail.team2Score !== null) ? detail.team2Score : '';
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
