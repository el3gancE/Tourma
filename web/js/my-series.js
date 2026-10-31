(function () {
    'use strict';
    var model = 'ALL';
    var search = document.getElementById('searchInput');
    var cards = Array.from(document.querySelectorAll('.series-card'));
    function applyFilters() {
        var query = search.value.trim().toLocaleLowerCase('vi');
        var count = 0;
        cards.forEach(function (card) {
            var visible = (model === 'ALL' || card.dataset.model === model)
                && card.dataset.name.toLocaleLowerCase('vi').includes(query);
            card.hidden = !visible;
            if (visible) count++;
        });
        var empty = document.getElementById('seriesNoResults');
        if (empty) empty.hidden = count !== 0;
        var result = document.getElementById('seriesResultCount');
        if (result) result.textContent = count + ' / ' + cards.length + ' chuỗi giải';
    }
    document.querySelectorAll('[data-model-filter]').forEach(function (button) {
        button.addEventListener('click', function () {
            model = button.dataset.modelFilter;
            document.querySelectorAll('[data-model-filter]').forEach(function (item) {
                item.classList.toggle('active', item === button);
                item.setAttribute('aria-pressed', String(item === button));
            });
            applyFilters();
        });
    });
    search.addEventListener('input', applyFilters);
    document.querySelectorAll('.series-edit-button').forEach(function (button) {
        button.addEventListener('click', function () {
            window.openEditSeriesPopup({ id: button.dataset.seriesId, name: button.dataset.seriesName,
                phaseSize: Number(button.dataset.phaseSize), status: button.dataset.status,
                actionUrl: button.dataset.actionUrl });
        });
    });
    document.querySelectorAll('.series-delete-form').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            if (!window.confirm('Xóa chuỗi giải “' + form.dataset.seriesName
                + '”? Toàn bộ giải con, lịch sử và bảng xếp hạng thuộc Series này sẽ bị xóa.')) event.preventDefault();
        });
    });
    applyFilters();
})();
