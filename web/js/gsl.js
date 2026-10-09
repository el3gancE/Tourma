/**
 * ============================================================================
 * TOURMA - GSL FORMAT ENGINE & MULTI-VIEWPORT CONTROLLER (gsl.js)
 * High-Density Scaled Dual-Tournament Format Engine.
 * Unified 1-Viewport Architecture per Group (Upper & Lower in 1 Viewport)
 * 100% Matching Layout, Bracket Lines, Proportions, Round Controls & Viewports
 * with Single Elimination (SE) & Double Elimination (DE).
 * ============================================================================
 */
(function (window) {
    'use strict';

    var TourmaGSL = {
        tournamentId: null,
        tournamentName: 'GSL Tournament',
        teamsList: [],
        cutTarget: 0,
        currentStage: 1,
        tournamentType: 'SINGLE_STAGE',
        contextPath: '',

        // 32 Distinct Curated Accent Colors with High Adjacent Contrast (Alternating Hue Spectrum)
        GSL_PALETTE: [
            '#2dd4bf', // 0: Bảng A (Mint Teal)
            '#f43f5e', // 1: Bảng B (Rose Red)
            '#38bdf8', // 2: Bảng C (Sky Blue)
            '#fbbf24', // 3: Bảng D (Amber Gold)
            '#c084fc', // 4: Bảng E (Neon Purple)
            '#a3e635', // 5: Bảng F (Lime Green)
            '#f472b6', // 6: Bảng G (Hot Pink)
            '#06b6d4', // 7: Bảng H (Cyan Blue)
            '#f97316', // 8: Bảng I (Bright Orange)
            '#818cf8', // 9: Bảng J (Electric Indigo)
            '#4ade80', // 10: Bảng K (Emerald Green)
            '#e879f9', // 11: Bảng L (Fuchsia Magenta)
            '#facc15', // 12: Bảng M (Lemon Gold)
            '#60a5fa', // 13: Bảng N (Royal Blue)
            '#fb7185', // 14: Bảng O (Flamingo Pink)
            '#22c55e', // 15: Bảng P (Vibrant Green)
            '#a78bfa', // 16: Bảng Q (Lavender Purple)
            '#ff6b6b', // 17: Bảng R (Sunset Coral)
            '#0ea5e9', // 18: Bảng S (Deep Sky)
            '#eab308', // 19: Bảng T (Sunburst Yellow)
            '#d946ef', // 20: Bảng U (Electric Magenta)
            '#10b981', // 21: Bảng V (Teal Green)
            '#ea580c', // 22: Bảng W (Tangerine Flame)
            '#6366f1', // 23: Bảng X (Vivid Indigo)
            '#ec4899', // 24: Bảng Y (Candy Pink)
            '#84cc16', // 25: Bảng Z (Yellow-Lime)
            '#14b8a6', // 26: Bảng AA (Deep Aqua)
            '#ef4444', // 27: Bảng AB (Ruby Red)
            '#8b5cf6', // 28: Bảng AC (Deep Violet)
            '#3b82f6', // 29: Bảng AD (Sapphire Blue)
            '#f59e0b', // 30: Bảng AE (Deep Amber)
            '#db2777'  // 31: Bảng AF (Deep Magenta)
        ],

        compareGroupKeys: function (a, b) {
            var getIndex = function (str) {
                var s = String(str || '').trim();
                var match = s.match(/([A-Za-z]+)$/);
                if (match) {
                    var letters = match[1].toUpperCase();
                    if (letters.length === 1) {
                        return letters.charCodeAt(0) - 65; // A=0..Z=25
                    } else if (letters.length === 2) {
                        return 26 + (letters.charCodeAt(0) - 65) * 26 + (letters.charCodeAt(1) - 65); // AA=26..AF=31
                    }
                }
                var numMatch = s.match(/(\d+)$/);
                if (numMatch) return parseInt(numMatch[1], 10) - 1;
                return 9999;
            };
            var idxA = getIndex(a);
            var idxB = getIndex(b);
            if (idxA !== idxB) return idxA - idxB;
            return String(a).localeCompare(String(b));
        },

        getGroupColor: function (groupIdOrIndex) {
            if (typeof groupIdOrIndex === 'number') {
                return this.GSL_PALETTE[Math.abs(groupIdOrIndex) % this.GSL_PALETTE.length];
            }
            var str = String(groupIdOrIndex || '').trim();
            var match = str.match(/([A-Za-z]+)$/);
            if (match) {
                var letters = match[1].toUpperCase();
                var index = 0;
                if (letters.length === 1) {
                    index = letters.charCodeAt(0) - 65; // A=0, B=1...
                } else if (letters.length === 2) {
                    index = 26 + (letters.charCodeAt(0) - 65) * 26 + (letters.charCodeAt(1) - 65);
                }
                if (index >= 0 && index < this.GSL_PALETTE.length) {
                    return this.GSL_PALETTE[index];
                }
            }
            var numMatch = str.match(/(\d+)$/);
            if (numMatch) {
                var n = parseInt(numMatch[1], 10) - 1;
                if (!isNaN(n) && n >= 0) return this.GSL_PALETTE[n % this.GSL_PALETTE.length];
            }
            var hash = 0;
            for (var i = 0; i < str.length; i++) {
                hash = ((hash << 5) - hash) + str.charCodeAt(i);
                hash |= 0;
            }
            return this.GSL_PALETTE[Math.abs(hash) % this.GSL_PALETTE.length];
        },

        hexToRgb: function (hex) {
            var c = String(hex || '#2dd4bf').replace('#', '');
            if (c.length === 3) c = c[0] + c[0] + c[1] + c[1] + c[2] + c[2];
            var num = parseInt(c, 16);
            if (isNaN(num)) return '45, 212, 191';
            return ((num >> 16) & 255) + ', ' + ((num >> 8) & 255) + ', ' + (num & 255);
        },

        // Data Models
        matchesMap: {}, // Global matchId -> matchObj map
        groupsMap: {},  // groupId -> { groupId, title, upperRounds, lowerRounds, matchesMap, ... }

        // UI State
        activeGroupId: null, // 'ALL' or specific group name (e.g. 'Bảng A')
        currentViewMode: 'BRACKET', // 'BRACKET' | 'LIST'
        isQuickMode: false,

        /**
         * Initialize GSL Engine
         */
        init: function (options) {
            options = options || {};
            this.tournamentId = options.tournamentId || window.TourmaTournamentId || 'demo';
            this.tournamentName = options.tournamentName || 'GSL Tournament';
            this.teamsList = Array.isArray(options.teamsList) ? options.teamsList : [];
            this.cutTarget = parseInt(options.cutTarget, 10) || 0;
            this.currentStage = parseInt(options.stage, 10) || 1;
            this.tournamentType = options.tournamentType || 'SINGLE_STAGE';
            this.contextPath = options.contextPath || window.TourmaContextPath || '';

            console.log('[TourmaGSL] Initializing with DB matches:', (options.dbMatches ? options.dbMatches.length : 0), 'Teams:', this.teamsList.length);

            // 1. Sync Badges on Top Navbar
            var nameDisplay = document.getElementById('tournamentNameDisplay');
            if (nameDisplay && this.tournamentName) {
                nameDisplay.textContent = this.tournamentName;
            }

            var teamBadge = document.getElementById('tournamentTeamCountBadge') || document.getElementById('gslTeamCountBadge');
            if (teamBadge) {
                var count = this.teamsList.length;
                if (count === 0 && options.dbMatches && Array.isArray(options.dbMatches)) {
                    var set = {};
                    for (var i = 0; i < options.dbMatches.length; i++) {
                        var dm = options.dbMatches[i];
                        if (dm.team1 && dm.team1.name && !this.isPlaceholder(dm.team1.name) && dm.team1.name !== 'BYE') set[dm.team1.name] = true;
                        if (dm.team2 && dm.team2.name && !this.isPlaceholder(dm.team2.name) && dm.team2.name !== 'BYE') set[dm.team2.name] = true;
                    }
                    count = Object.keys(set).length;
                }
                teamBadge.textContent = count + ' Đội';
            }

            var advBadge = document.getElementById('tournamentAdvancingBadge') || document.getElementById('gslAdvancingBadge');
            if (advBadge && this.cutTarget > 0) {
                advBadge.innerHTML = '<i class="fa-solid fa-arrow-right-to-bracket"></i> ' + this.cutTarget + ' Đội đi tiếp';
                advBadge.style.display = 'inline-flex';
            }

            // 2. Hydrate or Construct Groups Model
            this.hydrateGroupsModel(options.dbMatches);

            // 3. Set Default Active Group Tab (Default: 'ALL' - Tất Cả Các Bảng)
            var groupKeys = Object.keys(this.groupsMap).sort(this.compareGroupKeys);
            if (groupKeys.length > 0) {
                var savedActiveGroup = null;
                try {
                    savedActiveGroup = localStorage.getItem('tourma_gsl_active_group_' + this.tournamentId);
                } catch (e) { }
                if (savedActiveGroup && (savedActiveGroup === 'ALL' || this.groupsMap[savedActiveGroup])) {
                    this.activeGroupId = savedActiveGroup;
                } else {
                    this.activeGroupId = 'ALL';
                }
            }

            // 4. Setup Controls & Preferences
            this.bindControls();

            // 5. Render UI & Init Group Viewports
            this.render();

            // 6. Check Completion Status
            this.checkTournamentCompletion();
        },

        hydrateBracketModel: function (dbMatches) {
            this.hydrateGroupsModel(dbMatches);
        },

        /**
         * Construct Groups & Bracket Data Structures from database records
         */
        hydrateGroupsModel: function (dbMatches) {
            this.matchesMap = {};
            this.groupsMap = {};

            if (Array.isArray(dbMatches) && dbMatches.length > 0) {
                for (var i = 0; i < dbMatches.length; i++) {
                    var m = dbMatches[i];
                    var mId = String(m.matchId || m.id || m.rawId || (i + 1));
                    var gId = m.groupId || 'Bảng A';
                    var rNum = parseInt(m.roundNumber || 1, 10);
                    var bType = (m.bracketType || 'WINNER_BRACKET').toUpperCase();

                    if (!this.groupsMap[gId]) {
                        this.groupsMap[gId] = {
                            groupId: gId,
                            title: gId,
                            upperRounds: [],
                            lowerRounds: [],
                            matchesMap: {},
                            upperGroups: {},
                            lowerGroups: {},
                            maxUbRound: 1,
                            maxLbRound: 1
                        };
                    }

                    var gObj = this.groupsMap[gId];
                    var matchObj = {
                        matchId: mId,
                        id: mId,
                        rawId: m.rawId || mId,
                        groupId: gId,
                        roundNumber: rNum,
                        matchNumber: m.matchNumber || (i + 1),
                        bracketType: (bType.indexOf('LOWER') !== -1 || bType.indexOf('LOSER') !== -1) ? 'LOWER' : 'UPPER',
                        team1: m.team1 || { name: '', seed: '', score: '' },
                        team2: m.team2 || { name: '', seed: '', score: '' },
                        winnerId: m.winnerId || null,
                        nextMatchId: m.nextMatchId ? String(m.nextMatchId) : null,
                        nextMatchSlot: m.nextMatchSlot || 1,
                        loserNextMatchId: m.loserNextMatchId ? String(m.loserNextMatchId) : null,
                        loserNextSlot: m.loserNextSlot || 1,
                        dropToMatchId: (m.dropToMatchId || m.loserNextMatchId) ? String(m.dropToMatchId || m.loserNextMatchId) : null,
                        dropToMatchSlot: m.dropToMatchSlot || m.loserNextSlot || 1,
                        isBye: m.isBye === true || m.isBye === 'true',
                        status: m.status || 'SCHEDULED'
                    };

                    this.matchesMap[mId] = matchObj;
                    gObj.matchesMap[mId] = matchObj;
                    if (matchObj.rawId) {
                        this.matchesMap[String(matchObj.rawId)] = matchObj;
                        gObj.matchesMap[String(matchObj.rawId)] = matchObj;
                    }

                    if (matchObj.bracketType === 'LOWER') {
                        if (rNum > gObj.maxLbRound) gObj.maxLbRound = rNum;
                        if (!gObj.lowerGroups[rNum]) gObj.lowerGroups[rNum] = [];
                        gObj.lowerGroups[rNum].push(matchObj);
                    } else {
                        if (rNum > gObj.maxUbRound) gObj.maxUbRound = rNum;
                        if (!gObj.upperGroups[rNum]) gObj.upperGroups[rNum] = [];
                        gObj.upperGroups[rNum].push(matchObj);
                    }
                }

                var getMatchNum = function (m) {
                    if (m.matchNumber !== undefined && m.matchNumber !== null && !isNaN(Number(m.matchNumber))) return Number(m.matchNumber);
                    var s = String(m.rawId || m.matchId || m.id || '');
                    var idx = s.lastIndexOf('_');
                    if (idx !== -1) {
                        var n = parseInt(s.substring(idx + 1), 10);
                        if (!isNaN(n)) return n;
                    }
                    var digits = s.replace(/[^0-9]/g, '');
                    return digits ? parseInt(digits, 10) : 0;
                };

                // Finalize ordered Upper & Lower Rounds per Group
                for (var gKey in this.groupsMap) {
                    var grp = this.groupsMap[gKey];
                    grp.upperRounds = [];
                    grp.lowerRounds = [];

                    for (var u = 1; u <= grp.maxUbRound; u++) {
                        var uMatches = grp.upperGroups[u] || [];
                        uMatches.sort(function (a, b) { return getMatchNum(a) - getMatchNum(b); });
                        var uTitle = 'UB Round ' + u;
                        grp.upperRounds.push({
                            roundNumber: u,
                            bracketType: 'UPPER',
                            title: uTitle,
                            matches: uMatches
                        });
                    }

                    for (var l = 1; l <= grp.maxLbRound; l++) {
                        var lMatches = grp.lowerGroups[l] || [];
                        lMatches.sort(function (a, b) { return getMatchNum(a) - getMatchNum(b); });
                        var lTitle = 'LB Round ' + l;
                        grp.lowerRounds.push({
                            roundNumber: l,
                            bracketType: 'LOWER',
                            title: lTitle,
                            matches: lMatches
                        });
                    }

                    // Clean display numbers #1..#5 within each group for standard UI aesthetics
                    this.assignIntraGroupMatchNumbers(grp);
                    this.fillMissingPlaceholdersForGroup(grp);
                }
            } else {
                // Algorithmic generation from teamsList if DB empty
                this.generateGSLBracketsFromTeams();
            }
        },

        /**
         * Assign clean display match numbers #1, #2, #3, #4, #5 within each group
         */
        assignIntraGroupMatchNumbers: function (grp) {
            if (!grp || !grp.matchesMap) return;
            var counter = 1;
            // 1. Upper Bracket Opening matches
            if (grp.upperRounds && grp.upperRounds[0] && grp.upperRounds[0].matches) {
                grp.upperRounds[0].matches.forEach(function (m) {
                    m.matchNumber = counter++;
                });
            }
            // 2. Lower Bracket Round 1 (Elimination)
            if (grp.lowerRounds && grp.lowerRounds[0] && grp.lowerRounds[0].matches) {
                grp.lowerRounds[0].matches.forEach(function (m) {
                    m.matchNumber = counter++;
                });
            }
            // 3. Upper Bracket Final (Winner Qualification)
            if (grp.upperRounds && grp.upperRounds.length > 1) {
                for (var u = 1; u < grp.upperRounds.length; u++) {
                    grp.upperRounds[u].matches.forEach(function (m) {
                        m.matchNumber = counter++;
                    });
                }
            }
            // 4. Lower Bracket Final (Decider / Loser Qualification)
            if (grp.lowerRounds && grp.lowerRounds.length > 1) {
                for (var l = 1; l < grp.lowerRounds.length; l++) {
                    grp.lowerRounds[l].matches.forEach(function (m) {
                        m.matchNumber = counter++;
                    });
                }
            }
        },

        /**
         * Fill missing placeholders (W #1, L #2) or actual advanced teams in this group
         */
        fillMissingPlaceholdersForGroup: function (grp) {
            if (!grp || !grp.matchesMap) return;
            var self = this;

            for (var k in grp.matchesMap) {
                var m = grp.matchesMap[k];
                if (!m) continue;

                // 1. Fill Team 1
                if (!m.team1 || !m.team1.name) {
                    var p1 = this.findParentMatchInGroup(grp, m.matchId, 1);
                    if (p1) {
                        var p1Winner = this.getMatchWinner(p1);
                        if (p1Winner && p1Winner.name && !self.isPlaceholder(p1Winner.name)) {
                            m.team1 = { name: p1Winner.name, seed: p1Winner.seed || '', score: (m.team1 && m.team1.score !== undefined) ? m.team1.score : '' };
                        } else {
                            var t1P = p1.matchNumber ? ('W #' + p1.matchNumber) : 'TBD';
                            m.team1 = { name: t1P, seed: '', score: '' };
                        }
                    } else {
                        var dropP1 = this.findDropFeederMatchInGroup(grp, m.matchId, 1);
                        if (dropP1) {
                            var dropP1Loser = this.getMatchLoser(dropP1);
                            if (dropP1Loser && dropP1Loser.name && !self.isPlaceholder(dropP1Loser.name)) {
                                m.team1 = { name: dropP1Loser.name, seed: dropP1Loser.seed || '', score: (m.team1 && m.team1.score !== undefined) ? m.team1.score : '' };
                            } else {
                                var l1P = dropP1.matchNumber ? ('L #' + dropP1.matchNumber) : 'TBD';
                                m.team1 = { name: l1P, seed: '', score: '' };
                            }
                        }
                    }
                }

                // 2. Fill Team 2
                if (!m.team2 || !m.team2.name) {
                    var p2 = this.findParentMatchInGroup(grp, m.matchId, 2);
                    if (p2) {
                        var p2Winner = this.getMatchWinner(p2);
                        if (p2Winner && p2Winner.name && !self.isPlaceholder(p2Winner.name)) {
                            m.team2 = { name: p2Winner.name, seed: p2Winner.seed || '', score: (m.team2 && m.team2.score !== undefined) ? m.team2.score : '' };
                        } else {
                            var t2P = p2.matchNumber ? ('W #' + p2.matchNumber) : 'TBD';
                            m.team2 = { name: t2P, seed: '', score: '' };
                        }
                    } else {
                        var dropP2 = this.findDropFeederMatchInGroup(grp, m.matchId, 2);
                        if (dropP2) {
                            var dropP2Loser = this.getMatchLoser(dropP2);
                            if (dropP2Loser && dropP2Loser.name && !self.isPlaceholder(dropP2Loser.name)) {
                                m.team2 = { name: dropP2Loser.name, seed: dropP2Loser.seed || '', score: (m.team2 && m.team2.score !== undefined) ? m.team2.score : '' };
                            } else {
                                var l2P = dropP2.matchNumber ? ('L #' + dropP2.matchNumber) : 'TBD';
                                m.team2 = { name: l2P, seed: '', score: '' };
                            }
                        }
                    }
                }
            }
        },

        findParentMatchInGroup: function (grp, targetMatchId, slot) {
            if (!grp || !grp.matchesMap || !targetMatchId) return null;
            var targetStr = String(targetMatchId).trim();
            for (var k in grp.matchesMap) {
                var m = grp.matchesMap[k];
                if (!m || !m.nextMatchId) continue;
                var nId = String(m.nextMatchId).trim();
                if (nId === targetStr || targetStr.endsWith('_' + nId) || nId.endsWith('_' + targetStr)) {
                    var s = (m.nextMatchSlot === 2 || m.nextMatchSlot === '2' || m.nextMatchSlot === 'SLOT_2') ? 2 : 1;
                    if (slot === undefined || s === slot) return m;
                }
            }
            return null;
        },

        findDropFeederMatchInGroup: function (grp, targetMatchId, slot) {
            if (!grp || !grp.matchesMap || !targetMatchId) return null;
            var targetStr = String(targetMatchId).trim();
            for (var k in grp.matchesMap) {
                var m = grp.matchesMap[k];
                if (!m) continue;
                var dId = m.dropToMatchId || m.loserNextMatchId;
                if (!dId) continue;
                var dropStr = String(dId).trim();
                if (dropStr === targetStr || targetStr.endsWith('_' + dropStr) || dropStr.endsWith('_' + targetStr)) {
                    var s = (m.dropToMatchSlot === 2 || m.dropToMatchSlot === '2' || m.dropToMatchSlot === 'SLOT_2' || m.loserNextSlot === 2 || m.loserNextSlot === '2' || m.loserNextSlot === 'SLOT_2') ? 2 : 1;
                    if (slot === undefined || s === slot) return m;
                }
            }
            return null;
        },

        getMatchWinner: function (m) {
            if (!m) return null;
            if (m.winnerId === 'team1' || m.winnerId === 1 || m.winnerId === '1' || m.winnerId === 'SLOT_1') return m.team1;
            if (m.winnerId === 'team2' || m.winnerId === 2 || m.winnerId === '2' || m.winnerId === 'SLOT_2') return m.team2;
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
            if (m.winnerId === 'team1' || m.winnerId === 1 || m.winnerId === '1' || m.winnerId === 'SLOT_1') return m.team2;
            if (m.winnerId === 'team2' || m.winnerId === 2 || m.winnerId === '2' || m.winnerId === 'SLOT_2') return m.team1;
            var s1 = parseInt(m.team1 ? m.team1.score : '', 10);
            var s2 = parseInt(m.team2 ? m.team2.score : '', 10);
            if (!isNaN(s1) && !isNaN(s2) && (m.status === 'COMPLETED' || m.status === 'FINISHED' || m.status === 'DONE')) {
                if (s1 > s2) return m.team2;
                if (s2 > s1) return m.team1;
            }
            return null;
        },

        /**
         * Algorithmic generation for GSL Groups (fallback when DB is empty)
         */
        generateGSLBracketsFromTeams: function () {
            if (!this.teamsList || this.teamsList.length < 2) return;
            var numTeams = this.teamsList.length;
            var teamsPerGroup = 4;
            if (numTeams % 4 !== 0 && numTeams >= 2) {
                teamsPerGroup = numTeams <= 2 ? 2 : 4;
            }
            var numGroups = Math.max(1, Math.ceil(numTeams / teamsPerGroup));

            var groupTeamsMap = {};
            for (var g = 0; g < numGroups; g++) {
                var gSuffix = (g < 26)
                    ? String.fromCharCode(65 + g)
                    : (String.fromCharCode(65 + Math.floor((g - 26) / 26)) + String.fromCharCode(65 + ((g - 26) % 26)));
                groupTeamsMap['Bảng ' + gSuffix] = [];
            }

            var gKeys = Object.keys(groupTeamsMap);
            for (var i = 0; i < this.teamsList.length; i++) {
                var roundIdx = Math.floor(i / numGroups);
                var posInRound = i % numGroups;
                var targetGIdx = (roundIdx % 2 === 0) ? posInRound : (numGroups - 1 - posInRound);
                groupTeamsMap[gKeys[targetGIdx]].push(this.teamsList[i]);
            }

            var advancePerGroup = Math.max(1, Math.floor(this.cutTarget / numGroups)) || 2;

            for (var k in groupTeamsMap) {
                var gTeams = groupTeamsMap[k];
                if (gTeams.length < 2) continue;
                if (window.TourmaDoubleElimAlgorithm && typeof window.TourmaDoubleElimAlgorithm.generateDoubleElimination === 'function') {
                    var generated = window.TourmaDoubleElimAlgorithm.generateDoubleElimination(gTeams, advancePerGroup);
                    var safeGPrefix = k.replace(/[^a-zA-Z0-9]/g, '_').toUpperCase();

                    var groupMap = {};
                    for (var rawMId in generated.matchesMap) {
                        var originalM = generated.matchesMap[rawMId];
                        var scopedId = (this.tournamentId || 'demo') + '_' + safeGPrefix + '_' + originalM.matchId;
                        var scopedNextId = originalM.nextMatchId ? ((this.tournamentId || 'demo') + '_' + safeGPrefix + '_' + originalM.nextMatchId) : null;
                        var scopedDropId = originalM.dropToMatchId ? ((this.tournamentId || 'demo') + '_' + safeGPrefix + '_' + originalM.dropToMatchId) : null;

                        var scopedMatch = Object.assign({}, originalM, {
                            matchId: scopedId,
                            id: scopedId,
                            rawId: scopedId,
                            groupId: k,
                            nextMatchId: scopedNextId,
                            dropToMatchId: scopedDropId,
                            loserNextMatchId: scopedDropId
                        });

                        groupMap[scopedId] = scopedMatch;
                        this.matchesMap[scopedId] = scopedMatch;
                    }

                    this.groupsMap[k] = {
                        groupId: k,
                        title: k,
                        upperRounds: generated.upperRounds || [],
                        lowerRounds: generated.lowerRounds || [],
                        matchesMap: groupMap
                    };

                    this.assignIntraGroupMatchNumbers(this.groupsMap[k]);
                    this.fillMissingPlaceholdersForGroup(this.groupsMap[k]);
                }
            }
        },

        /**
         * Find a match by ID across all groups strictly avoiding ID collisions
         */
        findMatch: function (targetId) {
            if (!targetId) return null;
            var strId = String(targetId).trim();

            if (this.matchesMap && this.matchesMap[strId]) return this.matchesMap[strId];

            for (var gKey in this.groupsMap) {
                var grp = this.groupsMap[gKey];
                if (grp.matchesMap && grp.matchesMap[strId]) return grp.matchesMap[strId];
            }

            for (var k in this.matchesMap) {
                var m = this.matchesMap[k];
                if (!m) continue;
                if (m.matchId && String(m.matchId) === strId) return m;
                if (m.id && String(m.id) === strId) return m;
                if (m.rawId && String(m.rawId) === strId) return m;
            }

            for (var gKey2 in this.groupsMap) {
                var grp2 = this.groupsMap[gKey2];
                if (grp2.matchesMap) {
                    for (var mk in grp2.matchesMap) {
                        var mObj = grp2.matchesMap[mk];
                        if (!mObj) continue;
                        var mIdStr = String(mObj.matchId || mObj.id || mObj.rawId || '');
                        if (mIdStr === strId || mIdStr.endsWith('_' + strId) || strId.endsWith('_' + mIdStr)) {
                            return mObj;
                        }
                    }
                }
            }

            return null;
        },

        /**
         * Setup Controls & Restore User Preferences
         */
        bindControls: function () {
            var self = this;

            // 1. Restore View Mode from localStorage
            var savedView = null;
            try {
                savedView = localStorage.getItem('tourma_view_mode_' + this.tournamentId) ||
                            localStorage.getItem('tourma_view_mode_' + (window.TourmaTournamentId || 'demo')) ||
                            localStorage.getItem('tourma_gsl_view_mode_' + this.tournamentId) ||
                            localStorage.getItem('tourma_gsl_view_mode_' + (window.TourmaTournamentId || 'demo'));
            } catch (e) { }
            if (savedView) {
                this.currentViewMode = (savedView.toUpperCase() === 'LIST') ? 'LIST' : 'BRACKET';
            }

            // 2. Restore Quick Mode from localStorage
            var savedQuick = null;
            try {
                savedQuick = localStorage.getItem('tourma_quick_mode_' + this.tournamentId) || localStorage.getItem('tourma_gsl_quick_mode_' + this.tournamentId);
            } catch (e) { }
            if (savedQuick === 'true') {
                this.isQuickMode = true;
                window.TourmaQuickMode = true;
                document.body.classList.add('quick-mode-active');
                var btns = document.querySelectorAll('#singleBtnQuickMode, #gslBtnQuickMode, .btn-quick-mode-toggle');
                btns.forEach(function (btn) {
                    btn.classList.add('active');
                    var st = btn.querySelector('.quick-mode-status-text');
                    if (st) st.textContent = 'ON';
                });
            } else {
                this.isQuickMode = false;
                window.TourmaQuickMode = false;
                document.body.classList.remove('quick-mode-active');
                var btns = document.querySelectorAll('#singleBtnQuickMode, #gslBtnQuickMode, .btn-quick-mode-toggle');
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

            // 4. Redraw SVGs on window resize
            window.addEventListener('resize', function () {
                if (self.currentViewMode === 'BRACKET') {
                    self.drawAllSvgConnectors();
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
                localStorage.setItem('tourma_gsl_quick_mode_' + this.tournamentId, this.isQuickMode ? 'true' : 'false');
            } catch (e) { }

            if (this.isQuickMode) {
                document.body.classList.add('quick-mode-active');
            } else {
                document.body.classList.remove('quick-mode-active');
            }

            var btns = document.querySelectorAll('#singleBtnQuickMode, #gslBtnQuickMode, .btn-quick-mode-toggle');
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
                localStorage.setItem('tourma_gsl_view_mode_' + this.tournamentId, normMode);
            } catch (e) { }

            var btnBracketViews = document.querySelectorAll('#btnViewBracket, #gslBtnBracketView');
            var btnListViews = document.querySelectorAll('#btnViewList, #gslBtnListView');
            var ws = document.getElementById('gslGroupsWorkspace');
            var listContainer = document.getElementById('gslListViewContainer');

            if (this.currentViewMode === 'LIST') {
                btnListViews.forEach(function (b) { b.classList.add('active'); });
                btnBracketViews.forEach(function (b) { b.classList.remove('active'); });
                if (ws) ws.style.display = 'none';
                if (listContainer) listContainer.style.display = 'flex';
                this.renderListView();
            } else {
                btnBracketViews.forEach(function (b) { b.classList.add('active'); });
                btnListViews.forEach(function (b) { b.classList.remove('active'); });
                if (ws) ws.style.display = 'flex';
                if (listContainer) listContainer.style.display = 'none';
                this.renderBracketViews();
            }
            this.updateRoundRandomButtons();
        },

        /**
         * Select Active Group Tab
         */
        selectGroupTab: function (groupId) {
            this.activeGroupId = groupId;
            try {
                localStorage.setItem('tourma_gsl_active_group_' + this.tournamentId, groupId);
            } catch (e) { }
            this.render();
        },

        /**
         * Main Render Function
         */
        render: function () {
            this.renderBracketViews();
            this.renderListView();

            var ws = document.getElementById('gslGroupsWorkspace');
            var listContainer = document.getElementById('gslListViewContainer');
            var btnBracketViews = document.querySelectorAll('#btnViewBracket, #gslBtnBracketView, .btn-view-toggle:first-child');
            var btnListViews = document.querySelectorAll('#btnViewList, #gslBtnListView, .btn-view-toggle:last-child');

            if (this.currentViewMode === 'LIST') {
                btnListViews.forEach(function (b) { b.classList.add('active'); });
                btnBracketViews.forEach(function (b) { b.classList.remove('active'); });
                if (ws) ws.style.display = 'none';
                if (listContainer) listContainer.style.display = 'flex';
            } else {
                btnBracketViews.forEach(function (b) { b.classList.add('active'); });
                btnListViews.forEach(function (b) { b.classList.remove('active'); });
                if (ws) ws.style.display = 'flex';
                if (listContainer) listContainer.style.display = 'none';
            }

            this.updateRoundRandomButtons();
            this.checkTournamentCompletion();
        },

        /**
         * Render Multi-Group Tabs and Unified Single Viewport per Group
         */
        renderBracketViews: function () {
            var self = this;
            var workspace = document.getElementById('gslGroupsWorkspace');
            if (!workspace) return;
            workspace.innerHTML = '';

            var groupKeys = Object.keys(this.groupsMap).sort(self.compareGroupKeys);
            if (groupKeys.length === 0) {
                workspace.innerHTML = '<div style="padding: 40px; text-align: center; color: #94a3b8;">Chưa có dữ liệu bảng đấu GSL.</div>';
                return;
            }

            // Ensure valid active group (Default: 'ALL')
            if (!this.activeGroupId || (this.activeGroupId !== 'ALL' && !this.groupsMap[this.activeGroupId])) {
                this.activeGroupId = 'ALL';
            }

            // 1. Render Group Tabs Bar (If 2+ groups)
            if (groupKeys.length > 1) {
                var tabsBar = document.createElement('div');
                tabsBar.className = 'gsl-group-tabs-bar';

                // All Groups Tab (Placed First!)
                var allTabBtn = document.createElement('button');
                allTabBtn.type = 'button';
                allTabBtn.className = 'gsl-group-tab-btn' + (self.activeGroupId === 'ALL' ? ' active' : '');
                allTabBtn.innerHTML = '<i class="fa-solid fa-table-cells"></i> Tất Cả Bảng';
                allTabBtn.addEventListener('click', function () {
                    self.selectGroupTab('ALL');
                });
                tabsBar.appendChild(allTabBtn);

                groupKeys.forEach(function (gKey) {
                    var grp = self.groupsMap[gKey];
                    var gColor = self.getGroupColor(gKey);
                    var gRgb = self.hexToRgb(gColor);

                    var tabBtn = document.createElement('button');
                    tabBtn.type = 'button';
                    tabBtn.className = 'gsl-group-tab-btn' + (self.activeGroupId === gKey ? ' active' : '');
                    tabBtn.style.setProperty('--tab-color', gColor);
                    tabBtn.style.setProperty('--tab-hover', 'rgba(' + gRgb + ', 0.18)');
                    tabBtn.style.setProperty('--tab-glow', 'rgba(' + gRgb + ', 0.35)');
                    tabBtn.setAttribute('data-group-color', gColor);
                    
                    var playedCount = 0;
                    var totalCount = 0;
                    for (var mk in grp.matchesMap) {
                        totalCount++;
                        var m = grp.matchesMap[mk];
                        if (m && (m.status === 'COMPLETED' || m.status === 'FINISHED' || m.status === 'DONE')) {
                            playedCount++;
                        }
                    }

                    tabBtn.innerHTML = '<i class="fa-solid fa-layer-group"></i> ' + gKey + ' <span class="gsl-group-tab-badge">' + playedCount + '/' + totalCount + '</span>';
                    tabBtn.addEventListener('click', function () {
                        self.selectGroupTab(gKey);
                    });
                    tabsBar.appendChild(tabBtn);
                });

                workspace.appendChild(tabsBar);
            }

            // 2. Determine which groups to render
            var groupsToRender = (this.activeGroupId === 'ALL') ? groupKeys : [this.activeGroupId];

            groupsToRender.forEach(function (gKey) {
                var grp = self.groupsMap[gKey];
                if (!grp) return;
                var safeGId = gKey.replace(/[^a-zA-Z0-9]/g, '_');
                var gColor = self.getGroupColor(gKey);
                var gRgb = self.hexToRgb(gColor);

                var sec = document.createElement('div');
                sec.className = 'gsl-group-section';
                sec.id = 'gslGroupSection_' + safeGId;
                sec.style.setProperty('--grp-color', gColor);
                sec.style.setProperty('--grp-hover', 'rgba(' + gRgb + ', 0.18)');
                sec.style.setProperty('--grp-glow', 'rgba(' + gRgb + ', 0.45)');
                sec.setAttribute('data-group-color', gColor);

                // Group Header Bar
                var headerCard = document.createElement('div');
                headerCard.className = 'gsl-group-main-header';
                
                var teamCount = 4;
                if (grp.upperRounds && grp.upperRounds[0] && grp.upperRounds[0].matches) {
                    teamCount = grp.upperRounds[0].matches.length * 2;
                }
                var advCount = Math.max(1, Math.floor(self.cutTarget / groupKeys.length)) || 2;

                headerCard.innerHTML =
                    '<div class="gsl-group-title-wrap">' +
                    '<h3 class="gsl-group-title"><i class="fa-solid fa-layer-group"></i> ' + grp.title + '</h3>' +
                    '<span class="gsl-group-badge">' + teamCount + ' Đội</span>' +
                    '<span class="gsl-group-advancing-badge"><i class="fa-solid fa-arrow-right-to-bracket"></i> ' + advCount + ' Đội đi tiếp</span>' +
                    '</div>' +
                    '<div class="gsl-group-actions">' +
                    '<button type="button" class="btn-gsl-group-action random-btn" onclick="TourmaGSL.executeRandomGroup(\'' + gKey + '\')" title="Tạo tỉ số ngẫu nhiên cho ' + grp.title + '">' +
                    '<i class="fa-solid fa-dice"></i> Random Bảng' +
                    '</button>' +
                    '<button type="button" class="btn-gsl-group-action reset-btn" onclick="TourmaGSL.executeResetGroup(\'' + gKey + '\')" title="Đặt lại kết quả ' + grp.title + '">' +
                    '<i class="fa-solid fa-rotate-right"></i> Reset' +
                    '</button>' +
                    '</div>';
                sec.appendChild(headerCard);

                // ============================================================
                // UNIFIED SINGLE VIEWPORT FRAME FOR BOTH UPPER & LOWER
                // ============================================================
                var vpFrame = document.createElement('div');
                vpFrame.className = 'bracket-viewport-frame gsl-viewport-frame';
                vpFrame.id = 'viewportFrame_' + safeGId;

                // Floating Zoom Toolbar
                var zoomToolbar = document.createElement('div');
                zoomToolbar.className = 'bracket-zoom-toolbar gsl-zoom-toolbar';
                zoomToolbar.id = 'zoomToolbar_' + safeGId;
                zoomToolbar.innerHTML =
                    '<button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.zoomOut(\'viewportContainer_' + safeGId + '\')" title="Thu nhỏ (-)">' +
                    '<i class="fa-solid fa-minus"></i>' +
                    '</button>' +
                    '<span id="zoomBadge_' + safeGId + '" class="zoom-level-badge">100%</span>' +
                    '<button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.zoomIn(\'viewportContainer_' + safeGId + '\')" title="Phóng to (+)">' +
                    '<i class="fa-solid fa-plus"></i>' +
                    '</button>' +
                    '<button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.resetZoom(\'viewportContainer_' + safeGId + '\')" title="Reset (100%)">' +
                    '<i class="fa-solid fa-rotate-right"></i>' +
                    '</button>';
                vpFrame.appendChild(zoomToolbar);

                var vpContainer = document.createElement('div');
                vpContainer.className = 'bracket-viewport-container gsl-viewport-container';
                vpContainer.id = 'viewportContainer_' + safeGId;

                var vpCanvas = document.createElement('div');
                vpCanvas.className = 'bracket-viewport-canvas gsl-viewport-canvas';
                vpCanvas.id = 'viewportCanvas_' + safeGId;
                vpCanvas.setAttribute('data-group-color', gColor);
                vpCanvas.style.setProperty('--grp-color', gColor);

                var singleWrapper = document.createElement('div');
                singleWrapper.className = 'gsl-single-viewport-wrapper';
                singleWrapper.id = 'columnsWrapper_' + safeGId;
                singleWrapper.setAttribute('data-group-color', gColor);
                singleWrapper.style.setProperty('--grp-color', gColor);

                // ------------------------------------------------------------
                // 1. UPPER BRACKET BRANCH
                // ------------------------------------------------------------
                var ubBranch = document.createElement('div');
                ubBranch.className = 'gsl-branch-section upper';

                var ubBranchHeader = document.createElement('div');
                ubBranchHeader.className = 'gsl-branch-header';
                ubBranchHeader.innerHTML = '<h4 class="gsl-branch-title">Upper Bracket</h4>';
                ubBranch.appendChild(ubBranchHeader);

                var ubCols = document.createElement('div');
                ubCols.className = 'gsl-branch-columns';

                grp.upperRounds.forEach(function (roundObj) {
                    var col = document.createElement('div');
                    col.className = 'de-round-column single-round-column gsl-round-column';
                    col.setAttribute('data-round', roundObj.roundNumber);
                    col.setAttribute('data-bracket-type', 'UPPER');
                    col.setAttribute('data-group-id', grp.groupId);

                    var rHead = document.createElement('div');
                    rHead.className = 'de-round-header single-round-header gsl-round-header';
                    var rName = roundObj.title || ('UB Round ' + roundObj.roundNumber);
                    var ctrlHtml = (window.TourmaRoundControls && typeof window.TourmaRoundControls.renderHeaderControlsHtml === 'function')
                        ? window.TourmaRoundControls.renderHeaderControlsHtml(roundObj.roundNumber, rName, 'UPPER', grp.groupId)
                        : '';
                    rHead.innerHTML = '<div class="round-header-title">' + rName + '</div>' + ctrlHtml;
                    col.appendChild(rHead);

                    var stack = document.createElement('div');
                    stack.className = 'de-round-matches-stack single-round-matches-box gsl-round-matches-stack';

                    if (roundObj.matches) {
                        roundObj.matches.forEach(function (m) {
                            var liveM = grp.matchesMap[m.matchId] || m;
                            var card = window.TourmaBracketCard ? window.TourmaBracketCard.createNodeElement(liveM) : self.createFallbackCard(liveM);
                            if (card) {
                                self.attachCardClickListener(card, liveM);
                                stack.appendChild(card);
                            }
                        });
                    }
                    col.appendChild(stack);
                    ubCols.appendChild(col);
                });
                ubBranch.appendChild(ubCols);
                singleWrapper.appendChild(ubBranch);

                // ------------------------------------------------------------
                // 2. LOWER BRACKET BRANCH
                // ------------------------------------------------------------
                var lbBranch = document.createElement('div');
                lbBranch.className = 'gsl-branch-section lower';

                var lbBranchHeader = document.createElement('div');
                lbBranchHeader.className = 'gsl-branch-header';
                lbBranchHeader.innerHTML = '<h4 class="gsl-branch-title">Lower Bracket</h4>';
                lbBranch.appendChild(lbBranchHeader);

                var lbCols = document.createElement('div');
                lbCols.className = 'gsl-branch-columns';

                grp.lowerRounds.forEach(function (roundObj) {
                    var col = document.createElement('div');
                    col.className = 'de-round-column single-round-column gsl-round-column';
                    col.setAttribute('data-round', roundObj.roundNumber);
                    col.setAttribute('data-bracket-type', 'LOWER');
                    col.setAttribute('data-group-id', grp.groupId);

                    var rHead = document.createElement('div');
                    rHead.className = 'de-round-header single-round-header gsl-round-header';
                    var rName = roundObj.title || ('LB Round ' + roundObj.roundNumber);
                    var ctrlHtml = (window.TourmaRoundControls && typeof window.TourmaRoundControls.renderHeaderControlsHtml === 'function')
                        ? window.TourmaRoundControls.renderHeaderControlsHtml(roundObj.roundNumber, rName, 'LOWER', grp.groupId)
                        : '';
                    rHead.innerHTML = '<div class="round-header-title">' + rName + '</div>' + ctrlHtml;
                    col.appendChild(rHead);

                    var stack = document.createElement('div');
                    stack.className = 'de-round-matches-stack single-round-matches-box gsl-round-matches-stack';

                    if (roundObj.matches) {
                        roundObj.matches.forEach(function (m) {
                            var liveM = grp.matchesMap[m.matchId] || m;
                            var card = window.TourmaBracketCard ? window.TourmaBracketCard.createNodeElement(liveM) : self.createFallbackCard(liveM);
                            if (card) {
                                self.attachCardClickListener(card, liveM);
                                stack.appendChild(card);
                            }
                        });
                    }
                    col.appendChild(stack);
                    lbCols.appendChild(col);
                });
                lbBranch.appendChild(lbCols);
                singleWrapper.appendChild(lbBranch);

                vpCanvas.appendChild(singleWrapper);
                vpContainer.appendChild(vpCanvas);
                vpFrame.appendChild(vpContainer);
                sec.appendChild(vpFrame);

                workspace.appendChild(sec);
            });

            // 3. Initialize TourmaViewport & Draw Connectors for all rendered viewports
            groupsToRender.forEach(function (gKey) {
                var safeGId = gKey.replace(/[^a-zA-Z0-9]/g, '_');

                if (window.TourmaViewport && typeof window.TourmaViewport.init === 'function') {
                    window.TourmaViewport.init('viewportContainer_' + safeGId, 'viewportCanvas_' + safeGId, {
                        badgeId: 'zoomBadge_' + safeGId,
                        toolbarId: 'zoomToolbar_' + safeGId,
                        onRedraw: function () {
                            self.drawGroupSvgConnectors(gKey);
                        }
                    });
                }
            });

            // 4. Bind TourmaRoundControls events
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.bindEvents === 'function') {
                window.TourmaRoundControls.bindEvents(workspace, this);
            }

            // 5. Draw SVG Lines across all frames
            requestAnimationFrame(function () {
                self.drawAllSvgConnectors();
            });
            setTimeout(function () {
                self.drawAllSvgConnectors();
            }, 60);
            setTimeout(function () {
                self.drawAllSvgConnectors();
            }, 250);
        },

        /**
         * Render Matches List View for all groups
         */
        renderListView: function () {
            var self = this;
            var container = document.getElementById('gslListViewContainer');
            if (!container) return;
            container.innerHTML = '';

            var groupKeys = Object.keys(this.groupsMap).sort(self.compareGroupKeys);
            if (groupKeys.length === 0) {
                container.innerHTML = '<div style="padding: 40px; text-align: center; color: #94a3b8;">Chưa có trận đấu nào.</div>';
                return;
            }

            // Ensure valid active group (Default: 'ALL')
            if (!this.activeGroupId || (this.activeGroupId !== 'ALL' && !this.groupsMap[this.activeGroupId])) {
                this.activeGroupId = 'ALL';
            }

            // 1. Render Group Tabs Bar (If 2+ groups)
            if (groupKeys.length > 1) {
                var tabsBar = document.createElement('div');
                tabsBar.className = 'gsl-group-tabs-bar';

                // All Groups Tab (Placed First!)
                var allTabBtn = document.createElement('button');
                allTabBtn.type = 'button';
                allTabBtn.className = 'gsl-group-tab-btn' + (self.activeGroupId === 'ALL' ? ' active' : '');
                allTabBtn.innerHTML = '<i class="fa-solid fa-table-cells"></i> Tất Cả Bảng';
                allTabBtn.addEventListener('click', function () {
                    self.selectGroupTab('ALL');
                });
                tabsBar.appendChild(allTabBtn);

                groupKeys.forEach(function (gKey) {
                    var grp = self.groupsMap[gKey];
                    var gColor = self.getGroupColor(gKey);
                    var gRgb = self.hexToRgb(gColor);

                    var tabBtn = document.createElement('button');
                    tabBtn.type = 'button';
                    tabBtn.className = 'gsl-group-tab-btn' + (self.activeGroupId === gKey ? ' active' : '');
                    tabBtn.style.setProperty('--tab-color', gColor);
                    tabBtn.style.setProperty('--tab-hover', 'rgba(' + gRgb + ', 0.18)');
                    tabBtn.style.setProperty('--tab-glow', 'rgba(' + gRgb + ', 0.35)');
                    tabBtn.setAttribute('data-group-color', gColor);

                    var playedCount = 0;
                    var totalCount = 0;
                    if (grp && grp.matchesMap) {
                        for (var mk in grp.matchesMap) {
                            totalCount++;
                            var m = grp.matchesMap[mk];
                            if (m && (m.status === 'COMPLETED' || m.status === 'FINISHED' || m.status === 'DONE')) {
                                playedCount++;
                            }
                        }
                    }

                    tabBtn.innerHTML = '<i class="fa-solid fa-layer-group"></i> ' + gKey + ' <span class="gsl-group-tab-badge">' + playedCount + '/' + totalCount + '</span>';
                    tabBtn.addEventListener('click', function () {
                        self.selectGroupTab(gKey);
                    });
                    tabsBar.appendChild(tabBtn);
                });

                container.appendChild(tabsBar);
            }

            // 2. Determine which groups to render
            var groupsToRender = (this.activeGroupId === 'ALL') ? groupKeys : [this.activeGroupId];

            groupsToRender.forEach(function (gKey) {
                var grp = self.groupsMap[gKey];
                if (!grp) return;
                var gColor = self.getGroupColor(gKey);
                var gRgb = self.hexToRgb(gColor);

                var card = document.createElement('div');
                card.className = 'gsl-list-group-card';
                card.style.setProperty('--grp-color', gColor);
                card.style.setProperty('--grp-hover', 'rgba(' + gRgb + ', 0.18)');
                card.style.setProperty('--grp-glow', 'rgba(' + gRgb + ', 0.45)');
                card.setAttribute('data-group-color', gColor);

                var grpHeader = document.createElement('div');
                grpHeader.className = 'gsl-list-group-header';
                grpHeader.innerHTML =
                    '<h3 class="gsl-list-group-title"><i class="fa-solid fa-layer-group"></i> ' + grp.title + '</h3>' +
                    '<div class="gsl-group-actions">' +
                    '<button type="button" class="btn-gsl-group-action random-btn" onclick="TourmaGSL.executeRandomGroup(\'' + gKey + '\')" title="Tạo tỉ số ngẫu nhiên cho ' + grp.title + '">' +
                    '<i class="fa-solid fa-dice"></i> Random Bảng' +
                    '</button>' +
                    '<button type="button" class="btn-gsl-group-action reset-btn" onclick="TourmaGSL.executeResetGroup(\'' + gKey + '\')" title="Đặt lại kết quả ' + grp.title + '">' +
                    '<i class="fa-solid fa-rotate-right"></i> Reset' +
                    '</button>' +
                    '</div>';
                card.appendChild(grpHeader);

                // Upper Bracket Rounds in List View
                if (grp.upperRounds) {
                    grp.upperRounds.forEach(function (roundObj) {
                        var rSec = document.createElement('div');
                        rSec.className = 'list-round-section';
                        rSec.setAttribute('data-round', roundObj.roundNumber);
                        rSec.setAttribute('data-bracket-type', 'UPPER');
                        rSec.setAttribute('data-group-id', grp.groupId);

                        var sHead = document.createElement('div');
                        sHead.className = 'gsl-list-section-header';
                        var rName = roundObj.title || ('UB Round ' + roundObj.roundNumber);
                        var listCtrlHtml = (window.TourmaRoundControls && typeof window.TourmaRoundControls.renderListHeaderControlsHtml === 'function')
                            ? window.TourmaRoundControls.renderListHeaderControlsHtml(roundObj.roundNumber, rName, 'UPPER', grp.groupId)
                            : '';
                        sHead.innerHTML =
                            '<div class="list-round-title-group">' +
                            '<h4 class="round-header-title">' + rName + '</h4>' +
                            '<span class="list-round-badge">' + (roundObj.matches ? roundObj.matches.length : 0) + ' trận</span>' +
                            '</div>' +
                            listCtrlHtml;
                        rSec.appendChild(sHead);

                        var grid = document.createElement('div');
                        grid.className = 'list-matches-grid';
                        if (roundObj.matches) {
                            roundObj.matches.forEach(function (m) {
                                var liveMatch = grp.matchesMap[m.matchId] || m;
                                var listCard = window.TourmaMatchCard ? window.TourmaMatchCard.createCardElement(liveMatch) : self.createFallbackCard(liveMatch);
                                if (listCard) {
                                    self.attachCardClickListener(listCard, liveMatch);
                                    grid.appendChild(listCard);
                                }
                            });
                        }
                        rSec.appendChild(grid);
                        card.appendChild(rSec);
                    });
                }

                // Lower Bracket Rounds in List View
                if (grp.lowerRounds) {
                    grp.lowerRounds.forEach(function (roundObj) {
                        var rSec = document.createElement('div');
                        rSec.className = 'list-round-section';
                        rSec.setAttribute('data-round', roundObj.roundNumber);
                        rSec.setAttribute('data-bracket-type', 'LOWER');
                        rSec.setAttribute('data-group-id', grp.groupId);

                        var sHead = document.createElement('div');
                        sHead.className = 'gsl-list-section-header';
                        var rName = roundObj.title || ('LB Round ' + roundObj.roundNumber);
                        var listCtrlHtml = (window.TourmaRoundControls && typeof window.TourmaRoundControls.renderListHeaderControlsHtml === 'function')
                            ? window.TourmaRoundControls.renderListHeaderControlsHtml(roundObj.roundNumber, rName, 'LOWER', grp.groupId)
                            : '';
                        sHead.innerHTML =
                            '<div class="list-round-title-group">' +
                            '<h4 class="round-header-title">' + rName + '</h4>' +
                            '<span class="list-round-badge">' + (roundObj.matches ? roundObj.matches.length : 0) + ' trận</span>' +
                            '</div>' +
                            listCtrlHtml;
                        rSec.appendChild(sHead);

                        var grid = document.createElement('div');
                        grid.className = 'list-matches-grid';
                        if (roundObj.matches) {
                            roundObj.matches.forEach(function (m) {
                                var liveMatch = grp.matchesMap[m.matchId] || m;
                                var listCard = window.TourmaMatchCard ? window.TourmaMatchCard.createCardElement(liveMatch) : self.createFallbackCard(liveMatch);
                                if (listCard) {
                                    self.attachCardClickListener(listCard, liveMatch);
                                    grid.appendChild(listCard);
                                }
                            });
                        }
                        rSec.appendChild(grid);
                        card.appendChild(rSec);
                    });
                }

                container.appendChild(card);
            });

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
                        roundName: matchData.roundName || matchData.title || (matchData.groupId ? (matchData.groupId + ' - Trận #' + (matchData.matchNumber || matchData.matchId)) : ('Trận #' + (matchData.matchNumber || matchData.matchId))),
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
                groupId: m.groupId || '',
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
                        m.team1 = m.team1 || { name: t1Name };
                        m.team2 = m.team2 || { name: t2Name };
                        m.team1.score = s1;
                        m.team2.score = s2;
                        m.winnerId = winnerId;
                        m.status = 'COMPLETED';

                        // Propagate within this specific group
                        var grp = self.groupsMap[m.groupId];
                        if (!grp) {
                            for (var gk in self.groupsMap) {
                                if (self.groupsMap[gk].matchesMap && self.groupsMap[gk].matchesMap[m.matchId]) {
                                    grp = self.groupsMap[gk];
                                    break;
                                }
                            }
                        }
                        var targetMap = grp ? grp.matchesMap : self.matchesMap;
                        var isT1 = (winnerId === 'team1');
                        if (window.TourmaDoubleElimAlgorithm && typeof window.TourmaDoubleElimAlgorithm.propagateMatchResult === 'function') {
                            window.TourmaDoubleElimAlgorithm.propagateMatchResult(targetMap, m.matchId || matchId, winnerId, isT1);
                        }

                        if (grp) {
                            self.fillMissingPlaceholdersForGroup(grp);
                        }

                        // Re-render UI
                        self.render();
                        self.checkTournamentCompletion();
                    } else {
                        console.error('[TourmaGSL] Error response:', data);
                        alert('Lỗi lưu kết quả: ' + (data.message || 'Không rõ nguyên nhân'));
                    }
                })
                .catch(function (err) {
                    console.error('[TourmaGSL] Error saving match score:', err);
                });
        },

        /**
         * Randomize single round via TourmaRoundControls
         */
        executeRandomRound: function (roundNumber, bracketType, groupId) {
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.executeRandomRound === 'function') {
                window.TourmaRoundControls.executeRandomRound(this, roundNumber, bracketType, groupId);
            }
        },

        /**
         * Reset single round via TourmaRoundControls
         */
        executeResetRound: function (roundNumber, bracketType, groupId) {
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.executeResetRound === 'function') {
                window.TourmaRoundControls.executeResetRound(this, roundNumber, bracketType, groupId);
            }
        },

        /**
         * Randomize all playable matches in a specific group
         */
        executeRandomGroup: function (groupId) {
            var grp = this.groupsMap[groupId];
            if (!grp || !grp.matchesMap) return;
            var self = this;

            for (var mId in grp.matchesMap) {
                var m = grp.matchesMap[mId];
                if (m.status !== 'COMPLETED' && m.status !== 'FINISHED' && m.status !== 'BYE' && !m.isBye) {
                    var t1N = m.team1 ? m.team1.name : '';
                    var t2N = m.team2 ? m.team2.name : '';
                    if (t1N && t2N && !self.isPlaceholder(t1N) && !self.isPlaceholder(t2N)) {
                        var randSlot = (Math.random() < 0.5) ? 1 : 2;
                        self.handleQuickWinner(m.matchId, randSlot, null);
                    }
                }
            }
        },

        /**
         * Reset matches in a specific group
         */
        executeResetGroup: function (groupId) {
            var self = this;
            if (confirm('Bạn có chắc chắn muốn đặt lại kết quả cho ' + groupId + ' không?')) {
                self.resetBracket(true);
            }
        },

        /**
         * Randomize all playable matches across the entire tournament
         */
        executeRandomAll: function () {
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.executeRandomAll === 'function') {
                window.TourmaRoundControls.executeRandomAll(this);
            }
        },

        /**
         * Reset Modal Handlers
         */
        openResetModal: function () {
            var modal = document.getElementById('seResetModalBackdrop') || document.getElementById('deResetModalBackdrop') || document.getElementById('gslResetModalBackdrop');
            if (modal) {
                modal.style.display = 'flex';
                modal.classList.add('show');
            }
        },

        closeResetModal: function () {
            var modal = document.getElementById('seResetModalBackdrop') || document.getElementById('deResetModalBackdrop') || document.getElementById('gslResetModalBackdrop');
            if (modal) {
                modal.style.display = 'none';
                modal.classList.remove('show');
            }
        },

        confirmResetBracket: function () {
            this.closeResetModal();
            this.resetBracket(true);
        },

        /**
         * Reset entire GSL bracket via POST to servlet
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
                .then(function () {
                    window.location.reload();
                })
                .catch(function (err) {
                    console.error('[TourmaGSL] Reset error:', err);
                    window.location.reload();
                });
        },

        /**
         * Draw SVG connector lines for a group's single unified canvas
         */
        drawGroupSvgConnectors: function (groupId) {
            var grp = this.groupsMap[groupId];
            if (!grp) return;
            var safeGId = groupId.replace(/[^a-zA-Z0-9]/g, '_');
            var canvas = document.getElementById('viewportCanvas_' + safeGId);
            var wrapper = document.getElementById('columnsWrapper_' + safeGId);
            if (!canvas || !wrapper) return;

            if (window.TourmaViewport && typeof window.TourmaViewport.drawConnectors === 'function') {
                window.TourmaViewport.drawConnectors(canvas, wrapper, grp.matchesMap, 1.0);
            }
        },

        /**
         * Draw SVG connector lines for all rendered viewports
         */
        drawAllSvgConnectors: function () {
            var self = this;
            var groupKeys = Object.keys(this.groupsMap).sort(self.compareGroupKeys);
            var groupsToDraw = (this.activeGroupId === 'ALL') ? groupKeys : [this.activeGroupId];

            groupsToDraw.forEach(function (gKey) {
                if (self.groupsMap[gKey]) {
                    self.drawGroupSvgConnectors(gKey);
                }
            });
        },

        /**
         * Check if stage or entire tournament has completed
         */
        checkTournamentCompletion: function () {
            var self = this;
            if (window.FinalStagePopup && typeof window.FinalStagePopup.checkAndRender === 'function') {
                window.FinalStagePopup.checkAndRender(
                    this.tournamentId,
                    'GSL',
                    this.matchesMap,
                    this.teamsList,
                    { isCutStage: (this.currentStage === 1), cutTarget: this.cutTarget },
                    function () {
                        self.updateRoundRandomButtons();
                    }
                );
            }
        },

        updateRoundRandomButtons: function () {
            if (window.TourmaRoundControls && typeof window.TourmaRoundControls.updateButtonsState === 'function') {
                window.TourmaRoundControls.updateButtonsState(this);
            }
        },

        filterMatches: function (query) {
            var cards = document.querySelectorAll('.bracket-node-card, .match-card, .match-card-item');
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

        isPlaceholder: function (name) {
            if (name === undefined || name === null) return true;
            var t = String(name).trim();
            if (!t || t === 'BYE' || t === 'TBD' || t === '?') return true;
            if (t.startsWith('W #') || t.startsWith('L #') || t.startsWith('W#') || t.startsWith('L#')) return true;
            if (t.startsWith('Winner ') || t.startsWith('Loser ')) return true;
            if (t === 'Winner UB' || t === 'Winner LB' || t === 'Loser UB' || t === 'Loser LB') return true;
            return false;
        },

        createFallbackCard: function (m) {
            var div = document.createElement('div');
            div.className = 'bracket-node-card';
            div.innerHTML = '<div style="padding:10px;font-size:0.8rem;">' + (m.team1 ? m.team1.name : 'T1') + ' vs ' + (m.team2 ? m.team2.name : 'T2') + '</div>';
            return div;
        }
    };

    // Global listener for tourmaMatchUpdated to ensure DB sync across all components
    document.addEventListener('tourmaMatchUpdated', function (e) {
        var detail = e.detail;
        if (!detail || !detail.matchId) return;
        if (window.TourmaGSL && typeof window.TourmaGSL.saveMatchScore === 'function') {
            window.TourmaGSL.saveMatchScore(
                detail.matchId,
                detail.team1Score !== undefined ? detail.team1Score : detail.score1,
                detail.team2Score !== undefined ? detail.team2Score : detail.score2,
                detail.penalty1,
                detail.penalty2,
                detail.winner
            );
        }
    });

    // Global Export
    window.TourmaGSL = TourmaGSL;
    window.GSLEngine = TourmaGSL;

})(window);
