/**
 * TOURMA - Swiss System / Swiss Lite Tournament Algorithm
 * Manages Swiss pairings, Buchholz score tiebreakers, and record pools (3-win Qualified / 3-loss Eliminated).
 * Dynamic randomized pairings per record pool with strict non-rematch enforcement.
 */

(function (window) {
  'use strict';

  var TourmaSwissAlgorithm = {
    
    // Calculate Buchholz Standings based on teams list and completed rounds/matches
    calculateStandings: function (teamsList, matchesMap) {
      if (!teamsList || !Array.isArray(teamsList)) return [];

      // 1. Initialize stats for each team
      var stats = {};
      teamsList.forEach(function (t) {
        if (!t) return;
        var name = (typeof t === 'string') ? t : (t.name || t.id);
        stats[name] = {
          name: name,
          wins: 0,
          losses: 0,
          draws: 0,
          points: 0,
          scoresFor: 0,
          scoresAgainst: 0,
          diff: 0,
          buchholz: 0,
          opponents: [],
          qualified: false,
          eliminated: false,
          statusText: 'Đang thi đấu'
        };
      });

      // 2. Process all completed matches
      if (matchesMap && typeof matchesMap === 'object') {
        Object.keys(matchesMap).forEach(function (key) {
          var m = matchesMap[key];
          if (!m || (m.status !== 'COMPLETED' && m.status !== 'DONE')) return;

          var t1Name = m.team1 ? (m.team1.name || m.team1) : m.team1Name;
          var t2Name = m.team2 ? (m.team2.name || m.team2) : m.team2Name;

          var s1 = parseInt(m.team1Score || 0, 10);
          var s2 = parseInt(m.team2Score || 0, 10);

          if (t1Name && stats[t1Name] && t2Name && stats[t2Name]) {
            stats[t1Name].opponents.push(t2Name);
            stats[t2Name].opponents.push(t1Name);

            stats[t1Name].scoresFor += s1;
            stats[t1Name].scoresAgainst += s2;
            stats[t2Name].scoresFor += s2;
            stats[t2Name].scoresAgainst += s1;

            if (s1 > s2) {
              stats[t1Name].wins += 1;
              stats[t1Name].points += 3;
              stats[t2Name].losses += 1;
            } else if (s2 > s1) {
              stats[t2Name].wins += 1;
              stats[t2Name].points += 3;
              stats[t1Name].losses += 1;
            } else {
              stats[t1Name].draws += 1;
              stats[t1Name].points += 1;
              stats[t2Name].draws += 1;
              stats[t2Name].points += 1;
            }
          } else if (t1Name && stats[t1Name] && (!t2Name || t2Name === 'BYE')) {
            // Bye round
            stats[t1Name].wins += 1;
            stats[t1Name].points += 3;
          }
        });
      }

      // 3. Compute Buchholz Score (Sum of opponents' total points) & Check Status
      Object.keys(stats).forEach(function (name) {
        var st = stats[name];
        st.diff = st.scoresFor - st.scoresAgainst;

        var bSum = 0;
        st.opponents.forEach(function (oppName) {
          if (stats[oppName]) {
            bSum += stats[oppName].points;
          }
        });
        st.buchholz = bSum;

        if (st.wins >= 3) {
          st.qualified = true;
          st.statusText = 'Qualified 🏆';
        } else if (st.losses >= 3) {
          st.eliminated = true;
          st.statusText = 'Eliminated ❌';
        }
      });

      // 4. Sort Standings:
      // Primary: Qualified status / Wins - Losses record (3-0 > 3-1 > 3-2 > other records)
      // When both qualified (wins >= 3): Losses ASC (0 losses [3-0] > 1 loss [3-1] > 2 losses [3-2])
      // When active: Wins DESC -> Losses ASC -> Buchholz DESC -> Diff DESC
      var standingsArr = Object.keys(stats).map(function (k) { return stats[k]; });

      standingsArr.sort(function (a, b) {
        if (a.qualified && !b.qualified) return -1;
        if (!a.qualified && b.qualified) return 1;
        if (a.qualified && b.qualified) {
          if (a.losses !== b.losses) return a.losses - b.losses; // 0 losses (3-0) < 1 loss (3-1) < 2 losses (3-2)
          if (b.buchholz !== a.buchholz) return b.buchholz - a.buchholz;
          if (b.diff !== a.diff) return b.diff - a.diff;
          return b.scoresFor - a.scoresFor;
        }

        if (a.eliminated && !b.eliminated) return 1;
        if (!a.eliminated && b.eliminated) return -1;

        if (b.wins !== a.wins) return b.wins - a.wins;
        if (a.losses !== b.losses) return a.losses - b.losses;
        if (b.buchholz !== a.buchholz) return b.buchholz - a.buchholz;
        if (b.diff !== a.diff) return b.diff - a.diff;
        return b.scoresFor - a.scoresFor;
      });

      return standingsArr;
    },

    // Group teams by their Win-Loss Record (e.g. 2-0, 1-0, 0-1, 1-1, etc.)
    groupByRecord: function (standings) {
      var pools = {};
      standings.forEach(function (st) {
        var recKey = st.wins + '-' + st.losses;
        if (!pools[recKey]) pools[recKey] = [];
        pools[recKey].push(st);
      });
      return pools;
    },

    // Robust Backtracking Matchmaker for a Pool
    pairPool: function (pool) {
      if (!pool || pool.length < 2) return [];

      // Try multiple randomized orders with backtracking to find a 100% valid rematch-free pairing
      for (var attempt = 0; attempt < 25; attempt++) {
        var shuffled = pool.slice().sort(function () { return 0.5 - Math.random(); });
        var result = this._backtrackPairing(shuffled, []);
        if (result !== null) {
          return result;
        }
      }

      // If strict rematch-free pairing was not found after attempts, pair greedily ignoring rematches as absolute fallback
      var fallback = [];
      var remaining = pool.slice().sort(function () { return 0.5 - Math.random(); });
      while (remaining.length >= 2) {
        var a = remaining.shift();
        var b = remaining.shift();
        fallback.push([a, b]);
      }
      return fallback;
    },

    _backtrackPairing: function (teams, currentPairs) {
      if (teams.length === 0) return currentPairs;
      if (teams.length === 1) return null; // Odd team cannot be paired

      var first = teams[0];
      var rest = teams.slice(1);

      for (var i = 0; i < rest.length; i++) {
        var candidate = rest[i];
        var isRematch = (first.opponents && first.opponents.indexOf(candidate.name) !== -1);
        if (!isRematch) {
          var nextTeams = rest.slice(0, i).concat(rest.slice(i + 1));
          var subResult = this._backtrackPairing(nextTeams, currentPairs.concat([[first, candidate]]));
          if (subResult !== null) return subResult;
        }
      }
      return null;
    },

    // Generate Next Swiss Round Pairings with Dynamic Pool Shuffling & Strict Non-Rematch Rule
    generateNextRound: function (standings, matchesMap, currentRound) {
      var activeTeams = standings.filter(function (st) {
        return !st.qualified && !st.eliminated;
      });

      var pools = this.groupByRecord(activeTeams);
      var recordKeys = Object.keys(pools).sort(function (a, b) {
        var partsA = a.split('-').map(Number);
        var partsB = b.split('-').map(Number);
        if (partsB[0] !== partsA[0]) return partsB[0] - partsA[0]; // Wins DESC
        return partsA[1] - partsB[1]; // Losses ASC
      });

      var newMatches = [];
      var matchCounter = 1;
      var self = this;

      recordKeys.forEach(function (recKey) {
        var poolTeams = pools[recKey] || [];
        var pairs = self.pairPool(poolTeams);

        pairs.forEach(function (pair) {
          var t1 = pair[0];
          var t2 = pair[1];
          newMatches.push({
            matchKey: 'R' + currentRound + '_M' + matchCounter,
            roundIndex: currentRound,
            matchNumber: matchCounter++,
            recordPool: recKey,
            team1: { name: t1.name },
            team2: { name: t2.name },
            team1Score: 0,
            team2Score: 0,
            status: 'READY'
          });
        });
      });

      return newMatches;
    }
  };

  window.TourmaSwissAlgorithm = TourmaSwissAlgorithm;

})(window);
