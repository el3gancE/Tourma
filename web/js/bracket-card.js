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
            var t1 = data.team1 || {};
            var t2 = data.team2 || {};

            var rawT1Name = (t1.name !== undefined && t1.name !== null && t1.name !== '') ? t1.name : (t1.rawName || '');
            var rawT2Name = (t2.name !== undefined && t2.name !== null && t2.name !== '') ? t2.name : (t2.rawName || '');

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

            // An editable match must have 2 confirmed teams, NO BYE, and if reset match, must be unlocked
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
                                return ''; // Hide all seeds if input is empty or 0
                            }
                            var maxVisible = parseInt(rawVisible, 10);
                            if (isNaN(maxVisible) || maxVisible <= 0) {
                                return ''; // Hide all seeds
                            }
                            if (sNum > maxVisible) {
                                return ''; // Hide seeds with number > maxVisible
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
            var isT1Loser = isDone && !hasBye && !isT1Winner && isT2Winner;
            var isT2Loser = isDone && !hasBye && !isT2Winner && isT1Winner;

            var hideByeSlot = data.hideByeSlot === true;

            // Play-In BYE: both teams auto-advance, render special card
            if (data.isPlayInBye === true) {
                var byeCard = document.createElement('div');
                byeCard.className = 'bracket-node-card play-in-bye-card';
                byeCard.dataset.matchId = matchId;
                byeCard.setAttribute('data-match-id', String(matchId));
                byeCard.innerHTML =
                    '<div class="bracket-node-header">' +
                        '<span class="bracket-match-id" style="color:#2dd4bf;">AUTO</span>' +
                        '<span class="bracket-status-badge" style="background:rgba(45,212,191,0.2);color:#2dd4bf;border-color:rgba(45,212,191,0.4);">BYE</span>' +
                    '</div>' +
                    '<div class="bracket-teams-box">' +
                        '<div class="bracket-team-row winner" data-team-name="' + t1Name + '">' +
                            '<div class="bracket-team-info">' +
                                '<span class="bracket-seed-badge"' + (seed1 ? ' style="background:rgba(45,212,191,0.25);color:#2dd4bf;"' : '') + '>' + (seed1 || '') + '</span>' +
                                '<span class="bracket-team-name" title="' + t1Name + '">' + t1Name + '</span>' +
                            '</div>' +
                            '<span class="bracket-score-box" style="color:#2dd4bf;" title="Tự động đi tiếp"><i class="fa-solid fa-forward-fast" style="font-size:0.75rem;"></i></span>' +
                        '</div>' +
                        '<div class="bracket-team-row winner" data-team-name="' + t2Name + '">' +
                            '<div class="bracket-team-info">' +
                                '<span class="bracket-seed-badge"' + (seed2 ? ' style="background:rgba(45,212,191,0.25);color:#2dd4bf;"' : '') + '>' + (seed2 || '') + '</span>' +
                                '<span class="bracket-team-name" title="' + t2Name + '">' + t2Name + '</span>' +
                            '</div>' +
                            '<span class="bracket-score-box" style="color:#2dd4bf;" title="Tự động đi tiếp"><i class="fa-solid fa-forward-fast" style="font-size:0.75rem;"></i></span>' +
                        '</div>' +
                    '</div>';
                return byeCard;
            }

            var themeClass = '';
            var bType = (data.bracketType || '').toUpperCase();
            if (bType === 'GRAND_FINAL' || bType === 'GF' || data.isGrandFinal || data.themeColor === 'gold' || data.isGold) {
                themeClass = ' swiss-card-gold';
            } else if (data.themeColor === 'green' || data.isGreen) {
                themeClass = ' swiss-card-green';
            } else if (data.themeColor === 'red' || data.isRed || bType === 'LOWER') {
                themeClass = ' lower-bracket-card';
            } else if (data.themeColor === 'mint' || data.isMint) {
                themeClass = ' swiss-card-mint';
            }

            var card = document.createElement('div');
            card.className = 'bracket-node-card' + themeClass + (!isPlayable ? ' disabled-unconfirmed' : '') + (hasBye ? ' bye-node-card' : '') + (hideByeSlot ? ' bye-empty-slot' : '');
            card.dataset.matchId = matchId;
            card.setAttribute('data-match-id', String(matchId));
            card.id = 'bracket-match-' + matchId;


            var t1RowClass = 'bracket-team-row ' + (isT1Winner ? 'winner ' : '') + (isT1Loser ? 'loser ' : '') + (isT1Bye ? 'bye-row ' : '');
            var t2RowClass = 'bracket-team-row ' + (isT2Winner ? 'winner ' : '') + (isT2Loser ? 'loser ' : '') + (isT2Bye ? 'bye-row ' : '');

            var t1SeedHtml = isT1Placeholder ? '' : 
                             (isT1Bye ? '<span class="bracket-seed-badge" style="visibility: hidden;"></span>' : 
                             ('<span class="bracket-seed-badge">' + (seed1 || '') + '</span>'));
            var t2SeedHtml = isT2Placeholder ? '' : 
                             (isT2Bye ? '<span class="bracket-seed-badge" style="visibility: hidden;"></span>' : 
                             ('<span class="bracket-seed-badge">' + (seed2 || '') + '</span>'));

            var matchHeaderContent = matchHeaderLabel ? 
                ('<span class="bracket-match-id">' + matchHeaderLabel + '</span>') : 
                (hasBye ? '<span class="bracket-match-id" style="color: #64748b; font-style: italic; font-size: 0.65rem;">BYE</span>' : '<span class="bracket-match-id">&nbsp;</span>');

            var statusBadgeContent = hasBye ? 
                '<span class="bracket-status-badge" style="visibility: hidden;">BYE</span>' : 
                ('<span class="bracket-status-badge ' + statusClass + '">' + statusLabel + '</span>');

            card.innerHTML =
                '<div class="bracket-node-header">' +
                    matchHeaderContent +
                    statusBadgeContent +
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
                    if (checkCardLocked()) return;
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
                        } else if (window.TourmaSwiss && typeof window.TourmaSwiss.handleQuickWinner === 'function') {
                            window.TourmaSwiss.handleQuickWinner(matchId, 1, customScore);
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
                    if (checkCardLocked()) return;
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
                        } else if (window.TourmaSwiss && typeof window.TourmaSwiss.handleQuickWinner === 'function') {
                            window.TourmaSwiss.handleQuickWinner(matchId, 2, customScore);
                        }
                    }
                });
            }

            // Attach Click Event to Launch Score Popup ONLY if match is playable and NOT in Quick Mode
            card.addEventListener('click', function () {
                if (checkCardLocked()) return;

                if (!isPlayable) {
                    return; // Disabled from clicking
                }

                if (window.TourmaQuickMode) {
                    return; // In Quick Mode, only team row clicks are active
                }

                if (window.TourmaScoreModal && typeof window.TourmaScoreModal.open === 'function') {
                    window.TourmaScoreModal.open({
                        matchId: matchId,
                        tournamentId: (data && (data.tournamentId || data.tourneyId)) || null,
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
