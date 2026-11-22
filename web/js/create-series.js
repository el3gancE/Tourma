(function () {
    'use strict';
    var form = document.getElementById('createSeriesForm');
    var rolling = document.getElementById('rollingOptionsPanel');
    var elo = document.getElementById('eloOptionsPanel');

    function syncRankingModel() {
        var isElo = form.elements.rankingModel.value === 'FIFA_ELO';
        rolling.hidden = rolling.disabled = isElo;
        elo.hidden = elo.disabled = !isElo;
    }

    form.addEventListener('change', function (event) {
        if (event.target.name === 'rankingModel') syncRankingModel();
    });
    window.addEventListener('pageshow', syncRankingModel);
    syncRankingModel();
})();
