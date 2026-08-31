/**
 * ============================================================================
 * TOURMA - ROUND ROBIN ALGORITHM ENGINE (round-robin-algorithm.js)
 * High-performance Circle / Berger scheduling algorithm, BYE auto-allocation,
 * Custom W-D-L points calculation, Realtime Live Standings & Tie-breakers.
 * ============================================================================
 */

(function () {
    'use strict';

    window.TourmaRoundRobinAlgorithm = {

        /**
         * Generate complete Round Robin schedule for N teams using the Circle/Polygon Method.
         * @param {Array<string>} teamsList - Array of team names (2 to 24 teams)
         * @param {Object} config - Optional settings: { legsCount: 1, winPoints: 3, drawPoints: 1, lossPoints: 0 }
         * @returns {Object} { rounds: Array, matchesMap: Object, totalMatches: number, config: Object }
         */
        generateRoundRobin: function (teamsList, config) {
            var teams = (teamsList && teamsList.length > 0) ? teamsList.slice(0, 24) : [];
            var numTeams = teams.length;
            if (numTeams < 2) return { rounds: [], matchesMap: {}, totalMatches: 0, config: config || {} };

            var cfg = {
                legsCount: (config && config.legsCount > 0 && config.legsCount <= 10) ? Number(config.legsCount) : 1,
                winPoints: (config && config.winPoints !== undefined) ? Number(config.winPoints) : 3,
                drawPoints: (config && config.drawPoints !== undefined) ? Number(config.drawPoints) : 1,
                lossPoints: (config && config.lossPoints !== undefined) ? Number(config.lossPoints) : 0
            };

            var getItemName = function(item) {
                if (!item) return '';
                if (typeof item === 'object') return item.name || item.rawName || '';
                return String(item);
            };
            var getItemSeed = function(item, defaultSeed) {
                if (item && typeof item === 'object' && item.seed !== undefined && item.seed !== null && item.seed !== '') {
                    return item.seed;
                }
                return defaultSeed;
            };

            var workingTeams = teams.slice();
            var hasBye = (numTeams % 2 !== 0);
            if (hasBye) {
                workingTeams.push('BYE');
            }

            var T = workingTeams.length; // Always even (e.g. 8, 12, 24)
            var roundsPerLeg = T - 1;
            var matchesPerRound = T / 2;

            var rounds = [];
            var matchesMap = {};
            var globalMatchCounter = 1;

            // Generate Legs (1 to K)
            for (var leg = 1; leg <= cfg.legsCount; leg++) {
                var isReverseLeg = (leg % 2 === 0);

                // Initialize Circle array [0, 1, 2, ... T-1]
                var circle = [];
                for (var c = 0; c < T; c++) {
                    circle.push(c);
                }

                for (var r = 0; r < roundsPerLeg; r++) {
                    var absoluteRoundNum = (leg - 1) * roundsPerLeg + (r + 1);
                    var roundTitle = 'Vòng ' + absoluteRoundNum;

                    var roundObj = {
                        roundNumber: absoluteRoundNum,
                        legNumber: leg,
                        roundInLeg: r + 1,
                        title: roundTitle,
                        matches: []
                    };

                    var roundPairings = [];
                    for (var m = 0; m < matchesPerRound; m++) {
                        var homeIdx = circle[m];
                        var awayIdx = circle[T - 1 - m];

                        // For the fixed pivot team (index 0), alternate Home/Away across rounds for fairness
                        if (m === 0 && r % 2 === 1) {
                            var temp = homeIdx;
                            homeIdx = awayIdx;
                            awayIdx = temp;
                        }

                        // In reverse legs (Lượt về), swap Home & Away
                        if (isReverseLeg) {
                            var swap = homeIdx;
                            homeIdx = awayIdx;
                            awayIdx = swap;
                        }

                        var t1Item = workingTeams[homeIdx];
                        var t2Item = workingTeams[awayIdx];
                        var t1Name = getItemName(t1Item);
                        var t2Name = getItemName(t2Item);
                        var t1Seed = getItemSeed(t1Item, homeIdx + 1);
                        var t2Seed = getItemSeed(t2Item, awayIdx + 1);

                        var isT1Bye = (t1Name === 'BYE');
                        var isT2Bye = (t2Name === 'BYE');
                        var isByeMatch = (isT1Bye || isT2Bye);

                        roundPairings.push({
                            t1Name: t1Name,
                            t2Name: t2Name,
                            t1Seed: t1Seed,
                            t2Seed: t2Seed,
                            homeIdx: homeIdx,
                            awayIdx: awayIdx,
                            isT1Bye: isT1Bye,
                            isT2Bye: isT2Bye,
                            isByeMatch: isByeMatch
                        });
                    }

                    // Shuffle match order within this round using Fisher-Yates algorithm
                    // so the order of matches is natural and dynamic instead of fixed
                    for (var s = roundPairings.length - 1; s > 0; s--) {
                        var randIdx = Math.floor(Math.random() * (s + 1));
                        var tempItem = roundPairings[s];
                        roundPairings[s] = roundPairings[randIdx];
                        roundPairings[randIdx] = tempItem;
                    }

                    // Assign sequential match numbers to the shuffled pairings
                    for (var p = 0; p < roundPairings.length; p++) {
                        var pData = roundPairings[p];

                        // If odd number of teams, record resting team and do NOT create a BYE match
                        if (pData.isByeMatch) {
                            roundObj.byeTeam = pData.isT1Bye ? pData.t2Name : pData.t1Name;
                            continue;
                        }

                        var mId = globalMatchCounter++;

                        var match = {
                            id: mId,
                            matchId: mId,
                            matchNumber: mId,
                            roundNumber: absoluteRoundNum,
                            legNumber: leg,
                            status: 'SCHEDULED',
                            team1: {
                                name: pData.t1Name,
                                seed: pData.t1Seed,
                                score: ''
                            },
                            team2: {
                                name: pData.t2Name,
                                seed: pData.t2Seed,
                                score: ''
                            },
                            winnerId: null
                        };

                        matchesMap[mId] = match;
                        roundObj.matches.push(match);
                    }

                    rounds.push(roundObj);

                    // Rotate circle array: keep index 0 fixed, rotate others clockwise
                    var lastElem = circle.pop();
                    circle.splice(1, 0, lastElem);
                }
            }

            return {
                rounds: rounds,
                matchesMap: matchesMap,
                totalMatches: Object.keys(matchesMap).length,
                teamsList: teams,
                config: cfg
            };
        },

        /**
         * Compute Live Standings Table from current matches state.
         * @param {Array<string>} teamsList - List of teams
         * @param {Object} matchesMap - Map of matchId -> Match Object
         * @param {Object} config - Score configuration { winPoints, drawPoints, lossPoints }
         * @returns {Array<Object>} Sorted list of team statistics
         */
        calculateStandings: function (teamsList, matchesMap, config) {
            var teams = (teamsList && teamsList.length > 0) ? teamsList.slice() : [];
            var cfg = {
                winPoints: (config && config.winPoints !== undefined) ? Number(config.winPoints) : 3,
                drawPoints: (config && config.drawPoints !== undefined) ? Number(config.drawPoints) : 1,
                lossPoints: (config && config.lossPoints !== undefined) ? Number(config.lossPoints) : 0
            };

            var getCleanName = function(val) {
                if (!val) return '';
                if (typeof val === 'object') return val.name || val.rawName || '';
                return String(val);
            };
            var getCleanSeed = function(val, defSeed) {
                if (val && typeof val === 'object' && val.seed !== undefined && val.seed !== null && val.seed !== '') {
                    return val.seed;
                }
                return defSeed;
            };

            var stats = {};
            var ensureTeamStat = function (rawVal, seedNum) {
                var name = getCleanName(rawVal);
                if (!name || name === 'BYE' || name === '[object Object]' || stats[name]) return;
                var seed = getCleanSeed(rawVal, seedNum || (Object.keys(stats).length + 1));
                stats[name] = {
                    team: name,
                    seed: seed,
                    played: 0,
                    won: 0,
                    drawn: 0,
                    lost: 0,
                    goalsFor: 0,
                    goalsAgainst: 0,
                    goalDifference: 0,
                    points: 0,
                    form: [], // 'W', 'D', 'L'
                    h2hPoints: {} // against specific teams
                };
            };

            for (var i = 0; i < teams.length; i++) {
                ensureTeamStat(teams[i], i + 1);
            }

            if (matchesMap) {
                var keys = Object.keys(matchesMap);

                // First pass: ensure all participating teams exist in stats map
                for (var j = 0; j < keys.length; j++) {
                    var mObj = matchesMap[keys[j]];
                    if (mObj) {
                        if (mObj.team1 && mObj.team1.name) ensureTeamStat(mObj.team1.name);
                        if (mObj.team2 && mObj.team2.name) ensureTeamStat(mObj.team2.name);
                    }
                }

                // Second pass: calculate statistics for all completed/scored matches
                for (var k = 0; k < keys.length; k++) {
                    var m = matchesMap[keys[k]];
                    if (!m) continue;

                    var isDone = (m.status === 'COMPLETED' || m.status === 'done' || (m.team1 && m.team1.score !== '' && m.team2 && m.team2.score !== ''));
                    if (!isDone) continue;

                    var t1 = getCleanName(m.team1 ? m.team1.name : '');
                    var t2 = getCleanName(m.team2 ? m.team2.name : '');

                    if (!t1 || !t2 || t1 === 'BYE' || t2 === 'BYE' || t1 === '[object Object]' || t2 === '[object Object]') continue;
                    if (!stats[t1] || !stats[t2]) continue;

                    var s1 = (m.team1 && m.team1.score !== '' && m.team1.score !== null && !isNaN(Number(m.team1.score))) ? Number(m.team1.score) : null;
                    var s2 = (m.team2 && m.team2.score !== '' && m.team2.score !== null && !isNaN(Number(m.team2.score))) ? Number(m.team2.score) : null;

                    if (s1 === null || s2 === null) continue;

                    var st1 = stats[t1];
                    var st2 = stats[t2];

                    st1.played++;
                    st2.played++;

                    st1.goalsFor += s1;
                    st1.goalsAgainst += s2;
                    st2.goalsFor += s2;
                    st2.goalsAgainst += s1;

                    if (s1 > s2) {
                        st1.won++;
                        st1.points += cfg.winPoints;
                        st1.form.push('W');

                        st2.lost++;
                        st2.points += cfg.lossPoints;
                        st2.form.push('L');

                        st1.h2hPoints[t2] = (st1.h2hPoints[t2] || 0) + cfg.winPoints;
                    } else if (s1 < s2) {
                        st2.won++;
                        st2.points += cfg.winPoints;
                        st2.form.push('W');

                        st1.lost++;
                        st1.points += cfg.lossPoints;
                        st1.form.push('L');

                        st2.h2hPoints[t1] = (st2.h2hPoints[t1] || 0) + cfg.winPoints;
                    } else {
                        // Draw
                        st1.drawn++;
                        st1.points += cfg.drawPoints;
                        st1.form.push('D');

                        st2.drawn++;
                        st2.points += cfg.drawPoints;
                        st2.form.push('D');

                        st1.h2hPoints[t2] = (st1.h2hPoints[t2] || 0) + cfg.drawPoints;
                        st2.h2hPoints[t1] = (st2.h2hPoints[t1] || 0) + cfg.drawPoints;
                    }
                }
            }

            var standingsList = [];
            var statKeys = Object.keys(stats);
            for (var sk = 0; sk < statKeys.length; sk++) {
                var item = stats[statKeys[sk]];
                if (!item || !item.team || item.team === 'BYE' || item.team === '[object Object]') continue;
                item.goalDifference = item.goalsFor - item.goalsAgainst;
                if (item.form.length > 5) {
                    item.form = item.form.slice(item.form.length - 5);
                }
                standingsList.push(item);
            }

            // Tie-breaker Sorting:
            // 1. Points DESC
            // 2. Goal Difference DESC
            // 3. Goals For DESC
            // 4. Won Matches DESC
            // 5. Head-to-head
            // 6. Original Seed ASC
            standingsList.sort(function (a, b) {
                if (b.points !== a.points) return b.points - a.points;
                if (b.goalDifference !== a.goalDifference) return b.goalDifference - a.goalDifference;
                if (b.goalsFor !== a.goalsFor) return b.goalsFor - a.goalsFor;
                if (b.won !== a.won) return b.won - a.won;

                // Head to head check
                var h2hA = a.h2hPoints[b.team] || 0;
                var h2hB = b.h2hPoints[a.team] || 0;
                if (h2hA !== h2hB) return h2hB - h2hA;

                return a.seed - b.seed;
            });

            // Assign Position (Rank)
            for (var p = 0; p < standingsList.length; p++) {
                standingsList[p].rank = p + 1;
            }

            return standingsList;
        }
    };

})();
