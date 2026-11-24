(function () {
    const dialog = document.getElementById('setup-template-dialog');
    if (!dialog) return;
    const endpoint = dialog.dataset.endpoint;
    const form = document.getElementById('setup-template-save');
    const list = document.getElementById('setup-template-list');
    const status = document.getElementById('setup-template-status');
    const field = id => document.getElementById(id)?.value || '';
    document.getElementById('setup-template-close').addEventListener('click', () => dialog.close());

    async function request(values) {
        const response = await fetch(endpoint, {
            method: 'POST',
            body: new URLSearchParams(values)
        });
        if (!response.ok) {
            const message = response.headers.get('Content-Type')?.startsWith('text/plain') ? await response.text() : '';
            throw new Error(message || 'Không thể cập nhật mẫu cấu hình.');
        }
    }

    async function load() {
        const response = await fetch(endpoint, { cache: 'no-store' });
        if (!response.ok) throw new Error('Không tải được danh sách mẫu.');
        const templates = await response.json();
        list.replaceChildren();
        if (!templates.length) {
            const empty = document.createElement('li');
            empty.textContent = 'Series chưa có mẫu đã lưu.';
            list.append(empty);
            return;
        }
        for (const template of templates) {
            const item = document.createElement('li');
            const details = document.createElement('div');
            const name = document.createElement('strong');
            const formats = document.createElement('small');
            const apply = document.createElement('button');
            name.textContent = template.name;
            const formatLabel = format => format.replaceAll('_', ' ');
            formats.textContent = template.teamCount + ' đội · ' + formatLabel(template.stage1)
                + (template.stage2 ? ' → ' + formatLabel(template.stage2) : '');
            details.append(name, formats);
            apply.type = 'button';
            apply.textContent = 'Áp dụng';
            apply.setAttribute('aria-label', 'Áp dụng mẫu ' + template.name);
            apply.addEventListener('click', async () => {
                if (!window.confirm('Áp dụng mẫu “' + template.name + '” cho giải này?')) return;
                apply.disabled = true;
                try {
                    await request({ action: 'apply', templateId: template.id });
                    window.clearCopiedConfig();
                    window.location.replace(window.location.pathname + '?id=' + encodeURIComponent(window.tournamentId));
                } catch (error) {
                    status.textContent = error.message;
                    apply.disabled = false;
                }
            });
            item.append(details, apply);
            list.append(item);
        }
    }

    document.getElementById('setup-template-open').addEventListener('click', async () => {
        const target = Number(field('targetTeamCount'));
        if (target >= 2) form.elements.teamCount.value = target;
        dialog.showModal();
        status.textContent = 'Đang tải mẫu…';
        try {
            await load();
            status.textContent = '';
        } catch (error) {
            status.textContent = error.message;
        }
    });

    form.addEventListener('submit', async event => {
        event.preventDefault();
        const button = event.submitter;
        button.disabled = true;
        try {
            window.persistFormatSelection();
            const type = field('selectedTournamentType');
            const multi = type === 'MULTI_STAGE';
            const config = field('detailConfig');
            const seats = multi ? (field('advancingSeatsCount') || '0') : '0';
            await request({
                action: 'save', name: form.elements.templateName.value, teamCount: form.elements.teamCount.value,
                type, stage1: multi ? field('stage1Format') : field('selectedFormat'),
                stage2: multi ? field('stage2Format') : '', seats, config,
                tier: field('selectedTierName') || 'S', topSeeds: field('lockTopSeedsInput'),
                classicDraw: String(Boolean(document.getElementById('classicDrawInput')?.checked))
            });
            form.elements.templateName.value = '';
            await load();
            status.textContent = 'Đã lưu mẫu.';
        } catch (error) {
            status.textContent = error.message;
        } finally {
            button.disabled = false;
        }
    });

    document.getElementById('setup-template-clear')?.addEventListener('click', async event => {
        const button = event.currentTarget;
        button.disabled = true;
        try {
            await request({ action: 'clear' });
            window.clearCopiedConfig();
            window.location.replace(window.location.pathname + '?id=' + encodeURIComponent(window.tournamentId));
        } catch (error) {
            status.textContent = error.message;
            button.disabled = false;
        }
    });
})();
