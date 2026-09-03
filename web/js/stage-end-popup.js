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
                var tType = localStorage.getItem('tourma_type_' + tournamentId);
                if (tType === 'SINGLE_STAGE') return false;
                var multiCfgRaw = localStorage.getItem('tourma_multi_config_' + tournamentId);
                if (multiCfgRaw) {
                    var mObj = JSON.parse(multiCfgRaw);
                    if (mObj && (mObj.isMultiStage === false || mObj.tournamentType === 'SINGLE_STAGE')) return false;
                    if (tType === 'MULTI_STAGE') return true;
                    return !!(mObj && mObj.stage2Format);
                }
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
                var gData = groupsData;
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

                if (cutTarget && cutTarget > 1 && teamsList && teamsList.length > 0) {
                    var pow2 = 1;
                    while (pow2 < teamsList.length) pow2 *= 2;
                    var totalRounds = Math.log2(pow2);
                    var diff = Math.round(Math.log2(cutTarget));
                    var stoppingRound = Math.max(1, totalRounds - diff);

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
                            var isDone = (s1 !== null && s2 !== null && (s1 > s2 || s2 > s1));
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
                // Primary: Swiss cut complete (8 teams qualified)
                if (teamsList && teamsList.length > 0 && window.TourmaSwissAlgorithm && typeof window.TourmaSwissAlgorithm.calculateStandings === 'function') {
                    var swStandings = window.TourmaSwissAlgorithm.calculateStandings(teamsList, matchesMap);
                    var swQualified = swStandings.filter(function (st) { return st.qualified; });
                    if (swQualified.length >= 8) return true;
                }

                // Fallback: all playable matches have decisive scores
                var swKeys = Object.keys(matchesMap);
                var swTotal = 0;
                var swDone = 0;
                for (var s = 0; s < swKeys.length; s++) {
                    var swm = matchesMap[swKeys[s]];
                    if (!swm) continue;
                    var st1 = swm.team1 ? (swm.team1.name || swm.team1) : '';
                    var st2 = swm.team2 ? (swm.team2.name || swm.team2) : '';
                    if (!st1 || !st2 || st1 === 'TBD' || st2 === 'TBD' || st1 === 'BYE' || st2 === 'BYE') continue;

                    swTotal++;
                    var ss1 = null;
                    var ss2 = null;
                    if (swm.team1Score !== '' && swm.team1Score !== null && swm.team1Score !== undefined && !isNaN(Number(swm.team1Score))) {
                        ss1 = Number(swm.team1Score);
                    } else if (swm.team1 && swm.team1.score !== '' && swm.team1.score !== null && !isNaN(Number(swm.team1.score))) {
                        ss1 = Number(swm.team1.score);
                    }
                    if (swm.team2Score !== '' && swm.team2Score !== null && swm.team2Score !== undefined && !isNaN(Number(swm.team2Score))) {
                        ss2 = Number(swm.team2Score);
                    } else if (swm.team2 && swm.team2.score !== '' && swm.team2.score !== null && !isNaN(Number(swm.team2.score))) {
                        ss2 = Number(swm.team2.score);
                    }

                    var isMatchDone = ss1 !== null && ss2 !== null && ss1 !== ss2;
                    if (!isMatchDone && (swm.status === 'COMPLETED' || swm.status === 'DONE') && ss1 !== null && ss2 !== null) {
                        isMatchDone = true;
                    }
                    if (isMatchDone) swDone++;
                }
                return (swTotal > 0 && swDone === swTotal);
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

                // Primary signal: checkAndTriggerStage2Cut already sets tourma_stage1_completed_ when cut is done
                try {
                    if (localStorage.getItem('tourma_stage1_completed_' + tid) === 'true') return true;
                } catch(e) {}

                // Secondary signal: stage2 teams have been created with correct count
                try {
                    var s2Raw = localStorage.getItem('tourma_stage2_teams_' + tid);
                    if (s2Raw) {
                        var s2Teams = JSON.parse(s2Raw);
                        if (Array.isArray(s2Teams) && s2Teams.length >= cutTarget) return true;
                    }
                } catch(e) {}

                // Tertiary signal: all scheduled matches in matchesMap have winners
                var mKeys = Object.keys(matchesMap);
                var hasMatches = false;
                var allMatchesDone = true;
                for (var mIdx = 0; mIdx < mKeys.length; mIdx++) {
                    var mat = matchesMap[mKeys[mIdx]];
                    if (mat && (mat.matchId !== undefined || mat.id !== undefined)) {
                        hasMatches = true;
                        if (!mat.winnerId || (mat.winnerId !== 'team1' && mat.winnerId !== 'team2')) {
                            allMatchesDone = false;
                            break;
                        }
                    }
                }
                if (hasMatches && allMatchesDone) return true;

                return false;
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

                var mainEl = document.querySelector('main.container');
                var controlBar = document.querySelector(
                    '.group-stage-control-bar, .single-elimination-control-bar, .rr-control-bar, .de-control-bar, .swiss-control-bar'
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

        /**
         * Handle Confirm End Stage: Lock and immediately transition to Stage 2
         */
        confirmStageEnd: function () {
            var tid = this.tournamentId;
            if (!tid) return;

            try {
                // 1. Set Lock flag
                localStorage.setItem('tourma_stage1_locked_' + tid, 'true');
                localStorage.setItem('tourma_stage1_completed_' + tid, 'true');

                // 2. Trigger Stage 2 cut generation across format engines
                if (window.TourmaGroupStage && typeof window.TourmaGroupStage.checkAndTriggerStage2Cut === 'function') {
                    window.TourmaGroupStage.checkAndTriggerStage2Cut();
                }
                if (window.SingleEliminationEngine && typeof window.SingleEliminationEngine.checkAndTriggerStage2Cut === 'function') {
                    window.SingleEliminationEngine.checkAndTriggerStage2Cut();
                }
                if (window.TourmaDoubleElimination && typeof window.TourmaDoubleElimination.checkAndTriggerStage2Cut === 'function') {
                    window.TourmaDoubleElimination.checkAndTriggerStage2Cut();
                }
                if (window.TourmaSwiss && typeof window.TourmaSwiss.checkSwissStageCompletion === 'function') {
                    window.TourmaSwiss.checkSwissStageCompletion();
                }

                // 3. Resolve Stage 2 URL
                var s2Url = this.resolveStage2Url(tid);

                // 4. Redirect immediately to Stage 2
                window.location.href = s2Url;
            } catch (e) {
                console.error('[StageEndPopup] confirmStageEnd error:', e);
            }
        },

        /**
         * Navigate directly to Stage 2
         */
        goToStage2: function () {
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
                var multiCfgRaw = localStorage.getItem('tourma_multi_config_' + tid);
                if (multiCfgRaw) {
                    var mCfg = JSON.parse(multiCfgRaw);
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
                            '⚠️ Lưu ý: Nếu thay đổi kết quả làm đổi các đội đi tiếp, nhánh đấu Vòng 2 có thể sẽ được tính toán lại.' +
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

            try {
                localStorage.setItem('tourma_stage1_locked_' + this.tournamentId, 'false');

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

                this.applyLockToUI(false);
            } catch (e) {
                console.error('[StageEndPopup] confirmUnlock error:', e);
            }
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
            } else {
                document.body.classList.remove('stage1-is-locked');
            }

            // Quick mode buttons
            var qBtns = document.querySelectorAll('#singleBtnQuickMode, #deBtnQuickMode, .btn-quick-mode-toggle');
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

            var randomBtns = document.querySelectorAll('.btn-random-scores, .btn-random-round, .btn-random-group, .btn-auto-score');
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

    window.StageEndPopup = StageEndPopup;

})(window);
