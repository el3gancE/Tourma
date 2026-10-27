/** Shared Stage 2 guard: database state overrides all browser caches and old matches. */
(function (window) {
    'use strict';
    if (window.StageFinishAlert) return;
    var guard = {
        tournamentId: null,
        state: window.TourmaStageAccessState || null,
        blocked: false,
        checkAndRender: function (tid, stage) {
            if (String(stage) !== '2' && new URLSearchParams(window.location.search).get('stage') !== '2') return false;
            this.tournamentId = tid;
            var state = this.state || window.TourmaStageAccessState;
            var dbStatus = window.TourmaDbStage1Status && window.TourmaDbStage1Status[tid];
            var ready = state ? state.ready === true : (dbStatus === 'LOCKED' || dbStatus === 'COMPLETED');
            var wrapper = document.getElementById('stageFinishAlertContainer');
            var main = document.querySelector('main.container');
            var navbar = main && main.querySelector('.tournament-navbar-control-bar, .rr-control-bar');
            if (wrapper && main) {
                if (navbar && wrapper.previousElementSibling !== navbar) main.insertBefore(wrapper, navbar.nextSibling);
                else if (!navbar && wrapper.parentNode !== main) main.insertBefore(wrapper, main.firstChild);
            }
            this.blocked = !ready;
            document.body.classList.toggle('stage2-is-blocked', !ready);
            if (navbar) navbar.querySelectorAll('button').forEach(function (button) {
                if (!ready && !button.disabled) { button.setAttribute('data-stage-guard-disabled','true'); button.disabled = true; }
                else if (ready && button.hasAttribute('data-stage-guard-disabled')) { button.removeAttribute('data-stage-guard-disabled'); button.disabled = false; }
            });
            if (wrapper) {
                wrapper.style.display = ready ? 'none' : 'flex';
                if (!ready) {
                    var format = state && state.stage1Format;
                    if (!format && window.TourmaMultiStageConfig) format = window.TourmaMultiStageConfig.stage1Format;
                    var pages = {SINGLE_ELIMINATION:'single-elimination.jsp',DOUBLE_ELIMINATION:'double-elimination.jsp',
                        ROUND_ROBIN:'round-robin.jsp',GSL:'gsl.jsp',GROUP_STAGE:'group-stage.jsp',SWISS:'swiss-stage.jsp',SWISS_LITE:'swiss-stage.jsp'};
                    var path = window.TourmaContextPath;
                    if (path === undefined) path = window.location.pathname.split('/common/')[0];
                    var url = path + '/common/' + (pages[format] || 'single-elimination.jsp') + '?id=' + encodeURIComponent(tid) + '&stage=1';
                    var series = new URLSearchParams(window.location.search).get('seriesId');
                    if (series) url += '&seriesId=' + encodeURIComponent(series);
                    var link = document.getElementById('stageFinishAlertReturnBtn');
                    if (link) link.setAttribute('href',url);
                }
            }
            return !ready;
        },
        applyState: function (state) {
            var previous = this.state || window.TourmaStageAccessState;
            var wasBlocked = this.blocked;
            this.state = state;
            if (state.ready && (wasBlocked || (previous && previous.generation !== state.generation))) {
                window.location.reload();
                return;
            }
            this.checkAndRender(this.tournamentId,2);
        },
        refresh: async function () {
            if (!this.tournamentId || this.refreshing) return;
            this.refreshing = true;
            try {
                var path = window.TourmaContextPath;
                if (path === undefined) path = window.location.pathname.split('/common/')[0];
                var response = await fetch(path + '/api/cut-stage', {
                    method:'POST', cache:'no-store', headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'},
                    body:new URLSearchParams({action:'stageState',tournamentId:this.tournamentId}).toString()
                });
                var data = await response.json();
                if (response.ok && data.status === 'success' && data.stageState) this.applyState(data.stageState);
            } catch (error) { /* Keep the current view until the next successful refresh. */ }
            finally { this.refreshing = false; }
        }
    };
    window.StageFinishAlert = guard;
    function start() {
        var params = new URLSearchParams(window.location.search);
        if (params.get('stage') !== '2') return;
        var tid = params.get('id') || params.get('tournamentId') || window.TourmaTournamentId;
        if (!tid || tid === 'demo') return;
        guard.checkAndRender(tid,2);
        guard.refresh();
        window.setInterval(function () { if (!document.hidden) guard.refresh(); },1000);
        window.addEventListener('focus',function () { guard.refresh(); });
        document.addEventListener('visibilitychange',function () { if (!document.hidden) guard.refresh(); });
        window.addEventListener('storage',function (event) {
            if (event.key === 'tourma_stage1_locked_' + tid && event.newValue === 'false') {
                guard.applyState(Object.assign({},guard.state,{ready:false,stage1Status:'PENDING'}));
            }
            if (event.key === 'tourma_stage_state_' + tid || event.key === 'tourma_stage1_locked_' + tid) guard.refresh();
        });
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded',start);
    else start();
})(window);
