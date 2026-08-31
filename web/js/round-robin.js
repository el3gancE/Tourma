/**
 * ============================================================================
 * TOURMA - ROUND ROBIN FIXTURES ENGINE (round-robin.js)
 * Standalone Round Robin Match List by Rounds, Round Filtering Tabs,
 * Score Entry Modal Integration, Randomize / Reset Rounds, and AJAX Sync.
 * (No Bracket View, No Quick Mode).
 * ============================================================================
 */

(function () {
    'use strict';

    window.TourmaRoundRobin = {
        tournamentId: null,
        teamsList: [],
        matchesMap: {},
        roundsList: [],
        roundRandomInputs: {},
        activeRoundFilter: 'all', // 'all' or roundNumber (1, 2, ...)

        /**
         * Initialize Round Robin Fixtures Page
         * @param {string} tourneyId - Tournament ID
         * @param {Array} dbMatches - Preloaded matches (optional)
         * @param {Array} preloadedTeams - Preloaded teams from DB
         */
        init: function (tourneyId, dbMatches, preloadedTeams, stage, cutTarget) {
            this.tournamentId = tourneyId || 'demo';
            this.currentStage = (stage === 2 || stage === '2') ? 2 : 1;
            this.cutTarget = cutTarget || 0;

            if (!this.cutTarget || this.cutTarget <= 1) {
                try {
                    var mCfg = JSON.parse(localStorage.getItem('tourma_multi_config_' + this.tournamentId));
                    if (mCfg && mCfg.stage1Config) {
                        var cfgAdv = mCfg.stage1Config.advanceCount || mCfg.stage1Config.totalAdvanceCount || 0;
                        if (cfgAdv > 1) {
                            this.cutTarget = cfgAdv;
                            localStorage.setItem('tourma_advance_count_' + this.tournamentId, cfgAdv);
                            localStorage.setItem('tourma_cut_target_' + this.tournamentId, cfgAdv);
                        }
                    }
                } catch(e) {}
            }
            if (!this.cutTarget || this.cutTarget <= 1) {
                var rawCut = localStorage.getItem('tourma_advance_count_' + this.tournamentId) ||
                             localStorage.getItem('tourma_cut_target_' + this.tournamentId);
                if (rawCut) this.cutTarget = parseInt(rawCut, 10);
            }

            // Update Advancing Badge (Pill style)
            var advBadge = document.getElementById('rrAdvancingBadge');
            var advText = document.getElementById('rrAdvancingText');
            if (advBadge && advText) {
                if (this.currentStage === 1 && this.cutTarget && this.cutTarget > 1) {
                    advBadge.style.display = 'inline-flex';
                    advText.innerText = this.cutTarget + ' Đội đi tiếp';
                } else {
                    advBadge.style.display = 'none';
                }
            }

            // Load saved round score inputs from localStorage
            try {
                this.roundRandomInputs = JSON.parse(localStorage.getItem('tourma_rr_round_inputs_' + this.tournamentId)) || {};
            } catch (e) {
                this.roundRandomInputs = {};
            }

            // Load custom Round Robin score rules & legs count config
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
                } catch (e) {
                    cfg = null;
                }
            }
            this.config = cfg || { winPoints: 3, drawPoints: 1, lossPoints: 0, legsCount: 1 };

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

            if (!teams) {
                teams = [];
            }
            this.teamsList = teams.slice(0, 24);

            // Update team count badge in top toolbar
            var countBadge = document.getElementById('tournamentTeamCountBadge');
            if (countBadge) {
                countBadge.innerText = this.teamsList.length + ' Đội';
            }

            // 2. Generate or Restore Matches Data
            this.loadOrGenerateSchedule(dbMatches);

            // 3. Render Round Filter Tabs & Fixtures
            this.renderRoundSelectorTabs();
            this.renderFixtures();

            // 4. Attach Match Modal Update Event Listeners
            this.attachEventListeners();

            // 5. Check Final Stage conclusion & render top banner if complete
            this.checkFinalStage();
        },

        /**
         * Load from localStorage or Generate fresh Round Robin schedule
         */
        loadOrGenerateSchedule: function (dbMatches) {
            var storageKeyMatches = (this.currentStage === 2) ? ('tourma_rr_matches_stage2_' + this.tournamentId) : ('tourma_rr_matches_' + this.tournamentId);
            var savedData = null;

            try {
                savedData = JSON.parse(localStorage.getItem(storageKeyMatches));
            } catch (e) {
                savedData = null;
            }

            // Helper to get string name
            var getTeamName = function(t) {
                if (!t) return '';
                if (typeof t === 'object') return t.name || t.rawName || '';
                return String(t);
            };

            // Check if saved teams match current teams (same team count and same team names)
            var savedTeamsList = savedData ? (savedData.teamsList || []) : [];
            var isTeamsCountSame = (savedTeamsList.length === this.teamsList.length);
            var isTeamsSameSet = false;
            if (isTeamsCountSame && this.teamsList.length > 0) {
                var sSet = savedTeamsList.map(getTeamName).sort();
                var cSet = this.teamsList.map(getTeamName).sort();
                isTeamsSameSet = true;
                for (var ti = 0; ti < sSet.length; ti++) {
                    if (sSet[ti] !== cSet[ti]) {
                        isTeamsSameSet = false;
                        break;
                    }
                }
            }

            // Check if saved config matches current config (legsCount)
            var savedConfig = savedData ? (savedData.config || {}) : {};
            var isConfigSame = (Number(savedConfig.legsCount || 1) === Number(this.config.legsCount || 1));

            // Validate: saved rounds must have matching teams, matching legsCount, and at least 1 round with ≥1 real match
            var isValidSaved = false;
            if (savedData && isTeamsSameSet && isConfigSame && savedData.rounds && savedData.rounds.length > 0) {
                for (var rv = 0; rv < savedData.rounds.length; rv++) {
                    var rdv = savedData.rounds[rv];
                    if (rdv && rdv.matches && rdv.matches.length > 0) {
                        // Check that at least one match is not a BYE match
                        for (var mv = 0; mv < rdv.matches.length; mv++) {
                            var mav = rdv.matches[mv];
                            if (mav && mav.team1 && mav.team2 &&
                                mav.team1.name !== 'BYE' && mav.team2.name !== 'BYE') {
                                isValidSaved = true;
                                break;
                            }
                        }
                    }
                    if (isValidSaved) break;
                }
            }

            if (isValidSaved) {
                this.roundsList = savedData.rounds;
                this.matchesMap = savedData.matchesMap || {};

                // Re-link, merge, and sanitize matchesMap and roundsList
                for (var r = 0; r < this.roundsList.length; r++) {
                    var rd = this.roundsList[r];
                    if (rd && rd.matches) {
                        for (var mi = 0; mi < rd.matches.length; mi++) {
                            var rMatch = rd.matches[mi];
                            var mid = rMatch.matchId || rMatch.id;
                            if (this.matchesMap[mid]) {
                                if ((!this.matchesMap[mid].team1.score || this.matchesMap[mid].team1.score === '') && (rMatch.team1 && rMatch.team1.score !== '')) {
                                    this.matchesMap[mid] = rMatch;
                                } else {
                                    rd.matches[mi] = this.matchesMap[mid];
                                }
                            } else {
                                this.matchesMap[mid] = rMatch;
                            }

                            // Ensure team names are always strings (not objects)
                            var mObj = this.matchesMap[mid] || rMatch;
                            if (mObj.team1) mObj.team1.name = getTeamName(mObj.team1.name);
                            if (mObj.team2) mObj.team2.name = getTeamName(mObj.team2.name);
                        }
                    }
                }
            } else if (this.teamsList && this.teamsList.length > 0 && window.TourmaRoundRobinAlgorithm) {
                // Clear stale cache and regenerate fresh schedule
                try {
                    localStorage.removeItem(storageKeyMatches);
                    if (this.currentStage === 1) localStorage.removeItem('tourma_matches_' + this.tournamentId);
                    else localStorage.removeItem('tourma_matches_stage2_' + this.tournamentId);
                } catch (e) {}

                var generated = window.TourmaRoundRobinAlgorithm.generateRoundRobin(this.teamsList, this.config);
                this.roundsList = generated.rounds;
                this.matchesMap = generated.matchesMap;

                if (this.currentStage === 1 && dbMatches && dbMatches.length > 0) {
                    for (var i = 0; i < dbMatches.length; i++) {
                        var dbm = dbMatches[i];
                        if (this.matchesMap[dbm.id]) {
                            var localM = this.matchesMap[dbm.id];
                            localM.status = dbm.status;
                            localM.team1.score = (dbm.team1Score != null) ? String(dbm.team1Score) : '';
                            localM.team2.score = (dbm.team2Score != null) ? String(dbm.team2Score) : '';
                            if (dbm.winnerId) {
                                localM.winnerId = (dbm.winnerId === localM.team1.name) ? 'team1' : 'team2';
                            }
                        }
                    }
                }
                this.persistLocal();
            }
        },

        /**
         * Persist matches to LocalStorage
         */
        persistLocal: function () {
            var storageKeyRR = (this.currentStage === 2) ? ('tourma_rr_matches_stage2_' + this.tournamentId) : ('tourma_rr_matches_' + this.tournamentId);
            var storageKeyMatches = (this.currentStage === 2) ? ('tourma_matches_stage2_' + this.tournamentId) : ('tourma_matches_' + this.tournamentId);
            try {
                var payload = {
                    rounds: this.roundsList,
                    matchesMap: this.matchesMap,
                    teamsList: this.teamsList,
                    config: this.config
                };
                localStorage.setItem(storageKeyRR, JSON.stringify(payload));
                localStorage.setItem(storageKeyMatches, JSON.stringify(this.matchesMap));
                if (this.currentStage === 1 && this.teamsList && this.teamsList.length > 0) {
                    localStorage.setItem('tourma_teams_' + this.tournamentId, JSON.stringify(this.teamsList));
                }
            } catch (e) {}
            this.checkAndTriggerStage2Cut();
            this.checkFinalStage();
        },

        checkAndTriggerStage2Cut: function () {
            if (this.currentStage !== 1 || !this.cutTarget || this.cutTarget <= 1) return;
            if (!this.matchesMap || !window.TourmaRoundRobinAlgorithm) return;

            // Check if all non-BYE matches are completed
            var allDone = true;
            var keys = Object.keys(this.matchesMap);
            if (keys.length === 0) return;

            for (var i = 0; i < keys.length; i++) {
                var m = this.matchesMap[keys[i]];
                if (!m) continue;
                var t1Name = (m.team1 && typeof m.team1 === 'object') ? m.team1.name : (m.team1 || '');
                var t2Name = (m.team2 && typeof m.team2 === 'object') ? m.team2.name : (m.team2 || '');
                var isBye = (t1Name === 'BYE' || t2Name === 'BYE' || m.isBye);
                if (isBye) continue;

                if (m.status !== 'COMPLETED' && m.status !== 'done') {
                    allDone = false;
                    break;
                }
            }

            if (!allDone) return;

            // Calculate live Standings to determine top K qualifiers
            var standings = window.TourmaRoundRobinAlgorithm.calculateStandings(
                this.teamsList,
                this.matchesMap,
                this.config
            );

            if (!standings || standings.length < this.cutTarget) return;

            var topKStandings = standings.slice(0, this.cutTarget);
            var self = this;

            var finalStage2Teams = topKStandings.map(function (row, idx) {
                var tName = row.team || row.teamName || row.name || '';
                var originalSeed = row.seed;
                if (!originalSeed) {
                    for (var t = 0; t < self.teamsList.length; t++) {
                        var tm = self.teamsList[t];
                        var tmName = (typeof tm === 'object') ? (tm.name || tm.rawName) : tm;
                        if (tmName === tName) {
                            originalSeed = (typeof tm === 'object' && tm.seed !== undefined && tm.seed !== null) ? tm.seed : (t + 1);
                            break;
                        }
                    }
                }
                return {
                    name: tName,
                    rawName: tName,
                    seed: originalSeed || (idx + 1),
                    rank: idx + 1
                };
            });

            localStorage.setItem('tourma_stage2_teams_' + this.tournamentId, JSON.stringify(finalStage2Teams));

            var multiCfg = null;
            try {
                multiCfg = JSON.parse(localStorage.getItem('tourma_multi_config_' + this.tournamentId)) || {};
            } catch (e) {
                multiCfg = {};
            }

            var s2Format = multiCfg.stage2Format || 'SINGLE_ELIMINATION';
            if (s2Format === 'SINGLE_ELIMINATION' && window.TourmaBracketAlgorithm) {
                var seStage2Bracket = window.TourmaBracketAlgorithm.generateSingleElimination(finalStage2Teams, 0);
                if (seStage2Bracket) {
                    localStorage.setItem('tourma_bracket_stage2_' + this.tournamentId, JSON.stringify(seStage2Bracket));
                    localStorage.setItem('tourma_matches_stage2_' + this.tournamentId, JSON.stringify(seStage2Bracket.matchesMap || {}));
                }
            } else if (s2Format === 'DOUBLE_ELIMINATION') {
                var doubleEngine = window.TourmaDoubleElimAlgorithm || window.TourmaDoubleEliminationAlgorithm;
                if (doubleEngine) {
                    var deBracket = doubleEngine.generateDoubleElimination(finalStage2Teams, 0);
                    if (deBracket) {
                        localStorage.setItem('tourma_de_matches_stage2_' + this.tournamentId, JSON.stringify(deBracket));
                        localStorage.setItem('tourma_matches_stage2_' + this.tournamentId, JSON.stringify(deBracket.matchesMap || {}));
                    }
                }
            } else if (s2Format === 'ROUND_ROBIN' && window.TourmaRoundRobinAlgorithm) {
                var rrBracket = window.TourmaRoundRobinAlgorithm.generateRoundRobin(finalStage2Teams, multiCfg.stage2Config);
                if (rrBracket) {
                    localStorage.setItem('tourma_rr_matches_stage2_' + this.tournamentId, JSON.stringify(rrBracket));
                    localStorage.setItem('tourma_matches_stage2_' + this.tournamentId, JSON.stringify(rrBracket.matchesMap || {}));
                }
            }

            multiCfg.stage2MatchesCreated = true;
            localStorage.setItem('tourma_multi_config_' + this.tournamentId, JSON.stringify(multiCfg));
            localStorage.setItem('tourma_stage1_completed_' + this.tournamentId, 'true');
        },

        checkFinalStage: function () {
            if (this.currentStage === 1 && this.cutTarget && this.cutTarget > 1) return;
            if (window.FinalStagePopup) {
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
         * Render Horizontal Round Selector Tabs
         */
        renderRoundSelectorTabs: function () {
            var tabsContainer = document.getElementById('rrRoundSelectorBar');
            if (!tabsContainer || !this.roundsList) return;

            tabsContainer.innerHTML = '';
            var self = this;

            // 'All Rounds' Tab
            var allTab = document.createElement('button');
            allTab.type = 'button';
            allTab.className = 'rr-round-tab-btn' + (this.activeRoundFilter === 'all' ? ' active' : '');
            allTab.innerText = 'Tất cả các vòng (' + this.roundsList.length + ')';
            allTab.onclick = function () {
                self.activeRoundFilter = 'all';
                self.renderRoundSelectorTabs();
                self.renderFixtures();
            };
            tabsContainer.appendChild(allTab);

            // Individual Round Tabs
            for (var r = 0; r < this.roundsList.length; r++) {
                var rd = this.roundsList[r];
                var tab = document.createElement('button');
                tab.type = 'button';
                tab.className = 'rr-round-tab-btn' + (this.activeRoundFilter === rd.roundNumber ? ' active' : '');
                tab.innerText = 'Vòng ' + rd.roundNumber;

                (function (rNum) {
                    tab.onclick = function () {
                        self.activeRoundFilter = rNum;
                        self.renderRoundSelectorTabs();
                        self.renderFixtures();
                    };
                })(rd.roundNumber);

                tabsContainer.appendChild(tab);
            }
        },

        renderEmptyState: function (containerElem) {
            if (!containerElem) return;
            if (window.TourmaEmptyTeamAlert && typeof window.TourmaEmptyTeamAlert.checkAndRender === 'function') {
                window.TourmaEmptyTeamAlert.checkAndRender(this.tournamentId, this.teamsList, containerElem);
            }
        },

        /**
         * Render Fixtures List by Rounds
         */
        renderFixtures: function () {
            var container = document.getElementById('rrFixturesContainer');
            if (!container) return;

            container.innerHTML = '';
            var self = this;

            if (!this.teamsList || this.teamsList.length === 0 || !this.roundsList || this.roundsList.length === 0) {
                this.renderEmptyState(container);
                return;
            }

            var filteredRounds = this.roundsList;
            if (this.activeRoundFilter !== 'all') {
                filteredRounds = this.roundsList.filter(function (rd) {
                    return rd.roundNumber === self.activeRoundFilter;
                });
            }

            for (var i = 0; i < filteredRounds.length; i++) {
                var roundObj = filteredRounds[i];

                // Auto-detect byeTeam from matches if not set
                if (!roundObj.byeTeam && roundObj.matches) {
                    for (var b = 0; b < roundObj.matches.length; b++) {
                        var bm = roundObj.matches[b];
                        if (bm.team1 && bm.team1.name === 'BYE' && bm.team2) {
                            roundObj.byeTeam = bm.team2.name;
                        } else if (bm.team2 && bm.team2.name === 'BYE' && bm.team1) {
                            roundObj.byeTeam = bm.team1.name;
                        }
                    }
                }

                // Round Header (Exact same format as SE / DE List View)
                var rHeader = document.createElement('div');
                rHeader.className = 'match-list-round-header';

                var rTitle = document.createElement('span');
                rTitle.className = 'round-header-title';
                var byeTag = (roundObj.byeTeam) ? (' <span class="rr-bye-badge"><i class="fa-solid fa-bed"></i> Nghỉ: <strong>' + roundObj.byeTeam + '</strong></span>') : '';
                rTitle.innerHTML = (roundObj.title || ('VÒNG ' + roundObj.roundNumber)).toUpperCase() + byeTag;
                rHeader.appendChild(rTitle);

                // Round Action Controls (Random Score Input, Random & Reset Buttons)
                var rndControls = document.createElement('div');
                rndControls.className = 'round-header-random-controls';

                var rndInput = document.createElement('input');
                rndInput.type = 'number';
                rndInput.className = 'round-random-input';
                rndInput.placeholder = '-';
                rndInput.min = '1';
                rndInput.max = '999';
                rndInput.value = (self.roundRandomInputs && self.roundRandomInputs[roundObj.roundNumber] !== undefined) ? self.roundRandomInputs[roundObj.roundNumber] : '';
                rndInput.title = 'Điểm đội thắng (dùng cho Random)';

                var rndBtn = document.createElement('button');
                rndBtn.type = 'button';
                rndBtn.className = 'btn-round-random';
                rndBtn.innerText = 'Random';
                rndBtn.title = 'Random kết quả cho các trận trong vòng này';

                var rndResetBtn = document.createElement('button');
                rndResetBtn.type = 'button';
                rndResetBtn.className = 'btn-round-reset';
                rndResetBtn.innerHTML = '<i class="fa-solid fa-arrows-rotate"></i>';
                rndResetBtn.title = 'Reset kết quả vòng này';

                (function (rNum, inp, rBtn, rRstBtn) {
                    var saveRoundInput = function () {
                        if (!self.roundRandomInputs) self.roundRandomInputs = {};
                        if (inp.value === '') {
                            delete self.roundRandomInputs[rNum];
                        } else {
                            self.roundRandomInputs[rNum] = inp.value;
                        }
                        try {
                            localStorage.setItem('tourma_rr_round_inputs_' + self.tournamentId, JSON.stringify(self.roundRandomInputs));
                        } catch (e) {}
                    };

                    inp.oninput = saveRoundInput;
                    inp.onchange = saveRoundInput;

                    rBtn.onclick = function (e) {
                        e.stopPropagation();
                        self.randomizeRound(rNum, inp.value);
                    };
                    rRstBtn.onclick = function (e) {
                        e.stopPropagation();
                        self.resetRound(rNum);
                    };
                })(roundObj.roundNumber, rndInput, rndBtn, rndResetBtn);

                rndControls.appendChild(rndInput);
                rndControls.appendChild(rndBtn);
                rndControls.appendChild(rndResetBtn);
                rHeader.appendChild(rndControls);
                container.appendChild(rHeader);

                // Matches List directly under round header
                if (roundObj.matches && roundObj.matches.length > 0) {
                    for (var m = 0; m < roundObj.matches.length; m++) {
                        var matchData = roundObj.matches[m];
                        var cardElem = this.createMatchCardElement(matchData);
                        if (cardElem) {
                            container.appendChild(cardElem);
                        }
                    }
                }
            }
        },

        /**
         * Create standard interactive horizontal list view match card for Round Robin
         */
        createMatchCardElement: function (m) {
            if (!m) return null;

            var t1 = m.team1 || {};
            var t2 = m.team2 || {};
            var isT1Bye = (t1.name === 'BYE');
            var isT2Bye = (t2.name === 'BYE');
            if (isT1Bye || isT2Bye) return null; // Do not render BYE matches

            if (window.TourmaMatchCard && typeof window.TourmaMatchCard.createCardElement === 'function') {
                return window.TourmaMatchCard.createCardElement({
                    matchId: m.matchId || m.id,
                    matchNumber: m.matchNumber || m.id,
                    roundNumber: m.roundNumber,
                    roundName: 'Vòng ' + m.roundNumber + ' (Trận #' + (m.matchNumber || m.id) + ')',
                    status: m.status,
                    team1: m.team1,
                    team2: m.team2,
                    winnerId: m.winnerId,
                    allowDraw: true,
                    hideSeeds: true
                });
            }

            return null;
        },

        /**
         * Randomize all matches in a round with custom score support [X, random(0..X-1)]
         */
        randomizeRound: function (roundNum, rawWinScore) {
            if (window.FinalStagePopup && window.FinalStagePopup.isLocked) {
                alert('Giải đấu đã kết thúc và đang ở trạng thái khóa. Vui lòng bấm "Mở khóa" trên thanh thông báo nếu muốn chỉnh sửa kết quả.');
                return;
            }
            var roundObj = this.roundsList.find(function (r) { return r.roundNumber === roundNum; });
            if (!roundObj) return;

            var customWinScore = null;
            if (rawWinScore !== undefined && rawWinScore !== null && String(rawWinScore).trim() !== '') {
                var parsed = Number(rawWinScore);
                if (!isNaN(parsed) && parsed > 0) {
                    customWinScore = parsed;
                }
            }

            for (var i = 0; i < roundObj.matches.length; i++) {
                var m = roundObj.matches[i];
                var mId = m.matchId || m.id;
                var mapMatch = this.matchesMap[mId] || m;

                var t1 = mapMatch.team1.name;
                var t2 = mapMatch.team2.name;
                if (t1 === 'BYE' || t2 === 'BYE') continue;

                var outcome = Math.random();
                var s1, s2;

                if (customWinScore !== null) {
                    var winScore = customWinScore;
                    var loseScore = (winScore > 1) ? Math.floor(Math.random() * winScore) : 0;
                    if (outcome < 0.45) {
                        s1 = winScore;
                        s2 = loseScore;
                    } else if (outcome < 0.75) {
                        s2 = winScore;
                        s1 = loseScore;
                    } else {
                        // Draw
                        s1 = winScore;
                        s2 = winScore;
                    }
                } else {
                    if (outcome < 0.45) {
                        s1 = Math.floor(Math.random() * 3) + 1;
                        s2 = Math.floor(Math.random() * s1);
                    } else if (outcome < 0.75) {
                        s2 = Math.floor(Math.random() * 3) + 1;
                        s1 = Math.floor(Math.random() * s2);
                    } else {
                        // Draw
                        s1 = Math.floor(Math.random() * 3);
                        s2 = s1;
                    }
                }

                mapMatch.team1.score = String(s1);
                mapMatch.team2.score = String(s2);
                mapMatch.status = 'COMPLETED';
                mapMatch.winnerId = (s1 > s2) ? 'team1' : ((s2 > s1) ? 'team2' : null);

                m.team1.score = mapMatch.team1.score;
                m.team2.score = mapMatch.team2.score;
                m.status = mapMatch.status;
                m.winnerId = mapMatch.winnerId;

                this.matchesMap[mId] = mapMatch;

                this.saveMatchAJAX(mId, m.team1.score, m.team2.score, (s1 > s2) ? t1 : ((s2 > s1) ? t2 : 'DRAW'));
            }

            this.persistLocal();
            this.renderFixtures();
        },

        /**
         * Reset all matches in a round
         */
        resetRound: function (roundNum) {
            if (window.FinalStagePopup && window.FinalStagePopup.isLocked) {
                alert('Giải đấu đã kết thúc và đang ở trạng thái khóa. Vui lòng bấm "Mở khóa" trên thanh thông báo nếu muốn chỉnh sửa kết quả.');
                return;
            }
            var roundObj = this.roundsList.find(function (r) { return r.roundNumber === roundNum; });
            if (!roundObj) return;

            for (var i = 0; i < roundObj.matches.length; i++) {
                var m = roundObj.matches[i];
                var mId = m.matchId || m.id;
                var mapMatch = this.matchesMap[mId] || m;
                if (mapMatch.team1.name === 'BYE' || mapMatch.team2.name === 'BYE') continue;

                mapMatch.team1.score = '';
                mapMatch.team2.score = '';
                mapMatch.status = 'SCHEDULED';
                mapMatch.winnerId = null;

                m.team1.score = '';
                m.team2.score = '';
                m.status = 'SCHEDULED';
                m.winnerId = null;

                this.matchesMap[mId] = mapMatch;

                this.saveMatchAJAX(mId, '', '', null);
            }

            this.persistLocal();
            this.renderFixtures();
        },

        /**
         * Confirm Reset Full Tournament
         */
        confirmResetTournament: function () {
            this.closeResetModal();
            this.roundRandomInputs = {};
            try {
                localStorage.removeItem('tourma_rr_matches_' + this.tournamentId);
                localStorage.removeItem('tourma_matches_' + this.tournamentId);
                localStorage.removeItem('tourma_rr_round_inputs_' + this.tournamentId);
                if (this.currentStage === 1) {
                    localStorage.removeItem('tourma_stage2_teams_' + this.tournamentId);
                    localStorage.removeItem('tourma_bracket_stage2_' + this.tournamentId);
                    localStorage.removeItem('tourma_matches_stage2_' + this.tournamentId);
                    localStorage.removeItem('tourma_de_matches_stage2_' + this.tournamentId);
                    localStorage.removeItem('tourma_stage1_completed_' + this.tournamentId);
                    var mCfg = JSON.parse(localStorage.getItem('tourma_multi_config_' + this.tournamentId)) || {};
                    mCfg.stage2MatchesCreated = false;
                    localStorage.setItem('tourma_multi_config_' + this.tournamentId, JSON.stringify(mCfg));
                }
            } catch (e) {}

            if (window.TourmaRoundRobinAlgorithm) {
                var gen = window.TourmaRoundRobinAlgorithm.generateRoundRobin(this.teamsList, this.config);
                this.roundsList = gen.rounds;
                this.matchesMap = gen.matchesMap;
                this.persistLocal();
                this.renderRoundSelectorTabs();
                this.renderFixtures();
            }
        },

        openResetModal: function () {
            if (window.FinalStagePopup && window.FinalStagePopup.isLocked) {
                alert('Giải đấu đã kết thúc và đang ở trạng thái khóa. Vui lòng bấm "Mở khóa" trên thanh thông báo nếu muốn reset giải.');
                return;
            }
            var modal = document.getElementById('rrResetModalBackdrop');
            if (modal) {
                modal.classList.add('show');
                document.body.style.overflow = 'hidden';
            }
        },

        closeResetModal: function () {
            var modal = document.getElementById('rrResetModalBackdrop');
            if (modal) {
                modal.classList.remove('show');
                document.body.style.overflow = '';
            }
        },

        /**
         * Attach Modal Update Listeners
         */
        attachEventListeners: function () {
            var self = this;
            document.addEventListener('tourmaMatchUpdated', function (e) {
                var detail = e.detail;
                if (!detail || !detail.matchId) return;

                var mId = Number(detail.matchId);
                var targetMatch = self.matchesMap[mId];
                if (!targetMatch) return;

                targetMatch.team1.score = String(detail.team1Score || '0');
                targetMatch.team2.score = String(detail.team2Score || '0');
                targetMatch.status = 'COMPLETED';

                var s1 = Number(targetMatch.team1.score);
                var s2 = Number(targetMatch.team2.score);
                targetMatch.winnerId = (s1 > s2) ? 'team1' : ((s2 > s1) ? 'team2' : null);

                self.persistLocal();
                self.renderFixtures();

                self.saveMatchAJAX(
                    mId,
                    targetMatch.team1.score,
                    targetMatch.team2.score,
                    (s1 > s2) ? targetMatch.team1.name : ((s2 > s1) ? targetMatch.team2.name : 'DRAW')
                );
            });
        },

        /**
         * Send AJAX save request
         */
        saveMatchAJAX: function (matchId, t1Score, t2Score, winner) {
            var params = new URLSearchParams();
            params.append('matchId', matchId);
            params.append('team1Score', t1Score);
            params.append('team2Score', t2Score);
            params.append('winner', winner || '');

            fetch('round-robin', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
                },
                body: params.toString()
            }).then(function (res) {
                return res.json();
            }).then(function (data) {
                console.log('Round Robin match updated:', data);
            }).catch(function (err) {
                console.warn('AJAX save notice:', err);
            });
        }
    };

})();
