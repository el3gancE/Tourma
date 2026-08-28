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
            var rounds = Math.log2(pow2) - 1;
            var pls = [1, 2];

            for (var i = 0; i < rounds; i++) {
                var nextPls = [];
                var sum = Math.pow(2, i + 2) + 1;
                for (var j = 0; j < pls.length; j++) {
                    nextPls.push(pls[j]);
                    nextPls.push(sum - pls[j]);
                }
                pls = nextPls;
            }

            var pairs = [];
            for (var k = 0; k < pls.length; k += 2) {
                pairs.push([pls[k], pls[k + 1]]);
            }
            return pairs;
        },

        /**
         * Standard International English Round Names (Dynamically supports up to Round of 1024 and beyond)
         */
        getRoundTitle: function (r, totalRounds) {
            var diff = totalRounds - r;
            if (diff === 0) return 'Finals';
            if (diff === 1) return 'Semi Finals';
            if (diff === 2) return 'Quarter Finals';

            var teamsInRound = Math.pow(2, diff + 1);
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
        generateSingleElimination: function (teamsList) {
            var numTeams = teamsList ? teamsList.length : 0;
            if (numTeams < 2) return { roundsList: [], matchesMap: {} };

            var pow2 = this.calculatePowerOfTwo(numTeams);
            var totalRounds = Math.log2(pow2);
            var internalIdCounter = 1;
            var roundsList = [];
            var matchesMap = {};

            // 1. Generate Round 1 (First Round with standard BYE placement)
            var round1MatchesCount = pow2 / 2;
            var round1 = { roundNumber: 1, title: this.getRoundTitle(1, totalRounds), matches: [] };
            var seedPairs = this.generateSeedPairs(pow2);

            for (var i = 0; i < round1MatchesCount; i++) {
                var pair = seedPairs[i];
                var s1 = pair[0];
                var s2 = pair[1];

                var t1Name = (s1 <= numTeams) ? teamsList[s1 - 1] : 'BYE';
                var t2Name = (s2 <= numTeams) ? teamsList[s2 - 1] : 'BYE';
                var isBye = (t1Name === 'BYE' || t2Name === 'BYE');
                var mId = internalIdCounter++;

                var match = {
                    matchId: mId,
                    matchNumber: null, // Computed contiguously
                    roundNumber: 1,
                    status: isBye ? 'COMPLETED' : 'SCHEDULED',
                    team1: { name: t1Name, seed: (t1Name === 'BYE' ? '' : s1), score: '' },
                    team2: { name: t2Name, seed: (t2Name === 'BYE' ? '' : s2), score: '' },
                    winnerId: null,
                    nextMatchId: null,
                    nextMatchSlot: (i % 2 === 0) ? 1 : 2
                };

                // Handle BYE auto-advancement
                if (t1Name === 'BYE' && t2Name !== 'BYE') {
                    match.winnerId = 'team2';
                } else if (t2Name === 'BYE' && t1Name !== 'BYE') {
                    match.winnerId = 'team1';
                }

                matchesMap[mId] = match;
                round1.matches.push(match);
            }
            roundsList.push(round1);

            // 2. Generate Subsequent Rounds (Round 2 to Finals)
            var currentRoundMatches = round1MatchesCount;
            var previousRoundMatchIds = round1.matches.map(function (m) { return m.matchId; });

            for (var r = 2; r <= totalRounds; r++) {
                var roundMatchesCount = currentRoundMatches / 2;
                var roundObj = { roundNumber: r, title: this.getRoundTitle(r, totalRounds), matches: [] };
                var nextMatchIds = [];

                for (var j = 0; j < roundMatchesCount; j++) {
                    var mId = internalIdCounter++;
                    var parent1Id = previousRoundMatchIds[j * 2];
                    var parent2Id = previousRoundMatchIds[j * 2 + 1];

                    // Link parent matches to this next match
                    if (matchesMap[parent1Id]) matchesMap[parent1Id].nextMatchId = mId;
                    if (matchesMap[parent2Id]) matchesMap[parent2Id].nextMatchId = mId;

                    var parent1 = matchesMap[parent1Id];
                    var parent2 = matchesMap[parent2Id];

                    var t1Placeholder = (parent1 && parent1.winnerId) ?
                        (parent1.winnerId === 'team1' ? parent1.team1.name : parent1.team2.name) : ('W #' + parent1Id);

                    var t2Placeholder = (parent2 && parent2.winnerId) ?
                        (parent2.winnerId === 'team1' ? parent2.team1.name : parent2.team2.name) : ('W #' + parent2Id);

                    var t1Seed = (parent1 && parent1.winnerId) ?
                        (parent1.winnerId === 'team1' ? parent1.team1.seed : parent1.team2.seed) : '';
                    var t2Seed = (parent2 && parent2.winnerId) ?
                        (parent2.winnerId === 'team1' ? parent2.team1.seed : parent2.team2.seed) : '';

                    var mObj = {
                        matchId: mId,
                        matchNumber: null, // Computed contiguously
                        roundNumber: r,
                        status: 'SCHEDULED',
                        team1: { name: t1Placeholder, seed: t1Seed, score: '' },
                        team2: { name: t2Placeholder, seed: t2Seed, score: '' },
                        winnerId: null,
                        nextMatchId: null,
                        nextMatchSlot: (j % 2 === 0) ? 1 : 2
                    };

                    matchesMap[mId] = mObj;
                    roundObj.matches.push(mObj);
                    nextMatchIds.push(mId);
                }

                roundsList.push(roundObj);
                previousRoundMatchIds = nextMatchIds;
                currentRoundMatches = roundMatchesCount;
            }

            // 3. Renumber all playable matches strictly sequentially without skips
            this.renumberMatchesContiguously(roundsList, matchesMap);

            return { roundsList: roundsList, matchesMap: matchesMap };
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
                    var isBye = (t1 === 'BYE' || t2 === 'BYE');

                    if (isBye) {
                        match.matchNumber = null;
                    } else {
                        match.matchNumber = counter++;
                    }
                }
            }

            // Update placeholders in subsequent rounds to reference parent matchNumber
            for (var r = 1; r < roundsList.length; r++) {
                var roundObj = roundsList[r];
                for (var m = 0; m < roundObj.matches.length; m++) {
                    var match = roundObj.matches[m];

                    var parent1 = this.findParentMatch(matchesMap, match.matchId, 1);
                    var parent2 = this.findParentMatch(matchesMap, match.matchId, 2);

                    if (parent1 && !parent1.winnerId) {
                        if (parent1.matchNumber) {
                            match.team1.name = 'W #' + parent1.matchNumber;
                        }
                    }
                    if (parent2 && !parent2.winnerId) {
                        if (parent2.matchNumber) {
                            match.team2.name = 'W #' + parent2.matchNumber;
                        }
                    }
                }
            }
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
            // Few BYE matches (<= 50%): SHOW BYEs (true)
            // Too many BYE matches (> 50%): HIDE BYEs (false)
            // Exactly 50%: SHOW BYEs (true)
            return ratio <= threshold;
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
