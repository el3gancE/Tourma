/**
 * ============================================================================
 * TOURMA - UNIFIED ROUND CONTROLS & BATCH RANDOM HELPER (round-control-helper.js)
 * Reusable for Single Elimination (SE), Double Elimination (DE), and Swiss System.
 * Handles:
 * 1. Clean empty winning score inputs (NO pre-filled numbers, NO placeholder)
 * 2. High-speed batch random score generation
 * 3. Individual round reset with cascading downstream clearing
 * 4. Custom score persistence & bi-directional sync across Bracket & List views
 * 5. Direct Event Listeners + Global Bulletproof Event Delegation
 * ============================================================================
 */
(function (window) {
    'use strict';

    var TourmaRoundControls = {
        /**
         * In-memory cache of user-typed custom scores keyed by roundNumber
         */
        customScores: {},

        /**
         * Resolve active tournament engine safely
         */
        resolveEngine: function (engine) {
            if (engine && typeof engine.render === 'function') return engine;
            if (window.SingleEliminationEngine && typeof window.SingleEliminationEngine.render === 'function') return window.SingleEliminationEngine;
            if (window.TourmaSingleElimination && typeof window.TourmaSingleElimination.render === 'function') return window.TourmaSingleElimination;
            if (window.TourmaDoubleElimination && typeof window.TourmaDoubleElimination.render === 'function') return window.TourmaDoubleElimination;
            if (window.TourmaSwiss && typeof window.TourmaSwiss.render === 'function') return window.TourmaSwiss;
            if (window.TourmaRoundRobin && typeof window.TourmaRoundRobin.render === 'function') return window.TourmaRoundRobin;
            if (window.TourmaGroupStage && typeof window.TourmaGroupStage.render === 'function') return window.TourmaGroupStage;
            return null;
        },

        /**
         * Generate HTML string for Bracket View Round Header Controls
         */
        renderHeaderControlsHtml: function (roundNumber, roundTitle) {
            var rNum = roundNumber || 1;
            var rTitle = roundTitle || ('Vòng ' + rNum);
            var savedVal = (this.customScores && this.customScores[rNum] !== undefined) ? this.customScores[rNum] : '';
            return '<div class="round-header-random-controls" data-round="' + rNum + '">'
                + '<input type="number" id="round_random_score_bracket_' + rNum + '" name="round_random_score_bracket_' + rNum + '" class="round-random-input" data-round="' + rNum + '" min="1" max="99" value="' + savedVal + '" autocomplete="off" title="Nhập điểm thắng tùy chỉnh" />'
                + '<button type="button" class="btn-round-random" data-round="' + rNum + '" title="Tạo tỉ số ngẫu nhiên cho ' + rTitle + '">'
                + '<i class="fa-solid fa-dice"></i> Random'
                + '</button>'
                + '<button type="button" class="btn-round-reset" data-round="' + rNum + '" title="Đặt lại kết quả ' + rTitle + '">'
                + '<i class="fa-solid fa-rotate-right"></i>'
                + '</button>'
                + '</div>';
        },

        /**
         * Generate HTML string for List View Round Header Controls
         */
        renderListHeaderControlsHtml: function (roundNumber, roundTitle) {
            var rNum = roundNumber || 1;
            var savedVal = (this.customScores && this.customScores[rNum] !== undefined) ? this.customScores[rNum] : '';
            return '<div class="list-round-actions" data-round="' + rNum + '">'
                + '<input type="number" id="round_random_score_list_' + rNum + '" name="round_random_score_list_' + rNum + '" class="round-random-input" data-round="' + rNum + '" min="1" max="99" value="' + savedVal + '" autocomplete="off" title="Nhập điểm thắng tùy chỉnh" />'
                + '<button type="button" class="btn-random-round" data-round="' + rNum + '" title="Tạo tỉ số ngẫu nhiên cho Vòng ' + rNum + '">'
                + '<i class="fa-solid fa-dice"></i> Random Vòng ' + rNum
                + '</button>'
                + '<button type="button" class="btn-reset-round" data-round="' + rNum + '" title="Đặt lại kết quả Vòng ' + rNum + '">'
                + '<i class="fa-solid fa-rotate-right"></i> Reset Vòng ' + rNum
                + '</button>'
                + '</div>';
        },

        /**
         * Extract custom win score from the nearest input or round input
         */
        getCustomWinScore: function (cardOrElement, fallback) {
            var def = (fallback !== undefined && fallback !== null) ? fallback : 2;
            if (!cardOrElement) return def;

            var parentContainer = cardOrElement.closest ? cardOrElement.closest('.single-round-column, .de-round-column, .de-column, .swiss-round-column, .list-round-section, [data-round]') : null;
            var rInp = parentContainer ? parentContainer.querySelector('.round-random-input') : null;
            if (rInp && rInp.value && parseInt(rInp.value, 10) > 0) {
                return parseInt(rInp.value, 10);
            }

            var rNumAttr = parentContainer ? parentContainer.getAttribute('data-round') : null;
            if (rNumAttr && this.customScores[rNumAttr]) {
                return parseInt(this.customScores[rNumAttr], 10);
            }

            return def;
        },

        /**
         * Execute high-speed deadlock-free batch random for a single round
         */
        executeRandomRound: function (engine, roundNumber) {
            var activeEngine = this.resolveEngine(engine);
            var self = this;
            var contextPath = (activeEngine && activeEngine.contextPath) ? activeEngine.contextPath : (window.TourmaContextPath || '');
            var tid = (activeEngine && activeEngine.tournamentId) ? activeEngine.tournamentId : (window.TourmaTournamentId || 'demo');
            var stage = (activeEngine && activeEngine.currentStage) ? activeEngine.currentStage : 1;

            // Look up target score from all matching inputs in DOM or cache
            var targetScore = null;
            var inputs = document.querySelectorAll('.round-random-input[data-round="' + roundNumber + '"], [data-round="' + roundNumber + '"] .round-random-input');
            for (var i = 0; i < inputs.length; i++) {
                if (inputs[i].value && parseInt(inputs[i].value, 10) > 0) {
                    targetScore = parseInt(inputs[i].value, 10);
                    self.customScores[roundNumber] = targetScore;
                    break;
                }
            }

            if (!targetScore && self.customScores[roundNumber]) {
                targetScore = parseInt(self.customScores[roundNumber], 10);
            }

            var payload = {
                action: 'randomRound',
                tournamentId: tid,
                stage: stage,
                roundNumber: roundNumber
            };
            if (targetScore) {
                payload.targetScore = targetScore;
            }

            // Visual spinner on clicked buttons
            var btns = document.querySelectorAll('.btn-round-random[data-round="' + roundNumber + '"], .btn-random-round[data-round="' + roundNumber + '"]');
            btns.forEach(function (b) {
                b.setAttribute('data-original-html', b.innerHTML);
                b.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i>';
            });

            fetch(contextPath + '/api/tournament-random', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                },
                body: new URLSearchParams(payload).toString()
            })
            .then(function (res) { return res.json(); })
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
        executeResetRound: function (engine, roundNumber) {
            var activeEngine = this.resolveEngine(engine);
            var self = this;
            var contextPath = (activeEngine && activeEngine.contextPath) ? activeEngine.contextPath : (window.TourmaContextPath || '');
            var tid = (activeEngine && activeEngine.tournamentId) ? activeEngine.tournamentId : (window.TourmaTournamentId || 'demo');
            var stage = (activeEngine && activeEngine.currentStage) ? activeEngine.currentStage : 1;

            var payload = {
                action: 'resetRound',
                tournamentId: tid,
                stage: stage,
                roundNumber: roundNumber
            };

            // Visual spinner on clicked buttons
            var btns = document.querySelectorAll('.btn-round-reset[data-round="' + roundNumber + '"], .btn-reset-round[data-round="' + roundNumber + '"]');
            btns.forEach(function (b) {
                b.setAttribute('data-original-html', b.innerHTML);
                b.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i>';
            });

            fetch(contextPath + '/api/tournament-random', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                },
                body: new URLSearchParams(payload).toString()
            })
            .then(function (res) { return res.json(); })
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
            var activeEngine = this.resolveEngine(engine);
            var self = this;
            var contextPath = (activeEngine && activeEngine.contextPath) ? activeEngine.contextPath : (window.TourmaContextPath || '');
            var tid = (activeEngine && activeEngine.tournamentId) ? activeEngine.tournamentId : (window.TourmaTournamentId || 'demo');
            var stage = (activeEngine && activeEngine.currentStage) ? activeEngine.currentStage : 1;

            var payload = {
                action: 'randomAll',
                tournamentId: tid,
                stage: stage
            };

            fetch(contextPath + '/api/tournament-random', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                },
                body: new URLSearchParams(payload).toString()
            })
            .then(function (res) { return res.json(); })
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
                    if (!isNaN(rNum)) {
                        self.executeRandomRound(activeEngine, rNum);
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
                    if (!isNaN(rNum)) {
                        self.executeResetRound(activeEngine, rNum);
                    }
                };
            });

            // Direct Input Bindings on Score Inputs
            var inputs = container.querySelectorAll('.round-random-input');
            inputs.forEach(function (inp) {
                inp.oninput = function () {
                    var rNum = this.getAttribute('data-round');
                    var val = this.value;
                    if (rNum) {
                        self.customScores[rNum] = val;
                        var siblings = document.querySelectorAll('.round-random-input[data-round="' + rNum + '"]');
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
            if (!activeEngine || !Array.isArray(activeEngine.roundsList)) return;

            // 1. Update Random Round Buttons Visual State
            var randomBtns = document.querySelectorAll('.btn-round-random, .btn-random-round');
            randomBtns.forEach(function (btn) {
                var rNum = parseInt(btn.getAttribute('data-round'), 10);
                var isReady = false;

                for (var i = 0; i < activeEngine.roundsList.length; i++) {
                    if (activeEngine.roundsList[i].roundNumber === rNum) {
                        var matches = activeEngine.roundsList[i].matches;
                        if (Array.isArray(matches)) {
                            for (var j = 0; j < matches.length; j++) {
                                var m = (activeEngine.matchesMap && activeEngine.matchesMap[matches[j].matchId]) ? activeEngine.matchesMap[matches[j].matchId] : matches[j];
                                var t1 = (m.team1 && m.team1.name) ? m.team1.name : '';
                                var t2 = (m.team2 && m.team2.name) ? m.team2.name : '';
                                var isP1 = (typeof activeEngine.isPlaceholder === 'function') ? activeEngine.isPlaceholder(t1) : (!t1 || t1 === 'TBD' || t1 === 'BYE');
                                var isP2 = (typeof activeEngine.isPlaceholder === 'function') ? activeEngine.isPlaceholder(t2) : (!t2 || t2 === 'TBD' || t2 === 'BYE');
                                var isByeMatch = m.isBye === true || m.isBye === 'true' || m.isBye === 1 || m.isBye === '1';

                                if (!isP1 && !isP2 && !isByeMatch) {
                                    isReady = true;
                                    break;
                                }
                            }
                        }
                        break;
                    }
                }

                if (!isReady) {
                    btn.classList.add('disabled');
                    btn.style.opacity = '0.45';
                } else {
                    btn.classList.remove('disabled');
                    btn.style.opacity = '1';
                }
            });

            // 2. Update Reset Round Buttons Visual State
            var resetBtns = document.querySelectorAll('.btn-round-reset, .btn-reset-round');
            resetBtns.forEach(function (btn) {
                var rNum = parseInt(btn.getAttribute('data-round'), 10);
                var hasPlayed = false;

                for (var i = 0; i < activeEngine.roundsList.length; i++) {
                    if (activeEngine.roundsList[i].roundNumber === rNum) {
                        var matches = activeEngine.roundsList[i].matches;
                        if (Array.isArray(matches)) {
                            for (var j = 0; j < matches.length; j++) {
                                var m = (activeEngine.matchesMap && activeEngine.matchesMap[matches[j].matchId]) ? activeEngine.matchesMap[matches[j].matchId] : matches[j];
                                var isByeMatch = m.isBye === true || m.isBye === 'true' || m.isBye === 1 || m.isBye === '1';

                                if (!isByeMatch) {
                                    var s1 = (m.team1 && m.team1.score !== undefined && m.team1.score !== null && m.team1.score !== '') ? String(m.team1.score).trim() : '';
                                    var s2 = (m.team2 && m.team2.score !== undefined && m.team2.score !== null && m.team2.score !== '') ? String(m.team2.score).trim() : '';
                                    if (m.status === 'COMPLETED' || m.status === 'FINISHED' || m.winnerId || s1 !== '' || s2 !== '') {
                                        hasPlayed = true;
                                        break;
                                    }
                                }
                            }
                        }
                        break;
                    }
                }

                if (!hasPlayed) {
                    btn.classList.add('disabled');
                    btn.style.opacity = '0.45';
                } else {
                    btn.classList.remove('disabled');
                    btn.style.opacity = '1';
                }
            });
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
            if (!isNaN(rNum)) {
                TourmaRoundControls.executeRandomRound(null, rNum);
            }
            return;
        }

        // Reset Round Button Click
        var resetBtn = e.target.closest ? e.target.closest('.btn-round-reset, .btn-reset-round') : null;
        if (resetBtn) {
            e.preventDefault();
            e.stopPropagation();
            var rNum = parseInt(resetBtn.getAttribute('data-round'), 10);
            if (!isNaN(rNum)) {
                TourmaRoundControls.executeResetRound(null, rNum);
            }
            return;
        }
    }, true);

    // Score Input Bi-Directional Sync across all round headers
    document.addEventListener('input', function (e) {
        if (e.target && e.target.classList && e.target.classList.contains('round-random-input')) {
            var rNum = e.target.getAttribute('data-round');
            var val = e.target.value;
            if (rNum) {
                TourmaRoundControls.customScores[rNum] = val;
                var siblings = document.querySelectorAll('.round-random-input[data-round="' + rNum + '"]');
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
            var val = e.target.value;
            if (rNum) {
                TourmaRoundControls.customScores[rNum] = val;
                var siblings = document.querySelectorAll('.round-random-input[data-round="' + rNum + '"]');
                siblings.forEach(function (other) {
                    if (other !== e.target) {
                        other.value = val;
                    }
                });
            }
        }
    }, true);

    window.TourmaRoundControls = TourmaRoundControls;

})(window);
