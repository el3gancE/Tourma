/**
 * ============================================================================
 * TOURMA - FINAL STAGE POPUP CONTROLLER (final-stage-popup.js)
 * Standalone module to manage tournament conclusion confirmation and champion announcement.
 * Supports Single Elimination (SE), Double Elimination (DE), Round Robin (RR), and Swiss.
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
                if (name === undefined || name === null) return false;
                var trimmed = String(name).trim();
                if (!trimmed || trimmed === 'BYE' || trimmed === 'TBD' || trimmed === '?') return false;
                if (trimmed.startsWith('W #') || trimmed.startsWith('L #') || trimmed.startsWith('W#') || trimmed.startsWith('L#')) return false;
                if (trimmed.startsWith('Winner ') || trimmed.startsWith('Loser ')) return false;
                if (trimmed === 'Winner UB' || trimmed === 'Winner LB' || trimmed === 'Loser UB' || trimmed === 'Loser LB') return false;
                return true;
            };

            var normFmt = (format || '').toUpperCase().trim();

            // 1. SINGLE ELIMINATION
            if (normFmt === 'SINGLE_ELIMINATION' || normFmt === 'SE' || normFmt === 'SINGLE') {
                var finalMatch = null;
                for (var i = 0; i < keys.length; i++) {
                    var m = matchesMap[keys[i]];
                    if (m && (m.isGrandFinal || m.roundTitle === 'Finals' || m.roundTitle === 'Final' || m.roundTitle === 'Chung Kết' || m.roundName === 'Final' || m.roundName === 'Chung Kết' || m.id === 'M_FINAL')) {
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
                    var t1Name = (typeof t1 === 'object' && t1) ? (t1.name || '') : String(t1 || '');
                    var t2Name = (typeof t2 === 'object' && t2) ? (t2.name || '') : String(t2 || '');

                    if (!isRealTeam(t1Name) || !isRealTeam(t2Name)) {
                        return null;
                    }

                    var s1 = (t1.score !== '' && t1.score !== null && t1.score !== undefined && !isNaN(Number(t1.score))) ? Number(t1.score) : null;
                    var s2 = (t2.score !== '' && t2.score !== null && t2.score !== undefined && !isNaN(Number(t2.score))) ? Number(t2.score) : null;

                    if (s1 !== null && s2 !== null && s1 !== s2) {
                        if (s1 > s2) return isRealTeam(t1Name) ? t1Name : null;
                        if (s2 > s1) return isRealTeam(t2Name) ? t2Name : null;
                    }

                    if (finalMatch.winnerId) {
                        var wid = String(finalMatch.winnerId).trim();
                        if ((wid === 'team1' || wid === '1' || wid === 'SLOT_1') && isRealTeam(t1Name)) return t1Name;
                        if ((wid === 'team2' || wid === '2' || wid === 'SLOT_2') && isRealTeam(t2Name)) return t2Name;
                        if (t1.id && wid === String(t1.id) && isRealTeam(t1Name)) return t1Name;
                        if (t2.id && wid === String(t2.id) && isRealTeam(t2Name)) return t2Name;
                    }

                    if (finalMatch.winner && finalMatch.winner.name && isRealTeam(finalMatch.winner.name)) {
                        return finalMatch.winner.name;
                    }
                }
                return null;
            }

            // 2. DOUBLE ELIMINATION
            if (normFmt === 'DOUBLE_ELIMINATION' || normFmt === 'DE' || normFmt === 'DOUBLE') {
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
                            return null;
                        }
                        if (gfReset) {
                            return null;
                        }
                        return gft2Name;
                    }
                }
                return null;
            }

            // 3. ROUND ROBIN
            if (normFmt === 'ROUND_ROBIN' || normFmt === 'RR') {
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

                    if (rm.status === 'COMPLETED' || rm.status === 'FINISHED' || rm.status === 'done' || (rs1 !== null && rs2 !== null)) {
                        completedMatchesCount++;
                    }
                }

                if (totalMatchesCount > 0 && completedMatchesCount === totalMatchesCount) {
                    if (window.TourmaRoundRobinAlgorithm && typeof window.TourmaRoundRobinAlgorithm.calculateStandings === 'function') {
                        var standings = window.TourmaRoundRobinAlgorithm.calculateStandings(teamsList, matchesMap, config);
                        if (standings && standings.length > 0 && standings[0].team) {
                            return standings[0].team;
                        }
                    }
                }
                return null;
            }

            // 4. SWISS SYSTEM (Single Stage Championship)
            if (normFmt === 'SWISS' || normFmt === 'SWISS_LITE') {
                if (window.TourmaSwissAlgorithm && typeof window.TourmaSwissAlgorithm.calculateStandings === 'function') {
                    var swStandings = window.TourmaSwissAlgorithm.calculateStandings(teamsList, matchesMap);
                    if (swStandings && swStandings.length > 0) {
                        var top1 = swStandings[0];
                        if (top1 && top1.wins >= 3) {
                            return top1.name;
                        }
                    }
                }
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
         * Injected inside <main> after the control bar.
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
                        '</div>' +
                    '</div>';

                var mainEl = document.querySelector('main.container, main.has-sidebar, main');
                var controlBar = document.querySelector(
                    '.tournament-navbar-control-bar, .single-elimination-control-bar, .rr-control-bar, .de-control-bar, .swiss-control-bar, .group-stage-control-bar'
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
        },

        /**
         * Direct programmatic display of champion announcement popup
         */
        show: function (championName, tournamentId, onLockCallback) {
            if (!championName) return;
            this.tournamentId = tournamentId || this.tournamentId || window.TourmaTournamentId || 'demo';
            this.championName = championName;
            if (onLockCallback) this.onLockCallback = onLockCallback;
            this.ensureDOM();

            var banner = document.getElementById('finalStagePopupBanner');
            var textEl = document.getElementById('finalStagePopupText');
            var confirmBtn = document.getElementById('finalStageConfirmBtn');
            var unlockBtn = document.getElementById('finalStageUnlockBtn');
            if (!banner || !textEl) return;

            this.isLocked = this.isTournamentLocked(this.tournamentId);

            if (this.isLocked) {
                if (typeof this.onLockCallback === 'function') {
                    this.onLockCallback(true);
                }
                textEl.innerHTML = 
                    '<div class="final-stage-title-line">Giải đấu đã kết thúc</div>' +
                    '<div class="final-stage-champion-line">Nhà vô địch: <span class="final-stage-champion-name">' + this.championName + '</span></div>';
                if (confirmBtn) confirmBtn.style.display = 'none';
                if (unlockBtn) unlockBtn.style.display = 'inline-block';
                banner.classList.add('is-locked');
                banner.style.display = 'flex';
            } else {
                textEl.innerHTML = '<div class="final-stage-title-line">Khi bạn xác nhận hoàn thành giải đấu, bạn sẽ không thể chỉnh sửa kết quả</div>';
                if (confirmBtn) confirmBtn.style.display = 'inline-block';
                if (unlockBtn) unlockBtn.style.display = 'none';
                banner.classList.remove('is-locked');
                banner.style.display = 'flex';
            }
        },

        /**
         * Check tournament status and render top banner accordingly
         */
        checkAndRender: function (tournamentId, format, matchesMap, teamsList, config, onLockCallback) {
            this.tournamentId = tournamentId || this.tournamentId || window.TourmaTournamentId || 'demo';
            this.format = format;
            this.onLockCallback = onLockCallback;
            this.ensureDOM();

            var banner = document.getElementById('finalStagePopupBanner');
            var textEl = document.getElementById('finalStagePopupText');
            var confirmBtn = document.getElementById('finalStageConfirmBtn');
            var unlockBtn = document.getElementById('finalStageUnlockBtn');
            if (!banner || !textEl) return;

            this.championName = this.checkChampion(format, matchesMap, teamsList, config);
            this.isLocked = this.isTournamentLocked(this.tournamentId);

            if (this.isLocked) {
                if (!this.championName) {
                    // Stale lock from previous stage or reset -> auto unlock
                    this.isLocked = false;
                    try { localStorage.removeItem('tourma_final_locked_' + this.tournamentId); } catch(e) {}
                    if (typeof this.onLockCallback === 'function') {
                        this.onLockCallback(false);
                    }
                    if (window.TourmaRoundControls && typeof window.TourmaRoundControls.updateButtonsState === 'function') {
                        window.TourmaRoundControls.updateButtonsState(null);
                    }
                    banner.style.display = 'none';
                } else {
                    if (typeof this.onLockCallback === 'function') {
                        this.onLockCallback(true);
                    }
                    if (window.TourmaRoundControls && typeof window.TourmaRoundControls.updateButtonsState === 'function') {
                        window.TourmaRoundControls.updateButtonsState(null);
                    }
                    textEl.innerHTML = 
                        '<div class="final-stage-title-line">Giải đấu đã kết thúc</div>' +
                        '<div class="final-stage-champion-line">Nhà vô địch: <span class="final-stage-champion-name">' + this.championName + '</span></div>';
                    if (confirmBtn) confirmBtn.style.display = 'none';
                    if (unlockBtn) unlockBtn.style.display = 'inline-block';
                    banner.classList.add('is-locked');
                    banner.style.display = 'flex';
                    return;
                }
            }

            // Tournament is NOT locked yet
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.updateButtonsState === 'function') {
                window.TourmaRoundControls.updateButtonsState(null);
            }

            if (this.championName) {
                // Step 1: Prompt for locking confirmation
                textEl.innerHTML = '<div class="final-stage-title-line">Khi bạn xác nhận hoàn thành giải đấu, bạn sẽ không thể chỉnh sửa kết quả</div>';
                if (confirmBtn) confirmBtn.style.display = 'inline-block';
                if (unlockBtn) unlockBtn.style.display = 'none';
                banner.classList.remove('is-locked');
                banner.style.display = 'flex';
            } else {
                banner.style.display = 'none';
            }
        },

        /**
         * User confirms conclusion -> Lock tournament & transition to Step 2
         */
        confirmConclusion: function () {
            if (!this.tournamentId) return;

            try {
                var rootPath = window.location.pathname.substring(0, window.location.pathname.indexOf('/', 1));
                if (!rootPath || rootPath === '/common') rootPath = '';
                var ctx = window.TourmaContextPath || rootPath || '';
                fetch(ctx + '/api/match-update', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                    body: 'action=finishTournament&tournamentId=' + encodeURIComponent(this.tournamentId) + '&championName=' + encodeURIComponent(this.championName || '')
                }).catch(function(err) { console.error('Error saving tournament finish to DB:', err); });
            } catch (e) {}

            this.isLocked = true;

            try {
                localStorage.setItem('tourma_final_locked_' + this.tournamentId, 'true');
            } catch (e) {}

            if (typeof this.onLockCallback === 'function') {
                this.onLockCallback(true);
            }
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.updateButtonsState === 'function') {
                window.TourmaRoundControls.updateButtonsState(null);
            }

            var textEl = document.getElementById('finalStagePopupText');
            var confirmBtn = document.getElementById('finalStageConfirmBtn');
            var unlockBtn = document.getElementById('finalStageUnlockBtn');

            if (textEl) {
                textEl.innerHTML = 
                    '<div class="final-stage-title-line">Giải đấu đã kết thúc</div>' +
                    '<div class="final-stage-champion-line">Nhà vô địch: <span class="final-stage-champion-name">' + (this.championName || '') + '</span></div>';
            }
            if (confirmBtn) confirmBtn.style.display = 'none';
            if (unlockBtn) unlockBtn.style.display = 'inline-block';
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
                var rootPath = window.location.pathname.substring(0, window.location.pathname.indexOf('/', 1));
                if (!rootPath || rootPath === '/common') rootPath = '';
                var ctx = window.TourmaContextPath || rootPath || '';
                fetch(ctx + '/api/match-update', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                    body: 'action=unlockTournament&tournamentId=' + encodeURIComponent(this.tournamentId)
                }).catch(function(err) { console.error('Error unlocking tournament in DB:', err); });
            } catch (e) {}

            try {
                localStorage.removeItem('tourma_final_locked_' + this.tournamentId);
            } catch (e) {}

            this.isLocked = false;

            if (typeof this.onLockCallback === 'function') {
                this.onLockCallback(false);
            }
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.updateButtonsState === 'function') {
                window.TourmaRoundControls.updateButtonsState(null);
            }

            var textEl = document.getElementById('finalStagePopupText');
            var confirmBtn = document.getElementById('finalStageConfirmBtn');
            var unlockBtn = document.getElementById('finalStageUnlockBtn');

            if (textEl) {
                textEl.innerHTML = '<div class="final-stage-title-line">Khi bạn xác nhận hoàn thành giải đấu, bạn sẽ không thể chỉnh sửa kết quả</div>';
            }
            if (confirmBtn) confirmBtn.style.display = 'inline-block';
            if (unlockBtn) unlockBtn.style.display = 'none';
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
        },

        /**
         * Prompt user when attempting to edit a locked tournament
         */
        promptUnlock: function () {
            var banner = document.getElementById('finalStagePopupBanner');
            if (banner) {
                banner.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            }

            // 2. Remove any existing toast
            var oldToast = document.getElementById('tourmaLockToast');
            if (oldToast) oldToast.remove();

            // 3. Create interactive sleek Toast
            var toast = document.createElement('div');
            toast.id = 'tourmaLockToast';
            toast.className = 'tourma-lock-toast';
            toast.innerHTML = 
                '<i class="fa-solid fa-lock tourma-lock-toast-icon"></i>' +
                '<div class="tourma-lock-toast-body">' +
                    '<div class="tourma-lock-toast-title">Giải đấu đã kết thúc & đang khóa</div>' +
                    '<div class="tourma-lock-toast-desc">Bấm "Mở khóa" trên banner để chỉnh sửa lại tỉ số.</div>' +
                '</div>' +
                '<button type="button" class="tourma-lock-toast-btn" onclick="if(window.FinalStagePopup){window.FinalStagePopup.unlockTournament();}var t=document.getElementById(\'tourmaLockToast\');if(t)t.remove();">Mở khóa</button>';

            document.body.appendChild(toast);

            // Auto dismiss after 4.5 seconds
            setTimeout(function () {
                if (toast && toast.parentNode) {
                    toast.style.opacity = '0';
                    toast.style.transition = 'opacity 0.3s ease';
                    setTimeout(function () {
                        if (toast && toast.parentNode) toast.remove();
                    }, 300);
                }
            }, 4500);
        }
    };

    window.FinalStagePopup = FinalStagePopup;
    window.TourmaFinalStagePopup = FinalStagePopup;

})(window);
