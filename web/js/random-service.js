/**
 * ============================================================================
 * TOURMA - CENTRALIZED RANDOM SERVICE (random-service.js)
 * High-Density Tournament Management Suite
 * Encapsulates all Match Score Randomization, Weighted Probability Distributions,
 * and Batch Round Execution across Single Elimination, Double Elimination,
 * Round Robin, and all future tournament formats.
 * ============================================================================
 */
(function (window) {
    'use strict';

    var TourmaRandomService = {

        /**
         * Generate realistic weighted random match score:
         * - If rawWinScore is specified (> 0), winner gets rawWinScore.
         * - If blank / null, 75% probability for winner score in [2, 5], 25% for [6, 9].
         * - Loser score is strictly integer in [0, winnerScore - 1].
         * - 50/50 probability for Team 1 vs Team 2 victory.
         *
         * @param {number|string|null} rawWinScore
         * @returns {{ team1Score: number, team2Score: number, winner: string, isT1Winner: boolean }}
         */
        generateMatchScore: function (rawWinScore) {
            var parsedScore = null;
            if (rawWinScore !== undefined && rawWinScore !== null && String(rawWinScore).trim() !== '') {
                var num = parseInt(String(rawWinScore).trim(), 10);
                if (!isNaN(num) && num > 0) {
                    parsedScore = num;
                }
            }

            var targetWinScore;
            if (parsedScore !== null) {
                targetWinScore = parsedScore;
            } else {
                var p = Math.random();
                if (p < 0.75) {
                    // 75% probability for realistic sports/esports match scores: 2, 3, 4, 5
                    targetWinScore = Math.floor(Math.random() * 4) + 2;
                } else {
                    // 25% probability for higher scores: 6, 7, 8, 9
                    targetWinScore = Math.floor(Math.random() * 4) + 6;
                }
            }

            var isT1Winner = Math.random() < 0.5;
            var loserScore = (targetWinScore > 0) ? Math.floor(Math.random() * targetWinScore) : 0;

            var s1 = isT1Winner ? targetWinScore : loserScore;
            var s2 = isT1Winner ? loserScore : targetWinScore;
            var winnerSlot = isT1Winner ? 'team1' : 'team2';

            return {
                team1Score: s1,
                team2Score: s2,
                winner: winnerSlot,
                isT1Winner: isT1Winner
            };
        },

        /**
         * Check if all matches in a round have determined teams AND at least one match is uncompleted.
         * If 100% of matches are completed (DONE), returns false (Random button is disabled until Reset).
         *
         * @param {Object} roundObj - Round object with matches array
         * @param {Object} matchesMap - Global matches map
         * @returns {boolean}
         */
        isRoundReadyForRandom: function (roundObj, matchesMap) {
            if (!roundObj || !roundObj.matches || roundObj.matches.length === 0) return false;
            var map = matchesMap || {};
            var hasPlayable = false;
            var hasUncompleted = false;

            for (var i = 0; i < roundObj.matches.length; i++) {
                var m = map[roundObj.matches[i].matchId] || roundObj.matches[i];
                if (m.isResetMatch && !m.isUnlocked) continue;

                var t1 = m.team1 ? m.team1.name : '';
                var t2 = m.team2 ? m.team2.name : '';
                var isBye = (t1 === 'BYE' || t2 === 'BYE');
                if (isBye) continue;

                var isPending = (!t1 || !t2 ||
                    t1.startsWith('W #') || t1.startsWith('L #') ||
                    t2.startsWith('W #') || t2.startsWith('L #') ||
                    t1 === 'Winner UB' || t2 === 'Winner LB');

                if (isPending) return false; // Found an undetermined team placeholder!

                hasPlayable = true;
                if (m.status !== 'COMPLETED') {
                    hasUncompleted = true;
                }
            }

            // Only ready if all teams are determined AND not 100% of matches are DONE
            return hasPlayable && hasUncompleted;
        },

        /**
         * Check if 100% of playable matches in a round are completed (DONE)
         *
         * @param {Object} roundObj
         * @param {Object} matchesMap
         * @returns {boolean}
         */
        isRoundAllCompleted: function (roundObj, matchesMap) {
            if (!roundObj || !roundObj.matches || roundObj.matches.length === 0) return false;
            var map = matchesMap || {};
            var hasPlayable = false;

            for (var i = 0; i < roundObj.matches.length; i++) {
                var m = map[roundObj.matches[i].matchId] || roundObj.matches[i];
                if (m.isResetMatch && !m.isUnlocked) continue;

                var t1 = m.team1 ? m.team1.name : '';
                var t2 = m.team2 ? m.team2.name : '';
                var isBye = (t1 === 'BYE' || t2 === 'BYE');
                if (isBye) continue;

                var isPending = (!t1 || !t2 ||
                    t1.startsWith('W #') || t1.startsWith('L #') ||
                    t2.startsWith('W #') || t2.startsWith('L #') ||
                    t1 === 'Winner UB' || t2 === 'Winner LB');

                if (isPending) return false;

                hasPlayable = true;
                if (m.status !== 'COMPLETED') return false;
            }

            return hasPlayable;
        },

        /**
         * Randomize all playable matches in a round (including DONE matches)
         * and invoke onMatchCompleted callback for each
         *
         * @param {Object} roundObj
         * @param {Object} matchesMap
         * @param {number|string|null} rawWinScore
         * @param {Function} onMatchCompleted - callback(matchId, winnerSlot, isT1Winner, s1, s2)
         * @returns {boolean} changed
         */
        randomizeRoundMatches: function (roundObj, matchesMap, rawWinScore, onMatchCompleted) {
            if (!this.isRoundReadyForRandom(roundObj, matchesMap)) return false;

            var map = matchesMap || {};
            var changed = false;

            for (var m = 0; m < roundObj.matches.length; m++) {
                var match = roundObj.matches[m];
                var mId = match.matchId;
                var currMatch = map[mId] || match;

                if (currMatch.isResetMatch && !currMatch.isUnlocked) continue;

                var t1 = currMatch.team1 ? currMatch.team1.name : '';
                var t2 = currMatch.team2 ? currMatch.team2.name : '';
                var isBye = (t1 === 'BYE' || t2 === 'BYE');
                var isPending = (!t1 || !t2 ||
                    t1.startsWith('W #') || t1.startsWith('L #') ||
                    t2.startsWith('W #') || t2.startsWith('L #') ||
                    t1 === 'Winner UB' || t2 === 'Winner LB');

                // Randomize ALL playable matches in this round, including already DONE matches!
                if (!isBye && !isPending) {
                    var result = this.generateMatchScore(rawWinScore);

                    currMatch.team1.score = result.team1Score;
                    currMatch.team2.score = result.team2Score;
                    currMatch.winnerId = result.winner;
                    currMatch.status = 'COMPLETED';

                    if (typeof onMatchCompleted === 'function') {
                        onMatchCompleted(mId, result.winner, result.isT1Winner, result.team1Score, result.team2Score);
                    }
                    changed = true;
                }
            }

            return changed;
        },

        /**
         * Check if any match in this round has been played / completed / scored
         *
         * @param {Object} roundObj
         * @param {Object} matchesMap
         * @returns {boolean}
         */
        hasCompletedMatchesInRound: function (roundObj, matchesMap) {
            if (!roundObj || !roundObj.matches) return false;
            var map = matchesMap || {};
            for (var i = 0; i < roundObj.matches.length; i++) {
                var m = map[roundObj.matches[i].matchId] || roundObj.matches[i];
                if (m.status === 'COMPLETED' || m.winnerId !== null || (m.team1 && m.team1.score !== '') || (m.team2 && m.team2.score !== '')) {
                    return true;
                }
            }
            return false;
        },

        /**
         * Reset all matches in a round back to SCHEDULED and invoke onMatchReset callback for each
         *
         * @param {Object} roundObj
         * @param {Object} matchesMap
         * @param {Function} onMatchReset - callback(matchId)
         * @returns {boolean} changed
         */
        resetRoundMatches: function (roundObj, matchesMap, onMatchReset) {
            if (!roundObj || !roundObj.matches) return false;
            var map = matchesMap || {};
            var changed = false;

            for (var m = 0; m < roundObj.matches.length; m++) {
                var match = roundObj.matches[m];
                var mId = match.matchId;
                var currMatch = map[mId] || match;

                if (currMatch.status === 'COMPLETED' || currMatch.winnerId !== null || (currMatch.team1 && currMatch.team1.score !== '') || (currMatch.team2 && currMatch.team2.score !== '')) {
                    currMatch.team1.score = '';
                    currMatch.team2.score = '';
                    currMatch.winnerId = null;
                    currMatch.status = 'SCHEDULED';

                    if (currMatch.isResetMatch) {
                        currMatch.isUnlocked = false;
                    }

                    if (typeof onMatchReset === 'function') {
                        onMatchReset(mId);
                    }
                    changed = true;
                }
            }

            return changed;
        }
    };

    window.TourmaRandomService = TourmaRandomService;

})(window);
