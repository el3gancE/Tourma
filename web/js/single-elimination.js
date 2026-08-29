/**
 * ============================================================================
 * TOURMA - SINGLE ELIMINATION BRACKET ENGINE (single-elimination.js)
 * High-Density Dark UI Orchestration, Viewport Drag/Zoom, SVG Tree Connectors,
 * Realtime Event Listening, and AJAX Database Persistence.
 * ============================================================================
 */

(function () {
    'use strict';

    window.SingleEliminationEngine = {
        tournamentId: null,
        teamsList: [],
        matchesMap: {},
        roundsList: [],

        /**
         * Initialize Single Elimination Tournament UI Engine
         * @param {string} tourneyId - Tournament ID from server
         * @param {Array} dbMatches - Matches preloaded from database (optional)
         */
        init: function (tourneyId, dbMatches, preloadedTeams) {
            this.tournamentId = tourneyId || 'demo';

            // 1. Load Teams List
            var storageKeyTeams = 'tourma_teams_' + this.tournamentId;
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
            this.teamsList = teams;

            // Update team count badge in top toolbar
            var countBadge = document.getElementById('tournamentTeamCountBadge');
            if (countBadge) {
                countBadge.innerText = this.teamsList.length + ' Đội';
            }

            // Restore round random inputs
            try {
                this.roundRandomInputs = JSON.parse(localStorage.getItem('tourma_round_inputs_' + this.tournamentId)) || {};
            } catch (e) {
                this.roundRandomInputs = {};
            }

            // 2. Generate or Restore Matches Data using TourmaBracketAlgorithm
            this.generateMatchesStructure(dbMatches);

            // 3. Render Views
            this.renderBracketView();
            this.renderListView();

            // Setup Quick Mode
            this.initQuickMode();

            // Restore active view mode (bracket or list) from localStorage
            var storageKeyViewMode = 'tourma_view_mode_' + this.tournamentId;
            var savedMode = localStorage.getItem(storageKeyViewMode) || 'bracket';
            this.switchViewMode(savedMode);

            // 4. Initialize TourmaViewport & attach redraw listeners
            var self = this;
            if (window.TourmaViewport && typeof window.TourmaViewport.init === 'function') {
                window.TourmaViewport.init('bracketViewportContainer', 'bracketViewportCanvas', {
                    badgeId: 'zoomLevelBadge',
                    onRedraw: function () { self.drawTreeConnectors(); }
                });
            }

            window.addEventListener('resize', function () {
                self.drawTreeConnectors();
            });
            setTimeout(function () {
                self.drawTreeConnectors();
            }, 100);

            // Check Final Stage conclusion & render top banner if complete
            this.checkFinalStage();
        },

        /**
         * Generate or Restore Matches Structure
         */
        generateMatchesStructure: function (dbMatches) {
            var storageKeyMatches = 'tourma_matches_' + this.tournamentId;
            var savedMatches = null;

            if (this.tournamentId) {
                try {
                    savedMatches = JSON.parse(localStorage.getItem(storageKeyMatches));
                } catch (e) {
                    savedMatches = null;
                }
            }

            if (savedMatches && Object.keys(savedMatches).length > 0) {
                if (savedMatches.matchesMap) {
                    this.matchesMap = savedMatches.matchesMap;
                    this.roundsList = savedMatches.roundsList || savedMatches.rounds || [];
                } else {
                    this.matchesMap = savedMatches;
                    this.buildRoundsFromMap();
                }
                if (window.TourmaBracketAlgorithm) {
                    window.TourmaBracketAlgorithm.renumberMatchesContiguously(this.roundsList, this.matchesMap);
                }
            } else if (this.teamsList && this.teamsList.length > 0 && window.TourmaBracketAlgorithm) {
                var generated = window.TourmaBracketAlgorithm.generateSingleElimination(this.teamsList);
                this.bracketData = generated;
                this.roundsList = generated.roundsList || [];
                this.matchesMap = generated.matchesMap || {};

                if (dbMatches && dbMatches.length > 0) {
                    for (var i = 0; i < dbMatches.length; i++) {
                        var dbm = dbMatches[i];
                        if (this.matchesMap[dbm.id]) {
                            var localM = this.matchesMap[dbm.id];
                            localM.status = dbm.status;
                            localM.team1.score = dbm.team1Score;
                            localM.team2.score = dbm.team2Score;
                            if (dbm.winnerId) {
                                localM.winnerId = (dbm.winnerId === localM.team1.name) ? 'team1' : 'team2';
                            }
                        }
                    }
                }
                this.persistMatches();
            }
        },

        renderEmptyState: function (containerElem) {
            if (!containerElem) return;
            if (window.TourmaEmptyTeamAlert && typeof window.TourmaEmptyTeamAlert.checkAndRender === 'function') {
                window.TourmaEmptyTeamAlert.checkAndRender(this.tournamentId, this.teamsList, containerElem);
            }
        },

        /**
         * Render Bracket Viewport Tree Columns Dynamically
         */
        renderBracketView: function () {
            var viewportFrame = document.getElementById('bracketViewportFrame');
            var alertContainer = document.getElementById('singleEmptyAlertContainer');
            var canvasWrapper = document.getElementById('singleBracketColumnsWrapper');
            if (!canvasWrapper) return;

            canvasWrapper.innerHTML = '';
            var self = this;

            var teamCount = (this.teamsList && Array.isArray(this.teamsList)) ? this.teamsList.length : 0;

            if (teamCount < 2 || !this.roundsList || this.roundsList.length === 0) {
                if (viewportFrame) viewportFrame.style.display = 'none';
                if (alertContainer) {
                    alertContainer.style.display = 'flex';
                    this.renderEmptyState(alertContainer);
                } else {
                    this.renderEmptyState(canvasWrapper);
                }
                return;
            } else {
                if (viewportFrame) viewportFrame.style.display = 'block';
                if (alertContainer) alertContainer.style.display = 'none';
            }

            for (var r = 0; r < this.roundsList.length; r++) {
                var roundObj = this.roundsList[r];

                var col = document.createElement('div');
                col.className = 'single-round-column';

                var header = document.createElement('div');
                header.className = 'single-round-header';

                var titleSpan = document.createElement('span');
                titleSpan.className = 'round-header-title';
                titleSpan.innerText = roundObj.title;
                header.appendChild(titleSpan);

                // Round Random & Quick Score Controls
                var canRandom = self.isRoundReadyForRandom(roundObj);
                var isAllDone = window.TourmaRandomService ? window.TourmaRandomService.isRoundAllCompleted(roundObj, self.matchesMap) : false;
                var canReset = window.TourmaRandomService ? window.TourmaRandomService.hasCompletedMatchesInRound(roundObj, self.matchesMap) : false;
                var rndControls = document.createElement('div');
                rndControls.className = 'round-header-random-controls';

                var rndInput = document.createElement('input');
                rndInput.type = 'number';
                rndInput.className = 'round-random-input';
                rndInput.placeholder = '-';
                rndInput.min = '1';
                rndInput.max = '999';
                rndInput.value = (self.roundRandomInputs && self.roundRandomInputs[roundObj.roundNumber] !== undefined) ? self.roundRandomInputs[roundObj.roundNumber] : '';
                rndInput.title = 'Điểm đội thắng (dùng cho Quick Mode và Random)';

                var rndBtn = document.createElement('button');
                rndBtn.type = 'button';
                rndBtn.className = 'btn-round-random' + (canRandom ? '' : ' disabled');
                rndBtn.innerText = 'Random';
                rndBtn.title = canRandom ? 'Random kết quả vòng đấu' : (isAllDone ? 'Tất cả các trận đã hoàn thành (bấm nút Reset để mở lại)' : 'Vòng đấu chưa xác định đủ các đội');
                if (!canRandom) rndBtn.disabled = true;

                var rndResetBtn = document.createElement('button');
                rndResetBtn.type = 'button';
                rndResetBtn.className = 'btn-round-reset' + (canReset ? '' : ' disabled');
                rndResetBtn.innerHTML = '<i class="fa-solid fa-arrows-rotate"></i>';
                rndResetBtn.title = canReset ? 'Reset kết quả vòng đấu' : 'Vòng đấu chưa có trận nào hoàn thành';
                if (!canReset) rndResetBtn.disabled = true;

                (function (rNum, inp) {
                    var saveRoundInput = function () {
                        if (!self.roundRandomInputs) self.roundRandomInputs = {};
                        if (inp.value === '') {
                            delete self.roundRandomInputs[rNum];
                        } else {
                            self.roundRandomInputs[rNum] = inp.value;
                        }
                        try {
                            localStorage.setItem('tourma_round_inputs_' + self.tournamentId, JSON.stringify(self.roundRandomInputs));
                        } catch (e) {}
                    };

                    inp.oninput = saveRoundInput;
                    inp.onchange = saveRoundInput;

                    rndBtn.onclick = function (e) {
                        e.stopPropagation();
                        self.randomizeRound(rNum, inp.value);
                    };
                    rndResetBtn.onclick = function (e) {
                        e.stopPropagation();
                        self.resetRound(rNum);
                    };
                })(roundObj.roundNumber, rndInput);

                rndControls.appendChild(rndInput);
                rndControls.appendChild(rndBtn);
                rndControls.appendChild(rndResetBtn);
                header.appendChild(rndControls);

                col.appendChild(header);

                var box = document.createElement('div');
                box.className = 'single-round-matches-box';

                var showRound1Byes = window.TourmaBracketAlgorithm ? window.TourmaBracketAlgorithm.shouldShowRound1Byes(self.roundsList, 0.50) : true;

                for (var m = 0; m < roundObj.matches.length; m++) {
                    var matchData = roundObj.matches[m];
                    var t1Name = matchData.team1 ? matchData.team1.name : '';
                    var t2Name = matchData.team2 ? matchData.team2.name : '';
                    var isBye = matchData.isBye || (t1Name === 'BYE' || t2Name === 'BYE');

                    matchData.hideByeSlot = (isBye && !showRound1Byes);

                    if (window.TourmaBracketCard && typeof window.TourmaBracketCard.createNodeElement === 'function') {
                        var nodeElem = window.TourmaBracketCard.createNodeElement(matchData);
                        if (nodeElem) box.appendChild(nodeElem);
                    }
                }

                col.appendChild(box);
                canvasWrapper.appendChild(col);
            }

            requestAnimationFrame(function () { self.drawTreeConnectors(); });
            setTimeout(function () { self.drawTreeConnectors(); }, 60);
            setTimeout(function () { self.drawTreeConnectors(); }, 250);
        },

        /**
         * Draw SVG Tree Connectors — delegates to shared TourmaViewport.drawConnectors engine
         */
        drawTreeConnectors: function () {
            var canvas = document.getElementById('bracketViewportCanvas');
            var wrapper = document.getElementById('singleBracketColumnsWrapper');
            if (!canvas || !wrapper) return;

            var scale = (window.TourmaViewport && window.TourmaViewport.currentScale) ? window.TourmaViewport.currentScale : 1;

            if (window.TourmaViewport && typeof window.TourmaViewport.drawConnectors === 'function') {
                window.TourmaViewport.drawConnectors(canvas, wrapper, this.matchesMap, scale);
            }
        },

        activeRoundFilter: 'all',

        /**
         * Get concise round abbreviation for filter tabs (RO32, RO16, QF, SF, Finals)
         */
        getShortRoundName: function (roundObj, totalRounds) {
            var t = (roundObj.title || '').trim().toLowerCase();
            if (t.includes('32') || t.includes('ro32')) return 'RO32';
            if (t.includes('16') || t.includes('ro16')) return 'RO16';
            if (t.includes('tứ kết') || t.includes('quarter') || t.includes('ro8') || t.includes('qf')) return 'QF';
            if (t.includes('bán kết') || t.includes('semi') || t.includes('ro4') || t.includes('sf')) return 'SF';
            if (t.includes('chung kết') || t.includes('final')) return 'Finals';

            var fromEnd = totalRounds - roundObj.roundNumber;
            if (fromEnd === 0) return 'Finals';
            if (fromEnd === 1) return 'SF';
            if (fromEnd === 2) return 'QF';
            if (fromEnd === 3) return 'RO16';
            if (fromEnd === 4) return 'RO32';
            return 'R' + roundObj.roundNumber;
        },

        /**
         * Render Matches List View Dynamically
         */
        renderListView: function () {
            var listContainer = document.getElementById('singleListViewContainer');
            if (!listContainer) return;

            listContainer.innerHTML = '';
            var self = this;

            var teamCount = (this.teamsList && Array.isArray(this.teamsList)) ? this.teamsList.length : 0;
            if (teamCount < 2 || !this.roundsList || this.roundsList.length === 0) {
                this.renderEmptyState(listContainer);
                return;
            }

            var allRounds = (window.TourmaBracketAlgorithm) ?
                window.TourmaBracketAlgorithm.filterMatchesForListView(this.roundsList) : this.roundsList;

            // 1. Render Horizontal Round Selector Tabs (Pills)
            var tabsBar = document.createElement('div');
            tabsBar.className = 'rr-round-selector-bar';

            var allTab = document.createElement('button');
            allTab.type = 'button';
            allTab.className = 'rr-round-tab-btn' + (self.activeRoundFilter === 'all' ? ' active' : '');
            allTab.innerText = 'Tất cả (' + allRounds.length + ')';
            allTab.onclick = function () {
                self.activeRoundFilter = 'all';
                self.renderListView();
            };
            tabsBar.appendChild(allTab);

            for (var i = 0; i < allRounds.length; i++) {
                var rObj = allRounds[i];
                var tab = document.createElement('button');
                tab.type = 'button';
                var isAct = (self.activeRoundFilter === rObj.roundNumber);
                tab.className = 'rr-round-tab-btn' + (isAct ? ' active' : '');
                tab.innerText = self.getShortRoundName(rObj, allRounds.length);

                (function (rNum) {
                    tab.onclick = function () {
                        self.activeRoundFilter = rNum;
                        self.renderListView();
                    };
                })(rObj.roundNumber);
                tabsBar.appendChild(tab);
            }
            listContainer.appendChild(tabsBar);

            // 2. Filter Rounds to display
            var roundsToRender = allRounds;
            if (self.activeRoundFilter !== 'all') {
                roundsToRender = allRounds.filter(function (r) {
                    return r.roundNumber === self.activeRoundFilter;
                });
            }

            for (var r = 0; r < roundsToRender.length; r++) {
                var roundObj = roundsToRender[r];

                var rHeader = document.createElement('div');
                rHeader.className = 'match-list-round-header';

                var rTitle = document.createElement('span');
                rTitle.className = 'round-header-title';
                rTitle.innerText = roundObj.title;
                rHeader.appendChild(rTitle);

                // Round Random Controls in list header
                var canListRandom = self.isRoundReadyForRandom(roundObj);
                var isListAllDone = window.TourmaRandomService ? window.TourmaRandomService.isRoundAllCompleted(roundObj, self.matchesMap) : false;
                var canListReset = window.TourmaRandomService ? window.TourmaRandomService.hasCompletedMatchesInRound(roundObj, self.matchesMap) : false;
                var rControls = document.createElement('div');
                rControls.className = 'round-header-random-controls';

                var rInp = document.createElement('input');
                rInp.type = 'number';
                rInp.className = 'round-random-input';
                rInp.placeholder = '-';
                rInp.min = '1';
                rInp.max = '999';
                rInp.value = (self.roundRandomInputs && self.roundRandomInputs[roundObj.roundNumber]) ? self.roundRandomInputs[roundObj.roundNumber] : '';
                rInp.title = canListRandom ? 'Điểm đội thắng' : (isListAllDone ? 'Tất cả các trận đã hoàn thành' : 'Vòng đấu chưa xác định đủ các đội');
                if (!canListRandom) rInp.disabled = true;
                rInp.oninput = function () {
                    if (!self.roundRandomInputs) self.roundRandomInputs = {};
                    self.roundRandomInputs[roundObj.roundNumber] = this.value;
                };

                var rBtn = document.createElement('button');
                rBtn.type = 'button';
                rBtn.className = 'btn-round-random' + (canListRandom ? '' : ' disabled');
                rBtn.innerText = 'Random';
                rBtn.title = canListRandom ? 'Random kết quả vòng đấu' : (isListAllDone ? 'Tất cả các trận đã hoàn thành (bấm nút Reset để mở lại)' : 'Vòng đấu chưa xác định đủ các đội');
                if (!canListRandom) rBtn.disabled = true;

                var rResetBtn = document.createElement('button');
                rResetBtn.type = 'button';
                rResetBtn.className = 'btn-round-reset' + (canListReset ? '' : ' disabled');
                rResetBtn.innerHTML = '<i class="fa-solid fa-arrows-rotate"></i>';
                rResetBtn.title = canListReset ? 'Reset kết quả vòng đấu' : 'Vòng đấu chưa có trận nào hoàn thành';
                if (!canListReset) rResetBtn.disabled = true;

                (function (rNum, inp) {
                    rBtn.onclick = function (e) {
                        e.stopPropagation();
                        self.randomizeRound(rNum, inp.value);
                    };
                    rResetBtn.onclick = function (e) {
                        e.stopPropagation();
                        self.resetRound(rNum);
                    };
                })(roundObj.roundNumber, rInp);

                rControls.appendChild(rInp);
                rControls.appendChild(rBtn);
                rControls.appendChild(rResetBtn);
                rHeader.appendChild(rControls);

                listContainer.appendChild(rHeader);

                for (var m = 0; m < roundObj.matches.length; m++) {
                    var matchData = roundObj.matches[m];
                    if (window.TourmaMatchCard && typeof window.TourmaMatchCard.createCardElement === 'function') {
                        var cardElem = window.TourmaMatchCard.createCardElement(matchData);
                        if (cardElem) listContainer.appendChild(cardElem);
                    }
                }
            }
        },

        /**
         * Check if all matches in a round have determined teams and need completion
         */
        isRoundReadyForRandom: function (roundObj) {
            if (window.TourmaRandomService) {
                return window.TourmaRandomService.isRoundReadyForRandom(roundObj, this.matchesMap);
            }
            return false;
        },

        /**
         * Randomize all playable uncompleted matches in a specific SE round via TourmaRandomService
         */
        randomizeRound: function (roundNumber, rawWinScore) {
            if (window.FinalStagePopup && window.FinalStagePopup.isLocked) {
                alert('Giải đấu đã kết thúc và đang ở trạng thái khóa. Vui lòng bấm "Mở khóa" trên thanh thông báo nếu muốn chỉnh sửa kết quả.');
                return;
            }
            var roundObj = null;
            for (var r = 0; r < this.roundsList.length; r++) {
                if (this.roundsList[r].roundNumber === roundNumber) {
                    roundObj = this.roundsList[r];
                    break;
                }
            }
            if (!roundObj) return;

            var self = this;
            var changed = false;

            if (window.TourmaRandomService) {
                changed = window.TourmaRandomService.randomizeRoundMatches(
                    roundObj,
                    this.matchesMap,
                    rawWinScore,
                    function (mId, winnerSlot, isT1Winner, s1, s2) {
                        if (window.TourmaBracketAlgorithm) {
                            window.TourmaBracketAlgorithm.propagateAndResetDownstream(self.matchesMap, mId, winnerSlot, isT1Winner);
                        }
                    }
                );
            }

            if (changed) {
                this.persistMatches();
                this.renderBracketView();
                this.renderListView();
            }
        },

        /**
         * Reset all matches in a specific SE round and cascade resets downstream
         */
        resetRound: function (roundNumber) {
            if (window.FinalStagePopup && window.FinalStagePopup.isLocked) {
                alert('Giải đấu đã kết thúc và đang ở trạng thái khóa. Vui lòng bấm "Mở khóa" trên thanh thông báo nếu muốn chỉnh sửa kết quả.');
                return;
            }
            var roundObj = null;
            for (var r = 0; r < this.roundsList.length; r++) {
                if (this.roundsList[r].roundNumber === roundNumber) {
                    roundObj = this.roundsList[r];
                    break;
                }
            }
            if (!roundObj) return;

            var self = this;
            var changed = false;

            if (window.TourmaRandomService) {
                changed = window.TourmaRandomService.resetRoundMatches(
                    roundObj,
                    this.matchesMap,
                    function (mId) {
                        if (window.TourmaBracketAlgorithm) {
                            window.TourmaBracketAlgorithm.cascadeResetPlaceholders(self.matchesMap, mId);
                        }
                    }
                );
            }

            if (changed) {
                this.persistMatches();
                this.renderBracketView();
                this.renderListView();
            }
        },

        /**
         * Toggle View Mode between Bracket View (Tree Canvas) and List View (Matches List)
         */
        switchViewMode: function (mode) {
            this.currentViewMode = mode;
            if (this.tournamentId) {
                var storageKeyViewMode = 'tourma_view_mode_' + this.tournamentId;
                try {
                    localStorage.setItem(storageKeyViewMode, mode);
                } catch (e) {}
            }

            var bracketContainer = document.getElementById('bracketViewportFrame');
            var alertContainer = document.getElementById('singleEmptyAlertContainer');
            var listContainer = document.getElementById('singleListViewContainer');

            var btnBracket = document.getElementById('btnViewBracket');
            var btnList = document.getElementById('btnViewList');

            var teamCount = (this.teamsList && Array.isArray(this.teamsList)) ? this.teamsList.length : 0;

            if (teamCount < 2 || !this.roundsList || this.roundsList.length === 0) {
                if (bracketContainer) bracketContainer.style.display = 'none';
                if (listContainer) listContainer.classList.remove('show');
                if (alertContainer) {
                    alertContainer.style.display = 'flex';
                    this.renderEmptyState(alertContainer);
                }
                return;
            }

            if (alertContainer) alertContainer.style.display = 'none';

            if (mode === 'bracket') {
                if (bracketContainer) bracketContainer.style.display = 'block';
                if (listContainer) listContainer.classList.remove('show');
                if (btnBracket) btnBracket.classList.add('active');
                if (btnList) btnList.classList.remove('active');
                var self = this;
                setTimeout(function () { self.drawTreeConnectors(); }, 50);
            } else {
                if (bracketContainer) bracketContainer.style.display = 'none';
                if (listContainer) listContainer.classList.add('show');
                if (btnBracket) btnBracket.classList.remove('active');
                if (btnList) btnList.classList.add('active');
            }
        },

        /**
         * Quick Mode Management (1-click winner selection)
         */
        isQuickMode: false,

        initQuickMode: function () {
            var storageKey = 'tourma_quick_mode_' + this.tournamentId;
            this.isQuickMode = (localStorage.getItem(storageKey) === 'true');
            window.TourmaQuickMode = this.isQuickMode;
            this.updateQuickModeUI();
        },

        toggleQuickMode: function () {
            if (window.FinalStagePopup && window.FinalStagePopup.isLocked) {
                alert('Giải đấu đã kết thúc và đang ở trạng thái khóa. Vui lòng bấm "Mở khóa" trên thanh thông báo nếu muốn chỉnh sửa kết quả.');
                return;
            }
            this.isQuickMode = !this.isQuickMode;
            window.TourmaQuickMode = this.isQuickMode;
            var storageKey = 'tourma_quick_mode_' + this.tournamentId;
            localStorage.setItem(storageKey, this.isQuickMode ? 'true' : 'false');
            this.updateQuickModeUI();
            this.renderBracketView();
            this.renderListView();
        },

        updateQuickModeUI: function () {
            var btn = document.getElementById('singleBtnQuickMode') || document.getElementById('seBtnQuickMode');
            if (btn) {
                btn.classList.toggle('active', this.isQuickMode);
                var txt = btn.querySelector('.quick-mode-status-text');
                if (txt) txt.innerText = this.isQuickMode ? 'ON' : 'OFF';
            }
            if (this.isQuickMode) {
                document.body.classList.add('tourma-quick-mode-active');
            } else {
                document.body.classList.remove('tourma-quick-mode-active');
            }
        },

        handleQuickWinner: function (matchId, winnerSlot, customScore) {
            if (window.FinalStagePopup && window.FinalStagePopup.isLocked) return;

            var mId = Number(matchId);
            var targetMatch = this.matchesMap[mId];
            if (!targetMatch) return;

            var t1 = targetMatch.team1 ? targetMatch.team1.name : '';
            var t2 = targetMatch.team2 ? targetMatch.team2.name : '';
            if (!t1 || !t2 || t1 === 'BYE' || t2 === 'BYE' || t1.startsWith('W #') || t2.startsWith('W #')) return;

            var isT1Winner = (winnerSlot === 1);
            var isT2Winner = (winnerSlot === 2);

            // Determine winning score from passed customScore or stored Round Score Input (Default 1 if not specified)
            var winningScore = '1';
            if (customScore && Number(customScore) > 0) {
                winningScore = String(customScore);
            } else {
                var rNum = targetMatch.roundNumber;
                if (!rNum && this.bracketData && this.bracketData.rounds) {
                    for (var r = 0; r < this.bracketData.rounds.length; r++) {
                        var rd = this.bracketData.rounds[r];
                        if (rd.matches && rd.matches.some(function (m) { return m.matchId === mId; })) {
                            rNum = rd.roundNumber;
                            break;
                        }
                    }
                }
                if (rNum && this.roundRandomInputs && this.roundRandomInputs[rNum]) {
                    var cScore = this.roundRandomInputs[rNum];
                    if (cScore && Number(cScore) > 0) {
                        winningScore = String(cScore);
                    }
                }
            }

            targetMatch.winnerId = isT1Winner ? 'team1' : 'team2';
            targetMatch.status = 'COMPLETED';

            var winNum = Number(winningScore);
            var losingScore = '0';
            if (winNum > 1) {
                losingScore = String(Math.floor(Math.random() * winNum)); // Range: [0, X-1]
            }

            targetMatch.team1.score = isT1Winner ? winningScore : losingScore;
            targetMatch.team2.score = isT2Winner ? winningScore : losingScore;

            if (window.TourmaBracketAlgorithm) {
                window.TourmaBracketAlgorithm.propagateAndResetDownstream(this.matchesMap, mId, targetMatch.winnerId, isT1Winner);
            }

            this.persistMatches();
            this.renderBracketView();
            this.renderListView();

            this.saveMatchResultAJAX(
                mId,
                targetMatch.team1.score,
                targetMatch.team2.score,
                isT1Winner ? targetMatch.team1.name : targetMatch.team2.name
            );
        },

        /**
         * Handle Realtime Match Updates (Dispatched from Popup Modal)
         */
        onMatchUpdated: function (detail) {
            if (!detail || !detail.matchId) return;

            var mId = Number(detail.matchId);
            var targetMatch = this.matchesMap[mId];
            if (!targetMatch) return;

            targetMatch.team1.score = detail.team1Score;
            targetMatch.team2.score = detail.team2Score;
            targetMatch.winnerId = detail.winner;
            targetMatch.status = detail.status || 'COMPLETED';

            var isT1Winner = (detail.winner === 'team1');
            var isT2Winner = (detail.winner === 'team2');

            // Propagate winner downstream & cascade reset future matches using algorithm engine
            if (window.TourmaBracketAlgorithm) {
                window.TourmaBracketAlgorithm.propagateAndResetDownstream(this.matchesMap, mId, detail.winner, isT1Winner);
            }

            // Persist updated state
            this.persistMatches();

            // Re-render UI views to reflect updated tree and list
            this.renderBracketView();
            this.renderListView();

            // Notify backend asynchronously
            this.saveMatchResultAJAX(
                mId,
                detail.team1Score,
                detail.team2Score,
                detail.winner ? (isT1Winner ? targetMatch.team1.name : targetMatch.team2.name) : ''
            );
        },

        /**
         * Open / Close / Confirm Reset Bracket Modal
         */
        openResetModal: function () {
            if (window.FinalStagePopup && window.FinalStagePopup.isLocked) {
                alert('Giải đấu đã kết thúc và đang ở trạng thái khóa. Vui lòng bấm "Mở khóa" trên thanh thông báo nếu muốn reset giải.');
                return;
            }
            var modal = document.getElementById('seResetModalBackdrop');
            if (modal) {
                modal.classList.add('show');
                document.body.style.overflow = 'hidden';
            }
        },

        closeResetModal: function () {
            var modal = document.getElementById('seResetModalBackdrop');
            if (modal) {
                modal.classList.remove('show');
                document.body.style.overflow = '';
            }
        },

        confirmResetBracket: function () {
            this.closeResetModal();
            this.roundRandomInputs = {};
            if (this.tournamentId) {
                try {
                    localStorage.removeItem('tourma_matches_' + this.tournamentId);
                    localStorage.removeItem('tourma_matches_demo');
                    localStorage.removeItem('tourma_round_inputs_' + this.tournamentId);
                } catch (e) {}
            }

            // Regenerate fresh initial bracket
            if (window.TourmaBracketAlgorithm && typeof window.TourmaBracketAlgorithm.generateSingleElimination === 'function') {
                var generated = window.TourmaBracketAlgorithm.generateSingleElimination(this.teamsList);
                this.bracketData = generated;
                this.roundsList = generated.roundsList || [];
                this.matchesMap = generated.matchesMap || {};

                this.persistMatches();
                this.renderBracketView();
                this.renderListView();
                var self = this;
                setTimeout(function () {
                    self.drawTreeConnectors();
                }, 50);
            }
        },

        /**
         * Persist matches map to localStorage
         */
        persistMatches: function () {
            if (this.tournamentId) {
                var storageKeyMatches = 'tourma_matches_' + this.tournamentId;
                try {
                    localStorage.setItem(storageKeyMatches, JSON.stringify(this.matchesMap));
                    if (this.teamsList && this.teamsList.length > 0) {
                        localStorage.setItem('tourma_teams_' + this.tournamentId, JSON.stringify(this.teamsList));
                    }
                } catch (e) {}
            }
            this.checkFinalStage();
        },

        checkFinalStage: function () {
            var self = this;
            if (window.FinalStagePopup) {
                window.FinalStagePopup.checkAndRender(
                    this.tournamentId,
                    'SINGLE_ELIMINATION',
                    this.matchesMap,
                    this.teamsList,
                    null,
                    function (isLocked) {
                        if (isLocked) {
                            self.isQuickMode = false;
                            window.TourmaQuickMode = false;
                            var qBtn = document.getElementById('singleBtnQuickMode') || document.getElementById('seBtnQuickMode');
                            if (qBtn) qBtn.style.display = 'none';
                        }
                    }
                );
            }
        },

        buildMapFromList: function (dbMatches) {
            this.matchesMap = {};
            for (var i = 0; i < dbMatches.length; i++) {
                var m = dbMatches[i];
                this.matchesMap[m.matchId || m.id] = m;
            }
            this.buildRoundsFromMap();
        },

        buildRoundsFromMap: function () {
            var roundGroup = {};
            var keys = Object.keys(this.matchesMap);

            for (var i = 0; i < keys.length; i++) {
                var m = this.matchesMap[keys[i]];
                var r = m.roundNumber || 1;
                if (!roundGroup[r]) roundGroup[r] = [];
                roundGroup[r].push(m);
            }

            var rKeys = Object.keys(roundGroup).map(Number).sort(function (a, b) { return a - b; });
            this.roundsList = [];

            for (var j = 0; j < rKeys.length; j++) {
                var rNum = rKeys[j];
                var title = (window.TourmaBracketAlgorithm) ?
                    window.TourmaBracketAlgorithm.getRoundTitle(rNum, rKeys.length) : ('Round ' + rNum);

                this.roundsList.push({
                    roundNumber: rNum,
                    title: title,
                    matches: roundGroup[rNum]
                });
            }
        },

        /**
         * Send AJAX POST to SingleEliminationServlet
         */
        saveMatchResultAJAX: function (matchId, t1Score, t2Score, winner) {
            var params = new URLSearchParams();
            params.append('matchId', matchId);
            params.append('team1Score', t1Score);
            params.append('team2Score', t2Score);
            params.append('winner', winner);

            fetch('single-elimination', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
                },
                body: params.toString()
            }).then(function (res) {
                return res.json();
            }).then(function (data) {
                console.log('Match result saved:', data);
            }).catch(function (err) {
                console.warn('AJAX save notification:', err);
            });
        }
    };

    // Listen for custom tourmaMatchUpdated events
    document.addEventListener('tourmaMatchUpdated', function (e) {
        if (window.SingleEliminationEngine) {
            window.SingleEliminationEngine.onMatchUpdated(e.detail);
        }
    });

})();
