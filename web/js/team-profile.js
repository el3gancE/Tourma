/**
 * TOURMA - UNIFIED TEAM PROFILE SCRIPT (team-profile.js)
 * Synchronizes Server + LocalStorage sub-tournament placements and BXH series stats.
 * Uses canonical TourmaRollingStandingsEngine to compute exact live team stats, W-L records,
 * achievements, tournament performance table, and TeamBadgeEngine honors.
 */

(function () {
  'use strict';

  function calculateAndSyncTeamProfile() {
    var targetTeamName = (window.profileTeamName || '').trim();
    if (!targetTeamName) return;

    if (typeof TourmaRollingStandingsEngine === 'undefined' || !TourmaRollingStandingsEngine.getTeamProfile) {
      console.warn('[TeamProfile] TourmaRollingStandingsEngine is not loaded.');
      if (typeof TeamBadgeEngine !== 'undefined' && TeamBadgeEngine.renderBadges) {
        TeamBadgeEngine.renderBadges('teamBadgesContainer', targetTeamName, {
          subTournaments: window.seriesSubTournaments || [],
          championTourneys: [],
          tourneyPerformances: []
        });
      }
      return;
    }

    var prof = TourmaRollingStandingsEngine.getTeamProfile(targetTeamName, {
      seriesId: window.seriesIdVal,
      partners: window.seriesPartners || [],
      subTourneys: window.seriesSubTournaments || [],
      phaseSize: window.seriesPhaseSize || 26,
      serverTourneyPoints: window.serverTourneyPoints || [],
      serverTourneyParticipation: window.serverTourneyParticipation || []
    });

    if (!prof) {
      console.log('[TeamProfile] No profile stats found for:', targetTeamName);
      if (typeof TeamBadgeEngine !== 'undefined' && TeamBadgeEngine.renderBadges) {
        TeamBadgeEngine.renderBadges('teamBadgesContainer', targetTeamName, {
          subTournaments: window.seriesSubTournaments || [],
          championTourneys: [],
          tourneyPerformances: []
        });
      }
      return;
    }

    // 1. Current Rank
    var rEl = document.getElementById('profCurrentRank');
    if (rEl) {
      rEl.textContent = (prof.currentRank > 0) ? ('#' + prof.currentRank) : '-';
      if (prof.currentRank === 1) {
        rEl.classList.add('color-gold');
      } else {
        rEl.classList.remove('color-gold');
      }
      var curBox = rEl.closest('.team-stat-box');
      if (curBox) {
        if (prof.currentRank === 1) curBox.classList.add('highlight-gold');
        else curBox.classList.remove('highlight-gold');
      }
    }

    // 2. Highest Rank
    var hEl = document.getElementById('profHighestRank');
    if (hEl) {
      var hHtml = (prof.highestRank > 0) ? ('#' + prof.highestRank) : '-';
      if (prof.highestRank > 0 && prof.highestRankTourneyName) {
        var hUrl = prof.highestRankTourneyUrl || '#';
        hHtml += '<span class="rank-tourney-name">(<a href="' + hUrl + '" class="rank-tourney-link" style="color: inherit; text-decoration: underline; text-underline-offset: 3px;" title="Xem giai đoạn cuối giải ' + prof.highestRankTourneyName + '">' + prof.highestRankTourneyName + '</a>)</span>';
      }
      hEl.innerHTML = hHtml;
      if (prof.highestRank === 1 && prof.currentPoints > 0) {
        hEl.classList.add('color-gold');
      } else {
        hEl.classList.remove('color-gold');
      }
      var hBox = hEl.closest('.team-stat-box');
      if (hBox) {
        if (prof.highestRank === 1 && prof.currentPoints > 0) hBox.classList.add('highlight-gold');
        else hBox.classList.remove('highlight-gold');
      }
    }

    // Guarantee that totalAccumulatedPoints strictly equals the sum of pointsEarned in prof.performances
    var tablePointsSum = 0;
    if (prof.performances && prof.performances.length > 0) {
      prof.performances.forEach(function (perf) {
        tablePointsSum += (perf.pointsEarned || 0);
      });
      prof.totalAccumulatedPoints = tablePointsSum;
      var phaseSize = window.seriesPhaseSize || 26;
      if (prof.playedCount <= phaseSize || prof.performances.length <= phaseSize) {
        prof.currentPoints = tablePointsSum;
      }
    }

    // 3. Current Points
    var cpEl = document.getElementById('profCurrentPoints');
    if (cpEl) {
      cpEl.innerHTML = (prof.currentPoints || 0) + '<span class="pts-unit">pts</span>';
    }

    // 4. Total Accumulated Points
    var tpEl = document.getElementById('profTotalPoints');
    if (tpEl) {
      tpEl.innerHTML = (prof.totalAccumulatedPoints || 0) + '<span class="pts-unit">pts</span>';
    }

    // 5. Win / Loss
    var wlEl = document.getElementById('profWinLoss');
    if (wlEl) {
      var w = prof.wins || 0;
      var l = prof.losses || 0;
      if (w === 0 && l === 0 && prof.performances && prof.performances.length > 0) {
        prof.performances.forEach(function (perf) {
          var isMulti = perf.format && perf.format.indexOf('➔') !== -1;
          var d = (TourmaRollingStandingsEngine && TourmaRollingStandingsEngine.deduceMatchStatsFromAchievement) ?
                  TourmaRollingStandingsEngine.deduceMatchStatsFromAchievement(perf.format, perf.achievement, isMulti) :
                  { wins: 0, losses: 1 };
          w += d.wins;
          l += d.losses;
        });
      }
      wlEl.textContent = w + 'W - ' + l + 'L';
    }

    // 6. Tournaments Played
    var tpCountEl = document.getElementById('profTourneysPlayed');
    if (tpCountEl) {
      tpCountEl.innerHTML = (prof.playedCount || 0) + '<span class="pts-unit" style="font-size: 0.95rem; font-weight: 700;">Giải</span>';
    }

    // 7. Achievement Cards
    var ccEl = document.getElementById('profChampCount');
    if (ccEl) ccEl.innerHTML = (prof.champCount || 0) + '<span class="pts-unit" style="font-size: 0.95rem; font-weight: 700; color: #ffffff;">Lần</span>';

    var ruEl = document.getElementById('profRunnerUpCount');
    if (ruEl) ruEl.innerHTML = (prof.runnerUpCount || 0) + '<span class="pts-unit" style="font-size: 0.95rem; font-weight: 700; color: #ffffff;">Lần</span>';

    var scEl = document.getElementById('profSemiCount');
    if (scEl) scEl.innerHTML = (prof.semiCount || 0) + '<span class="pts-unit" style="font-size: 0.95rem; font-weight: 700; color: #ffffff;">Lần</span>';

    var qcEl = document.getElementById('profQuarterCount');
    if (qcEl) qcEl.innerHTML = (prof.quarterCount || 0) + '<span class="pts-unit" style="font-size: 0.95rem; font-weight: 700; color: #ffffff;">Lần</span>';

    // 8. Champion Badges Section (Only if champCount > 0)
    var elChampSection = document.getElementById('profChampionSection');
    var elChampBadges = document.getElementById('profChampionBadges');
    if (elChampSection) {
      if (prof.champCount > 0 && prof.championTourneys && prof.championTourneys.length > 0) {
        elChampSection.style.display = '';
        if (elChampBadges) {
          var reversedChampList = prof.championTourneys.slice().reverse();
          elChampBadges.innerHTML = reversedChampList.map(function (ct) {
            var bUrl = ct.finalStageUrl || '#';
            var tier = (ct.tier || ct.tierName || 'A').toUpperCase();
            return '<a href="' + bUrl + '" class="champion-badge-pill" data-tournament-id="' + (ct.id || ct.tournamentId || '') + '" data-tier="' + tier + '" style="text-decoration: none; cursor: pointer;" title="Xem giai đoạn cuối giải ' + ct.name + '">' +
              '<i class="fa-solid fa-crown"></i> ' + ct.name +
            '</a>';
          }).join('');
        }
      } else {
        elChampSection.style.display = 'none';
      }
    }

    // 9. Render Special Honors & Badges using TeamBadgeEngine
    if (typeof TeamBadgeEngine !== 'undefined' && TeamBadgeEngine.renderBadges) {
      TeamBadgeEngine.renderBadges('teamBadgesContainer', targetTeamName, {
        subTournaments: window.seriesSubTournaments || [],
        championTourneys: prof.championTourneys || [],
        tourneyPerformances: prof.performances || [],
        currentRank: prof.currentRank,
        highestRank: prof.highestRank,
        totalAccumulatedPoints: prof.totalAccumulatedPoints
      });
    }

    // 10. Update Performance Table (Most recent tournament at top, highest STT at top)
    var elTbody = document.getElementById('profPerformanceTbody');
    if (elTbody) {
      if (prof.performances && prof.performances.length > 0) {
        var reversedPerformances = prof.performances.slice().reverse();
        var rowsHtml = '';
        reversedPerformances.forEach(function (perf, idx) {
          var achClass = "muted";
          if (perf.achievement === "Vô Địch" || perf.achievement === "Champion") achClass = "champ";
          else if (perf.achievement === "Á Quân" || perf.achievement === "Runner-Up") achClass = "runner-up";
          else if (perf.achievement === "Bán Kết" || perf.achievement === "Semi-Finals") achClass = "semi";
          else if (perf.achievement === "Tứ Kết" || perf.achievement === "Quarter-Finals") achClass = "quarter";

          var sttVal = (perf.stt !== undefined && perf.stt !== null && perf.stt > 0) ? perf.stt : (reversedPerformances.length - idx);
          var tourneyUrl = perf.finalStageUrl || '#';
          var pTier = (perf.tier || perf.tierName || 'A').toUpperCase();

          rowsHtml += '<tr>' +
            '<td style="font-weight: 700; color: var(--text-muted);">' + sttVal + '</td>' +
            '<td style="font-weight: 700; color: #ffffff;">' +
              '<a href="' + tourneyUrl + '" class="tourney-name-link" style="color: #ffffff; text-decoration: none; font-weight: 700; transition: color 0.18s ease;" onmouseover="this.style.color=\'#2dd4bf\'" onmouseout="this.style.color=\'#ffffff\'" title="Xem giai đoạn cuối giải ' + perf.name + '">' +
                perf.name +
              '</a>' +
              '<span class="tier-tag tier-' + pTier.toLowerCase() + '" style="margin-left: 0.55rem;">[' + pTier + ']</span>' +
            '</td>' +
            '<td style="color: var(--text-muted); font-size: 0.82rem; font-weight: 600;">' + perf.format + '</td>' +
            '<td><span class="achievement-text ' + achClass + '">' + perf.achievement + '</span></td>' +
            '<td style="text-align: right; font-weight: 800; color: #fbbf24;">+' + (perf.pointsEarned || 0) + ' pts</td>' +
          '</tr>';
        });
        elTbody.innerHTML = rowsHtml;
      } else {
        elTbody.innerHTML = '<tr><td colspan="5" style="text-align: center; padding: 2.5rem; color: var(--text-muted);">Chưa có dữ liệu thành tích thi đấu giải con nào trong Series này.</td></tr>';
      }
    }
  }

  // Debounced runner for storage events
  var syncTimeout = null;
  function debouncedSync() {
    if (syncTimeout) clearTimeout(syncTimeout);
    syncTimeout = setTimeout(function () {
      calculateAndSyncTeamProfile();
    }, 20);
  }

  // Auto-run on DOM ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', calculateAndSyncTeamProfile);
  } else {
    calculateAndSyncTeamProfile();
  }

  if (typeof window !== 'undefined' && window.addEventListener) {
    window.addEventListener('storage', debouncedSync);
  }

})();
