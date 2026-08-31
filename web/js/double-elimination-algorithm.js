/**
 * ============================================================================
 * TOURMA - DOUBLE ELIMINATION TOURNAMENT ALGORITHM ENGINE (double-elimination-algorithm.js)
 * High-precision bracket generator for Upper Bracket (with Grand Finals) & Lower Bracket,
 * including drop-down mappings, standard BYE propagation, contiguous match numbering,
 * and Grand Finals Bracket Reset triggers.
 * ============================================================================
 */

(function () {
    'use strict';

    window.TourmaDoubleElimAlgorithm = {

        /**
         * Calculate next power of 2 for N teams
         */
        calculatePowerOfTwo: function (numTeams) {
            var pow2 = 2;
            while (pow2 < numTeams) {
                pow2 *= 2;
            }
            return Math.max(4, pow2);
        },

        /**
         * Standard International Seeding Pairs
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
         * Standard Round Titles for Upper Bracket: UB Round 1, UB Round 2, ... UB Finals
         */
        getUpperRoundTitle: function (r, totalUbRounds) {
            if (r === totalUbRounds) return 'UB Finals';
            return 'UB Round ' + r;
        },

        /**
         * Standard Round Titles for Lower Bracket: LB Round 1, LB Round 2, ... LB Finals
         */
        getLowerRoundTitle: function (r, totalLbRounds) {
            if (r === totalLbRounds) return 'LB Finals';
            return 'LB Round ' + r;
        },

        /**
         * Generate Complete Double Elimination Structure
         * Upper Bracket includes Grand Finals at the end.
         */
        generateDoubleElimination: function (teamsList) {
            var numTeams = teamsList ? teamsList.length : 0;
            if (numTeams < 2) {
                return {
                    upperRounds: [],
                    lowerRounds: [],
                    grandFinalsRound: null,
                    matchesMap: {}
                };
            }

            var pow2 = this.calculatePowerOfTwo(numTeams);
            var totalUbRounds = Math.log2(pow2);
            var totalLbRounds = (totalUbRounds - 1) * 2;

            var internalIdCounter = 1;
            var matchesMap = {};
            var upperRounds = [];
            var lowerRounds = [];

            // ====================================================================
            // 1. GENERATE UPPER BRACKET (UB)
            // ====================================================================
            var ubRound1MatchesCount = pow2 / 2;
            var ubRound1 = {
                roundNumber: 1,
                bracketType: 'UPPER',
                title: this.getUpperRoundTitle(1, totalUbRounds),
                matches: []
            };

            var seedPairs = this.generateSeedPairs(pow2);

            for (var i = 0; i < ubRound1MatchesCount; i++) {
                var pair = seedPairs[i];
                var s1 = pair[0];
                var s2 = pair[1];
                var item1 = (s1 <= numTeams) ? teamsList[s1 - 1] : null;
                var t1Name = item1 ? (typeof item1 === 'object' ? (item1.name || item1.rawName || '') : String(item1)) : 'BYE';
                var item2 = (s2 <= numTeams) ? teamsList[s2 - 1] : null;
                var t2Name = item2 ? (typeof item2 === 'object' ? (item2.name || item2.rawName || '') : String(item2)) : 'BYE';
                var isBye = (t1Name === 'BYE' || t2Name === 'BYE');
                var mId = internalIdCounter++;

                var t1Seed = (t1Name === 'BYE') ? '' : (item1 && typeof item1 === 'object' && item1.seed !== undefined && item1.seed !== null && item1.seed !== '' ? item1.seed : s1);
                var t2Seed = (t2Name === 'BYE') ? '' : (item2 && typeof item2 === 'object' && item2.seed !== undefined && item2.seed !== null && item2.seed !== '' ? item2.seed : s2);

                var match = {
                    matchId: mId,
                    matchNumber: null,
                    bracketType: 'UPPER',
                    roundNumber: 1,
                    status: isBye ? 'COMPLETED' : 'SCHEDULED',
                    team1: { name: t1Name, seed: t1Seed, score: '' },
                    team2: { name: t2Name, seed: t2Seed, score: '' },
                    winnerId: null,
                    nextMatchId: null,
                    nextMatchSlot: (i % 2 === 0) ? 1 : 2,
                    dropToMatchId: null,
                    dropToMatchSlot: (i % 2 === 0) ? 1 : 2
                };

                // Auto advance BYE
                if (t1Name === 'BYE' && t2Name !== 'BYE') {
                    match.winnerId = 'team2';
                } else if (t2Name === 'BYE' && t1Name !== 'BYE') {
                    match.winnerId = 'team1';
                }

                matchesMap[mId] = match;
                ubRound1.matches.push(match);
            }
            upperRounds.push(ubRound1);

            // Subsequent UB Rounds
            var currentUbCount = ubRound1MatchesCount;
            var prevUbMatchIds = ubRound1.matches.map(function (m) { return m.matchId; });

            for (var r = 2; r <= totalUbRounds; r++) {
                var count = currentUbCount / 2;
                var roundObj = {
                    roundNumber: r,
                    bracketType: 'UPPER',
                    title: this.getUpperRoundTitle(r, totalUbRounds),
                    matches: []
                };
                var nextIds = [];

                for (var j = 0; j < count; j++) {
                    var mId = internalIdCounter++;
                    var p1Id = prevUbMatchIds[j * 2];
                    var p2Id = prevUbMatchIds[j * 2 + 1];

                    if (matchesMap[p1Id]) matchesMap[p1Id].nextMatchId = mId;
                    if (matchesMap[p2Id]) matchesMap[p2Id].nextMatchId = mId;

                    var p1 = matchesMap[p1Id];
                    var p2 = matchesMap[p2Id];

                    var t1Placeholder = (p1 && p1.winnerId) ?
                        (p1.winnerId === 'team1' ? p1.team1.name : p1.team2.name) : ('W #' + p1Id);
                    var t2Placeholder = (p2 && p2.winnerId) ?
                        (p2.winnerId === 'team1' ? p2.team1.name : p2.team2.name) : ('W #' + p2Id);

                    var t1Seed = (p1 && p1.winnerId) ?
                        (p1.winnerId === 'team1' ? p1.team1.seed : p1.team2.seed) : '';
                    var t2Seed = (p2 && p2.winnerId) ?
                        (p2.winnerId === 'team1' ? p2.team1.seed : p2.team2.seed) : '';

                    var mObj = {
                        matchId: mId,
                        matchNumber: null,
                        bracketType: 'UPPER',
                        roundNumber: r,
                        status: 'SCHEDULED',
                        team1: { name: t1Placeholder, seed: t1Seed, score: '' },
                        team2: { name: t2Placeholder, seed: t2Seed, score: '' },
                        winnerId: null,
                        nextMatchId: null,
                        nextMatchSlot: (j % 2 === 0) ? 1 : 2,
                        dropToMatchId: null,
                        dropToMatchSlot: (j % 2 === 0) ? 1 : 2
                    };

                    matchesMap[mId] = mObj;
                    roundObj.matches.push(mObj);
                    nextIds.push(mId);
                }

                upperRounds.push(roundObj);
                prevUbMatchIds = nextIds;
                currentUbCount = count;
            }

            // ====================================================================
            // 2. GENERATE LOWER BRACKET (LB)
            // ====================================================================
            var lbMatchesPerRound = [];
            var matchCountInLb = pow2 / 4;
            for (var lr = 1; lr <= totalLbRounds; lr++) {
                lbMatchesPerRound.push(matchCountInLb);
                if (lr % 2 === 0) {
                    matchCountInLb = Math.max(1, matchCountInLb / 2);
                }
            }

            var prevLbMatchIds = [];

            for (var lr = 1; lr <= totalLbRounds; lr++) {
                var mCount = lbMatchesPerRound[lr - 1];
                var lbRoundObj = {
                    roundNumber: lr,
                    bracketType: 'LOWER',
                    title: this.getLowerRoundTitle(lr, totalLbRounds),
                    matches: []
                };
                var currentRoundIds = [];
                var isMajorRound = (lr % 2 === 0); // Even rounds receive drop downs from UB

                for (var k = 0; k < mCount; k++) {
                    var mId = internalIdCounter++;
                    currentRoundIds.push(mId);

                    var t1Placeholder = 'L #' + mId;
                    var t2Placeholder = 'L #' + (mId + 1);

                    // If not LB round 1, connect previous LB round winner to slot 1
                    if (lr > 1) {
                        var feederLbMatchId = isMajorRound ? prevLbMatchIds[k] : prevLbMatchIds[k * 2];
                        if (feederLbMatchId && matchesMap[feederLbMatchId]) {
                            matchesMap[feederLbMatchId].nextMatchId = mId;
                            matchesMap[feederLbMatchId].nextMatchSlot = 1;
                            t1Placeholder = 'W #' + feederLbMatchId;
                        }

                        if (!isMajorRound) {
                            var feederLbMatch2Id = prevLbMatchIds[k * 2 + 1];
                            if (feederLbMatch2Id && matchesMap[feederLbMatch2Id]) {
                                matchesMap[feederLbMatch2Id].nextMatchId = mId;
                                matchesMap[feederLbMatch2Id].nextMatchSlot = 2;
                                t2Placeholder = 'W #' + feederLbMatch2Id;
                            }
                        }
                    }

                    var lbMatch = {
                        matchId: mId,
                        matchNumber: null,
                        bracketType: 'LOWER',
                        roundNumber: lr,
                        status: 'SCHEDULED',
                        team1: { name: t1Placeholder, seed: '', score: '' },
                        team2: { name: t2Placeholder, seed: '', score: '' },
                        winnerId: null,
                        nextMatchId: null,
                        nextMatchSlot: (k % 2 === 0) ? 1 : 2
                    };

                    matchesMap[mId] = lbMatch;
                    lbRoundObj.matches.push(lbMatch);
                }

                lowerRounds.push(lbRoundObj);
                prevLbMatchIds = currentRoundIds;
            }

            // Link Drop Downs from Upper Bracket to Lower Bracket
            // UB Round 1 losers drop to LB Round 1 (Quad-Fold Inverted Cross-Over: Q1 vs Q4, Q2 vs Q3)
            if (upperRounds[0] && lowerRounds[0]) {
                var ubR1Matches = upperRounds[0].matches;
                var lbR1Matches = lowerRounds[0].matches;
                var totalUbR1 = ubR1Matches.length;
                var totalLbR1 = lbR1Matches.length;

                for (var u = 0; u < totalUbR1; u++) {
                    var targetLbIndex, targetLbSlot;

                    if (totalUbR1 >= 8) {
                        // Quad-Fold Cross: Split into 4 Quarters
                        // Q1 (0..quarter-1) vs Q4 (2N-1..2N-quarter) -> drops to LB 0..quarter-1
                        // Q2 (quarter..half-1) vs Q3 (2N-quarter-1..half) -> drops to LB quarter..half-1
                        var half = totalLbR1; // N
                        var quarter = Math.floor(half / 2); // N/2

                        if (u < quarter) { // Q1
                            targetLbIndex = u;
                            targetLbSlot = 1;
                        } else if (u < half) { // Q2
                            targetLbIndex = u;
                            targetLbSlot = 1;
                        } else if (u < half + quarter) { // Q3
                            targetLbIndex = quarter + (half + quarter - 1 - u); // inverted into Q2 area
                            targetLbSlot = 2;
                        } else { // Q4
                            targetLbIndex = (totalUbR1 - 1 - u); // inverted into Q1 area
                            targetLbSlot = 2;
                        }
                    } else if (u < totalLbR1) {
                        targetLbIndex = u;
                        targetLbSlot = 1;
                    } else {
                        targetLbIndex = (totalUbR1 - 1 - u);
                        targetLbSlot = 2;
                    }

                    var ubM = ubR1Matches[u];
                    var isUbBye = (ubM.team1.name === 'BYE' || ubM.team2.name === 'BYE');

                    if (lbR1Matches[targetLbIndex]) {
                        ubM.dropToMatchId = lbR1Matches[targetLbIndex].matchId;
                        ubM.dropToMatchSlot = targetLbSlot;
                        
                        // Set placeholder text in LB
                        if (isUbBye) {
                            // If this UB match was a BYE, no real team lost! The loser is BYE!
                            if (targetLbSlot === 1) {
                                lbR1Matches[targetLbIndex].team1.name = 'BYE';
                                lbR1Matches[targetLbIndex].team1.seed = '';
                            } else {
                                lbR1Matches[targetLbIndex].team2.name = 'BYE';
                                lbR1Matches[targetLbIndex].team2.seed = '';
                            }
                        } else {
                            if (targetLbSlot === 1) {
                                lbR1Matches[targetLbIndex].team1.name = 'L #' + ubM.matchId;
                            } else {
                                lbR1Matches[targetLbIndex].team2.name = 'L #' + ubM.matchId;
                            }
                        }
                    }
                }

                // Check LB Round 1 matches for double BYEs
                for (var k = 0; k < lbR1Matches.length; k++) {
                    var lm = lbR1Matches[k];
                    if (lm.team1.name === 'BYE' && lm.team2.name === 'BYE') {
                        lm.status = 'COMPLETED';
                        lm.winnerId = 'team1';
                    }
                }
            }

            // UB Round 2+ losers drop to Major LB rounds (Alternating Half-Inversion Anti-Rematch Drop Engine)
            for (var ur = 2; ur <= totalUbRounds; ur++) {
                var targetLbRoundIdx = (ur - 1) * 2 - 1; // Major LB round index
                if (upperRounds[ur - 1] && lowerRounds[targetLbRoundIdx]) {
                    var ubMatches = upperRounds[ur - 1].matches;
                    var lbMajorMatches = lowerRounds[targetLbRoundIdx].matches;
                    var mCount = ubMatches.length;

                    for (var u = 0; u < mCount; u++) {
                        var targetIdx = u;
                        if (mCount >= 4) {
                            var halfM = Math.floor(mCount / 2);
                            if (ur % 2 === 0) {
                                // Even drop rounds (R2, R4):
                                // - Top half (u < halfM): keeps forward order: targetIdx = u
                                // - Bottom half (u >= halfM): inverts order: targetIdx = halfM + (mCount - 1 - u)
                                if (u < halfM) {
                                    targetIdx = u;
                                } else {
                                    targetIdx = halfM + (mCount - 1 - u);
                                }
                            } else {
                                // Odd drop rounds (R3, R5):
                                // - Top half (u < halfM): inverts order: targetIdx = halfM - 1 - u
                                // - Bottom half (u >= halfM): keeps forward order: targetIdx = u
                                if (u < halfM) {
                                    targetIdx = halfM - 1 - u;
                                } else {
                                    targetIdx = u;
                                }
                            }
                        } else if (mCount === 2) {
                            // Semi-Finals UB: Cross drop (Semi 1 -> LB Semi 2, Semi 2 -> LB Semi 1)
                            targetIdx = (u === 0) ? 1 : 0;
                        } else {
                            targetIdx = 0;
                        }

                        if (lbMajorMatches[targetIdx]) {
                            ubMatches[u].dropToMatchId = lbMajorMatches[targetIdx].matchId;
                            ubMatches[u].dropToMatchSlot = 2;
                            lbMajorMatches[targetIdx].team2.name = 'L #' + ubMatches[u].matchId;
                        }
                    }
                }
            }

            // ====================================================================
            // 3. GENERATE GRAND FINALS (Attached in Upper Bracket Column)
            // ====================================================================
            var ubFinalMatch = upperRounds[upperRounds.length - 1].matches[0];
            var lbFinalMatch = lowerRounds[lowerRounds.length - 1].matches[0];

            var gf1Id = internalIdCounter++;

            var grandFinal1 = {
                matchId: gf1Id,
                matchNumber: null,
                bracketType: 'GRAND_FINAL',
                roundNumber: totalUbRounds + 1,
                status: 'SCHEDULED',
                team1: { name: 'Winner UB', seed: '', score: '' },
                team2: { name: 'Winner LB', seed: '', score: '' },
                winnerId: null,
                nextMatchId: null,
                nextMatchSlot: 1,
                isResetMatch: false
            };

            if (ubFinalMatch) {
                ubFinalMatch.nextMatchId = gf1Id;
                ubFinalMatch.nextMatchSlot = 1;
            }
            if (lbFinalMatch) {
                lbFinalMatch.nextMatchId = gf1Id;
                lbFinalMatch.nextMatchSlot = 2;
            }

            matchesMap[gf1Id] = grandFinal1;

            var grandFinalsRound = {
                roundNumber: totalUbRounds + 1,
                bracketType: 'GRAND_FINAL',
                title: 'Grand Finals',
                matches: [grandFinal1]
            };

            // 4. Resolve all cascading initial BYEs (Double BYEs & Single BYEs with predetermined real teams)
            var bracketData = {
                upperRounds: upperRounds,
                lowerRounds: lowerRounds,
                grandFinalsRound: grandFinalsRound,
                matchesMap: matchesMap
            };

            this.resolveAllInitialByes(bracketData);

            // 5. Renumber all matches contiguously
            this.renumberDoubleEliminationContiguously(bracketData);

            return bracketData;
        },

        /**
         * Cascade and resolve all initial BYE advances across all Upper and Lower bracket rounds
         */
        resolveAllInitialByes: function (bracketData) {
            var matchesMap = bracketData.matchesMap;
            if (!matchesMap) return;

            var changed = true;
            var iterations = 0;

            // Iterate until all cascading BYE effects settle (max 20 iterations)
            while (changed && iterations < 20) {
                changed = false;
                iterations++;

                var keys = Object.keys(matchesMap);
                for (var i = 0; i < keys.length; i++) {
                    var m = matchesMap[keys[i]];
                    var t1 = m.team1 ? m.team1.name : '';
                    var t2 = m.team2 ? m.team2.name : '';

                    // Case A: Double BYE (BYE vs BYE)
                    if (t1 === 'BYE' && t2 === 'BYE') {
                        if (m.status !== 'COMPLETED' || m.winnerId !== 'team1') {
                            m.status = 'COMPLETED';
                            m.winnerId = 'team1';
                            changed = true;
                        }
                        // Advance BYE to next match
                        if (m.nextMatchId && matchesMap[m.nextMatchId]) {
                            var nextM = matchesMap[m.nextMatchId];
                            var slot = m.nextMatchSlot || 1;
                            var currSlotName = (slot === 1) ? nextM.team1.name : nextM.team2.name;
                            if (currSlotName !== 'BYE') {
                                if (slot === 1) {
                                    nextM.team1.name = 'BYE';
                                    nextM.team1.seed = '';
                                } else {
                                    nextM.team2.name = 'BYE';
                                    nextM.team2.seed = '';
                                }
                                changed = true;
                            }
                        }
                    }
                    // Case B: Single BYE with an already determined Real Team
                    else if ((t1 === 'BYE' && t2 && t2 !== 'BYE' && !t2.startsWith('W #') && !t2.startsWith('L #') && !t2.startsWith('Winner')) ||
                             (t2 === 'BYE' && t1 && t1 !== 'BYE' && !t1.startsWith('W #') && !t1.startsWith('L #') && !t1.startsWith('Winner'))) {
                        var realWinnerSlot = (t1 === 'BYE') ? 'team2' : 'team1';
                        var realWinnerName = (t1 === 'BYE') ? t2 : t1;
                        var realWinnerSeed = (t1 === 'BYE') ? (m.team2 ? m.team2.seed : '') : (m.team1 ? m.team1.seed : '');

                        if (m.status !== 'COMPLETED' || m.winnerId !== realWinnerSlot) {
                            m.status = 'COMPLETED';
                            m.winnerId = realWinnerSlot;
                            changed = true;
                        }

                        // Advance Real Team to next match
                        if (m.nextMatchId && matchesMap[m.nextMatchId]) {
                            var nextM = matchesMap[m.nextMatchId];
                            var slot = m.nextMatchSlot || 1;
                            var currSlotName = (slot === 1) ? nextM.team1.name : nextM.team2.name;
                            if (currSlotName !== realWinnerName) {
                                if (slot === 1) {
                                    nextM.team1.name = realWinnerName;
                                    nextM.team1.seed = realWinnerSeed;
                                } else {
                                    nextM.team2.name = realWinnerName;
                                    nextM.team2.seed = realWinnerSeed;
                                }
                                changed = true;
                            }
                        }

                        // If UB match with BYE, drop to LB is BYE
                        if (m.bracketType === 'UPPER' && m.dropToMatchId && matchesMap[m.dropToMatchId]) {
                            var dropM = matchesMap[m.dropToMatchId];
                            var dropSlot = m.dropToMatchSlot || 1;
                            var currDropName = (dropSlot === 1) ? dropM.team1.name : dropM.team2.name;
                            if (currDropName !== 'BYE') {
                                if (dropSlot === 1) {
                                    dropM.team1.name = 'BYE';
                                    dropM.team1.seed = '';
                                } else {
                                    dropM.team2.name = 'BYE';
                                    dropM.team2.seed = '';
                                }
                                changed = true;
                            }
                        }
                    }
                }
            }
        },

        /**
         * Renumber all playable matches in EXACT TOURNAMENT PLAY ORDER:
         *
         * Rules:
         * 1. UB Round 1 is numbered first (#1..#M).
         * 2. LB Round 1 (receives UB R1 losers) is numbered next.
         * 3. For each subsequent round k = 1 to N-1:
         *    a. Play/number UB Round (k+1) -> produces losers dropping to LB.
         *    b. Play/number LB Even Round (2k) -> absorbs the losers from UB Round (k+1).
         *    c. If exists, play/number LB Odd Round (2k+1) -> pure intra-LB round (no UB drop-ins).
         * 4. Grand Finals are numbered last.
         */
        renumberDoubleEliminationContiguously: function (bracketData) {
            if (!bracketData || !bracketData.matchesMap) return;

            var counter = 1;
            var upperRounds = bracketData.upperRounds || [];
            var lowerRounds = bracketData.lowerRounds || [];
            var gfRound = bracketData.grandFinalsRound;

            // Helper: number non-BYE matches in a round
            var numberRound = function (roundObj) {
                if (!roundObj || !roundObj.matches) return;
                for (var m = 0; m < roundObj.matches.length; m++) {
                    var match = roundObj.matches[m];
                    var t1 = match.team1 ? match.team1.name : '';
                    var t2 = match.team2 ? match.team2.name : '';
                    var isBye = (t1 === 'BYE' || t2 === 'BYE');
                    match.matchNumber = isBye ? null : counter++;
                }
            };

            // 1. UB Round 1 (ub = 0)
            if (upperRounds.length > 0) {
                numberRound(upperRounds[0]);
            }

            // 2. LB Round 1 (lb = 0)
            if (lowerRounds.length > 0) {
                numberRound(lowerRounds[0]);
            }

            // 3. Subsequent Rounds (k = 1 .. totalUbRounds - 1)
            for (var k = 1; k < upperRounds.length; k++) {
                // a. UB Round (k + 1)
                numberRound(upperRounds[k]);

                // b. LB Even Round (receives drop-ins from UB Round k+1)
                var lbEvenIdx = (k - 1) * 2 + 1;
                if (lbEvenIdx < lowerRounds.length) {
                    numberRound(lowerRounds[lbEvenIdx]);
                }

                // c. LB Odd Round (pure intra-LB round between winners)
                var lbOddIdx = (k - 1) * 2 + 2;
                if (lbOddIdx < lowerRounds.length) {
                    numberRound(lowerRounds[lbOddIdx]);
                }
            }

            // 4. Grand Finals
            if (gfRound && gfRound.matches) {
                for (var g = 0; g < gfRound.matches.length; g++) {
                    gfRound.matches[g].matchNumber = counter++;
                }
            }

            // Update placeholders to reference actual matchNumbers
            this.updatePlaceholdersWithMatchNumbers(bracketData);
        },
        /**
         * Update W #... and L #... placeholders across all matches
         */
        updatePlaceholdersWithMatchNumbers: function (bracketData) {
            var matchesMap = bracketData.matchesMap;
            if (!matchesMap) return;

            var keys = Object.keys(matchesMap);
            for (var i = 0; i < keys.length; i++) {
                var curr = matchesMap[keys[i]];
                
                // Update winner progression placeholder ONLY if match has no winner and has a valid matchNumber
                if (curr.nextMatchId && matchesMap[curr.nextMatchId]) {
                    var nextM = matchesMap[curr.nextMatchId];
                    var slot = curr.nextMatchSlot || 1;
                    if (!curr.winnerId && curr.matchNumber) {
                        var pLabel = 'W #' + curr.matchNumber;
                        if (slot === 1 && (!nextM.team1.name || nextM.team1.name.startsWith('W #') || nextM.team1.name.startsWith('L #') || nextM.team1.name === 'Winner UB')) {
                            nextM.team1.name = pLabel;
                        } else if (slot === 2 && (!nextM.team2.name || nextM.team2.name.startsWith('W #') || nextM.team2.name.startsWith('L #') || nextM.team2.name === 'Winner LB')) {
                            nextM.team2.name = pLabel;
                        }
                    }
                }

                // Update loser drop-down placeholder ONLY if match has no winner and has a valid matchNumber
                if (curr.dropToMatchId && matchesMap[curr.dropToMatchId]) {
                    var dropM = matchesMap[curr.dropToMatchId];
                    var dropSlot = curr.dropToMatchSlot || 1;
                    if (!curr.winnerId && curr.matchNumber) {
                        var lLabel = 'L #' + curr.matchNumber;
                        if (dropSlot === 1 && (!dropM.team1.name || dropM.team1.name.startsWith('L #') || dropM.team1.name.startsWith('W #'))) {
                            dropM.team1.name = lLabel;
                        } else if (dropSlot === 2 && (!dropM.team2.name || dropM.team2.name.startsWith('L #') || dropM.team2.name.startsWith('W #'))) {
                            dropM.team2.name = lLabel;
                        }
                    }
                }
            }
        },

        /**
         * Recursively reset subsequent descendant matches to placeholders and clear stale scores/winners
         */
        cascadeResetPlaceholders: function (matchesMap, resetMatchId) {
            var curr = matchesMap[resetMatchId];
            if (!curr) return;

            // 1. Reset downstream winner branch
            if (curr.nextMatchId && matchesMap[curr.nextMatchId]) {
                var nextMatch = matchesMap[curr.nextMatchId];
                var slot = curr.nextMatchSlot || 1;
                var placeholderName = (curr.bracketType === 'UPPER' && nextMatch.bracketType === 'GRAND_FINAL') ? 'Winner UB' :
                                      (curr.bracketType === 'LOWER' && nextMatch.bracketType === 'GRAND_FINAL') ? 'Winner LB' :
                                      (curr.matchNumber ? ('W #' + curr.matchNumber) : ('W #' + curr.matchId));

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
            }

            // 2. Reset downstream loser drop-down branch (if this was an Upper Bracket match)
            if (curr.dropToMatchId && matchesMap[curr.dropToMatchId]) {
                var dropMatch = matchesMap[curr.dropToMatchId];
                var dropSlot = curr.dropToMatchSlot || 1;
                var lPlaceholder = curr.matchNumber ? ('L #' + curr.matchNumber) : ('L #' + curr.matchId);

                if (dropSlot === 1) {
                    dropMatch.team1.name = lPlaceholder;
                    dropMatch.team1.seed = '';
                } else {
                    dropMatch.team2.name = lPlaceholder;
                    dropMatch.team2.seed = '';
                }

                dropMatch.team1.score = '';
                dropMatch.team2.score = '';
                dropMatch.winnerId = null;
                dropMatch.status = 'SCHEDULED';

                this.cascadeResetPlaceholders(matchesMap, curr.dropToMatchId);
            }
        },

        /**
         * Propagate Match Result Realtime (Winners up, Losers down to LB, Grand Finals reset)
         */
        propagateMatchResult: function (matchesMap, matchId, winnerId, isT1Winner) {
            var currMatch = matchesMap[matchId];
            if (!currMatch) return;

            var winnerName = isT1Winner ? currMatch.team1.name : currMatch.team2.name;
            var winnerSeed = isT1Winner ? currMatch.team1.seed : currMatch.team2.seed;
            var loserName = isT1Winner ? currMatch.team2.name : currMatch.team1.name;
            var loserSeed = isT1Winner ? currMatch.team2.seed : currMatch.team1.seed;

            // 1. Advance Winner to next match & recursively reset descendants
            if (currMatch.nextMatchId && matchesMap[currMatch.nextMatchId]) {
                var nextM = matchesMap[currMatch.nextMatchId];
                var slot = currMatch.nextMatchSlot || 1;
                if (slot === 1) {
                    nextM.team1.name = winnerName;
                    nextM.team1.seed = winnerSeed;
                } else {
                    nextM.team2.name = winnerName;
                    nextM.team2.seed = winnerSeed;
                }
                nextM.team1.score = '';
                nextM.team2.score = '';
                nextM.winnerId = null;
                nextM.status = 'SCHEDULED';

                // Reset any downstream matches from nextM
                this.cascadeResetPlaceholders(matchesMap, nextM.matchId);
            }

            // 2. Drop Loser to Lower Bracket & recursively reset descendants
            if (currMatch.dropToMatchId && matchesMap[currMatch.dropToMatchId]) {
                var dropM = matchesMap[currMatch.dropToMatchId];
                var dropSlot = currMatch.dropToMatchSlot || 1;
                
                // If loser is not BYE, drop to LB
                if (loserName && loserName !== 'BYE') {
                    if (dropSlot === 1) {
                        dropM.team1.name = loserName;
                        dropM.team1.seed = loserSeed;
                    } else {
                        dropM.team2.name = loserName;
                        dropM.team2.seed = loserSeed;
                    }
                    dropM.team1.score = '';
                    dropM.team2.score = '';
                    dropM.winnerId = null;
                    dropM.status = 'SCHEDULED';

                    // Reset any downstream matches from dropM
                    this.cascadeResetPlaceholders(matchesMap, dropM.matchId);

                    // If opponent in this LB match is a BYE, auto-advance the real team!
                    var opponentName = (dropSlot === 1) ? dropM.team2.name : dropM.team1.name;
                    if (opponentName === 'BYE') {
                        var realWinnerSlot = (dropSlot === 1) ? 'team1' : 'team2';
                        dropM.status = 'COMPLETED';
                        dropM.winnerId = realWinnerSlot;
                        this.propagateMatchResult(matchesMap, dropM.matchId, realWinnerSlot, (realWinnerSlot === 'team1'));
                    }
                }
            }

            // 3. Handle Grand Finals Reset Match Logic
            if (currMatch.bracketType === 'GRAND_FINAL' && !currMatch.isResetMatch) {
                var resetMatch = currMatch.nextMatchId ? matchesMap[currMatch.nextMatchId] : null;
                if (resetMatch && resetMatch.isResetMatch) {
                    if (!isT1Winner) {
                        // Winner of LB won GF1! Unlock Bracket Reset Match (GF2)
                        resetMatch.isUnlocked = true;
                        resetMatch.team1.name = currMatch.team1.name;
                        resetMatch.team1.seed = currMatch.team1.seed;
                        resetMatch.team2.name = currMatch.team2.name;
                        resetMatch.team2.seed = currMatch.team2.seed;
                        resetMatch.status = 'SCHEDULED';
                    } else {
                        // Winner of UB won GF1! Champion crowned, Reset match not needed
                        resetMatch.isUnlocked = false;
                        resetMatch.status = 'SCHEDULED';
                    }
                }
            }
        },

        /**
         * Filter and Group Matches for List View in Play-Order Sequence (#1, #2, #3...)
         */
        filterMatchesForListView: function (bracketData) {
            var result = [];
            if (!bracketData) return result;

            var upperRounds = bracketData.upperRounds || [];
            var lowerRounds = bracketData.lowerRounds || [];
            var gfRound = bracketData.grandFinalsRound;

            var pushRound = function (ro, bType) {
                if (!ro || !ro.matches) return;
                var validMatches = ro.matches.filter(function (m) {
                    var t1 = m.team1 ? m.team1.name : '';
                    var t2 = m.team2 ? m.team2.name : '';
                    return t1 !== 'BYE' && t2 !== 'BYE';
                });
                if (validMatches.length > 0) {
                    result.push({
                        bracketType: bType || ro.bracketType || 'UPPER',
                        roundNumber: ro.roundNumber,
                        title: ro.title,
                        matches: validMatches
                    });
                }
            };

            // 1. UB Round 1
            if (upperRounds.length > 0) {
                pushRound(upperRounds[0], 'UPPER');
            }

            // 2. LB Round 1
            if (lowerRounds.length > 0) {
                pushRound(lowerRounds[0], 'LOWER');
            }

            // 3. Subsequent Rounds matching Play-Order
            for (var k = 1; k < upperRounds.length; k++) {
                // a. UB Round (k + 1)
                pushRound(upperRounds[k], 'UPPER');

                // b. LB Even Round (absorbs UB Round k+1 losers)
                var lbEvenIdx = (k - 1) * 2 + 1;
                if (lbEvenIdx < lowerRounds.length) {
                    pushRound(lowerRounds[lbEvenIdx], 'LOWER');
                }

                // c. LB Odd Round (pure intra-LB round)
                var lbOddIdx = (k - 1) * 2 + 2;
                if (lbOddIdx < lowerRounds.length) {
                    pushRound(lowerRounds[lbOddIdx], 'LOWER');
                }
            }

            // 4. Grand Finals
            if (gfRound && gfRound.matches) {
                pushRound(gfRound, 'GRAND_FINAL');
            }

            return result;
        }
    };

})();
