/* Shared presentation for the format/roster workspace; saving remains in its engines. */
(function () {
    'use strict';
    window.addEventListener('DOMContentLoaded', function () {
        var panel = document.querySelector('.standalone-format-panel');
        if (!panel) return;
        var toolbar = panel.querySelector('.format-toolbar');
        var settings = panel.querySelector('.form-container-box');
        var banner = document.getElementById('copiedConfigBanner');
        if (!settings || !toolbar || !banner) return;
        // Keep fixed dialogs outside the layout containers.
        var modalLayer = document.createElement('div');
        modalLayer.className = 'format-page setup-modal-layer';
        document.body.appendChild(modalLayer);
        panel.querySelectorAll('.tourma-modal-backdrop').forEach(function (modal) { modalLayer.appendChild(modal); });
        var metadata = document.createElement('div');
        metadata.className = 'setup-toolbar-meta';
        toolbar.querySelector('.stage-toggle-bar').after(metadata);
        var tier = panel.querySelector('.tier-switcher-box');
        if (tier) metadata.appendChild(tier);
        metadata.appendChild(banner);
        var details = document.createElement('details');
        details.className = 'setup-settings';
        details.open = true;
        var summary = document.createElement('summary');
        summary.innerHTML = '<span class="setup-settings-title">Cấu hình thi đấu</span><span class="setup-settings-summary"></span><span class="setup-settings-toggle">Chỉnh sửa</span>';
        settings.before(details);
        details.appendChild(summary);
        details.appendChild(settings);
        details.addEventListener('toggle', function () {
            summary.querySelector('.setup-settings-toggle').textContent = details.open ? 'Thu gọn' : 'Chỉnh sửa';
        });
        function update() {
            var source = document.getElementById('copiedFrom');
            var linked = source && source.value && banner.style.display !== 'none';
            panel.classList.toggle('has-copy-source', Boolean(linked));
            var selected = Array.from(panel.querySelectorAll('.compact-format-select'))
                .filter(function (input) { return input.closest('#singleStagePanel') ? document.getElementById('singleStagePanel').style.display !== 'none' : document.getElementById('multiStagePanel').style.display !== 'none'; })
                .map(function (input) { return input.selectedOptions[0] ? input.selectedOptions[0].textContent.trim() : ''; });
            var description = summary.querySelector('.setup-settings-summary');
            var selectedText = selected.join(' → ');
            if (description.textContent !== selectedText) description.textContent = selectedText;
            var target = Number(document.getElementById('targetTeamCount').value) || 0;
            var count = document.querySelectorAll('#subtourneyTeamsTable tbody tr.team-table-row, #teamsTableBody tr.team-table-row').length;
            var progress = document.getElementById('setupCopyProgress');
            var progressText = target ? count + '/' + target + ' đội · ' + (count === target ? 'Đã đủ' : count < target ? 'Thiếu ' + (target - count) : 'Thừa ' + (count - target)) : count + ' đội';
            if (progress.textContent !== progressText) progress.textContent = progressText;
            progress.classList.toggle('is-complete', target > 0 && count === target);
        }
        var updatePending = false;
        function scheduleUpdate() {
            if (updatePending) return;
            updatePending = true;
            window.requestAnimationFrame(function () {
                updatePending = false;
                update();
            });
        }
        new MutationObserver(scheduleUpdate).observe(banner, {attributes:true, attributeFilter:['style']});
        panel.addEventListener('change', scheduleUpdate);
        var roster = document.querySelector('#subtourneyTeamsTable tbody, #teamsTableBody');
        if (roster) new MutationObserver(scheduleUpdate).observe(roster, {childList:true, subtree:true});
        var controls = panel.querySelector('#configureFormatForm');
        if (controls) new MutationObserver(scheduleUpdate).observe(controls, {attributes:true, subtree:true, attributeFilter:['class','disabled']});
        update();
    });
})();
