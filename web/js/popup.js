/**
 * TOURMA - MATCH SCORE EDIT POPUP MODAL ALGORITHM (popup.js)
 * Manages score editing, winner determination logic (Team 1 / Draw / Team 2), and custom event broadcasting.
 */

(function () {
    'use strict';

    window.TourmaScoreModal = {
        activeMatchId: null,
        selectedWinner: 'team1',
        allowDraw: false,
        onSaveCallback: null,

        /**
         * Check if tournament editing is currently locked (Stage 1 locked or Final Stage champion locked)
         */
        isLocked: function (tournamentId) {
            var tid = tournamentId;
            if (!tid) {
                try {
                    var urlParams = new URLSearchParams(window.location.search);
                    tid = urlParams.get('id');
                } catch(e) {}
            }
            if (!tid && window.FinalStagePopup) tid = window.FinalStagePopup.tournamentId;
            if (!tid && window.StageEndPopup) tid = window.StageEndPopup.tournamentId;
            if (!tid && window.SingleEliminationEngine) tid = window.SingleEliminationEngine.tournamentId;
            if (!tid && window.TourmaDoubleElimination) tid = window.TourmaDoubleElimination.tournamentId;
            if (!tid && window.TourmaRoundRobin) tid = window.TourmaRoundRobin.tournamentId;
            if (!tid && window.TourmaSwiss) tid = window.TourmaSwiss.tournamentId;
            if (!tid && window.TourmaGroupStage) tid = window.TourmaGroupStage.tournamentId;

            if (!tid) return false;

            // 1. Final champion lock (always applies regardless of tournament type)
            try {
                if (localStorage.getItem('tourma_final_locked_' + tid) === 'true') return true;
            } catch (e) {}
            if (window.FinalStagePopup && window.FinalStagePopup.isLocked) return true;

            // 2. Stage 1 lock — ONLY for Multi-Stage tournaments, ONLY when currently in Stage 1
            var stageParam = null;
            try {
                stageParam = new URLSearchParams(window.location.search).get('stage');
            } catch(e) {}
            var isStage2 = (stageParam === '2');

            if (!isStage2) {
                // Check if this is actually a Multi-Stage tournament
                var isMultiStage = false;
                try {
                    isMultiStage = (
                        localStorage.getItem('tourma_type_' + tid) === 'MULTI_STAGE' ||
                        !!localStorage.getItem('tourma_multi_config_' + tid)
                    );
                } catch(e) {}

                if (isMultiStage) {
                    try {
                        if (localStorage.getItem('tourma_stage1_locked_' + tid) === 'true') return true;
                    } catch (e) {}
                }
            }

            return false;
        },

        /**
         * Open Score Editing Modal with initial match data
         * @param {Object} matchData - { matchId, roundName, team1Name, team1Seed, team1Score, team2Name, team2Seed, team2Score, winnerId, status, allowDraw }
         * @param {Function} callback - Optional callback on save
         */
        open: function (matchData, callback) {
            if (!matchData) return;

            // Check lock directly from localStorage & memory for guaranteed correctness
            if (this.isLocked(matchData.tournamentId)) {
                return;
            }

            this.activeMatchId = matchData.matchId || matchData.id || '1';
            this.onSaveCallback = callback || null;
            this.allowDraw = matchData.allowDraw === true || matchData.allowDraw === 'true';

            // Populate Modal Title Header
            var modalTitle = document.getElementById('modalMatchTitle');
            if (modalTitle) {
                modalTitle.innerText = matchData.roundName || ('Trận #' + this.activeMatchId);
            }

            var hiddenMatchId = document.getElementById('modalMatchIdInput');
            if (hiddenMatchId) hiddenMatchId.value = this.activeMatchId;

            // Clean seed numbers
            var cleanSeed1 = (matchData.team1Seed || '1').toString().replace('#', '');
            var cleanSeed2 = (matchData.team2Seed || '2').toString().replace('#', '');

            var t1NameStr = matchData.team1Name || 'Đội 1';
            var t2NameStr = matchData.team2Name || 'Đội 2';

            // Populate Team 1 & Team 2 Rows
            var t1Seed = document.getElementById('modalTeam1Seed');
            if (t1Seed) t1Seed.innerText = cleanSeed1;
            var t1Name = document.getElementById('modalTeam1Name');
            if (t1Name) t1Name.innerText = t1NameStr;
            
            var t1Score = document.getElementById('modalTeam1Score');
            var s1Val = (matchData.team1Score !== undefined && matchData.team1Score !== null && matchData.team1Score !== '') ? matchData.team1Score : '';
            if (t1Score) t1Score.value = s1Val;

            var t2Seed = document.getElementById('modalTeam2Seed');
            if (t2Seed) t2Seed.innerText = cleanSeed2;
            var t2Name = document.getElementById('modalTeam2Name');
            if (t2Name) t2Name.innerText = t2NameStr;

            var t2Score = document.getElementById('modalTeam2Score');
            var s2Val = (matchData.team2Score !== undefined && matchData.team2Score !== null && matchData.team2Score !== '') ? matchData.team2Score : '';
            if (t2Score) t2Score.value = s2Val;

            // Populate Winner Segmented Button Labels
            var btnT1 = document.getElementById('btnWinnerT1');
            if (btnT1) btnT1.innerText = t1NameStr;

            var btnT2 = document.getElementById('btnWinnerT2');
            if (btnT2) btnT2.innerText = t2NameStr;

            // Show / Hide "Hòa" Draw Segment Button based on allowDraw
            var btnDraw = document.getElementById('btnWinnerDraw');
            if (btnDraw) {
                btnDraw.style.display = this.allowDraw ? 'block' : 'none';
            }

            // Hide any previous error message
            var err = document.getElementById('modalScoreError');
            if (err) err.style.display = 'none';

            // Determine Initial Winner Selection
            var initWinner = matchData.winnerId || matchData.winner;
            if (initWinner === 'team1') {
                this.selectWinner('team1');
            } else if (initWinner === 'team2') {
                this.selectWinner('team2');
            } else if (initWinner === 'draw' && this.allowDraw) {
                this.selectWinner('draw');
            } else if (s1Val !== '' || s2Val !== '') {
                var s1 = (s1Val !== '') ? Number(s1Val) : 0;
                var s2 = (s2Val !== '') ? Number(s2Val) : 0;
                if (s1 > s2) {
                    this.selectWinner('team1');
                } else if (s2 > s1) {
                    this.selectWinner('team2');
                } else if (s1 === s2 && this.allowDraw) {
                    this.selectWinner('draw');
                } else {
                    this.selectWinner(null);
                }
            } else {
                // Not played yet (both blank) -> Turn off all lights
                this.selectWinner(null);
            }

            // Show Backdrop
            var backdrop = document.getElementById('tourmaScoreModalBackdrop');
            if (backdrop) {
                backdrop.classList.add('show');
                document.body.style.overflow = 'hidden';
            }
        },

        /**
         * Close Score Editing Modal
         */
        close: function () {
            var backdrop = document.getElementById('tourmaScoreModalBackdrop');
            if (backdrop) {
                backdrop.classList.remove('show');
                document.body.style.overflow = '';
            }
            var err = document.getElementById('modalScoreError');
            if (err) err.style.display = 'none';
            this.activeMatchId = null;
        },

        /**
         * Select Winner Segment Option ('team1' | 'draw' | 'team2' | null)
         */
        selectWinner: function (winnerType) {
            this.selectedWinner = winnerType;

            var btnT1 = document.getElementById('btnWinnerT1');
            var btnDraw = document.getElementById('btnWinnerDraw');
            var btnT2 = document.getElementById('btnWinnerT2');

            if (btnT1) btnT1.classList.toggle('active', winnerType === 'team1');
            if (btnDraw) btnDraw.classList.toggle('active', winnerType === 'draw');
            if (btnT2) btnT2.classList.toggle('active', winnerType === 'team2');

            // Hide error banner once user selects a winner
            if (winnerType) {
                var err = document.getElementById('modalScoreError');
                if (err) err.style.display = 'none';
            }
        },

        /**
         * Auto-determine winner based on score inputs (Blank counts as 0)
         */
        autoDetermineWinner: function () {
            var val1 = document.getElementById('modalTeam1Score').value;
            var val2 = document.getElementById('modalTeam2Score').value;

            // If both are completely blank, turn off selection
            if (val1 === '' && val2 === '') {
                this.selectWinner(null);
                return;
            }

            // Blank input defaults to 0
            var s1 = (val1 !== '') ? Number(val1) : 0;
            var s2 = (val2 !== '') ? Number(val2) : 0;

            if (s1 > s2) {
                this.selectWinner('team1');
            } else if (s2 > s1) {
                this.selectWinner('team2');
            } else if (s1 === s2) {
                if (this.allowDraw) {
                    this.selectWinner('draw');
                } else {
                    // Turn off selection when scores are tied in knockout match
                    this.selectWinner(null);
                }
            }
        },

        /**
         * Randomize match result, immediately save and exit popup
         */
        randomizeAndSave: function () {
            if (!this.activeMatchId) return;

            var winScoreInput = document.getElementById('modalRandomWinScore');
            var rawVal = winScoreInput ? winScoreInput.value : '';

            var scoreResult = (window.TourmaRandomService)
                ? window.TourmaRandomService.generateMatchScore(rawVal)
                : { team1Score: 5, team2Score: 3, winner: 'team1', isT1Winner: true };

            var resultPayload = {
                matchId: this.activeMatchId,
                team1Score: scoreResult.team1Score,
                team2Score: scoreResult.team2Score,
                winner: scoreResult.winner,
                status: 'COMPLETED'
            };

            // Dispatch Custom DOM Event for Bracket and List update
            var updateEvent = new CustomEvent('tourmaMatchUpdated', {
                detail: resultPayload,
                bubbles: true
            });
            document.dispatchEvent(updateEvent);

            if (typeof this.onSaveCallback === 'function') {
                this.onSaveCallback(resultPayload);
            }

            this.close();
        },

        /**
         * Save Match Result Algorithm (Automatically marks match as COMPLETED)
         */
        save: function () {
            if (!this.activeMatchId) return;

            // In elimination / knockout matches (no draw allowed), must specify a winner
            if (!this.allowDraw) {
                if (!this.selectedWinner || (this.selectedWinner !== 'team1' && this.selectedWinner !== 'team2')) {
                    var err = document.getElementById('modalScoreError');
                    if (err) {
                        err.style.display = 'flex';
                    } else {
                        alert('phải xác định 1 đội thắng');
                    }
                    return;
                }
            } else {
                if (!this.selectedWinner) {
                    var val1 = document.getElementById('modalTeam1Score').value;
                    var val2 = document.getElementById('modalTeam2Score').value;
                    if (val1 !== '' && val2 !== '' && Number(val1) === Number(val2)) {
                        this.selectedWinner = 'draw';
                    } else {
                        var err = document.getElementById('modalScoreError');
                        if (err) {
                            err.style.display = 'flex';
                        } else {
                            alert('phải xác định 1 đội thắng');
                        }
                        return;
                    }
                }
            }

            var val1 = document.getElementById('modalTeam1Score').value;
            var val2 = document.getElementById('modalTeam2Score').value;

            var s1 = val1 !== '' ? Number(val1) : 0;
            var s2 = val2 !== '' ? Number(val2) : 0;

            var resultPayload = {
                matchId: this.activeMatchId,
                team1Score: s1,
                team2Score: s2,
                winner: this.selectedWinner,
                status: 'COMPLETED'
            };

            // Dispatch Custom DOM Event for Bracket and List update
            var updateEvent = new CustomEvent('tourmaMatchUpdated', {
                detail: resultPayload,
                bubbles: true
            });
            document.dispatchEvent(updateEvent);

            if (typeof this.onSaveCallback === 'function') {
                this.onSaveCallback(resultPayload);
            }

            this.close();
        }
    };

    // Keyboard Listener for ESC key
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') {
            window.TourmaScoreModal.close();
        }
    });

})();
