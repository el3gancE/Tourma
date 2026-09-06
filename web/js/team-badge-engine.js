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
    if (!prefixList || prefixList.length === 0 || !id) return null;
    var cacheKey = prefixList.join('|') + '__' + id;
    if (storageDataCache[cacheKey] !== undefined) {
      return storageDataCache[cacheKey];
    }
    for (var i = 0; i < prefixList.length; i++) {
      var p = prefixList[i];
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

    // 3. Check localStorage champion override: tourma_champion_
    var rawChamp = getStorageData(['tourma_champion_'], t.id);
    if (rawChamp) {
      try {
        var cName = extractName(rawChamp) || (typeof rawChamp === 'string' ? rawChamp.trim() : null);
        if (cName && isTeamSelf(cName, teamName)) {
          champOfTourneyCache[memoKey] = true;
          return true;
        }
      } catch (e) {}
    }

    // 4. Bracket matches (Single Elim / Double Elim)
    if (t.id) {
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
    }

    champOfTourneyCache[memoKey] = false;
    return false;
  }

  /**
   * Helper to count cups won by tier for a given team
   */
  function getTierWinCounts(teamName, context) {
    var counts = { S: 0, A: 0, B: 0, C: 0, D: 0 };
    var subTourneys = context.subTournaments || window.seriesSubTournaments || [];
    var processed = {};

    // 1. Scan subTourneys
    for (var i = 0; i < subTourneys.length; i++) {
      var t = subTourneys[i];
      if (!t || !t.id) continue;
      if (isTeamChampOfTourney(t, teamName, context)) {
        var tier = (t.tierName || t.tier || 'A').toUpperCase().trim();
        if (counts.hasOwnProperty(tier)) {
          counts[tier]++;
          processed[t.id] = true;
        }
      }
    }

    // 2. Scan championTourneys
    var champList = context.championTourneys || [];
    for (var j = 0; j < champList.length; j++) {
      var ct = champList[j];
      if (!ct) continue;
      var ctId = ct.id || ct.tournamentId;
      if (ctId && processed[ctId]) continue;

      var cTier = (ct.tier || ct.tierName || 'A').toUpperCase().trim();
      if (counts.hasOwnProperty(cTier)) {
        counts[cTier]++;
        if (ctId) processed[ctId] = true;
      }
    }

    // 3. Scan tourneyPerformances
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
        if (b.category === 'TIER_CHAMPION' && b.meta && b.meta.tier) {
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
