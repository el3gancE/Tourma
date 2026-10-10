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
        init: function (tourneyId, preloadedTeams, config, stage, cutTarget, dbMatches) {
            this.tournamentId = tourneyId || 'demo';
            this.currentStage = (stage === 2 || stage === '2') ? 2 : 1;
            this.cutTarget = cutTarget || 0;

            if (!this.cutTarget || this.cutTarget <= 1) {
                var rawCut = localStorage.getItem('tourma_advance_count_' + this.tournamentId) ||
                             localStorage.getItem('tourma_cut_target_' + this.tournamentId);
                if (rawCut) this.cutTarget = parseInt(rawCut, 10);
            }
            
            var storageKeyConfig = 'tourma_rr_config_' + this.tournamentId;
            var cfg = config;
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

            // Handle Stage Finish Alert (Stage 2 access check)
            if (this.currentStage === 2) {
                if (window.StageFinishAlert && typeof window.StageFinishAlert.checkAndRender === 'function') {
                    if (window.StageFinishAlert.checkAndRender(this.tournamentId, this.currentStage, document.getElementById('stageFinishAlertContainer') || document.querySelector('.rr-standings-card'))) {
                        var card = document.querySelector('.rr-standings-card');
                        if (card) card.style.display = 'none';
                        return;
                    }
                }
            }

            if (!teams) {
                teams = [];
            }
            this.teamsList = teams.filter(function(t) {
                if (!t) return false;
                var n = (typeof t === 'object') ? (t.name || t.rawName || '') : String(t);
                return n !== 'BYE' && n.trim() !== '';
            }).slice(0, 24);

            // Update team count badge
            var countBadge = document.getElementById('tournamentTeamCountBadge');
            if (countBadge) {
                countBadge.innerText = this.teamsList.length + ' Đội';
            }

            // 2. Load Matches State
            this.loadMatchesState(dbMatches);

            // 3. Render Standings Table
            this.renderStandings();

            // 4. Check Final Stage conclusion & render top banner if complete
            this.checkFinalStage();
        },

        checkFinalStage: function () {
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
         * Load Matches Data from DB or LocalStorage
         */
        loadMatchesState: function (dbMatches) {
            this.matchesMap = {};

            if (dbMatches && Array.isArray(dbMatches) && dbMatches.length > 0) {
                for (var i = 0; i < dbMatches.length; i++) {
                    var rawM = dbMatches[i];
                    var mId = rawM.matchId || rawM.id || ('M_' + this.tournamentId + '_RR_' + (i + 1));
                    var t1 = rawM.team1 || {};
                    var t2 = rawM.team2 || {};
                    var matchObj = {
                        id: mId,
                        matchId: mId,
                        matchNumber: rawM.matchNumber || (i + 1),
                        roundNumber: rawM.roundNumber || 1,
                        status: (rawM.status === 'FINISHED' || rawM.status === 'COMPLETED') ? 'COMPLETED' : 'SCHEDULED',
                        team1: {
                            name: (typeof t1 === 'object') ? (t1.name || '') : String(t1),
                            seed: (typeof t1 === 'object') ? (t1.seed || '') : '',
                            score: (typeof t1 === 'object' && t1.score !== undefined && t1.score !== null) ? String(t1.score) : ''
                        },
                        team2: {
                            name: (typeof t2 === 'object') ? (t2.name || '') : String(t2),
                            seed: (typeof t2 === 'object') ? (t2.seed || '') : '',
                            score: (typeof t2 === 'object' && t2.score !== undefined && t2.score !== null) ? String(t2.score) : ''
                        },
                        winnerId: rawM.winnerId || null,
                        isBye: rawM.isBye === true
                    };
                    this.matchesMap[mId] = matchObj;
                }
                return;
            }

            var storageKeyRR = (this.currentStage === 2) ? ('tourma_rr_matches_stage2_' + this.tournamentId) : ('tourma_rr_matches_' + this.tournamentId);
            var storageKeyMatches = (this.currentStage === 2) ? ('tourma_matches_stage2_' + this.tournamentId) : ('tourma_matches_' + this.tournamentId);

            try {
                var saved = JSON.parse(localStorage.getItem(storageKeyRR));
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
                    var universal = JSON.parse(localStorage.getItem(storageKeyMatches));
                    this.matchesMap = universal || {};
                }
            } catch (e) {
                this.matchesMap = {};
            }

            // If empty, generate fresh matches structure
            if (!this.matchesMap || Object.keys(this.matchesMap).length === 0) {
                if (this.teamsList && this.teamsList.length > 0 && window.TourmaRoundRobinAlgorithm) {
                    var gen = window.TourmaRoundRobinAlgorithm.generateRoundRobin(this.teamsList, this.config);
                    this.matchesMap = gen.matchesMap || {};
                }
            }
        },

        renderEmptyState: function (containerElem) {
            if (!containerElem) return;
            var tr = document.createElement('tr');
            var td = document.createElement('td');
            td.colSpan = 10;
            td.style.textAlign = 'center';
            td.style.padding = '2rem 1rem';
            tr.appendChild(td);
            containerElem.appendChild(tr);

            if (window.TourmaEmptyTeamAlert && typeof window.TourmaEmptyTeamAlert.checkAndRender === 'function') {
                window.TourmaEmptyTeamAlert.checkAndRender(this.tournamentId, this.teamsList, td);
            }
        },

        /**
         * Render Standings Table
         */
        renderStandings: function () {
            var tbody = document.getElementById('rrStandingsTableBody');
            if (!tbody || !window.TourmaRoundRobinAlgorithm) return;

            tbody.innerHTML = '';

            if (!this.teamsList || this.teamsList.length < 2) {
                this.renderEmptyState(tbody);
                return;
            }

            var standings = window.TourmaRoundRobinAlgorithm.calculateStandings(
                this.teamsList,
                this.matchesMap,
                this.config
            );

            var isStage1WithCut = (this.currentStage === 1 && this.cutTarget && this.cutTarget > 1);
            var isFinalStage = !isStage1WithCut; // Single Stage RR or Stage 2 RR

            for (var i = 0; i < standings.length; i++) {
                var row = standings[i];
                var tr = document.createElement('tr');

                var isAdvancingRow = false;

                if (isStage1WithCut && row.rank <= this.cutTarget) {
                    isAdvancingRow = true;
                } else if (isFinalStage && row.rank === 1) {
                    isAdvancingRow = true;
                }

                if (isAdvancingRow) {
                    tr.className = 'rr-row-advancing-mint';
                }

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
            var tid = this.tournamentId || 'demo';

            // Clean localStorage keys
            try {
                localStorage.removeItem('tourma_rr_matches_' + tid);
                localStorage.removeItem('tourma_matches_' + tid);
                localStorage.removeItem('tourma_champion_' + tid);
                localStorage.removeItem('tourma_final_champion_' + tid);
                localStorage.removeItem('tourma_final_locked_' + tid);
            } catch (e) {}

            fetch((window.TourmaContextPath || '') + '/api/match-update', {
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
                    console.error('[TourmaRoundRobinStandings] Reset error:', err);
                    self.closeResetModal();
                    window.location.reload();
                });
        }
    };

})();
