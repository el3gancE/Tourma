/**
 * ============================================================================
 * TOURMA - FINAL STAGE POPUP CONTROLLER (final-stage-popup.js)
 * Standalone module to manage tournament conclusion confirmation and champion announcement.
 * ============================================================================
 */

(function (window) {
    'use strict';

    var FinalStagePopup = {
        tournamentId: null,
        format: null,
        championName: '',
        onLockCallback: null,
        isLocked: false,

        /**
         * Determine if the tournament is completed and get the champion team name
         */
        checkChampion: function (format, matchesMap, teamsList, config) {
            if (!matchesMap || typeof matchesMap !== 'object') return null;
            var keys = Object.keys(matchesMap);
            if (keys.length === 0) return null;

            // 1. SINGLE ELIMINATION
            if (format === 'SINGLE_ELIMINATION') {
                var finalMatch = null;
                for (var i = 0; i < keys.length; i++) {
                    var m = matchesMap[keys[i]];
                    if (m && (m.isGrandFinal || m.roundTitle === 'Finals' || m.roundTitle === 'Chung Kết' || m.id === 'M_FINAL')) {
                        finalMatch = m;
                        break;
                    }
                }
                // Fallback: match with max roundNumber that is not 3rd place
                if (!finalMatch) {
                    var maxRound = 0;
                    for (var j = 0; j < keys.length; j++) {
                        var matchObj = matchesMap[keys[j]];
                        if (matchObj && matchObj.roundNumber > maxRound && !matchObj.isThirdPlace) {
                            maxRound = matchObj.roundNumber;
                            finalMatch = matchObj;
                        }
                    }
                }

                if (finalMatch) {
                    var isCompleted = (finalMatch.status === 'COMPLETED' || finalMatch.status === 'done');
                    var t1 = finalMatch.team1 || {};
                    var t2 = finalMatch.team2 || {};
                    var s1 = (t1.score !== '' && t1.score !== null && !isNaN(Number(t1.score))) ? Number(t1.score) : null;
                    var s2 = (t2.score !== '' && t2.score !== null && !isNaN(Number(t2.score))) ? Number(t2.score) : null;

                    if (isCompleted || (s1 !== null && s2 !== null && s1 !== s2)) {
                        if (finalMatch.winner && finalMatch.winner.name) {
                            return finalMatch.winner.name;
                        }
                        if (s1 !== null && s2 !== null) {
                            return (s1 > s2) ? t1.name : t2.name;
                        }
                    }
                }
                return null;
            }

            // 2. DOUBLE ELIMINATION
            if (format === 'DOUBLE_ELIMINATION') {
                var gfReset = null;
                var gfMain = null;

                for (var k = 0; k < keys.length; k++) {
                    var deM = matchesMap[keys[k]];
                    if (!deM) continue;
                    var bType = (deM.bracketType || '').toUpperCase();
                    var isGf = (bType === 'GRAND_FINAL' || bType === 'GF' || deM.isGrandFinal || String(deM.matchId).startsWith('M_GF'));
                    if (isGf) {
                        if (deM.isResetMatch || deM.isGrandFinalReset || bType === 'GF_RESET') {
                            gfReset = deM;
                        } else {
                            gfMain = deM;
                        }
                    }
                }

                // If reset match is unlocked (LB winner won GF1)
                if (gfReset && gfReset.isUnlocked) {
                    var rT1 = gfReset.team1 || {};
                    var rT2 = gfReset.team2 || {};
                    var rS1 = (rT1.score !== '' && rT1.score !== null && !isNaN(Number(rT1.score))) ? Number(rT1.score) : null;
                    var rS2 = (rT2.score !== '' && rT2.score !== null && !isNaN(Number(rT2.score))) ? Number(rT2.score) : null;
                    var rDone = (gfReset.status === 'COMPLETED' || gfReset.status === 'done');

                    if (rDone || gfReset.winnerId || (rS1 !== null && rS2 !== null && rS1 !== rS2)) {
                        if (gfReset.winnerId === 'team1') return rT1.name;
                        if (gfReset.winnerId === 'team2') return rT2.name;
                        if (rS1 !== null && rS2 !== null && rS1 !== rS2) {
                            return (rS1 > rS2) ? rT1.name : rT2.name;
                        }
                    }
                    return null; // Waiting for reset match to finish
                }

                // Check Main Grand Finals match (GF1)
                if (gfMain) {
                    var gfT1 = gfMain.team1 || {};
                    var gfT2 = gfMain.team2 || {};
                    var gfS1 = (gfT1.score !== '' && gfT1.score !== null && !isNaN(Number(gfT1.score))) ? Number(gfT1.score) : null;
                    var gfS2 = (gfT2.score !== '' && gfT2.score !== null && !isNaN(Number(gfT2.score))) ? Number(gfT2.score) : null;
                    var gfDone = (gfMain.status === 'COMPLETED' || gfMain.status === 'done');

                    var isT1Win = (gfMain.winnerId === 'team1' || (gfS1 !== null && gfS2 !== null && gfS1 > gfS2));
                    var isT2Win = (gfMain.winnerId === 'team2' || (gfS1 !== null && gfS2 !== null && gfS2 > gfS1));

                    if (isT1Win) {
                        // Winner UB wins GF1 -> Tournament complete, Champion is Winner UB!
                        return gfT1.name;
                    } else if (isT2Win) {
                        // Winner LB wins GF1 -> If reset match exists & unlocked, must wait for GF2
                        if (gfReset && gfReset.isUnlocked) {
                            return null;
                        }
                        return gfT2.name;
                    }
                }
                return null;
            }

            // 3. ROUND ROBIN
            if (format === 'ROUND_ROBIN') {
                // All matches must be finished / scored (excluding BYE)
                var totalMatchesCount = 0;
                var completedMatchesCount = 0;

                for (var r = 0; r < keys.length; r++) {
                    var rm = matchesMap[keys[r]];
                    if (!rm) continue;
                    var rt1 = rm.team1 ? rm.team1.name : '';
                    var rt2 = rm.team2 ? rm.team2.name : '';

                    if (!rt1 || !rt2 || rt1 === 'BYE' || rt2 === 'BYE') continue;

                    totalMatchesCount++;
                    var rs1 = (rm.team1 && rm.team1.score !== '' && rm.team1.score !== null && !isNaN(Number(rm.team1.score))) ? Number(rm.team1.score) : null;
                    var rs2 = (rm.team2 && rm.team2.score !== '' && rm.team2.score !== null && !isNaN(Number(rm.team2.score))) ? Number(rm.team2.score) : null;

                    if (rm.status === 'COMPLETED' || rm.status === 'done' || (rs1 !== null && rs2 !== null)) {
                        completedMatchesCount++;
                    }
                }

                if (totalMatchesCount > 0 && completedMatchesCount === totalMatchesCount) {
                    if (window.TourmaRoundRobinAlgorithm) {
                        var standings = window.TourmaRoundRobinAlgorithm.calculateStandings(teamsList, matchesMap, config);
                        if (standings && standings.length > 0 && standings[0].team) {
                            return standings[0].team;
                        }
                    }
                }
                return null;
            }

            return null;
        },

        /**
         * Check if tournament is locked in storage
         */
        isTournamentLocked: function (tournamentId) {
            if (!tournamentId) return false;
            try {
                return localStorage.getItem('tourma_final_locked_' + tournamentId) === 'true';
            } catch (e) {
                return false;
            }
        },

        /**
         * Ensure banner element exists in DOM.
         * Inject inside <main> after the control-bar so it appears
         * between the title/actions row and the round tabs / bracket.
         */
        ensureDOM: function () {
            var banner = document.getElementById('finalStagePopupBanner');
            if (!banner) {
                var div = document.createElement('div');
                div.id = 'finalStagePopupBanner';
                div.className = 'final-stage-popup-banner';
                div.style.display = 'none';
                div.innerHTML =
                    '<div class="final-stage-popup-content">' +
                        '<span id="finalStagePopupText" class="final-stage-popup-text"></span>' +
                        '<div id="finalStagePopupActions" class="final-stage-popup-actions">' +
                            '<button type="button" id="finalStageConfirmBtn" class="final-stage-confirm-btn" onclick="window.FinalStagePopup.confirmConclusion()">Xác nhận</button>' +
                            '<button type="button" id="finalStageUnlockBtn" class="final-stage-unlock-btn" style="display: none;" onclick="window.FinalStagePopup.unlockTournament()">Mở khóa</button>' +
                            '<button type="button" id="finalStageCloseBtn" class="final-stage-close-btn" style="display: none;" onclick="window.FinalStagePopup.closeBanner()">Đóng</button>' +
                        '</div>' +
                    '</div>';

                // Try to inject after the control bar inside <main>.
                // Selectors cover all 3 formats (SE, DE, RR).
                var mainEl = document.querySelector('main.container');
                var controlBar = document.querySelector(
                    '.single-elimination-control-bar, .rr-control-bar, .de-control-bar'
                );

                if (mainEl && controlBar && controlBar.parentNode === mainEl) {
                    // Insert right after the control bar
                    var next = controlBar.nextSibling;
                    if (next) {
                        mainEl.insertBefore(div, next);
                    } else {
                        mainEl.appendChild(div);
                    }
                } else if (mainEl) {
                    // Fallback: prepend to main
                    mainEl.insertBefore(div, mainEl.firstChild);
                } else {
                    // Ultimate fallback: body prepend
                    document.body.insertBefore(div, document.body.firstChild);
                }
            }
        },

        /**
         * Check tournament status and render top banner accordingly
         */
        checkAndRender: function (tournamentId, format, matchesMap, teamsList, config, onLockCallback) {
            this.tournamentId = tournamentId;
            this.format = format;
            this.onLockCallback = onLockCallback;
            this.ensureDOM();

            var banner = document.getElementById('finalStagePopupBanner');
            var textEl = document.getElementById('finalStagePopupText');
            var confirmBtn = document.getElementById('finalStageConfirmBtn');
            var unlockBtn = document.getElementById('finalStageUnlockBtn');
            var closeBtn = document.getElementById('finalStageCloseBtn');
            if (!banner || !textEl) return;

            this.championName = this.checkChampion(format, matchesMap, teamsList, config);
            this.isLocked = this.isTournamentLocked(tournamentId);

            if (this.isLocked) {
                // If tournament is already locked, notify caller to disable edit modes
                if (typeof this.onLockCallback === 'function') {
                    this.onLockCallback(true);
                }

                if (this.championName) {
                    textEl.innerHTML = 
                        '<div class="final-stage-title-line">Giải đấu đã kết thúc</div>' +
                        '<div class="final-stage-champion-line">Nhà vô địch: <span class="final-stage-champion-name">' + this.championName + '</span></div>';
                    if (confirmBtn) confirmBtn.style.display = 'none';
                    if (unlockBtn) unlockBtn.style.display = 'inline-block';
                    if (closeBtn) closeBtn.style.display = 'inline-block';
                    banner.classList.add('is-locked');
                    banner.style.display = 'flex';
                } else {
                    banner.style.display = 'none';
                }
                return;
            }

            // Tournament is NOT locked yet
            if (this.championName) {
                // Step 1: Final match completed -> Prompt for locking confirmation
                textEl.innerHTML = '<div class="final-stage-title-line">Khi bạn xác nhận hoàn thành giải đấu, bạn sẽ không thể chỉnh sửa kết quả</div>';
                if (confirmBtn) confirmBtn.style.display = 'inline-block';
                if (unlockBtn) unlockBtn.style.display = 'none';
                if (closeBtn) closeBtn.style.display = 'none';
                banner.classList.remove('is-locked');
                banner.style.display = 'flex';
            } else {
                // Tournament not complete yet -> hide banner
                banner.style.display = 'none';
            }
        },

        /**
         * User confirms conclusion -> Lock tournament & transition to Step 2
         */
        confirmConclusion: function () {
            if (!this.tournamentId) return;

            try {
                localStorage.setItem('tourma_final_locked_' + this.tournamentId, 'true');
            } catch (e) {}

            this.isLocked = true;

            if (typeof this.onLockCallback === 'function') {
                this.onLockCallback(true);
            }

            var textEl = document.getElementById('finalStagePopupText');
            var confirmBtn = document.getElementById('finalStageConfirmBtn');
            var unlockBtn = document.getElementById('finalStageUnlockBtn');
            var closeBtn = document.getElementById('finalStageCloseBtn');

            if (textEl) {
                textEl.innerHTML = 
                    '<div class="final-stage-title-line">Giải đấu đã kết thúc</div>' +
                    '<div class="final-stage-champion-line">Nhà vô địch: <span class="final-stage-champion-name">' + (this.championName || '') + '</span></div>';
            }
            if (confirmBtn) confirmBtn.style.display = 'none';
            if (unlockBtn) unlockBtn.style.display = 'inline-block';
            if (closeBtn) closeBtn.style.display = 'inline-block';
            var bannerEl = document.getElementById('finalStagePopupBanner');
            if (bannerEl) {
                bannerEl.classList.add('is-locked');
                bannerEl.style.display = 'flex';
            }
        },

        /**
         * User unlocks tournament -> Re-enable editing & transition back to Step 1
         */
        unlockTournament: function () {
            if (!this.tournamentId) return;

            try {
                localStorage.removeItem('tourma_final_locked_' + this.tournamentId);
            } catch (e) {}

            this.isLocked = false;

            if (typeof this.onLockCallback === 'function') {
                this.onLockCallback(false);
            }

            var textEl = document.getElementById('finalStagePopupText');
            var confirmBtn = document.getElementById('finalStageConfirmBtn');
            var unlockBtn = document.getElementById('finalStageUnlockBtn');
            var closeBtn = document.getElementById('finalStageCloseBtn');

            if (textEl) {
                textEl.innerHTML = '<div class="final-stage-title-line">Khi bạn xác nhận hoàn thành giải đấu, bạn sẽ không thể chỉnh sửa kết quả</div>';
            }
            if (confirmBtn) confirmBtn.style.display = 'inline-block';
            if (unlockBtn) unlockBtn.style.display = 'none';
            if (closeBtn) closeBtn.style.display = 'none';
            var bannerEl = document.getElementById('finalStagePopupBanner');
            if (bannerEl) {
                bannerEl.classList.remove('is-locked');
                bannerEl.style.display = 'flex';
            }
        },

        /**
         * Close top banner
         */
        closeBanner: function () {
            var banner = document.getElementById('finalStagePopupBanner');
            if (banner) {
                banner.style.display = 'none';
            }
        }
    };

    window.FinalStagePopup = FinalStagePopup;

})(window);
