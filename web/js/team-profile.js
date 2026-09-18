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
      if (prof.currentPoints === undefined || prof.currentPoints === null) {
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
        totalAccumulatedPoints: prof.totalAccumulatedPoints,
        phaseBadges: prof.phaseBadges || [],
        phaseSize: window.seriesPhaseSize || 3
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
    // 11. Render Rank Progression Chart
    if (prof && prof.rankProgression && prof.rankProgression.length > 0) {
      window.teamRankProgression = prof.rankProgression;
    }
    renderRankProgressionChart();
  }

  var currentChartInstance = null;

  function renderRankProgressionChart() {
    var canvas = document.getElementById('teamRankChart');
    if (!canvas) return;

    var data = window.teamRankProgression || [];
    if (!data || data.length === 0) {
      var card = document.getElementById('rankProgressionCard');
      if (card) {
        var wrapper = card.querySelector('.rank-chart-wrapper');
        if (wrapper) {
          wrapper.innerHTML = '<div style="display: flex; align-items: center; justify-content: center; height: 100%; color: var(--text-muted); font-size: 0.9rem;">Chưa có dữ liệu biến động thứ hạng cho đội này.</div>';
        }
      }
      return;
    }

    var labels = [];
    var ranks = [];
    var pointBgColors = [];
    var pointBorderColors = [];
    var pointRadii = [];
    var pointHoverRadii = [];

    var maxRankFound = 1;
    data.forEach(function (item) {
      if (item.rank > maxRankFound) maxRankFound = item.rank;
    });
    var yAxisMax = Math.max(maxRankFound + 2, 8);

    var totalPoints = data.length;
    var top1R = totalPoints > 20 ? 3.5 : (totalPoints > 12 ? 4.2 : 5);
    var partR = totalPoints > 20 ? 2.8 : (totalPoints > 12 ? 3.4 : 4);
    var notPartR = totalPoints > 20 ? 1.8 : (totalPoints > 12 ? 2.2 : 2.8);

    var top1HoverR = top1R + 2.5;
    var partHoverR = partR + 2.5;
    var notPartHoverR = notPartR + 2;

    data.forEach(function (item) {
      var tLabel = '#' + item.tournamentIndex + ' ' + (item.tournamentName || '');
      if (tLabel.length > 20) tLabel = tLabel.substring(0, 18) + '...';
      labels.push(tLabel);
      ranks.push(item.rank > 0 ? item.rank : null);

      var isTop1 = (item.rank === 1);
      if (isTop1) {
        pointBgColors.push('#fbbf24');
        pointBorderColors.push('#ffffff');
        pointRadii.push(top1R);
        pointHoverRadii.push(top1HoverR);
      } else if (item.participated) {
        pointBgColors.push('#2dd4bf');
        pointBorderColors.push('#0f172a');
        pointRadii.push(partR);
        pointHoverRadii.push(partHoverR);
      } else {
        pointBgColors.push('#64748b');
        pointBorderColors.push('#0f172a');
        pointRadii.push(notPartR);
        pointHoverRadii.push(notPartHoverR);
      }
    });

    if (typeof Chart !== 'undefined') {
      if (currentChartInstance) {
        currentChartInstance.destroy();
        currentChartInstance = null;
      }

      var ctx = canvas.getContext('2d');

      currentChartInstance = new Chart(ctx, {
        type: 'line',
        data: {
          labels: labels,
          datasets: [{
            label: 'Thứ hạng BXH',
            data: ranks,
            borderColor: '#2dd4bf',
            borderWidth: 2.2,
            backgroundColor: 'transparent',
            fill: false,
            tension: 0.35,
            pointBackgroundColor: pointBgColors,
            pointBorderColor: pointBorderColors,
            pointBorderWidth: 1.5,
            pointRadius: pointRadii,
            pointHoverRadius: pointHoverRadii,
            clip: false,
            spanGaps: true
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          layout: {
            padding: {
              top: 24,
              bottom: 12,
              left: 10,
              right: 15
            }
          },
          interaction: {
            mode: 'index',
            intersect: false
          },
          plugins: {
            legend: {
              display: false
            },
            tooltip: {
              backgroundColor: 'rgba(15, 23, 42, 0.95)',
              titleColor: '#ffffff',
              titleFont: { family: 'Lexend, sans-serif', weight: '700', size: 13 },
              bodyColor: '#cbd5e1',
              bodyFont: { family: 'Lexend, sans-serif', size: 12 },
              borderColor: 'rgba(45, 212, 191, 0.4)',
              borderWidth: 1,
              padding: 12,
              cornerRadius: 10,
              displayColors: false,
              callbacks: {
                title: function (context) {
                  var idx = context[0].dataIndex;
                  var item = data[idx];
                  return 'Giải #' + item.tournamentIndex + ': ' + item.tournamentName;
                },
                label: function (context) {
                  var idx = context.dataIndex;
                  var item = data[idx];
                  var lines = [];
                  lines.push('Thứ hạng BXH: #' + item.rank);
                  lines.push('Điểm tích lũy: ' + item.totalActivePoints + ' pts');
                  if (item.participated) {
                    lines.push('Thành tích: ' + item.achievement + ' (+' + item.pointsEarned + ' pts)');
                  } else {
                    lines.push('Thành tích: Không tham gia');
                  }
                  return lines;
                }
              }
            }
          },
          scales: {
            y: {
              reverse: true,
              min: 0.4,
              max: yAxisMax + 0.4,
              grid: {
                color: function (context) {
                  if (context.tick && (context.tick.value < 0.9 || context.tick.value > yAxisMax + 0.1)) {
                    return 'transparent';
                  }
                  return 'rgba(255, 255, 255, 0.06)';
                }
              },
              ticks: {
                precision: 0,
                color: '#94a3b8',
                font: { family: 'Lexend, sans-serif', weight: '600', size: 11 },
                stepSize: (yAxisMax <= 8) ? 1 : Math.max(1, Math.round(yAxisMax / 6)),
                callback: function (val) {
                  if (val >= 1 && val <= yAxisMax && Number.isInteger(val)) {
                    return '#' + val;
                  }
                  return '';
                }
              }
            },
            x: {
              grid: {
                color: 'rgba(255, 255, 255, 0.03)'
              },
              ticks: {
                color: '#94a3b8',
                font: { family: 'Lexend, sans-serif', size: 11 },
                maxRotation: 40,
                minRotation: 0
              }
            }
          }
        }
      });
    } else {
      renderCanvasFallback(canvas, data, labels, ranks, yAxisMax);
    }
  }

  function renderCanvasFallback(canvas, data, labels, ranks, yAxisMax) {
    var ctx = canvas.getContext('2d');
    var w = canvas.parentElement.clientWidth || 800;
    var h = canvas.parentElement.clientHeight || 320;
    canvas.width = w * (window.devicePixelRatio || 1);
    canvas.height = h * (window.devicePixelRatio || 1);
    canvas.style.width = w + 'px';
    canvas.style.height = h + 'px';
    ctx.scale(window.devicePixelRatio || 1, window.devicePixelRatio || 1);

    var padLeft = 45, padRight = 25, padTop = 35, padBottom = 45;
    var plotW = w - padLeft - padRight;
    var plotH = h - padTop - padBottom;

    ctx.clearRect(0, 0, w, h);

    ctx.strokeStyle = 'rgba(255, 255, 255, 0.06)';
    ctx.fillStyle = '#94a3b8';
    ctx.font = '11px Lexend, sans-serif';
    ctx.textAlign = 'right';

    var ySteps = 5;
    for (var s = 0; s <= ySteps; s++) {
      var rVal = Math.round(1 + (s / ySteps) * (yAxisMax - 1));
      var yPos = padTop + 12 + (s / ySteps) * (plotH - 24);
      ctx.beginPath();
      ctx.moveTo(padLeft, yPos);
      ctx.lineTo(w - padRight, yPos);
      ctx.stroke();
      ctx.fillText('#' + rVal, padLeft - 8, yPos + 4);
    }

    if (data.length < 1) return;

    var pts = [];
    for (var i = 0; i < data.length; i++) {
      var x = padLeft + (i / Math.max(1, data.length - 1)) * plotW;
      var r = data[i].rank > 0 ? data[i].rank : yAxisMax;
      var y = padTop + 12 + ((r - 1) / Math.max(1, yAxisMax - 1)) * (plotH - 24);
      pts.push({ x: x, y: y, item: data[i] });
    }

    ctx.beginPath();
    ctx.strokeStyle = '#2dd4bf';
    ctx.lineWidth = 3;
    for (var i = 0; i < pts.length; i++) {
      if (i === 0) ctx.moveTo(pts[i].x, pts[i].y);
      else ctx.lineTo(pts[i].x, pts[i].y);
    }
    ctx.stroke();

    var totalPtsCount = data.length;
    var fbTop1R = totalPtsCount > 20 ? 3.5 : (totalPtsCount > 12 ? 4.2 : 5);
    var fbPartR = totalPtsCount > 20 ? 2.8 : (totalPtsCount > 12 ? 3.4 : 4);
    var fbNotPartR = totalPtsCount > 20 ? 1.8 : (totalPtsCount > 12 ? 2.2 : 2.8);

    for (var i = 0; i < pts.length; i++) {
      var p = pts[i];
      var isTop1 = (p.item.rank === 1);
      ctx.beginPath();
      ctx.arc(p.x, p.y, isTop1 ? fbTop1R : (p.item.participated ? fbPartR : fbNotPartR), 0, Math.PI * 2);
      ctx.fillStyle = isTop1 ? '#fbbf24' : (p.item.participated ? '#2dd4bf' : '#64748b');
      ctx.fill();
      ctx.strokeStyle = isTop1 ? '#ffffff' : '#0f172a';
      ctx.lineWidth = 1.5;
      ctx.stroke();
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
    window.addEventListener('resize', debouncedSync);
  }

})();
