/**
 * ============================================================================
 * TOURMA - BRACKET TOURNAMENT ALGORITHM ENGINE (bracket-algorithm.js)
 * Pure tournament data structures, standard seeding pairs, BYE allocation,
 * contiguous match numbering, and cascading downstream reset graph engine.
 * ============================================================================
 */

(function () {
    'use strict';

    window.TourmaBracketAlgorithm = {

        /**
         * Calculate next power of 2 for N teams (e.g. 18 -> 32, 8 -> 8, 12 -> 16)
         */
        calculatePowerOfTwo: function (numTeams) {
            var pow2 = 2;
            while (pow2 < numTeams) {
                pow2 *= 2;
            }
            return pow2;
        },

        /**
         * Generate Standard Seeding Pairs: (1 vs N, 4 vs 5, 2 vs N-1, 3 vs N-2...)
         * Ensures top seeds meet as late as possible according to international bracket standards.
         */
        generateSeedPairs: function (pow2) {
            var isPow2 = (pow2 > 0 && (pow2 & (pow2 - 1)) === 0);
            if (!isPow2 && pow2 % 2 === 0) {
                var pairs = [];
                for (var i = 1; i <= pow2 / 2; i++) {
                    pairs.push([i, pow2 - i + 1]);
                }
                return pairs;
            }

            var seeds = [1, 2];
            while (seeds.length < pow2) {
                var nextLen = seeds.length * 2;
                var sum = nextLen + 1;
                var next = [];
                for (var i = 0; i < seeds.length; i++) {
                    var s = seeds[i];
                    var comp = sum - s;
                    if (i % 2 === 0) {
                        next.push(s);
                        next.push(comp);
                    } else {
                        next.push(comp);
                        next.push(s);
                    }
                }
                seeds = next;
            }

            var pairs = [];
            for (var k = 0; k < seeds.length; k += 2) {
                var a = seeds[k];
                var b = seeds[k + 1];
                pairs.push([Math.min(a, b), Math.max(a, b)]);
            }
            return pairs;
        },

        /**
         * Standard International English Round Names (Dynamically supports up to Round of 1024 and beyond)
         */
        getRoundTitle: function (r, totalRounds, isCutStage) {
            var diff = totalRounds - r;
            var teamsInRound = Math.pow(2, diff + 1);

            if (isCutStage) {
                return 'Round ' + r;
            }

            if (diff === 0) return 'Final';
            if (diff === 1) return 'Semi Final';
            if (diff === 2) return 'Quarter Final';

            return 'Round of ' + teamsInRound;
        },

        /**
         * Find parent match feeding into target match slot (1 or 2)
         */
        findParentMatch: function (matchesMap, targetMatchId, slot) {
            if (!matchesMap) return null;
            var keys = Object.keys(matchesMap);
            for (var i = 0; i < keys.length; i++) {
                var m = matchesMap[keys[i]];
                if (m.nextMatchId === targetMatchId && (m.nextMatchSlot || 1) === slot) {
                    return m;
                }
            }
            return null;
        },

        /**
         * Generate Complete Single Elimination Structure for N teams with standard BYE placement
         */
        generateSingleElimination: function (teamsList, cutTarget) {
            var numTeams = teamsList ? teamsList.length : 0;
            if (numTeams < 2) return { roundsList: [], matchesMap: {} };

            var cTarget = (cutTarget && parseInt(cutTarget, 10) > 1) ? parseInt(cutTarget, 10) : 0;
            if (cTarget <= 0 || cTarget >= numTeams) {
                cTarget = 1;
            }

            var roundsList = [];
            var matchesMap = {};
            var internalIdCounter = 1;
            var previousRoundMatchIds = null;

            var currentTeamPool = [];
            for (var t = 0; t < numTeams; t++) {
                var item = teamsList[t];
                var tName = (typeof item === 'object' && item) ? (item.name || item.rawName || '') : (item || '');
                var tSeed = (t + 1);
                if (typeof item === 'object' && item && item.seed !== undefined && item.seed !== null && item.seed !== '') {
                    tSeed = item.seed;
                }
                currentTeamPool.push({ seed: tSeed, name: tName });
            }

            var isCutStage = (cTarget > 1 && cTarget < numTeams);

            if (isCutStage) {
                // =========================================================================
                // Universal K-Branch Qualifying Cut Algorithm
                // Guarantees:
                // 1. Top K seeds are distributed across K separate sub-brackets (Zero early head-to-head).
                //    If no upsets occur, all top K seeds qualify 100%!
                // 2. Strict intra-branch connections (ZERO CROSSING LINES / Broken lines).
                // 3. Exactly K matches in final stopping round producing exactly K qualifiers.
                // =========================================================================
                // 1. Sort teams by seed ascending
                var sortedTeams = currentTeamPool.slice().sort(function (a, b) {
                    var sA = (a.originalSeed !== undefined && a.originalSeed !== null && a.originalSeed !== '') ? Number(a.originalSeed)
                           : ((a.seed !== undefined && a.seed !== null && a.seed !== '') ? Number(a.seed) : 9999);
                    var sB = (b.originalSeed !== undefined && b.originalSeed !== null && b.originalSeed !== '') ? Number(b.originalSeed)
                           : ((b.seed !== undefined && b.seed !== null && b.seed !== '') ? Number(b.seed) : 9999);
                    return sA - sB;
                });

                // 2. Distribute teams into K branches via serpentine seeding
                var branches = [];
                for (var i = 0; i < cTarget; i++) branches.push([]);

                var forward = true;
                var tIdx = 0;
                while (tIdx < numTeams) {
                    if (forward) {
                        for (var b = 0; b < cTarget && tIdx < numTeams; b++) {
                            branches[b].push(sortedTeams[tIdx++]);
                        }
                    } else {
                        for (var b = cTarget - 1; b >= 0 && tIdx < numTeams; b--) {
                            branches[b].push(sortedTeams[tIdx++]);
                        }
                    }
                    forward = !forward;
                }

                // Determine branch max size & power-of-2 depth
                var maxM = 0;
                for (var b = 0; b < cTarget; b++) {
                    if (branches[b].length > maxM) maxM = branches[b].length;
                }

                var pMax = 1;
                while (pMax < maxM) pMax *= 2;
                if (pMax < 2) pMax = 2;
                var totalRounds = Math.round(Math.log(pMax) / Math.log(2));

                var branchSeedPairs = this.generateSeedPairs(pMax);

                // Collect matches per round: roundMatches[r] array
                var roundMatches = [];
                for (var r = 0; r <= totalRounds; r++) roundMatches.push([]);

                for (var bIdx = 0; bIdx < cTarget; bIdx++) {
                    var bTeams = branches[bIdx];
                    var m = bTeams.length;
                    var currentRoundMatches = pMax / 2;
                    var branchPrevIds = [];

                    for (var r = 1; r <= totalRounds; r++) {
                        var branchCurIds = [];

                        for (var i = 0; i < currentRoundMatches; i++) {
                            var mId = internalIdCounter++;
                            var match;

                            if (r === 1) {
                                var pair = branchSeedPairs[i];
                                var s1Rel = pair[0];
                                var s2Rel = pair[1];
                                var t1 = (s1Rel <= m) ? bTeams[s1Rel - 1] : { name: 'BYE', seed: '' };
                                var t2 = (s2Rel <= m) ? bTeams[s2Rel - 1] : { name: 'BYE', seed: '' };
                                var isBye = (t1.name === 'BYE' || t2.name === 'BYE');

                                match = {
                                    matchId: mId,
                                    matchNumber: mId,
                                    roundNumber: 1,
                                    branchIndex: bIdx,
                                    status: isBye ? 'COMPLETED' : 'SCHEDULED',
                                    team1: { name: t1.name, seed: (t1.name === 'BYE' ? '' : (t1.seed !== undefined && t1.seed !== null && t1.seed !== '' ? t1.seed : s1Rel)), score: '' },
                                    team2: { name: t2.name, seed: (t2.name === 'BYE' ? '' : (t2.seed !== undefined && t2.seed !== null && t2.seed !== '' ? t2.seed : s2Rel)), score: '' },
                                    winnerId: isBye ? (t1.name === 'BYE' ? 'team2' : 'team1') : null,
                                    nextMatchId: null,
                                    nextMatchSlot: 1,
                                    isBye: isBye
                                };
                            } else {
                                match = {
                                    matchId: mId,
                                    matchNumber: mId,
                                    roundNumber: r,
                                    branchIndex: bIdx,
                                    status: 'SCHEDULED',
                                    team1: { name: 'TBD', seed: '', score: '' },
                                    team2: { name: 'TBD', seed: '', score: '' },
                                    winnerId: null,
                                    nextMatchId: null,
                                    nextMatchSlot: 1,
                                    isBye: false
                                };
                            }

                            matchesMap[mId] = match;
                            roundMatches[r].push(match);
                            branchCurIds.push(mId);
                        }

                        if (branchPrevIds.length > 0) {
                            for (var k = 0; k < branchPrevIds.length; k++) {
                                var pId = branchPrevIds[k];
                                var nId = branchCurIds[Math.floor(k / 2)];
                                if (matchesMap[pId] && nId) {
                                    matchesMap[pId].nextMatchId = nId;
                                    matchesMap[pId].nextMatchSlot = (k % 2 === 0) ? 1 : 2;
                                }
                            }
                        }

                        branchPrevIds = branchCurIds;
                        currentRoundMatches = Math.floor(currentRoundMatches / 2);
                    }
                }

                for (var r = 1; r <= totalRounds; r++) {
                    var rTitle = this.getRoundTitle(r, totalRounds, true);
                    roundsList.push({ roundNumber: r, title: rTitle, matches: roundMatches[r] });
                }

                this.renumberMatchesContiguously(roundsList, matchesMap);
                return { teamsList: teamsList, roundsList: roundsList, matchesMap: matchesMap };
            }

            // Standard Championship Bracket (Stage 2 OR Single Stage Full Tournament)
            var bracketSize = Math.pow(2, Math.ceil(Math.log2(numTeams)));
            var totalEstimatedRounds = Math.ceil(Math.log2(bracketSize));

            var currentTeamsInRound = bracketSize;
            var currentRoundNumber = 1;

            while (currentTeamsInRound > cTarget) {
                var roundMatchesCount = currentTeamsInRound / 2;
                var roundTitle = this.getRoundTitle(currentRoundNumber, totalEstimatedRounds, (cTarget > 1));
                var roundObj = { roundNumber: currentRoundNumber, title: roundTitle, matches: [] };

                var nextRoundMatchIds = [];

                if (currentRoundNumber === 1) {
                    var seedPairs = this.generateSeedPairs(bracketSize);
                    for (var i = 0; i < roundMatchesCount; i++) {
                        var pair = seedPairs[i];
                        var s1 = pair[0];
                        var s2 = pair[1];

                        var t1 = (s1 <= numTeams) ? currentTeamPool[s1 - 1] : { name: 'BYE', seed: '' };
                        var t2 = (s2 <= numTeams) ? currentTeamPool[s2 - 1] : { name: 'BYE', seed: '' };
                        var isBye = (t1.name === 'BYE' || t2.name === 'BYE');

                        var mId = internalIdCounter++;
                        var match = {
                            matchId: mId,
                            matchNumber: mId,
                            roundNumber: currentRoundNumber,
                            status: isBye ? 'COMPLETED' : 'SCHEDULED',
                            team1: { name: t1.name, seed: (t1.name === 'BYE' ? '' : (t1.seed !== undefined && t1.seed !== null && t1.seed !== '' ? t1.seed : s1)), score: '' },
                            team2: { name: t2.name, seed: (t2.name === 'BYE' ? '' : (t2.seed !== undefined && t2.seed !== null && t2.seed !== '' ? t2.seed : s2)), score: '' },
                            winnerId: isBye ? (t1.name === 'BYE' ? 'team2' : 'team1') : null,
                            nextMatchId: null,
                            nextMatchSlot: (i % 2 === 0) ? 1 : 2
                        };
                        matchesMap[mId] = match;
                        roundObj.matches.push(match);
                        nextRoundMatchIds.push(mId);
                    }
                } else {
                    for (var i = 0; i < roundMatchesCount; i++) {
                        var mId = internalIdCounter++;
                        var p1Id = previousRoundMatchIds ? previousRoundMatchIds[2 * i] : null;
                        var p2Id = previousRoundMatchIds ? previousRoundMatchIds[2 * i + 1] : null;

                        var t1Name = 'TBD';
                        var t2Name = 'TBD';
                        var s1 = '';
                        var s2 = '';

                        if (p1Id && matchesMap[p1Id]) {
                            matchesMap[p1Id].nextMatchId = mId;
                            matchesMap[p1Id].nextMatchSlot = 1;
                            if (matchesMap[p1Id].winnerId) {
                                var w1 = (matchesMap[p1Id].winnerId === 'team1') ? matchesMap[p1Id].team1 : matchesMap[p1Id].team2;
                                t1Name = (w1 && w1.name) ? w1.name : ('W #' + (matchesMap[p1Id].matchNumber || p1Id));
                                s1 = (w1 && w1.seed !== undefined && w1.seed !== null) ? w1.seed : '';
                            } else {
                                t1Name = 'W #' + (matchesMap[p1Id].matchNumber || p1Id);
                                s1 = '';
                            }
                        }
                        if (p2Id && matchesMap[p2Id]) {
                            matchesMap[p2Id].nextMatchId = mId;
                            matchesMap[p2Id].nextMatchSlot = 2;
                            if (matchesMap[p2Id].winnerId) {
                                var w2 = (matchesMap[p2Id].winnerId === 'team1') ? matchesMap[p2Id].team1 : matchesMap[p2Id].team2;
                                t2Name = (w2 && w2.name) ? w2.name : ('W #' + (matchesMap[p2Id].matchNumber || p2Id));
                                s2 = (w2 && w2.seed !== undefined && w2.seed !== null) ? w2.seed : '';
                            } else {
                                t2Name = 'W #' + (matchesMap[p2Id].matchNumber || p2Id);
                                s2 = '';
                            }
                        }

                        var match = {
                            matchId: mId,
                            matchNumber: mId,
                            roundNumber: currentRoundNumber,
                            status: 'SCHEDULED',
                            team1: { name: t1Name, seed: s1, score: '' },
                            team2: { name: t2Name, seed: s2, score: '' },
                            winnerId: null,
                            nextMatchId: null,
                            nextMatchSlot: (i % 2 === 0) ? 1 : 2
                        };
                        matchesMap[mId] = match;
                        roundObj.matches.push(match);
                        nextRoundMatchIds.push(mId);
                    }
                }
                previousRoundMatchIds = nextRoundMatchIds;
                currentTeamsInRound = Math.floor(currentTeamsInRound / 2);

                roundsList.push(roundObj);
                currentRoundNumber++;
            }

            this.renumberMatchesContiguously(roundsList, matchesMap);
            return { teamsList: teamsList, roundsList: roundsList, matchesMap: matchesMap };
        },

        /**
         * Renumber all playable matches strictly sequentially (1, 2, 3...) and update placeholders
         */
        renumberMatchesContiguously: function (roundsList, matchesMap) {
            if (!roundsList || !matchesMap) return;

            var counter = 1;
            for (var r = 0; r < roundsList.length; r++) {
                var roundObj = roundsList[r];
                for (var m = 0; m < roundObj.matches.length; m++) {
                    var match = roundObj.matches[m];
                    var t1 = match.team1 ? match.team1.name : '';
                    var t2 = match.team2 ? match.team2.name : '';
                    var isBye = (match.isBye === true || t1 === 'BYE' || t2 === 'BYE' || (match.team1 && match.team1.isBye) || (match.team2 && match.team2.isBye));

                    if (isBye) {
                        match.isBye = true;
                        match.matchNumber = null;
                        if (t1 === 'BYE') {
                            match.winnerId = 'team2';
                            match.status = 'COMPLETED';
                        } else if (t2 === 'BYE') {
                            match.winnerId = 'team1';
                            match.status = 'COMPLETED';
                        }
                    } else {
                        match.isBye = false;
                        match.matchNumber = counter++;
                    }
                }
            }

            // Update placeholders in subsequent rounds to reference parent matchNumber or propagate BYE winner
            for (var r = 1; r < roundsList.length; r++) {
                var roundObj = roundsList[r];
                for (var m = 0; m < roundObj.matches.length; m++) {
                    var match = roundObj.matches[m];

                    var parent1 = this.findParentMatch(matchesMap, match.matchId, 1);
                    var parent2 = this.findParentMatch(matchesMap, match.matchId, 2);

                    if (parent1) {
                        if (parent1.winnerId) {
                            var w1 = (parent1.winnerId === 'team1') ? parent1.team1 : parent1.team2;
                            if (w1 && w1.name && w1.name !== 'BYE') {
                                match.team1.name = w1.name;
                                match.team1.seed = (w1.seed !== undefined && w1.seed !== null) ? w1.seed : '';
                            }
                        } else if (parent1.matchNumber) {
                            match.team1.name = 'W #' + parent1.matchNumber;
                        }
                    }
                    if (parent2) {
                        if (parent2.winnerId) {
                            var w2 = (parent2.winnerId === 'team1') ? parent2.team1 : parent2.team2;
                            if (w2 && w2.name && w2.name !== 'BYE') {
                                match.team2.name = w2.name;
                                match.team2.seed = (w2.seed !== undefined && w2.seed !== null) ? w2.seed : '';
                            }
                        } else if (parent2.matchNumber) {
                            match.team2.name = 'W #' + parent2.matchNumber;
                        }
                    }
                }
            }
        },

        /**
         * Find the parent feeder match for a given target match and slot (1 or 2)
         */
        findParentMatch: function (matchesMap, childMatchId, slot) {
            if (!matchesMap || !childMatchId) return null;
            var keys = Object.keys(matchesMap);
            for (var i = 0; i < keys.length; i++) {
                var m = matchesMap[keys[i]];
                if (m && String(m.nextMatchId) === String(childMatchId)) {
                    if (slot === undefined || m.nextMatchSlot === slot) {
                        return m;
                    }
                }
            }
            return null;
        },

        /**
         * Propagate winning team to downstream match and reset all subsequent matches downstream
         */
        propagateAndResetDownstream: function (matchesMap, matchId, winnerId, isT1Winner) {
            var currMatch = matchesMap[matchId];
            if (!currMatch || !currMatch.nextMatchId) return;

            var nextMatchId = currMatch.nextMatchId;
            var nextMatch = matchesMap[nextMatchId];
            if (!nextMatch) return;

            var slot = currMatch.nextMatchSlot || 1;
            var winnerName = isT1Winner ? currMatch.team1.name : currMatch.team2.name;
            var winnerSeed = isT1Winner ? currMatch.team1.seed : currMatch.team2.seed;

            if (slot === 1) {
                nextMatch.team1.name = winnerName;
                nextMatch.team1.seed = winnerSeed;
            } else {
                nextMatch.team2.name = winnerName;
                nextMatch.team2.seed = winnerSeed;
            }

            // Reset immediate downstream match
            nextMatch.team1.score = '';
            nextMatch.team2.score = '';
            nextMatch.winnerId = null;
            nextMatch.status = 'SCHEDULED';

            // Cascade reset all descendants
            this.cascadeResetPlaceholders(matchesMap, nextMatchId);
        },

        /**
         * Recursively reset subsequent descendant matches to 'W #...' placeholders and clear scores
         */
        cascadeResetPlaceholders: function (matchesMap, resetMatchId) {
            var curr = matchesMap[resetMatchId];
            if (!curr || !curr.nextMatchId) return;

            var nextMatch = matchesMap[curr.nextMatchId];
            if (!nextMatch) return;

            var slot = curr.nextMatchSlot || 1;
            var placeholderName = 'W #' + (curr.matchNumber || resetMatchId);

            if (slot === 1) {
                nextMatch.team1.name = placeholderName;
                nextMatch.team1.seed = '';
            } else {
                nextMatch.team2.name = placeholderName;
                nextMatch.team2.seed = '';
            }

            nextMatch.team1.score = '';
            nextMatch.team2.score = '';
            nextMatch.winnerId = null;
            nextMatch.status = 'SCHEDULED';

            this.cascadeResetPlaceholders(matchesMap, curr.nextMatchId);
        },

        /**
         * Determine if Round 1 BYE ratio exceeds the threshold (default > 40%)
         */
        shouldShowRound1Byes: function (roundsList, thresholdRatio) {
            if (!roundsList || roundsList.length === 0) return true;
            var threshold = (thresholdRatio !== undefined) ? thresholdRatio : 0.50;
            var round1 = roundsList[0];
            var totalR1 = round1 && round1.matches ? round1.matches.length : 0;
            if (totalR1 === 0) return true;

            var byeCount = 0;
            for (var i = 0; i < totalR1; i++) {
                var m = round1.matches[i];
                var t1 = m.team1 ? m.team1.name : '';
                var t2 = m.team2 ? m.team2.name : '';
                if (t1 === 'BYE' || t2 === 'BYE' || m.isBye) {
                    byeCount++;
                }
            }
            var ratio = byeCount / totalR1;
            // Few BYE matches (< 50%): SHOW BYEs (true)
            // Many BYE matches (>= 50%): HIDE BYEs (false)
            return ratio < threshold;
        },

        /**
         * Filter matches for List View based on Round 1 BYE threshold rules (<=50% BYE shows Round 1 BYEs, >50% hides)
         */
        filterMatchesForListView: function (roundsList) {
            if (!roundsList) return [];
            var showRound1Byes = this.shouldShowRound1Byes(roundsList, 0.50);
            var result = [];

            for (var r = 0; r < roundsList.length; r++) {
                var roundObj = roundsList[r];
                var isRound1 = (roundObj.roundNumber === 1 || r === 0);

                var matchesToRender = roundObj.matches.filter(function (m) {
                    var t1 = m.team1 ? m.team1.name : '';
                    var t2 = m.team2 ? m.team2.name : '';
                    var isBye = (t1 === 'BYE' || t2 === 'BYE' || m.isBye);
                    if (isBye) {
                        return isRound1 && showRound1Byes;
                    }
                    return true;
                });

                if (matchesToRender.length > 0) {
                    result.push({
                        roundNumber: roundObj.roundNumber,
                        title: roundObj.title,
                        matches: matchesToRender
                    });
                }
            }

            return result;
        }
    };

})();
