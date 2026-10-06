/**
 * ============================================================================
 * TOURMA - SINGLE ELIMINATION BRACKET & MATCH LIFECYCLE ENGINE (single-elimination.js)
 * 100% Database-Driven, Clean AJAX API, Zero localStorage Match Caching.
 * ============================================================================
 */
(function (window) {
    'use strict';

    var SingleEliminationEngine = {
        tournamentId: null,
        currentStage: 1,
        cutTarget: 0,
        teamsList: [],
        matchesMap: {},
        roundsList: [],
        currentViewMode: 'BRACKET', // 'BRACKET' or 'LIST'
        contextPath: '',

        /**
         * Initialize Single Elimination Page from Database Records
         */
        init: function (tournamentId, dbMatches, preloadedTeams, cutTarget, currentStage) {
            this.tournamentId = tournamentId || 'demo';
            this.currentStage = currentStage || 1;
            this.cutTarget = (cutTarget && parseInt(cutTarget, 10) > 1) ? parseInt(cutTarget, 10) : 0;
            this.teamsList = Array.isArray(preloadedTeams) ? preloadedTeams : [];
            this.contextPath = window.TourmaContextPath || '';

            console.log('[SingleEliminationEngine] Initializing with DB matches:', (dbMatches ? dbMatches.length : 0), 'Teams:', this.teamsList.length);

            // Update team count badge
            var teamBadge = document.getElementById('tournamentTeamCountBadge');
            if (teamBadge) {
                var actualCount = this.teamsList.length;
                if (actualCount === 0 && Array.isArray(dbMatches)) {
                    var set = {};
                    for (var t = 0; t < dbMatches.length; t++) {
                        var dm = dbMatches[t];
                        if (dm.team1 && dm.team1.name && !this.isPlaceholder(dm.team1.name) && dm.team1.name !== 'BYE') set[dm.team1.name] = true;
                        if (dm.team2 && dm.team2.name && !this.isPlaceholder(dm.team2.name) && dm.team2.name !== 'BYE') set[dm.team2.name] = true;
                    }
                    actualCount = Object.keys(set).length;
                }
                teamBadge.textContent = actualCount + ' Đội';
            }

            // 1. Build or Hydrate Bracket Model
            this.hydrateBracketModel(dbMatches);

            // 2. Setup Event Listeners (Controls, View Modes, Reset, Search, Popups)
            this.bindControls();

            // 3. Render Views
            this.render();

            // 4. Check Final / Cut Stage status
            this.checkTournamentCompletion();
        },

        /**
         * Construct in-memory bracket data structure from database match array
         */
        hydrateBracketModel: function (dbMatches) {
            this.matchesMap = {};
            this.roundsList = [];

            // If DB matches exist, populate matchesMap directly
            if (Array.isArray(dbMatches) && dbMatches.length > 0) {
                var roundGroups = {};
                var maxRound = 1;

                for (var i = 0; i < dbMatches.length; i++) {
                    var m = dbMatches[i];
                    var mId = String(m.matchId || m.id || m.rawId || (i + 1));
                    var rNum = parseInt(m.roundNumber || 1, 10);
                    if (rNum > maxRound) maxRound = rNum;

                    var matchNum = m.matchNumber || (i + 1);

                    var matchObj = {
                        matchId: mId,
                        id: mId,
                        rawId: m.rawId || mId,
                        roundNumber: rNum,
                        matchNumber: matchNum,
                        bracketType: m.bracketType || 'MAIN',
                        team1: m.team1 || { name: '', seed: '', score: '' },
                        team2: m.team2 || { name: '', seed: '', score: '' },
                        winnerId: m.winnerId || null,
                        nextMatchId: m.nextMatchId ? String(m.nextMatchId) : null,
                        nextMatchSlot: m.nextMatchSlot || 1,
                        isBye: m.isBye === true || m.isBye === 'true',
                        status: m.status || 'PENDING'
                    };

                    this.matchesMap[mId] = matchObj;

                    if (!roundGroups[rNum]) roundGroups[rNum] = [];
                    roundGroups[rNum].push(matchObj);
                }

                // Build ordered roundsList
                for (var r = 1; r <= maxRound; r++) {
                    var rMatches = roundGroups[r] || [];
                    var rTitle = (window.TourmaBracketAlgorithm && window.TourmaBracketAlgorithm.getRoundTitle)
                        ? window.TourmaBracketAlgorithm.getRoundTitle(r, maxRound, this.cutTarget > 1)
                        : ('Vòng ' + r);

                    this.roundsList.push({
                        roundNumber: r,
                        roundName: rTitle,
                        matches: rMatches
                    });
                }

                // Propagate advancing winners from previous rounds across the whole bracket
                this.propagateAllWinners();
            } else {
                // Fallback: Generate tournament structure algorithmically if DB was empty
                if (window.TourmaBracketAlgorithm && this.teamsList.length >= 2) {
                    var generated = window.TourmaBracketAlgorithm.generateSingleElimination(this.teamsList, this.cutTarget);
                    this.roundsList = generated.roundsList || [];
                    this.matchesMap = generated.matchesMap || {};
                }
            }
        },

        findMatch: function (targetId) {
            if (!targetId) return null;
            var strId = String(targetId).trim();
            if (this.matchesMap[strId]) return this.matchesMap[strId];

            for (var k in this.matchesMap) {
                var m = this.matchesMap[k];
                if (m.matchId && String(m.matchId) === strId) return m;
                if (m.id && String(m.id) === strId) return m;
                if (m.rawId && String(m.rawId) === strId) return m;
                if (m.matchNumber && String(m.matchNumber) === strId) return m;
                if (m.matchId && (String(m.matchId).endsWith('_' + strId) || String(m.matchId).endsWith('_S1_' + strId) || String(m.matchId).endsWith('_S2_' + strId))) return m;
            }
            return null;
        },

        findParentMatch: function (targetMatchId, slot) {
            if (!targetMatchId) return null;
            var targetStr = String(targetMatchId).trim();

            for (var k in this.matchesMap) {
                var m = this.matchesMap[k];
                if (m.nextMatchId) {
                    var nId = String(m.nextMatchId).trim();
                    var isMatch = (nId === targetStr || targetStr.endsWith('_' + nId) || nId.endsWith('_' + targetStr));
                    if (isMatch) {
                        var s = (m.nextMatchSlot === 2 || m.nextMatchSlot === '2' || m.nextMatchSlot === 'SLOT_2') ? 2 : 1;
                        if (slot === undefined || s === slot) {
                            return m;
                        }
                    }
                }
            }
            return null;
        },

        getMatchWinner: function (m) {
            if (!m) return null;
            if (m.isBye) {
                var t1N = (m.team1 && m.team1.name) ? m.team1.name : '';
                var t2N = (m.team2 && m.team2.name) ? m.team2.name : '';
                if (t1N && t1N !== 'BYE' && !this.isPlaceholder(t1N)) return m.team1;
                if (t2N && t2N !== 'BYE' && !this.isPlaceholder(t2N)) return m.team2;
            }

            var wid = m.winnerId ? String(m.winnerId).trim() : null;
            if (wid) {
                if (wid === 'team1' || wid === '1' || wid === 'SLOT_1') return m.team1;
                if (wid === 'team2' || wid === '2' || wid === 'SLOT_2') return m.team2;
                if (m.team1 && (wid === String(m.team1.id) || wid === String(m.team1.name))) return m.team1;
                if (m.team2 && (wid === String(m.team2.id) || wid === String(m.team2.name))) return m.team2;
            }

            var s1 = (m.team1 && m.team1.score !== undefined && m.team1.score !== null && m.team1.score !== '') ? parseInt(m.team1.score, 10) : NaN;
            var s2 = (m.team2 && m.team2.score !== undefined && m.team2.score !== null && m.team2.score !== '') ? parseInt(m.team2.score, 10) : NaN;

            if (!isNaN(s1) && !isNaN(s2) && (m.status === 'COMPLETED' || m.status === 'FINISHED' || m.status === 'DONE')) {
                if (s1 > s2) return m.team1;
                if (s2 > s1) return m.team2;
            }
            return null;
        },

        propagateAllWinners: function () {
            for (var r = 2; r <= this.roundsList.length; r++) {
                var roundObj = null;
                for (var i = 0; i < this.roundsList.length; i++) {
                    if (this.roundsList[i].roundNumber === r) {
                        roundObj = this.roundsList[i];
                        break;
                    }
                }
                if (!roundObj || !roundObj.matches) continue;

                for (var j = 0; j < roundObj.matches.length; j++) {
                    var match = this.matchesMap[roundObj.matches[j].matchId] || roundObj.matches[j];
                    var p1 = this.findParentMatch(match.matchId, 1);
                    var p2 = this.findParentMatch(match.matchId, 2);

                    var p1Winner = this.getMatchWinner(p1);
                    var p2Winner = this.getMatchWinner(p2);

                    if (p1Winner && p1Winner.name && p1Winner.name !== 'BYE' && !this.isPlaceholder(p1Winner.name)) {
                        if (!match.team1 || this.isPlaceholder(match.team1.name) || match.team1.name !== p1Winner.name) {
                            match.team1 = {
                                name: p1Winner.name,
                                seed: p1Winner.seed || '',
                                score: (match.team1 && match.team1.score !== undefined) ? match.team1.score : ''
                            };
                        }
                    } else if (!match.team1 || !match.team1.name || this.isPlaceholder(match.team1.name)) {
                        var t1P = p1 ? ('W #' + (p1.matchNumber || p1.matchId)) : 'TBD';
                        match.team1 = { name: t1P, seed: '', score: '' };
                    }

                    if (p2Winner && p2Winner.name && p2Winner.name !== 'BYE' && !this.isPlaceholder(p2Winner.name)) {
                        if (!match.team2 || this.isPlaceholder(match.team2.name) || match.team2.name !== p2Winner.name) {
                            match.team2 = {
                                name: p2Winner.name,
                                seed: p2Winner.seed || '',
                                score: (match.team2 && match.team2.score !== undefined) ? match.team2.score : ''
                            };
                        }
                    } else if (!match.team2 || !match.team2.name || this.isPlaceholder(match.team2.name)) {
                        var t2P = p2 ? ('W #' + (p2.matchNumber || p2.matchId)) : 'TBD';
                        match.team2 = { name: t2P, seed: '', score: '' };
                    }

                    if (match.team1 && match.team1.name && !this.isPlaceholder(match.team1.name) &&
                        match.team2 && match.team2.name && !this.isPlaceholder(match.team2.name)) {
                        if (match.status !== 'COMPLETED' && match.status !== 'FINISHED') {
                            match.status = 'READY';
                        }
                    }
                }
            }
        },

        /**
         * Render both Bracket and List Views based on current state
         */
        render: function () {
            this.renderBracketView();
            this.renderListView();
            this.updateRoundRandomButtons();
            this.checkTournamentCompletion();
        },

        /**
         * Render Canvas Bracket View (Columns of Node Cards with SVG Connector Lines)
         */
        renderBracketView: function () {
            var self = this;
            var container = document.getElementById('singleBracketColumnsWrapper');
            if (!container) return;
            container.innerHTML = '';

            var totalRounds = this.roundsList.length;
            if (totalRounds === 0) {
                container.innerHTML = '<div style="padding: 40px; text-align: center; color: var(--text-muted, #94a3b8); font-size: 15px;">Chưa có dữ liệu nhánh đấu.</div>';
                return;
            }

            for (var r = 0; r < totalRounds; r++) {
                var roundObj = this.roundsList[r];
                var col = document.createElement('div');
                col.className = 'single-round-column';
                col.setAttribute('data-round', roundObj.roundNumber);

                // Round Header with Title & Random Control
                var header = document.createElement('div');
                header.className = 'single-round-header';
                var rName = roundObj.roundName || ('Vòng ' + roundObj.roundNumber);
                var ctrlHtml = (window.TourmaRoundControls && typeof window.TourmaRoundControls.renderHeaderControlsHtml === 'function')
                    ? window.TourmaRoundControls.renderHeaderControlsHtml(roundObj.roundNumber, rName)
                    : ('<div class="round-header-random-controls" data-round="' + roundObj.roundNumber + '"><input type="number" id="round_random_score_bracket_' + roundObj.roundNumber + '" name="round_random_score_bracket_' + roundObj.roundNumber + '" class="round-random-input" data-round="' + roundObj.roundNumber + '" min="1" max="99" value="" autocomplete="off" title="Nhập điểm thắng tùy chỉnh" /><button type="button" class="btn-round-random" data-round="' + roundObj.roundNumber + '" title="Tạo tỉ số ngẫu nhiên cho ' + rName + '"><i class="fa-solid fa-dice"></i> Random</button><button type="button" class="btn-round-reset" data-round="' + roundObj.roundNumber + '" title="Reset kết quả ' + rName + '"><i class="fa-solid fa-rotate-right"></i></button></div>');

                header.innerHTML = '<div class="round-header-title">' + rName + '</div>' + ctrlHtml;
                col.appendChild(header);

                // Match Cards Wrapper (vertically distributed with space-around)
                var matchesWrapper = document.createElement('div');
                matchesWrapper.className = 'single-round-matches-box';

                if (roundObj.matches) {
                    for (var m = 0; m < roundObj.matches.length; m++) {
                        var matchData = roundObj.matches[m];
                        var liveMatch = this.matchesMap[matchData.matchId] || matchData;

                        var cardNode = null;
                        if (window.TourmaBracketCard && typeof window.TourmaBracketCard.createNodeElement === 'function') {
                            cardNode = window.TourmaBracketCard.createNodeElement(liveMatch);
                        } else {
                            cardNode = this.createFallbackCard(liveMatch);
                        }

                        if (cardNode) {
                            this.attachCardClickListener(cardNode, liveMatch);
                            matchesWrapper.appendChild(cardNode);
                        }
                    }
                }

                col.appendChild(matchesWrapper);
                container.appendChild(col);
            }

            // Bind click events on Bracket View Random Round buttons via TourmaRoundControls
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.bindEvents === 'function') {
                window.TourmaRoundControls.bindEvents(container, this);
            } else {
                var bracketRandomBtns = container.querySelectorAll('.btn-round-random');
                bracketRandomBtns.forEach(function (btn) {
                    btn.addEventListener('click', function (e) {
                        e.stopPropagation();
                        var rNum = parseInt(this.getAttribute('data-round'), 10);
                        self.executeRandomRound(rNum);
                    });
                });
            }

            // Initialize viewport panning & zooming
            if (window.TourmaViewport && typeof window.TourmaViewport.init === 'function') {
                window.TourmaViewport.init('bracketViewportContainer', 'bracketViewportCanvas', {
                    badgeId: 'zoomLevelBadge',
                    onRedraw: function () {
                        if (self && typeof self.drawSvgConnectors === 'function') {
                            self.drawSvgConnectors();
                        } else if (window.SingleEliminationEngine && typeof window.SingleEliminationEngine.drawSvgConnectors === 'function') {
                            window.SingleEliminationEngine.drawSvgConnectors();
                        }
                    }
                });
            }

            // Draw SVG connector lines with animation frame & timeout backups
            requestAnimationFrame(function () {
                if (self && typeof self.drawSvgConnectors === 'function') self.drawSvgConnectors();
            });
            setTimeout(function () {
                if (self && typeof self.drawSvgConnectors === 'function') self.drawSvgConnectors();
            }, 60);
            setTimeout(function () {
                if (self && typeof self.drawSvgConnectors === 'function') self.drawSvgConnectors();
            }, 250);
        },

        /**
         * Universal Orthogonal SVG Bracket Connectors Drawing
         */
        drawSvgConnectors: function () {
            var canvas = document.getElementById('bracketViewportCanvas');
            var wrapper = document.getElementById('singleBracketColumnsWrapper');
            if (!canvas || !wrapper) return;

            if (window.TourmaViewport && typeof window.TourmaViewport.drawConnectors === 'function') {
                window.TourmaViewport.drawConnectors(canvas, wrapper, this.matchesMap, 1.0);
            }
        },

        /**
         * Render Matches List View (Cards grouped by Round)
         */
        renderListView: function () {
            var self = this;
            var container = document.getElementById('singleListViewContainer');
            if (!container) return;
            container.innerHTML = '';

            if (this.roundsList.length === 0) {
                container.innerHTML = '<div style="padding: 40px; text-align: center; color: var(--text-muted, #94a3b8);">Chưa có trận đấu nào.</div>';
                return;
            }

            for (var r = 0; r < this.roundsList.length; r++) {
                var roundObj = this.roundsList[r];
                var roundSec = document.createElement('div');
                roundSec.className = 'list-round-section';
                roundSec.setAttribute('data-round', roundObj.roundNumber);

                // Section Header with Random Round Action
                var secHeader = document.createElement('div');
                secHeader.className = 'list-round-header';
                var rTitle = roundObj.roundName || ('Vòng ' + roundObj.roundNumber);
                var listCtrlHtml = (window.TourmaRoundControls && typeof window.TourmaRoundControls.renderListHeaderControlsHtml === 'function')
                    ? window.TourmaRoundControls.renderListHeaderControlsHtml(roundObj.roundNumber, rTitle)
                    : ('<div class="list-round-actions" data-round="' + roundObj.roundNumber + '"><input type="number" id="round_random_score_list_' + roundObj.roundNumber + '" name="round_random_score_list_' + roundObj.roundNumber + '" class="round-random-input" data-round="' + roundObj.roundNumber + '" min="1" max="99" value="" autocomplete="off" title="Nhập điểm thắng tùy chỉnh" /><button type="button" class="btn-random-round" data-round="' + roundObj.roundNumber + '" title="Tạo tỉ số ngẫu nhiên cho ' + rTitle + '"><i class="fa-solid fa-dice"></i> Random ' + rTitle + '</button><button type="button" class="btn-reset-round" data-round="' + roundObj.roundNumber + '" title="Reset kết quả ' + rTitle + '"><i class="fa-solid fa-rotate-right"></i> Reset ' + rTitle + '</button></div>');

                secHeader.innerHTML = '<div class="list-round-title-group">'
                    + '<h3 class="list-round-title">' + rTitle + '</h3>'
                    + '<span class="list-round-badge">' + (roundObj.matches ? roundObj.matches.length : 0) + ' trận</span>'
                    + '</div>'
                    + listCtrlHtml;
                roundSec.appendChild(secHeader);

                // Matches Grid
                var grid = document.createElement('div');
                grid.className = 'list-matches-grid';

                if (roundObj.matches) {
                    for (var m = 0; m < roundObj.matches.length; m++) {
                        var matchData = roundObj.matches[m];
                        var liveMatch = this.matchesMap[matchData.matchId] || matchData;

                        var listCard = null;
                        if (window.TourmaMatchCard && typeof window.TourmaMatchCard.createCardElement === 'function') {
                            listCard = window.TourmaMatchCard.createCardElement(liveMatch);
                        } else {
                            listCard = this.createFallbackCard(liveMatch);
                        }

                        if (listCard) {
                            this.attachCardClickListener(listCard, liveMatch);
                            grid.appendChild(listCard);
                        }
                    }
                }

                roundSec.appendChild(grid);
                container.appendChild(roundSec);
            }

            // Bind click events on Random Round buttons via TourmaRoundControls
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.bindEvents === 'function') {
                window.TourmaRoundControls.bindEvents(container, this);
            } else {
                var self = this;
                var randomBtns = container.querySelectorAll('.btn-random-round');
                randomBtns.forEach(function (btn) {
                    btn.addEventListener('click', function (e) {
                        e.stopPropagation();
                        var rNum = parseInt(this.getAttribute('data-round'), 10);
                        self.executeRandomRound(rNum);
                    });
                });
            }
        },

        /**
         * Attach Match Card Click to open Score Edit Popup
         */
        /**
         * Attach Match Card Click to open Score Edit Popup
         */
        attachCardClickListener: function (cardEl, matchData) {
            var self = this;
            cardEl.addEventListener('click', function () {
                var m = self.findMatch(matchData.matchId) || matchData;
                var t1Name = (m.team1 && m.team1.name) ? m.team1.name : '';
                var t2Name = (m.team2 && m.team2.name) ? m.team2.name : '';

                // Cannot edit placeholder matches
                if (self.isPlaceholder(t1Name) || self.isPlaceholder(t2Name) || m.isBye) {
                    return;
                }

                var modal = window.TourmaScoreModal || window.TourmaPopup;
                if (modal && typeof modal.open === 'function') {
                    modal.open({
                        matchId: m.matchId,
                        tournamentId: self.tournamentId,
                        roundName: (m.matchNumber ? ('Trận #' + m.matchNumber) : ('Vòng ' + m.roundNumber)),
                        team1Name: t1Name,
                        team1Seed: (m.team1 && m.team1.seed) ? m.team1.seed : '',
                        team1Score: (m.team1 && m.team1.score !== undefined && m.team1.score !== null) ? m.team1.score : '',
                        team2Name: t2Name,
                        team2Seed: (m.team2 && m.team2.seed) ? m.team2.seed : '',
                        team2Score: (m.team2 && m.team2.score !== undefined && m.team2.score !== null) ? m.team2.score : '',
                        winnerId: m.winnerId,
                        status: m.status,
                        allowDraw: false
                    }, function (resultData) {
                        self.saveMatchScore(
                            m.matchId,
                            resultData.team1Score !== undefined ? resultData.team1Score : resultData.score1,
                            resultData.team2Score !== undefined ? resultData.team2Score : resultData.score2,
                            resultData.penalty1,
                            resultData.penalty2,
                            resultData.winner
                        );
                    });
                }
            });
        },

        /**
         * Handle 1-click Quick Winner selection
         */
        handleQuickWinner: function (matchId, winnerSlotNum, customScore) {
            var m = this.findMatch(matchId);
            if (!m) return;
            var t1Name = (m.team1 && m.team1.name) ? m.team1.name : '';
            var t2Name = (m.team2 && m.team2.name) ? m.team2.name : '';
            if (this.isPlaceholder(t1Name) || this.isPlaceholder(t2Name) || m.isBye) return;

            var winScore = customScore ? parseInt(customScore, 10) : 2;
            var loseScore = customScore ? Math.max(0, winScore - 1) : 0;

            var score1 = (winnerSlotNum === 1) ? winScore : loseScore;
            var score2 = (winnerSlotNum === 1) ? loseScore : winScore;
            var winnerFlag = (winnerSlotNum === 1) ? 'team1' : 'team2';

            this.saveMatchScore(m.matchId, score1, score2, null, null, winnerFlag);
        },

        /**
         * Core Function: Save Match Score directly to Database via /api/match-update
         */
        saveMatchScore: function (matchId, score1, score2, penalty1, penalty2, winnerSlot) {
            var self = this;
            var m = this.findMatch(matchId);
            if (!m) return;

            var t1Name = (m.team1 && m.team1.name) ? m.team1.name : '';
            var t2Name = (m.team2 && m.team2.name) ? m.team2.name : '';

            var payload = {
                action: 'updateMatch',
                tournamentId: this.tournamentId,
                matchId: m.matchId || matchId,
                score1: (score1 !== undefined && score1 !== null && score1 !== '') ? score1 : 0,
                score2: (score2 !== undefined && score2 !== null && score2 !== '') ? score2 : 0,
                penalty1: (penalty1 !== undefined && penalty1 !== null) ? penalty1 : '',
                penalty2: (penalty2 !== undefined && penalty2 !== null) ? penalty2 : '',
                winner: winnerSlot || '',
                team1Name: t1Name,
                team2Name: t2Name
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
                    if (data.status === 'success') {
                        // Update local match state
                        m.team1.score = payload.score1;
                        m.team2.score = payload.score2;
                        m.winnerId = winnerSlot;
                        m.status = 'COMPLETED';

                        // Determine winner team
                        var winnerObj = self.getMatchWinner(m);
                        var winnerName = winnerObj ? winnerObj.name : ((winnerSlot === 'team1' || winnerSlot === '1') ? t1Name : t2Name);
                        var winnerSeed = winnerObj ? winnerObj.seed : ((winnerSlot === 'team1' || winnerSlot === '1') ? (m.team1 ? m.team1.seed : '') : (m.team2 ? m.team2.seed : ''));

                        // Advance winner to target match
                        var targetNextId = data.nextMatchId || m.nextMatchId;
                        var nextM = self.findMatch(targetNextId);
                        if (nextM) {
                            var isSlot2 = (m.nextMatchSlot === 2 || m.nextMatchSlot === '2' || m.nextMatchSlot === 'SLOT_2' || data.nextSlot === 'SLOT_2' || data.nextSlot === '2');
                            if (isSlot2) {
                                nextM.team2 = { name: winnerName, seed: winnerSeed, score: (nextM.team2 && nextM.team2.score !== undefined) ? nextM.team2.score : '' };
                            } else {
                                nextM.team1 = { name: winnerName, seed: winnerSeed, score: (nextM.team1 && nextM.team1.score !== undefined) ? nextM.team1.score : '' };
                            }
                            if (nextM.team1 && nextM.team1.name && !self.isPlaceholder(nextM.team1.name) &&
                                nextM.team2 && nextM.team2.name && !self.isPlaceholder(nextM.team2.name)) {
                                nextM.status = 'READY';
                            }
                        }

                        // Propagate all winners across bracket
                        self.propagateAllWinners();

                        // Re-render UI
                        self.render();
                        self.checkTournamentCompletion();
                    } else {
                        console.error('[SingleEliminationEngine] Error response:', data);
                        alert('Lỗi lưu kết quả: ' + (data.message || 'Không rõ nguyên nhân'));
                    }
                })
                .catch(function (err) {
                    console.error('[SingleEliminationEngine] Error saving match score:', err);
                });
        },

        /**
         * Randomize all matches in a round via high-speed, deadlock-free Batch Random Servlet
         */
        executeRandomRound: function (roundNumber) {
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.executeRandomRound === 'function') {
                window.TourmaRoundControls.executeRandomRound(this, roundNumber);
            }
        },

        /**
         * Reset all matches in a round with cascading downstream clearing
         */
        executeResetRound: function (roundNumber) {
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.executeResetRound === 'function') {
                window.TourmaRoundControls.executeResetRound(this, roundNumber);
            }
        },

        /**
         * Randomize All Playable Matches in the entire bracket
         */
        executeRandomAll: function () {
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.executeRandomAll === 'function') {
                window.TourmaRoundControls.executeRandomAll(this);
            }
        },

        /**
         * Reset entire Single Elimination bracket via POST to servlet
         */
        resetBracket: function (skipConfirm) {
            var self = this;
            var tid = this.tournamentId || window.TourmaTournamentId || 'demo';
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
                    if (data && data.status === 'success') {
                        window.location.reload();
                    } else {
                        // Fallback to /single-elimination
                        fetch((self.contextPath || '') + '/single-elimination', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                            body: new URLSearchParams({ action: 'reset', tournamentId: tid, stage: self.currentStage || 1 }).toString()
                        })
                            .then(function () { window.location.reload(); })
                            .catch(function () { window.location.reload(); });
                    }
                })
                .catch(function (err) {
                    console.error('[SingleEliminationEngine] Reset error:', err);
                    window.location.reload();
                });
        },

        /**
         * Check if stage or entire tournament has completed (Grand Final finished)
         */
        checkTournamentCompletion: function () {
            // 1. Qualifier/Cut Stages (Cut Top N) never produce an overall tournament champion
            if (this.cutTarget && this.cutTarget > 1) {
                if (window.FinalStagePopup && typeof window.FinalStagePopup.closeBanner === 'function') {
                    window.FinalStagePopup.closeBanner();
                }
                return;
            }

            // 2. Delegate directly to unified FinalStagePopup engine
            if (window.FinalStagePopup && typeof window.FinalStagePopup.checkAndRender === 'function') {
                window.FinalStagePopup.checkAndRender(
                    this.tournamentId,
                    'SINGLE_ELIMINATION',
                    this.matchesMap,
                    this.teamsList,
                    { isCutStage: false, cutTarget: 0 },
                    null
                );
            }
        },

        /**
         * Bind UI Controls (View Mode, Zoom, Random Buttons, Search Filter)
         */
        bindControls: function () {
            var self = this;

            // View Mode Toggle (Bracket vs List)
            var btnBracketView = document.getElementById('btnBracketView');
            var btnListView = document.getElementById('btnListView');
            var bracketContainer = document.getElementById('singleBracketViewContainer');
            var listContainer = document.getElementById('singleListViewContainer');

            if (btnBracketView && btnListView) {
                btnBracketView.addEventListener('click', function () {
                    self.currentViewMode = 'BRACKET';
                    btnBracketView.classList.add('active');
                    btnListView.classList.remove('active');
                    if (bracketContainer) bracketContainer.style.display = 'block';
                    if (listContainer) listContainer.style.display = 'none';
                    self.renderBracketView();
                });

                btnListView.addEventListener('click', function () {
                    self.currentViewMode = 'LIST';
                    btnListView.classList.add('active');
                    btnBracketView.classList.remove('active');
                    if (bracketContainer) bracketContainer.style.display = 'none';
                    if (listContainer) listContainer.style.display = 'block';
                    self.renderListView();
                });
            }

            // Redraw SVG connectors on window resize
            window.addEventListener('resize', function () {
                if (self.currentViewMode === 'BRACKET') {
                    self.drawSvgConnectors();
                }
            });

            // Random All Button
            var btnRandomAll = document.getElementById('btnRandomAllMatches');
            if (btnRandomAll) {
                btnRandomAll.addEventListener('click', function () {
                    self.executeRandomAll();
                });
            }

            // Reset Bracket Button
            var btnReset = document.getElementById('btnResetBracket');
            if (btnReset) {
                btnReset.addEventListener('click', function () {
                    self.resetBracket();
                });
            }

            // Search Box Filtering
            var searchInput = document.getElementById('bracketSearchInput');
            if (searchInput) {
                searchInput.addEventListener('input', function () {
                    var query = (this.value || '').trim().toLowerCase();
                    self.filterMatches(query);
                });
            }
        },

        /**
         * Search filter highlighting matching team names
         */
        filterMatches: function (query) {
            var cards = document.querySelectorAll('.bracket-node-card, .match-card');
            cards.forEach(function (card) {
                if (!query) {
                    card.style.opacity = '1';
                    card.classList.remove('search-match', 'search-dimmed');
                    return;
                }
                var text = (card.textContent || '').toLowerCase();
                if (text.indexOf(query) !== -1) {
                    card.style.opacity = '1';
                    card.classList.add('search-match');
                    card.classList.remove('search-dimmed');
                } else {
                    card.style.opacity = '0.35';
                    card.classList.remove('search-match');
                    card.classList.add('search-dimmed');
                }
            });
        },

        updateRoundRandomButtons: function () {
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.updateButtonsState === 'function') {
                window.TourmaRoundControls.updateButtonsState(this);
            }
        },

        isPlaceholder: function (name) {
            if (name === undefined || name === null) return true;
            var t = String(name).trim();
            if (!t || t === 'BYE' || t === 'TBD' || t === '?') return true;
            if (t.startsWith('W #') || t.startsWith('L #') || t.startsWith('W#') || t.startsWith('L#')) return true;
            if (t.startsWith('Winner ') || t.startsWith('Loser ')) return true;
            return false;
        },

        isQuickMode: false,

        toggleQuickMode: function () {
            this.isQuickMode = !this.isQuickMode;
            window.TourmaQuickMode = this.isQuickMode;
            var btn = document.getElementById('singleBtnQuickMode');
            var statusText = btn ? btn.querySelector('.quick-mode-status-text') : null;
            if (this.isQuickMode) {
                if (btn) btn.classList.add('active');
                if (statusText) statusText.textContent = 'ON';
            } else {
                if (btn) btn.classList.remove('active');
                if (statusText) statusText.textContent = 'OFF';
            }
        },

        switchViewMode: function (mode) {
            var btnBracketView = document.getElementById('btnViewBracket');
            var btnListView = document.getElementById('btnViewList');
            var bracketFrame = document.getElementById('bracketViewportFrame');
            var listContainer = document.getElementById('singleListViewContainer');

            if (mode === 'bracket') {
                this.currentViewMode = 'BRACKET';
                if (btnBracketView) btnBracketView.classList.add('active');
                if (btnListView) btnListView.classList.remove('active');
                if (bracketFrame) bracketFrame.style.display = 'block';
                if (listContainer) listContainer.style.display = 'none';
                this.renderBracketView();
            } else {
                this.currentViewMode = 'LIST';
                if (btnListView) btnListView.classList.add('active');
                if (btnBracketView) btnBracketView.classList.remove('active');
                if (bracketFrame) bracketFrame.style.display = 'none';
                if (listContainer) listContainer.style.display = 'block';
                this.renderListView();
            }
        },

        openResetModal: function () {
            var modal = document.getElementById('seResetModalBackdrop');
            if (modal) {
                modal.style.display = 'flex';
                modal.classList.add('show');
            }
        },

        closeResetModal: function () {
            var modal = document.getElementById('seResetModalBackdrop');
            if (modal) {
                modal.style.display = 'none';
                modal.classList.remove('show');
            }
        },

        confirmResetBracket: function () {
            this.closeResetModal();
            this.resetBracket(true);
        },

        createFallbackCard: function (data) {
            var div = document.createElement('div');
            div.className = 'bracket-node-card';
            div.setAttribute('data-match-id', data.matchId || data.id);
            var t1 = data.team1 || {};
            var t2 = data.team2 || {};
            div.innerHTML = '<div class="team-row"><span class="team-name">' + (t1.name || 'TBD') + '</span><span class="team-score">' + (t1.score !== undefined ? t1.score : '-') + '</span></div>'
                + '<div class="team-row"><span class="team-name">' + (t2.name || 'TBD') + '</span><span class="team-score">' + (t2.score !== undefined ? t2.score : '-') + '</span></div>';
            return div;
        }
    };

    // Export globally for both namespaces
    window.SingleEliminationEngine = SingleEliminationEngine;
    window.TourmaSingleElimination = SingleEliminationEngine;
    if (!window.TourmaPopup && window.TourmaScoreModal) {
        window.TourmaPopup = window.TourmaScoreModal;
    }

    // Global listener for tourmaMatchUpdated to ensure DB sync across all components
    document.addEventListener('tourmaMatchUpdated', function (e) {
        var detail = e.detail;
        if (!detail || !detail.matchId) return;
        if (window.SingleEliminationEngine && typeof window.SingleEliminationEngine.saveMatchScore === 'function') {
            window.SingleEliminationEngine.saveMatchScore(
                detail.matchId,
                detail.team1Score !== undefined ? detail.team1Score : detail.score1,
                detail.team2Score !== undefined ? detail.team2Score : detail.score2,
                detail.penalty1,
                detail.penalty2,
                detail.winner
            );
        }
    });

})(window);
