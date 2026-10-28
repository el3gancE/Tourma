/* UI only. Team parsing, seeding, drag/drop and persistence remain in the JSP. */
(function () {
    'use strict';
    var navigation = document.querySelector('.tournament-navigation');
    var desktop = window.matchMedia('(min-width:1000px)');
    function layoutNavigation() { if (navigation) navigation.open = desktop.matches; }
    layoutNavigation();
    if (desktop.addEventListener) desktop.addEventListener('change', layoutNavigation);
    var rows = document.getElementById('teamsTableBody');
    var empty = document.getElementById('teamsEmptyState');
    function updateEmptyState() {
        if (!rows) return;
        var hasRows = rows.rows.length > 0;
        if (empty) empty.hidden = hasRows;
        var tableRegion = document.querySelector('.table-scroll-wrapper');
        if (tableRegion) tableRegion.hidden = !hasRows;
    }
    if (rows) new MutationObserver(updateEmptyState).observe(rows, { childList: true });
    updateEmptyState();
})();
