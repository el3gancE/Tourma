/**
 * ============================================================================
 * TOURMA - ROUND ROBIN STANDINGS ENGINE (round-robin-standings.js)
 * Standalone Live Standings Table, Realtime Rank Recalculation,
 * Medals, Goal Differences, Points, and Recent Form Visualizer.
 * ============================================================================
 */

(function () {
    'use strict';

    window.TourmaRoundRobinStandings = {
        tournamentId: null,
        teamsList: [],
        matchesMap: {},
        config: {},

        /**
         * Initialize Standings Page
         */
        init: function (tourneyId, preloadedTeams, config) {
            this.tournamentId = tourneyId || 'demo';
            
            var storageKeyConfig = 'tourma_rr_config_' + this.tournamentId;
            var cfg = config;
            if (!cfg) {
                try {
                    cfg = JSON.parse(localStorage.getItem(storageKeyConfig));
                } catch (e) {
                    cfg = null;
                }
            }
            this.config = cfg || { winPoints: 3, drawPoints: 1, lossPoints: 0, legsCount: 1 };

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

            if (!teams || teams.length === 0) {
                teams = [
                    'Hà Nội FC', 'Công An Hà Nội', 'Hải Phòng FC', 'Đông Á Thanh Hóa',
                    'Sông Lam Nghệ An', 'SHB Đà Nẵng', 'Bình Định FC', 'Becamex Bình Dương'
                ];
            }
            this.teamsList = teams.slice(0, 24);

            // Update team count badge
            var countBadge = document.getElementById('tournamentTeamCountBadge');
            if (countBadge) {
                countBadge.innerText = this.teamsList.length + ' Đội';
            }

            // 2. Load Matches State
            this.loadMatchesState();

            // 3. Render Standings Table
            this.renderStandings();
        },

        /**
         * Load Matches Data from LocalStorage
         */
        loadMatchesState: function () {
            var storageKeyMatches = 'tourma_rr_matches_' + this.tournamentId;
            try {
                var saved = JSON.parse(localStorage.getItem(storageKeyMatches));
                if (saved) {
                    this.matchesMap = saved.matchesMap || {};
                    if (saved.teamsList && saved.teamsList.length > 0) {
                        this.teamsList = saved.teamsList;
                    }
                    if (saved.rounds && saved.rounds.length > 0) {
                        for (var r = 0; r < saved.rounds.length; r++) {
                            var rd = saved.rounds[r];
                            if (rd && rd.matches) {
                                for (var mi = 0; mi < rd.matches.length; mi++) {
                                    var rm = rd.matches[mi];
                                    var mid = rm.matchId || rm.id;
                                    if (!this.matchesMap[mid] || (rm.team1 && rm.team1.score !== '' && rm.team1.score != null)) {
                                        this.matchesMap[mid] = rm;
                                    }
                                }
                            }
                        }
                    }
                } else {
                    var universal = JSON.parse(localStorage.getItem('tourma_matches_' + this.tournamentId));
                    this.matchesMap = universal || {};
                }
            } catch (e) {
                this.matchesMap = {};
            }

            // If empty, generate fresh matches structure
            if (!this.matchesMap || Object.keys(this.matchesMap).length === 0) {
                if (window.TourmaRoundRobinAlgorithm) {
                    var gen = window.TourmaRoundRobinAlgorithm.generateRoundRobin(this.teamsList, this.config);
                    this.matchesMap = gen.matchesMap;
                }
            }
        },

        /**
         * Render Standings Table
         */
        renderStandings: function () {
            var tbody = document.getElementById('rrStandingsTableBody');
            if (!tbody || !window.TourmaRoundRobinAlgorithm) return;

            var standings = window.TourmaRoundRobinAlgorithm.calculateStandings(
                this.teamsList,
                this.matchesMap,
                this.config
            );

            tbody.innerHTML = '';

            for (var i = 0; i < standings.length; i++) {
                var row = standings[i];
                var tr = document.createElement('tr');

                // Recent Form Badges HTML
                var formHtml = '<div class="rr-form-group">';
                if (row.form && row.form.length > 0) {
                    for (var f = 0; f < row.form.length; f++) {
                        var res = row.form[f];
                        var fClass = (res === 'W') ? 'rr-form-w' : ((res === 'D') ? 'rr-form-d' : 'rr-form-l');
                        formHtml += '<span class="rr-form-badge ' + fClass + '">' + res + '</span>';
                    }
                } else {
                    formHtml += '<span style="color:#64748b; font-size:0.75rem;">-</span>';
                }
                formHtml += '</div>';

                var gdDisplay = (row.goalDifference > 0) ? ('+' + row.goalDifference) : String(row.goalDifference);

                tr.innerHTML =
                    '<td style="width: 50px;">' +
                        '<span class="rr-rank-badge">' + row.rank + '</span>' +
                    '</td>' +
                    '<td>' +
                        '<div class="rr-team-cell">' +
                            '<span>' + row.team + '</span>' +
                        '</div>' +
                    '</td>' +
                    '<td style="text-align: center;" class="rr-stat-cell">' + row.played + '</td>' +
                    '<td style="text-align: center;" class="rr-stat-green">' + row.won + '</td>' +
                    '<td style="text-align: center;" class="rr-stat-green">' + row.drawn + '</td>' +
                    '<td style="text-align: center;" class="rr-stat-green">' + row.lost + '</td>' +
                    '<td style="text-align: center;" class="rr-stat-cell">' + row.goalsFor + '</td>' +
                    '<td style="text-align: center;" class="rr-stat-cell">' + row.goalsAgainst + '</td>' +
                    '<td style="text-align: center;" class="rr-stat-cell">' + gdDisplay + '</td>' +
                    '<td style="text-align: center;" class="rr-points-cell">' + row.points + '</td>' +
                    '<td>' + formHtml + '</td>';

                tbody.appendChild(tr);
            }
        }
    };

})();
