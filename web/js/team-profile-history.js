(function () {
  'use strict';

  function matches(index, size, filters, tier) {
    if (filters.tier && String(tier || 'A').toUpperCase() !== filters.tier.toUpperCase()) return false;
    if (filters.phase && Math.ceil(index / size) !== Number(filters.phase)) return false;
    if (filters.window && (index > Number(filters.window) || index < Math.max(1, Number(filters.window) - size + 1))) return false;
    return (!filters.from || index >= Number(filters.from)) && (!filters.to || index <= Number(filters.to));
  }
  if (typeof module !== 'undefined') module.exports = {matches: matches};
  if (typeof document === 'undefined') return;

  function init() {
    var form = document.getElementById('profileHistoryFilters');
    var tbody = document.getElementById('profPerformanceTbody');
    if (!form || !tbody) return;
    var size = Number(window.seriesPhaseSize) || 3;
    var tournaments = (window.seriesSubTournaments || []).slice().sort(function (a, b) { return a.index - b.index; });
    var byId = new Map(tournaments.map(function (t) {return [String(t.id), t];}));
    var phase = document.getElementById('profileHistoryPhase');
    var tier = document.getElementById('profileHistoryTier');
    var rollingWindow = document.getElementById('profileHistoryWindow');
    var from = document.getElementById('profileHistoryFrom');
    var to = document.getElementById('profileHistoryTo');
    var scroll = form.nextElementSibling;
    var empty = document.getElementById('profileHistoryEmpty');
    var counter = document.getElementById('profileHistoryCount');
    function option(select, value, label) {
      var el = document.createElement('option');
      el.value = String(value);
      el.textContent = label;
      select.appendChild(el);
    }
    var phases = new Set();
    var tiers = Array.from(new Set(tournaments.map(function (t) {return String(t.tierName || 'A').toUpperCase();})));
    var tierOrder = ['S', 'A', 'B', 'C', 'D'];
    tiers.sort(function (a, b) {
      var ai = tierOrder.indexOf(a), bi = tierOrder.indexOf(b);
      return (ai < 0 ? tierOrder.length : ai) - (bi < 0 ? tierOrder.length : bi) || a.localeCompare(b);
    }).forEach(function (name) {option(tier, name, 'Tier ' + name);});
    tournaments.forEach(function (t) {
      var p = Math.ceil(t.index / size);
      if (!phases.has(p)) {
        phases.add(p);
        option(phase, p, 'Phase ' + p + ' · #' + ((p - 1) * size + 1) + '–' + (p * size));
      }
      var label = '#' + t.index + ' · ' + t.name;
      option(from, t.index, label);
      option(to, t.index, label);
    });
    tournaments.slice().reverse().forEach(function (t) {
      option(rollingWindow, t.index, '#' + Math.max(1, t.index - size + 1) + '–' + t.index + ' · ' + t.name);
    });

    function update(resetScroll) {
      var invalid = Boolean(from.value && to.value && Number(from.value) > Number(to.value));
      from.setAttribute('aria-invalid', String(invalid));
      to.setAttribute('aria-invalid', String(invalid));
      var rows = Array.from(tbody.querySelectorAll('tr[data-tournament-id]'));
      var filters = {tier: tier.value, phase: phase.value, window: rollingWindow.value, from: from.value, to: to.value};
      var visible = [];
      rows.forEach(function (row) {
        var tournament = byId.get(row.dataset.tournamentId);
        var show = !invalid && tournament && matches(Number(tournament.index), size, filters, tournament.tierName);
        row.hidden = !show;
        if (show) visible.push(row);
      });
      counter.textContent = invalid ? 'Giải bắt đầu phải trước hoặc bằng giải kết thúc.' : visible.length + '/' + rows.length + ' giải';
      empty.hidden = visible.length > 0 || rows.length === 0;
      // Measure the actual first 20 rows so wrapped names and narrow screens
      // still stop at 20 tournaments rather than an estimated pixel height.
      var header = tbody.closest('table').tHead;
      var height = (header ? header.getBoundingClientRect().height : 0) + 1;
      visible.slice(0, 20).forEach(function (row) {height += row.getBoundingClientRect().height;});
      if (visible.length) scroll.style.setProperty('--profile-history-height', Math.ceil(height) + 'px');
      if (resetScroll) scroll.scrollTop = 0;
    }
    form.addEventListener('change', function () {update(true);});
    document.getElementById('profileHistoryReset').addEventListener('click', function () {
      tier.value = phase.value = rollingWindow.value = from.value = to.value = '';
      update(true);
    });
    new MutationObserver(function () {update(false);}).observe(tbody, {childList:true});
    if (typeof ResizeObserver !== 'undefined') {
      var width = 0;
      new ResizeObserver(function (entries) {
        var nextWidth = entries[0].contentRect.width;
        if (width !== nextWidth) {width = nextWidth; update(false);}
      }).observe(scroll);
    }
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(function () {update(false);});
    update(false);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
