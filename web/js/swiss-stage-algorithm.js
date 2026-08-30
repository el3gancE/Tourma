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
          if (!m || m.status !== 'COMPLETED') return;

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

      // 4. Sort Standings: Points DESC -> Buchholz DESC -> Goal Diff DESC -> Wins DESC
      var standingsArr = Object.keys(stats).map(function (k) { return stats[k]; });

      standingsArr.sort(function (a, b) {
        if (b.points !== a.points) return b.points - a.points;
        if (b.buchholz !== a.buchholz) return b.buchholz - a.buchholz;
        if (b.diff !== a.diff) return b.diff - a.diff;
        return b.wins - a.wins;
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
      var floatedTeams = [];

      recordKeys.forEach(function (recKey) {
        // RANDOM SHUFFLE pool teams to maximize dynamic pairing variety on every recalculation!
        var pool = pools[recKey].slice().sort(function () { return 0.5 - Math.random(); }).concat(floatedTeams);
        floatedTeams = [];

        while (pool.length >= 2) {
          var t1 = pool.shift();
          var t2Index = -1;

          // Find opponent t2 in randomized pool that t1 has NOT played against yet
          for (var i = 0; i < pool.length; i++) {
            if (t1.opponents.indexOf(pool[i].name) === -1) {
              t2Index = i;
              break;
            }
          }

          if (t2Index !== -1) {
            var t2 = pool.splice(t2Index, 1)[0];
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
          } else {
            // Cannot pair in this pool without rematch; float t1 down
            floatedTeams.push(t1);
          }
        }

        if (pool.length === 1) {
          floatedTeams.push(pool.shift());
        }
      });

      return newMatches;
    }
  };

  window.TourmaSwissAlgorithm = TourmaSwissAlgorithm;

})(window);
