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
            style="<%= ("MULTI_STAGE".equals(tournamentType) && cutTarget > 1) ? "" : "display: none;" %>">
            <i class="fa-solid fa-arrow-right-to-bracket"></i>
            <%= cutTarget %> Đội đi tiếp
        </span>
    </div>

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
        getEngine: function () {
            return window[this.engineName] || window.SingleEliminationEngine || window.TourmaSingleElimination || window.TourmaDoubleElimination || window.TourmaSwiss || window.TourmaRoundRobin || window.TourmaGroupStage;
        },
        openResetModal: function () {
            var engine = this.getEngine();
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
</script>
