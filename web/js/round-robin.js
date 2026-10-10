/**
 * ============================================================================
 * TOURMA - ROUND ROBIN STAGE ENGINE (round-robin.js)
 * 100% Database-Driven with real-time sync via /api/match-update.
 * Supports:
 * - Circle / Berger round generation & DB match loading
 * - Live match score editing with DRAW support (Hòa)
 * - Prominent Round Tabs & Section Headers with Batch Random/Reset Controls
 * - Multi-stage cut target & Single-stage championship integration
 * ============================================================================
 */

(function () {
    'use strict';

    window.TourmaRoundRobin = {
        tournamentId: null,
        contextPath: '',
        currentStage: 1,
        cutTarget: 0,
        teamsList: [],
        rounds: [],
        matchesMap: {},
        activeRoundFilter: 'ALL',
        config: { winPoints: 3, drawPoints: 1, lossPoints: 0, legsCount: 1 },

        /**
         * Main Entry Point
         */
        init: function (tourneyId, dbMatches, preloadedTeams, stage, cutTarget) {
            this.tournamentId = tourneyId || window.TourmaTournamentId || 'demo';
            this.contextPath = window.TourmaContextPath || '';
            this.currentStage = (stage === 2 || stage === '2') ? 2 : 1;
            this.cutTarget = cutTarget || 0;

            if (!this.cutTarget || this.cutTarget <= 1) {
                var rawCut = localStorage.getItem('tourma_advance_count_' + this.tournamentId) ||
                             localStorage.getItem('tourma_cut_target_' + this.tournamentId);
                if (rawCut) this.cutTarget = parseInt(rawCut, 10);
            }

            // 1. Load Teams List
            var storageKeyTeams = (this.currentStage === 2) ? ('tourma_stage2_teams_' + this.tournamentId) : ('tourma_teams_' + this.tournamentId);
            var teams = (preloadedTeams && preloadedTeams.length > 0) ? preloadedTeams : null;
            if (!teams) {
                try {
                    teams = JSON.parse(localStorage.getItem(storageKeyTeams));
                } catch (e) {
                    teams = null;
                }
            }

            // Handle Stage Finish Alert (Stage 2 access check)
            if (this.currentStage === 2) {
                if (window.StageFinishAlert && typeof window.StageFinishAlert.checkAndRender === 'function') {
                    if (window.StageFinishAlert.checkAndRender(this.tournamentId, this.currentStage, document.getElementById('stageFinishAlertContainer') || document.getElementById('rrEmptyAlertContainer'))) {
                        var rBar = document.getElementById('rrRoundSelectorBar');
                        if (rBar) rBar.style.display = 'none';
                        var rFix = document.getElementById('rrFixturesContainer');
                        if (rFix) rFix.style.display = 'none';
                        return;
                    }
                }
            }

            if (!teams || teams.length < 2) {
                if (window.TourmaEmptyTeamAlert && typeof window.TourmaEmptyTeamAlert.checkAndRender === 'function') {
                    window.TourmaEmptyTeamAlert.checkAndRender(this.tournamentId, teams || [], document.getElementById('rrEmptyAlertContainer'));
                }
                var rBar = document.getElementById('rrRoundSelectorBar');
                if (rBar) rBar.style.display = 'none';
                var rFix = document.getElementById('rrFixturesContainer');
                if (rFix) rFix.style.display = 'none';
                return;
            }

            this.teamsList = teams.filter(function (t) {
                if (!t) return false;
                var n = (typeof t === 'object') ? (t.name || t.rawName || '') : String(t);
                return n !== 'BYE' && n.trim() !== '';
            }).slice(0, 24);

            // Update team count badge
            var countBadge = document.getElementById('tournamentTeamCountBadge');
            if (countBadge) {
                countBadge.innerText = this.teamsList.length + ' Đội';
            }

            // 2. Load Configuration
            var storageKeyConfig = 'tourma_rr_config_' + this.tournamentId;
            var cfg = null;
            if (this.currentStage === 2) {
                try {
                    var mCfg = JSON.parse(localStorage.getItem('tourma_multi_config_' + this.tournamentId));
                    if (mCfg && mCfg.stage2Config) cfg = mCfg.stage2Config;
                } catch (e) {}
            }
            if (!cfg) {
                try {
                    cfg = JSON.parse(localStorage.getItem(storageKeyConfig));
                } catch (e) {}
            }
            this.config = cfg || { winPoints: 3, drawPoints: 1, lossPoints: 0, legsCount: 1 };

            // 3. Load Matches State (From DB or Generated Algorithm)
            this.loadMatches(dbMatches);

            // 4. Render UI
            this.render();

            // 5. Setup Event Listeners
            this.setupListeners();

            // 6. Check Final Stage / Multi-stage status
            this.checkTournamentCompletion();
        },

        /**
         * Render Whole View
         */
        render: function () {
            this.renderRoundSelector();
            this.renderFixtures();
        },

        /**
         * Load Matches from DB array or Generate Schedule
         */
        loadMatches: function (dbMatches) {
            this.matchesMap = {};
            this.rounds = [];

            // A. If DB matches array exists and is populated
            if (dbMatches && Array.isArray(dbMatches) && dbMatches.length > 0) {
                var roundsMap = {};
                for (var i = 0; i < dbMatches.length; i++) {
                    var rawM = dbMatches[i];
                    var rNum = rawM.roundNumber || 1;
                    if (!roundsMap[rNum]) {
                        roundsMap[rNum] = {
                            roundNumber: rNum,
                            legNumber: rawM.legNumber || 1,
                            title: 'Vòng ' + rNum,
                            matches: []
                        };
                    }

                    var mId = rawM.matchId || rawM.id || ('M_' + this.tournamentId + '_RR_R' + rNum + '_' + (roundsMap[rNum].matches.length + 1));
                    var t1 = rawM.team1 || {};
                    var t2 = rawM.team2 || {};

                    var s1 = (typeof t1 === 'object' && t1.score !== undefined && t1.score !== null) ? String(t1.score) : '';
                    var s2 = (typeof t2 === 'object' && t2.score !== undefined && t2.score !== null) ? String(t2.score) : '';
                    var isDone = (rawM.status === 'FINISHED' || rawM.status === 'COMPLETED' || (s1 !== '' && s2 !== ''));

                    var matchObj = {
                        id: mId,
                        matchId: mId,
                        matchNumber: rawM.matchNumber || (i + 1),
                        roundNumber: rNum,
                        legNumber: rawM.legNumber || 1,
                        status: isDone ? 'COMPLETED' : 'SCHEDULED',
                        team1: {
                            name: (typeof t1 === 'object') ? (t1.name || '') : String(t1),
                            seed: (typeof t1 === 'object') ? (t1.seed || '') : '',
                            score: s1
                        },
                        team2: {
                            name: (typeof t2 === 'object') ? (t2.name || '') : String(t2),
                            seed: (typeof t2 === 'object') ? (t2.seed || '') : '',
                            score: s2
                        },
                        winnerId: rawM.winnerId || null,
                        isBye: rawM.isBye === true
                    };

                    this.matchesMap[mId] = matchObj;
                    this.matchesMap[matchObj.matchNumber] = matchObj;
                    roundsMap[rNum].matches.push(matchObj);
                }

                var rKeys = Object.keys(roundsMap).map(Number).sort(function (a, b) { return a - b; });
                for (var r = 0; r < rKeys.length; r++) {
                    this.rounds.push(roundsMap[rKeys[r]]);
                }
            } else {
                // B. Generate via Algorithm Engine
                if (window.TourmaRoundRobinAlgorithm && typeof window.TourmaRoundRobinAlgorithm.generateRoundRobin === 'function') {
                    var generated = window.TourmaRoundRobinAlgorithm.generateRoundRobin(this.teamsList, this.config);
                    this.rounds = generated.rounds || [];
                    this.matchesMap = generated.matchesMap || {};
                }
            }

            // Sync matches to LocalStorage
            this.persistLocalState();
        },

        /**
         * Hydrate model from server-returned batch matches array (after random or reset)
         */
        hydrateBracketModel: function (matchesData) {
            if (!Array.isArray(matchesData) || matchesData.length === 0) return;

            for (var i = 0; i < matchesData.length; i++) {
                var rawM = matchesData[i];
                var mId = rawM.matchId || rawM.id;
                var m = this.findMatch(mId) || this.findMatch(rawM.matchNumber);
                if (m) {
                    if (rawM.team1 && rawM.team1.score !== undefined && rawM.team1.score !== null) {
                        m.team1.score = String(rawM.team1.score);
                    } else if (rawM.status === 'READY' || rawM.status === 'PENDING') {
                        m.team1.score = '';
                    }

                    if (rawM.team2 && rawM.team2.score !== undefined && rawM.team2.score !== null) {
                        m.team2.score = String(rawM.team2.score);
                    } else if (rawM.status === 'READY' || rawM.status === 'PENDING') {
                        m.team2.score = '';
                    }

                    m.winnerId = rawM.winnerId || null;
                    var hasScores = (m.team1.score !== '' && m.team2.score !== '');
                    m.status = (rawM.status === 'FINISHED' || rawM.status === 'COMPLETED' || hasScores) ? 'COMPLETED' : 'SCHEDULED';
                }
            }

            this.persistLocalState();
        },

        /**
         * Persist in-memory state to LocalStorage
         */
        persistLocalState: function () {
            var storageKeyRR = (this.currentStage === 2) ? ('tourma_rr_matches_stage2_' + this.tournamentId) : ('tourma_rr_matches_' + this.tournamentId);
            var storageKeyUniversal = (this.currentStage === 2) ? ('tourma_matches_stage2_' + this.tournamentId) : ('tourma_matches_' + this.tournamentId);

            var payload = {
                tournamentId: this.tournamentId,
                stage: this.currentStage,
                teamsList: this.teamsList,
                rounds: this.rounds,
                matchesMap: this.matchesMap,
                config: this.config
            };

            try {
                localStorage.setItem(storageKeyRR, JSON.stringify(payload));
                localStorage.setItem(storageKeyUniversal, JSON.stringify(this.matchesMap));
                localStorage.setItem('tourma_format_' + this.tournamentId, 'ROUND_ROBIN');
            } catch (e) {}
        },

        /**
         * Find Match by String/Numeric ID
         */
        findMatch: function (matchId) {
            if (!matchId) return null;
            if (this.matchesMap[matchId]) return this.matchesMap[matchId];

            var keys = Object.keys(this.matchesMap);
            for (var i = 0; i < keys.length; i++) {
                var m = this.matchesMap[keys[i]];
                if (m && (m.id == matchId || m.matchId == matchId || m.matchNumber == matchId)) {
                    return m;
                }
            }
            return null;
        },

        /**
         * Render Horizontal Round Selector Bar (Pill Tabs)
         */
        renderRoundSelector: function () {
            var container = document.getElementById('rrRoundSelectorBar');
            if (!container) return;

            container.innerHTML = '';

            // Tab "Tất cả các vòng"
            var allBtn = document.createElement('button');
            allBtn.type = 'button';
            allBtn.className = 'rr-round-tab-btn' + (this.activeRoundFilter === 'ALL' ? ' active' : '');
            allBtn.innerText = 'Tất cả các vòng';
            var self = this;
            allBtn.addEventListener('click', function () {
                self.activeRoundFilter = 'ALL';
                self.renderRoundSelector();
                self.renderFixtures();
            });
            container.appendChild(allBtn);

            // Tabs for each Round
            for (var i = 0; i < this.rounds.length; i++) {
                var rd = this.rounds[i];
                var rNum = rd.roundNumber || (i + 1);
                var btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'rr-round-tab-btn' + (self.activeRoundFilter === rNum ? ' active' : '');
                btn.innerText = 'Vòng ' + rNum;
                (function (roundNum) {
                    btn.addEventListener('click', function () {
                        self.activeRoundFilter = roundNum;
                        self.renderRoundSelector();
                        self.renderFixtures();
                    });
                })(rNum);
                container.appendChild(btn);
            }
        },

        /**
         * Render All Fixtures Grid
         */
        renderFixtures: function () {
            var container = document.getElementById('rrFixturesContainer');
            if (!container) return;

            container.innerHTML = '';

            var self = this;
            var visibleRounds = this.rounds.filter(function (rd) {
                if (self.activeRoundFilter === 'ALL') return true;
                return (rd.roundNumber === self.activeRoundFilter);
            });

            if (visibleRounds.length === 0) {
                container.innerHTML = '<div style="text-align: center; color: #94a3b8; padding: 2rem 0; font-size: 0.9rem;">Chưa có trận đấu nào trong vòng này.</div>';
                return;
            }

            for (var i = 0; i < visibleRounds.length; i++) {
                var rd = visibleRounds[i];
                var rNum = rd.roundNumber || (i + 1);

                // Section Wrapper
                var sectionEl = document.createElement('div');
                sectionEl.className = 'rr-round-section list-round-section';
                sectionEl.setAttribute('data-round', String(rNum));
                sectionEl.style.marginBottom = '1.5rem';

                // Round Header Bar with Title on left & Batch Actions (Random/Reset) on right
                var headerEl = document.createElement('div');
                headerEl.className = 'match-list-round-header';

                var byeHtml = rd.byeTeam
                    ? ('<span class="rr-bye-badge"><i class="fa-solid fa-bed"></i> Nghỉ vòng này: <strong>' + rd.byeTeam + '</strong></span>')
                    : '';

                var actionsHtml = '';
                if (window.TourmaRoundControls && typeof window.TourmaRoundControls.renderListHeaderControlsHtml === 'function') {
                    actionsHtml = window.TourmaRoundControls.renderListHeaderControlsHtml(rNum, 'Vòng ' + rNum);
                }

                headerEl.innerHTML = '<div class="round-header-title"><i class="fa-solid fa-flag text-gold"></i> VÒNG ' + rNum + byeHtml + '</div>' + actionsHtml;

                sectionEl.appendChild(headerEl);

                // Matches Grid Container
                var gridEl = document.createElement('div');
                gridEl.className = 'rr-matches-grid';
                gridEl.style.display = 'flex';
                gridEl.style.flexDirection = 'column';
                gridEl.style.gap = '0.75rem';

                var matches = rd.matches || [];
                for (var m = 0; m < matches.length; m++) {
                    var matchData = matches[m];
                    matchData.tournamentId = self.tournamentId;

                    var cardNode = null;
                    if (window.TourmaMatchCard && typeof window.TourmaMatchCard.createCardElement === 'function') {
                        cardNode = window.TourmaMatchCard.createCardElement(matchData);
                    }

                    if (cardNode) {
                        (function (mObj) {
                            cardNode.addEventListener('click', function (e) {
                                self.openEditScoreModal(mObj);
                            });
                        })(matchData);

                        gridEl.appendChild(cardNode);
                    }
                }

                sectionEl.appendChild(gridEl);
                container.appendChild(sectionEl);
            }

            // Sync button states via TourmaRoundControls
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.updateButtonsState === 'function') {
                window.TourmaRoundControls.updateButtonsState(this);
            }
        },

        /**
         * Open Score Editing Modal for a match
         */
        openEditScoreModal: function (matchObj) {
            if (!matchObj) return;

            var self = this;
            var t1Name = (matchObj.team1 && matchObj.team1.name) ? matchObj.team1.name : 'Đội 1';
            var t2Name = (matchObj.team2 && matchObj.team2.name) ? matchObj.team2.name : 'Đội 2';

            if (t1Name === 'BYE' || t2Name === 'BYE') return;

            var modalData = {
                matchId: matchObj.matchId || matchObj.id,
                tournamentId: this.tournamentId,
                roundName: 'Vòng ' + matchObj.roundNumber + ' - Trận ' + matchObj.matchNumber,
                team1Name: t1Name,
                team1Seed: (matchObj.team1 && matchObj.team1.seed) ? matchObj.team1.seed : '',
                team1Score: (matchObj.team1 && matchObj.team1.score !== undefined) ? matchObj.team1.score : '',
                team2Name: t2Name,
                team2Seed: (matchObj.team2 && matchObj.team2.seed) ? matchObj.team2.seed : '',
                team2Score: (matchObj.team2 && matchObj.team2.score !== undefined) ? matchObj.team2.score : '',
                winnerId: matchObj.winnerId,
                status: matchObj.status,
                allowDraw: true // Round Robin allows DRAW
            };

            if (window.TourmaScoreModal && typeof window.TourmaScoreModal.open === 'function') {
                window.TourmaScoreModal.open(modalData, function (res) {
                    var s1 = (res.team1Score !== undefined && res.team1Score !== '') ? parseInt(res.team1Score, 10) : 0;
                    var s2 = (res.team2Score !== undefined && res.team2Score !== '') ? parseInt(res.team2Score, 10) : 0;
                    var p1 = (res.penalty1 !== undefined && res.penalty1 !== '') ? parseInt(res.penalty1, 10) : null;
                    var p2 = (res.penalty2 !== undefined && res.penalty2 !== '') ? parseInt(res.penalty2, 10) : null;
                    var wSlot = res.winner || '';

                    self.saveMatchScore(res.matchId, s1, s2, p1, p2, wSlot);
                });
            }
        },

        /**
         * Core Function: Save Match Score to Database via /api/match-update
         */
        saveMatchScore: function (matchId, score1, score2, penalty1, penalty2, winnerSlot) {
            var self = this;
            var m = this.findMatch(matchId);
            if (!m) return;

            var t1Name = (m.team1 && m.team1.name) ? m.team1.name : '';
            var t2Name = (m.team2 && m.team2.name) ? m.team2.name : '';

            var finalWinner = winnerSlot || '';
            if (finalWinner === 'draw' || finalWinner === 'none') finalWinner = '';

            var payload = {
                action: 'updateMatch',
                tournamentId: this.tournamentId,
                matchId: m.matchId || matchId,
                score1: (score1 !== undefined && score1 !== null && score1 !== '') ? score1 : 0,
                score2: (score2 !== undefined && score2 !== null && score2 !== '') ? score2 : 0,
                penalty1: (penalty1 !== undefined && penalty1 !== null) ? penalty1 : '',
                penalty2: (penalty2 !== undefined && penalty2 !== null) ? penalty2 : '',
                winner: finalWinner,
                winnerId: finalWinner,
                team1Name: t1Name,
                team2Name: t2Name,
                stage: this.currentStage
            };

            fetch((this.contextPath || '') + '/api/match-update', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                },
                body: new URLSearchParams(payload).toString()
            })
                .then(function (res) { return res.json(); })
                .then(function (data) {
                    if (data && data.status === 'success') {
                        // Update local match object
                        m.team1.score = String(payload.score1);
                        m.team2.score = String(payload.score2);
                        m.winnerId = (finalWinner !== '') ? finalWinner : null;
                        m.status = 'COMPLETED';

                        self.persistLocalState();
                        self.renderFixtures();
                        self.checkTournamentCompletion();
                    } else {
                        console.error('[TourmaRoundRobin] Error saving match score:', data);
                        alert('Lỗi lưu kết quả: ' + (data.message || 'Không rõ nguyên nhân'));
                    }
                })
                .catch(function (err) {
                    console.error('[TourmaRoundRobin] Error saving match score:', err);
                });
        },

        /**
         * Execute Batch Random for a Round
         */
        executeRandomRound: function (roundNumber) {
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.executeRandomRound === 'function') {
                window.TourmaRoundControls.executeRandomRound(this, roundNumber);
            }
        },

        /**
         * Execute Batch Reset for a Round
         */
        executeResetRound: function (roundNumber) {
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.executeResetRound === 'function') {
                window.TourmaRoundControls.executeResetRound(this, roundNumber);
            }
        },

        /**
         * Check Stage / Tournament Completion
         */
        checkTournamentCompletion: function () {
            if (this.currentStage === 1 && this.cutTarget && this.cutTarget > 1) {
                if (window.FinalStagePopup && typeof window.FinalStagePopup.closeBanner === 'function') {
                    window.FinalStagePopup.closeBanner();
                }
                if (window.StageEndPopup && typeof window.StageEndPopup.update === 'function') {
                    window.StageEndPopup.update(
                        this.tournamentId,
                        'ROUND_ROBIN',
                        this.matchesMap,
                        this.teamsList,
                        { isMultiStage: true, cutTarget: this.cutTarget },
                        null,
                        this.currentStage
                    );
                }
                return;
            }

            if (window.FinalStagePopup && typeof window.FinalStagePopup.checkAndRender === 'function') {
                window.FinalStagePopup.checkAndRender(
                    this.tournamentId,
                    'ROUND_ROBIN',
                    this.matchesMap,
                    this.teamsList,
                    this.config,
                    null
                );
            }
        },

        /**
         * Reset Modal Dialog Controls
         */
        openResetModal: function () {
            var tid = this.tournamentId || window.TourmaTournamentId || 'demo';
            if (window.TourmaScoreModal && typeof window.TourmaScoreModal.isLocked === 'function') {
                if (window.TourmaScoreModal.isLocked(tid)) {
                    if (window.FinalStagePopup && typeof window.FinalStagePopup.promptUnlock === 'function') {
                        window.FinalStagePopup.promptUnlock();
                    } else if (window.StageEndPopup && typeof window.StageEndPopup.promptUnlock === 'function') {
                        window.StageEndPopup.promptUnlock();
                    }
                    return;
                }
            } else if (window.FinalStagePopup && typeof window.FinalStagePopup.isTournamentLocked === 'function') {
                if (window.FinalStagePopup.isTournamentLocked(tid)) {
                    window.FinalStagePopup.promptUnlock();
                    return;
                }
            }

            var modal = document.getElementById('rrResetModalBackdrop');
            if (modal) {
                modal.style.display = 'flex';
                document.body.style.overflow = 'hidden';
            }
        },

        closeResetModal: function () {
            var modal = document.getElementById('rrResetModalBackdrop');
            if (modal) {
                modal.style.display = 'none';
            }
            document.body.style.overflow = '';
        },

        confirmResetTournament: function () {
            var self = this;
            var tid = this.tournamentId || window.TourmaTournamentId || 'demo';

            // Clean localStorage keys
            try {
                localStorage.removeItem('tourma_rr_matches_' + tid);
                localStorage.removeItem('tourma_matches_' + tid);
                localStorage.removeItem('tourma_champion_' + tid);
                localStorage.removeItem('tourma_final_champion_' + tid);
                localStorage.removeItem('tourma_final_locked_' + tid);
            } catch (e) {}

            fetch((this.contextPath || '') + '/api/match-update', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                },
                body: new URLSearchParams({
                    action: 'resetBracket',
                    tournamentId: tid,
                    stage: this.currentStage || 1
                }).toString()
            })
                .then(function (res) { return res.json(); })
                .then(function (data) {
                    self.closeResetModal();
                    window.location.reload();
                })
                .catch(function (err) {
                    console.error('[TourmaRoundRobin] Reset error:', err);
                    self.closeResetModal();
                    window.location.reload();
                });
        },

        /**
         * Setup Custom Listeners
         */
        setupListeners: function () {
            var self = this;
            document.addEventListener('tourmaMatchUpdated', function (e) {
                if (e.detail && e.detail.matchId) {
                    var mid = e.detail.matchId;
                    var s1 = (e.detail.team1Score !== undefined) ? parseInt(e.detail.team1Score, 10) : 0;
                    var s2 = (e.detail.team2Score !== undefined) ? parseInt(e.detail.team2Score, 10) : 0;
                    var w = e.detail.winner || '';
                    self.saveMatchScore(mid, s1, s2, null, null, w);
                }
            });
        }
    };

})();
