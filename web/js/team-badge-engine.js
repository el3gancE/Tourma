/**
 * TOURMA - TEAM BADGE ENGINE (team-badge-engine.js)
 * Modular badge registry & evaluation engine for team honors and achievements.
 * Supports dynamic champions, luxury gold aesthetics, and static tier winner badges.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.TeamBadgeEngine = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  function normalizeStr(str) {
    if (!str) return '';
    try {
      return String(str).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
    } catch (e) {
      return String(str).toLowerCase().trim();
    }
  }

  function isTeamSelf(name, targetKey) {
    if (!name || !targetKey) return false;
    var n = normalizeStr(name);
    var t = normalizeStr(targetKey);
    if (n === t) return true;
    var cTarget = t.replace(/[^a-z0-9]/g, '');
    var c = n.replace(/[^a-z0-9]/g, '');
    if (c && cTarget && c === cTarget) return true;

    // Strict number check: if either or both have digits, digits MUST match exactly
    var d1 = n.replace(/[^0-9]/g, '');
    var d2 = t.replace(/[^0-9]/g, '');
    if (d1 !== d2) {
      if (d1 !== '' || d2 !== '') return false;
    }

    // Substring match only for long strings with close length difference
    if (c.length >= 6 && cTarget.length >= 6) {
      if (Math.abs(c.length - cTarget.length) <= 3) {
        if (c.indexOf(cTarget) !== -1 || cTarget.indexOf(c) !== -1) {
          return true;
        }
      }
    }
    return false;
  }

  function extractName(obj) {
    if (!obj) return null;
    if (typeof obj === 'string') {
      var s = obj.trim().replace(/^\[\d+\]\s*/, '').replace(/^\(\d+\)\s*/, '').replace(/^\d+\.\s*/, '').replace(/^#\d+\s*/, '');
      if (s && s !== 'BYE' && s !== 'TBD' && !s.startsWith('W #') && !s.startsWith('L #') && !s.startsWith('Winner ') && !s.startsWith('Loser ')) {
        return s;
      }
      return null;
    }
    if (typeof obj === 'object') {
      var name = obj.name || obj.rawName || obj.normalizedName || (obj.team && (typeof obj.team === 'object' ? (obj.team.name || obj.team.rawName) : obj.team));
      if (name && typeof name === 'string') {
        var s = name.trim().replace(/^\[\d+\]\s*/, '').replace(/^\(\d+\)\s*/, '').replace(/^\d+\.\s*/, '').replace(/^#\d+\s*/, '');
        if (s && s !== 'BYE' && s !== 'TBD' && !s.startsWith('W #') && !s.startsWith('L #') && !s.startsWith('Winner ') && !s.startsWith('Loser ')) {
          return s;
        }
      }
    }
    return null;
  }

  var storageDataCache = {};
  function getStorageData(prefixList, id) {
    if (typeof localStorage === 'undefined') return null;
    if (!prefixList || prefixList.length === 0 || !id) return null;
    var cacheKey = prefixList.join('|') + '__' + id;
    if (storageDataCache[cacheKey] !== undefined) {
      return storageDataCache[cacheKey];
    }
    for (var i = 0; i < prefixList.length; i++) {
      var p = prefixList[i];
      try {
        var val = localStorage.getItem(p + id);
        if (val) {
          storageDataCache[cacheKey] = val;
          return val;
        }
        val = localStorage.getItem(p + 'tournament_' + id);
        if (val) {
          storageDataCache[cacheKey] = val;
          return val;
        }
      } catch (e) {}
    }
    storageDataCache[cacheKey] = null;
    return null;
  }

  var champOfTourneyCache = {};
  /**
   * Helper to check if a team won a given tournament
   */
  function isTeamChampOfTourney(t, teamName, context) {
    if (!t || !teamName) return false;
    var memoKey = (t.id || t.name || '') + '__' + teamName;
    if (champOfTourneyCache[memoKey] !== undefined) {
      return champOfTourneyCache[memoKey];
    }

    // 1. Direct champion list from context
    var champList = context.championTourneys || [];
    for (var i = 0; i < champList.length; i++) {
      var ct = champList[i];
      if (ct && (ct.id === t.id || ct.tournamentId === t.id || (ct.name && ct.name === t.name))) {
        champOfTourneyCache[memoKey] = true;
        return true;
      }
    }

    // 2. Check tourneyPerformances in context
    var perfs = context.tourneyPerformances || [];
    for (var p = 0; p < perfs.length; p++) {
      var perf = perfs[p];
      if (perf && (perf.id === t.id || perf.name === t.name) && (perf.achievement === "Vô Địch" || perf.achievement === "Champion")) {
        champOfTourneyCache[memoKey] = true;
        return true;
      }
    }

    // 3. Check localStorage champion override: tourma_champion_ / tourma_final_champion_
    // This is the same reliable source the management page uses to display champions.
    var rawChamp = getStorageData(['tourma_champion_', 'tourma_final_champion_'], t.id);
    if (rawChamp) {
      try {
        var cName = extractName(rawChamp) || (typeof rawChamp === 'string' ? rawChamp.trim() : null);
        if (cName && isTeamSelf(cName, teamName)) {
          champOfTourneyCache[memoKey] = true;
          return true;
        }
      } catch (e) {}
    }

    // 3b. Check tournament object championName (from DB or engine)
    if (t.championName) {
      try {
        var dbChamp = extractName(t.championName) || (typeof t.championName === 'string' ? t.championName.trim() : null);
        if (dbChamp && isTeamSelf(dbChamp, teamName)) {
          champOfTourneyCache[memoKey] = true;
          return true;
        }
      } catch (e) {}
    }

    // 4. Bracket matches (Single Elim / Double Elim) — SKIP in server mode
    // Raw bracket localStorage data is unreliable; only used in client-only mode
    if (!context.serverMode && t.id) {
      var rawBracket = getStorageData(['tourma_bracket_', 'tourma_bracket_matches_', 'tourma_matches_'], t.id);
      if (rawBracket) {
        try {
          var bData = JSON.parse(rawBracket);
          var matchesMap = bData.matchesMap || bData;
          var roundsList = bData.roundsList || [];
          if (roundsList && roundsList.length > 0) {
            var finalRound = roundsList[roundsList.length - 1];
            if (finalRound && finalRound.matches && finalRound.matches.length > 0) {
              var finalM = finalRound.matches[0];
              if (finalM && (finalM.winnerId || finalM.winner || finalM.status === 'COMPLETED' || finalM.status === 'DONE')) {
                var wName = extractName(finalM.winner) || (finalM.winnerId === 'team1' ? extractName(finalM.team1) : extractName(finalM.team2));
                if (wName && isTeamSelf(wName, teamName)) {
                  champOfTourneyCache[memoKey] = true;
                  return true;
                }
              }
            }
          }
        } catch (e) {}
      }

      var rawDE = getStorageData(['tourma_de_matches_'], t.id);
      if (rawDE) {
        try {
          var deData = JSON.parse(rawDE);
          var gf = deData.grandFinalsRound;
          if (gf && gf.matches && gf.matches.length > 0) {
            var gfM = (gf.matches.length > 1 && gf.matches[1].winnerId) ? gf.matches[1] : gf.matches[0];
            if (gfM) {
              var wNameDE = extractName(gfM.winner) || (gfM.winnerId === 'team1' ? extractName(gfM.team1) : extractName(gfM.team2));
              if (wNameDE && isTeamSelf(wNameDE, teamName)) {
                champOfTourneyCache[memoKey] = true;
                return true;
              }
            }
          }
        } catch (e) {}
      }
    } // end !serverMode (bracket data only)

    champOfTourneyCache[memoKey] = false;
    return false;
  }

  /**
   * Helper to count cups won by tier for a given team
   */
  function getTierWinCounts(teamName, context) {
    var counts = { S: 0, A: 0, B: 0, C: 0, D: 0 };
    var subTourneys = context.subTournaments || window.seriesSubTournaments || [];
    // Use a Set-like object keyed by tournament id to prevent any double-counting
    var processed = {};

    // 1. Scan subTourneys — the authoritative list; mark ALL visited ids regardless of win
    for (var i = 0; i < subTourneys.length; i++) {
      var t = subTourneys[i];
      if (!t || !t.id) continue;
      // Always mark as processed so steps 2 & 3 won't re-count same tournament
      processed[t.id] = true;
      if (isTeamChampOfTourney(t, teamName, context)) {
        var tier = (t.tierName || t.tier || 'A').toUpperCase().trim();
        if (counts.hasOwnProperty(tier)) {
          counts[tier]++;
        }
      }
    }

    // 2. Scan championTourneys — only count tournaments NOT already seen in step 1
    var champList = context.championTourneys || [];
    for (var j = 0; j < champList.length; j++) {
      var ct = champList[j];
      if (!ct) continue;
      var ctId = ct.id || ct.tournamentId;
      // Skip if this tournament was already processed in step 1
      if (ctId && processed[ctId]) continue;

      var cTier = (ct.tier || ct.tierName || 'A').toUpperCase().trim();
      if (counts.hasOwnProperty(cTier)) {
        counts[cTier]++;
        if (ctId) processed[ctId] = true;
      }
    }

    // 3. Scan tourneyPerformances — only count tournaments NOT already seen in steps 1 or 2
    var perfs = context.tourneyPerformances || [];
    for (var k = 0; k < perfs.length; k++) {
      var perf = perfs[k];
      if (perf && (perf.achievement === 'Vô Địch' || perf.achievement === 'Champion')) {
        var pId = perf.id || perf.tournamentId;
        if (pId && processed[pId]) continue;

        var pTier = (perf.tier || perf.tierName || 'A').toUpperCase().trim();
        if (counts.hasOwnProperty(pTier)) {
          counts[pTier]++;
          if (pId) processed[pId] = true;
        }
      }
    }

    return counts;
  }

  // =========================================================================
  // BADGE REGISTRY & DEFINITIONS
  // =========================================================================
  var registry = [];

  /**
   * 1. INAUGURAL CHAMPION (Nhà vô địch mở màn)
   * Awarded to the team that won Tournament #1.
   */
  registry.push({
    id: 'INAUGURAL_CHAMPION',
    name: 'Inaugural Champion',
    category: 'HISTORIC',
    rarity: 'Huyền Thoại',
    themeClass: 'tourma-badge-gold',
    iconClass: 'fa-solid fa-crown',
    description: 'Nhà vô địch giải đấu đầu tiên trong lịch sử chuỗi giải.',
    evaluate: function (teamName, context) {
      if (!teamName || !context) return null;
      var subTourneys = context.subTournaments || window.seriesSubTournaments || [];
      if (!subTourneys || subTourneys.length === 0) return null;

      var firstTourney = subTourneys[0];
      if (!firstTourney) return null;

      if (isTeamChampOfTourney(firstTourney, teamName, context)) {
        var firstTourneyName = firstTourney.name || 'Giải #1';
        return {
          id: 'INAUGURAL_CHAMPION',
          title: 'Inaugural Champion',
          iconClass: 'fa-solid fa-crown',
          meta: {
            tourneyName: firstTourneyName,
            tier: (firstTourney.tierName || 'A').toUpperCase()
          }
        };
      }

      return null;
    }
  });

  /**
   * 2. DEFENDING CHAMPION (Đương kim vô địch)
   * Awarded to the reigning champion of the latest completed tournament in the series.
   */
  registry.push({
    id: 'DEFENDING_CHAMPION',
    name: 'Defending Champion',
    category: 'REIGNING',
    rarity: 'Đương Kim',
    themeClass: 'tourma-badge-defending',
    iconClass: 'fa-solid fa-shield-halved',
    description: 'Nhà vô địch của giải đấu gần nhất trong chuỗi giải.',
    evaluate: function (teamName, context) {
      if (!teamName || !context) return null;
      var subTourneys = context.subTournaments || window.seriesSubTournaments || [];
      if (!subTourneys || subTourneys.length === 0) return null;

      var latestChampTourney = null;
      for (var idx = subTourneys.length - 1; idx >= 0; idx--) {
        var t = subTourneys[idx];
        if (isTeamChampOfTourney(t, teamName, context)) {
          latestChampTourney = t;
          break;
        }
      }

      if (latestChampTourney) {
        var totalTourneys = subTourneys.length;
        var lastTourney = subTourneys[totalTourneys - 1];

        if (latestChampTourney.id === lastTourney.id || isTeamChampOfTourney(lastTourney, teamName, context)) {
          var tourneyName = latestChampTourney.name || ('Giải #' + totalTourneys);
          return {
            id: 'DEFENDING_CHAMPION',
            title: 'Defending Champion',
            themeClass: 'tourma-badge-defending',
            iconClass: 'fa-solid fa-shield-halved',
            meta: {
              tourneyName: tourneyName,
              tier: (latestChampTourney.tierName || 'A').toUpperCase()
            }
          };
        }
      }

      return null;
    }
  });

  /**
   * 3. TIER S WINNER (Tối thiểu 1 cúp Tier S; nếu 1 cúp -> "S-Tier Winner", nếu >= 2 -> "{n}x S-Tier Winner")
   */
  registry.push({
    id: 'TIER_S_WINNER',
    name: 'S-Tier Winner',
    category: 'TIER_CHAMPION',
    rarity: 'Tier S',
    themeClass: 'tourma-badge-tier-s',
    iconClass: 'fa-solid fa-trophy',
    evaluate: function (teamName, context) {
      var counts = getTierWinCounts(teamName, context);
      var n = counts['S'] || 0;
      if (n >= 1) {
        var name = (n === 1) ? 'S-Tier Winner' : (n + 'x S-Tier Winner');
        return {
          id: 'TIER_S_WINNER',
          name: name,
          title: name,
          themeClass: 'tourma-badge-tier-s',
          iconClass: 'fa-solid fa-trophy',
          rarity: 'Tier S',
          description: 'Đội đã đạt ' + n + ' lần vô địch các giải đấu Cấp độ S (Tier S).',
          meta: { count: n, tier: 'S' }
        };
      }
      return null;
    }
  });

  /**
   * 4. TIER A WINNER (Tối thiểu 2 cúp Tier A -> "{n}x A-Tier Winner")
   */
  registry.push({
    id: 'TIER_A_WINNER',
    name: 'A-Tier Winner',
    category: 'TIER_CHAMPION',
    rarity: 'Tier A',
    themeClass: 'tourma-badge-tier-a',
    iconClass: 'fa-solid fa-trophy',
    evaluate: function (teamName, context) {
      var counts = getTierWinCounts(teamName, context);
      var n = counts['A'] || 0;
      if (n >= 2) {
        var name = n + 'x A-Tier Winner';
        return {
          id: 'TIER_A_WINNER',
          name: name,
          title: name,
          themeClass: 'tourma-badge-tier-a',
          iconClass: 'fa-solid fa-trophy',
          rarity: 'Tier A',
          description: 'Đội đã đạt ' + n + ' lần vô địch các giải đấu Cấp độ A (Tier A).',
          meta: { count: n, tier: 'A' }
        };
      }
      return null;
    }
  });

  /**
   * 5. TIER B WINNER (Tối thiểu 3 cúp Tier B -> "{n}x B-Tier Winner")
   */
  registry.push({
    id: 'TIER_B_WINNER',
    name: 'B-Tier Winner',
    category: 'TIER_CHAMPION',
    rarity: 'Tier B',
    themeClass: 'tourma-badge-tier-b',
    iconClass: 'fa-solid fa-trophy',
    evaluate: function (teamName, context) {
      var counts = getTierWinCounts(teamName, context);
      var n = counts['B'] || 0;
      if (n >= 3) {
        var name = n + 'x B-Tier Winner';
        return {
          id: 'TIER_B_WINNER',
          name: name,
          title: name,
          themeClass: 'tourma-badge-tier-b',
          iconClass: 'fa-solid fa-trophy',
          rarity: 'Tier B',
          description: 'Đội đã đạt ' + n + ' lần vô địch các giải đấu Cấp độ B (Tier B).',
          meta: { count: n, tier: 'B' }
        };
      }
      return null;
    }
  });

  /**
   * 6. TIER C WINNER (Tối thiểu 4 cúp Tier C -> "{n}x C-Tier Winner")
   */
  registry.push({
    id: 'TIER_C_WINNER',
    name: 'C-Tier Winner',
    category: 'TIER_CHAMPION',
    rarity: 'Tier C',
    themeClass: 'tourma-badge-tier-c',
    iconClass: 'fa-solid fa-trophy',
    evaluate: function (teamName, context) {
      var counts = getTierWinCounts(teamName, context);
      var n = counts['C'] || 0;
      if (n >= 4) {
        var name = n + 'x C-Tier Winner';
        return {
          id: 'TIER_C_WINNER',
          name: name,
          title: name,
          themeClass: 'tourma-badge-tier-c',
          iconClass: 'fa-solid fa-trophy',
          rarity: 'Tier C',
          description: 'Đội đã đạt ' + n + ' lần vô địch các giải đấu Cấp độ C (Tier C).',
          meta: { count: n, tier: 'C' }
        };
      }
      return null;
    }
  });

  /**
   * 7. TIER D WINNER (Tối thiểu 5 cúp Tier D -> "{n}x D-Tier Winner")
   */
  registry.push({
    id: 'TIER_D_WINNER',
    name: 'D-Tier Winner',
    category: 'TIER_CHAMPION',
    rarity: 'Tier D',
    themeClass: 'tourma-badge-tier-d',
    iconClass: 'fa-solid fa-trophy',
    evaluate: function (teamName, context) {
      var counts = getTierWinCounts(teamName, context);
      var n = counts['D'] || 0;
      if (n >= 5) {
        var name = n + 'x D-Tier Winner';
        return {
          id: 'TIER_D_WINNER',
          name: name,
          title: name,
          themeClass: 'tourma-badge-tier-d',
          iconClass: 'fa-solid fa-trophy',
          rarity: 'Tier D',
          description: 'Đội đã đạt ' + n + ' lần vô địch các giải đấu Cấp độ D (Tier D).',
          meta: { count: n, tier: 'D' }
        };
      }
      return null;
    }
  });

  /**
   * Helper to find all champion streaks for a team across all sub-tournaments,
   * returning the longest consecutive championship win streak.
   */
  function getLongestChampionStreak(teamName, context) {
    if (!teamName || !context) return { length: 0, tourneys: [] };

    var bestStreakTourneys = [];
    var currentStreakTourneys = [];

    // 1. Primary Source: tourneyPerformances (actual sequence of tournaments played by the team)
    var perfs = (context.tourneyPerformances || []).slice();
    if (perfs && perfs.length > 0) {
      perfs.sort(function (a, b) {
        var sA = (a.stt !== undefined && a.stt !== null) ? a.stt : 0;
        var sB = (b.stt !== undefined && b.stt !== null) ? b.stt : 0;
        return sA - sB;
      });

      for (var p = 0; p < perfs.length; p++) {
        var perf = perfs[p];
        var isChamp = (perf.achievement === "Vô Địch" || perf.achievement === "Champion") ||
                      isTeamChampOfTourney({ id: perf.id || perf.tournamentId, name: perf.name }, teamName, context);
        if (isChamp) {
          currentStreakTourneys.push({
            id: perf.id || perf.tournamentId || '',
            name: perf.name || ('Giải #' + (p + 1)),
            tier: (perf.tier || perf.tierName || 'A').toUpperCase()
          });
          if (currentStreakTourneys.length >= bestStreakTourneys.length) {
            bestStreakTourneys = currentStreakTourneys.slice();
          }
        } else {
          currentStreakTourneys = [];
        }
      }

      if (bestStreakTourneys.length >= 2) {
        return {
          length: bestStreakTourneys.length,
          tourneys: bestStreakTourneys
        };
      }
    }

    // 2. Secondary Source: Evaluate across subTournaments (ignoring unplayed/non-participated events)
    var subTourneys = context.subTournaments || window.seriesSubTournaments || [];
    if (!subTourneys || subTourneys.length === 0) {
      return { length: bestStreakTourneys.length, tourneys: bestStreakTourneys };
    }

    // Sort tournaments by index to guarantee chronological order
    var sortedSub = subTourneys.slice().sort(function (a, b) {
      var idxA = (a.index !== undefined && a.index !== null) ? a.index : (a.tournamentIndexInSeries || 0);
      var idxB = (b.index !== undefined && b.index !== null) ? b.index : (b.tournamentIndexInSeries || 0);
      return idxA - idxB;
    });

    var subBestStreak = [];
    var subCurStreak = [];

    for (var i = 0; i < sortedSub.length; i++) {
      var t = sortedSub[i];
      var won = isTeamChampOfTourney(t, teamName, context);
      if (won) {
        subCurStreak.push({
          id: t.id,
          name: t.name || ('Giải #' + (t.index || (i + 1))),
          tier: (t.tierName || t.tier || 'A').toUpperCase()
        });
        if (subCurStreak.length >= subBestStreak.length) {
          subBestStreak = subCurStreak.slice();
        }
      } else {
        // Only reset streak if the team actually played in this tournament and lost
        var teamPlayed = false;
        if (perfs && perfs.length > 0) {
          teamPlayed = perfs.some(function (pf) {
            return (pf.id === t.id || pf.tournamentId === t.id || (pf.name && pf.name === t.name));
          });
        }
        if (teamPlayed) {
          subCurStreak = [];
        }
      }
    }

    if (subBestStreak.length > bestStreakTourneys.length) {
      bestStreakTourneys = subBestStreak;
    }

    return {
      length: bestStreakTourneys.length,
      tourneys: bestStreakTourneys
    };
  }

  /**
   * 8. BACK-2-BACK (Vô địch 2 giải liên tiếp)
   */
  registry.push({
    id: 'BACK_TO_BACK',
    name: 'Back-2-Back',
    category: 'STREAK',
    rarity: 'Đặc Biệt',
    themeClass: 'tourma-badge-streak',
    iconClass: 'fa-solid fa-fire',
    description: 'Vô địch 2 giải đấu liên tiếp trong chuỗi giải.',
    evaluate: function (teamName, context) {
      var streak = getLongestChampionStreak(teamName, context);
      if (streak.length >= 2) {
        return {
          id: 'BACK_TO_BACK',
          name: 'Back-2-Back',
          title: 'Back-2-Back',
          themeClass: 'tourma-badge-streak',
          iconClass: 'fa-solid fa-fire',
          rarity: 'Đặc Biệt',
          description: 'Đội đã đạt thành tích vô địch 2 giải đấu liên tiếp.',
          meta: {
            count: 2,
            streakTourneys: streak.tourneys.slice(0, 2)
          }
        };
      }
      return null;
    }
  });

  /**
   * 9. WINNING STREAK (Vô địch 3+ giải liên tiếp -> "{n}x Winning Streak")
   */
  registry.push({
    id: 'WINNING_STREAK',
    name: 'Winning Streak',
    category: 'STREAK',
    rarity: 'Huyền Thoại',
    themeClass: 'tourma-badge-streak-fire',
    iconClass: 'fa-solid fa-fire-flame-curved',
    description: 'Vô địch 3 giải đấu liên tiếp trở lên trong chuỗi giải.',
    evaluate: function (teamName, context) {
      var streak = getLongestChampionStreak(teamName, context);
      if (streak.length >= 3) {
        var badgeTitle = streak.length + 'x Winning Streak';
        return {
          id: 'WINNING_STREAK',
          name: badgeTitle,
          title: badgeTitle,
          themeClass: 'tourma-badge-streak-fire',
          iconClass: 'fa-solid fa-fire-flame-curved',
          rarity: 'Huyền Thoại',
          description: 'Thống trị tuyệt đối với chuỗi ' + streak.length + ' giải đấu vô địch liên tiếp!',
          meta: {
            count: streak.length,
            streakTourneys: streak.tourneys
          }
        };
      }
      return null;
    }
  });

  // =========================================================================
  // BADGE ENGINE PUBLIC API
  // =========================================================================
  var TeamBadgeEngine = {

    registerBadge: function (badgeDef) {
      if (badgeDef && badgeDef.id && typeof badgeDef.evaluate === 'function') {
        registry.push(badgeDef);
      }
    },

    evaluateTeamBadges: function (teamName, context) {
      if (!teamName) return [];
      var earnedBadges = [];
      registry.forEach(function (badgeDef) {
        try {
          var result = badgeDef.evaluate(teamName, context || {});
          if (result) {
            earnedBadges.push(Object.assign({}, badgeDef, result));
          }
        } catch (err) {
          console.warn('[TeamBadgeEngine] Error evaluating badge ' + badgeDef.id, err);
        }
      });
      return earnedBadges;
    },

    renderBadges: function (containerElementOrId, teamName, context) {
      var container = (typeof containerElementOrId === 'string') 
        ? document.getElementById(containerElementOrId) 
        : containerElementOrId;

      if (!container) return;

      // Clear caches on each render to prevent cross-team contamination
      champOfTourneyCache = {};
      storageDataCache = {};

      var earned = this.evaluateTeamBadges(teamName, context);

      if (earned.length === 0) {
        container.innerHTML = '';
        return;
      }

      var html = '';
      earned.forEach(function (b) {
        var tourneyName = (b.meta && b.meta.tourneyName) ? b.meta.tourneyName : '';
        var iconClass = b.iconClass || 'fa-solid fa-trophy';
        var isDefending = (b.id === 'DEFENDING_CHAMPION' || (b.themeClass && b.themeClass.indexOf('defending') !== -1));

        var circuitSvg = isDefending ? (
          '<svg class="tourma-badge-svg-border" aria-hidden="true">' +
            '<defs>' +
              '<linearGradient id="defendingBeamGrad" x1="0%" y1="0%" x2="100%" y2="100%">' +
                '<stop offset="0%" stop-color="#facc15" stop-opacity="0.1" />' +
                '<stop offset="40%" stop-color="#fde047" stop-opacity="0.7" />' +
                '<stop offset="85%" stop-color="#ffffff" stop-opacity="1" />' +
                '<stop offset="100%" stop-color="#ffffff" stop-opacity="1" />' +
              '</linearGradient>' +
            '</defs>' +
            '<rect x="0.75" y="0.75" width="calc(100% - 1.5px)" height="calc(100% - 1.5px)" rx="13.25" ry="13.25" fill="none" stroke="url(#defendingBeamGrad)" stroke-width="1.5" stroke-linecap="round" pathLength="100" class="defending-beam-stroke" />' +
          '</svg>'
        ) : '';

        var footerText = '';
        if (b.meta && b.meta.streakTourneys && b.meta.streakTourneys.length > 0) {
          var itemsHtml = b.meta.streakTourneys.map(function(t) {
            var tName = t.name || t;
            var tTier = t.tier ? (' (' + t.tier + ')') : '';
            return '<span class="streak-tourney-item"><i class="fa-solid fa-trophy"></i> ' + tName + tTier + '</span>';
          }).join('');
          footerText = '<div class="tourma-badge-tooltip-footer streak-footer">' +
            '<div class="streak-footer-title"><i class="fa-solid fa-fire"></i> Các giải đã vô địch:</div>' +
            '<div class="streak-tourney-list">' + itemsHtml + '</div>' +
          '</div>';
        } else if (b.category === 'TIER_CHAMPION' && b.meta && b.meta.tier) {
          footerText = '<div class="tourma-badge-tooltip-footer"><i class="fa-solid fa-award"></i> Danh hiệu vô địch Tier ' + b.meta.tier + ' (' + b.meta.count + ' cúp)</div>';
        } else if (tourneyName) {
          footerText = '<div class="tourma-badge-tooltip-footer"><i class="fa-solid fa-trophy"></i> Vô địch ' + tourneyName + '</div>';
        }

        html += '<div class="tourma-badge-pill ' + (b.themeClass || 'tourma-badge-gold') + '" tabindex="0" title="' + b.name + '">' +
          circuitSvg +
          '<i class="' + iconClass + ' badge-icon"></i>' +
          '<span class="tourma-badge-name">' + b.name + '</span>' +
          '<!-- Tooltip on Hover -->' +
          '<div class="tourma-badge-tooltip">' +
            '<div class="tourma-badge-tooltip-header">' +
              '<span class="tourma-badge-tooltip-title"><i class="' + iconClass + '"></i> ' + b.name + '</span>' +
              '<span class="tourma-badge-tooltip-rarity">' + (b.rarity || 'Đặc Biệt') + '</span>' +
            '</div>' +
            '<div class="tourma-badge-tooltip-desc">' + b.description + '</div>' +
            footerText +
          '</div>' +
        '</div>';
      });

      container.innerHTML = html;
    }
  };

  return TeamBadgeEngine;
}));
