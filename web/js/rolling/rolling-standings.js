/** Displays server-calculated series standings. Browser caches never supply points. */
(function () {
  'use strict';
  var snapshotRequest = 0;

  // =========================================================================
  // MAIN STANDINGS RECALCULATION BY MILESTONE
  // =========================================================================
  async function computeStandingsForMilestone(milestoneVal) {
    var standingsTable = document.getElementById('rollingStandingsTable');
    if (!standingsTable) return;

    var tbody = standingsTable.querySelector('tbody');
    if (!tbody) return;

    var requestId = ++snapshotRequest;
    var result;
    var contextPath = window.appContextPath || '';
    var params = new URLSearchParams({
      action: 'snapshot',
      id: window.seriesIdVal || '',
      milestone: milestoneVal == null ? 'LATEST' : milestoneVal
    });
    try {
      var response = await fetch(contextPath + '/rolling/standings?' + params, { cache: 'no-store' });
      if (!response.ok) throw new Error('Unable to load standings');
      result = await response.json();
      if (!Array.isArray(result.teamDataArray)) throw new Error('Invalid standings snapshot');
    } catch (error) {
      if (requestId !== snapshotRequest) return;
      var errorLabel = document.getElementById('standingsLoadError');
      if (!errorLabel) {
        errorLabel = document.createElement('p');
        errorLabel.id = 'standingsLoadError';
        errorLabel.setAttribute('role', 'alert');
        standingsTable.parentNode.insertBefore(errorLabel, standingsTable);
      }
      errorLabel.textContent = 'Không tải được bảng xếp hạng từ DB. Vui lòng tải lại trang.';
      return;
    }
    if (requestId !== snapshotRequest) return;
    var previousError = document.getElementById('standingsLoadError');
    if (previousError) previousError.remove();

    var teamDataArray = result.teamDataArray;
    var targetIdx = result.targetIdx;
    var subTourneys = window.seriesSubTournaments || [];
    var phaseSize = window.seriesPhaseSize || 3;
    var targetTourney = (targetIdx >= 0 && targetIdx < subTourneys.length) ? subTourneys[targetIdx] : null;

    // Update Header Title & Milestone Label
    var tableTitle = document.getElementById('standingsTableTitle');
    var thLastPts = document.getElementById('thLastPts');

    if (tableTitle) {
      if (milestoneVal === 'LATEST' || milestoneVal === undefined || milestoneVal === null) {
        tableTitle.textContent = 'Bảng Xếp Hạng Chi Tiết (Cửa Sổ Trượt W = ' + phaseSize + ' Giải)';
      } else if (targetTourney) {
        tableTitle.textContent = 'Bảng Xếp Hạng Tính Đến: ' + targetTourney.name + ' (Giải #' + (targetIdx + 1) + ')';
      }
    }

    if (thLastPts) {
      if (milestoneVal === 'LATEST' || milestoneVal === undefined || milestoneVal === null) {
        thLastPts.textContent = 'Điểm giải gần nhất';
      } else {
        thLastPts.textContent = 'Điểm giải #' + (targetIdx + 1);
      }
    }

    // Render Table DOM cleanly with full exact rankings & fluctuations
    tbody.innerHTML = '';
    var ctx = (typeof window.appContextPath !== 'undefined' && window.appContextPath) ? window.appContextPath : '';
    var seriesId = (typeof window.seriesIdVal !== 'undefined' && window.seriesIdVal) ? window.seriesIdVal : '';

    var frag = document.createDocumentFragment();

    teamDataArray.forEach(function (data, rankIdx) {
      var rank = data.rank || (rankIdx + 1);
      var rankChange = data.rankChange || 0;

      var rankChangeHtml = '';
      if (targetIdx >= 1) {
        if (rankChange > 0) {
          rankChangeHtml = '<span class="rank-change up" title="Tăng ' + rankChange + ' bậc"><i class="fa-solid fa-arrow-up"></i> ' + rankChange + '</span>';
        } else if (rankChange < 0) {
          rankChangeHtml = '<span class="rank-change down" title="Giảm ' + Math.abs(rankChange) + ' bậc"><i class="fa-solid fa-arrow-down"></i> ' + Math.abs(rankChange) + '</span>';
        } else {
          rankChangeHtml = '<span class="rank-change same" title="Không đổi bậc">-</span>';
        }
      } else {
        rankChangeHtml = '<span class="rank-change same" title="Giải đầu tiên">-</span>';
      }

      var isTop10 = (rank <= 10);
      var row = document.createElement('tr');
      row.setAttribute('data-team-name', data.name);
      if (isTop10) {
        row.classList.add('top-10');
      }

      var lastPtsText = data.lastPts > 0 ? ('<span style="color: #2dd4bf; font-weight: 700;">+' + data.lastPts + ' pts</span>') : '<span style="color: #94a3b8;">0 pts</span>';

      var netChange = data.lastPts - (data.droppedPts || 0);
      var changeText = '0 pts';
      var changeColor = '#94a3b8';
      if (netChange > 0) {
        changeText = '+' + netChange + ' pts';
        changeColor = '#2dd4bf';
      } else if (netChange < 0) {
        changeText = netChange + ' pts';
        changeColor = '#ef4444';
      }

      var rankColor = isTop10 ? '#2dd4bf' : '#ffffff';
      var nameColor = isTop10 ? '#2dd4bf' : '#ffffff';
      var nameHover = isTop10 ? '#5eead4' : '#2dd4bf';
      var badgeClass = 'rank-badge' + (isTop10 ? ' top-10' : '') + ' rank-' + rank;

      row.innerHTML = 
        '<td style="font-weight: 800; color: ' + rankColor + ';">' +
          '<div class="rank-wrap">' +
            '<span class="' + badgeClass + '">' + rank + '</span>' +
            rankChangeHtml +
          '</div>' +
        '</td>' +
        '<td style="font-weight: 700; color: ' + nameColor + ';">' +
          '<a href="' + ctx + '/team-profile?seriesId=' + encodeURIComponent(seriesId) + '&teamName=' + encodeURIComponent(data.name) + '" class="team-link" style="color: ' + nameColor + '; text-decoration: none; transition: color 0.18s ease;" onmouseover="this.style.color=\'' + nameHover + '\'" onmouseout="this.style.color=\'' + nameColor + '\'">' +
            data.name +
          '</a>' +
        '</td>' +
        '<td style="text-align: center; font-weight: 700; color: var(--rolling-text-muted);">' +
          data.activeTourneys +
        '</td>' +
        '<td style="text-align: right; font-weight: 800; font-size: 1.05rem; color: #fbbf24;">' +
          data.totalPts + ' pts' +
        '</td>' +
        '<td style="text-align: right; font-weight: 700;">' +
          lastPtsText +
        '</td>' +
        '<td style="text-align: right; font-weight: 700; color: ' + changeColor + ';">' +
          changeText +
        '</td>';

      frag.appendChild(row);
    });

    tbody.appendChild(frag);

  }

  // =========================================================================
  // PUBLIC EVENT HANDLERS & AUTO-UPDATE
  // =========================================================================
  window.onMilestoneChange = function (milestoneVal) {
    computeStandingsForMilestone(milestoneVal);
  };

  window.syncLatestStandings = function () {
    // A full request recalculates and commits standings/history before rendering.
    window.location.reload();
  };

  window.refreshRollingStandings = function () {
    var milestoneSelect = document.getElementById('milestoneSelect');
    var curVal = milestoneSelect ? milestoneSelect.value : 'LATEST';
    computeStandingsForMilestone(curVal);
  };

  // Refresh server snapshots on page load and lifecycle events with debounce
  var autoUpdateTimer = null;
  function triggerAutoUpdate() {
    if (autoUpdateTimer) clearTimeout(autoUpdateTimer);
    autoUpdateTimer = setTimeout(function() {
      var milestoneSelect = document.getElementById('milestoneSelect');
      var curVal = milestoneSelect ? milestoneSelect.value : 'LATEST';
      computeStandingsForMilestone(curVal);
    }, 100);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', triggerAutoUpdate);
  } else {
    triggerAutoUpdate();
  }

  window.addEventListener('pageshow', triggerAutoUpdate);
  window.addEventListener('focus', triggerAutoUpdate);
  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'visible') {
      triggerAutoUpdate();
    }
  });

})();
