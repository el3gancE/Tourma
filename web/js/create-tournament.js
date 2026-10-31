(function () {
    'use strict';
    var form = document.getElementById('createTournamentForm');
    var name = document.getElementById('tournamentName');
    var description = document.getElementById('tournamentDescription');
    var submit = document.getElementById('createSubmit');
    function updateFields() {
        name.setCustomValidity(name.value && !name.value.trim() ? 'Vui lòng nhập tên giải đấu.' : '');
        document.getElementById('descriptionCount').textContent = Array.from(description.value).length + ' ký tự';
    }
    name.addEventListener('input', updateFields);
    description.addEventListener('input', updateFields);
    form.addEventListener('submit', function (event) {
        updateFields();
        if (!form.reportValidity()) { event.preventDefault(); return; }
        submit.disabled = true;
        submit.textContent = 'Đang tạo giải…';
    });
    window.addEventListener('pageshow', function () {
        submit.disabled = false;
        submit.innerHTML = 'Tạo &amp; tiếp tục <i class="fa-solid fa-arrow-right" aria-hidden="true"></i>';
        updateFields();
    });
    updateFields();
})();
