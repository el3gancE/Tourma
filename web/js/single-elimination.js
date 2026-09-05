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
        init: function (tourneyId, dbMatches, preloadedTeams, cutTarget, stage) {
            this.tournamentId = tourneyId || 'demo';
            this.currentStage = (stage === 2 || stage === '2') ? 2 : 1;

            // JSP already resolved the correct cutTarget (from tourma_multi_config_ / DB / etc.)
            // cutTarget=0 means Single Stage / Stage 2 (play all rounds), cutTarget>1 means Multi-Stage cut
            if (this.currentStage === 2) {
                this.cutTarget = null;
                // Check Stage 2 Lock immediately
                if (window.StageFinishAlert && typeof window.StageFinishAlert.checkAndRender === 'function') {
                    var isBlocked = window.StageFinishAlert.checkAndRender(this.tournamentId, 2, 'singleEmptyAlertContainer');
                    if (isBlocked) {
                        var vpFrame = document.getElementById('bracketViewportFrame');
                        if (vpFrame) vpFrame.style.display = 'none';
                        var listV = document.getElementById('singleListViewContainer');
                        if (listV) listV.style.display = 'none';
                        var emptyC = document.getElementById('singleEmptyAlertContainer');
                        if (emptyC) emptyC.style.display = 'flex';
                        return; // Stop initialization completely!
                    }
                }
            } else {
                var target = (cutTarget && parseInt(cutTarget, 10) > 1) ? parseInt(cutTarget, 10) : null;
                this.cutTarget = target;
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

            if (!teams) {
                teams = [];
            }

            // In Stage 2, enforce advanceCount limit if teams length exceeds advanceCount
            if (this.currentStage === 2) {
                var advCount = 0;
                try {
                    var mCfg = JSON.parse(localStorage.getItem('tourma_multi_config_' + this.tournamentId));
                    if (mCfg && mCfg.stage1Config) advCount = mCfg.stage1Config.advanceCount || mCfg.stage1Config.totalAdvanceCount || 0;
                } catch (e) {}
                if (!advCount || advCount <= 1) {
                    var rawAdv = localStorage.getItem('tourma_advance_count_' + this.tournamentId) || localStorage.getItem('tourma_cut_target_' + this.tournamentId);
                    if (rawAdv) advCount = parseInt(rawAdv, 10);
                }
                if (advCount && advCount > 1 && teams.length > advCount) {
                    teams = teams.slice(0, advCount);
                }
            }

            this.teamsList = teams;

            // Update team count badge in top toolbar
            var countBadge = document.getElementById('tournamentTeamCountBadge');
            if (countBadge) {
                countBadge.innerText = this.teamsList.length + ' Đội';
            }

            // Update advancing team count badge in top toolbar
            var advBadge = document.getElementById('tournamentAdvancingBadge');
            if (advBadge) {
                if (this.currentStage === 1 && this.cutTarget && this.cutTarget > 1) {
                    advBadge.style.display = 'inline-flex';
                    advBadge.innerHTML = '<i class="fa-solid fa-arrow-right-to-bracket"></i> ' + this.cutTarget + ' Đội đi tiếp';
                } else {
                    advBadge.style.display = 'none';
                }
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

            // Check and trigger Stage 2 generation if cut round is complete
            this.checkAndTriggerStage2Cut();
        },

        getStorageKeyBracket: function () {
            return (this.currentStage === 2) ? ('tourma_bracket_stage2_' + this.tournamentId) : ('tourma_bracket_' + this.tournamentId);
        },

        getStorageKeyMatches: function () {
            return (this.currentStage === 2) ? ('tourma_matches_stage2_' + this.tournamentId) : ('tourma_matches_' + this.tournamentId);
        },

        /**
         * Generate or Restore Matches Structure
         */
        generateMatchesStructure: function (dbMatches) {
            var bKey = this.getStorageKeyBracket();
            var mKey = this.getStorageKeyMatches();
            var savedBracket = null;

            if (this.tournamentId) {
                // 1. Primary bracket key
                try {
                    savedBracket = JSON.parse(localStorage.getItem(bKey));
                } catch (e) {
                    savedBracket = null;
                }
                // 2. Matches key fallback
                if (!savedBracket || !savedBracket.matchesMap || Object.keys(savedBracket.matchesMap).length === 0) {
                    try {
                        var rawM = JSON.parse(localStorage.getItem(mKey));
                        if (rawM && typeof rawM === 'object' && Object.keys(rawM).length > 0) {
                            if (rawM.matchesMap) {
                                savedBracket = rawM;
                            } else {
                                savedBracket = { matchesMap: rawM, roundsList: (rawM.roundsList || []) };
                            }
                        }
                    } catch (e) {}
                }
                // 3. Stage 2 explicit key fallback if needed
                if ((!savedBracket || !savedBracket.matchesMap || Object.keys(savedBracket.matchesMap).length === 0) && this.currentStage === 2) {
                    try {
                        var rawS2B = JSON.parse(localStorage.getItem('tourma_bracket_stage2_' + this.tournamentId));
                        if (rawS2B && rawS2B.matchesMap && Object.keys(rawS2B.matchesMap).length > 0) {
                            savedBracket = rawS2B;
                        }
                    } catch (e) {}
                    if (!savedBracket || !savedBracket.matchesMap || Object.keys(savedBracket.matchesMap).length === 0) {
                        try {
                            var rawS2M = JSON.parse(localStorage.getItem('tourma_matches_stage2_' + this.tournamentId));
                            if (rawS2M && typeof rawS2M === 'object' && Object.keys(rawS2M).length > 0) {
                                savedBracket = { matchesMap: (rawS2M.matchesMap || rawS2M), roundsList: (rawS2M.roundsList || []) };
                            }
                        } catch (e) {}
                    }
                }
            }

            // Restore if ANY saved bracket data exists
            var savedValid = !!(savedBracket && savedBracket.matchesMap && Object.keys(savedBracket.matchesMap).length > 0);

            // Safety Check: If savedBracket contains ANY completed match or score, NEVER wipe it!
            if (!savedValid && savedBracket && savedBracket.matchesMap) {
                var mKeysCheck = Object.keys(savedBracket.matchesMap);
                for (var ck = 0; ck < mKeysCheck.length; ck++) {
                    var matCheck = savedBracket.matchesMap[mKeysCheck[ck]];
                    if (matCheck && (matCheck.winnerId || (matCheck.team1 && matCheck.team1.score !== undefined && matCheck.team1.score !== '') || (matCheck.team2 && matCheck.team2.score !== undefined && matCheck.team2.score !== '') || matCheck.status === 'COMPLETED')) {
                        savedValid = true;
                        break;
                    }
                }
            }

            var hasDbMatches = (dbMatches && Array.isArray(dbMatches) && dbMatches.length > 0);
            console.log('[SE restore] key=' + bKey + ' | hasDbMatches=' + hasDbMatches + ' (count=' + (hasDbMatches ? dbMatches.length : 0) + ') | hasSaved=' + savedValid + ' | mapSize=' + (savedBracket && savedBracket.matchesMap ? Object.keys(savedBracket.matchesMap).length : 0));

            if (hasDbMatches) {
                // 1. RESTORE DIRECTLY FROM DATABASE
                console.log('[SE restore] Restoring directly from DATABASE matches!');
                this.buildMapFromList(dbMatches);

                // If localStorage had any completed scores or user inputs that DB missed, merge them
                if (savedValid && savedBracket && savedBracket.matchesMap) {
                    var sKeys = Object.keys(savedBracket.matchesMap);
                    var hadMerged = false;
                    for (var sIdx = 0; sIdx < sKeys.length; sIdx++) {
                        var sMat = savedBracket.matchesMap[sKeys[sIdx]];
                        var curMat = this.matchesMap[sKeys[sIdx]];
                        if (sMat && curMat) {
                            if (!curMat.winnerId && sMat.winnerId) {
                                if (curMat.team1 && sMat.team1 && sMat.team1.score !== undefined) curMat.team1.score = sMat.team1.score;
                                if (curMat.team2 && sMat.team2 && sMat.team2.score !== undefined) curMat.team2.score = sMat.team2.score;
                                curMat.winnerId = sMat.winnerId;
                                curMat.status = sMat.status || 'COMPLETED';
                                hadMerged = true;
                            }
                        }
                    }
                    if (hadMerged) {
                        this.persistMatches();
                    }
                }
            } else if (savedValid && savedBracket) {
                // DIRECT RESTORE
                this.matchesMap = savedBracket.matchesMap || {};
                this.roundsList = savedBracket.roundsList || [];
                this.bracketData = savedBracket;

                if (this.roundsList.length === 0 && Object.keys(this.matchesMap).length > 0) {
                    this.buildRoundsFromMap();
                }

                if (savedBracket.teamsList && savedBracket.teamsList.length > 0) {
                    this.teamsList = savedBracket.teamsList;
                }

                // Immediately sync to DB so DB now has this tournament's matches permanently!
                this.syncBracketToDB(true);

                // Re-link roundsList match references to matchesMap objects (shared reference)
                for (var r = 0; r < this.roundsList.length; r++) {
                    for (var m = 0; m < this.roundsList[r].matches.length; m++) {
                        var mRef = this.roundsList[r].matches[m];
                        var mKeyRef = (mRef && (mRef.matchId !== undefined && mRef.matchId !== null ? mRef.matchId : mRef.id));
                        if (mKeyRef && this.matchesMap[mKeyRef]) {
                            this.roundsList[r].matches[m] = this.matchesMap[mKeyRef];
                        }
                    }
                }

                // Re-sync seeds from tourma_teams_ if available
                var allOriginalTeams = null;
                try {
                    allOriginalTeams = JSON.parse(localStorage.getItem('tourma_teams_' + this.tournamentId));
                } catch(e) {}

                var seedLookup = {};
                if (allOriginalTeams && Array.isArray(allOriginalTeams)) {
                    for (var ot = 0; ot < allOriginalTeams.length; ot++) {
                        var oTeam = allOriginalTeams[ot];
                        var oName = (typeof oTeam === 'object') ? (oTeam.name || oTeam.rawName) : oTeam;
                        var oSeed = (typeof oTeam === 'object' && oTeam.seed !== undefined && oTeam.seed !== null && oTeam.seed !== '') ? oTeam.seed : '';
                        if (oName && oSeed !== '') seedLookup[oName] = oSeed;
                    }
                }

                if (this.teamsList && this.teamsList.length > 0) {
                    for (var s = 0; s < this.teamsList.length; s++) {
                        var st = this.teamsList[s];
                        var sName = (typeof st === 'object') ? (st.name || st.rawName) : st;
                        var sSeed = (typeof st === 'object' && st.seed !== undefined && st.seed !== null && st.seed !== '') ? st.seed : '';
                        if (sName && sSeed !== '' && seedLookup[sName] === undefined) seedLookup[sName] = sSeed;
                    }
                    var mKeys = Object.keys(this.matchesMap);
                    for (var k = 0; k < mKeys.length; k++) {
                        var mat = this.matchesMap[mKeys[k]];
                        if (mat.team1 && mat.team1.name && seedLookup[mat.team1.name] !== undefined) {
                            mat.team1.seed = seedLookup[mat.team1.name];
                        } else if (mat.team1 && (!mat.team1.name || mat.team1.name.startsWith('W #') || mat.team1.name.startsWith('L #') || mat.team1.name === 'TBD' || mat.team1.name === 'BYE')) {
                            mat.team1.seed = '';
                        }
                        if (mat.team2 && mat.team2.name && seedLookup[mat.team2.name] !== undefined) {
                            mat.team2.seed = seedLookup[mat.team2.name];
                        } else if (mat.team2 && (!mat.team2.name || mat.team2.name.startsWith('W #') || mat.team2.name.startsWith('L #') || mat.team2.name === 'TBD' || mat.team2.name === 'BYE')) {
                            mat.team2.seed = '';
                        }
                    }

                    // Auto-resolve any unlinked BYE winners to their next round slots
                    for (var k = 0; k < mKeys.length; k++) {
                        var mat = this.matchesMap[mKeys[k]];
                        if (mat && mat.winnerId && mat.nextMatchId && this.matchesMap[mat.nextMatchId]) {
                            var isT1 = (mat.winnerId === 'team1');
                            var wTeam = isT1 ? mat.team1 : mat.team2;
                            var nextM = this.matchesMap[mat.nextMatchId];
                            if (wTeam && wTeam.name && wTeam.name !== 'BYE') {
                                if (mat.nextMatchSlot === 1 && (!nextM.team1.name || nextM.team1.name.startsWith('W #') || nextM.team1.name === 'TBD')) {
                                    nextM.team1.name = wTeam.name;
                                    nextM.team1.seed = wTeam.seed;
                                } else if (mat.nextMatchSlot === 2 && (!nextM.team2.name || nextM.team2.name.startsWith('W #') || nextM.team2.name === 'TBD')) {
                                    nextM.team2.name = wTeam.name;
                                    nextM.team2.seed = wTeam.seed;
                                }
                            }
                        }
                    }
                }
            } else if (this.teamsList && this.teamsList.length > 0 && window.TourmaBracketAlgorithm) {
                // GENERATE FRESH BRACKET directly from current teamsList sequence
                var generated = window.TourmaBracketAlgorithm.generateSingleElimination(this.teamsList, this.cutTarget);
                this.bracketData = generated;
                this.roundsList = generated.roundsList || [];
                this.matchesMap = generated.matchesMap || {};
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

            // Check if Stage 2 is locked (Stage 1 not yet completed or not confirmed)
            if (window.StageFinishAlert && typeof window.StageFinishAlert.checkAndRender === 'function') {
                var isStage2Locked = window.StageFinishAlert.checkAndRender(this.tournamentId, this.currentStage, alertContainer || canvasWrapper);
                if (isStage2Locked) {
                    if (viewportFrame) viewportFrame.style.display = 'none';
                    if (alertContainer) alertContainer.style.display = 'flex';
                    var listView = document.getElementById('singleListViewContainer');
                    if (listView) listView.style.display = 'none';
                    return; // Stop rendering Stage 2 viewport!
                }
            }

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
                if (viewportFrame && this.currentViewMode !== 'list') viewportFrame.style.display = 'block';
                if (alertContainer) alertContainer.style.display = 'none';
            }

            // 1. Calculate Cut Target and Stopping Round threshold (Stage 2 is always 0 / final champion)
            var cutTarget = (this.currentStage === 2) ? null : this.cutTarget;
            if (this.currentStage !== 2 && (!cutTarget || cutTarget <= 1)) {
                try {
                    var rawCut = localStorage.getItem('tourma_advance_count_' + this.tournamentId) || localStorage.getItem('tourma_cut_target_' + this.tournamentId);
                    if (rawCut) cutTarget = parseInt(rawCut, 10);
                } catch (e) {
                    cutTarget = null;
                }
            }
            // DEBUG
            console.log('[renderBracket] cutTarget=', cutTarget, '| this.cutTarget=', this.cutTarget,
                        '| localStorage adv=', localStorage.getItem('tourma_advance_count_' + this.tournamentId),
                        '| teamCount=', teamCount, '| totalRounds=', this.roundsList.length);

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

                var prevRoundMatches = (r > 0 && self.roundsList[r - 1]) ? self.roundsList[r - 1].matches : [];
                var showRound1Byes = window.TourmaBracketAlgorithm ? window.TourmaBracketAlgorithm.shouldShowRound1Byes(self.roundsList, 0.50) : true;

                for (var m = 0; m < roundObj.matches.length; m++) {
                    var matchData = roundObj.matches[m];
                    matchData.tournamentId = self.tournamentId;
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

            self.alignTreeCards();
            requestAnimationFrame(function () { self.alignTreeCards(); self.drawTreeConnectors(); });
            setTimeout(function () { self.alignTreeCards(); self.drawTreeConnectors(); }, 60);
            setTimeout(function () { self.alignTreeCards(); self.drawTreeConnectors(); }, 250);
        },

        alignTreeCards: function () {
            var wrapper = document.getElementById('singleBracketColumnsWrapper');
            if (!wrapper) return;

            var cols = wrapper.querySelectorAll('.single-round-column');
            if (cols.length < 2) return;

            var self = this;
            for (var c = 1; c < cols.length; c++) {
                var prevCol = cols[c - 1];
                var curCol = cols[c];

                var prevBox = prevCol.querySelector('.single-round-matches-box');
                var curBox = curCol.querySelector('.single-round-matches-box');
                if (!prevBox || !curBox) continue;

                prevBox.style.position = 'relative';
                curBox.style.position = 'relative';
                if (prevBox.offsetHeight > 0) {
                    curBox.style.minHeight = prevBox.offsetHeight + 'px';
                }

                // Include bye-empty-slot so BYE matches contribute to child positioning
                var prevCards = prevBox.querySelectorAll('.bracket-node-card');
                var curCards = curBox.querySelectorAll('.bracket-node-card:not(.bye-empty-slot)');
                if (!curCards || curCards.length === 0) continue;

                var prevCardMap = {};
                for (var p = 0; p < prevCards.length; p++) {
                    var pid = prevCards[p].getAttribute('data-match-id') || prevCards[p].dataset.matchId;
                    if (pid) prevCardMap[String(pid)] = prevCards[p];
                }

                for (var k = 0; k < curCards.length; k++) {
                    var curCard = curCards[k];
                    var mId = curCard.getAttribute('data-match-id') || curCard.dataset.matchId;
                    if (!mId) continue;

                    var parentCards = [];
                    var keys = Object.keys(self.matchesMap || {});
                    for (var i = 0; i < keys.length; i++) {
                        var m = self.matchesMap[keys[i]];
                        if (m && String(m.nextMatchId) === String(mId)) {
                            var pCard = prevCardMap[String(m.matchId)];
                            if (pCard) parentCards.push(pCard);
                        }
                    }

                    if (parentCards.length > 0) {
                        var sumY = 0;
                        for (var j = 0; j < parentCards.length; j++) {
                            var pc = parentCards[j];
                            sumY += pc.offsetTop + (pc.offsetHeight / 2);
                        }
                        var targetMidY = sumY / parentCards.length;
                        var cardH = curCard.offsetHeight || 66;
                        var desiredTop = targetMidY - (cardH / 2);

                        curCard.style.position = 'absolute';
                        curCard.style.top = Math.max(0, desiredTop).toFixed(1) + 'px';
                        curCard.style.left = '0';
                        curCard.style.right = '0';
                        curCard.style.margin = '0 auto';
                    }
                }
            }
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
            if (t.includes('chung kết') || t.includes('final')) return 'Final';

            var fromEnd = totalRounds - roundObj.roundNumber;
            if (fromEnd === 0) return 'Final';
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
                    matchData.tournamentId = self.tournamentId;
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
                this.switchViewMode(this.currentViewMode || 'bracket');
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
                this.switchViewMode(this.currentViewMode || 'bracket');
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
            this.switchViewMode(this.currentViewMode || 'bracket');
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
            this.switchViewMode(this.currentViewMode || 'bracket');

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
            this.switchViewMode(this.currentViewMode || 'bracket');

            // Notify backend asynchronously
            this.saveMatchResultAJAX(
                mId,
                detail.team1Score,
                detail.team2Score,
                detail.winner ? (isT1Winner ? targetMatch.team1.name : targetMatch.team2.name) : ''
            );
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
                    localStorage.removeItem(this.getStorageKeyBracket());
                    localStorage.removeItem(this.getStorageKeyMatches());
                    localStorage.removeItem('tourma_round_inputs_' + this.tournamentId);
                    if (this.currentStage === 2) {
                        localStorage.removeItem('tourma_matches_stage2_' + this.tournamentId);
                        localStorage.removeItem('tourma_bracket_stage2_' + this.tournamentId);
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

                // Reset matches in database
                var resetParams = new URLSearchParams();
                resetParams.append('action', 'reset');
                resetParams.append('tournamentId', this.tournamentId);
                resetParams.append('stage', this.currentStage || 1);
                fetch('single-elimination', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
                    body: resetParams.toString()
                }).catch(function (e) {});
            }

            // Regenerate fresh initial bracket
            if (window.TourmaBracketAlgorithm && typeof window.TourmaBracketAlgorithm.generateSingleElimination === 'function') {
                var generated = window.TourmaBracketAlgorithm.generateSingleElimination(this.teamsList, this.cutTarget);
                this.bracketData = generated;
                this.roundsList = generated.roundsList || [];
                this.matchesMap = generated.matchesMap || {};

                this.persistMatches();
                this.renderBracketView();
                this.renderListView();
                this.switchViewMode(this.currentViewMode || 'bracket');
                var self = this;
                setTimeout(function () {
                    self.drawTreeConnectors();
                }, 100);
            }
        },

        /**
         * Persist matches map (and full bracket structure) to localStorage with quota-safe eviction
         */
        persistMatches: function () {
            if (!this.tournamentId) return;

            var bKey = this.getStorageKeyBracket();
            var mKey = this.getStorageKeyMatches();

            this.bracketData = {
                teamsList: this.teamsList,
                roundsList: this.roundsList,
                matchesMap: this.matchesMap
            };

            // Safe localStorage writer
            var safeSet = function (key, dataObj) {
                if (!key || !dataObj) return false;
                try {
                    localStorage.setItem(key, JSON.stringify(dataObj));
                    return true;
                } catch (err) {
                    console.error('[SE Storage] Failed to save ' + key, err);
                    return false;
                }
            };

            // 1. Save full bracket structure
            var bSaved = safeSet(bKey, this.bracketData);

            // 2. Save matchesMap independently as reliable fallback
            var mSaved = safeSet(mKey, this.matchesMap);

            // 3. Save teams list
            if (this.teamsList && this.teamsList.length > 0) {
                var tKey = (this.currentStage === 2) ? ('tourma_stage2_teams_' + this.tournamentId) : ('tourma_teams_' + this.tournamentId);
                safeSet(tKey, this.teamsList);
            }

            console.log('[SE persist] bKey=' + bKey + ' (' + bSaved + ') | mKey=' + mKey + ' (' + mSaved + ') | mapSize=' + Object.keys(this.matchesMap || {}).length);

            if (this.currentStage === 1) {
                this.checkAndTriggerStage2Cut();
            }
            this.checkFinalStage();

            // Sync to database
            this.syncBracketToDB(false);
        },

        _syncTimeout: null,

        /**
         * Batch Sync entire bracket structure to database table 'matches'
         */
        syncBracketToDB: function (immediate) {
            var self = this;
            if (!this.tournamentId || !this.matchesMap) return;
            var keys = Object.keys(this.matchesMap);
            if (keys.length === 0) return;

            var doSync = function () {
                var matchesArray = [];
                var allKeys = Object.keys(self.matchesMap);
                for (var i = 0; i < allKeys.length; i++) {
                    var m = self.matchesMap[allKeys[i]];
                    if (!m) continue;
                    matchesArray.push({
                        matchId: m.matchId || m.id,
                        roundNumber: m.roundNumber || 1,
                        matchNumber: m.matchNumber || (i + 1),
                        team1Name: (m.team1 ? m.team1.name : ''),
                        team1Seed: (m.team1 ? m.team1.seed : ''),
                        team1Score: (m.team1 ? m.team1.score : ''),
                        team2Name: (m.team2 ? m.team2.name : ''),
                        team2Seed: (m.team2 ? m.team2.seed : ''),
                        team2Score: (m.team2 ? m.team2.score : ''),
                        winnerId: m.winnerId || '',
                        nextMatchId: m.nextMatchId || '',
                        nextMatchSlot: m.nextMatchSlot || 1,
                        isBye: !!m.isBye,
                        status: m.status || 'PENDING'
                    });
                }

                var params = new URLSearchParams();
                params.append('action', 'batchSync');
                params.append('tournamentId', self.tournamentId);
                params.append('stage', self.currentStage || 1);
                params.append('matchesData', JSON.stringify(matchesArray));

                fetch('single-elimination', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
                    },
                    body: params.toString()
                }).then(function (res) {
                    return res.json();
                }).then(function (data) {
                    console.log('[SE AJAX] DB batchSync success:', data);
                }).catch(function (err) {
                    console.warn('[SE AJAX] DB batchSync error:', err);
                });
            };

            if (immediate) {
                if (this._syncTimeout) {
                    clearTimeout(this._syncTimeout);
                    this._syncTimeout = null;
                }
                doSync();
            } else {
                if (this._syncTimeout) clearTimeout(this._syncTimeout);
                this._syncTimeout = setTimeout(doSync, 300);
            }
        },

        /**
         * Recursively trace upstream to resolve the actual winning team name
         */
        resolveActualTeamWinner: function (match) {
            if (!match || !match.winnerId) return null;
            var winner = (match.winnerId === 'team1') ? match.team1 : ((match.winnerId === 'team2') ? match.team2 : null);
            if (!winner || !winner.name || winner.name === 'BYE' || winner.name === 'TBD') return null;

            var wName = winner.name;
            var wSeed = winner.seed;

            // Trace upstream if name is a placeholder (e.g. 'W #33')
            var depth = 0;
            while (wName && wName.startsWith('W #') && depth < 10) {
                depth++;
                var pMatchNum = parseInt(wName.replace('W #', ''), 10);
                var foundParent = null;
                var keys = Object.keys(this.matchesMap || {});
                for (var i = 0; i < keys.length; i++) {
                    var pm = this.matchesMap[keys[i]];
                    if (pm && (pm.matchNumber === pMatchNum || pm.matchId === pMatchNum)) {
                        foundParent = pm;
                        break;
                    }
                }
                if (foundParent && foundParent.winnerId) {
                    var pw = (foundParent.winnerId === 'team1') ? foundParent.team1 : foundParent.team2;
                    if (pw && pw.name) {
                        wName = pw.name;
                        wSeed = (pw.seed !== undefined && pw.seed !== null && pw.seed !== '') ? pw.seed : wSeed;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }

            // Look up original seed from this.teamsList if not set
            if ((wSeed === undefined || wSeed === null || wSeed === '') && this.teamsList) {
                for (var s = 0; s < this.teamsList.length; s++) {
                    var tObj = this.teamsList[s];
                    var tName = (typeof tObj === 'object') ? (tObj.name || tObj.rawName) : tObj;
                    if (tName === wName) {
                        wSeed = (typeof tObj === 'object' && tObj.seed !== undefined && tObj.seed !== null && tObj.seed !== '') ? tObj.seed : '';
                        break;
                    }
                }
            }

            return { name: wName, rawName: wName, seed: wSeed };
        },

        /**
         * Automatically extract qualified winners and generate Stage 2 Double Elimination matches
         * when the stopping round in Stage 1 is fully completed.
         */
        checkAndTriggerStage2Cut: function () {
            if (!this.roundsList || this.roundsList.length === 0) return;

            var cutTarget = this.cutTarget;
            if (!cutTarget || cutTarget <= 1) {
                try {
                    var rawCut = localStorage.getItem('tourma_advance_count_' + this.tournamentId) ||
                                 localStorage.getItem('tourma_cut_target_' + this.tournamentId);
                    if (rawCut) cutTarget = parseInt(rawCut, 10);
                } catch (e) {}
            }

            var multiCfg = null;
            try {
                multiCfg = JSON.parse(localStorage.getItem('tourma_multi_config_' + this.tournamentId));
            } catch (e) {}

            var isMultiStage = (localStorage.getItem('tourma_type_' + this.tournamentId) === 'MULTI_STAGE' || !!multiCfg || (cutTarget > 1));
            if (!isMultiStage) return;

            var lastRound = this.roundsList[this.roundsList.length - 1];
            if (!lastRound || !lastRound.matches || lastRound.matches.length === 0) return;

            var allCompleted = true;
            for (var m = 0; m < lastRound.matches.length; m++) {
                var mId = lastRound.matches[m].matchId;
                var match = this.matchesMap[mId] || lastRound.matches[m];
                var t1Name = match.team1 ? match.team1.name : '';
                var t2Name = match.team2 ? match.team2.name : '';
                var isBye = (t1Name === 'BYE' || t2Name === 'BYE');

                if (!isBye && match.status !== 'COMPLETED' && !match.winnerId) {
                    allCompleted = false;
                    break;
                }
            }

            if (!allCompleted) return;

            // Extract all qualified winners
            var qualifiedTeams = [];

            // Helper to get definitive original seed
            var self = this;
            var getDefinitiveSeed = function(teamName, fallbackSeed) {
                if (self.teamsList) {
                    for (var s = 0; s < self.teamsList.length; s++) {
                        var tObj = self.teamsList[s];
                        var tName = (typeof tObj === 'object') ? (tObj.name || tObj.rawName) : tObj;
                        if (tName === teamName) {
                            return (typeof tObj === 'object' && tObj.seed !== undefined && tObj.seed !== null && tObj.seed !== '') ? tObj.seed : '';
                        }
                    }
                }
                return (fallbackSeed !== undefined && fallbackSeed !== null) ? fallbackSeed : '';
            };

            // 1. Winners from stopping round matches
            for (var m = 0; m < lastRound.matches.length; m++) {
                var mId = lastRound.matches[m].matchId;
                var match = this.matchesMap[mId] || lastRound.matches[m];
                var wObj = this.resolveActualTeamWinner(match);
                if (wObj && wObj.name && wObj.name !== 'BYE' && wObj.name !== 'TBD') {
                    var defSeed = getDefinitiveSeed(wObj.name, wObj.seed);
                    qualifiedTeams.push({ name: wObj.name, rawName: wObj.name, seed: defSeed });
                }
            }

            // 2. Winners from earlier rounds who had direct BYEs to cut stage
            for (var r = 0; r < this.roundsList.length - 1; r++) {
                var rMatches = this.roundsList[r].matches;
                for (var i = 0; i < rMatches.length; i++) {
                    var mId = rMatches[i].matchId;
                    var rm = this.matchesMap[mId] || rMatches[i];
                    if (!rm.nextMatchId && (rm.status === 'COMPLETED' || rm.winnerId)) {
                        var wObj = this.resolveActualTeamWinner(rm);
                        if (wObj && wObj.name && wObj.name !== 'BYE' && wObj.name !== 'TBD') {
                            var alreadyIn = qualifiedTeams.some(function (t) { return t.name === wObj.name; });
                            if (!alreadyIn) {
                                var defSeed = getDefinitiveSeed(wObj.name, wObj.seed);
                                qualifiedTeams.unshift({ name: wObj.name, rawName: wObj.name, seed: defSeed });
                            }
                        }
                    }
                }
            }

            if (qualifiedTeams.length < 2) return;

            // 3. Shuffle randomly for Stage 2 (100% Random rule for SE -> DE pairing)
            // KEEP each participant's original seed!
            var shuffledStage2Teams = qualifiedTeams.slice();
            for (var i = shuffledStage2Teams.length - 1; i > 0; i--) {
                var j = Math.floor(Math.random() * (i + 1));
                var temp = shuffledStage2Teams[i];
                shuffledStage2Teams[i] = shuffledStage2Teams[j];
                shuffledStage2Teams[j] = temp;
            }

            // Save to LocalStorage
            localStorage.setItem('tourma_stage2_teams_' + this.tournamentId, JSON.stringify(shuffledStage2Teams));
            localStorage.setItem('tourma_stage1_completed_' + this.tournamentId, 'true');

            // Sync Stage 2 Teams & Multi-Stage Config to DB
            try {
                var cPath = window.contextPath || '';
                var targetUrl = (cPath ? cPath : '') + '/single-elimination';
                var pS2 = new URLSearchParams();
                pS2.append('action', 'saveStage2Teams');
                pS2.append('tournamentId', this.tournamentId);
                pS2.append('stage2Teams', JSON.stringify(shuffledStage2Teams));
                fetch(targetUrl, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                    body: pS2.toString()
                }).catch(function(err) {});

                if (multiCfg) {
                    var pCfg = new URLSearchParams();
                    pCfg.append('action', 'saveMultiStageConfig');
                    pCfg.append('tournamentId', this.tournamentId);
                    pCfg.append('multiConfig', JSON.stringify(multiCfg));
                    fetch(targetUrl, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                        body: pCfg.toString()
                    }).catch(function(err) {});
                }
            } catch (e) {}

            // Check Stage 2 format
            var s2Format = 'SINGLE_ELIMINATION';
            if (multiCfg && multiCfg.stage2Format) {
                s2Format = multiCfg.stage2Format;
            }

            if (s2Format === 'SINGLE_ELIMINATION' && window.TourmaBracketAlgorithm && typeof window.TourmaBracketAlgorithm.generateSingleElimination === 'function') {
                var seStage2Bracket = window.TourmaBracketAlgorithm.generateSingleElimination(shuffledStage2Teams, 0);
                if (seStage2Bracket) {
                    localStorage.setItem('tourma_bracket_stage2_' + this.tournamentId, JSON.stringify(seStage2Bracket));
                    localStorage.setItem('tourma_matches_stage2_' + this.tournamentId, JSON.stringify(seStage2Bracket.matchesMap || {}));
                }
            } else if (s2Format === 'DOUBLE_ELIMINATION' && window.TourmaDoubleElimAlgorithm && typeof window.TourmaDoubleElimAlgorithm.generateDoubleElimination === 'function') {
                var deBracket = window.TourmaDoubleElimAlgorithm.generateDoubleElimination(shuffledStage2Teams);
                if (deBracket) {
                    localStorage.setItem('tourma_de_matches_' + this.tournamentId, JSON.stringify(deBracket));
                }
            } else if (s2Format === 'ROUND_ROBIN' && window.TourmaRoundRobinAlgorithm && typeof window.TourmaRoundRobinAlgorithm.generateRoundRobin === 'function') {
                var rrBracket = window.TourmaRoundRobinAlgorithm.generateRoundRobin(shuffledStage2Teams, multiCfg ? multiCfg.stage2Config : null);
                if (rrBracket) {
                    localStorage.setItem('tourma_rr_matches_' + this.tournamentId, JSON.stringify(rrBracket));
                }
            }
        },

        checkFinalStage: function () {
            if (this.currentStage === 1) {
                if (window.StageEndPopup) {
                    window.StageEndPopup.update(
                        this.tournamentId,
                        'SINGLE_ELIMINATION',
                        this.matchesMap,
                        this.teamsList,
                        { cutTarget: this.cutTarget },
                        null,
                        1
                    );
                }
                if (this.cutTarget && this.cutTarget > 1) {
                    return; // Stage 1 cut stage — NEVER trigger FinalStagePopup!
                }
            }
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
         * Send AJAX POST to SingleEliminationServlet to update match score in DB
         */
        saveMatchResultAJAX: function (matchId, t1Score, t2Score, winner) {
            if (!this.tournamentId) return;
            var targetMatch = this.matchesMap ? this.matchesMap[matchId] : null;
            var t1Name = (targetMatch && targetMatch.team1) ? targetMatch.team1.name : '';
            var t2Name = (targetMatch && targetMatch.team2) ? targetMatch.team2.name : '';

            var params = new URLSearchParams();
            params.append('action', 'updateScore');
            params.append('tournamentId', this.tournamentId);
            params.append('stage', this.currentStage || 1);
            params.append('matchId', matchId);
            params.append('team1Score', t1Score !== undefined && t1Score !== null ? t1Score : '');
            params.append('team2Score', t2Score !== undefined && t2Score !== null ? t2Score : '');
            params.append('winner', winner || '');
            params.append('team1Name', t1Name);
            params.append('team2Name', t2Name);

            // Sync bracket state immediately to ensure all winner propagations are saved
            this.syncBracketToDB(true);

            fetch('single-elimination', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
                },
                body: params.toString()
            }).then(function (res) {
                return res.json();
            }).then(function (data) {
                console.log('[SE AJAX] Match result saved to DB:', data);
            }).catch(function (err) {
                console.warn('[SE AJAX] AJAX save notification:', err);
            });
        }
    };

    window.TourmaSingleElimination = window.SingleEliminationEngine;

    // Listen for custom tourmaMatchUpdated events
    document.addEventListener('tourmaMatchUpdated', function (e) {
        if (window.SingleEliminationEngine) {
            window.SingleEliminationEngine.onMatchUpdated(e.detail);
        }
    });

})();
