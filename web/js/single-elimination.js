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
        init: function (tourneyId, dbMatches) {
            this.tournamentId = tourneyId || 'demo';

            // 1. Load Teams List
            var storageKeyTeams = 'tourma_teams_' + this.tournamentId;
            var teams = null;
            try {
                teams = JSON.parse(localStorage.getItem(storageKeyTeams));
            } catch (e) {
                teams = null;
            }

            if (!teams || teams.length === 0) {
                teams = [
                    'Hà Nội FC', 'Công An Hà Nội', 'Hải Phòng FC', 'Đông Á Thanh Hóa',
                    'Sông Lam Nghệ An', 'SHB Đà Nẵng', 'Bình Định FC', 'Becamex Bình Dương',
                    'TP Hồ Chí Minh', 'Hồng Lĩnh Hà Tĩnh', 'Khánh Hòa FC', 'Quảng Nam FC',
                    'Hoàng Anh Gia Lai', 'Viettel FC', 'Thép Xanh Nam Định', 'PVF-CAND',
                    'Bà Rịa Vũng Tàu', 'Long An FC'
                ];
            }
            this.teamsList = teams;

            // Update team count badge in top toolbar
            var countBadge = document.getElementById('tournamentTeamCountBadge');
            if (countBadge) {
                countBadge.innerText = this.teamsList.length + ' Đội';
            }

            // 2. Generate or Restore Matches Data using TourmaBracketAlgorithm
            this.generateMatchesStructure(dbMatches);

            // 3. Render Views
            this.renderBracketView();
            this.renderListView();

            // Restore active view mode (bracket or list) from localStorage
            var storageKeyViewMode = 'tourma_view_mode_' + this.tournamentId;
            var savedMode = localStorage.getItem(storageKeyViewMode) || 'bracket';
            this.switchViewMode(savedMode);

            // 4. Attach resize & redraw listeners
            var self = this;
            window.addEventListener('resize', function () {
                self.drawTreeConnectors();
            });
            setTimeout(function () {
                self.drawTreeConnectors();
            }, 100);
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

            // If DB matches exist, use them
            if (dbMatches && dbMatches.length > 0) {
                this.buildMapFromList(dbMatches);
                if (window.TourmaBracketAlgorithm) {
                    window.TourmaBracketAlgorithm.renumberMatchesContiguously(this.roundsList, this.matchesMap);
                }
                return;
            }

            // Check if savedMatches has any user-entered match scores
            var hasAnyPlayedScores = false;
            if (savedMatches) {
                var keys = Object.keys(savedMatches);
                for (var k = 0; k < keys.length; k++) {
                    var sm = savedMatches[keys[k]];
                    var t1 = sm.team1 ? sm.team1.name : '';
                    var t2 = sm.team2 ? sm.team2.name : '';
                    var isByeMatch = (t1 === 'BYE' || t2 === 'BYE');
                    if (!isByeMatch && (sm.status === 'COMPLETED' || sm.status === 'done' || (sm.team1 && sm.team1.score !== '') || (sm.team2 && sm.team2.score !== ''))) {
                        hasAnyPlayedScores = true;
                        break;
                    }
                }
            }

            // Only use savedMatches if matches have actually been played
            // If no matches played yet, always generate fresh bracket to reflect any team list changes!
            if (hasAnyPlayedScores && savedMatches && Object.keys(savedMatches).length > 0) {
                this.matchesMap = savedMatches;
                this.buildRoundsFromMap();
                if (window.TourmaBracketAlgorithm) {
                    window.TourmaBracketAlgorithm.renumberMatchesContiguously(this.roundsList, this.matchesMap);
                }
                return;
            }

            // Otherwise, generate standard single elimination bracket structure using algorithm engine
            if (window.TourmaBracketAlgorithm) {
                var generated = window.TourmaBracketAlgorithm.generateSingleElimination(this.teamsList);
                this.roundsList = generated.roundsList;
                this.matchesMap = generated.matchesMap;
            }

            this.persistMatches();
        },

        /**
         * Render Bracket Viewport Tree Columns Dynamically
         */
        renderBracketView: function () {
            var canvasWrapper = document.getElementById('singleBracketColumnsWrapper');
            if (!canvasWrapper) return;

            canvasWrapper.innerHTML = '';

            for (var r = 0; r < this.roundsList.length; r++) {
                var roundObj = this.roundsList[r];

                var col = document.createElement('div');
                col.className = 'single-round-column';

                var header = document.createElement('div');
                header.className = 'single-round-header';
                header.innerText = roundObj.title;
                col.appendChild(header);

                var box = document.createElement('div');
                box.className = 'single-round-matches-box';

                for (var m = 0; m < roundObj.matches.length; m++) {
                    var matchData = roundObj.matches[m];
                    if (window.TourmaBracketCard && typeof window.TourmaBracketCard.createNodeElement === 'function') {
                        var nodeElem = window.TourmaBracketCard.createNodeElement(matchData);
                        if (nodeElem) box.appendChild(nodeElem);
                    }
                }

                col.appendChild(box);
                canvasWrapper.appendChild(col);
            }

            var self = this;
            setTimeout(function () {
                self.drawTreeConnectors();
            }, 50);
        },

        /**
         * Dynamic High-Precision SVG Stepped Tree Connectors
         */
        drawTreeConnectors: function () {
            var canvas = document.getElementById('bracketViewportCanvas');
            var canvasWrapper = document.getElementById('singleBracketColumnsWrapper');
            if (!canvas || !canvasWrapper) return;

            var svg = document.getElementById('bracketSvgConnectors');
            if (!svg) {
                svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
                svg.id = 'bracketSvgConnectors';
                svg.setAttribute('class', 'bracket-svg-connectors');
                canvas.insertBefore(svg, canvasWrapper);
            }

            // Sync SVG coordinate dimensions with canvas
            var w = Math.max(canvas.scrollWidth, canvasWrapper.offsetWidth + 100);
            var h = Math.max(canvas.scrollHeight, canvasWrapper.offsetHeight + 100);
            svg.setAttribute('width', w);
            svg.setAttribute('height', h);
            svg.innerHTML = '';

            var cRect = canvas.getBoundingClientRect();
            var scale = (window.TourmaViewport && window.TourmaViewport.currentZoom) ? window.TourmaViewport.currentZoom : 1;

            var keys = Object.keys(this.matchesMap);
            for (var i = 0; i < keys.length; i++) {
                var mId = keys[i];
                var match = this.matchesMap[mId];
                if (!match || !match.nextMatchId) continue;

                var sourceEl = document.querySelector('.bracket-node-card[data-match-id="' + mId + '"]');
                var targetEl = document.querySelector('.bracket-node-card[data-match-id="' + match.nextMatchId + '"]');

                if (!sourceEl || !targetEl) continue;

                var sRect = sourceEl.getBoundingClientRect();
                var tRect = targetEl.getBoundingClientRect();

                // Compute exact center right of source card
                var x1 = (sRect.right - cRect.left) / scale;
                var y1 = (sRect.top + sRect.height / 2 - cRect.top) / scale;

                // Compute exact target team row connector entry of target card
                var slot = match.nextMatchSlot || 1;
                var x2 = (tRect.left - cRect.left) / scale;
                var y2 = (tRect.top + (slot === 1 ? (tRect.height * 0.42) : (tRect.height * 0.72)) - cRect.top) / scale;

                var midX = x1 + (x2 - x1) / 2;
                var isCompleted = (match.status === 'COMPLETED' || match.status === 'done');

                // Draw Orthogonal Step Line: x1,y1 -> midX,y1 -> midX,y2 -> x2,y2
                var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                var d = 'M ' + x1.toFixed(1) + ' ' + y1.toFixed(1) +
                        ' H ' + midX.toFixed(1) +
                        ' V ' + y2.toFixed(1) +
                        ' H ' + x2.toFixed(1);

                path.setAttribute('d', d);
                path.setAttribute('fill', 'none');
                path.setAttribute('stroke', isCompleted ? '#2dd4bf' : 'rgba(255, 255, 255, 0.2)');
                path.setAttribute('stroke-width', '2');
                path.setAttribute('stroke-linecap', 'round');
                path.setAttribute('stroke-linejoin', 'round');

                svg.appendChild(path);
            }
        },

        /**
         * Render Matches List View Dynamically
         */
        renderListView: function () {
            var listContainer = document.getElementById('singleListViewContainer');
            if (!listContainer) return;

            listContainer.innerHTML = '';

            var roundsToRender = (window.TourmaBracketAlgorithm) ?
                window.TourmaBracketAlgorithm.filterMatchesForListView(this.roundsList) : this.roundsList;

            for (var r = 0; r < roundsToRender.length; r++) {
                var roundObj = roundsToRender[r];

                var rHeader = document.createElement('div');
                rHeader.className = 'match-list-round-header';
                rHeader.innerText = roundObj.title;
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
         * Toggle View Mode between Bracket View (Tree Canvas) and List View (Matches List)
         */
        switchViewMode: function (mode) {
            if (this.tournamentId) {
                try {
                    localStorage.setItem('tourma_view_mode_' + this.tournamentId, mode);
                } catch (e) {}
            }

            var bracketContainer = document.getElementById('bracketViewportFrame') || document.getElementById('bracketViewportContainer');
            var listContainer = document.getElementById('singleListViewContainer');

            var btnBracket = document.getElementById('btnViewBracket');
            var btnList = document.getElementById('btnViewList');

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
         * Persist matches map to localStorage
         */
        persistMatches: function () {
            if (this.tournamentId) {
                var storageKeyMatches = 'tourma_matches_' + this.tournamentId;
                try {
                    localStorage.setItem(storageKeyMatches, JSON.stringify(this.matchesMap));
                } catch (e) {}
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
