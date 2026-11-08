/* Compact selectors delegate to the existing controls, including their locks. */
(function () {
    'use strict';
    window.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('.standalone-format-panel .format-pill-grid').forEach(function (grid, index) {
            var buttons = Array.from(grid.querySelectorAll('button'));
            var select = document.createElement('select');
            select.className = 'form-control compact-format-select';
            select.setAttribute('aria-label', index === 0 ? 'Thể thức' : 'Thể thức giai đoạn ' + index);
            buttons.forEach(function (button) {
                var option = document.createElement('option');
                option.value = button.id;
                var name = button.querySelector('.format-option-name');
                option.textContent = (name || button).textContent.trim();
                select.appendChild(option);
            });
            function sync() {
                buttons.forEach(function (button, i) {
                    select.options[i].disabled = button.disabled;
                    if (button.classList.contains('active')) select.value = button.id;
                });
                select.disabled = buttons.every(function (button) { return button.disabled; });
            }
            select.addEventListener('change', function () {
                var button = buttons.find(function (item) { return item.id === select.value; });
                if (button && !button.disabled) button.click();
                sync();
            });
            if (index > 0) {
                var field = document.createElement('div');
                field.className = 'compact-format-field';
                var label = document.createElement('span');
                label.className = 'form-label';
                label.textContent = 'Thể thức';
                field.appendChild(label);
                field.appendChild(select);
                grid.after(field);
            } else grid.after(select);
            grid.classList.add('compact-picker-source');
            new MutationObserver(sync).observe(grid, {subtree:true, attributes:true, attributeFilter:['class','disabled']});
            sync();
        });

        // Group advance count and score rules on one line without changing field IDs.
        ['stage1FieldsRR', 'stage1FieldsGR', 'stage2FieldsRR'].forEach(function (id) {
            var fields = document.getElementById(id);
            if (!fields) return;
            var row = document.createElement('div');
            row.className = 'compact-stage-fields' + (fields.children.length > 1 ? ' has-advance-count' : '');
            while (fields.firstChild) row.appendChild(fields.firstChild);
            fields.appendChild(row);
        });

        ['stage1AdvanceRR', 'stage1AdvanceGR', 'stage1AdvanceSE', 'stage1AdvanceDE', 'stage1AdvanceGSL'].forEach(function (id) {
            var input = document.getElementById(id);
            if (!input) return;
            var label = input.previousElementSibling;
            if (label && label.tagName === 'LABEL') {
                var description = label.textContent.replace(/\s+/g, ' ').trim();
                input.title = description;
                input.setAttribute('aria-label', description);
                label.textContent = 'Đội đi tiếp';
            }
        });
        ['dePowerErrorMsg1', 'gslStage1PowerErrorMsg'].forEach(function (id) {
            var error = document.getElementById(id);
            if (!error) return;
            var section = error.closest('.format-stage-section');
            if (!section) return;
            var input = error.previousElementSibling;
            if (input && input.tagName === 'INPUT') input.setAttribute('aria-describedby', id);
            error.classList.add('compact-stage-error');
            error.setAttribute('role', 'alert');
            section.appendChild(error);
        });
    });
})();
