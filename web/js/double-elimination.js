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
                this.cutTarget = config.cutTarget || 0;
                this.tournamentType = config.tournamentType || 'SINGLE_STAGE';
                this._preloadedTeams = config.teamsList || [];
                this.currentStage = (config.stage === 2 || config.stage === '2') ? 2 : 1;
            }

            var stageParam = new URLSearchParams(window.location.search).get('stage');
            if (stageParam === '2' || stageParam === 2) {
                this.currentStage = 2;
            } else if (stageParam === '1' || stageParam === 1) {
                this.currentStage = 1;
            } else if (!this.currentStage) {
                this.currentStage = 1;
            }

            // Check Stage 2 Lock immediately
            if (this.currentStage === 2 && window.StageFinishAlert && typeof window.StageFinishAlert.checkAndRender === 'function') {
                var isBlocked = window.StageFinishAlert.checkAndRender(this.tournamentId, 2, 'deEmptyAlertContainer');
                if (isBlocked) {
                    var dualWs = document.getElementById('deDualViewportWorkspace');
                    if (dualWs) dualWs.style.display = 'none';
                    var listV = document.getElementById('deListViewContainer');
                    if (listV) listV.style.display = 'none';
                    var emptyAlert = document.getElementById('deEmptyAlertContainer');
                    if (emptyAlert) emptyAlert.style.display = 'flex';
                    return; // Stop initialization completely!
                }
            }

            // cutTarget from localStorage only relevant for multi-stage (when not already set by JSP/config)
            if (!this.cutTarget) {
                // Only read from localStorage for Stage 2 (multi-stage scenario)
                if (this.currentStage === 2) {
                    try {
                        var rawCut = localStorage.getItem('tourma_advance_count_' + this.tournamentId) ||
                                     localStorage.getItem('tourma_cut_target_' + this.tournamentId);
                        if (rawCut) this.cutTarget = parseInt(rawCut, 10);
                    } catch (e) {}
                }
            }

            // Load teams — JSP preloadedTeams is the authoritative source (already resolved & sliced by server)
            var stage2Teams = null;
            try {
                stage2Teams = JSON.parse(localStorage.getItem('tourma_stage2_teams_' + this.tournamentId));
            } catch (e) {}

            var allTeamsRaw = null;
            try {
                allTeamsRaw = JSON.parse(localStorage.getItem('tourma_teams_' + this.tournamentId));
            } catch (e) {}

            var findOriginalSeed = function(teamName, defaultSeed) {
                if (!teamName) return defaultSeed;
                if (allTeamsRaw && Array.isArray(allTeamsRaw)) {
                    for (var idx = 0; idx < allTeamsRaw.length; idx++) {
                        var at = allTeamsRaw[idx];
                        var atName = (typeof at === 'object') ? (at.name || at.rawName) : at;
                        if (atName === teamName) {
                            return (typeof at === 'object' && at.seed !== undefined && at.seed !== null) ? at.seed : (idx + 1);
                        }
                    }
                }
                return defaultSeed;
            };

            var mapTeam = function(t, i) {
                var tName = (typeof t === 'object') ? (t.name || t.rawName) : t;
                var tSeed = (typeof t === 'object' && t.seed !== undefined && t.seed !== null) ? t.seed : findOriginalSeed(tName, i + 1);
                return { name: tName, rawName: tName, seed: tSeed };
            };

            // Priority 1: JSP server-provided teams (authoritative — already sliced to correct count)
            if (this._preloadedTeams && this._preloadedTeams.length > 0) {
                this.teamsList = this._preloadedTeams.map(mapTeam);
            // Priority 2: stage2Teams from localStorage (only when JSP sent nothing, e.g. demo mode)
            } else if (this.currentStage === 2 && stage2Teams && stage2Teams.length > 0) {
                this.teamsList = stage2Teams.map(mapTeam);
            } else {
                // Priority 3: Fallback from localStorage tourma_teams_ / tourma_stage2_teams_
                try {
                    var storageKeyTeams = (this.currentStage === 2) ? ('tourma_stage2_teams_' + this.tournamentId) : ('tourma_teams_' + this.tournamentId);
                    var savedTeams = JSON.parse(localStorage.getItem(storageKeyTeams));
                    if (savedTeams && savedTeams.length > 0) {
                        this.teamsList = savedTeams.map(mapTeam);
                    }
                } catch (e) {}
            }

            if (!this.teamsList) {
                this.teamsList = [];
            }

            // Enforce cutTarget limit ONLY for Stage 2 when cutTarget comes from JSP/DB (not stale localStorage)
            if (this.currentStage === 2 && this.cutTarget > 1 && this.teamsList.length > this.cutTarget) {
                this.teamsList = this.teamsList.slice(0, this.cutTarget);
            }

            // If still empty, synthesize placeholder teams using cutTarget
            if (this.teamsList.length === 0) {
                var count = (this.cutTarget > 1 && this.currentStage === 2) ? this.cutTarget : 16;
                for (var s = 1; s <= count; s++) {
                    this.teamsList.push({ name: 'Hạt giống #' + s, seed: s });
                }
            }

            // Separate storage keys for Stage 1 vs Stage 2
            this.storageKey = (this.currentStage === 2) ? ('tourma_de_matches_stage2_' + this.tournamentId) : ('tourma_de_matches_' + this.tournamentId);
            this.inputsKey = (this.currentStage === 2) ? ('tourma_de_round_inputs_stage2_' + this.tournamentId) : ('tourma_de_round_inputs_' + this.tournamentId);

            // Update Team Count Badge
            var teamCountBadge = document.getElementById('deTeamCountBadge');
            if (teamCountBadge) {
                teamCountBadge.innerText = this.teamsList.length + ' Đội';
            }

            // Update Advancing Badge
            var advBadge = document.getElementById('deAdvancingBadge');
            var advText = document.getElementById('deAdvancingText');
            if (advBadge && advText) {
                if (this.currentStage === 1 && this.cutTarget && this.cutTarget > 1) {
                    advBadge.style.display = 'inline-flex';
                    advText.innerText = this.cutTarget + ' Đội đi tiếp';
                } else {
                    advBadge.style.display = 'none';
                }
            }

            // Restore saved round inputs
            try {
                this.roundRandomInputs = JSON.parse(localStorage.getItem(this.inputsKey)) || {};
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

            var self = this;
            setTimeout(function () {
                self.drawSvgConnectors();
            }, 100);

            // Check Final Stage conclusion & render top banner if complete
            this.checkFinalStage();

            // Setup Global Event Listeners
            this.attachEventListeners();
        },

        /**
         * Load or Generate Bracket Structure
         */
        loadOrGenerateBracketData: function () {
            var storageKey = this.storageKey || ((this.currentStage === 2) ? ('tourma_de_matches_stage2_' + this.tournamentId) : ('tourma_de_matches_' + this.tournamentId));
            var savedBracket = null;

            try {
                savedBracket = JSON.parse(localStorage.getItem(storageKey));
            } catch (e) {
                savedBracket = null;
            }

            // Check if saved bracket team count matches current teamsList
            var savedBracketValid = false;
            if (savedBracket && savedBracket.matchesMap && Object.keys(savedBracket.matchesMap).length > 0) {
                // Determine how many unique teams appear in the saved bracket's first round
                var savedTeamNames = {};
                var keys = Object.keys(savedBracket.matchesMap);
                for (var i = 0; i < keys.length; i++) {
                    var m = savedBracket.matchesMap[keys[i]];
                    var t1 = m.team1 ? m.team1.name : '';
                    var t2 = m.team2 ? m.team2.name : '';
                    if (t1 && t1 !== 'BYE' && !t1.startsWith('W #') && !t1.startsWith('L #') && t1 !== 'Winner UB' && t1 !== 'Winner LB') savedTeamNames[t1] = 1;
                    if (t2 && t2 !== 'BYE' && !t2.startsWith('W #') && !t2.startsWith('L #') && t2 !== 'Winner UB' && t2 !== 'Winner LB') savedTeamNames[t2] = 1;
                }
                var savedTeamCount = Object.keys(savedTeamNames).length;
                var isAllMatch = true;
                if (this.teamsList && this.teamsList.length > 0) {
                    for (var ti = 0; ti < this.teamsList.length; ti++) {
                        var tm = this.teamsList[ti];
                        var tmName = (typeof tm === 'object') ? (tm.name || tm.rawName) : tm;
                        if (tmName && !savedTeamNames[tmName]) {
                            isAllMatch = false;
                            break;
                        }
                    }
                }
                // Accept saved bracket only if every team matches and team count matches
                savedBracketValid = isAllMatch && (savedTeamCount === 0 || savedTeamCount === this.teamsList.length || Math.abs(savedTeamCount - this.teamsList.length) <= 1);

                // Verify if cut stage format matches expected cut stage vs full bracket
                var isCutExpected = (this.currentStage === 1 && this.cutTarget && this.cutTarget > 1 && this.teamsList && this.cutTarget < this.teamsList.length);
                var isSavedCut = (savedBracket && savedBracket.grandFinalsRound === null);
                if (isCutExpected !== isSavedCut) {
                    savedBracketValid = false;
                }
            }

            if (savedBracketValid && savedBracket) {
                this.bracketData = savedBracket;
                this.matchesMap = savedBracket.matchesMap || {};

                // Synchronize correct original seeds from this.teamsList to matchesMap in saved bracket
                if (this.teamsList && this.teamsList.length > 0) {
                    var seedLookup = {};
                    for (var s = 0; s < this.teamsList.length; s++) {
                        var st = this.teamsList[s];
                        var sName = (typeof st === 'object') ? (st.name || st.rawName) : st;
                        var sSeed = (typeof st === 'object' && st.seed !== undefined && st.seed !== null) ? st.seed : (s + 1);
                        if (sName) seedLookup[sName] = sSeed;
                    }
                    var mKeys = Object.keys(this.matchesMap);
                    for (var k = 0; k < mKeys.length; k++) {
                        var mat = this.matchesMap[mKeys[k]];
                        if (mat.team1 && mat.team1.name && seedLookup[mat.team1.name] !== undefined) {
                            mat.team1.seed = seedLookup[mat.team1.name];
                        }
                        if (mat.team2 && mat.team2.name && seedLookup[mat.team2.name] !== undefined) {
                            mat.team2.seed = seedLookup[mat.team2.name];
                        }
                    }
                }
            } else if (this.teamsList && this.teamsList.length > 0 && window.TourmaDoubleElimAlgorithm) {
                // Bracket mismatch or missing — regenerate from current teamsList
                if (savedBracket) {
                    // Clear stale bracket
                    try { localStorage.removeItem('tourma_de_matches_' + this.tournamentId); } catch (e) {}
                }
                var cut = (this.currentStage === 1 && this.cutTarget && this.cutTarget > 1) ? this.cutTarget : 0;
                this.bracketData = window.TourmaDoubleElimAlgorithm.generateDoubleElimination(this.teamsList, cut);
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

            // Check if Stage 2 is locked
            if (window.StageFinishAlert && typeof window.StageFinishAlert.checkAndRender === 'function') {
                var isStage2Locked = window.StageFinishAlert.checkAndRender(this.tournamentId, this.currentStage, alertContainer || wrapper);
                if (isStage2Locked) {
                    if (dualWorkspace) dualWorkspace.style.display = 'none';
                    if (alertContainer) alertContainer.style.display = 'flex';
                    var listView = document.getElementById('deListViewContainer');
                    if (listView) listView.style.display = 'none';
                    return; // Stop rendering Stage 2 DE viewport!
                }
            }

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

                var showRound1Byes = window.TourmaDoubleElimAlgorithm ? window.TourmaDoubleElimAlgorithm.shouldShowRound1Byes(self.bracketData ? self.bracketData.upperRounds : null, 0.50) : true;

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
                gfTitle.innerText = 'Grand Final';
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
                return 'Grand Final';
            }
            if (t.includes("winner's qualification") || t.includes('winner qualification') || t.includes('ub cut')) {
                return 'WQ';
            }
            if (t.includes("loser's qualification") || t.includes('loser qualification') || t.includes('lb cut')) {
                return 'LQ';
            }
            if (bType === 'UPPER') {
                if (t.includes('final') || t.includes('chung kết')) return 'UB Final';
                return 'UB R' + rNum;
            }
            if (bType === 'LOWER') {
                if (t.includes('final') || t.includes('chung kết')) return 'LB Final';
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

        openResetModal: function () {
            if (this.currentStage === 1 && window.StageEndPopup && typeof window.StageEndPopup.isStage1Locked === 'function' && window.StageEndPopup.isStage1Locked(this.tournamentId)) {
                alert('Vòng 1 đã hoàn tất và đang ở trạng thái khóa. Vui lòng bấm "Mở khóa để sửa" trên thanh thông báo nếu bạn muốn thiết lập lại.');
                return;
            }
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
            var sKey = this.storageKey || ((this.currentStage === 2) ? ('tourma_de_matches_stage2_' + this.tournamentId) : ('tourma_de_matches_' + this.tournamentId));
            var iKey = this.inputsKey || ((this.currentStage === 2) ? ('tourma_de_round_inputs_stage2_' + this.tournamentId) : ('tourma_de_round_inputs_' + this.tournamentId));
            try {
                localStorage.removeItem(sKey);
                localStorage.removeItem(iKey);
                if (this.currentStage === 2) {
                    localStorage.removeItem('tourma_matches_stage2_' + this.tournamentId);
                } else if (this.currentStage === 1) {
                    localStorage.removeItem('tourma_matches_' + this.tournamentId);
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

            // Re-generate fresh initial bracket
            if (window.TourmaDoubleElimAlgorithm && typeof window.TourmaDoubleElimAlgorithm.generateDoubleElimination === 'function') {
                var cut = (this.currentStage === 1 && this.cutTarget && this.cutTarget > 1) ? this.cutTarget : 0;
                this.bracketData = window.TourmaDoubleElimAlgorithm.generateDoubleElimination(this.teamsList, cut);
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
                var sKey = this.storageKey || ((this.currentStage === 2) ? ('tourma_de_matches_stage2_' + this.tournamentId) : ('tourma_de_matches_' + this.tournamentId));
                localStorage.setItem(sKey, JSON.stringify(this.bracketData));
                if (this.currentStage === 2) {
                    localStorage.setItem('tourma_matches_stage2_' + this.tournamentId, JSON.stringify(this.matchesMap));
                } else if (this.currentStage === 1) {
                    localStorage.setItem('tourma_matches_' + this.tournamentId, JSON.stringify(this.matchesMap));
                    if (this.teamsList && this.teamsList.length > 0) {
                        localStorage.setItem('tourma_teams_' + this.tournamentId, JSON.stringify(this.teamsList));
                    }
                }
            } catch (e) {}
            this.checkAndTriggerStage2Cut();
            this.checkFinalStage();
        },

        checkAndTriggerStage2Cut: function () {
            if (this.currentStage !== 1 || !this.cutTarget || this.cutTarget <= 1) return;
            if (!this.bracketData || !this.bracketData.upperRounds || !this.bracketData.lowerRounds) return;

            var pow2 = window.TourmaDoubleElimAlgorithm.calculatePowerOfTwo(this.teamsList.length);
            var totalUbRounds = Math.log2(pow2);
            var ubQualifiersCount = this.cutTarget / 2;
            var ubStopRound = totalUbRounds - Math.round(Math.log2(ubQualifiersCount));
            var lbStopRound = (ubStopRound - 1) * 2;

            var ubRoundObj = this.bracketData.upperRounds[ubStopRound - 1];
            var lbRoundObj = this.bracketData.lowerRounds[lbStopRound - 1];
            if (!ubRoundObj || !lbRoundObj) return;

            var ubCutMatches = ubRoundObj.matches || [];
            var lbCutMatches = lbRoundObj.matches || [];

            // Check if all UB cut matches and all LB cut matches are finished with winners
            var allDone = true;
            for (var u = 0; u < ubCutMatches.length; u++) {
                var m = this.matchesMap[ubCutMatches[u].matchId] || ubCutMatches[u];
                if (!m.winnerId || (m.winnerId !== 'team1' && m.winnerId !== 'team2')) {
                    allDone = false;
                    break;
                }
            }
            if (allDone) {
                for (var l = 0; l < lbCutMatches.length; l++) {
                    var lm = this.matchesMap[lbCutMatches[l].matchId] || lbCutMatches[l];
                    if (!lm.winnerId || (lm.winnerId !== 'team1' && lm.winnerId !== 'team2')) {
                        allDone = false;
                        break;
                    }
                }
            }

            if (!allDone) return;

            // Helper to get definitive original seed
            var self = this;
            var getDefinitiveSeed = function(teamName, fallbackSeed) {
                if (!teamName) return fallbackSeed;
                for (var idx = 0; idx < self.teamsList.length; idx++) {
                    var tm = self.teamsList[idx];
                    var tmName = (typeof tm === 'object') ? (tm.name || tm.rawName) : tm;
                    if (tmName === teamName) {
                        return (typeof tm === 'object' && tm.seed !== undefined && tm.seed !== null) ? tm.seed : (idx + 1);
                    }
                }
                return fallbackSeed;
            };

            // Collect UB Qualifiers
            var ubWinners = [];
            for (var i = 0; i < ubCutMatches.length; i++) {
                var um = this.matchesMap[ubCutMatches[i].matchId] || ubCutMatches[i];
                var wName = (um.winnerId === 'team1') ? um.team1.name : um.team2.name;
                var wSeed = (um.winnerId === 'team1') ? um.team1.seed : um.team2.seed;
                var origSeed = getDefinitiveSeed(wName, wSeed || (i + 1));
                ubWinners.push({ name: wName, rawName: wName, seed: origSeed, source: 'UB' });
            }

            // Collect LB Qualifiers
            var lbWinners = [];
            for (var j = 0; j < lbCutMatches.length; j++) {
                var lbm = this.matchesMap[lbCutMatches[j].matchId] || lbCutMatches[j];
                var lwName = (lbm.winnerId === 'team1') ? lbm.team1.name : lbm.team2.name;
                var lwSeed = (lbm.winnerId === 'team1') ? lbm.team1.seed : lbm.team2.seed;
                var lOrigSeed = getDefinitiveSeed(lwName, lwSeed || (j + 1));
                lbWinners.push({ name: lwName, rawName: lwName, seed: lOrigSeed, source: 'LB' });
            }

            if (ubWinners.length + lbWinners.length !== this.cutTarget) return;

            // Check if stage 2 teams already created and match current qualifiers
            var existingS2Raw = localStorage.getItem('tourma_stage2_teams_' + this.tournamentId);
            var isAlreadyCreated = false;
            if (existingS2Raw) {
                try {
                    var existingS2 = JSON.parse(existingS2Raw);
                    if (Array.isArray(existingS2) && existingS2.length === this.cutTarget) {
                        var existingNames = existingS2.map(function(t) { return (typeof t === 'object') ? (t.name || t.rawName) : t; });
                        var allPresent = true;
                        for (var ui = 0; ui < ubWinners.length; ui++) {
                            if (existingNames.indexOf(ubWinners[ui].name) === -1) { allPresent = false; break; }
                        }
                        if (allPresent) {
                            for (var li = 0; li < lbWinners.length; li++) {
                                if (existingNames.indexOf(lbWinners[li].name) === -1) { allPresent = false; break; }
                            }
                        }
                        if (allPresent) isAlreadyCreated = true;
                    }
                } catch(e) {}
            }

            if (!isAlreadyCreated) {
                // 100% Random Draw: Shuffle UB qualifiers and LB qualifiers independently
                var shuffledUb = ubWinners.slice().sort(function() { return 0.5 - Math.random(); });
                var shuffledLb = lbWinners.slice().sort(function() { return 0.5 - Math.random(); });

                var finalStage2Teams = [];
                var halfK = ubWinners.length;
                
                if (halfK === 4) {
                    // 8 teams standard pairing where UB strictly plays LB in Round 1:
                    // Match 1: Pos 1 vs Pos 8 -> shuffledUb[0] vs shuffledLb[0]
                    // Match 2: Pos 4 vs Pos 5 -> shuffledUb[1] vs shuffledLb[1]
                    // Match 3: Pos 3 vs Pos 6 -> shuffledUb[2] vs shuffledLb[2]
                    // Match 4: Pos 2 vs Pos 7 -> shuffledUb[3] vs shuffledLb[3]
                    finalStage2Teams = [
                        shuffledUb[0], // Pos 1 (plays Pos 8)
                        shuffledUb[3], // Pos 2 (plays Pos 7)
                        shuffledUb[2], // Pos 3 (plays Pos 6)
                        shuffledUb[1], // Pos 4 (plays Pos 5)
                        shuffledLb[1], // Pos 5
                        shuffledLb[2], // Pos 6
                        shuffledLb[3], // Pos 7
                        shuffledLb[0]  // Pos 8
                    ];
                } else if (halfK === 2) {
                    // 4 teams standard pairing:
                    finalStage2Teams = [
                        shuffledUb[0], // Pos 1 (plays Pos 4)
                        shuffledUb[1], // Pos 2 (plays Pos 3)
                        shuffledLb[1], // Pos 3
                        shuffledLb[0]  // Pos 4
                    ];
                } else {
                    finalStage2Teams = shuffledUb.concat(shuffledLb);
                }

                localStorage.setItem('tourma_stage2_teams_' + this.tournamentId, JSON.stringify(finalStage2Teams));

                var multiCfg = null;
                try {
                    multiCfg = JSON.parse(localStorage.getItem('tourma_multi_config_' + this.tournamentId)) || {};
                } catch(e) {
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
                        localStorage.setItem('tourma_rr_matches_' + this.tournamentId, JSON.stringify(rrBracket));
                    }
                }

                multiCfg.stage2MatchesCreated = true;
                localStorage.setItem('tourma_multi_config_' + this.tournamentId, JSON.stringify(multiCfg));
            }

            localStorage.setItem('tourma_stage1_completed_' + this.tournamentId, 'true');
        },

        checkFinalStage: function () {
            if (this.currentStage === 1) {
                if (window.StageEndPopup) {
                    window.StageEndPopup.update(
                        this.tournamentId,
                        'DOUBLE_ELIMINATION',
                        this.matchesMap,
                        this.teamsList,
                        { cutTarget: this.cutTarget },
                        null,
                        1
                    );
                }
                if (this.cutTarget && this.cutTarget > 1) return;
            }
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
