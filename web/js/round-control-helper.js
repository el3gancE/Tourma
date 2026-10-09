/**
 * ============================================================================
 * TOURMA - UNIFIED ROUND CONTROLS & BATCH RANDOM HELPER (round-control-helper.js)
 * Reusable for Single Elimination (SE), Double Elimination (DE), and Swiss System.
 * Handles:
 * 1. Clean empty winning score inputs (NO pre-filled numbers, NO placeholder)
 * 2. High-speed batch random score generation (with bracketType: UPPER, LOWER, GRAND_FINAL support)
 * 3. Individual round reset with cascading downstream clearing
 * 4. Custom score persistence & bi-directional sync across Bracket & List views
 * 5. Direct Event Listeners + Global Bulletproof Event Delegation
 * ============================================================================
 */
(function (window) {
    'use strict';

    var TourmaRoundControls = {
        /**
         * In-memory cache of user-typed custom scores keyed by [bracketType_]roundNumber
         */
        customScores: {},

        /**
         * Retrieve persisted custom score for a round from cache or localStorage
         */
        getCustomScoreForRound: function (roundNumber, tid, bracketType) {
            var rNum = String(roundNumber || 1);
            var bType = bracketType ? String(bracketType).toUpperCase().trim() : '';
            var rKey = (bType ? bType + '_' : '') + rNum;

            if (this.customScores && this.customScores[rKey] !== undefined && this.customScores[rKey] !== null && this.customScores[rKey] !== '') {
                return this.customScores[rKey];
            }
            if (this.customScores && this.customScores[rNum] !== undefined && this.customScores[rNum] !== null && this.customScores[rNum] !== '') {
                return this.customScores[rNum];
            }

            var targetTid = tid || (window.SingleEliminationEngine ? window.SingleEliminationEngine.tournamentId : null) || (window.TourmaDoubleElimination ? window.TourmaDoubleElimination.tournamentId : null) || window.TourmaTournamentId || 'demo';
            try {
                var stored = JSON.parse(localStorage.getItem('tourma_custom_scores_' + targetTid) || '{}');
                if (stored && stored[rKey] !== undefined && stored[rKey] !== null && stored[rKey] !== '') {
                    this.customScores[rKey] = stored[rKey];
                    return stored[rKey];
                }
                if (stored && stored[rNum] !== undefined && stored[rNum] !== null && stored[rNum] !== '') {
                    this.customScores[rNum] = stored[rNum];
                    return stored[rNum];
                }
            } catch (e) { }
            return '';
        },

        /**
         * Save custom score for a round to in-memory cache and localStorage
         */
        saveCustomScoreForRound: function (roundNumber, val, tid, bracketType) {
            var rNum = String(roundNumber || 1);
            var bType = bracketType ? String(bracketType).toUpperCase().trim() : '';
            var rKey = (bType ? bType + '_' : '') + rNum;

            this.customScores[rKey] = val;
            this.customScores[rNum] = val;

            var targetTid = tid || (window.SingleEliminationEngine ? window.SingleEliminationEngine.tournamentId : null) || (window.TourmaDoubleElimination ? window.TourmaDoubleElimination.tournamentId : null) || window.TourmaTournamentId || 'demo';
            try {
                var stored = JSON.parse(localStorage.getItem('tourma_custom_scores_' + targetTid) || '{}');
                stored[rKey] = val;
                stored[rNum] = val;
                localStorage.setItem('tourma_custom_scores_' + targetTid, JSON.stringify(stored));
            } catch (e) { }
        },

        /**
         * Resolve active tournament engine safely
         */
        resolveEngine: function (engine) {
            if (engine && typeof engine.render === 'function') return engine;
            if (window.GSLEngine && typeof window.GSLEngine.render === 'function' && window.GSLEngine.tournamentId) return window.GSLEngine;
            if (window.TourmaGSL && typeof window.TourmaGSL.render === 'function' && window.TourmaGSL.tournamentId) return window.TourmaGSL;
            if (window.DoubleEliminationEngine && typeof window.DoubleEliminationEngine.render === 'function' && window.DoubleEliminationEngine.tournamentId) return window.DoubleEliminationEngine;
            if (window.TourmaDoubleElimination && typeof window.TourmaDoubleElimination.render === 'function' && window.TourmaDoubleElimination.tournamentId) return window.TourmaDoubleElimination;
            if (window.SingleEliminationEngine && typeof window.SingleEliminationEngine.render === 'function' && window.SingleEliminationEngine.tournamentId) return window.SingleEliminationEngine;
            if (window.TourmaSingleElimination && typeof window.TourmaSingleElimination.render === 'function' && window.TourmaSingleElimination.tournamentId) return window.TourmaSingleElimination;
            if (window.TourmaSwiss && typeof window.TourmaSwiss.render === 'function' && window.TourmaSwiss.tournamentId) return window.TourmaSwiss;
            if (window.TourmaRoundRobin && typeof window.TourmaRoundRobin.render === 'function' && window.TourmaRoundRobin.tournamentId) return window.TourmaRoundRobin;
            if (window.TourmaGroupStage && typeof window.TourmaGroupStage.render === 'function' && window.TourmaGroupStage.tournamentId) return window.TourmaGroupStage;
            if (window.TourmaFFA && typeof window.TourmaFFA.render === 'function' && window.TourmaFFA.tournamentId) return window.TourmaFFA;
            if (window.GSLEngine && typeof window.GSLEngine.render === 'function') return window.GSLEngine;
            if (window.TourmaGSL && typeof window.TourmaGSL.render === 'function') return window.TourmaGSL;
            if (window.SingleEliminationEngine && typeof window.SingleEliminationEngine.render === 'function') return window.SingleEliminationEngine;
            if (window.TourmaDoubleElimination && typeof window.TourmaDoubleElimination.render === 'function') return window.TourmaDoubleElimination;
            if (window.DoubleEliminationEngine && typeof window.DoubleEliminationEngine.render === 'function') return window.DoubleEliminationEngine;
            if (window.TourmaSwiss && typeof window.TourmaSwiss.render === 'function') return window.TourmaSwiss;
            return null;
        },

        /**
         * Generate HTML string for Bracket View Round Header Controls
         */
        renderHeaderControlsHtml: function (roundNumber, roundTitle, bracketType, groupId) {
            var rNum = roundNumber || 1;
            var rTitle = roundTitle || ('Vòng ' + rNum);
            var bType = bracketType ? String(bracketType).toUpperCase().trim() : '';
            var gId = (groupId !== undefined && groupId !== null) ? String(groupId).trim() : '';
            var savedVal = this.getCustomScoreForRound(rNum, null, bType);
            var idSuffix = (gId ? gId.replace(/[^a-zA-Z0-9]/g, '_').toLowerCase() + '_' : '') + (bType ? bType.toLowerCase() + '_' : '') + rNum;
            var gAttr = gId ? (' data-group-id="' + gId + '"') : '';

            return '<div class="round-header-random-controls" data-round="' + rNum + '"' + (bType ? ' data-bracket-type="' + bType + '"' : '') + gAttr + '>'
                + '<input type="number" id="round_random_score_bracket_' + idSuffix + '" name="round_random_score_bracket_' + idSuffix + '" class="round-random-input" data-round="' + rNum + '"' + (bType ? ' data-bracket-type="' + bType + '"' : '') + gAttr + ' min="1" max="99" value="' + savedVal + '" autocomplete="off" title="Nhập điểm thắng tùy chỉnh" />'
                + '<button type="button" class="btn-round-random" data-round="' + rNum + '"' + (bType ? ' data-bracket-type="' + bType + '"' : '') + gAttr + ' title="Tạo tỉ số ngẫu nhiên cho ' + rTitle + '">'
                + '<i class="fa-solid fa-dice"></i> Random'
                + '</button>'
                + '<button type="button" class="btn-round-reset" data-round="' + rNum + '"' + (bType ? ' data-bracket-type="' + bType + '"' : '') + gAttr + ' title="Đặt lại kết quả ' + rTitle + '">'
                + '<i class="fa-solid fa-rotate-right"></i>'
                + '</button>'
                + '</div>';
        },

        /**
         * Generate HTML string for List View Round Header Controls
         */
        renderListHeaderControlsHtml: function (roundNumber, roundTitle, bracketType, groupId) {
            var rNum = roundNumber || 1;
            var rTitle = roundTitle || ('Vòng ' + rNum);
            var bType = bracketType ? String(bracketType).toUpperCase().trim() : '';
            var gId = (groupId !== undefined && groupId !== null) ? String(groupId).trim() : '';
            var savedVal = this.getCustomScoreForRound(rNum, null, bType);
            var idSuffix = (gId ? gId.replace(/[^a-zA-Z0-9]/g, '_').toLowerCase() + '_' : '') + (bType ? bType.toLowerCase() + '_' : '') + rNum;
            var gAttr = gId ? (' data-group-id="' + gId + '"') : '';

            return '<div class="list-round-actions" data-round="' + rNum + '"' + (bType ? ' data-bracket-type="' + bType + '"' : '') + gAttr + '>'
                + '<input type="number" id="round_random_score_list_' + idSuffix + '" name="round_random_score_list_' + idSuffix + '" class="round-random-input" data-round="' + rNum + '"' + (bType ? ' data-bracket-type="' + bType + '"' : '') + gAttr + ' min="1" max="99" value="' + savedVal + '" autocomplete="off" title="Nhập điểm thắng tùy chỉnh" />'
                + '<button type="button" class="btn-random-round" data-round="' + rNum + '"' + (bType ? ' data-bracket-type="' + bType + '"' : '') + gAttr + ' title="Tạo tỉ số ngẫu nhiên cho ' + rTitle + '">'
                + '<i class="fa-solid fa-dice"></i> Random ' + rTitle
                + '</button>'
                + '<button type="button" class="btn-reset-round" data-round="' + rNum + '"' + (bType ? ' data-bracket-type="' + bType + '"' : '') + gAttr + ' title="Đặt lại kết quả ' + rTitle + '">'
                + '<i class="fa-solid fa-rotate-right"></i> Reset ' + rTitle
                + '</button>'
                + '</div>';
        },

        /**
         * Extract custom win score from the nearest input or round input
         */
        getCustomWinScore: function (cardOrElement, fallback) {
            var def = (fallback !== undefined && fallback !== null) ? fallback : null;
            if (!cardOrElement) return def;

            var parentContainer = cardOrElement.closest ? cardOrElement.closest('.single-round-column, .de-round-column, .de-column, .swiss-round-column, .list-round-section, [data-round]') : null;
            var rInp = parentContainer ? parentContainer.querySelector('.round-random-input') : null;
            if (rInp && rInp.value && parseInt(rInp.value, 10) > 0) {
                return parseInt(rInp.value, 10);
            }

            var rNumAttr = parentContainer ? parentContainer.getAttribute('data-round') : null;
            var bTypeAttr = parentContainer ? parentContainer.getAttribute('data-bracket-type') : null;
            if (rNumAttr) {
                var val = this.getCustomScoreForRound(rNumAttr, null, bTypeAttr);
                if (val && parseInt(val, 10) > 0) return parseInt(val, 10);
            }

            return def;
        },

        /**
         * Unified Realistic Score Generator for predetermined winner (Quick Mode / 1-Click Winner)
         * - If customScore is specified (> 0), winner gets customScore.
         * - If customScore is blank / null, winner gets realistic score: 75% for [2, 5], 25% for [6, 9].
         * - Loser score is strictly random from 0 to (winScore - 1).
         */
        generateQuickWinnerScore: function (winnerSlotNum, customScore) {
            var isT1 = (winnerSlotNum === 1 || winnerSlotNum === '1' || winnerSlotNum === 'team1');
            var parsed = null;
            if (customScore !== undefined && customScore !== null && String(customScore).trim() !== '') {
                var num = parseInt(String(customScore).trim(), 10);
                if (!isNaN(num) && num > 0) parsed = num;
            }

            var winScore;
            if (parsed !== null) {
                winScore = parsed;
            } else {
                winScore = (Math.random() < 0.75) ? (Math.floor(Math.random() * 4) + 2) : (Math.floor(Math.random() * 4) + 6);
            }

            var loseScore = (winScore > 0) ? Math.floor(Math.random() * winScore) : 0;

            return {
                score1: isT1 ? winScore : loseScore,
                score2: isT1 ? loseScore : winScore,
                winner: isT1 ? 'team1' : 'team2',
                isT1Winner: isT1
            };
        },

        isLocked: function (engine, shouldPrompt) {
            var activeEngine = this.resolveEngine(engine);
            var tid = (activeEngine && activeEngine.tournamentId) ? activeEngine.tournamentId : (window.TourmaTournamentId || 'demo');
            if (window.TourmaScoreModal && typeof window.TourmaScoreModal.isLocked === 'function') {
                return window.TourmaScoreModal.isLocked(tid);
            }
            if (window.FinalStagePopup && window.FinalStagePopup.isLocked) {
                if (shouldPrompt && typeof window.FinalStagePopup.promptUnlock === 'function') {
                    window.FinalStagePopup.promptUnlock();
                }
                return true;
            }
            if (tid) {
                try {
                    if (localStorage.getItem('tourma_final_locked_' + tid) === 'true') {
                        return true;
                    }
                } catch (e) { }
            }
            return false;
        },

        /**
         * Centralized Quick Winner Handler for ALL tournament engines
         */
        handleQuickWinner: function (engine, matchId, winnerSlotNum, customScore) {
            if (this.isLocked(engine, true)) return;
            var activeEngine = this.resolveEngine(engine);
            if (!activeEngine) return;

            var m = (typeof activeEngine.findMatch === 'function') ? activeEngine.findMatch(matchId) : ((activeEngine.matchesMap) ? activeEngine.matchesMap[matchId] : null);
            if (!m) return;

            var t1Name = (m.team1 && m.team1.name) ? m.team1.name : '';
            var t2Name = (m.team2 && m.team2.name) ? m.team2.name : '';
            var isP1 = (typeof activeEngine.isPlaceholder === 'function') ? activeEngine.isPlaceholder(t1Name) : (!t1Name || t1Name === 'TBD' || t1Name === 'BYE');
            var isP2 = (typeof activeEngine.isPlaceholder === 'function') ? activeEngine.isPlaceholder(t2Name) : (!t2Name || t2Name === 'TBD' || t2Name === 'BYE');
            if (isP1 || isP2 || m.isBye) return;

            var scoreRes = this.generateQuickWinnerScore(winnerSlotNum, customScore);

            if (typeof activeEngine.saveMatchScore === 'function') {
                activeEngine.saveMatchScore(m.matchId || matchId, scoreRes.score1, scoreRes.score2, null, null, scoreRes.winner);
            }
        },

        /**
         * Execute high-speed deadlock-free batch random for a single round
         */
        getContextPath: function (engine) {
            var activeEngine = this.resolveEngine(engine);
            if (activeEngine && activeEngine.contextPath && typeof activeEngine.contextPath === 'string' && activeEngine.contextPath.trim().length > 0) {
                return activeEngine.contextPath.trim();
            }
            if (window.TourmaContextPath && typeof window.TourmaContextPath === 'string' && window.TourmaContextPath.trim().length > 0) {
                return window.TourmaContextPath.trim();
            }
            var pathname = window.location.pathname || '';
            if (pathname.indexOf('/Tourma') === 0) return '/Tourma';
            var secondSlash = pathname.indexOf('/', 1);
            if (secondSlash > 0) return pathname.substring(0, secondSlash);
            return '';
        },

        /**
         * Execute batch random for a single round
         */
        executeRandomRound: function (engine, roundNumber, bracketType, groupId) {
            if (this.isLocked(engine, true)) return;
            var activeEngine = this.resolveEngine(engine);
            var self = this;
            var contextPath = this.getContextPath(activeEngine);
            var tid = (activeEngine && activeEngine.tournamentId) ? activeEngine.tournamentId : (window.TourmaTournamentId || 'demo');
            var stage = (activeEngine && activeEngine.currentStage) ? activeEngine.currentStage : 1;
            var bType = bracketType ? String(bracketType).toUpperCase().trim() : '';
            var gId = (groupId !== undefined && groupId !== null) ? String(groupId).trim() : '';

            // Look up target score from matching inputs in DOM or cache
            var targetScore = null;
            var selector = bType
                ? ('.round-random-input[data-round="' + roundNumber + '"][data-bracket-type="' + bType + '"]' + (gId ? '[data-group-id="' + gId + '"]' : '') + ', [data-round="' + roundNumber + '"][data-bracket-type="' + bType + '"]' + (gId ? '[data-group-id="' + gId + '"]' : '') + ' .round-random-input')
                : ('.round-random-input[data-round="' + roundNumber + '"]' + (gId ? '[data-group-id="' + gId + '"]' : '') + ', [data-round="' + roundNumber + '"]' + (gId ? '[data-group-id="' + gId + '"]' : '') + ' .round-random-input');
            var inputs = document.querySelectorAll(selector);
            for (var i = 0; i < inputs.length; i++) {
                if (inputs[i].value && parseInt(inputs[i].value, 10) > 0) {
                    targetScore = parseInt(inputs[i].value, 10);
                    self.saveCustomScoreForRound(roundNumber, targetScore, tid, bType);
                    break;
                }
            }

            if (!targetScore) {
                var cached = self.getCustomScoreForRound(roundNumber, tid, bType);
                if (cached && parseInt(cached, 10) > 0) targetScore = parseInt(cached, 10);
            }

            var payload = {
                action: 'randomRound',
                tournamentId: tid,
                stage: stage,
                roundNumber: roundNumber
            };
            if (bType) {
                payload.bracketType = bType;
            }
            if (gId) {
                payload.groupId = gId;
            }
            if (targetScore) {
                payload.targetScore = targetScore;
            }

            // Visual spinner on clicked buttons
            var btnSelector = bType
                ? ('.btn-round-random[data-round="' + roundNumber + '"][data-bracket-type="' + bType + '"]' + (gId ? '[data-group-id="' + gId + '"]' : '') + ', .btn-random-round[data-round="' + roundNumber + '"][data-bracket-type="' + bType + '"]' + (gId ? '[data-group-id="' + gId + '"]' : ''))
                : ('.btn-round-random[data-round="' + roundNumber + '"]' + (gId ? '[data-group-id="' + gId + '"]' : '') + ', .btn-random-round[data-round="' + roundNumber + '"]' + (gId ? '[data-group-id="' + gId + '"]' : ''));
            var btns = document.querySelectorAll(btnSelector);
            btns.forEach(function (b) {
                b.setAttribute('data-original-html', b.innerHTML);
                b.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i>';
            });

            var apiUrl = (contextPath ? contextPath : '') + '/api/tournament-random';
            fetch(apiUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                },
                body: new URLSearchParams(payload).toString()
            })
            .then(function (res) {
                if (!res.ok) throw new Error('HTTP ' + res.status);
                return res.json();
            })
            .then(function (data) {
                // Restore button HTML
                btns.forEach(function (b) {
                    var orig = b.getAttribute('data-original-html');
                    if (orig) b.innerHTML = orig;
                });

                if (data.status === 'success') {
                    if (Array.isArray(data.matchesData) && data.matchesData.length > 0) {
                        if (activeEngine && typeof activeEngine.hydrateBracketModel === 'function') {
                            activeEngine.hydrateBracketModel(data.matchesData);
                        }
                    }
                    if (activeEngine && typeof activeEngine.render === 'function') {
                        activeEngine.render();
                    }
                    if (activeEngine && typeof activeEngine.checkTournamentCompletion === 'function') {
                        activeEngine.checkTournamentCompletion();
                    }
                } else {
                    alert('Lỗi tạo tỉ số ngẫu nhiên: ' + (data.message || ''));
                }
            })
            .catch(function (err) {
                console.error('[TourmaRoundControls] Error randomizing round:', err);
                btns.forEach(function (b) {
                    var orig = b.getAttribute('data-original-html');
                    if (orig) b.innerHTML = orig;
                });
                window.location.reload();
            });
        },

        /**
         * Execute reset for a single round
         */
        executeResetRound: function (engine, roundNumber, bracketType, groupId) {
            if (this.isLocked(engine, true)) return;
            var activeEngine = this.resolveEngine(engine);
            var self = this;
            var contextPath = this.getContextPath(activeEngine);
            var tid = (activeEngine && activeEngine.tournamentId) ? activeEngine.tournamentId : (window.TourmaTournamentId || 'demo');
            var stage = (activeEngine && activeEngine.currentStage) ? activeEngine.currentStage : 1;
            var bType = bracketType ? String(bracketType).toUpperCase().trim() : '';
            var gId = (groupId !== undefined && groupId !== null) ? String(groupId).trim() : '';

            var payload = {
                action: 'resetRound',
                tournamentId: tid,
                stage: stage,
                roundNumber: roundNumber
            };
            if (bType) {
                payload.bracketType = bType;
            }
            if (gId) {
                payload.groupId = gId;
            }

            // Visual spinner on clicked buttons
            var btnSelector = bType
                ? ('.btn-round-reset[data-round="' + roundNumber + '"][data-bracket-type="' + bType + '"]' + (gId ? '[data-group-id="' + gId + '"]' : '') + ', .btn-reset-round[data-round="' + roundNumber + '"][data-bracket-type="' + bType + '"]' + (gId ? '[data-group-id="' + gId + '"]' : ''))
                : ('.btn-round-reset[data-round="' + roundNumber + '"]' + (gId ? '[data-group-id="' + gId + '"]' : '') + ', .btn-reset-round[data-round="' + roundNumber + '"]' + (gId ? '[data-group-id="' + gId + '"]' : ''));
            var btns = document.querySelectorAll(btnSelector);
            btns.forEach(function (b) {
                b.setAttribute('data-original-html', b.innerHTML);
                b.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i>';
            });

            var apiUrl = (contextPath ? contextPath : '') + '/api/tournament-random';
            fetch(apiUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                },
                body: new URLSearchParams(payload).toString()
            })
            .then(function (res) {
                if (!res.ok) throw new Error('HTTP ' + res.status);
                return res.json();
            })
            .then(function (data) {
                // Restore button HTML
                btns.forEach(function (b) {
                    var orig = b.getAttribute('data-original-html');
                    if (orig) b.innerHTML = orig;
                });

                if (data.status === 'success') {
                    if (Array.isArray(data.matchesData) && data.matchesData.length > 0) {
                        if (activeEngine && typeof activeEngine.hydrateBracketModel === 'function') {
                            activeEngine.hydrateBracketModel(data.matchesData);
                        }
                    }
                    if (activeEngine && typeof activeEngine.render === 'function') {
                        activeEngine.render();
                    }
                    if (activeEngine && typeof activeEngine.checkTournamentCompletion === 'function') {
                        activeEngine.checkTournamentCompletion();
                    }
                } else {
                    alert('Lỗi đặt lại vòng đấu: ' + (data.message || ''));
                }
            })
            .catch(function (err) {
                console.error('[TourmaRoundControls] Error resetting round:', err);
                btns.forEach(function (b) {
                    var orig = b.getAttribute('data-original-html');
                    if (orig) b.innerHTML = orig;
                });
                window.location.reload();
            });
        },

        /**
         * Execute batch random for all playable matches across the entire tournament
         */
        executeRandomAll: function (engine) {
            if (this.isLocked(engine, true)) return;
            var activeEngine = this.resolveEngine(engine);
            var self = this;
            var contextPath = this.getContextPath(activeEngine);
            var tid = (activeEngine && activeEngine.tournamentId) ? activeEngine.tournamentId : (window.TourmaTournamentId || 'demo');
            var stage = (activeEngine && activeEngine.currentStage) ? activeEngine.currentStage : 1;

            var payload = {
                action: 'randomAll',
                tournamentId: tid,
                stage: stage
            };

            var apiUrl = (contextPath ? contextPath : '') + '/api/tournament-random';
            fetch(apiUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                },
                body: new URLSearchParams(payload).toString()
            })
            .then(function (res) {
                if (!res.ok) throw new Error('HTTP ' + res.status);
                return res.json();
            })
            .then(function (data) {
                if (data.status === 'success') {
                    if (Array.isArray(data.matchesData) && data.matchesData.length > 0) {
                        if (activeEngine && typeof activeEngine.hydrateBracketModel === 'function') {
                            activeEngine.hydrateBracketModel(data.matchesData);
                        }
                    }
                    if (activeEngine && typeof activeEngine.render === 'function') {
                        activeEngine.render();
                    }
                    if (activeEngine && typeof activeEngine.checkTournamentCompletion === 'function') {
                        activeEngine.checkTournamentCompletion();
                    }
                } else {
                    alert('Lỗi tạo tỉ số ngẫu nhiên: ' + (data.message || ''));
                }
            })
            .catch(function (err) {
                console.error('[TourmaRoundControls] Error randomizing all:', err);
                window.location.reload();
            });
        },

        /**
         * Bind direct events to all Round Controls inside a container
         */
        bindEvents: function (container, engine) {
            if (!container) return;
            var activeEngine = this.resolveEngine(engine);
            var self = this;

            // Direct Click Bindings on Random Buttons
            var randomBtns = container.querySelectorAll('.btn-round-random, .btn-random-round');
            randomBtns.forEach(function (btn) {
                btn.onclick = function (e) {
                    e.preventDefault();
                    e.stopPropagation();
                    var rNum = parseInt(this.getAttribute('data-round'), 10);
                    var bType = this.getAttribute('data-bracket-type') || (this.closest('[data-bracket-type]') ? this.closest('[data-bracket-type]').getAttribute('data-bracket-type') : null);
                    if (!isNaN(rNum)) {
                        self.executeRandomRound(activeEngine, rNum, bType);
                    }
                };
            });

            // Direct Click Bindings on Reset Buttons
            var resetBtns = container.querySelectorAll('.btn-round-reset, .btn-reset-round');
            resetBtns.forEach(function (btn) {
                btn.onclick = function (e) {
                    e.preventDefault();
                    e.stopPropagation();
                    var rNum = parseInt(this.getAttribute('data-round'), 10);
                    var bType = this.getAttribute('data-bracket-type') || (this.closest('[data-bracket-type]') ? this.closest('[data-bracket-type]').getAttribute('data-bracket-type') : null);
                    if (!isNaN(rNum)) {
                        self.executeResetRound(activeEngine, rNum, bType);
                    }
                };
            });

            // Direct Input Bindings on Score Inputs
            var inputs = container.querySelectorAll('.round-random-input');
            inputs.forEach(function (inp) {
                inp.oninput = function () {
                    var rNum = this.getAttribute('data-round');
                    var bType = this.getAttribute('data-bracket-type') || (this.closest('[data-bracket-type]') ? this.closest('[data-bracket-type]').getAttribute('data-bracket-type') : null);
                    var val = this.value;
                    if (rNum) {
                        self.saveCustomScoreForRound(rNum, val, null, bType);
                        var sel = bType 
                            ? ('.round-random-input[data-round="' + rNum + '"][data-bracket-type="' + bType + '"]')
                            : ('.round-random-input[data-round="' + rNum + '"]');
                        var siblings = document.querySelectorAll(sel);
                        siblings.forEach(function (other) {
                            if (other !== inp) {
                                other.value = val;
                            }
                        });
                    }
                };
            });

            if (activeEngine) {
                this.updateButtonsState(activeEngine);
            }
        },

        /**
         * Check and update visual states of Random Round & Reset Round buttons
         */
        updateButtonsState: function (engine) {
            var activeEngine = this.resolveEngine(engine);
            var locked = this.isLocked(activeEngine, false);
            var allBtns = document.querySelectorAll('.btn-round-random, .btn-random-round, .btn-round-reset, .btn-reset-round');
            var scoreInputs = document.querySelectorAll('.round-random-input');

            if (locked) {
                allBtns.forEach(function (btn) {
                    btn.classList.add('disabled');
                    btn.removeAttribute('disabled');
                });
                scoreInputs.forEach(function (inp) {
                    inp.disabled = true;
                });
            } else {
                allBtns.forEach(function (btn) {
                    btn.classList.remove('disabled');
                    btn.removeAttribute('disabled');
                    btn.style.opacity = '';
                    btn.style.cursor = '';
                    btn.style.pointerEvents = '';
                });
                scoreInputs.forEach(function (inp) {
                    inp.disabled = false;
                });
            }
        }
    };

    // ========================================================================
    // GLOBAL BULLETPROOF EVENT DELEGATION
    // Ensures clicks on Random / Reset and typing in score input ALWAYS execute
    // ========================================================================
    document.addEventListener('click', function (e) {
        // Random Round Button Click
        var randomBtn = e.target.closest ? e.target.closest('.btn-round-random, .btn-random-round') : null;
        if (randomBtn) {
            e.preventDefault();
            e.stopPropagation();
            var rNum = parseInt(randomBtn.getAttribute('data-round'), 10);
            var bType = randomBtn.getAttribute('data-bracket-type') || (randomBtn.closest('[data-bracket-type]') ? randomBtn.closest('[data-bracket-type]').getAttribute('data-bracket-type') : null);
            var gId = randomBtn.getAttribute('data-group-id') || (randomBtn.closest('[data-group-id]') ? randomBtn.closest('[data-group-id]').getAttribute('data-group-id') : null);
            if (!isNaN(rNum)) {
                TourmaRoundControls.executeRandomRound(null, rNum, bType, gId);
            }
            return;
        }

        // Reset Round Button Click
        var resetBtn = e.target.closest ? e.target.closest('.btn-round-reset, .btn-reset-round') : null;
        if (resetBtn) {
            e.preventDefault();
            e.stopPropagation();
            var rNum = parseInt(resetBtn.getAttribute('data-round'), 10);
            var bType = resetBtn.getAttribute('data-bracket-type') || (resetBtn.closest('[data-bracket-type]') ? resetBtn.closest('[data-bracket-type]').getAttribute('data-bracket-type') : null);
            var gId = resetBtn.getAttribute('data-group-id') || (resetBtn.closest('[data-group-id]') ? resetBtn.closest('[data-group-id]').getAttribute('data-group-id') : null);
            if (!isNaN(rNum)) {
                TourmaRoundControls.executeResetRound(null, rNum, bType, gId);
            }
            return;
        }
    }, true);

    // Score Input Bi-Directional Sync & LocalStorage persistence across all round headers
    document.addEventListener('input', function (e) {
        if (e.target && e.target.classList && e.target.classList.contains('round-random-input')) {
            var rNum = e.target.getAttribute('data-round');
            var bType = e.target.getAttribute('data-bracket-type') || (e.target.closest('[data-bracket-type]') ? e.target.closest('[data-bracket-type]').getAttribute('data-bracket-type') : null);
            var val = e.target.value;
            if (rNum) {
                TourmaRoundControls.saveCustomScoreForRound(rNum, val, null, bType);
                var sel = bType 
                    ? ('.round-random-input[data-round="' + rNum + '"][data-bracket-type="' + bType + '"]')
                    : ('.round-random-input[data-round="' + rNum + '"]');
                var siblings = document.querySelectorAll(sel);
                siblings.forEach(function (other) {
                    if (other !== e.target) {
                        other.value = val;
                    }
                });
            }
        }
    }, true);

    document.addEventListener('change', function (e) {
        if (e.target && e.target.classList && e.target.classList.contains('round-random-input')) {
            var rNum = e.target.getAttribute('data-round');
            var bType = e.target.getAttribute('data-bracket-type') || (e.target.closest('[data-bracket-type]') ? e.target.closest('[data-bracket-type]').getAttribute('data-bracket-type') : null);
            var val = e.target.value;
            if (rNum) {
                TourmaRoundControls.saveCustomScoreForRound(rNum, val, null, bType);
                var sel = bType 
                    ? ('.round-random-input[data-round="' + rNum + '"][data-bracket-type="' + bType + '"]')
                    : ('.round-random-input[data-round="' + rNum + '"]');
                var siblings = document.querySelectorAll(sel);
                siblings.forEach(function (other) {
                    if (other !== e.target) {
                        other.value = val;
                    }
                });
            }
        }
    }, true);

    // Auto-update button states & populate inputs on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            TourmaRoundControls.updateButtonsState(null);
        });
    } else {
        TourmaRoundControls.updateButtonsState(null);
    }
    window.addEventListener('load', function () {
        TourmaRoundControls.updateButtonsState(null);
    });

    window.TourmaRoundControls = TourmaRoundControls;

})(window);
