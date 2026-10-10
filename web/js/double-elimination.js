/**
 * ============================================================================
 * TOURMA - DOUBLE ELIMINATION BRACKET & MATCH LIFECYCLE ENGINE (double-elimination.js)
 * 100% Database-Driven, Clean AJAX API, Zero localStorage Match Caching.
 * Manages Dual Viewports (Upper Bracket & Lower Bracket), Grand Finals,
 * Cross-Over Drop Downs, Quick Winner 1-Click Mode, Custom Score Inputs,
 * Batch Random / Reset, and Grand Finals Conclusion Popups.
 * ============================================================================
 */
(function (window) {
    'use strict';

    var TourmaDoubleElimination = {
        tournamentId: null,
        tournamentName: '',
        currentStage: 1,
        cutTarget: 0,
        tournamentType: 'SINGLE_STAGE',
        teamsList: [],
        matchesMap: {},
        upperRounds: [],
        lowerRounds: [],
        grandFinalsRound: null,
        bracketData: null,
        currentViewMode: 'BRACKET', // 'BRACKET' or 'LIST'
        isQuickMode: false,
        contextPath: '',

        /**
         * Initialize Double Elimination Page from Database Records
         */
        init: function (options) {
            options = options || {};
            this.tournamentId = options.tournamentId || window.TourmaTournamentId || 'demo';
            this.tournamentName = options.tournamentName || 'Giải Đấu Double Elimination';
            this.currentStage = options.stage ? parseInt(options.stage, 10) : 1;
            this.cutTarget = (options.cutTarget && parseInt(options.cutTarget, 10) > 1) ? parseInt(options.cutTarget, 10) : 0;
            this.tournamentType = options.tournamentType || 'SINGLE_STAGE';
            this.teamsList = Array.isArray(options.teamsList) ? options.teamsList : [];
            this.contextPath = window.TourmaContextPath || '';

            console.log('[DoubleEliminationEngine] Initializing with DB matches:', (options.dbMatches ? options.dbMatches.length : 0), 'Teams:', this.teamsList.length);

            // 1. Update Team Count Badge
            var teamBadge = document.getElementById('tournamentTeamCountBadge') || document.getElementById('deTeamCountBadge');
            if (teamBadge) {
                var actualCount = this.teamsList.length;
                if (actualCount === 0 && Array.isArray(options.dbMatches)) {
                    var set = {};
                    for (var t = 0; t < options.dbMatches.length; t++) {
                        var dm = options.dbMatches[t];
                        if (dm.team1 && dm.team1.name && !this.isPlaceholder(dm.team1.name) && dm.team1.name !== 'BYE') set[dm.team1.name] = true;
                        if (dm.team2 && dm.team2.name && !this.isPlaceholder(dm.team2.name) && dm.team2.name !== 'BYE') set[dm.team2.name] = true;
                    }
                    actualCount = Object.keys(set).length;
                }
                teamBadge.textContent = actualCount + ' Đội';
            }

            // 2. Update Advancing Badge if Cut Stage
            var advBadge = document.getElementById('tournamentAdvancingBadge') || document.getElementById('deAdvancingBadge');
            var advText = document.getElementById('deAdvancingText');
            if (advBadge && this.cutTarget > 1) {
                advBadge.style.display = 'inline-flex';
                if (advText) advText.textContent = this.cutTarget + ' Đội đi tiếp';
            } else if (advBadge) {
                advBadge.style.display = 'none';
            }

            // Handle Stage Finish Alert (Stage 2 access check)
            if (this.currentStage === 2) {
                if (window.StageFinishAlert && typeof window.StageFinishAlert.checkAndRender === 'function') {
                    if (window.StageFinishAlert.checkAndRender(this.tournamentId, this.currentStage, document.getElementById('stageFinishAlertContainer') || document.getElementById('deEmptyAlertContainer'))) {
                        var ws = document.getElementById('deDualViewportWorkspace');
                        if (ws) ws.style.display = 'none';
                        var lv = document.getElementById('deListViewContainer');
                        if (lv) lv.style.display = 'none';
                        return;
                    }
                }
            }

            // 3. Handle Empty Team Alert (< 2 teams)
            if (this.teamsList.length < 2 && (!options.dbMatches || options.dbMatches.length === 0)) {
                if (window.TourmaEmptyTeamAlert && typeof window.TourmaEmptyTeamAlert.checkAndRender === 'function') {
                    window.TourmaEmptyTeamAlert.checkAndRender(this.tournamentId, this.teamsList, document.getElementById('deEmptyAlertContainer') || document.getElementById('singleEmptyAlertContainer'));
                }
                var ws = document.getElementById('deDualViewportWorkspace');
                if (ws) ws.style.display = 'none';
                return;
            }

            // 4. Hydrate or Construct Bracket Model
            this.hydrateBracketModel(options.dbMatches);

            // 5. Setup Controls & Restore Preferences (View mode, Quick mode, Search)
            this.bindControls();

            // 6. Initialize Dual Viewports via TourmaViewport (applies standard 0.80 visual scale & smooth drag-to-pan)
            this.initViewports();

            // 7. Render UI
            this.render();

            // 8. Check Stage Completion / Final Champion Status
            this.checkTournamentCompletion();
        },

        /**
         * Initialize Viewports via TourmaViewport Engine
         */
        initViewports: function () {
            var self = this;
            if (window.TourmaViewport && typeof window.TourmaViewport.init === 'function') {
                window.TourmaViewport.init('upperViewportContainer', 'upperViewportCanvas', {
                    badgeId: 'upperZoomBadge',
                    toolbarId: 'upperZoomToolbar',
                    onRedraw: function () {
                        self.drawUpperSvgConnectors();
                    }
                });

                window.TourmaViewport.init('lowerViewportContainer', 'lowerViewportCanvas', {
                    badgeId: 'lowerZoomBadge',
                    toolbarId: 'lowerZoomToolbar',
                    onRedraw: function () {
                        self.drawLowerSvgConnectors();
                    }
                });
            }
        },

        /**
         * Construct in-memory bracket data structure from database match array
         */
        hydrateBracketModel: function (dbMatches) {
            this.matchesMap = {};
            this.upperRounds = [];
            this.lowerRounds = [];
            this.grandFinalsRound = null;

            if (Array.isArray(dbMatches) && dbMatches.length > 0) {
                var upperGroups = {};
                var lowerGroups = {};
                var gfMatches = [];
                var maxUbRound = 1;
                var maxLbRound = 1;

                for (var i = 0; i < dbMatches.length; i++) {
                    var m = dbMatches[i];
                    var mId = String(m.matchId || m.id || m.rawId || (i + 1));
                    var rNum = parseInt(m.roundNumber || 1, 10);
                    var bType = (m.bracketType || 'WINNER_BRACKET').toUpperCase();
                    var isBye = (m.isBye === true || m.isBye === 'true');
                    var matchNum = isBye ? null : ((m.matchNumber !== undefined && m.matchNumber !== null) ? m.matchNumber : null);

                    var matchObj = {
                        matchId: mId,
                        id: mId,
                        rawId: m.rawId || mId,
                        roundNumber: rNum,
                        matchNumber: matchNum,
                        bracketType: bType,
                        team1: m.team1 || { name: '', seed: '', score: '' },
                        team2: m.team2 || { name: '', seed: '', score: '' },
                        winnerId: m.winnerId || null,
                        nextMatchId: m.nextMatchId ? String(m.nextMatchId) : null,
                        nextMatchSlot: m.nextMatchSlot || 1,
                        loserNextMatchId: m.loserNextMatchId ? String(m.loserNextMatchId) : null,
                        loserNextSlot: m.loserNextSlot || 1,
                        dropToMatchId: (m.dropToMatchId || m.loserNextMatchId) ? String(m.dropToMatchId || m.loserNextMatchId) : null,
                        dropToMatchSlot: m.dropToMatchSlot || m.loserNextSlot || 1,
                        isBye: isBye,
                        isResetMatch: bType === 'GRAND_FINAL_RESET' || (m.isResetMatch === true),
                        isUnlocked: !(bType === 'GRAND_FINAL_RESET' && !m.winnerId && (!m.team1 || !m.team1.name)),
                        status: m.status || 'SCHEDULED'
                    };

                    this.matchesMap[mId] = matchObj;
                    if (matchObj.rawId) {
                        this.matchesMap[String(matchObj.rawId)] = matchObj;
                    }
                    if (matchObj.matchNumber !== null && matchObj.matchNumber !== undefined) {
                        this.matchesMap[String(matchObj.matchNumber)] = matchObj;
                    }

                    if (bType === 'GRAND_FINAL' || bType === 'GRAND_FINALS' || bType === 'GRAND_FINAL_RESET') {
                        gfMatches.push(matchObj);
                    } else if (bType === 'LOSER_BRACKET' || bType === 'LOWER') {
                        if (rNum > maxLbRound) maxLbRound = rNum;
                        if (!lowerGroups[rNum]) lowerGroups[rNum] = [];
                        lowerGroups[rNum].push(matchObj);
                    } else {
                        if (rNum > maxUbRound) maxUbRound = rNum;
                        if (!upperGroups[rNum]) upperGroups[rNum] = [];
                        upperGroups[rNum].push(matchObj);
                    }
                }

                var getMatchTreeOrder = function (m) {
                    var s = String(m.rawId || m.matchId || m.id || '');
                    var idx = s.lastIndexOf('_');
                    if (idx !== -1) {
                        var n = parseInt(s.substring(idx + 1), 10);
                        if (!isNaN(n)) return n;
                    }
                    var digits = s.replace(/[^0-9]/g, '');
                    return digits ? parseInt(digits, 10) : 0;
                };

                // Build ordered Upper Rounds
                for (var u = 1; u <= maxUbRound; u++) {
                    var uMatches = upperGroups[u] || [];
                    uMatches.sort(function (a, b) { return getMatchTreeOrder(a) - getMatchTreeOrder(b); });
                    var uTitle = (window.TourmaDoubleElimAlgorithm && typeof window.TourmaDoubleElimAlgorithm.getUpperRoundTitle === 'function')
                        ? window.TourmaDoubleElimAlgorithm.getUpperRoundTitle(u, maxUbRound)
                        : ('UB Vòng ' + u);

                    this.upperRounds.push({
                        roundNumber: u,
                        bracketType: 'UPPER',
                        title: uTitle,
                        matches: uMatches
                    });
                }

                // Build ordered Lower Rounds
                for (var l = 1; l <= maxLbRound; l++) {
                    var lMatches = lowerGroups[l] || [];
                    lMatches.sort(function (a, b) { return getMatchTreeOrder(a) - getMatchTreeOrder(b); });
                    var lTitle = (window.TourmaDoubleElimAlgorithm && typeof window.TourmaDoubleElimAlgorithm.getLowerRoundTitle === 'function')
                        ? window.TourmaDoubleElimAlgorithm.getLowerRoundTitle(l, maxLbRound)
                        : ('LB Vòng ' + l);

                    this.lowerRounds.push({
                        roundNumber: l,
                        bracketType: 'LOWER',
                        title: lTitle,
                        matches: lMatches
                    });
                }

                // Build Grand Finals Round
                if (gfMatches.length > 0) {
                    gfMatches.sort(function (a, b) { return getMatchTreeOrder(a) - getMatchTreeOrder(b); });
                    this.grandFinalsRound = {
                        roundNumber: 1,
                        bracketType: 'GRAND_FINAL',
                        title: 'Grand Finals',
                        matches: gfMatches
                    };
                }

                this.bracketData = {
                    upperRounds: this.upperRounds,
                    lowerRounds: this.lowerRounds,
                    grandFinalsRound: this.grandFinalsRound,
                    matchesMap: this.matchesMap
                };

                // Fill any missing placeholder labels without clearing database scores
                this.fillMissingPlaceholders();
            } else {
                // Fallback: Construct bracket algorithmically if DB was empty
                if (window.TourmaDoubleElimAlgorithm && this.teamsList.length >= 2) {
                    this.bracketData = window.TourmaDoubleElimAlgorithm.generateDoubleElimination(this.teamsList, this.cutTarget);
                    this.upperRounds = this.bracketData.upperRounds || [];
                    this.lowerRounds = this.bracketData.lowerRounds || [];
                    this.grandFinalsRound = this.bracketData.grandFinalsRound || null;
                    this.matchesMap = this.bracketData.matchesMap || {};
                }
            }
        },

        /**
         * Fill placeholder labels (W #1, L #2) on unplayed matches without touching DB scores
         */
        fillMissingPlaceholders: function () {
            for (var k in this.matchesMap) {
                var m = this.matchesMap[k];
                if (!m) continue;

                var bType = (m.bracketType || '').toUpperCase();
                var rNum = parseInt(m.roundNumber || 1, 10);
                var isUb = (bType === 'WINNER_BRACKET' || bType === 'UPPER' || bType === 'MAIN');
                var isLb = (bType === 'LOSER_BRACKET' || bType === 'LOWER' || bType === 'LB');
                var isGf = (bType === 'GRAND_FINAL' || bType === 'GRAND_FINALS' || bType === 'GRAND_FINAL_RESET');

                // Case 1: Upper Bracket Round 1
                if (isUb && rNum === 1) {
                    if (m.isBye) {
                        if (!m.team1 || !m.team1.name) m.team1 = { name: 'BYE', seed: '', score: '' };
                        if (!m.team2 || !m.team2.name) m.team2 = { name: 'BYE', seed: '', score: '' };
                    }
                    continue;
                }

                // Case 2: Upper Bracket Round 2+
                if (isUb && rNum > 1) {
                    if (!m.team1 || !m.team1.name || this.isPlaceholder(m.team1.name)) {
                        var p1 = this.findParentMatch(m.matchId, 1);
                        var w1 = this.getMatchWinner(p1);
                        if (w1 && w1.name && w1.name !== 'BYE' && !this.isPlaceholder(w1.name)) {
                            m.team1 = { name: w1.name, seed: w1.seed || '', score: (m.team1 && m.team1.score !== undefined) ? m.team1.score : '' };
                        } else {
                            var p1Label = (p1 && p1.matchNumber) ? ('W #' + p1.matchNumber) : (p1 && p1.isBye ? this.getNonByeTeamOrPlaceholder(p1) : 'TBD');
                            m.team1 = { name: p1Label, seed: '', score: '' };
                        }
                    }
                    if (!m.team2 || !m.team2.name || this.isPlaceholder(m.team2.name)) {
                        var p2 = this.findParentMatch(m.matchId, 2);
                        var w2 = this.getMatchWinner(p2);
                        if (w2 && w2.name && w2.name !== 'BYE' && !this.isPlaceholder(w2.name)) {
                            m.team2 = { name: w2.name, seed: w2.seed || '', score: (m.team2 && m.team2.score !== undefined) ? m.team2.score : '' };
                        } else {
                            var p2Label = (p2 && p2.matchNumber) ? ('W #' + p2.matchNumber) : (p2 && p2.isBye ? this.getNonByeTeamOrPlaceholder(p2) : 'TBD');
                            m.team2 = { name: p2Label, seed: '', score: '' };
                        }
                    }
                    continue;
                }

                // Case 3: Lower Bracket Round 1 (Fed ONLY by Upper Bracket drops, NEVER by parent winners!)
                if (isLb && rNum === 1) {
                    if (!m.team1 || !m.team1.name || this.isPlaceholder(m.team1.name)) {
                        var drop1 = this.findDropFeederMatch(m.matchId, 1);
                        if (!drop1 || drop1.isBye) {
                            m.team1 = { name: 'BYE', seed: '', score: '' };
                        } else {
                            var l1 = this.getMatchLoser(drop1);
                            if (l1 && l1.name && l1.name !== 'BYE' && !this.isPlaceholder(l1.name)) {
                                m.team1 = { name: l1.name, seed: l1.seed || '', score: (m.team1 && m.team1.score !== undefined) ? m.team1.score : '' };
                            } else {
                                var drop1Label = drop1.matchNumber ? ('L #' + drop1.matchNumber) : 'TBD';
                                m.team1 = { name: drop1Label, seed: '', score: '' };
                            }
                        }
                    }
                    if (!m.team2 || !m.team2.name || this.isPlaceholder(m.team2.name)) {
                        var drop2 = this.findDropFeederMatch(m.matchId, 2);
                        if (!drop2 || drop2.isBye) {
                            m.team2 = { name: 'BYE', seed: '', score: '' };
                        } else {
                            var l2 = this.getMatchLoser(drop2);
                            if (l2 && l2.name && l2.name !== 'BYE' && !this.isPlaceholder(l2.name)) {
                                m.team2 = { name: l2.name, seed: l2.seed || '', score: (m.team2 && m.team2.score !== undefined) ? m.team2.score : '' };
                            } else {
                                var drop2Label = drop2.matchNumber ? ('L #' + drop2.matchNumber) : 'TBD';
                                m.team2 = { name: drop2Label, seed: '', score: '' };
                            }
                        }
                    }
                    if (m.team1.name === 'BYE' && m.team2.name === 'BYE') {
                        m.isBye = true;
                        m.status = 'COMPLETED';
                    }
                    continue;
                }

                // Case 4: Lower Bracket Major Rounds (Even Rounds: 2, 4, 6...)
                // Slot 1 = Previous LB Winner; Slot 2 = UB Loser Drop
                if (isLb && (rNum % 2 === 0)) {
                    if (!m.team1 || !m.team1.name || this.isPlaceholder(m.team1.name)) {
                        var lbParent = this.findParentMatch(m.matchId, 1);
                        var lbW = this.getMatchWinner(lbParent);
                        if (lbW && lbW.name && lbW.name !== 'BYE' && !this.isPlaceholder(lbW.name)) {
                            m.team1 = { name: lbW.name, seed: lbW.seed || '', score: (m.team1 && m.team1.score !== undefined) ? m.team1.score : '' };
                        } else if (lbParent && lbParent.isBye) {
                            var fwd = this.getNonByeTeamOrPlaceholder(lbParent);
                            m.team1 = { name: fwd, seed: '', score: '' };
                        } else {
                            var lbPLabel = (lbParent && lbParent.matchNumber) ? ('W #' + lbParent.matchNumber) : 'TBD';
                            m.team1 = { name: lbPLabel, seed: '', score: '' };
                        }
                    }
                    if (!m.team2 || !m.team2.name || this.isPlaceholder(m.team2.name)) {
                        var ubDrop = this.findDropFeederMatch(m.matchId, 2);
                        if (!ubDrop || ubDrop.isBye) {
                            m.team2 = { name: 'BYE', seed: '', score: '' };
                        } else {
                            var ubL = this.getMatchLoser(ubDrop);
                            if (ubL && ubL.name && ubL.name !== 'BYE' && !this.isPlaceholder(ubL.name)) {
                                m.team2 = { name: ubL.name, seed: ubL.seed || '', score: (m.team2 && m.team2.score !== undefined) ? m.team2.score : '' };
                            } else {
                                var ubDropLabel = ubDrop.matchNumber ? ('L #' + ubDrop.matchNumber) : 'TBD';
                                m.team2 = { name: ubDropLabel, seed: '', score: '' };
                            }
                        }
                    }
                    continue;
                }

                // Case 5: Lower Bracket Minor Rounds (Odd Rounds: 3, 5, 7...)
                // Both Slot 1 and Slot 2 come from previous LB winners
                if (isLb && (rNum % 2 !== 0) && rNum > 1) {
                    if (!m.team1 || !m.team1.name || this.isPlaceholder(m.team1.name)) {
                        var lbP1 = this.findParentMatch(m.matchId, 1);
                        var lbW1 = this.getMatchWinner(lbP1);
                        if (lbW1 && lbW1.name && lbW1.name !== 'BYE' && !this.isPlaceholder(lbW1.name)) {
                            m.team1 = { name: lbW1.name, seed: lbW1.seed || '', score: (m.team1 && m.team1.score !== undefined) ? m.team1.score : '' };
                        } else if (lbP1 && lbP1.isBye) {
                            var fwd1 = this.getNonByeTeamOrPlaceholder(lbP1);
                            m.team1 = { name: fwd1, seed: '', score: '' };
                        } else {
                            var lbP1Label = (lbP1 && lbP1.matchNumber) ? ('W #' + lbP1.matchNumber) : 'TBD';
                            m.team1 = { name: lbP1Label, seed: '', score: '' };
                        }
                    }
                    if (!m.team2 || !m.team2.name || this.isPlaceholder(m.team2.name)) {
                        var lbP2 = this.findParentMatch(m.matchId, 2);
                        var lbW2 = this.getMatchWinner(lbP2);
                        if (lbW2 && lbW2.name && lbW2.name !== 'BYE' && !this.isPlaceholder(lbW2.name)) {
                            m.team2 = { name: lbW2.name, seed: lbW2.seed || '', score: (m.team2 && m.team2.score !== undefined) ? m.team2.score : '' };
                        } else if (lbP2 && lbP2.isBye) {
                            var fwd2 = this.getNonByeTeamOrPlaceholder(lbP2);
                            m.team2 = { name: fwd2, seed: '', score: '' };
                        } else {
                            var lbP2Label = (lbP2 && lbP2.matchNumber) ? ('W #' + lbP2.matchNumber) : 'TBD';
                            m.team2 = { name: lbP2Label, seed: '', score: '' };
                        }
                    }
                    continue;
                }

                // Case 6: Grand Finals
                if (isGf) {
                    if (!m.team1 || !m.team1.name || this.isPlaceholder(m.team1.name)) {
                        var gfUb = this.findParentMatch(m.matchId, 1);
                        var gfUbW = this.getMatchWinner(gfUb);
                        if (gfUbW && gfUbW.name && gfUbW.name !== 'BYE' && !this.isPlaceholder(gfUbW.name)) {
                            m.team1 = { name: gfUbW.name, seed: gfUbW.seed || '', score: (m.team1 && m.team1.score !== undefined) ? m.team1.score : '' };
                        } else {
                            m.team1 = { name: 'Winner UB', seed: '', score: '' };
                        }
                    }
                    if (!m.team2 || !m.team2.name || this.isPlaceholder(m.team2.name)) {
                        var gfLb = this.findParentMatch(m.matchId, 2);
                        var gfLbW = this.getMatchWinner(gfLb);
                        if (gfLbW && gfLbW.name && gfLbW.name !== 'BYE' && !this.isPlaceholder(gfLbW.name)) {
                            m.team2 = { name: gfLbW.name, seed: gfLbW.seed || '', score: (m.team2 && m.team2.score !== undefined) ? m.team2.score : '' };
                        } else {
                            m.team2 = { name: 'Winner LB', seed: '', score: '' };
                        }
                    }
                }
            }
        },

        getNonByeTeamOrPlaceholder: function (m) {
            if (!m) return 'TBD';
            var t1 = m.team1 ? m.team1.name : '';
            var t2 = m.team2 ? m.team2.name : '';
            if (t1 && t1 !== 'BYE') return t1;
            if (t2 && t2 !== 'BYE') return t2;
            return 'BYE';
        },

        getMatchWinner: function (m) {
            if (!m) return null;
            if (m.isBye) {
                var t1N = (m.team1 && m.team1.name) ? m.team1.name : '';
                var t2N = (m.team2 && m.team2.name) ? m.team2.name : '';
                if (t1N && t1N !== 'BYE' && !this.isPlaceholder(t1N)) return m.team1;
                if (t2N && t2N !== 'BYE' && !this.isPlaceholder(t2N)) return m.team2;
            }
            if (m.winnerId === 'team1' || m.winnerId === 1 || m.winnerId === '1' || m.winnerId === 'SLOT_1') return m.team1;
            if (m.winnerId === 'team2' || m.winnerId === 2 || m.winnerId === '2' || m.winnerId === 'SLOT_2') return m.team2;
            if (m.team1 && (m.winnerId === String(m.team1.id) || m.winnerId === String(m.team1.name))) return m.team1;
            if (m.team2 && (m.winnerId === String(m.team2.id) || m.winnerId === String(m.team2.name))) return m.team2;
            var s1 = parseInt(m.team1 ? m.team1.score : '', 10);
            var s2 = parseInt(m.team2 ? m.team2.score : '', 10);
            if (!isNaN(s1) && !isNaN(s2) && (m.status === 'COMPLETED' || m.status === 'FINISHED' || m.status === 'DONE')) {
                if (s1 > s2) return m.team1;
                if (s2 > s1) return m.team2;
            }
            return null;
        },

        getMatchLoser: function (m) {
            if (!m) return null;
            if (m.isBye) return null;
            if (m.winnerId === 'team1' || m.winnerId === 1 || m.winnerId === '1' || m.winnerId === 'SLOT_1') return m.team2;
            if (m.winnerId === 'team2' || m.winnerId === 2 || m.winnerId === '2' || m.winnerId === 'SLOT_2') return m.team1;
            if (m.team1 && (m.winnerId === String(m.team1.id) || m.winnerId === String(m.team1.name))) return m.team2;
            if (m.team2 && (m.winnerId === String(m.team2.id) || m.winnerId === String(m.team2.name))) return m.team1;
            var s1 = parseInt(m.team1 ? m.team1.score : '', 10);
            var s2 = parseInt(m.team2 ? m.team2.score : '', 10);
            if (!isNaN(s1) && !isNaN(s2) && (m.status === 'COMPLETED' || m.status === 'FINISHED' || m.status === 'DONE')) {
                if (s1 > s2) return m.team2;
                if (s2 > s1) return m.team1;
            }
            return null;
        },

        findMatch: function (targetId) {
            if (!targetId) return null;
            var strId = String(targetId).trim();
            if (this.matchesMap && this.matchesMap[strId]) return this.matchesMap[strId];

            if (window.TourmaDoubleElimAlgorithm && typeof window.TourmaDoubleElimAlgorithm.findMatchInMap === 'function') {
                var found = window.TourmaDoubleElimAlgorithm.findMatchInMap(this.matchesMap, strId);
                if (found) return found;
            }

            for (var k in this.matchesMap) {
                var m = this.matchesMap[k];
                if (!m) continue;
                if (m.matchId && String(m.matchId) === strId) return m;
                if (m.id && String(m.id) === strId) return m;
                if (m.rawId && String(m.rawId) === strId) return m;
                if (m.matchNumber && String(m.matchNumber) === strId) return m;
            }
            return null;
        },

        findParentMatch: function (targetMatchId, slot) {
            if (!targetMatchId) return null;
            var targetM = this.findMatch(targetMatchId);
            var targetIds = [];
            if (targetM) {
                if (targetM.id) targetIds.push(String(targetM.id).trim());
                if (targetM.matchId) targetIds.push(String(targetM.matchId).trim());
                if (targetM.rawId) targetIds.push(String(targetM.rawId).trim());
            } else {
                targetIds.push(String(targetMatchId).trim());
            }

            for (var k in this.matchesMap) {
                var m = this.matchesMap[k];
                if (!m) continue;
                // Never match self!
                if (targetM && (m === targetM || m.id === targetM.id || m.matchId === targetM.matchId || m.rawId === targetM.rawId)) {
                    continue;
                }
                if (m.nextMatchId) {
                    var nId = String(m.nextMatchId).trim();
                    var isMatch = targetIds.indexOf(nId) !== -1;
                    if (isMatch) {
                        var s = (m.nextMatchSlot === 2 || m.nextMatchSlot === '2' || m.nextMatchSlot === 'SLOT_2') ? 2 : 1;
                        if (slot === undefined || s === slot) return m;
                    }
                }
            }
            return null;
        },

        findDropFeederMatch: function (targetMatchId, slot) {
            if (!targetMatchId) return null;
            var targetM = this.findMatch(targetMatchId);
            var targetIds = [];
            if (targetM) {
                if (targetM.id) targetIds.push(String(targetM.id).trim());
                if (targetM.matchId) targetIds.push(String(targetM.matchId).trim());
                if (targetM.rawId) targetIds.push(String(targetM.rawId).trim());
            } else {
                targetIds.push(String(targetMatchId).trim());
            }

            for (var k in this.matchesMap) {
                var m = this.matchesMap[k];
                if (!m) continue;
                // Never match self!
                if (targetM && (m === targetM || m.id === targetM.id || m.matchId === targetM.matchId || m.rawId === targetM.rawId)) {
                    continue;
                }
                var dId = m.dropToMatchId || m.loserNextMatchId;
                if (dId) {
                    var dropStr = String(dId).trim();
                    var isMatch = targetIds.indexOf(dropStr) !== -1;
                    if (isMatch) {
                        var s = (m.dropToMatchSlot === 2 || m.dropToMatchSlot === '2' || m.dropToMatchSlot === 'SLOT_2' || m.loserNextSlot === 2 || m.loserNextSlot === '2' || m.loserNextSlot === 'SLOT_2') ? 2 : 1;
                        if (slot === undefined || s === slot) return m;
                    }
                }
            }
            return null;
        },

        /**
         * Setup Controls & Restore User Preferences (View Mode, Quick Mode, Search)
         */
        bindControls: function () {
            var self = this;

            // 1. Restore View Mode from localStorage
            var savedView = null;
            try {
                savedView = localStorage.getItem('tourma_view_mode_' + this.tournamentId) ||
                            localStorage.getItem('tourma_view_mode_' + (window.TourmaTournamentId || 'demo')) ||
                            localStorage.getItem('tourma_de_view_mode_' + this.tournamentId) ||
                            localStorage.getItem('tourma_de_view_mode_' + (window.TourmaTournamentId || 'demo'));
            } catch (e) { }
            if (savedView) {
                this.currentViewMode = (savedView.toUpperCase() === 'LIST') ? 'LIST' : 'BRACKET';
            }

            // 2. Restore Quick Mode from localStorage
            var savedQuick = null;
            try {
                savedQuick = localStorage.getItem('tourma_quick_mode_' + this.tournamentId) || localStorage.getItem('tourma_de_quick_mode_' + this.tournamentId);
            } catch (e) { }
            if (savedQuick === 'true') {
                this.isQuickMode = true;
                window.TourmaQuickMode = true;
                var btns = document.querySelectorAll('#singleBtnQuickMode, #deBtnQuickMode');
                btns.forEach(function (btn) {
                    btn.classList.add('active');
                    var st = btn.querySelector('.quick-mode-status-text');
                    if (st) st.textContent = 'ON';
                });
            } else {
                this.isQuickMode = false;
                window.TourmaQuickMode = false;
                var btns = document.querySelectorAll('#singleBtnQuickMode, #deBtnQuickMode');
                btns.forEach(function (btn) {
                    btn.classList.remove('active');
                    var st = btn.querySelector('.quick-mode-status-text');
                    if (st) st.textContent = 'OFF';
                });
            }

            // 3. Search Box Filtering
            var searchInput = document.getElementById('bracketSearchInput');
            if (searchInput) {
                searchInput.addEventListener('input', function () {
                    var query = (this.value || '').trim().toLowerCase();
                    self.filterMatches(query);
                });
            }

            // 4. Redraw SVG on window resize
            window.addEventListener('resize', function () {
                if (self.currentViewMode === 'BRACKET') {
                    self.drawUpperSvgConnectors();
                    self.drawLowerSvgConnectors();
                }
            });
        },

        /**
         * Toggle Quick Mode (1-Click Winner)
         */
        toggleQuickMode: function () {
            this.isQuickMode = !this.isQuickMode;
            window.TourmaQuickMode = this.isQuickMode;
            try {
                localStorage.setItem('tourma_quick_mode_' + this.tournamentId, this.isQuickMode ? 'true' : 'false');
                localStorage.setItem('tourma_de_quick_mode_' + this.tournamentId, this.isQuickMode ? 'true' : 'false');
            } catch (e) { }

            var btns = document.querySelectorAll('#singleBtnQuickMode, #deBtnQuickMode');
            var isQ = this.isQuickMode;
            btns.forEach(function (btn) {
                var statusText = btn.querySelector('.quick-mode-status-text');
                if (isQ) {
                    btn.classList.add('active');
                    if (statusText) statusText.textContent = 'ON';
                } else {
                    btn.classList.remove('active');
                    if (statusText) statusText.textContent = 'OFF';
                }
            });
        },

        /**
         * Switch between Bracket View and List View
         */
        switchViewMode: function (mode) {
            this.setViewMode(mode);
        },

        setViewMode: function (mode) {
            var normMode = (mode || 'bracket').toLowerCase();
            this.currentViewMode = (normMode === 'list') ? 'LIST' : 'BRACKET';
            try {
                localStorage.setItem('tourma_view_mode_' + this.tournamentId, normMode);
                localStorage.setItem('tourma_de_view_mode_' + this.tournamentId, normMode);
            } catch (e) { }

            var btnBracketViews = document.querySelectorAll('#btnViewBracket, #deBtnBracketView, .btn-view-toggle:first-child');
            var btnListViews = document.querySelectorAll('#btnViewList, #deBtnListView, .btn-view-toggle:last-child');
            var dualWorkspace = document.getElementById('deDualViewportWorkspace');
            var listContainer = document.getElementById('deListViewContainer');

            if (this.currentViewMode === 'LIST') {
                btnListViews.forEach(function (b) { b.classList.add('active'); });
                btnBracketViews.forEach(function (b) { b.classList.remove('active'); });
                if (dualWorkspace) dualWorkspace.style.display = 'none';
                if (listContainer) listContainer.style.display = 'flex';
                this.renderListView();
            } else {
                btnBracketViews.forEach(function (b) { b.classList.add('active'); });
                btnListViews.forEach(function (b) { b.classList.remove('active'); });
                if (dualWorkspace) dualWorkspace.style.display = 'flex';
                if (listContainer) listContainer.style.display = 'none';
                this.renderUpperBracket();
                this.renderLowerBracket();
            }

            this.updateRoundRandomButtons();
        },

        /**
         * Render both Bracket and List Views based on current state
         */
        render: function () {
            this.renderUpperBracket();
            this.renderLowerBracket();
            this.renderListView();

            // Apply active view mode visibility
            var btnBracketViews = document.querySelectorAll('#btnViewBracket, #deBtnBracketView, .btn-view-toggle:first-child');
            var btnListViews = document.querySelectorAll('#btnViewList, #deBtnListView, .btn-view-toggle:last-child');
            var dualWorkspace = document.getElementById('deDualViewportWorkspace');
            var listContainer = document.getElementById('deListViewContainer');

            if (this.currentViewMode === 'LIST') {
                btnListViews.forEach(function (b) { b.classList.add('active'); });
                btnBracketViews.forEach(function (b) { b.classList.remove('active'); });
                if (dualWorkspace) dualWorkspace.style.display = 'none';
                if (listContainer) listContainer.style.display = 'flex';
            } else {
                btnBracketViews.forEach(function (b) { b.classList.add('active'); });
                btnListViews.forEach(function (b) { b.classList.remove('active'); });
                if (dualWorkspace) dualWorkspace.style.display = 'flex';
                if (listContainer) listContainer.style.display = 'none';
            }

            this.updateRoundRandomButtons();
            this.checkTournamentCompletion();
        },

        /**
         * Render Upper Bracket View (Columns of Node Cards & Grand Finals)
         */
        renderUpperBracket: function () {
            var self = this;
            var container = document.getElementById('upperBracketColumnsWrapper');
            if (!container) return;
            container.innerHTML = '';

            var totalRounds = this.upperRounds.length;
            if (totalRounds === 0) {
                container.innerHTML = '<div style="padding: 30px; text-align: center; color: #94a3b8;">Chưa có dữ liệu Upper Bracket.</div>';
                return;
            }

            for (var r = 0; r < totalRounds; r++) {
                var roundObj = this.upperRounds[r];
                var col = document.createElement('div');
                col.className = 'de-round-column single-round-column';
                col.setAttribute('data-round', roundObj.roundNumber);
                col.setAttribute('data-bracket-type', 'UPPER');

                // Header with Round Title & Random Control
                var header = document.createElement('div');
                header.className = 'de-round-header single-round-header upper';
                var rName = roundObj.title || ('UB Vòng ' + roundObj.roundNumber);
                var ctrlHtml = (window.TourmaRoundControls && typeof window.TourmaRoundControls.renderHeaderControlsHtml === 'function')
                    ? window.TourmaRoundControls.renderHeaderControlsHtml(roundObj.roundNumber, rName, 'UPPER')
                    : '';

                header.innerHTML = '<div class="round-header-title">' + rName + '</div>' + ctrlHtml;
                col.appendChild(header);

                // Matches Stack
                var stack = document.createElement('div');
                stack.className = 'de-round-matches-stack single-round-matches-box';

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
                            stack.appendChild(cardNode);
                        }
                    }
                }

                col.appendChild(stack);
                container.appendChild(col);
            }

            // Render Grand Finals Column in Upper Bracket if present
            if (this.grandFinalsRound && this.grandFinalsRound.matches && this.grandFinalsRound.matches.length > 0 && !(this.cutTarget > 1)) {
                var gfCol = document.createElement('div');
                gfCol.className = 'de-round-column single-round-column grand-finals';
                gfCol.setAttribute('data-round', '1');
                gfCol.setAttribute('data-bracket-type', 'GRAND_FINAL');

                var gfHeader = document.createElement('div');
                gfHeader.className = 'de-round-header single-round-header gf';
                var gfCtrlHtml = (window.TourmaRoundControls && typeof window.TourmaRoundControls.renderHeaderControlsHtml === 'function')
                    ? window.TourmaRoundControls.renderHeaderControlsHtml(1, 'Grand Finals', 'GRAND_FINAL')
                    : '';
                gfHeader.innerHTML = '<div class="round-header-title">Grand Finals</div>' + gfCtrlHtml;
                gfCol.appendChild(gfHeader);

                var gfStack = document.createElement('div');
                gfStack.className = 'de-round-matches-stack single-round-matches-box';

                for (var g = 0; g < this.grandFinalsRound.matches.length; g++) {
                    var gfMatch = this.grandFinalsRound.matches[g];
                    var liveGf = this.matchesMap[gfMatch.matchId] || gfMatch;

                    var gfCard = null;
                    if (window.TourmaBracketCard && typeof window.TourmaBracketCard.createNodeElement === 'function') {
                        gfCard = window.TourmaBracketCard.createNodeElement(liveGf);
                    } else {
                        gfCard = this.createFallbackCard(liveGf);
                    }

                    if (gfCard) {
                        this.attachCardClickListener(gfCard, liveGf);
                        gfStack.appendChild(gfCard);
                    }
                }

                gfCol.appendChild(gfStack);
                container.appendChild(gfCol);
            }

            // Bind click events on Random Round buttons via TourmaRoundControls
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.bindEvents === 'function') {
                window.TourmaRoundControls.bindEvents(container, this);
            }

            // Draw SVG Connectors with multiple frames to ensure DOM layout settles
            requestAnimationFrame(function () {
                self.drawUpperSvgConnectors();
            });
            setTimeout(function () {
                self.drawUpperSvgConnectors();
            }, 60);
            setTimeout(function () {
                self.drawUpperSvgConnectors();
            }, 250);
        },

        /**
         * Render Lower Bracket View (Columns of Node Cards)
         */
        renderLowerBracket: function () {
            var self = this;
            var container = document.getElementById('lowerBracketColumnsWrapper');
            if (!container) return;
            container.innerHTML = '';

            var totalRounds = this.lowerRounds.length;
            if (totalRounds === 0) {
                container.innerHTML = '<div style="padding: 30px; text-align: center; color: #94a3b8;">Chưa có dữ liệu Lower Bracket.</div>';
                return;
            }

            for (var r = 0; r < totalRounds; r++) {
                var roundObj = this.lowerRounds[r];
                var col = document.createElement('div');
                col.className = 'de-round-column single-round-column';
                col.setAttribute('data-round', roundObj.roundNumber);
                col.setAttribute('data-bracket-type', 'LOWER');

                // Header with Round Title & Random Control
                var header = document.createElement('div');
                header.className = 'de-round-header single-round-header lower';
                var rName = roundObj.title || ('LB Vòng ' + roundObj.roundNumber);
                var ctrlHtml = (window.TourmaRoundControls && typeof window.TourmaRoundControls.renderHeaderControlsHtml === 'function')
                    ? window.TourmaRoundControls.renderHeaderControlsHtml(roundObj.roundNumber, rName, 'LOWER')
                    : '';

                header.innerHTML = '<div class="round-header-title">' + rName + '</div>' + ctrlHtml;
                col.appendChild(header);

                // Matches Stack
                var stack = document.createElement('div');
                stack.className = 'de-round-matches-stack single-round-matches-box';

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
                            stack.appendChild(cardNode);
                        }
                    }
                }

                col.appendChild(stack);
                container.appendChild(col);
            }

            // Bind click events on Random Round buttons via TourmaRoundControls
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.bindEvents === 'function') {
                window.TourmaRoundControls.bindEvents(container, this);
            }

            // Draw SVG Connectors with multiple frames to ensure DOM layout settles
            requestAnimationFrame(function () {
                self.drawLowerSvgConnectors();
            });
            setTimeout(function () {
                self.drawLowerSvgConnectors();
            }, 60);
            setTimeout(function () {
                self.drawLowerSvgConnectors();
            }, 250);
        },

        /**
         * Render Matches List View (Play-Order Sequential Layout)
         */
        renderListView: function () {
            var container = document.getElementById('deListViewContainer');
            if (!container) return;
            container.innerHTML = '';

            var listSections = [];
            if (window.TourmaDoubleElimAlgorithm && typeof window.TourmaDoubleElimAlgorithm.filterMatchesForListView === 'function') {
                listSections = window.TourmaDoubleElimAlgorithm.filterMatchesForListView(this.bracketData);
            }

            if (listSections.length === 0) {
                container.innerHTML = '<div style="padding: 40px; text-align: center; color: #94a3b8;">Chưa có trận đấu nào.</div>';
                return;
            }

            for (var s = 0; s < listSections.length; s++) {
                var sec = listSections[s];
                var roundSec = document.createElement('div');
                roundSec.className = 'list-round-section';
                roundSec.setAttribute('data-round', sec.roundNumber);
                roundSec.setAttribute('data-bracket-type', sec.bracketType);

                // Section Header
                var secHeader = document.createElement('div');
                var bTypeClass = (sec.bracketType === 'UPPER' ? ' upper' : (sec.bracketType === 'LOWER' ? ' lower' : ' gf'));
                secHeader.className = 'de-list-section-header' + bTypeClass;
                var secTitle = sec.title || ('Vòng ' + sec.roundNumber);

                var listCtrlHtml = (window.TourmaRoundControls && typeof window.TourmaRoundControls.renderListHeaderControlsHtml === 'function')
                    ? window.TourmaRoundControls.renderListHeaderControlsHtml(sec.roundNumber, secTitle, sec.bracketType)
                    : '';

                secHeader.innerHTML = '<div class="list-round-title-group">'
                    + '<h3 class="round-header-title">' + secTitle + '</h3>'
                    + '<span class="list-round-badge">' + (sec.matches ? sec.matches.length : 0) + ' trận</span>'
                    + '</div>'
                    + listCtrlHtml;
                roundSec.appendChild(secHeader);

                // Matches Grid
                var grid = document.createElement('div');
                grid.className = 'list-matches-grid';

                if (sec.matches) {
                    for (var m = 0; m < sec.matches.length; m++) {
                        var matchData = sec.matches[m];
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
            }
        },

        /**
         * Attach Click Event on Node/List Cards to open Score Entry Modal
         */
        attachCardClickListener: function (cardElement, matchData) {
            var self = this;
            if (!cardElement || !matchData) return;

            cardElement.addEventListener('click', function (e) {
                // If Quick Mode is active, NEVER open modal on card click!
                if (window.TourmaQuickMode || self.isQuickMode) return;

                if (e.target.closest('.bracket-team-row, .match-team-side, .btn-round-random, .btn-round-reset, input, button')) {
                    if (window.TourmaQuickMode || self.isQuickMode) return;
                }

                if (matchData.isBye || matchData.status === 'BYE') return;

                var isPlayable = (matchData.team1 && matchData.team1.name && !self.isPlaceholder(matchData.team1.name) &&
                    matchData.team2 && matchData.team2.name && !self.isPlaceholder(matchData.team2.name));
                if (!isPlayable) return;

                var modal = window.TourmaScoreModal || window.TourmaPopup;
                if (modal && typeof modal.open === 'function') {
                    modal.open({
                        matchId: matchData.matchId || matchData.id,
                        tournamentId: self.tournamentId,
                        roundName: matchData.roundName || matchData.title || ('Trận #' + (matchData.matchNumber || matchData.matchId)),
                        team1Name: matchData.team1 ? matchData.team1.name : '',
                        team1Seed: matchData.team1 ? matchData.team1.seed : '',
                        team1Score: (matchData.team1 && matchData.team1.score !== undefined && matchData.team1.score !== null) ? matchData.team1.score : '',
                        team2Name: matchData.team2 ? matchData.team2.name : '',
                        team2Seed: matchData.team2 ? matchData.team2.seed : '',
                        team2Score: (matchData.team2 && matchData.team2.score !== undefined && matchData.team2.score !== null) ? matchData.team2.score : '',
                        winnerId: matchData.winnerId,
                        status: matchData.status,
                        allowDraw: false
                    }, function (resultData) {
                        var s1 = (resultData.team1Score !== undefined) ? resultData.team1Score : resultData.score1;
                        var s2 = (resultData.team2Score !== undefined) ? resultData.team2Score : resultData.score2;
                        self.saveMatchScore(
                            matchData.matchId,
                            s1,
                            s2,
                            resultData.penalty1,
                            resultData.penalty2,
                            resultData.winner
                        );
                    });
                }
            });
        },

        /**
         * Quick Winner (1-Click Winner) Handler
         */
        handleQuickWinner: function (matchId, winnerSlotNum, customScore) {
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.handleQuickWinner === 'function') {
                window.TourmaRoundControls.handleQuickWinner(this, matchId, winnerSlotNum, customScore);
                return;
            }

            var m = this.findMatch(matchId);
            if (!m) return;
            var t1Name = (m.team1 && m.team1.name) ? m.team1.name : '';
            var t2Name = (m.team2 && m.team2.name) ? m.team2.name : '';
            if (this.isPlaceholder(t1Name) || this.isPlaceholder(t2Name) || m.isBye) return;

            var scoreRes = (window.TourmaRoundControls && typeof window.TourmaRoundControls.generateQuickWinnerScore === 'function')
                ? window.TourmaRoundControls.generateQuickWinnerScore(winnerSlotNum, customScore)
                : {
                    score1: (winnerSlotNum === 1 || winnerSlotNum === '1' || winnerSlotNum === 'team1') ? (customScore || 2) : 0,
                    score2: (winnerSlotNum === 1 || winnerSlotNum === '1' || winnerSlotNum === 'team1') ? 0 : (customScore || 2),
                    winner: (winnerSlotNum === 1 || winnerSlotNum === '1' || winnerSlotNum === 'team1') ? 'team1' : 'team2'
                };

            this.saveMatchScore(m.matchId || matchId, scoreRes.score1, scoreRes.score2, null, null, scoreRes.winner);
        },

        /**
         * Save Match Score via AJAX to match-update servlet
         */
        saveMatchScore: function (matchId, score1, score2, penalty1, penalty2, winnerSlot) {
            var self = this;
            var m = this.findMatch(matchId);
            if (!m) return;

            var s1 = parseInt(score1, 10);
            var s2 = parseInt(score2, 10);
            if (isNaN(s1)) s1 = 0;
            if (isNaN(s2)) s2 = 0;

            var winnerId = null;
            if (winnerSlot === 'team1' || winnerSlot === 1 || winnerSlot === '1') {
                winnerId = 'team1';
            } else if (winnerSlot === 'team2' || winnerSlot === 2 || winnerSlot === '2') {
                winnerId = 'team2';
            } else if (s1 > s2) {
                winnerId = 'team1';
            } else if (s2 > s1) {
                winnerId = 'team2';
            }

            var t1Name = (m.team1 && m.team1.name) ? m.team1.name : '';
            var t2Name = (m.team2 && m.team2.name) ? m.team2.name : '';

            var payload = {
                action: 'updateMatch',
                tournamentId: this.tournamentId,
                stage: this.currentStage,
                matchId: m.rawId || m.matchId || m.id,
                score1: s1,
                score2: s2,
                penalty1: (penalty1 !== undefined && penalty1 !== null) ? penalty1 : '',
                penalty2: (penalty2 !== undefined && penalty2 !== null) ? penalty2 : '',
                winner: winnerId || '',
                team1Name: t1Name,
                team2Name: t2Name
            };

            fetch((this.contextPath || '') + '/api/match-update', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                body: new URLSearchParams(payload).toString()
            })
                .then(function (res) { return res.json(); })
                .then(function (data) {
                    if (data && data.status === 'success') {
                        // Update local match state
                        m.team1 = m.team1 || { name: t1Name };
                        m.team2 = m.team2 || { name: t2Name };
                        m.team1.score = s1;
                        m.team2.score = s2;
                        m.winnerId = winnerId;
                        m.status = 'COMPLETED';

                        // Propagate across UB, LB, and Grand Finals
                        var isT1 = (winnerId === 'team1');
                        if (window.TourmaDoubleElimAlgorithm && typeof window.TourmaDoubleElimAlgorithm.propagateMatchResult === 'function') {
                            window.TourmaDoubleElimAlgorithm.propagateMatchResult(self.matchesMap, m.matchId || matchId, winnerId, isT1);
                        }

                        // Re-render UI
                        self.render();
                        self.checkTournamentCompletion();
                    } else {
                        console.error('[DoubleEliminationEngine] Error response:', data);
                        alert('Lỗi lưu kết quả: ' + (data.message || 'Không rõ nguyên nhân'));
                    }
                })
                .catch(function (err) {
                    console.error('[DoubleEliminationEngine] Error saving match score:', err);
                });
        },

        /**
         * Randomize single round via TourmaRoundControls
         */
        executeRandomRound: function (roundNumber, bracketType) {
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.executeRandomRound === 'function') {
                window.TourmaRoundControls.executeRandomRound(this, roundNumber, bracketType);
            }
        },

        /**
         * Reset single round via TourmaRoundControls
         */
        executeResetRound: function (roundNumber, bracketType) {
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.executeResetRound === 'function') {
                window.TourmaRoundControls.executeResetRound(this, roundNumber, bracketType);
            }
        },

        /**
         * Randomize all playable matches in the bracket
         */
        executeRandomAll: function () {
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.executeRandomAll === 'function') {
                window.TourmaRoundControls.executeRandomAll(this);
            }
        },

        /**
         * Open Reset Bracket Modal
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

            var modal = document.getElementById('seResetModalBackdrop') || document.getElementById('deResetModalBackdrop');
            if (modal) {
                modal.style.display = 'flex';
                modal.classList.add('show');
            }
        },

        /**
         * Close Reset Bracket Modal
         */
        closeResetModal: function () {
            var modal = document.getElementById('seResetModalBackdrop') || document.getElementById('deResetModalBackdrop');
            if (modal) {
                modal.style.display = 'none';
                modal.classList.remove('show');
            }
        },

        /**
         * Confirm Reset Bracket execution
         */
        confirmResetBracket: function () {
            this.closeResetModal();
            this.resetBracket(true);
        },

        /**
         * Reset entire Double Elimination bracket via POST to servlet
         */
        resetBracket: function (skipConfirm) {
            var self = this;
            var tid = this.tournamentId || window.TourmaTournamentId || 'demo';
            fetch((this.contextPath || '') + '/api/match-update', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                body: new URLSearchParams({
                    action: 'resetBracket',
                    tournamentId: tid,
                    stage: this.currentStage || 1
                }).toString()
            })
                .then(function (res) { return res.json(); })
                .then(function (data) {
                    window.location.reload();
                })
                .catch(function (err) {
                    console.error('[DoubleEliminationEngine] Reset error:', err);
                    window.location.reload();
                });
        },

        /**
         * Check if stage or entire tournament has completed
         */
        checkTournamentCompletion: function () {
            var self = this;
            // 1. Qualifier/Cut Stages (Cut Top N)
            if (this.cutTarget && this.cutTarget > 1) {
                if (window.FinalStagePopup && typeof window.FinalStagePopup.closeBanner === 'function') {
                    window.FinalStagePopup.closeBanner();
                }
                if (window.StageEndPopup && typeof window.StageEndPopup.update === 'function') {
                    window.StageEndPopup.update(
                        this.tournamentId,
                        'DOUBLE_ELIMINATION',
                        this.matchesMap,
                        this.teamsList,
                        { isMultiStage: true, cutTarget: this.cutTarget },
                        null,
                        this.currentStage
                    );
                }
                return;
            }

            // 2. Delegate directly to unified FinalStagePopup engine
            if (window.FinalStagePopup && typeof window.FinalStagePopup.checkAndRender === 'function') {
                window.FinalStagePopup.checkAndRender(
                    this.tournamentId,
                    'DOUBLE_ELIMINATION',
                    this.matchesMap,
                    this.teamsList,
                    { isCutStage: false, cutTarget: 0 },
                    function (isLocked) {
                        if (window.TourmaRoundControls && typeof window.TourmaRoundControls.updateButtonsState === 'function') {
                            window.TourmaRoundControls.updateButtonsState(self);
                        }
                    }
                );
            }
        },

        /**
         * Update visual state of Round Random and Reset buttons
         */
        updateRoundRandomButtons: function () {
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.updateButtonsState === 'function') {
                window.TourmaRoundControls.updateButtonsState(this);
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

        /**
         * Check if a team name is a placeholder
         */
        isPlaceholder: function (name) {
            if (name === undefined || name === null) return true;
            var t = String(name).trim();
            if (!t || t === 'BYE' || t === 'TBD' || t === '?') return true;
            if (t.startsWith('W #') || t.startsWith('L #') || t.startsWith('W#') || t.startsWith('L#')) return true;
            if (t.startsWith('Winner ') || t.startsWith('Loser ')) return true;
            if (t === 'Winner UB' || t === 'Winner LB' || t === 'Loser UB' || t === 'Loser LB') return true;
            return false;
        },

        /**
         * Draw Standard Orthogonal SVG Connector Lines for Upper Bracket
         */
        drawUpperSvgConnectors: function () {
            var canvas = document.getElementById('upperViewportCanvas');
            var wrapper = document.getElementById('upperBracketColumnsWrapper');
            if (!canvas || !wrapper) return;
            if (window.TourmaViewport && typeof window.TourmaViewport.drawConnectors === 'function') {
                window.TourmaViewport.drawConnectors(canvas, wrapper, this.matchesMap, 1.0);
            }
        },

        /**
         * Draw Standard Orthogonal SVG Connector Lines for Lower Bracket
         */
        drawLowerSvgConnectors: function () {
            var canvas = document.getElementById('lowerViewportCanvas');
            var wrapper = document.getElementById('lowerBracketColumnsWrapper');
            if (!canvas || !wrapper) return;
            if (window.TourmaViewport && typeof window.TourmaViewport.drawConnectors === 'function') {
                window.TourmaViewport.drawConnectors(canvas, wrapper, this.matchesMap, 1.0);
            }
        }
    };

    // Export globally for both namespaces
    window.TourmaDoubleElimination = TourmaDoubleElimination;
    window.DoubleEliminationEngine = TourmaDoubleElimination;
    if (!window.TourmaPopup && window.TourmaScoreModal) {
        window.TourmaPopup = window.TourmaScoreModal;
    }

    // Global listener for tourmaMatchUpdated to ensure DB sync across all components
    document.addEventListener('tourmaMatchUpdated', function (e) {
        var detail = e.detail;
        if (!detail || !detail.matchId) return;
        if (window.TourmaDoubleElimination && typeof window.TourmaDoubleElimination.saveMatchScore === 'function') {
            window.TourmaDoubleElimination.saveMatchScore(
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
