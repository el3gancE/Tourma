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
            var t1 = data.team1 || {};
            var t2 = data.team2 || {};

            var resolveStrName = function(val) {
                if (!val) return '';
                if (typeof val === 'object') return val.name || val.rawName || '';
                return String(val);
            };

            var rawT1Name = resolveStrName(t1.name);
            var rawT2Name = resolveStrName(t2.name);

            var isT1Bye = (rawT1Name === 'BYE' || t1.isBye === true);
            var isT2Bye = (rawT2Name === 'BYE' || t2.isBye === true);
            var hasBye = data.isBye === true || isT1Bye || isT2Bye;

            var matchNum = hasBye ? null : (data.matchNumber || null);

            var defaultT1Name = hasBye ? (isT1Bye ? 'BYE' : '') : (data.team1Placeholder || (matchNum ? ('W #' + (Number(matchNum) * 2 - 1)) : ''));
            var defaultT2Name = hasBye ? (isT2Bye ? 'BYE' : '') : (data.team2Placeholder || (matchNum ? ('W #' + (Number(matchNum) * 2)) : ''));

            var t1Name = rawT1Name ? rawT1Name : defaultT1Name;
            var t2Name = rawT2Name ? rawT2Name : defaultT2Name;

            if (hasBye) {
                if (isT1Bye || t1Name === 'BYE') t1Name = 'BYE';
                if (isT2Bye || t2Name === 'BYE') t2Name = 'BYE';
            }

            var checkIsPlaceholder = function (name) {
                if (name === undefined || name === null) return true;
                var t = String(name).trim();
                if (!t || t === 'BYE' || t === 'TBD' || t === '?') return true;
                if (t.startsWith('W #') || t.startsWith('L #') || t.startsWith('W#') || t.startsWith('L#')) return true;
                if (t.startsWith('Winner ') || t.startsWith('Loser ')) return true;
                if (t === 'Winner UB' || t === 'Winner LB' || t === 'Loser UB' || t === 'Loser LB') return true;
                return false;
            };

            var isT1Placeholder = checkIsPlaceholder(t1Name);
            var isT2Placeholder = checkIsPlaceholder(t2Name);

            var matchHeaderLabel = (matchNum && !hasBye) ? ('#' + matchNum) : '';

            // Only matches with 2 real confirmed teams, NOT BYE, and unlocked reset match are clickable
            var isLockedReset = (data.isResetMatch && !data.isUnlocked);
            var isPlayable = !isT1Placeholder && !isT2Placeholder && !hasBye && !isLockedReset;

            var getDisplaySeed = function (rawSeed, matchData, teamName) {
                var sNum = NaN;
                if (rawSeed !== undefined && rawSeed !== null && rawSeed !== '') {
                    sNum = parseInt(String(rawSeed).replace('#', '').trim(), 10);
                }

                var tid = (matchData && (matchData.tournamentId || matchData.tourneyId)) ||
                          (window.SingleEliminationEngine && window.SingleEliminationEngine.tournamentId) ||
                          (window.TourmaDoubleElimination && window.TourmaDoubleElimination.tournamentId) ||
                          (window.TourmaRoundRobin && window.TourmaRoundRobin.tournamentId) ||
                          (window.TourmaSwissStage && window.TourmaSwissStage.tournamentId) ||
                          (window.TourmaGroupStage && window.TourmaGroupStage.tournamentId) ||
                          (window.TourmaSingleElimination && window.TourmaSingleElimination.tournamentId) ||
                          window.TourmaContextPathTourneyId;
                if (!tid) {
                    try {
                        var params = new URLSearchParams(window.location.search);
                        tid = params.get('id');
                    } catch (e) {}
                }

                // If seed is missing, try looking it up by teamName from memory or localStorage
                if (isNaN(sNum) && teamName && teamName !== 'BYE' && teamName !== 'TBD' && !teamName.startsWith('W #') && !teamName.startsWith('L #')) {
                    try {
                        var teams = (window.SingleEliminationEngine && window.SingleEliminationEngine.teamsList) ||
                                    (window.TourmaDoubleElimination && window.TourmaDoubleElimination.teamsList) ||
                                    (window.TourmaRoundRobin && window.TourmaRoundRobin.teamsList) ||
                                    (window.TourmaSwissStage && window.TourmaSwissStage.teamsList) ||
                                    (window.TourmaGroupStage && window.TourmaGroupStage.teamsList);
                        if ((!teams || !Array.isArray(teams) || teams.length === 0) && tid) {
                            teams = JSON.parse(localStorage.getItem('tourma_teams_' + tid));
                        }
                        if (teams && Array.isArray(teams)) {
                            var cleanName = String(teamName).trim().toLowerCase();
                            for (var ti = 0; ti < teams.length; ti++) {
                                var tm = teams[ti];
                                var tn = (typeof tm === 'object' && tm) ? (tm.name || tm.rawName) : tm;
                                if (tn && String(tn).trim().toLowerCase() === cleanName) {
                                    var ts = (typeof tm === 'object' && tm && tm.seed !== undefined && tm.seed !== null && tm.seed !== '') ? tm.seed : (ti + 1);
                                    sNum = parseInt(String(ts).replace('#', '').trim(), 10);
                                    break;
                                }
                            }
                        }
                    } catch (e) {}
                }

                if (isNaN(sNum)) return '';

                var cfgRaw = null;
                if (tid) {
                    try {
                        cfgRaw = localStorage.getItem('tourma_hide_seed_config_' + tid);
                    } catch (e) {}
                }
                if (!cfgRaw) {
                    try {
                        cfgRaw = localStorage.getItem('tourma_hide_seed_config_demo');
                    } catch (e) {}
                }

                if (cfgRaw) {
                    try {
                        var cfg = (typeof cfgRaw === 'string') ? JSON.parse(cfgRaw) : cfgRaw;
                        if (cfg && cfg.isEnabled) {
                            var rawVisible = (cfg.visibleCount !== undefined && cfg.visibleCount !== null) ? String(cfg.visibleCount).trim() : '';
                            if (rawVisible === '' || rawVisible === '0') {
                                return '';
                            }
                            var maxVisible = parseInt(rawVisible, 10);
                            if (isNaN(maxVisible) || maxVisible <= 0) {
                                return '';
                            }
                            if (sNum > maxVisible) {
                                return '';
                            }
                        }
                    } catch (e) {}
                }
                return String(sNum);
            };

            var seed1 = (isT1Bye || isT1Placeholder) ? '' : getDisplaySeed(t1.seed, data, t1Name);
            var seed2 = (isT2Bye || isT2Placeholder) ? '' : getDisplaySeed(t2.seed, data, t2Name);

            var rawStatus = (data.status || '').toUpperCase();
            var isDone = (rawStatus === 'DONE' || rawStatus === 'COMPLETED' || rawStatus === 'FINISHED' || (data.winnerId !== undefined && data.winnerId !== null && data.winnerId !== ''));
            var statusLabel = isDone ? 'DONE' : ((rawStatus === 'LIVE' || rawStatus === 'IN_PROGRESS' || rawStatus === 'PLAYING') ? 'LIVE' : 'PENDING');
            var statusClass = isDone ? 'done' : ((rawStatus === 'LIVE' || rawStatus === 'IN_PROGRESS' || rawStatus === 'PLAYING') ? 'live' : 'pending');

            var t1ScoreDisp = (isDone && !hasBye && t1.score !== undefined && t1.score !== null && t1.score !== '') ? t1.score : '';
            var t2ScoreDisp = (isDone && !hasBye && t2.score !== undefined && t2.score !== null && t2.score !== '') ? t2.score : '';

            var isT1Winner = data.winnerId === 'team1' || (isDone && Number(t1.score) > Number(t2.score));
            var isT2Winner = data.winnerId === 'team2' || (isDone && Number(t2.score) > Number(t1.score));

            var bType = (data.bracketType || '').toUpperCase();
            var bTypeClass = (bType === 'LOWER') ? ' lower' : ((bType === 'GRAND_FINAL' || bType === 'GF') ? ' grand-final' : '');

            var themeClass = '';
            if (data.themeColor === 'green' || data.isGreen) themeClass = ' swiss-card-green';
            else if (data.themeColor === 'red' || data.isRed) themeClass = ' swiss-card-red';
            else if (data.themeColor === 'gold' || data.isGold) themeClass = ' swiss-card-gold';
            else if (data.themeColor === 'mint' || data.isMint) themeClass = ' swiss-card-mint';

            var card = document.createElement('div');
            card.className = 'match-card-item' + bTypeClass + themeClass + (!isPlayable ? ' disabled-unconfirmed' : '') + (hasBye ? ' bye-match-item' : '');
            card.dataset.matchId = matchId;
            card.dataset.status = isDone ? 'COMPLETED' : 'SCHEDULED';

            var t1Class = 'match-team-side team-left ' + (isT1Winner ? 'winner ' : '') + (isT1Placeholder ? 'placeholder ' : '') + (isT1Bye ? 'bye-team ' : '');
            var t2Class = 'match-team-side team-right ' + (isT2Winner ? 'winner ' : '') + (isT2Placeholder ? 'placeholder ' : '') + (isT2Bye ? 'bye-team ' : '');

            var seed1Html = isT1Placeholder ? '<span class="match-list-seed bye-seed" style="visibility:hidden"></span>' : 
                            (isT1Bye ? '<span class="match-list-seed bye-seed"></span>' : 
                            ('<span class="match-list-seed">' + (seed1 || '') + '</span>'));
            var seed2Html = isT2Placeholder ? '<span class="match-list-seed bye-seed" style="visibility:hidden"></span>' : 
                            (isT2Bye ? '<span class="match-list-seed bye-seed"></span>' : 
                            ('<span class="match-list-seed">' + (seed2 || '') + '</span>'));

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

            var checkCardLocked = function () {
                var tid = (data && (data.tournamentId || data.tourneyId)) || null;
                // Delegate to TourmaScoreModal.isLocked which properly checks multi-stage guard
                if (window.TourmaScoreModal && typeof window.TourmaScoreModal.isLocked === 'function') {
                    return window.TourmaScoreModal.isLocked(tid);
                }
                // Fallback: only check final champion lock
                if (window.FinalStagePopup && window.FinalStagePopup.isLocked) return true;
                var _tid = tid || (window.FinalStagePopup ? window.FinalStagePopup.tournamentId : null);
                if (_tid) {
                    try { if (localStorage.getItem('tourma_final_locked_' + _tid) === 'true') return true; } catch(e) {}
                }
                return false;
            };

            // Quick Mode Team Side Click Handlers (No path hover in list mode)
            var t1Side = card.querySelector('.match-team-side.team-left');
            var t2Side = card.querySelector('.match-team-side.team-right');

            if (t1Side) {
                t1Side.addEventListener('click', function (e) {
                    if (checkCardLocked()) return;
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
                    if (checkCardLocked()) return;
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
                if (checkCardLocked()) return;

                if (!isPlayable) {
                    return; // Prevent clicking unconfirmed / BYE matches
                }

                if (window.TourmaQuickMode) {
                    return; // In Quick Mode, only team clicks are active
                }

                if (window.TourmaScoreModal && typeof window.TourmaScoreModal.open === 'function') {
                    window.TourmaScoreModal.open({
                        matchId: matchId,
                        tournamentId: (data && (data.tournamentId || data.tourneyId)) || null,
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
