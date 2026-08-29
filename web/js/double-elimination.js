/**
 * ============================================================================
 * TOURMA - DOUBLE ELIMINATION CONTROLLER ENGINE (double-elimination.js)
 * Dual-Viewport Management (Upper & Lower), Realtime SVG Connectors,
 * Drag-to-Pan/Zoom, AJAX Persistence, and Modal Score Updates.
 * ============================================================================
 */

(function () {
    'use strict';

    window.TourmaDoubleElimination = {
        tournamentId: 'demo',
        tournamentName: 'Giải Đấu Double Elimination',
        format: 'DOUBLE_ELIMINATION',
        teamsList: [],
        bracketData: null,
        matchesMap: {},
        currentView: 'bracket',

        // Viewport scales
        upperScale: 1.0,
        lowerScale: 1.0,

        /**
         * Initialize Double Elimination Page
         */
        init: function (config) {
            if (config) {
                this.tournamentId = config.tournamentId || 'demo';
                this.tournamentName = config.tournamentName || 'Giải Đấu Double Elimination';
                this.teamsList = config.teamsList || [];
            }

            // Fallback load teams from localStorage if not provided
            if (!this.teamsList || this.teamsList.length === 0) {
                try {
                    var savedTeams = JSON.parse(localStorage.getItem('tourma_teams_' + this.tournamentId));
                    if (savedTeams && savedTeams.length > 0) {
                        this.teamsList = savedTeams;
                    }
                } catch (e) {}
            }

            if (!this.teamsList) {
                this.teamsList = [];
            }

            // Restore saved round inputs
            try {
                this.roundRandomInputs = JSON.parse(localStorage.getItem('tourma_de_round_inputs_' + this.tournamentId)) || {};
            } catch (e) {
                this.roundRandomInputs = {};
            }

            // Restore saved view mode
            var savedView = localStorage.getItem('tourma_de_view_' + this.tournamentId);
            if (savedView === 'list' || savedView === 'bracket') {
                this.currentView = savedView;
            }

            // Generate or Restore Matches Data
            this.loadOrGenerateBracketData();

            // Setup Quick Mode
            this.initQuickMode();

            // Setup Dual Viewport Controls
            this.initDualViewports();

            // Render Views
            this.renderAll();

            // Check Final Stage conclusion & render top banner if complete
            this.checkFinalStage();

            // Setup Global Event Listeners
            this.attachEventListeners();
        },

        /**
         * Load or Generate Bracket Structure
         */
        loadOrGenerateBracketData: function () {
            var storageKey = 'tourma_de_matches_' + this.tournamentId;
            var savedBracket = null;

            try {
                savedBracket = JSON.parse(localStorage.getItem(storageKey));
            } catch (e) {
                savedBracket = null;
            }

            // Check if saved data has played scores
            var hasPlayedScores = false;
            if (savedBracket && savedBracket.matchesMap) {
                var keys = Object.keys(savedBracket.matchesMap);
                for (var i = 0; i < keys.length; i++) {
                    var m = savedBracket.matchesMap[keys[i]];
                    var t1 = m.team1 ? m.team1.name : '';
                    var t2 = m.team2 ? m.team2.name : '';
                    var isBye = (t1 === 'BYE' || t2 === 'BYE');
                    if (!isBye && (m.status === 'COMPLETED' || (m.team1 && m.team1.score !== '') || (m.team2 && m.team2.score !== ''))) {
                        hasPlayedScores = true;
                        break;
                    }
                }
            }

            if (hasPlayedScores && savedBracket) {
                this.bracketData = savedBracket;
                this.matchesMap = savedBracket.matchesMap || {};
            } else if (this.teamsList && this.teamsList.length > 0 && window.TourmaDoubleElimAlgorithm) {
                this.bracketData = window.TourmaDoubleElimAlgorithm.generateDoubleElimination(this.teamsList);
                this.matchesMap = this.bracketData ? this.bracketData.matchesMap : {};
                this.persistLocal();
            } else {
                this.bracketData = null;
                this.matchesMap = {};
            }
        },

        /**
         * Initialize Pan & Zoom on Both Upper and Lower Viewports via TourmaViewport engine
         */
        initDualViewports: function () {
            var self = this;
            if (window.TourmaViewport && typeof window.TourmaViewport.init === 'function') {
                window.TourmaViewport.init('upperViewportContainer', 'upperViewportCanvas', {
                    badgeId: 'upperZoomBadge',
                    toolbarId: 'upperZoomToolbar',
                    onRedraw: function () { self.drawSvgConnectors(); }
                });
                window.TourmaViewport.init('lowerViewportContainer', 'lowerViewportCanvas', {
                    badgeId: 'lowerZoomBadge',
                    toolbarId: 'lowerZoomToolbar',
                    onRedraw: function () { self.drawSvgConnectors(); }
                });
            }
        },

        /**
         * Zoom In / Out Handlers delegating to TourmaViewport
         */
        zoomUpper: function (delta) {
            if (window.TourmaViewport) {
                if (delta > 0) window.TourmaViewport.zoomIn('upperViewportContainer');
                else window.TourmaViewport.zoomOut('upperViewportContainer');
            }
        },

        resetZoomUpper: function () {
            if (window.TourmaViewport) {
                window.TourmaViewport.resetZoom('upperViewportContainer');
            }
        },

        zoomLower: function (delta) {
            if (window.TourmaViewport) {
                if (delta > 0) window.TourmaViewport.zoomIn('lowerViewportContainer');
                else window.TourmaViewport.zoomOut('lowerViewportContainer');
            }
        },

        resetZoomLower: function () {
            if (window.TourmaViewport) {
                window.TourmaViewport.resetZoom('lowerViewportContainer');
            }
        },

        /**
         * Render All Views (Upper Bracket, Lower Bracket, Grand Finals, List View)
         */
        renderAll: function () {
            this.updateHeaderInfo();
            this.renderUpperBracket();
            this.renderLowerBracket();
            this.renderListView();
            this.applyViewMode(this.currentView);

            // Apply default zoom
            if (window.TourmaViewport) {
                window.TourmaViewport.setZoomOnInstance('upperViewportContainer', 1.0);
                window.TourmaViewport.setZoomOnInstance('lowerViewportContainer', 1.0);
            }

            var uCont = document.getElementById('upperViewportContainer');
            if (uCont && !this._hasScrolledUpper) {
                uCont.scrollTop = 0;
                uCont.scrollLeft = 0;
                this._hasScrolledUpper = true;
            }
            var lCont = document.getElementById('lowerViewportContainer');
            if (lCont && !this._hasScrolledLower) {
                lCont.scrollTop = 0;
                lCont.scrollLeft = 0;
                this._hasScrolledLower = true;
            }

            var self = this;
            requestAnimationFrame(function () {
                self.drawSvgConnectors();
            });
            setTimeout(function () { self.drawSvgConnectors(); }, 60);
            setTimeout(function () { self.drawSvgConnectors(); }, 200);
            setTimeout(function () { self.drawSvgConnectors(); }, 500);
        },

        /**
         * Update Tournament Header Badges
         */
        updateHeaderInfo: function () {
            var titleElem = document.getElementById('deTournamentTitle');
            var teamCountElem = document.getElementById('deTeamCountBadge');
            var formatElem = document.getElementById('deFormatBadge');

            if (titleElem) titleElem.innerText = this.tournamentName;
            if (teamCountElem) teamCountElem.innerText = this.teamsList.length + ' Đội';
            if (formatElem) formatElem.innerText = 'DOUBLE ELIMINATION';
        },

        renderEmptyState: function (containerElem) {
            if (!containerElem) return;
            if (window.TourmaEmptyTeamAlert && typeof window.TourmaEmptyTeamAlert.checkAndRender === 'function') {
                window.TourmaEmptyTeamAlert.checkAndRender(this.tournamentId, this.teamsList, containerElem);
            }
        },

        /**
         * Render Upper Bracket (including Grand Finals column at the end)
         */
        renderUpperBracket: function () {
            var dualWorkspace = document.getElementById('deDualViewportWorkspace');
            var alertContainer = document.getElementById('deEmptyAlertContainer');
            var wrapper = document.getElementById('upperBracketColumnsWrapper');
            if (!wrapper) return;
            wrapper.innerHTML = '';
            var self = this;

            var teamCount = (this.teamsList && Array.isArray(this.teamsList)) ? this.teamsList.length : 0;

            if (teamCount < 2 || !this.bracketData) {
                if (dualWorkspace) dualWorkspace.style.display = 'none';
                if (alertContainer) {
                    alertContainer.style.display = 'flex';
                    this.renderEmptyState(alertContainer);
                } else {
                    this.renderEmptyState(wrapper);
                }
                return;
            } else {
                if (dualWorkspace) dualWorkspace.style.display = 'flex';
                if (alertContainer) alertContainer.style.display = 'none';
            }

            var upperRounds = this.bracketData.upperRounds || [];
            var gfRound = this.bracketData.grandFinalsRound;

            // 1. Render Upper Rounds
            for (var r = 0; r < upperRounds.length; r++) {
                var ro = upperRounds[r];
                var col = document.createElement('div');
                col.className = 'de-round-column';
                col.dataset.roundNumber = ro.roundNumber;

                var h = document.createElement('div');
                h.className = 'de-round-header upper';

                var hTitle = document.createElement('span');
                hTitle.className = 'round-header-title';
                hTitle.innerText = ro.title;
                h.appendChild(hTitle);

                // Round Random Controls
                var canRandom = self.isRoundReadyForRandom(ro);
                var isAllDone = window.TourmaRandomService ? window.TourmaRandomService.isRoundAllCompleted(ro, self.matchesMap) : false;
                var canReset = window.TourmaRandomService ? window.TourmaRandomService.hasCompletedMatchesInRound(ro, self.matchesMap) : false;
                var ubKey = 'UPPER_' + ro.roundNumber;
                var rControls = document.createElement('div');
                rControls.className = 'round-header-random-controls';

                var rInput = document.createElement('input');
                rInput.type = 'number';
                rInput.className = 'round-random-input';
                rInput.placeholder = '-';
                rInput.min = '1';
                rInput.max = '999';
                rInput.value = (self.roundRandomInputs && self.roundRandomInputs[ubKey] !== undefined) ? self.roundRandomInputs[ubKey] : '';
                rInput.title = 'Điểm đội thắng (dùng cho Quick Mode và Random)';

                var rBtn = document.createElement('button');
                rBtn.type = 'button';
                rBtn.className = 'btn-round-random' + (canRandom ? '' : ' disabled');
                rBtn.innerText = 'Random';
                rBtn.title = canRandom ? 'Random kết quả vòng đấu' : (isAllDone ? 'Tất cả các trận đã hoàn thành (bấm nút Reset để mở lại)' : 'Vòng đấu chưa xác định đủ các đội');
                if (!canRandom) rBtn.disabled = true;

                var rResetBtn = document.createElement('button');
                rResetBtn.type = 'button';
                rResetBtn.className = 'btn-round-reset' + (canReset ? '' : ' disabled');
                rResetBtn.innerHTML = '<i class="fa-solid fa-arrows-rotate"></i>';
                rResetBtn.title = canReset ? 'Reset kết quả vòng đấu' : 'Vòng đấu chưa có trận nào hoàn thành';
                if (!canReset) rResetBtn.disabled = true;

                (function (bType, rNum, currentKey, inp) {
                    var saveUbInput = function () {
                        if (!self.roundRandomInputs) self.roundRandomInputs = {};
                        if (inp.value === '') {
                            delete self.roundRandomInputs[currentKey];
                        } else {
                            self.roundRandomInputs[currentKey] = inp.value;
                        }
                        try {
                            localStorage.setItem('tourma_de_round_inputs_' + self.tournamentId, JSON.stringify(self.roundRandomInputs));
                        } catch (e) {}
                    };

                    inp.oninput = saveUbInput;
                    inp.onchange = saveUbInput;

                    rBtn.onclick = function (e) {
                        e.stopPropagation();
                        self.randomizeRound(bType, rNum, inp.value);
                    };
                    rResetBtn.onclick = function (e) {
                        e.stopPropagation();
                        self.resetRound(bType, rNum);
                    };
                })('UPPER', ro.roundNumber, ubKey, rInput);

                rControls.appendChild(rInput);
                rControls.appendChild(rBtn);
                rControls.appendChild(rResetBtn);
                h.appendChild(rControls);

                col.appendChild(h);

                var stack = document.createElement('div');
                stack.className = 'de-round-matches-stack';

                var showRound1Byes = window.TourmaBracketAlgorithm ? window.TourmaBracketAlgorithm.shouldShowRound1Byes(self.ubRounds, 0.50) : true;

                for (var m = 0; m < ro.matches.length; m++) {
                    var matchObj = this.matchesMap[ro.matches[m].matchId] || ro.matches[m];
                    var t1Name = matchObj.team1 ? matchObj.team1.name : '';
                    var t2Name = matchObj.team2 ? matchObj.team2.name : '';
                    var isBye = matchObj.isBye || (t1Name === 'BYE' || t2Name === 'BYE');

                    matchObj.hideByeSlot = (isBye && !showRound1Byes);

                    if (window.TourmaBracketCard) {
                        var cardElem = window.TourmaBracketCard.createNodeElement ? 
                            window.TourmaBracketCard.createNodeElement(matchObj) : 
                            window.TourmaBracketCard.createCardElement(matchObj);
                        if (cardElem) stack.appendChild(cardElem);
                    }
                }
                col.appendChild(stack);
                wrapper.appendChild(col);
            }

            // 2. Append Grand Finals Column to Upper Bracket
            if (gfRound && gfRound.matches) {
                var gfCol = document.createElement('div');
                gfCol.className = 'de-round-column grand-finals';
                gfCol.dataset.roundNumber = gfRound.roundNumber;

                var gfH = document.createElement('div');
                gfH.className = 'de-round-header gf';

                var gfTitle = document.createElement('span');
                gfTitle.className = 'round-header-title';
                gfTitle.innerText = 'Grand Finals';
                gfH.appendChild(gfTitle);

                // Round Random Controls for GF
                var canGfRandom = self.isRoundReadyForRandom(gfRound);
                var isGfAllDone = window.TourmaRandomService ? window.TourmaRandomService.isRoundAllCompleted(gfRound, self.matchesMap) : false;
                var canGfReset = window.TourmaRandomService ? window.TourmaRandomService.hasCompletedMatchesInRound(gfRound, self.matchesMap) : false;
                var gfKey = 'GRAND_FINAL_' + gfRound.roundNumber;
                var gfControls = document.createElement('div');
                gfControls.className = 'round-header-random-controls';

                var gfInput = document.createElement('input');
                gfInput.type = 'number';
                gfInput.className = 'round-random-input';
                gfInput.placeholder = '-';
                gfInput.min = '1';
                gfInput.max = '999';
                gfInput.value = (self.roundRandomInputs && self.roundRandomInputs[gfKey] !== undefined) ? self.roundRandomInputs[gfKey] : '';
                gfInput.title = 'Điểm đội thắng (dùng cho Quick Mode và Random)';

                var gfBtn = document.createElement('button');
                gfBtn.type = 'button';
                gfBtn.className = 'btn-round-random' + (canGfRandom ? '' : ' disabled');
                gfBtn.innerText = 'Random';
                gfBtn.title = canGfRandom ? 'Random kết quả Grand Finals' : (isGfAllDone ? 'Tất cả các trận đã hoàn thành (bấm nút Reset để mở lại)' : 'Vòng đấu chưa xác định đủ các đội');
                if (!canGfRandom) gfBtn.disabled = true;

                var gfResetBtn = document.createElement('button');
                gfResetBtn.type = 'button';
                gfResetBtn.className = 'btn-round-reset' + (canGfReset ? '' : ' disabled');
                gfResetBtn.innerHTML = '<i class="fa-solid fa-arrows-rotate"></i>';
                gfResetBtn.title = canGfReset ? 'Reset kết quả Grand Finals' : 'Vòng đấu chưa có trận nào hoàn thành';
                if (!canGfReset) gfResetBtn.disabled = true;

                (function (bType, rNum, currentKey, inp) {
                    var saveGfInput = function () {
                        if (!self.roundRandomInputs) self.roundRandomInputs = {};
                        if (inp.value === '') {
                            delete self.roundRandomInputs[currentKey];
                        } else {
                            self.roundRandomInputs[currentKey] = inp.value;
                        }
                        try {
                            localStorage.setItem('tourma_de_round_inputs_' + self.tournamentId, JSON.stringify(self.roundRandomInputs));
                        } catch (e) {}
                    };

                    inp.oninput = saveGfInput;
                    inp.onchange = saveGfInput;

                    gfBtn.onclick = function (e) {
                        e.stopPropagation();
                        self.randomizeRound(bType, rNum, inp.value);
                    };
                    gfResetBtn.onclick = function (e) {
                        e.stopPropagation();
                        self.resetRound(bType, rNum);
                    };
                })('GRAND_FINAL', gfRound.roundNumber, gfKey, gfInput);

                gfControls.appendChild(gfInput);
                gfControls.appendChild(gfBtn);
                gfControls.appendChild(gfResetBtn);
                gfH.appendChild(gfControls);

                gfCol.appendChild(gfH);

                var gfStack = document.createElement('div');
                gfStack.className = 'de-round-matches-stack';

                for (var g = 0; g < gfRound.matches.length; g++) {
                    var gfMatch = this.matchesMap[gfRound.matches[g].matchId] || gfRound.matches[g];
                    if (window.TourmaBracketCard) {
                        var gfCard = window.TourmaBracketCard.createNodeElement ?
                            window.TourmaBracketCard.createNodeElement(gfMatch) :
                            window.TourmaBracketCard.createCardElement(gfMatch);
                        if (gfCard) {
                            if (gfMatch.isResetMatch && !gfMatch.isUnlocked) {
                                gfCard.classList.add('gf-reset-locked');
                                gfCard.title = 'Trận Reset Final chỉ mở khóa khi Đội Nhánh Thua thắng trận Grand Final 1';
                            }
                            gfStack.appendChild(gfCard);
                        }
                    }
                }
                gfCol.appendChild(gfStack);
                wrapper.appendChild(gfCol);
            }
        },

        /**
         * Render Lower Bracket
         */
        renderLowerBracket: function () {
            var wrapper = document.getElementById('lowerBracketColumnsWrapper');
            if (!wrapper || !this.bracketData) return;
            wrapper.innerHTML = '';
            var self = this;

            var lowerRounds = this.bracketData.lowerRounds || [];

            for (var lr = 0; lr < lowerRounds.length; lr++) {
                var lro = lowerRounds[lr];
                var lbKey = 'LOWER_' + lro.roundNumber;
                var col = document.createElement('div');
                col.className = 'de-round-column';
                col.dataset.roundNumber = lro.roundNumber;

                var h = document.createElement('div');
                h.className = 'de-round-header lower';

                var lTitle = document.createElement('span');
                lTitle.className = 'round-header-title';
                lTitle.innerText = lro.title;
                h.appendChild(lTitle);

                // Round Random Controls for LB
                var canLbRandom = self.isRoundReadyForRandom(lro);
                var isLbAllDone = window.TourmaRandomService ? window.TourmaRandomService.isRoundAllCompleted(lro, self.matchesMap) : false;
                var canLbReset = window.TourmaRandomService ? window.TourmaRandomService.hasCompletedMatchesInRound(lro, self.matchesMap) : false;
                var lbControls = document.createElement('div');
                lbControls.className = 'round-header-random-controls';

                var lbInput = document.createElement('input');
                lbInput.type = 'number';
                lbInput.className = 'round-random-input';
                lbInput.placeholder = '-';
                lbInput.min = '1';
                lbInput.max = '999';
                lbInput.value = (self.roundRandomInputs && self.roundRandomInputs[lbKey] !== undefined) ? self.roundRandomInputs[lbKey] : '';
                lbInput.title = 'Điểm đội thắng (dùng cho Quick Mode và Random)';

                var lbBtn = document.createElement('button');
                lbBtn.type = 'button';
                lbBtn.className = 'btn-round-random' + (canLbRandom ? '' : ' disabled');
                lbBtn.innerText = 'Random';
                lbBtn.title = canLbRandom ? 'Random kết quả vòng đấu' : (isLbAllDone ? 'Tất cả các trận đã hoàn thành (bấm nút Reset để mở lại)' : 'Vòng đấu chưa xác định đủ các đội');
                if (!canLbRandom) lbBtn.disabled = true;

                var lbResetBtn = document.createElement('button');
                lbResetBtn.type = 'button';
                lbResetBtn.className = 'btn-round-reset' + (canLbReset ? '' : ' disabled');
                lbResetBtn.innerHTML = '<i class="fa-solid fa-arrows-rotate"></i>';
                lbResetBtn.title = canLbReset ? 'Reset kết quả vòng đấu' : 'Vòng đấu chưa có trận nào hoàn thành';
                if (!canLbReset) lbResetBtn.disabled = true;

                (function (bType, rNum, currentKey, inp) {
                    var saveLbInput = function () {
                        if (!self.roundRandomInputs) self.roundRandomInputs = {};
                        if (inp.value === '') {
                            delete self.roundRandomInputs[currentKey];
                        } else {
                            self.roundRandomInputs[currentKey] = inp.value;
                        }
                        try {
                            localStorage.setItem('tourma_de_round_inputs_' + self.tournamentId, JSON.stringify(self.roundRandomInputs));
                        } catch (e) {}
                    };

                    inp.oninput = saveLbInput;
                    inp.onchange = saveLbInput;

                    lbBtn.onclick = function (e) {
                        e.stopPropagation();
                        self.randomizeRound(bType, rNum, inp.value);
                    };
                    lbResetBtn.onclick = function (e) {
                        e.stopPropagation();
                        self.resetRound(bType, rNum);
                    };
                })('LOWER', lro.roundNumber, lbKey, lbInput);

                lbControls.appendChild(lbInput);
                lbControls.appendChild(lbBtn);
                lbControls.appendChild(lbResetBtn);
                h.appendChild(lbControls);

                col.appendChild(h);

                var stack = document.createElement('div');
                stack.className = 'de-round-matches-stack';

                for (var lm = 0; lm < lro.matches.length; lm++) {
                    var matchObj = this.matchesMap[lro.matches[lm].matchId] || lro.matches[lm];
                    if (window.TourmaBracketCard) {
                        var cardElem = window.TourmaBracketCard.createNodeElement ?
                            window.TourmaBracketCard.createNodeElement(matchObj) :
                            window.TourmaBracketCard.createCardElement(matchObj);
                        if (cardElem) stack.appendChild(cardElem);
                    }
                }
                col.appendChild(stack);
                wrapper.appendChild(col);
            }
        },

        activeRoundFilter: 'all',

        /**
         * Get concise round abbreviation for filter tabs (UB R1, UB Finals, LB R1, LB Finals, Grand Finals)
         */
        getShortRoundName: function (ro) {
            var bType = (ro.bracketType || '').toUpperCase();
            var rNum = ro.roundNumber;
            var t = (ro.title || '').toLowerCase();

            if (bType === 'GRAND_FINAL' || bType === 'GF' || t.includes('grand')) {
                return 'Grand Finals';
            }
            if (bType === 'UPPER') {
                if (t.includes('final') || t.includes('chung kết')) return 'UB Finals';
                return 'UB R' + rNum;
            }
            if (bType === 'LOWER') {
                if (t.includes('final') || t.includes('chung kết')) return 'LB Finals';
                return 'LB R' + rNum;
            }
            return 'R' + rNum;
        },

        /**
         * Render List View
         */
        renderListView: function () {
            var container = document.getElementById('deListViewContainer');
            if (!container) return;
            container.innerHTML = '';
            var self = this;

            var teamCount = (this.teamsList && Array.isArray(this.teamsList)) ? this.teamsList.length : 0;
            if (teamCount < 2 || !this.bracketData) {
                this.renderEmptyState(container);
                return;
            }

            var allRounds = (window.TourmaDoubleElimAlgorithm) ?
                window.TourmaDoubleElimAlgorithm.filterMatchesForListView(this.bracketData) : [];

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
                var roTab = allRounds[i];
                var tabKey = roTab.bracketType + '_' + roTab.roundNumber;
                var tab = document.createElement('button');
                tab.type = 'button';
                var isAct = (self.activeRoundFilter === tabKey);
                tab.className = 'rr-round-tab-btn' + (isAct ? ' active' : '');
                tab.innerText = self.getShortRoundName(roTab);

                (function (key) {
                    tab.onclick = function () {
                        self.activeRoundFilter = key;
                        self.renderListView();
                    };
                })(tabKey);
                tabsBar.appendChild(tab);
            }
            container.appendChild(tabsBar);

            // 2. Filter Rounds to display
            var roundsList = allRounds;
            if (self.activeRoundFilter !== 'all') {
                roundsList = allRounds.filter(function (r) {
                    return (r.bracketType + '_' + r.roundNumber) === self.activeRoundFilter;
                });
            }

            for (var i = 0; i < roundsList.length; i++) {
                var ro = roundsList[i];
                var listKey = ro.bracketType + '_' + ro.roundNumber;

                var sectionHeader = document.createElement('div');
                var typeClass = (ro.bracketType === 'UPPER') ? 'upper' : (ro.bracketType === 'LOWER' ? 'lower' : 'gf');
                sectionHeader.className = 'de-list-section-header ' + typeClass;

                var secTitle = document.createElement('span');
                secTitle.className = 'round-header-title';
                secTitle.innerText = ro.title;
                sectionHeader.appendChild(secTitle);

                // Round Random Controls in list section header
                var canListRandom = self.isRoundReadyForRandom(ro);
                var isListAllDone = window.TourmaRandomService ? window.TourmaRandomService.isRoundAllCompleted(ro, self.matchesMap) : false;
                var canListReset = window.TourmaRandomService ? window.TourmaRandomService.hasCompletedMatchesInRound(ro, self.matchesMap) : false;
                var sControls = document.createElement('div');
                sControls.className = 'round-header-random-controls';

                var sInp = document.createElement('input');
                sInp.type = 'number';
                sInp.className = 'round-random-input';
                sInp.placeholder = '-';
                sInp.min = '1';
                sInp.max = '999';
                sInp.value = (self.roundRandomInputs && self.roundRandomInputs[listKey]) ? self.roundRandomInputs[listKey] : '';
                sInp.title = canListRandom ? 'Điểm đội thắng' : (isListAllDone ? 'Tất cả các trận đã hoàn thành' : 'Vòng đấu chưa xác định đủ các đội');
                if (!canListRandom) sInp.disabled = true;
                sInp.oninput = function () {
                    if (!self.roundRandomInputs) self.roundRandomInputs = {};
                    self.roundRandomInputs[listKey] = this.value;
                };

                var sBtn = document.createElement('button');
                sBtn.type = 'button';
                sBtn.className = 'btn-round-random' + (canListRandom ? '' : ' disabled');
                sBtn.innerText = 'Random';
                sBtn.title = canListRandom ? 'Random kết quả vòng đấu' : (isListAllDone ? 'Tất cả các trận đã hoàn thành (bấm nút Reset để mở lại)' : 'Vòng đấu chưa xác định đủ các đội');
                if (!canListRandom) sBtn.disabled = true;

                var sResetBtn = document.createElement('button');
                sResetBtn.type = 'button';
                sResetBtn.className = 'btn-round-reset' + (canListReset ? '' : ' disabled');
                sResetBtn.innerHTML = '<i class="fa-solid fa-arrows-rotate"></i>';
                sResetBtn.title = canListReset ? 'Reset kết quả vòng đấu' : 'Vòng đấu chưa có trận nào hoàn thành';
                if (!canListReset) sResetBtn.disabled = true;

                (function (bType, rNum, inp) {
                    sBtn.onclick = function (e) {
                        e.stopPropagation();
                        self.randomizeRound(bType, rNum, inp.value);
                    };
                    sResetBtn.onclick = function (e) {
                        e.stopPropagation();
                        self.resetRound(bType, rNum);
                    };
                })(ro.bracketType, ro.roundNumber, sInp);

                sControls.appendChild(sInp);
                sControls.appendChild(sBtn);
                sControls.appendChild(sResetBtn);
                sectionHeader.appendChild(sControls);

                container.appendChild(sectionHeader);

                for (var m = 0; m < ro.matches.length; m++) {
                    var mData = this.matchesMap[ro.matches[m].matchId] || ro.matches[m];
                    if (mData) mData.bracketType = ro.bracketType;
                    if (window.TourmaMatchCard) {
                        var cardElem = window.TourmaMatchCard.createCardElement(mData);
                        if (cardElem) container.appendChild(cardElem);
                    }
                }
            }
        },

        /**
         * Check if all matches in a DE round have determined teams and need completion
         */
        isRoundReadyForRandom: function (roundObj) {
            if (window.TourmaRandomService) {
                return window.TourmaRandomService.isRoundReadyForRandom(roundObj, this.matchesMap);
            }
            return false;
        },

        /**
         * Randomize all playable uncompleted matches in a specific DE round via TourmaRandomService
         */
        randomizeRound: function (bracketType, roundNumber, rawWinScore) {
            if (window.FinalStagePopup && window.FinalStagePopup.isLocked) {
                alert('Giải đấu đã kết thúc và đang ở trạng thái khóa. Vui lòng bấm "Mở khóa" trên thanh thông báo nếu muốn chỉnh sửa kết quả.');
                return;
            }
            if (!this.bracketData) return;

            var targetRound = null;
            if (bracketType === 'UPPER') {
                var uRounds = this.bracketData.upperRounds || [];
                for (var i = 0; i < uRounds.length; i++) {
                    if (uRounds[i].roundNumber === Number(roundNumber)) { targetRound = uRounds[i]; break; }
                }
            } else if (bracketType === 'LOWER') {
                var lRounds = this.bracketData.lowerRounds || [];
                for (var i = 0; i < lRounds.length; i++) {
                    if (lRounds[i].roundNumber === Number(roundNumber)) { targetRound = lRounds[i]; break; }
                }
            } else if (bracketType === 'GRAND_FINAL' || bracketType === 'GF') {
                targetRound = this.bracketData.grandFinalsRound;
            }

            if (!targetRound || !targetRound.matches) return;

            var self = this;
            var changed = false;

            if (window.TourmaRandomService) {
                changed = window.TourmaRandomService.randomizeRoundMatches(
                    targetRound,
                    this.matchesMap,
                    rawWinScore,
                    function (mId, winnerSlot, isT1Winner, s1, s2) {
                        if (window.TourmaDoubleElimAlgorithm) {
                            window.TourmaDoubleElimAlgorithm.propagateMatchResult(self.matchesMap, mId, winnerSlot, isT1Winner);
                        }
                    }
                );
            }

            if (changed) {
                if (window.TourmaDoubleElimAlgorithm) {
                    window.TourmaDoubleElimAlgorithm.renumberDoubleEliminationContiguously(this.bracketData);
                }
                this.persistLocal();
                this.renderUpperBracket();
                this.renderLowerBracket();
                this.renderListView();
                this.applyViewMode(this.currentView || 'bracket');
                var self = this;
                requestAnimationFrame(function () { self.drawSvgConnectors(); });
                setTimeout(function () { self.drawSvgConnectors(); }, 60);
                setTimeout(function () { self.drawSvgConnectors(); }, 200);
            }
        },

        /**
         * Reset all matches in a specific DE round and cascade resets downstream
         */
        resetRound: function (bracketType, roundNumber) {
            if (window.FinalStagePopup && window.FinalStagePopup.isLocked) {
                alert('Giải đấu đã kết thúc và đang ở trạng thái khóa. Vui lòng bấm "Mở khóa" trên thanh thông báo nếu muốn chỉnh sửa kết quả.');
                return;
            }
            if (!this.bracketData) return;

            var targetRound = null;
            if (bracketType === 'UPPER') {
                var uRounds = this.bracketData.upperRounds || [];
                for (var i = 0; i < uRounds.length; i++) {
                    if (uRounds[i].roundNumber === Number(roundNumber)) { targetRound = uRounds[i]; break; }
                }
            } else if (bracketType === 'LOWER') {
                var lRounds = this.bracketData.lowerRounds || [];
                for (var i = 0; i < lRounds.length; i++) {
                    if (lRounds[i].roundNumber === Number(roundNumber)) { targetRound = lRounds[i]; break; }
                }
            } else if (bracketType === 'GRAND_FINAL' || bracketType === 'GF') {
                targetRound = this.bracketData.grandFinalsRound;
            }

            if (!targetRound || !targetRound.matches) return;

            var self = this;
            var changed = false;

            if (window.TourmaRandomService) {
                changed = window.TourmaRandomService.resetRoundMatches(
                    targetRound,
                    this.matchesMap,
                    function (mId) {
                        if (window.TourmaDoubleElimAlgorithm) {
                            window.TourmaDoubleElimAlgorithm.cascadeResetPlaceholders(self.matchesMap, mId);
                        }
                    }
                );
            }

            if (changed) {
                if (window.TourmaDoubleElimAlgorithm) {
                    window.TourmaDoubleElimAlgorithm.renumberDoubleEliminationContiguously(this.bracketData);
                }
                this.persistLocal();
                this.renderUpperBracket();
                this.renderLowerBracket();
                this.renderListView();
                this.applyViewMode(this.currentView || 'bracket');
                var self = this;
                requestAnimationFrame(function () { self.drawSvgConnectors(); });
                setTimeout(function () { self.drawSvgConnectors(); }, 60);
                setTimeout(function () { self.drawSvgConnectors(); }, 200);
            }
        },

        /**
         * Draw SVG Connectors for Upper and Lower Brackets via TourmaViewport engine
         */
        drawSvgConnectors: function () {
            var upperCanvas = document.getElementById('upperViewportCanvas');
            var upperWrapper = document.getElementById('upperBracketColumnsWrapper');
            var lowerCanvas = document.getElementById('lowerViewportCanvas');
            var lowerWrapper = document.getElementById('lowerBracketColumnsWrapper');

            if (window.TourmaViewport && typeof window.TourmaViewport.drawConnectors === 'function') {
                if (upperCanvas && upperWrapper) {
                    window.TourmaViewport.drawConnectors(upperCanvas, upperWrapper, this.matchesMap);
                }
                if (lowerCanvas && lowerWrapper) {
                    window.TourmaViewport.drawConnectors(lowerCanvas, lowerWrapper, this.matchesMap);
                }
            }
        },

        /**
         * Switch between Dual Viewport Bracket mode & List View mode
         */
        setViewMode: function (mode) {
            this.currentView = mode;
            localStorage.setItem('tourma_de_view_' + this.tournamentId, mode);
            this.applyViewMode(mode);
        },

        applyViewMode: function (mode) {
            var dualWorkspace = document.getElementById('deDualViewportWorkspace');
            var listContainer = document.getElementById('deListViewContainer');
            var alertContainer = document.getElementById('deEmptyAlertContainer');
            var btnBracket = document.getElementById('deBtnBracketView');
            var btnList = document.getElementById('deBtnListView');

            var teamCount = (this.teamsList && Array.isArray(this.teamsList)) ? this.teamsList.length : 0;

            if (teamCount < 2 || !this.bracketData) {
                if (dualWorkspace) dualWorkspace.style.display = 'none';
                if (listContainer) listContainer.style.display = 'none';
                if (alertContainer) {
                    alertContainer.style.display = 'flex';
                    this.renderEmptyState(alertContainer);
                }
                return;
            }

            if (alertContainer) alertContainer.style.display = 'none';

            if (mode === 'list') {
                if (dualWorkspace) dualWorkspace.style.display = 'none';
                if (listContainer) listContainer.style.display = 'flex';
                if (btnBracket) btnBracket.classList.remove('active');
                if (btnList) btnList.classList.add('active');
            } else {
                if (dualWorkspace) dualWorkspace.style.display = 'flex';
                if (listContainer) listContainer.style.display = 'none';
                if (btnBracket) btnBracket.classList.add('active');
                if (btnList) btnList.classList.remove('active');
                var self = this;
                setTimeout(function () { self.drawSvgConnectors(); }, 50);
            }
        },

        /**
         * Quick Mode Management (1-click winner selection for DE)
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
            this.renderUpperBracket();
            this.renderLowerBracket();
            this.renderListView();
        },

        updateQuickModeUI: function () {
            var btn = document.getElementById('deBtnQuickMode');
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

            var m = this.matchesMap[matchId];
            if (!m) return;

            var t1 = m.team1 ? m.team1.name : '';
            var t2 = m.team2 ? m.team2.name : '';
            if (!t1 || !t2 || t1 === 'BYE' || t2 === 'BYE' ||
                t1.startsWith('W #') || t1.startsWith('L #') ||
                t2.startsWith('W #') || t2.startsWith('L #') ||
                t1 === 'Winner UB' || t2 === 'Winner LB') return;

            if (m.isResetMatch && !m.isUnlocked) return;

            var isT1Winner = (winnerSlot === 1);
            var isT2Winner = (winnerSlot === 2);

            // Determine winning score from passed customScore or stored Round Score Input (Default 1 if not specified)
            var winningScore = '1';
            if (customScore && Number(customScore) > 0) {
                winningScore = String(customScore);
            } else {
                var roundKey = null;
                if (m.bracketType === 'UPPER') {
                    roundKey = 'UPPER_' + m.roundNumber;
                } else if (m.bracketType === 'LOWER') {
                    roundKey = 'LOWER_' + m.roundNumber;
                } else if (m.bracketType === 'GRAND_FINAL' || m.bracketType === 'GF') {
                    roundKey = 'GRAND_FINAL_' + m.roundNumber;
                    if (!this.roundRandomInputs || !this.roundRandomInputs[roundKey]) {
                        roundKey = 'GRAND_FINAL';
                    }
                }

                if (roundKey && this.roundRandomInputs && this.roundRandomInputs[roundKey]) {
                    var cScore = this.roundRandomInputs[roundKey];
                    if (cScore && Number(cScore) > 0) {
                        winningScore = String(cScore);
                    }
                }
            }

            m.winnerId = isT1Winner ? 'team1' : 'team2';
            m.status = 'COMPLETED';

            var winNum = Number(winningScore);
            var losingScore = '0';
            if (winNum > 1) {
                losingScore = String(Math.floor(Math.random() * winNum)); // Range: [0, X-1]
            }

            m.team1.score = isT1Winner ? winningScore : losingScore;
            m.team2.score = isT2Winner ? winningScore : losingScore;

            var winnerName = isT1Winner ? t1 : t2;

            if (window.TourmaDoubleElimAlgorithm) {
                window.TourmaDoubleElimAlgorithm.propagateMatchResult(this.matchesMap, matchId, m.winnerId, isT1Winner);
                window.TourmaDoubleElimAlgorithm.renumberDoubleEliminationContiguously(this.bracketData);
            }

            this.persistLocal();
            this.renderUpperBracket();
            this.renderLowerBracket();
            this.renderListView();
            this.applyViewMode(this.currentView || 'bracket');

            this.saveMatchAJAX(matchId, m.team1.score, m.team2.score, winnerName);
        },

        /**
         * Attach Match Updates & Global Event Listeners
         */
        attachEventListeners: function () {
            var self = this;

            // Global Match Update Event (from Popup Score Modal)
            window.addEventListener('tourmaMatchUpdated', function (e) {
                var detail = e.detail;
                if (!detail || !detail.matchId) return;

                var mId = detail.matchId;
                var curr = self.matchesMap[mId];
                if (!curr) return;

                curr.team1.score = detail.team1Score;
                curr.team2.score = detail.team2Score;
                curr.winnerId = detail.winner;
                curr.status = 'COMPLETED';

                var isT1Winner = (detail.winner === 'team1');

                // Propagate results across Upper, Lower, and Grand Finals
                if (window.TourmaDoubleElimAlgorithm) {
                    window.TourmaDoubleElimAlgorithm.propagateMatchResult(self.matchesMap, mId, detail.winner, isT1Winner);
                    window.TourmaDoubleElimAlgorithm.renumberDoubleEliminationContiguously(self.bracketData);
                }

                // Persist state
                self.persistLocal();
                self.persistAjax(curr);

                // Re-render
                self.renderAll();
                self.applyViewMode(self.currentView || 'bracket');
            });

            window.addEventListener('resize', function () {
                self.drawSvgConnectors();
            });
        },

        /**
         * Open / Close / Confirm Reset Bracket Modal
         */
        openResetModal: function () {
            if (window.FinalStagePopup && window.FinalStagePopup.isLocked) {
                alert('Giải đấu đã kết thúc và đang ở trạng thái khóa. Vui lòng bấm "Mở khóa" trên thanh thông báo nếu muốn reset giải.');
                return;
            }
            var modal = document.getElementById('deResetModalBackdrop');
            if (modal) {
                modal.classList.add('show');
                document.body.style.overflow = 'hidden';
            }
        },

        closeResetModal: function () {
            var modal = document.getElementById('deResetModalBackdrop');
            if (modal) {
                modal.classList.remove('show');
                document.body.style.overflow = '';
            }
        },

        confirmResetBracket: function () {
            this.closeResetModal();
            this.roundRandomInputs = {};
            try {
                localStorage.removeItem('tourma_de_matches_' + this.tournamentId);
                localStorage.removeItem('tourma_matches_' + this.tournamentId);
                localStorage.removeItem('tourma_de_round_inputs_' + this.tournamentId);
            } catch (e) {}

            // Re-generate fresh initial bracket
            if (window.TourmaDoubleElimAlgorithm && typeof window.TourmaDoubleElimAlgorithm.generateDoubleElimination === 'function') {
                this.bracketData = window.TourmaDoubleElimAlgorithm.generateDoubleElimination(this.teamsList);
                this.matchesMap = this.bracketData ? this.bracketData.matchesMap : {};
                this.persistLocal();
                this.renderAll();
            }
        },

        /**
         * Persist to LocalStorage (both DE and universal matches key for format lock detection)
         */
        persistLocal: function () {
            try {
                localStorage.setItem('tourma_de_matches_' + this.tournamentId, JSON.stringify(this.bracketData));
                localStorage.setItem('tourma_matches_' + this.tournamentId, JSON.stringify(this.matchesMap));
                if (this.teamsList && this.teamsList.length > 0) {
                    localStorage.setItem('tourma_teams_' + this.tournamentId, JSON.stringify(this.teamsList));
                }
            } catch (e) {}
            this.checkFinalStage();
        },

        checkFinalStage: function () {
            var self = this;
            if (window.FinalStagePopup) {
                window.FinalStagePopup.checkAndRender(
                    this.tournamentId,
                    'DOUBLE_ELIMINATION',
                    this.matchesMap,
                    this.teamsList,
                    null,
                    function (isLocked) {
                        if (isLocked) {
                            self.isQuickMode = false;
                            window.TourmaQuickMode = false;
                            var qBtn = document.getElementById('deBtnQuickMode');
                            if (qBtn) qBtn.style.display = 'none';
                        }
                    }
                );
            }
        },

        /**
         * Persist Match Update to Database via AJAX
         */
        persistAjax: function (matchObj) {
            if (!matchObj) return;
            var contextPath = window.TourmaContextPath || '';
            var url = contextPath + '/double-elimination';

            var params = new URLSearchParams();
            params.append('matchId', matchObj.matchId);
            params.append('team1Score', matchObj.team1.score || '0');
            params.append('team2Score', matchObj.team2.score || '0');
            params.append('winner', matchObj.winnerId || 'team1');

            fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                body: params.toString()
            }).catch(function () {});
        }
    };

})();
