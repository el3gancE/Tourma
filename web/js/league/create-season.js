const seasonForm = document.getElementById('league-season-form');
if (seasonForm) {
    const rows = document.getElementById('league-division-rows');
    const template = document.getElementById('league-division-template');
    const assignments = Array.from(seasonForm.querySelectorAll('[data-assignment]'));
    const divisionCount = document.getElementById('league-division-count');
    const savedDivisions = Array.from(seasonForm.querySelectorAll('[data-saved-legs]'));
    const error = document.getElementById('league-setup-error');
    const unassignedLabel = document.getElementById('league-unassigned');
    const picker = document.getElementById('league-team-picker');
    const pickerOptions = document.getElementById('league-team-picker-options');
    const pickerAdd = document.getElementById('league-team-picker-add');
    let pickedLevel = '';
    const maxDivisions = Math.floor(assignments.length / 2);

    function countTeams() {
        const counts = Array(rows.children.length).fill(0);
        assignments.forEach(input => {
            if (input.value) counts[Number(input.value) - 1]++;
        });
        return counts;
    }

    function renderTeams() {
        const available = assignments.filter(input => !input.value).length;
        unassignedLabel.textContent = available + ' đội chưa phân hạng';
        const empty = countTeams().flatMap((count, index) => count === 0 ? [index + 1] : []);
        if (available >= 2 && empty.length === 1) {
            unassignedLabel.textContent += ' · Sẽ được đưa vào Hạng ' + empty[0] + ' khi lưu.';
        }
        Array.from(rows.children).forEach((row, index) => {
            const teams = assignments.filter(input => Number(input.value) === index + 1);
            row.querySelector('[data-division-count]').textContent = teams.length + ' đội';
            row.querySelector('[data-add-teams]').disabled = available === 0;
            row.querySelector('[data-division-teams]').replaceChildren(...teams.map(input => {
                const item = document.createElement('li');
                const name = document.createElement('span');
                const remove = document.createElement('button');
                name.textContent = input.dataset.team;
                remove.type = 'button';
                remove.textContent = '×';
                remove.setAttribute('aria-label', 'Bỏ ' + input.dataset.team + ' khỏi hạng');
                remove.addEventListener('click', () => {
                    input.value = '';
                    renderTeams();
                    row.querySelector('[data-add-teams]').focus();
                });
                item.append(name, remove);
                return item;
            }));
        });
    }

    function refreshDivisions() {
        const divisions = Array.from(rows.children);
        divisions.forEach((row, index) => {
            const title = 'Hạng ' + (index + 1);
            const isLast = index === divisions.length - 1;
            const exchange = row.querySelector('[data-exchange]');
            row.querySelector('legend').textContent = title;
            exchange.hidden = isLast;
            exchange.querySelector('[data-exchange-label]').textContent =
                'Hạng ' + (index + 1) + ' xuống / Hạng ' + (index + 2) + ' lên';
            if (isLast) exchange.querySelector('input').value = '0';
        });
        assignments.forEach(input => {
            if (Number(input.value) > divisions.length) input.value = '';
        });
        renderTeams();
    }

    function addDivision(saved) {
        const row = template.content.cloneNode(true);
        if (saved) {
            row.querySelector('[name="legs"]').value = saved.dataset.savedLegs;
            row.querySelector('[name="slots"]').value = saved.dataset.savedSlots;
        }
        rows.append(row);
    }

    function applyDivisionCount() {
        if (!divisionCount.validity.valid) return;
        const count = divisionCount.valueAsNumber;
        if (rows.children.length === count) return;
        while (rows.children.length < count) addDivision();
        while (rows.children.length > count) rows.lastElementChild.remove();
        refreshDivisions();
    }

    savedDivisions.forEach(addDivision);
    divisionCount.max = maxDivisions;
    divisionCount.value = Math.min(savedDivisions.length || 2, maxDivisions);
    applyDivisionCount();
    refreshDivisions();
    divisionCount.addEventListener('change', applyDivisionCount);
    rows.addEventListener('click', event => {
        const add = event.target.closest('[data-add-teams]');
        if (!add) return;
        pickedLevel = String(Array.from(rows.children).indexOf(add.closest('fieldset')) + 1);
        document.getElementById('league-team-picker-title').textContent = 'Thêm đội vào Hạng ' + pickedLevel;
        pickerOptions.replaceChildren(...assignments.flatMap((input, index) => {
            if (input.value) return [];
            const label = document.createElement('label');
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.value = String(index);
            label.append(checkbox, document.createTextNode(input.dataset.team));
            return [label];
        }));
        pickerAdd.disabled = true;
        picker.showModal();
    });
    pickerOptions.addEventListener('change', () => {
        pickerAdd.disabled = !pickerOptions.querySelector(':checked');
    });
    picker.querySelector('form').addEventListener('submit', event => {
        if (event.submitter?.value !== 'add') return;
        pickerOptions.querySelectorAll(':checked').forEach(checkbox => {
            assignments[Number(checkbox.value)].value = pickedLevel;
        });
        const row = rows.children[Number(pickedLevel) - 1];
        renderTeams();
        if (row.querySelector('[data-add-teams]').disabled) {
            picker.addEventListener('close', () => row.querySelector('[data-division-teams] button')?.focus(), { once: true });
        }
        error.hidden = true;
    });
    seasonForm.addEventListener('submit', event => {
        applyDivisionCount();
        if (!seasonForm.reportValidity()) {
            event.preventDefault();
            return;
        }
        const counts = countTeams();
        const remaining = assignments.length - counts.reduce((sum, count) => sum + count, 0);
        const empty = counts.flatMap((count, index) => count === 0 ? [index] : []);
        if (remaining >= 2 && empty.length === 1) {
            counts[empty[0]] = remaining;
        } else if (remaining > 0) {
            event.preventDefault();
            error.hidden = false;
            error.textContent = 'Còn ' + remaining + ' đội chưa phân hạng.';
            return;
        }
        const slots = Array.from(seasonForm.querySelectorAll('[name="slots"]'), input => Number(input.value));
        const invalidIndex = counts.findIndex((count, index) => count < 2 || (slots[index - 1] || 0) + slots[index] > count);
        error.hidden = invalidIndex === -1;
        if (invalidIndex !== -1) {
            event.preventDefault();
            error.textContent = 'Hạng ' + (invalidIndex + 1) + ' cần ít nhất 2 đội và đủ chỗ cho các suất chuyển hạng.';
        }
    });
    document.getElementById('save-league-season').disabled = false;
}
