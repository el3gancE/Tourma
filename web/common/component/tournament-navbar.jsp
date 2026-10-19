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

<link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-navbar.css">

<!-- Top Control Bar (Tournament Title, Format Badge, Team Count Badge & View Mode Toggle) -->
<div class="tournament-navbar-control-bar single-elimination-control-bar">
    <div class="tournament-info-badge-group">
        <h1 class="tournament-name-title">
            <i class="fa-solid fa-trophy text-gold"></i>
            <span id="tournamentNameDisplay"><%= tourneyName %></span>
        </h1>
        <span class="<%= formatBadgeClass %>"><%= formatName %></span>
        <span id="tournamentTeamCountBadge" class="team-count-badge">0 Đội</span>
        <span id="tournamentAdvancingBadge" class="advancing-count-badge"
            style="<%= (cutTarget > 0) ? "" : "display: none;" %>">
            <i class="fa-solid fa-arrow-right-to-bracket"></i>
            <%= cutTarget %> Đội đi tiếp
        </span>
    </div>

    <% if (showLiveStatus) { %>
    <div id="tournamentLiveStatus" class="tournament-live-status" role="status" aria-live="polite" aria-atomic="true"
         title="S: Seed · US: Unseed · G: Guest. Đội còn trong giải / tổng ban đầu.">
        <span class="live-status-label"><i class="fa-solid fa-circle"></i> Live Status</span>
        <span class="live-status-count live-seed" title="Seed">S: <strong id="liveSeedCount">—/—</strong></span>
        <span class="live-status-count live-unseed" title="Unseed">US: <strong id="liveUnseedCount">—/—</strong></span>
        <span class="live-status-count live-guest" title="Guest">G: <strong id="liveGuestCount">—/—</strong></span>
    </div>
    <% } %>

    <!-- Right Action Bar: Quick Mode Toggle + Standalone Reset Button + View Mode Toggle Buttons -->
    <div class="control-actions-right-group">
        <% if (showQuickMode) { %>
        <!-- Quick Mode Toggle Button -->
        <button type="button" id="singleBtnQuickMode" class="btn-quick-mode-toggle"
            onclick="window.TourmaNavbar && window.TourmaNavbar.toggleQuickMode()"
            title="Chế độ phân định thắng thua nhanh (1-click chọn đội thắng)">
            <i class="fa-solid fa-bolt"></i> Quick Mode: <span class="quick-mode-status-text">OFF</span>
        </button>
        <% } %>

        <% if (showReset) { %>
        <!-- Standalone Reset Bracket Button -->
        <button type="button" id="seBtnResetBracket" class="btn-reset-bracket-action"
            onclick="window.TourmaNavbar && window.TourmaNavbar.openResetModal()"
            title="Xóa kết quả và reset lại sơ đồ ban đầu">
            <i class="fa-solid fa-rotate-right"></i> <%= resetLabel %>
        </button>
        <% } %>

        <% if (showViewToggle) { %>
        <!-- View Mode Toggle Buttons (Bracket ↔ List View) -->
        <div class="view-mode-toggle-group">
            <button type="button" id="btnViewBracket" class="btn-view-toggle active"
                onclick="window.TourmaNavbar && window.TourmaNavbar.switchViewMode('bracket')">
                <i class="fa-solid <%= view1Icon %>"></i> <%= view1Label %>
            </button>
            <button type="button" id="btnViewList" class="btn-view-toggle"
                onclick="window.TourmaNavbar && window.TourmaNavbar.switchViewMode('list')">
                <i class="fa-solid <%= view2Icon %>"></i> <%= view2Label %>
            </button>
        </div>
        <% } %>
    </div>
</div>

<% if (showReset) { %>
<!-- RESET BRACKET CONFIRMATION MODAL -->
<div id="seResetModalBackdrop" class="tourma-modal-backdrop"
    onclick="if(event.target === this) window.TourmaNavbar && window.TourmaNavbar.closeResetModal();">
    <div class="tourma-modal-card" style="max-width: 480px; border-color: rgba(244, 63, 94, 0.4);"
        onclick="event.stopPropagation();">
        <div class="modal-header-bar" style="border-bottom: 1px solid rgba(244, 63, 94, 0.2);">
            <div class="modal-header-title"
                style="color: #f43f5e; font-size: 0.95rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem;">
                <i class="fa-solid fa-rotate-right"></i>
                <span><%= resetModalTitle %></span>
            </div>
            <button type="button" class="modal-close-btn"
                onclick="window.TourmaNavbar && window.TourmaNavbar.closeResetModal()" title="Đóng">
                <i class="fa-solid fa-xmark"></i>
            </button>
        </div>

        <div class="modal-body-content" style="padding: 1.25rem 1rem;">
            <div
                style="background: rgba(244, 63, 94, 0.08); border: 1px solid rgba(244, 63, 94, 0.2); border-radius: 8px; padding: 0.85rem; margin-bottom: 1rem; color: #cbd5e1; font-size: 0.82rem; line-height: 1.5;">
                <strong style="color: #f43f5e;">⚠️ Cảnh báo quan trọng:</strong><br>
                <%= resetWarningText %>
            </div>
            <p style="color: #94a3b8; font-size: 0.8rem; margin: 0;">
                Bạn có chắc chắn muốn thiết lập lại toàn bộ không?
            </p>
        </div>

        <div class="modal-footer-bar" style="display: flex; justify-content: flex-end; gap: 0.65rem;">
            <button type="button" class="btn btn-secondary"
                onclick="window.TourmaNavbar && window.TourmaNavbar.closeResetModal()"
                style="font-size: 0.8rem; padding: 0.45rem 1rem;">Hủy Bỏ</button>
            <button type="button" class="btn"
                style="background: #f43f5e; color: #ffffff; border: none; font-size: 0.8rem; font-weight: 700; padding: 0.45rem 1.25rem; border-radius: 6px; cursor: pointer;"
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
                    var response=await fetch(endpoint+'?'+new URLSearchParams({action:'liveStatus',tournamentId:id}),{cache:'no-store'});
                    if (!response.ok) throw new Error('Cannot load Live Status');
                    var data=await response.json();
                    if (data.status!=='success' || !data.liveStatus) throw new Error('Invalid Live Status');
                    if (version!==self.liveStatusVersion) return;
                    [['seed','liveSeedCount'],['unseed','liveUnseedCount'],['guest','liveGuestCount']].forEach(function (item) {
                        var value=data.liveStatus[item[0]];
                        document.getElementById(item[1]).textContent=value.alive+'/'+value.total;
                    });
                    widget.classList.remove('is-stale');
                    widget.title='S: Seed · US: Unseed · G: Guest. Đội còn trong giải / tổng ban đầu.';
                } catch(error) {
                    if (version!==self.liveStatusVersion) return;
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
