/**
 * ============================================================================
 * TOURMA - STAGE END POPUP CONTROLLER (stage-end-popup.js)
 * Standalone module for Stage 1 Completion Confirmation, Lock Guard,
 * Seamless Transition to Stage 2, and Unlock to Edit functionality.
 * ============================================================================
 */

(function (window) {
    'use strict';

    var StageEndPopup = {
        tournamentId: null,
        format: null,
        isMultiStage: false,
        currentStage: 1,
        onLockCallback: null,

        /**
         * Check if tournament is a multi-stage tournament in Stage 1
         */
        isMultiStage1: function (tournamentId, currentStage) {
            if (currentStage && (currentStage === 2 || currentStage === '2')) return false;
            try {
                if (window.TourmaTournamentType === 'MULTI_STAGE') return true;
                if (window.TourmaGSL && (window.TourmaGSL.tournamentType === 'MULTI_STAGE' || window.TourmaGSL.cutTarget > 0)) return true;
                if (window.GSLEngine && (window.GSLEngine.tournamentType === 'MULTI_STAGE' || window.GSLEngine.cutTarget > 0)) return true;
                if (window.SingleEliminationEngine && (window.SingleEliminationEngine.tournamentType === 'MULTI_STAGE' || window.SingleEliminationEngine.cutTarget > 0)) return true;
                if (window.TourmaDoubleElimination && (window.TourmaDoubleElimination.tournamentType === 'MULTI_STAGE' || window.TourmaDoubleElimination.cutTarget > 0)) return true;

                var tType = localStorage.getItem('tourma_type_' + tournamentId);
                if (tType === 'SINGLE_STAGE') return false;
                var multiCfgRaw = localStorage.getItem('tourma_multi_config_' + tournamentId);
                if (multiCfgRaw) {
                    var mObj = JSON.parse(multiCfgRaw);
                    if (mObj && (mObj.isMultiStage === false || mObj.tournamentType === 'SINGLE_STAGE')) return false;
                    if (tType === 'MULTI_STAGE') return true;
                    return !!(mObj && mObj.stage2Format);
                }
                var cut = localStorage.getItem('tourma_advance_count_' + tournamentId) || localStorage.getItem('tourma_cut_target_' + tournamentId);
                if (cut && parseInt(cut, 10) > 0) return true;
                return (tType === 'MULTI_STAGE');
            } catch (e) {
                return false;
            }
        },

        /**
         * Check if Stage 1 is locked in storage
         */
        isStage1Locked: function (tournamentId) {
            if (!tournamentId) return false;
            try {
                // PRIMARY: read from DB-injected value (set by JSP from tournament.stage1_status)
                if (window.TourmaDbStage1Status && window.TourmaDbStage1Status[tournamentId]) {
                    var s = window.TourmaDbStage1Status[tournamentId];
                    return (s === 'LOCKED' || s === 'COMPLETED');
                }
                // FALLBACK: localStorage (backward compat while old data exists)
                return localStorage.getItem('tourma_stage1_locked_' + tournamentId) === 'true';
            } catch (e) {
                return false;
            }
        },

        /**
         * Check if Stage 1 is 100% completed across various Stage 1 formats
         */
        checkStage1Completed: function (format, matchesMap, teamsList, config, groupsData) {
            if (!this.tournamentId) return false;
            var tid = this.tournamentId;

            // Format specific completion detection - MUST ALWAYS check actual match scores!
            // 1. GROUP STAGE
            if (format === 'GROUP_STAGE') {
                var gData = groupsData || matchesMap;
                if (!gData) {
                    try {
                        gData = JSON.parse(localStorage.getItem('tourma_group_matches_' + tid));
                    } catch(e) {}
                }
                if (!gData) return false;

                var allDone = true;
                var totalMatches = 0;
                var gKeys = Object.keys(gData);
                if (gKeys.length === 0) return false;

                for (var i = 0; i < gKeys.length; i++) {
                    var gMatches = gData[gKeys[i]] || [];
                    for (var j = 0; j < gMatches.length; j++) {
                        var m = gMatches[j];
                        var t1 = m.team1 ? (m.team1.name || m.team1) : '';
                        var t2 = m.team2 ? (m.team2.name || m.team2) : '';
                        if (!t1 || !t2 || t1 === 'BYE' || t2 === 'BYE') continue;

                        totalMatches++;
                        var s1 = (m.team1 && m.team1.score !== '' && m.team1.score !== null && !isNaN(Number(m.team1.score))) ? Number(m.team1.score) : null;
                        var s2 = (m.team2 && m.team2.score !== '' && m.team2.score !== null && !isNaN(Number(m.team2.score))) ? Number(m.team2.score) : null;
                        var isFinished = (s1 !== null && s2 !== null);
                        if (!isFinished) {
                            allDone = false;
                            break;
                        }
                    }
                    if (!allDone) break;
                }
                return (totalMatches > 0 && allDone);
            }

            // 2. SINGLE ELIMINATION (with cut target)
            if (format === 'SINGLE_ELIMINATION' && matchesMap) {
                var cutTarget = (config && config.cutTarget) ? config.cutTarget : 0;
                if (!cutTarget) {
                    try {
                        var rawCut = localStorage.getItem('tourma_advance_count_' + tid) || localStorage.getItem('tourma_cut_target_' + tid);
                        if (rawCut) cutTarget = parseInt(rawCut, 10);
                    } catch (e) {}
                }

                if (cutTarget && cutTarget > 1) {
                    var stoppingRound = 0;
                    if (window.SingleEliminationEngine && typeof window.SingleEliminationEngine.getStoppingRound === 'function') {
                        stoppingRound = window.SingleEliminationEngine.getStoppingRound();
                    }
                    if (!stoppingRound && teamsList && teamsList.length > 0) {
                        var numTeams = teamsList.length;
                        var current = cutTarget;
                        while (current * 2 <= numTeams) {
                            current *= 2;
                            stoppingRound++;
                        }
                        if (current < numTeams) {
                            stoppingRound++;
                        }
                    }
                    stoppingRound = Math.max(1, stoppingRound);

                    var allStopMatchesDone = true;
                    var stopMatchCount = 0;
                    var mKeys = Object.keys(matchesMap);

                    for (var k = 0; k < mKeys.length; k++) {
                        var sm = matchesMap[mKeys[k]];
                        if (sm && sm.roundNumber === stoppingRound) {
                            var t1Name = (sm.team1 && sm.team1.name) ? sm.team1.name : '';
                            var t2Name = (sm.team2 && sm.team2.name) ? sm.team2.name : '';
                            if (t1Name === 'BYE' || t2Name === 'BYE' || sm.isBye) continue;

                            stopMatchCount++;
                            var s1 = (sm.team1 && sm.team1.score !== '' && sm.team1.score !== null && !isNaN(Number(sm.team1.score))) ? Number(sm.team1.score) : null;
                            var s2 = (sm.team2 && sm.team2.score !== '' && sm.team2.score !== null && !isNaN(Number(sm.team2.score))) ? Number(sm.team2.score) : null;
                            var isDone = (Boolean(sm.winnerId) || (s1 !== null && s2 !== null && (s1 > s2 || s2 > s1)) || sm.status === 'COMPLETED' || sm.status === 'FINISHED' || sm.status === 'DONE');
                            if (!isDone) {
                                allStopMatchesDone = false;
                                break;
                            }
                        }
                    }
                    return (stopMatchCount > 0 && allStopMatchesDone);
                }
            }

            // 3. ROUND ROBIN
            if (format === 'ROUND_ROBIN' && matchesMap) {
                var rKeys = Object.keys(matchesMap);
                var totalRm = 0;
                var doneRm = 0;
                for (var r = 0; r < rKeys.length; r++) {
                    var rm = matchesMap[rKeys[r]];
                    if (!rm) continue;
                    var rt1 = rm.team1 ? rm.team1.name : '';
                    var rt2 = rm.team2 ? rm.team2.name : '';
                    if (!rt1 || !rt2 || rt1 === 'BYE' || rt2 === 'BYE') continue;

                    totalRm++;
                    var rs1 = (rm.team1 && rm.team1.score !== '' && rm.team1.score !== null && !isNaN(Number(rm.team1.score))) ? Number(rm.team1.score) : null;
                    var rs2 = (rm.team2 && rm.team2.score !== '' && rm.team2.score !== null && !isNaN(Number(rm.team2.score))) ? Number(rm.team2.score) : null;
                    if (rs1 !== null && rs2 !== null) {
                        doneRm++;
                    }
                }
                return (totalRm > 0 && doneRm === totalRm);
            }

            // 4. SWISS
            if ((format === 'SWISS' || format === 'SWISS_LITE') && matchesMap) {
                if (!teamsList || teamsList.length !== 16 || !window.TourmaSwissAlgorithm) return false;
                var standings = window.TourmaSwissAlgorithm.calculateStandings(teamsList, matchesMap);
                // All teams must have reached 3 wins or 3 losses, never just finished the current round.
                return standings.length === 16
                    && standings.filter(function (st) { return st.wins === 3 && st.losses < 3; }).length === 8
                    && standings.every(function (st) { return (st.wins === 3 && st.losses < 3)
                        || (st.losses === 3 && st.wins < 3); });
            }

            // 5. DOUBLE ELIMINATION (Multi-Stage Stage 1 with cutTarget)
            if (format === 'DOUBLE_ELIMINATION' && matchesMap) {
                var cutTarget = (config && config.cutTarget) ? config.cutTarget : 0;
                if (!cutTarget) {
                    try {
                        var rawDE = localStorage.getItem('tourma_advance_count_' + tid) || localStorage.getItem('tourma_cut_target_' + tid);
                        if (rawDE) cutTarget = parseInt(rawDE, 10);
                    } catch(e) {}
                }
                if (!cutTarget || cutTarget <= 1) return false;

                var engine = window.TourmaDoubleElimination;
                return !!(engine && typeof engine.getAdvancingTeams === 'function'
                    && engine.getAdvancingTeams().length === Number(cutTarget));
            }

            // 6. GSL (Dual Tournament) Stage 1
            if (format === 'GSL') {
                var gslData = groupsData;
                if (!gslData && window.TourmaGSL && window.TourmaGSL.groupsMap) {
                    gslData = window.TourmaGSL.groupsMap;
                }
                if (!gslData && window.GSLEngine && window.GSLEngine.groupsMap) {
                    gslData = window.GSLEngine.groupsMap;
                }
                if (!gslData) {
                    try {
                        gslData = JSON.parse(localStorage.getItem('tourma_gsl_matches_' + tid));
                    } catch(e) {}
                }
                if (!gslData) return false;
                var gKeys = Object.keys(gslData);
                if (gKeys.length === 0) return false;

                var engine = window.TourmaGSL || window.GSLEngine;
                var cutTarget = (config && config.cutTarget) ? config.cutTarget : (engine ? engine.cutTarget : 0);
                var advancePerGroup = cutTarget > 0 ? Math.max(1, Math.round(cutTarget / gKeys.length)) : 2;

                var allGroupsDone = true;
                var totalQualifiers = 0;

                for (var gIdx = 0; gIdx < gKeys.length; gIdx++) {
                    var grp = gslData[gKeys[gIdx]];
                    if (!grp || !grp.matchesMap) { allGroupsDone = false; break; }

                    // 1. Verify group has required qualifiers determined
                    if (engine && typeof engine.getGroupQualifiers === 'function') {
                        var qList = engine.getGroupQualifiers(grp, advancePerGroup);
                        if (!qList || qList.length < advancePerGroup) {
                            allGroupsDone = false;
                            break;
                        }
                        totalQualifiers += qList.length;
                    }

                    // 2. Verify all active matches in this group are actually completed
                    var mMap = grp.matchesMap;
                    var mKeys = Object.keys(mMap);
                    var checkedMatches = {};

                    for (var mk = 0; mk < mKeys.length; mk++) {
                        var gm = mMap[mKeys[mk]];
                        if (!gm) continue;
                        var rawId = gm.rawId || gm.matchId || gm.id;
                        if (checkedMatches[rawId]) continue;
                        checkedMatches[rawId] = true;

                        if (gm.isBye || gm.status === 'BYE') continue;

                        var t1 = gm.team1 ? (gm.team1.name || gm.team1) : '';
                        var t2 = gm.team2 ? (gm.team2.name || gm.team2) : '';

                        // If any match still has placeholder or unresolved teams, group is definitely NOT done
                        if (!t1 || !t2 || t1 === 'TBD' || t2 === 'TBD' ||
                            (typeof t1 === 'string' && (t1.indexOf('#') !== -1 || t1.indexOf('Winner') !== -1 || t1.indexOf('Loser') !== -1)) ||
                            (typeof t2 === 'string' && (t2.indexOf('#') !== -1 || t2.indexOf('Winner') !== -1 || t2.indexOf('Loser') !== -1))) {
                            allGroupsDone = false;
                            break;
                        }

                        var s1 = (gm.team1 && gm.team1.score !== '' && gm.team1.score !== null && !isNaN(Number(gm.team1.score))) ? Number(gm.team1.score) : null;
                        var s2 = (gm.team2 && gm.team2.score !== '' && gm.team2.score !== null && !isNaN(Number(gm.team2.score))) ? Number(gm.team2.score) : null;
                        var hasWinner = (gm.winnerId === 'team1' || gm.winnerId === 'team2' || (gm.winnerId && (String(gm.winnerId) === String(gm.team1.id) || String(gm.winnerId) === String(gm.team2.id))));
                        var hasFinishedScore = (s1 !== null && s2 !== null && s1 !== s2);

                        if (!hasWinner && !hasFinishedScore) {
                            allGroupsDone = false;
                            break;
                        }
                    }
                    if (!allGroupsDone) break;
                }

                var requiredTotal = cutTarget > 0 ? cutTarget : (gKeys.length * advancePerGroup);
                return (allGroupsDone && totalQualifiers >= requiredTotal);
            }

            return false;

        },

        /**
         * Ensure banner element exists in DOM
         */
        ensureDOM: function () {
            var banner = document.getElementById('stageEndPopupBanner');
            if (!banner) {
                var div = document.createElement('div');
                div.id = 'stageEndPopupBanner';
                div.className = 'stage-end-popup-banner';
                div.style.display = 'none';
                div.innerHTML =
                    '<div class="stage-end-popup-content">' +
                        '<div id="stageEndPopupText" class="stage-end-popup-text">' +
                            '<span id="stageEndTitleLine" class="stage-end-title-line"></span>' +
                        '</div>' +
                        '<div id="stageEndPopupActions" class="stage-end-popup-actions">' +
                            '<button type="button" id="stageEndConfirmBtn" class="stage-end-confirm-btn" onclick="window.StageEndPopup.confirmStageEnd()">' +
                                'Xác nhận' +
                            '</button>' +
                            '<button type="button" id="stageEndNextBtn" class="stage-end-next-btn" style="display: none;" onclick="window.StageEndPopup.goToStage2()">' +
                                '<i class="fa-solid fa-arrow-right"></i> Tiếp Tục Vòng 2' +
                            '</button>' +
                            '<button type="button" id="stageEndUnlockBtn" class="stage-end-unlock-btn" style="display: none;" onclick="window.StageEndPopup.showUnlockConfirmModal()">' +
                                'Mở khóa để sửa' +
                            '</button>' +
                        '</div>' +
                    '</div>';

                var mainEl = document.querySelector('main.container, main.has-sidebar, main');
                var controlBar = document.querySelector(
                    '.tournament-navbar-control-bar, .gsl-control-bar, .group-stage-control-bar, .single-elimination-control-bar, .rr-control-bar, .de-control-bar, .swiss-control-bar'
                );

                if (mainEl && controlBar && controlBar.parentNode === mainEl) {
                    var next = controlBar.nextSibling;
                    if (next) {
                        mainEl.insertBefore(div, next);
                    } else {
                        mainEl.appendChild(div);
                    }
                } else if (mainEl) {
                    mainEl.insertBefore(div, mainEl.firstChild);
                } else {
                    document.body.insertBefore(div, document.body.firstChild);
                }
            }
            return document.getElementById('stageEndPopupBanner');
        },

        /**
         * Update and render Stage End Popup
         */
        update: function (tournamentId, format, matchesMap, teamsList, config, groupsData, stage) {
            if (!this.transitionInProgress) this.pendingStage2Teams = null;
            this.tournamentId = tournamentId || 'demo';
            this.format = format;
            this.currentStage = (stage === 2 || stage === '2') ? 2 : 1;

            // Only active in Stage 1 of Multi-Stage tournaments
            if (!this.isMultiStage1(this.tournamentId, this.currentStage)) {
                this.hideBanner();
                return;
            }

            var isCompleted = this.checkStage1Completed(format, matchesMap, teamsList, config, groupsData);
            var isLocked = this.isStage1Locked(this.tournamentId);

            if (!isCompleted && !isLocked) {
                this.hideBanner();
                return;
            }

            this.ensureDOM();
            var banner = document.getElementById('stageEndPopupBanner');
            var titleLine = document.getElementById('stageEndTitleLine');
            var confirmBtn = document.getElementById('stageEndConfirmBtn');
            var nextBtn = document.getElementById('stageEndNextBtn');
            var unlockBtn = document.getElementById('stageEndUnlockBtn');

            if (!banner) return;

            if (isLocked) {
                // STATE 2: ALREADY LOCKED (When returning to Stage 1)
                banner.classList.add('is-locked');
                banner.style.display = 'flex';

                if (titleLine) {
                    titleLine.textContent = 'Vòng 1 đã hoàn tất';
                }

                if (confirmBtn) confirmBtn.style.display = 'none';
                if (nextBtn) nextBtn.style.display = 'inline-flex';
                if (unlockBtn) unlockBtn.style.display = 'inline-flex';

                // Enforce read-only locks on Stage 1 UI
                this.applyLockToUI(true);

            } else if (isCompleted) {
                // STATE 1: COMPLETED, READY FOR CONFIRMATION
                banner.classList.remove('is-locked');
                banner.style.display = 'flex';

                if (titleLine) {
                    titleLine.textContent = 'Khi bạn xác nhận hoàn thành vòng 1, kết quả sẽ bị khóa và bạn sẽ được chuyển sang vòng tiếp theo.';
                }

                if (confirmBtn) confirmBtn.style.display = 'inline-flex';
                if (nextBtn) nextBtn.style.display = 'none';
                if (unlockBtn) unlockBtn.style.display = 'none';

                this.applyLockToUI(false);
            }
        },

        showTransitionLoader: function (msg) {
            var loader = document.getElementById('stageEndTransitionOverlay');
            if (!loader) {
                loader = document.createElement('div');
                loader.id = 'stageEndTransitionOverlay';
                loader.style.cssText = 'position: fixed; inset: 0; z-index: 99999; background: rgba(10, 15, 29, 0.75); backdrop-filter: blur(4px); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16px; color: #fff; font-family: Lexend, sans-serif;';
                loader.innerHTML = '<div style="width: 44px; height: 44px; border: 4px solid rgba(45, 212, 191, 0.2); border-top-color: #2dd4bf; border-radius: 50%; animation: stageSpin 0.7s linear infinite;"></div>' +
                                   '<div id="stageEndTransitionMsg" style="font-size: 1.05rem; font-weight: 600; color: #f1f5f9; text-shadow: 0 2px 8px rgba(0,0,0,0.5);">' + (msg || 'Đang chuyển sang Vòng 2...') + '</div>' +
                                   '<style>@keyframes stageSpin { to { transform: rotate(360deg); } }</style>';
                document.body.appendChild(loader);
            } else {
                var msgEl = document.getElementById('stageEndTransitionMsg');
                if (msgEl) msgEl.textContent = msg || 'Đang chuyển sang Vòng 2...';
                loader.style.display = 'flex';
            }
        },

        /**
         * Safely extract advancing teams from current Stage 1 engine
         */
        getAdvancingTeamsFromCurrentEngine: function () {
            var tid = this.tournamentId;
            var teams = null;

            try {
                if (window.TourmaGroupStage && typeof window.TourmaGroupStage.getAdvancingTeams === 'function') {
                    teams = window.TourmaGroupStage.getAdvancingTeams();
                } else if (window.TourmaSwiss && typeof window.TourmaSwiss.getAdvancingTeams === 'function') {
                    teams = window.TourmaSwiss.getAdvancingTeams();
                } else if (window.TourmaGSL && typeof window.TourmaGSL.getAdvancingTeams === 'function') {
                    teams = window.TourmaGSL.getAdvancingTeams();
                } else if (window.TourmaRoundRobin && typeof window.TourmaRoundRobin.getAdvancingTeams === 'function') {
                    teams = window.TourmaRoundRobin.getAdvancingTeams();
                } else if (window.SingleEliminationEngine && typeof window.SingleEliminationEngine.getAdvancingTeams === 'function') {
                    teams = window.SingleEliminationEngine.getAdvancingTeams();
                } else if (window.TourmaDoubleElimination && typeof window.TourmaDoubleElimination.getAdvancingTeams === 'function') {
                    teams = window.TourmaDoubleElimination.getAdvancingTeams();
                }
            } catch (e) {
                console.warn('[StageEndPopup] getAdvancingTeams error:', e);
                // A failed GSL draw must not be replaced with an old cached draw.
                if (this.format === 'GSL') throw e;
            }

            // Fallback: check localStorage for tourma_stage2_teams_
            if (!teams || !Array.isArray(teams) || teams.length === 0) {
                try {
                    var raw = localStorage.getItem('tourma_stage2_teams_' + tid) || localStorage.getItem('tourma_advance_teams_' + tid);
                    if (raw) teams = JSON.parse(raw);
                } catch (e) {}
            }

            return (teams && Array.isArray(teams)) ? teams : null;
        },

        /**
         * Handle Confirm End Stage: Lock and immediately transition to Stage 2
         */
        resetStage: function (tid, stage, contextPath) {
            return fetch(contextPath + '/api/cut-stage', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'},
                body: new URLSearchParams({action: 'resetStage', tournamentId: tid, stage: stage}).toString()
            }).then(function (res) { return res.json(); }).then(function (data) {
                if (!data || data.status !== 'success') throw new Error('Không reset được stage. Dữ liệu chưa được thay đổi.');
                var keys = ['tourma_final_locked_', 'tourma_champion_', 'tourma_final_champion_'];
                if (Number(stage) === 1) {
                    keys = keys.concat(['tourma_stage2_teams_', 'tourma_advance_teams_', 'tourma_stage1_locked_',
                        'tourma_stage1_completed_', 'tourma_bracket_stage2_', 'tourma_matches_stage2_']);
                }
                try { keys.forEach(function (key) { localStorage.removeItem(key + tid); }); } catch (e) {}
                window.location.reload();
            }).catch(function (error) { window.alert(error.message || 'Không reset được stage. Vui lòng thử lại.'); });
        },

        confirmStageEnd: async function () {
            var tid = this.tournamentId;
            if (!tid || this.transitionInProgress) return;
            this.transitionInProgress = true;

            // 0. Immediate UI feedback (instant responsiveness)
            var confirmBtn = document.getElementById('stageEndConfirmBtn');
            if (confirmBtn) {
                confirmBtn.disabled = true;
                confirmBtn.style.pointerEvents = 'none';
                confirmBtn.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin"></i> Đang chuyển Vòng 2...';
            }
            var modalBtn = document.getElementById('stageEndModalConfirmBtn');
            if (modalBtn) {
                modalBtn.disabled = true;
                modalBtn.style.pointerEvents = 'none';
                modalBtn.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin"></i> Đang chuyển Vòng 2...';
            }
            this.showTransitionLoader('Đang xác nhận kết quả và lưu vào cơ sở dữ liệu...');

            var self = this;
            var failTransition = function (err) {
                self.transitionInProgress = false;
                var loader = document.getElementById('stageEndTransitionOverlay');
                if (loader) loader.style.display = 'none';
                [confirmBtn, modalBtn].forEach(function (btn) {
                    if (!btn) return;
                    btn.disabled = false;
                    btn.style.pointerEvents = '';
                    btn.textContent = 'Thử lại';
                });
                console.error('[StageEndPopup] Stage transition failed:', err);
                window.alert('Chưa thể chuyển sang Vòng 2: ' + (err.message || err));
            };
            var requireSuccess = function (data) {
                if (!data || data.status !== 'success') {
                    throw new Error((data && data.message) || 'Không lưu được dữ liệu. Vui lòng thử lại.');
                }
                return data;
            };
            try {
                var league = this.format === 'ROUND_ROBIN' || this.format === 'GROUP_STAGE';
                if (league) {
                    var engine = this.format === 'GROUP_STAGE' ? window.TourmaGroupStage : window.TourmaRoundRobin;
                    if (engine && engine.waitForPendingSaves) await engine.waitForPendingSaves();
                    this.pendingStage2Teams = null;
                }
                // Extract and draw once; retain the draw when retrying a failed save.
                if (!this.pendingStage2Teams || this.format === 'SWISS' || this.format === 'SWISS_LITE') {
                    var advTeams = league
                        ? await fetch((window.TourmaContextPath || '') + '/api/cut-stage', {
                            method: 'POST', headers: {'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'},
                            body: new URLSearchParams({action:'qualify',tournamentId:tid}).toString()
                        }).then(function(res){return res.json();}).then(requireSuccess).then(function(data){return data.qualifiedTeams;})
                        : (this.format === 'SWISS' || this.format === 'SWISS_LITE')
                        ? await window.TourmaSwiss.getAdvancingTeams()
                        : this.getAdvancingTeamsFromCurrentEngine();
                    if (!advTeams || advTeams.length === 0) {
                        throw new Error('Chưa xác định được đội đi tiếp.');
                    }
                    var s2Url = this.resolveStage2Url(tid);
                    // GSL returns seed order. DE consumes consecutive pairs instead.
                    if ((league || this.format === 'GSL' || this.format === 'DOUBLE_ELIMINATION' || this.format === 'SWISS' || this.format === 'SWISS_LITE') && s2Url.indexOf('double-elimination') !== -1
                            && !(this.format === 'GROUP_STAGE' && (advTeams.length & (advTeams.length - 1)) !== 0)) {
                        var gsl = window.TourmaGSL || window.GSLEngine;
                        var seeds = gsl ? gsl.getStandardBracketSeeds(advTeams.length)
                            : window.TourmaBracketAlgorithm.generateSeedPairs(advTeams.length).reduce(function (all, pair) { return all.concat(pair); }, []);
                        advTeams = seeds.map(function (seed) { return advTeams[seed - 1]; });
                    }
                    this.pendingStage2Teams = advTeams;
                }
                var teams = this.pendingStage2Teams;
                var save;
                if (['SINGLE_ELIMINATION', 'GSL', 'DOUBLE_ELIMINATION', 'SWISS', 'SWISS_LITE', 'ROUND_ROBIN', 'GROUP_STAGE'].indexOf(this.format) !== -1) {
                    save = fetch((window.TourmaContextPath || '') + '/api/cut-stage', {
                        method: 'POST',
                        headers: {'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'},
                        body: new URLSearchParams({action: 'finalizeStage1', tournamentId: tid, stage2Teams: JSON.stringify(teams)}).toString()
                    }).then(function (res) { return res.json(); }).then(requireSuccess);
                } else {
                    save = window.TourmaStageEndPopup_saveStage2TeamsToDB(tid, teams)
                        .then(requireSuccess).then(function () {
                            return window.TourmaStageEndPopup_saveStage1StatusToDB(tid, 'LOCKED');
                        }).then(requireSuccess);
                }
                save
                    .then(function () {
                        try {
                            localStorage.setItem('tourma_stage2_teams_' + tid, JSON.stringify(teams));
                            localStorage.setItem('tourma_advance_teams_' + tid, JSON.stringify(teams));
                            localStorage.setItem('tourma_stage1_locked_' + tid, 'true');
                            localStorage.setItem('tourma_stage1_completed_' + tid, 'true');
                        } catch (e) {}
                        window.location.href = self.resolveStage2Url(tid);
                    }).catch(failTransition);
            } catch (e) {
                failTransition(e);
            }
        },

        /**
         * Navigate directly to Stage 2
         */
        goToStage2: function () {
            var nextBtn = document.getElementById('stageEndNextBtn');
            if (nextBtn) {
                nextBtn.disabled = true;
                nextBtn.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin"></i> Đang tải...';
            }
            this.showTransitionLoader('Đang chuyển sang Vòng 2...');
            var s2Url = this.resolveStage2Url(this.tournamentId);
            window.location.href = s2Url;
        },

        /**
         * Resolve Stage 2 target page URL based on multi_config
         */
        resolveStage2Url: function (tid) {
            var contextPath = (window.location.pathname.indexOf('/Tourma/') !== -1) ? '/Tourma' : '';
            var page = 'single-elimination.jsp';

            try {
                var mCfg = window.TourmaMultiStageConfig;
                if (!mCfg) {
                    var multiCfgRaw = localStorage.getItem('tourma_multi_config_' + tid);
                    if (multiCfgRaw) {
                        mCfg = (typeof multiCfgRaw === 'string') ? JSON.parse(multiCfgRaw) : multiCfgRaw;
                    }
                }
                if (mCfg) {
                    var s2Fmt = mCfg.stage2Format || 'SINGLE_ELIMINATION';
                    if (s2Fmt === 'SINGLE_ELIMINATION') page = 'single-elimination.jsp';
                    else if (s2Fmt === 'DOUBLE_ELIMINATION') page = 'double-elimination.jsp';
                    else if (s2Fmt === 'ROUND_ROBIN') page = 'round-robin.jsp';
                    else if (s2Fmt === 'GROUP_STAGE') page = 'group-stage.jsp';
                    else if (s2Fmt === 'SWISS' || s2Fmt === 'SWISS_LITE') page = 'swiss-stage.jsp';
                }
            } catch (e) {}

            return contextPath + '/common/' + page + '?id=' + tid + '&stage=2';
        },

        /**
         * Show modal confirmation for unlocking Stage 1
         */
        showUnlockConfirmModal: function () {
            var modalId = 'stageEndUnlockModal';
            var existingModal = document.getElementById(modalId);
            if (existingModal) existingModal.remove();

            var overlay = document.createElement('div');
            overlay.id = modalId;
            overlay.className = 'stage-end-modal-overlay';
            overlay.innerHTML =
                '<div class="stage-end-modal-container">' +
                    '<div class="stage-end-modal-header">' +
                        '<i class="fa-solid fa-triangle-exclamation"></i>' +
                        '<span>Mở Khóa Vòng 1</span>' +
                    '</div>' +
                    '<div class="stage-end-modal-body">' +
                        '<p>Mở khóa Vòng 1 sẽ cho phép chỉnh sửa lại tỉ số các trận đấu.</p>' +
                        '<p style="margin-top: 0.5rem; color: #f59e0b; font-size: 0.85rem;">' +
                            '⚠️ Mở khóa sẽ xóa toàn bộ nhánh và kết quả Vòng 2. Xác nhận lại Vòng 1 sẽ tạo nhánh Vòng 2 mới.' +
                        '</p>' +
                    '</div>' +
                    '<div class="stage-end-modal-footer">' +
                        '<button type="button" class="stage-end-close-btn" onclick="document.getElementById(\'' + modalId + '\').remove()">Hủy</button>' +
                        '<button type="button" class="stage-end-unlock-btn" onclick="window.StageEndPopup.confirmUnlock()">' +
                            '<i class="fa-solid fa-lock-open"></i> Đồng Ý Mở Khóa' +
                        '</button>' +
                    '</div>' +
                '</div>';

            document.body.appendChild(overlay);
        },

        /**
         * Confirm unlock
         */
        confirmUnlock: function () {
            var modal = document.getElementById('stageEndUnlockModal');
            if (modal) modal.remove();

            var tid = this.tournamentId;
            this.pendingStage2Teams = null;
            try {
                // BOTH localStorage (compat) AND DB


                var self = this;
                window.TourmaStageEndPopup_saveStage1StatusToDB(tid, 'PENDING').then(function (data) {
                    if (!data || data.status !== 'success') {
                        window.alert('Không mở khóa được stage. Vui lòng thử lại.');
                        return;
                    }
                    try {
                        ['tourma_stage2_teams_','tourma_advance_teams_','tourma_bracket_stage2_','tourma_matches_stage2_',
                         'tourma_de_matches_stage2_','tourma_rr_matches_stage2_','tourma_swiss_matches_stage2_',
                         'tourma_final_locked_','tourma_final_champion_','tourma_champion_'].forEach(function (prefix) { localStorage.removeItem(prefix+tid); });
                        localStorage.setItem('tourma_stage1_locked_'+tid,'false');
                        localStorage.setItem('tourma_stage_state_'+tid,JSON.stringify({ready:false,changedAt:Date.now()}));
                    } catch (ignored) {}
                    var banner = document.getElementById('stageEndPopupBanner');
                    if (banner) {
                        banner.classList.remove('is-locked');
                        var titleLine = document.getElementById('stageEndTitleLine');
                        if (titleLine) {
                            titleLine.textContent = 'Khi bạn xác nhận hoàn thành vòng 1, kết quả sẽ bị khóa và bạn sẽ được chuyển sang vòng tiếp theo.';
                        }
                        var confirmBtn = document.getElementById('stageEndConfirmBtn');
                        if (confirmBtn) confirmBtn.style.display = 'inline-flex';
                        var nextBtn = document.getElementById('stageEndNextBtn');
                        if (nextBtn) nextBtn.style.display = 'none';
                        var unlockBtn = document.getElementById('stageEndUnlockBtn');
                        if (unlockBtn) unlockBtn.style.display = 'none';
                    }
                    self.applyLockToUI(false);
                });
            } catch (e) {
                console.error('[StageEndPopup] confirmUnlock error:', e);
            }
        },

        /**
         * Prompt user when attempting to edit a locked Stage 1
         */
        promptUnlock: function () {
            var banner = document.getElementById('stageEndPopupBanner');
            if (banner) {
                banner.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            }

            var oldToast = document.getElementById('tourmaLockToast');
            if (oldToast) oldToast.remove();

            var toast = document.createElement('div');
            toast.id = 'tourmaLockToast';
            toast.className = 'tourma-lock-toast';
            toast.innerHTML = 
                '<i class="fa-solid fa-lock tourma-lock-toast-icon"></i>' +
                '<div class="tourma-lock-toast-body">' +
                    '<div class="tourma-lock-toast-title">Vòng 1 đã hoàn thành & đang khóa</div>' +
                    '<div class="tourma-lock-toast-desc">Bấm "Mở khóa" trên banner để chỉnh sửa lại kết quả.</div>' +
                '</div>' +
                '<button type="button" class="tourma-lock-toast-btn" onclick="if(window.StageEndPopup){window.StageEndPopup.showUnlockConfirmModal();}var t=document.getElementById(\'tourmaLockToast\');if(t)t.remove();">Mở khóa</button>';
            document.body.appendChild(toast);
            setTimeout(function () {
                if (toast && toast.parentNode) toast.remove();
            }, 5000);
        },

        /**
         * Apply or remove disabled states from inputs/buttons when locked
         */
        applyLockToUI: function (isLocked) {
            if (isLocked) {
                document.body.classList.add('stage1-is-locked');
                window.TourmaQuickMode = false;
                if (window.SingleEliminationEngine) window.SingleEliminationEngine.isQuickMode = false;
                if (window.TourmaDoubleElimination) window.TourmaDoubleElimination.isQuickMode = false;
                if (window.TourmaGSL) window.TourmaGSL.isQuickMode = false;
                if (window.GSLEngine) window.GSLEngine.isQuickMode = false;
            } else {
                document.body.classList.remove('stage1-is-locked');
            }

            // Quick mode buttons
            var qBtns = document.querySelectorAll('#singleBtnQuickMode, #deBtnQuickMode, #gslBtnQuickMode, .btn-quick-mode-toggle');
            for (var q = 0; q < qBtns.length; q++) {
                if (isLocked) {
                    qBtns[q].classList.remove('active');
                    var statusSpan = qBtns[q].querySelector('.quick-mode-status-text');
                    if (statusSpan) statusSpan.textContent = 'OFF';
                    qBtns[q].style.opacity = '0.5';
                    qBtns[q].style.pointerEvents = 'none';
                } else {
                    qBtns[q].style.opacity = '1';
                    qBtns[q].style.pointerEvents = 'auto';
                }
            }

            // Disable random score buttons, edit score inputs if locked
            var scoreInputs = document.querySelectorAll('.match-score-input, .score-input, .group-score-input');
            for (var i = 0; i < scoreInputs.length; i++) {
                scoreInputs[i].disabled = isLocked;
            }

            var randomBtns = document.querySelectorAll('.btn-random-scores, .btn-random-round, .btn-random-group, .btn-auto-score, #gslBtnRandomAll, .btn-random-all');
            for (var j = 0; j < randomBtns.length; j++) {
                randomBtns[j].disabled = isLocked;
                if (isLocked) {
                    randomBtns[j].style.opacity = '0.5';
                    randomBtns[j].style.pointerEvents = 'none';
                } else {
                    randomBtns[j].style.opacity = '1';
                    randomBtns[j].style.pointerEvents = 'auto';
                }
            }
        },

        /**
         * Hide banner
         */
        hideBanner: function () {
            var banner = document.getElementById('stageEndPopupBanner');
            if (banner) banner.style.display = 'none';
        }
    };

    /**
     * Helper to resolve standard API endpoint for stage actions
     */
    function resolveStageApiEndpoint() {
        var contextPath = window.TourmaContextPath || '';
        var path = window.location.pathname || '';
        if (path.indexOf('/double-elimination') !== -1) {
            return contextPath + '/double-elimination';
        } else if (path.indexOf('/swiss-stage') !== -1) {
            return contextPath + '/swiss-stage';
        } else if (path.indexOf('/group-stage') !== -1) {
            return contextPath + '/group-stage';
        } else if (path.indexOf('/round-robin') !== -1) {
            return contextPath + '/round-robin';
        } else if (path.indexOf('/gsl') !== -1) {
            return contextPath + '/gsl';
        }
        return contextPath + '/single-elimination';
    }

    /**
     * Helper to persist Stage 1 status (PENDING, LOCKED, COMPLETED) to DB via AJAX (Returns Promise)
     */
    window.TourmaStageEndPopup_saveStage1StatusToDB = function (tournamentId, status) {
        if (!tournamentId || tournamentId === 'demo') return Promise.resolve(null);
        var endpoint = resolveStageApiEndpoint();

        var params = new URLSearchParams();
        params.append('action', 'saveStage1Status');
        params.append('tournamentId', tournamentId);
        params.append('stage1Status', status || 'PENDING');

        return fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
            body: params.toString()
        }).then(function (res) { return res.json(); })
        .then(function (data) {
            if (!data || data.status !== 'success') return data;
            console.log('[STAGE1 STATUS AJAX] Saved status to DB:', status, data);
            if (!window.TourmaDbStage1Status) window.TourmaDbStage1Status = {};
            window.TourmaDbStage1Status[tournamentId] = status;
            return data;
        }).catch(function (err) {
            console.warn('[STAGE1 STATUS AJAX] Failed to save stage1 status:', err);
            return null;
        });
    };

    /**
     * Helper to persist Stage 2 qualified teams to DB via AJAX (Returns Promise)
     */
    window.TourmaStageEndPopup_saveStage2TeamsToDB = function (tournamentId, stage2Teams) {
        if (!tournamentId || tournamentId === 'demo' || !stage2Teams || stage2Teams.length === 0) {
            return Promise.resolve(null);
        }
        var endpoint = resolveStageApiEndpoint();

        var teamsPayload = (typeof stage2Teams === 'string') ? stage2Teams : JSON.stringify(stage2Teams);
        var params = new URLSearchParams();
        params.append('action', 'saveStage2Teams');
        params.append('tournamentId', tournamentId);
        params.append('stage2Teams', teamsPayload);

        return fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
            body: params.toString()
        }).then(function (res) { return res.json(); })
        .then(function (data) {
            console.log('[STAGE2 TEAMS AJAX] Saved to DB:', data);
            return data;
        }).catch(function (err) {
            console.warn('[STAGE2 TEAMS AJAX] Failed saving to DB:', err);
            return null;
        });
    };

    window.StageEndPopup = StageEndPopup;

})(window);
