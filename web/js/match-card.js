/**
 * TOURMA - LIST VIEW MATCH CARD ALGORITHM (match-card.js)
 * Handles rendering match list items (#1, #2 match headers + accent bar, 2 separate score boxes, DONE/PENDING status).
 * Unconfirmed and BYE matches cannot be clicked to edit.
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
            var matchNum = data.matchNumber || null;

            var status = (data.status || 'PENDING').toLowerCase();
            var isDone = (status === 'completed' || status === 'done');
            var statusClass = isDone ? 'done' : 'pending';
            var statusLabel = isDone ? 'DONE' : 'PENDING';

            var defaultT1Name = data.team1Placeholder || (matchNum ? ('W #' + (Number(matchNum) * 2 - 1)) : '');
            var defaultT2Name = data.team2Placeholder || (matchNum ? ('W #' + (Number(matchNum) * 2)) : '');

            var t1 = data.team1 || {};
            var t2 = data.team2 || {};

            var resolveStrName = function(val, defaultVal) {
                if (!val) return defaultVal;
                if (typeof val === 'object') return val.name || val.rawName || defaultVal;
                return String(val);
            };

            var t1Name = resolveStrName(t1.name, defaultT1Name);
            var t2Name = resolveStrName(t2.name, defaultT2Name);

            var isT1Placeholder = !t1.name || t1Name.startsWith('W #') || t1Name.startsWith('L #');
            var isT2Placeholder = !t2.name || t2Name.startsWith('W #') || t2Name.startsWith('L #');

            var isT1Bye = (t1Name === 'BYE' || t1.isBye === true);
            var isT2Bye = (t2Name === 'BYE' || t2.isBye === true);
            var hasBye = isT1Bye || isT2Bye;

            var matchHeaderLabel = (matchNum && !hasBye) ? ('#' + matchNum) : '';

            // Only matches with 2 real confirmed teams and NOT BYE are clickable
            var isPlayable = !isT1Placeholder && !isT2Placeholder && !hasBye;

            var hideSeeds = (data.hideSeeds === true);
            var seed1 = (isT1Bye || hideSeeds) ? '' : (t1.seed || '').toString().replace('#', '');
            var seed2 = (isT2Bye || hideSeeds) ? '' : (t2.seed || '').toString().replace('#', '');

            var t1ScoreDisp = (isDone && !hasBye && t1.score !== undefined && t1.score !== null && t1.score !== '') ? t1.score : '';
            var t2ScoreDisp = (isDone && !hasBye && t2.score !== undefined && t2.score !== null && t2.score !== '') ? t2.score : '';

            var isT1Winner = data.winnerId === 'team1' || (isDone && Number(t1.score) > Number(t2.score));
            var isT2Winner = data.winnerId === 'team2' || (isDone && Number(t2.score) > Number(t1.score));

            var bType = (data.bracketType || '').toUpperCase();
            var bTypeClass = (bType === 'LOWER') ? ' lower' : ((bType === 'GRAND_FINAL' || bType === 'GF') ? ' grand-final' : '');

            var card = document.createElement('div');
            card.className = 'match-card-item' + bTypeClass + (!isPlayable ? ' disabled-unconfirmed' : '') + (hasBye ? ' bye-match-item' : '');
            card.dataset.matchId = matchId;
            card.dataset.status = isDone ? 'COMPLETED' : 'SCHEDULED';

            var t1Class = 'match-team-side team-left ' + (isT1Winner ? 'winner ' : '') + (isT1Placeholder ? 'placeholder ' : '') + (isT1Bye ? 'bye-team ' : '');
            var t2Class = 'match-team-side team-right ' + (isT2Winner ? 'winner ' : '') + (isT2Placeholder ? 'placeholder ' : '') + (isT2Bye ? 'bye-team ' : '');

            var seed1Html = (isT1Bye || !seed1) ? '<span class="match-list-seed bye-seed"></span>' : ('<span class="match-list-seed">' + seed1 + '</span>');
            var seed2Html = (isT2Bye || !seed2) ? '<span class="match-list-seed bye-seed"></span>' : ('<span class="match-list-seed">' + seed2 + '</span>');

            var accentClass = 'match-card-accent-bar' + bTypeClass;

            card.innerHTML =
                '<div class="match-card-meta">' +
                    '<div class="' + accentClass + '"' + (hasBye ? ' style="background: #475569; box-shadow: none;"' : '') + '></div>' +
                    '<span class="match-card-id">' + matchHeaderLabel + '</span>' +
                '</div>' +

                '<div class="match-card-versus">' +
                    seed1Html +

                    '<div class="' + t1Class + '" data-team-name="' + t1Name + '">' +
                        '<span class="match-list-name" title="' + t1Name + '">' + t1Name + '</span>' +
                    '</div>' +

                    '<!-- Two Separate Score Boxes -->' +
                    '<div class="match-score-container">' +
                        '<span class="match-score-single-box">' + t1ScoreDisp + '</span>' +
                        '<span class="match-score-dash">-</span>' +
                        '<span class="match-score-single-box">' + t2ScoreDisp + '</span>' +
                    '</div>' +

                    '<div class="' + t2Class + '" data-team-name="' + t2Name + '">' +
                        '<span class="match-list-name" title="' + t2Name + '">' + t2Name + '</span>' +
                    '</div>' +

                    seed2Html +
                '</div>' +

                '<div class="match-card-actions">' +
                    (hasBye ? '' : ('<span class="match-list-status ' + statusClass + '">' + statusLabel + '</span>')) +
                '</div>';

            // Quick Mode Team Side Click Handlers (No path hover in list mode)
            var t1Side = card.querySelector('.match-team-side.team-left');
            var t2Side = card.querySelector('.match-team-side.team-right');

            if (t1Side) {
                t1Side.addEventListener('click', function (e) {
                    if (window.FinalStagePopup && window.FinalStagePopup.isLocked) return;
                    if (window.TourmaQuickMode && isPlayable) {
                        e.stopPropagation();
                        e.preventDefault();
                        if (window.SingleEliminationEngine && typeof window.SingleEliminationEngine.handleQuickWinner === 'function') {
                            window.SingleEliminationEngine.handleQuickWinner(matchId, 1);
                        } else if (window.TourmaDoubleElimination && typeof window.TourmaDoubleElimination.handleQuickWinner === 'function') {
                            window.TourmaDoubleElimination.handleQuickWinner(matchId, 1);
                        }
                    }
                });
            }

            if (t2Side) {
                t2Side.addEventListener('click', function (e) {
                    if (window.FinalStagePopup && window.FinalStagePopup.isLocked) return;
                    if (window.TourmaQuickMode && isPlayable) {
                        e.stopPropagation();
                        e.preventDefault();
                        if (window.SingleEliminationEngine && typeof window.SingleEliminationEngine.handleQuickWinner === 'function') {
                            window.SingleEliminationEngine.handleQuickWinner(matchId, 2);
                        } else if (window.TourmaDoubleElimination && typeof window.TourmaDoubleElimination.handleQuickWinner === 'function') {
                            window.TourmaDoubleElimination.handleQuickWinner(matchId, 2);
                        }
                    }
                });
            }

            // Attach Click Handler to Entire Card (Only if playable and NOT Quick Mode)
            card.addEventListener('click', function () {
                // Check lock directly from localStorage - most reliable source of truth
                var _tid = window.FinalStagePopup ? window.FinalStagePopup.tournamentId : null;
                var _locked = (window.FinalStagePopup && window.FinalStagePopup.isLocked);
                if (!_locked && _tid) {
                    try { _locked = localStorage.getItem('tourma_final_locked_' + _tid) === 'true'; } catch(e) {}
                }
                if (_locked) return;

                if (!isPlayable) {
                    return; // Prevent clicking unconfirmed / BYE matches
                }

                if (window.TourmaQuickMode) {
                    return; // In Quick Mode, only team clicks are active
                }

                if (window.TourmaScoreModal && typeof window.TourmaScoreModal.open === 'function') {
                    window.TourmaScoreModal.open({
                        matchId: matchId,
                        roundName: data.roundName || ('Trận ' + matchHeaderLabel),
                        team1Name: t1Name,
                        team1Seed: seed1,
                        team1Score: t1ScoreDisp,
                        team2Name: t2Name,
                        team2Seed: seed2,
                        team2Score: t2ScoreDisp,
                        winnerId: isT1Winner ? 'team1' : (isT2Winner ? 'team2' : null),
                        status: isDone ? 'COMPLETED' : 'SCHEDULED',
                        allowDraw: (data.allowDraw === true)
                    });
                }
            });

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

            var scores = card.querySelectorAll('.match-score-single-box');
            if (scores.length >= 2) {
                scores[0].innerText = (detail.team1Score !== undefined && detail.team1Score !== null) ? detail.team1Score : '';
                scores[1].innerText = (detail.team2Score !== undefined && detail.team2Score !== null) ? detail.team2Score : '';
            }

            var teamLeft = card.querySelector('.team-left');
            if (teamLeft) teamLeft.classList.toggle('winner', detail.winner === 'team1');

            var teamRight = card.querySelector('.team-right');
            if (teamRight) teamRight.classList.toggle('winner', detail.winner === 'team2');

            var statusBadge = card.querySelector('.match-list-status');
            if (statusBadge) {
                statusBadge.className = 'match-list-status ' + (isDone ? 'done' : 'pending');
                statusBadge.innerText = isDone ? 'DONE' : 'PENDING';
            }
        }
    });

})();
