<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%--
    Document   : tournament-navbar.jsp
    Description: Reusable Top Navigation & Control Bar Component for All Tournament Formats
--%>
<%
    String tourneyName = request.getParameter("tourneyName");
    if (tourneyName == null || tourneyName.trim().isEmpty()) {
        tourneyName = "Giải Đấu";
    }

    String formatName = request.getParameter("format");
    if (formatName == null || formatName.trim().isEmpty()) {
        formatName = "Single Elimination";
    }

    String formatBadgeClass = request.getParameter("formatBadgeClass");
    if (formatBadgeClass == null || formatBadgeClass.trim().isEmpty()) {
        formatBadgeClass = "format-badge-single";
    }

    String tournamentType = request.getParameter("tournamentType");
    if (tournamentType == null) tournamentType = "SINGLE_STAGE";

    int cutTarget = 0;
    try {
        String cutParam = request.getParameter("cutTarget");
        if (cutParam != null) cutTarget = Integer.parseInt(cutParam.trim());
    } catch (Exception ignore) {}

    // Quick Mode only appears in Single Elimination, Double Elimination, and Swiss System
    String fmtKey = (formatName != null) ? formatName.trim().toUpperCase() : "";
    String compactFormatName = formatName.replaceAll("(?i)Single Elimination", "SE")
            .replaceAll("(?i)Double Elimination", "DE").replaceAll("(?i)GSL Format", "GSL")
            .replaceAll("(?i)Swiss System", "Swiss").replaceAll("(?i)Stage 2:[ ]*", "S2 · ");
    boolean isSupportedQuickFormat = fmtKey.contains("SINGLE") || fmtKey.contains("DOUBLE") || fmtKey.contains("SWISS") || fmtKey.contains("ELIMINATION");

    String showQuickParam = request.getParameter("showQuickMode");
    boolean showQuickMode = false;
    if (showQuickParam != null && !showQuickParam.trim().isEmpty()) {
        showQuickMode = "true".equalsIgnoreCase(showQuickParam.trim());
    } else {
        showQuickMode = isSupportedQuickFormat;
    }

    boolean showReset = !"false".equalsIgnoreCase(request.getParameter("showReset"));
    boolean showViewToggle = !"false".equalsIgnoreCase(request.getParameter("showViewToggle"));

    String resetLabel = request.getParameter("resetLabel");
    if (resetLabel == null || resetLabel.trim().isEmpty()) {
        resetLabel = "Reset Nhánh";
    }

    String resetModalTitle = request.getParameter("resetModalTitle");
    if (resetModalTitle == null || resetModalTitle.trim().isEmpty()) {
        resetModalTitle = "Xác Nhận Reset Nhánh Đấu";
    }

    String resetWarningText = request.getParameter("resetWarningText");
    if (resetWarningText == null || resetWarningText.trim().isEmpty()) {
        resetWarningText = "Hành động này sẽ XÓA TOÀN BỘ tỷ số và kết quả các trận đã đấu, reset lại sơ đồ nguyên bản ban đầu từ danh sách hạt giống.";
    }

    String engineName = request.getParameter("engineName");
    if (engineName == null || engineName.trim().isEmpty()) {
        engineName = "SingleEliminationEngine";
    }
    boolean showLiveStatus = fmtKey.contains("SINGLE") || fmtKey.contains("DOUBLE") || fmtKey.contains("GSL")
            || "GSLEngine".equals(engineName) || "TourmaGSL".equals(engineName);

    String view1Icon = request.getParameter("view1Icon");
    if (view1Icon == null || view1Icon.trim().isEmpty()) view1Icon = "fa-diagram-project";

    String view1Label = request.getParameter("view1Label");
    if (view1Label == null || view1Label.trim().isEmpty()) view1Label = "Sơ Đồ Cây";

    String view2Icon = request.getParameter("view2Icon");
    if (view2Icon == null || view2Icon.trim().isEmpty()) view2Icon = "fa-list-ol";

    String view2Label = request.getParameter("view2Label");
    if (view2Label == null || view2Label.trim().isEmpty()) view2Label = "Danh Sách Trận";
%>

<link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-navbar.css?v=20261101-meta">

<!-- Top Control Bar (Tournament Title, Format Badge, Team Count Badge & View Mode Toggle) -->
<div class="tournament-navbar-control-bar single-elimination-control-bar <%= showLiveStatus ? "has-live-status" : "" %>">
    <div class="tournament-info-badge-group">
        <h1 class="tournament-name-title">
            <i class="fa-solid fa-trophy text-gold"></i>
            <span id="tournamentNameDisplay"><%= tourneyName %></span>
        </h1>
        <span class="<%= formatBadgeClass %>" title="<%= formatName %>"><%= compactFormatName %></span>
        <span id="tournamentTeamCountBadge" class="team-count-badge">0 Đội</span>
        <span id="tournamentAdvancingBadge" class="advancing-count-badge"
            style="<%= (cutTarget > 0) ? "" : "display: none;" %>">
            <i class="fa-solid fa-arrow-right-to-bracket"></i>
            <%= cutTarget %> Đội đi tiếp
        </span>
    </div>

    <% if (showLiveStatus) { %>
    <div id="tournamentLiveStatus" class="tournament-live-status" role="status" aria-label="Số đội còn trong giải theo Seed, Unseed và Guest" aria-live="polite" aria-atomic="true"
         title="S: Seed · US: Unseed · G: Guest. Đội còn trong giải / tổng ban đầu.">
        <span class="live-status-count live-seed team-count-badge" title="Seed">S: <strong id="liveSeedCount">—/—</strong></span>
        <span class="live-status-count live-unseed format-badge-single" title="Unseed">US: <strong id="liveUnseedCount">—/—</strong></span>
        <span class="live-status-count live-guest format-badge-double" title="Guest">G: <strong id="liveGuestCount">—/—</strong></span>
    </div>
    <% } %>

    <!-- Right Action Bar: Quick Mode Toggle + Standalone Reset Button + View Mode Toggle Buttons -->
    <div class="control-actions-right-group">
        <% if (showQuickMode) { %>
        <!-- Quick Mode Toggle Button -->
        <button type="button" id="singleBtnQuickMode" class="btn-quick-mode-toggle"
            onclick="window.TourmaNavbar && window.TourmaNavbar.toggleQuickMode()"
            title="Chế độ phân định thắng thua nhanh (1-click chọn đội thắng)">
            <i class="fa-solid fa-bolt"></i> Quick: <span class="quick-mode-status-text">OFF</span>
        </button>
        <% } %>

        <% if (showReset) { %>
        <!-- Standalone Reset Bracket Button -->
        <button type="button" id="seBtnResetBracket" class="btn-reset-bracket-action"
            onclick="window.TourmaNavbar && window.TourmaNavbar.openResetModal()"
            title="<%= resetLabel %>: Xóa kết quả và reset lại sơ đồ ban đầu">
            <i class="fa-solid fa-rotate-right"></i> Reset
        </button>
        <% } %>

        <% if (showViewToggle) { %>
        <!-- View Mode Toggle Buttons (Bracket ↔ List View) -->
        <div class="view-mode-toggle-group">

            <button type="button" id="btnViewBracket" class="btn-view-toggle active"
                onclick="window.TourmaNavbar && window.TourmaNavbar.switchViewMode('bracket')" title="<%= view1Label %>" aria-label="<%= view1Label %>">
                <i class="fa-solid <%= view1Icon %>"></i> Sơ đồ
            </button>
            <button type="button" id="btnViewList" class="btn-view-toggle"
                onclick="window.TourmaNavbar && window.TourmaNavbar.switchViewMode('list')" title="<%= view2Label %>" aria-label="<%= view2Label %>">
                <i class="fa-solid <%= view2Icon %>"></i> Trận
            </button>
        </div>
        <% } %>
    </div>
</div>

<% if (showReset) { %>
<!-- RESET BRACKET CONFIRMATION MODAL -->
<div id="seResetModalBackdrop" class="tourma-modal-backdrop"
    onclick="if(event.target === this) window.TourmaNavbar && window.TourmaNavbar.closeResetModal();">
    <div class="tourma-modal-card" data-ui-style="tournament-navbar-jsp-001"
        onclick="event.stopPropagation();">
        <div class="modal-header-bar" data-ui-style="tournament-navbar-jsp-002">
            <div class="modal-header-title"
                data-ui-style="tournament-navbar-jsp-003" style="display: flex;">
                <i class="fa-solid fa-rotate-right"></i>
                <span><%= resetModalTitle %></span>
            </div>
            <button type="button" class="modal-close-btn"
                onclick="window.TourmaNavbar && window.TourmaNavbar.closeResetModal()" title="Đóng">
                <i class="fa-solid fa-xmark"></i>
            </button>
        </div>

        <div class="modal-body-content" data-ui-style="tournament-navbar-jsp-004">
            <div
                data-ui-style="tournament-navbar-jsp-005">
                <strong data-ui-style="tournament-navbar-jsp-006">⚠️ Cảnh báo quan trọng:</strong><br>
                <%= resetWarningText %>
            </div>
            <p data-ui-style="tournament-navbar-jsp-007">
                Bạn có chắc chắn muốn thiết lập lại toàn bộ không?
            </p>
        </div>

        <div class="modal-footer-bar" data-ui-style="tournament-navbar-jsp-008" style="display: flex;">
            <button type="button" class="btn btn-secondary"
                onclick="window.TourmaNavbar && window.TourmaNavbar.closeResetModal()"
                data-ui-style="tournament-navbar-jsp-009">Hủy Bỏ</button>
            <button type="button" class="btn"
                data-ui-style="tournament-navbar-jsp-010"
                onclick="window.TourmaNavbar && window.TourmaNavbar.confirmReset()">
                <i class="fa-solid fa-rotate-right"></i> Xác Nhận Reset
            </button>
        </div>
    </div>
</div>
<% } %>

<script>
    window.TourmaNavbar = {
        engineName: '<%= engineName %>',
        refreshLiveStatus: function () {
            var widget=document.getElementById('tournamentLiveStatus');
            if (!widget) return;
            var self=this, version=(this.liveStatusVersion || 0)+1;
            this.liveStatusVersion=version;
            clearTimeout(this.liveStatusTimer);
            this.liveStatusTimer=setTimeout(async function () {
                var engine=self.getEngine();
                var id=(engine && engine.tournamentId) || window.TourmaTournamentId || new URLSearchParams(window.location.search).get('id');
                if (!id || id==='demo') return;
                var endpoint='${pageContext.request.contextPath}/api/match-update';
                try {
                    // Import a legacy browser setting only if the database has no saved value.
                    if (window.TourmaTopSeedCount == null) {
                        var legacy;
                        try { legacy=JSON.parse(localStorage.getItem('tourma_hide_seed_config_'+id)); } catch(e) {}
                        if (legacy && legacy.visibleCount !== undefined) {
                            var count=Number(legacy.visibleCount || 0);
                            if (Number.isInteger(count) && count>=0 && count<=999) {
                                var imported=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},
                                    body:new URLSearchParams({action:'initializeTopSeedCount',tournamentId:id,topSeedCount:count}).toString()});
                                if (!imported.ok) throw new Error('Cannot save Top Seed');
                                var saved=await imported.json();
                                if (saved.status!=='success') throw new Error('Cannot save Top Seed');
                                window.TourmaTopSeedCount=saved.topSeedCount;
                            }
                        }
                    }
                    var liveParams={action:'liveStatus',tournamentId:id};
                    if (document.getElementById('deupperLiveStatus') && engine) liveParams.branchStage=engine.currentStage || 1;
                    var response=await fetch(endpoint+'?'+new URLSearchParams(liveParams),{cache:'no-store'});
                    if (!response.ok) throw new Error('Cannot load Live Status');
                    var data=await response.json();
                    if (data.status!=='success' || !data.liveStatus) throw new Error('Invalid Live Status');
                    if (version!==self.liveStatusVersion) return;
                    [['seed','liveSeedCount'],['unseed','liveUnseedCount'],['guest','liveGuestCount']].forEach(function (item) {
                        var value=data.liveStatus[item[0]];
                        document.getElementById(item[1]).textContent=value.alive+'/'+value.total;
                    });
                    ['upper','lower'].forEach(function (branch) {
                        var branchWidget=document.getElementById('de'+branch+'LiveStatus');
                        if (!branchWidget) return;
                        var counts=data.branchLiveStatus && data.branchLiveStatus[branch];
                        if (!counts) { branchWidget.classList.add('is-stale'); return; }
                        branchWidget.querySelectorAll('[data-category]').forEach(function (node) {
                            var value=counts[node.getAttribute('data-category')];
                            node.textContent=value.alive+'/'+value.total;
                        });
                        branchWidget.classList.remove('is-stale');
                    });
                    widget.classList.remove('is-stale');
                    widget.title='S: Seed · US: Unseed · G: Guest. Đội còn trong giải / tổng ban đầu.';
                } catch(error) {
                    if (version!==self.liveStatusVersion) return;
                    document.querySelectorAll('.de-branch-live-status').forEach(function (node) { node.classList.add('is-stale'); });
                    widget.classList.add('is-stale');
                    widget.title='Không cập nhật được Live Status từ DB. Vui lòng thử lại.';
                }
            },40);
        },
        getEngine: function () {
            return window[this.engineName] || window.GSLEngine || window.TourmaGSL || window.DoubleEliminationEngine || window.TourmaDoubleElimination || window.SingleEliminationEngine || window.TourmaSingleElimination || window.TourmaSwiss || window.TourmaRoundRobin || window.TourmaGroupStage;
        },
        openResetModal: function () {
            var engine = this.getEngine();
            var tid = (engine && engine.tournamentId) || window.TourmaTournamentId || '';
            if (window.TourmaScoreModal && typeof window.TourmaScoreModal.isLocked === 'function') {
                if (window.TourmaScoreModal.isLocked(tid)) {
                    if (window.FinalStagePopup && typeof window.FinalStagePopup.promptUnlock === 'function') {
                        window.FinalStagePopup.promptUnlock();
                    } else if (window.StageEndPopup && typeof window.StageEndPopup.promptUnlock === 'function') {
                        window.StageEndPopup.promptUnlock();
                    }
                    return;
                }
            } else if (window.FinalStagePopup && typeof window.FinalStagePopup.isTournamentLocked === 'function') {
                if (window.FinalStagePopup.isTournamentLocked(tid)) {
                    window.FinalStagePopup.promptUnlock();
                    return;
                }
            }

            if (engine && typeof engine.openResetModal === 'function') {
                engine.openResetModal();
            } else {
                var modal = document.getElementById('seResetModalBackdrop');
                if (modal) {
                    modal.style.display = 'flex';
                    modal.classList.add('show');
                }
            }
        },
        closeResetModal: function () {
            var engine = this.getEngine();
            if (engine && typeof engine.closeResetModal === 'function') {
                engine.closeResetModal();
            } else {
                var modal = document.getElementById('seResetModalBackdrop');
                if (modal) {
                    modal.style.display = 'none';
                    modal.classList.remove('show');
                }
            }
        },
        confirmReset: function () {
            this.closeResetModal();
            var engine = this.getEngine();
            if (engine && typeof engine.confirmResetBracket === 'function') {
                engine.confirmResetBracket();
            } else if (engine && typeof engine.resetBracket === 'function') {
                engine.resetBracket(true);
            } else if (engine && typeof engine.resetMatches === 'function') {
                engine.resetMatches();
            } else {
                var tid = (engine && engine.tournamentId) || window.TourmaTournamentId || '';
                fetch((window.TourmaContextPath || '') + '/api/match-update', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                    body: new URLSearchParams({ action: 'resetBracket', tournamentId: tid, stage: '1' }).toString()
                })
                .then(function () { window.location.reload(); })
                .catch(function () { window.location.reload(); });
            }
        },
        toggleQuickMode: function () {
            var engine = this.getEngine();
            if (engine && typeof engine.toggleQuickMode === 'function') {
                engine.toggleQuickMode();
            } else {
                window.TourmaQuickMode = !window.TourmaQuickMode;
                var btn = document.getElementById('singleBtnQuickMode');
                var statusText = btn ? btn.querySelector('.quick-mode-status-text') : null;
                if (window.TourmaQuickMode) {
                    if (btn) btn.classList.add('active');
                    if (statusText) statusText.textContent = 'ON';
                } else {
                    if (btn) btn.classList.remove('active');
                    if (statusText) statusText.textContent = 'OFF';
                }
            }
        },
        switchViewMode: function (mode) {
            var engine = this.getEngine();
            if (engine && typeof engine.switchViewMode === 'function') {
                engine.switchViewMode(mode);
            }
        }
    };

    if (<%= showLiveStatus %>) {
        document.addEventListener('DOMContentLoaded',function () { window.TourmaNavbar.refreshLiveStatus(); });
        window.addEventListener('focus',function () { window.TourmaNavbar.refreshLiveStatus(); });
        setInterval(function () {
            if (!document.hidden) window.TourmaNavbar.refreshLiveStatus();
        },5000);
    }

    // Immediate View Mode Active State Sync from localStorage
    (function () {
        var tid = window.TourmaTournamentId || '';
        if (!tid) {
            try {
                var params = new URLSearchParams(window.location.search);
                tid = params.get('id') || params.get('tournamentId') || 'demo';
            } catch (e) { }
        }
        var savedView = null;
        try {
            savedView = localStorage.getItem('tourma_view_mode_' + tid) ||
                        localStorage.getItem('tourma_view_mode_demo') ||
                        localStorage.getItem('tourma_gsl_view_mode_' + tid) ||
                        localStorage.getItem('tourma_de_view_mode_' + tid) ||
                        localStorage.getItem('tourma_se_view_mode_' + tid);
        } catch (e) { }

        if (savedView && savedView.toUpperCase() === 'LIST') {
            var bBtns = document.querySelectorAll('#btnViewBracket, .btn-view-toggle:first-child');
            var lBtns = document.querySelectorAll('#btnViewList, .btn-view-toggle:last-child');
            bBtns.forEach(function (b) { b.classList.remove('active'); });
            lBtns.forEach(function (b) { b.classList.add('active'); });
        }
    })();
</script>
