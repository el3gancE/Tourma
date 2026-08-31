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
            if (config && (config.isCutStage || (config.cutTarget && config.cutTarget > 1))) {
                return null;
            }
            if (!matchesMap || typeof matchesMap !== 'object') return null;
            var keys = Object.keys(matchesMap);
            if (keys.length === 0) return null;

            // Helper to check if a team name is a real confirmed participant (not a placeholder or BYE)
            var isRealTeam = function (name) {
                if (!name || typeof name !== 'string') return false;
                var trimmed = name.trim();
                if (!trimmed || trimmed === 'BYE' || trimmed === 'TBD') return false;
                if (trimmed.startsWith('W #') || trimmed.startsWith('L #')) return false;
                if (trimmed.startsWith('Winner ') || trimmed === 'Winner UB' || trimmed === 'Winner LB') return false;
                return true;
            };

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
                    var t1 = finalMatch.team1 || {};
                    var t2 = finalMatch.team2 || {};
                    var t1Name = t1.name || '';
                    var t2Name = t2.name || '';

                    // Both finalists must be confirmed real teams!
                    if (!isRealTeam(t1Name) || !isRealTeam(t2Name)) {
                        return null;
                    }

                    var s1 = (t1.score !== '' && t1.score !== null && !isNaN(Number(t1.score))) ? Number(t1.score) : null;
                    var s2 = (t2.score !== '' && t2.score !== null && !isNaN(Number(t2.score))) ? Number(t2.score) : null;

                    // Match MUST have valid non-null scores entered!
                    if (s1 === null || s2 === null || s1 === s2) {
                        return null;
                    }

                    if (s1 > s2) {
                        return isRealTeam(t1Name) ? t1Name : null;
                    }
                    if (s2 > s1) {
                        return isRealTeam(t2Name) ? t2Name : null;
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
                    var rt1Name = rT1.name || '';
                    var rt2Name = rT2.name || '';

                    if (isRealTeam(rt1Name) && isRealTeam(rt2Name)) {
                        var rS1 = (rT1.score !== '' && rT1.score !== null && !isNaN(Number(rT1.score))) ? Number(rT1.score) : null;
                        var rS2 = (rT2.score !== '' && rT2.score !== null && !isNaN(Number(rT2.score))) ? Number(rT2.score) : null;

                        if (rS1 !== null && rS2 !== null && rS1 !== rS2) {
                            if (rS1 > rS2) return rt1Name;
                            if (rS2 > rS1) return rt2Name;
                        }
                    }
                    return null; // Waiting for reset match to finish
                }

                // Check Main Grand Finals match (GF1)
                if (gfMain) {
                    var gfT1 = gfMain.team1 || {};
                    var gfT2 = gfMain.team2 || {};
                    var gft1Name = gfT1.name || '';
                    var gft2Name = gfT2.name || '';

                    // Both Grand Finalists must be confirmed real teams!
                    if (!isRealTeam(gft1Name) || !isRealTeam(gft2Name)) {
                        return null;
                    }

                    var gfS1 = (gfT1.score !== '' && gfT1.score !== null && !isNaN(Number(gfT1.score))) ? Number(gfT1.score) : null;
                    var gfS2 = (gfT2.score !== '' && gfT2.score !== null && !isNaN(Number(gfT2.score))) ? Number(gfT2.score) : null;

                    if (gfS1 === null || gfS2 === null || gfS1 === gfS2) {
                        return null; // Not played yet!
                    }

                    // UB Winner (team1) won GF1 -> Champion directly!
                    if (gfS1 > gfS2) {
                        return gft1Name;
                    }
                    // LB Winner (team2) won GF1 -> Triggered Reset match
                    if (gfS2 > gfS1) {
                        if (gfReset && gfReset.isUnlocked) {
                            return null; // Must wait for reset match!
                        }
                        return gft2Name;
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
                if (!this.championName) {
                    // Stale lock from another stage or reset! Auto-unlock so editing is enabled.
                    this.isLocked = false;
                    try { localStorage.removeItem('tourma_final_locked_' + tournamentId); } catch(e) {}
                    if (typeof this.onLockCallback === 'function') {
                        this.onLockCallback(false);
                    }
                    banner.style.display = 'none';
                } else {
                    // If tournament is already locked and champion exists, notify caller to disable edit modes
                    if (typeof this.onLockCallback === 'function') {
                        this.onLockCallback(true);
                    }
                    textEl.innerHTML = 
                        '<div class="final-stage-title-line">Giải đấu đã kết thúc</div>' +
                        '<div class="final-stage-champion-line">Nhà vô địch: <span class="final-stage-champion-name">' + this.championName + '</span></div>';
                    if (confirmBtn) confirmBtn.style.display = 'none';
                    if (unlockBtn) unlockBtn.style.display = 'inline-block';
                    if (closeBtn) closeBtn.style.display = 'inline-block';
                    banner.classList.add('is-locked');
                    banner.style.display = 'flex';
                    return;
                }
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
                if (this.championName) {
                    localStorage.setItem('tourma_champion_' + this.tournamentId, this.championName);
                }
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
