<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@page import="dao.TournamentDAO"%>
<%@page import="dao.ParticipantDAO"%>
<%@page import="model.Tournament"%>
<%@page import="model.Team"%>
<%@page import="java.util.List"%>
<%
    String tourneyId = request.getParameter("id");
    String stageParam = request.getParameter("stage");
    int currentStage = (stageParam != null && "2".equals(stageParam.trim())) ? 2 : 1;
    String activeStepVal = (currentStage == 2) ? "stage2" : "stage1";
    String tourneyName = "Giải Đấu Double Elimination";
    String deTeamsJson = "[]";
    int cutTarget = 0;
    String tournamentType = "SINGLE_STAGE";

    if (tourneyId != null && !tourneyId.trim().isEmpty()) {
        try {
            TournamentDAO tDao = new TournamentDAO();
            Tournament t = tDao.getTournamentById(tourneyId);
            ParticipantDAO pDao = new ParticipantDAO();
            List<Team> plist = pDao.getTeamsByTournamentId(tourneyId);
            int totalTeamsCount = plist != null ? plist.size() : 8;

            if (t != null) {
                if (t.getName() != null && !t.getName().trim().isEmpty()) {
                    tourneyName = t.getName();
                }
                if (t.getTournamentType() != null) {
                    tournamentType = t.getTournamentType();
                }
                if ("MULTI_STAGE".equals(tournamentType) && currentStage == 1) {
                    cutTarget = t.getAdvancingSeatsCount();
                    if (cutTarget >= totalTeamsCount || cutTarget <= 1) {
                        cutTarget = Math.max(2, (int) Math.pow(2, Math.max(1, (int) Math.floor(Math.log(totalTeamsCount) / Math.log(2)) - 1)));
                    }
                } else {
                    cutTarget = 0;
                }
            }
            if (plist != null && !plist.isEmpty()) {
                int takeCount = plist.size();
                // For multi-stage stage 2: server only sends the advancingSeatsCount teams
                if ("MULTI_STAGE".equals(tournamentType) && currentStage == 2 && t != null) {
                    int advSeats = t.getAdvancingSeatsCount();
                    if (advSeats > 1 && advSeats < takeCount) {
                        takeCount = advSeats;
                    }
                }
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < takeCount; i++) {
                    if (i > 0) sb.append(",");
                    Team tm = plist.get(i);
                    String tName = tm != null ? tm.getName() : null;
                    if (tName == null || tName.trim().isEmpty()) {
                        tName = tm != null ? tm.getRawName() : null;
                    }
                    if (tName == null || tName.trim().isEmpty()) {
                        tName = "Đội #" + (i + 1);
                    }
                    sb.append("\"").append(tName.replace("\"", "\\\"")).append("\"");
                }
                sb.append("]");
                deTeamsJson = sb.toString();
            }
        } catch (Exception e) {}
    }
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <!-- Favicon -->
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <link rel="alternate icon" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title><%= tourneyName %> - Double Elimination - Tourma</title>
        
        <!-- Google Font Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        
        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Main Stylesheets -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-viewport.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-card.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/match-card.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/single-elimination.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/double-elimination.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/final-stage-popup.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/stage-end-popup.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/empty-team-alert.css">
    </head>
    <body>
        <!-- Empty Team Alert Component -->
        <jsp:include page="/common/component/empty-team-alert.jsp"/>

        <!-- Final Stage Popup Banner -->
        <jsp:include page="/common/component/final-stage-popup.jsp"/>

        <!-- Stage End Popup Component -->
        <jsp:include page="/common/component/stage-end-popup.jsp"/>

        <!-- Stage Finish Alert Component (Locked Stage 2) -->
        <jsp:include page="/common/component/stage-finish-alert.jsp"/>

        <!-- Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="tournaments"/>
        </jsp:include>

        <!-- Sidebar Component -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="activeStep" value="<%= activeStepVal %>"/>
            <jsp:param name="id" value="${not empty param.id ? param.id : (tournament != null ? tournament.id : '')}"/>
        </jsp:include>

        <!-- Main Content Area Shifted Right by Sidebar -->
        <main class="container has-sidebar">
            
            <!-- Top Control Bar (Tournament Title, Format Badge, Team Count Badge & View Mode Toggle) -->
            <div class="single-elimination-control-bar" style="margin-bottom: 1rem;">
                <div class="tournament-info-badge-group">
                    <h1 class="tournament-name-title">
                        <i class="fa-solid fa-trophy text-gold"></i> 
                        <span id="deTournamentTitle"><%= tourneyName %></span>
                    </h1>
                    <span id="deFormatBadge" class="format-badge-single" style="background: rgba(245, 158, 11, 0.15); color: #f59e0b; border-color: rgba(245, 158, 11, 0.35);"><%= (currentStage == 2) ? "STAGE 2: DOUBLE ELIMINATION" : "DOUBLE ELIMINATION" %></span>
                    <span id="deTeamCountBadge" class="team-count-badge">0 Đội</span>
                    <span id="deAdvancingBadge" class="advancing-count-badge" style="display: none; background: rgba(45, 212, 191, 0.15); color: #2dd4bf; border: 1px solid rgba(45, 212, 191, 0.35); font-size: 0.75rem; font-weight: 600; padding: 0.3rem 0.8rem; border-radius: 9999px; align-items: center; gap: 0.45rem; line-height: 1;">
                        <i class="fa-solid fa-arrow-right-to-bracket" style="font-size: 0.75rem;"></i> <span id="deAdvancingText">8 Đội đi tiếp</span>
                    </span>
                </div>

                <!-- Right Action Bar: Standalone Reset Button + Quick Mode Toggle + View Mode Toggle Buttons -->
                <div class="control-actions-right-group" style="display: flex; align-items: center; gap: 0.75rem;">
                    <!-- Quick Mode Toggle Button -->
                    <button type="button" id="deBtnQuickMode" class="btn-quick-mode-toggle" onclick="window.TourmaDoubleElimination.toggleQuickMode()" title="Chế độ phân định thắng thua nhanh (1-click chọn đội thắng)">
                        <i class="fa-solid fa-bolt"></i> Quick Mode: <span class="quick-mode-status-text">OFF</span>
                    </button>

                    <!-- Standalone Reset Bracket Button -->
                    <button type="button" id="deBtnResetBracket" class="btn-reset-bracket-action" onclick="window.TourmaDoubleElimination.openResetModal()" title="Xóa kết quả và reset lại sơ đồ ban đầu">
                        <i class="fa-solid fa-rotate-right"></i> Reset Nhánh
                    </button>

                    <!-- View Mode Toggle Buttons (Bracket ↔ List View) -->
                    <div class="view-mode-toggle-group">
                        <button type="button" id="deBtnBracketView" class="btn-view-toggle active" onclick="window.TourmaDoubleElimination.setViewMode('bracket')">
                            <i class="fa-solid fa-diagram-project"></i> Sơ Đồ Nhánh
                        </button>
                        <button type="button" id="deBtnListView" class="btn-view-toggle" onclick="window.TourmaDoubleElimination.setViewMode('list')">
                            <i class="fa-solid fa-list-ul"></i> Danh Sách Trận
                        </button>
                    </div>
                </div>
            </div>

            <!-- RESET BRACKET CONFIRMATION MODAL -->
            <div id="deResetModalBackdrop" class="tourma-modal-backdrop" onclick="if(event.target === this) window.TourmaDoubleElimination.closeResetModal();">
                <div class="tourma-modal-card" style="max-width: 480px; border-color: rgba(244, 63, 94, 0.4);" onclick="event.stopPropagation();">
                    <div class="modal-header-bar" style="border-bottom: 1px solid rgba(244, 63, 94, 0.2);">
                        <div class="modal-header-title" style="color: #f43f5e; font-size: 0.95rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem;">
                            <i class="fa-solid fa-rotate-right"></i>
                            <span>Xác Nhận Reset Nhánh Đấu</span>
                        </div>
                        <button type="button" class="modal-close-btn" onclick="window.TourmaDoubleElimination.closeResetModal()" title="Đóng">
                            <i class="fa-solid fa-xmark"></i>
                        </button>
                    </div>
                    
                    <div class="modal-body-content" style="padding: 1.25rem 1rem;">
                        <div style="background: rgba(244, 63, 94, 0.08); border: 1px solid rgba(244, 63, 94, 0.2); border-radius: 8px; padding: 0.85rem; margin-bottom: 1rem; color: #cbd5e1; font-size: 0.82rem; line-height: 1.5;">
                            <strong style="color: #f43f5e;">⚠️ Cảnh báo quan trọng:</strong><br>
                            Hành động này sẽ <strong style="color: #ffffff;">XÓA TOÀN BỘ tỷ số và kết quả các trận đã đấu</strong>, reset lại sơ đồ Double Elimination nguyên bản ban đầu từ danh sách hạt giống.
                        </div>
                        <p style="color: #94a3b8; font-size: 0.8rem; margin: 0;">
                            Bạn có chắc chắn muốn thiết lập lại toàn bộ nhánh đấu không?
                        </p>
                    </div>

                    <div class="modal-footer-bar" style="display: flex; justify-content: flex-end; gap: 0.65rem;">
                        <button type="button" class="btn btn-secondary" onclick="window.TourmaDoubleElimination.closeResetModal()" style="font-size: 0.8rem; padding: 0.45rem 1rem;">Hủy Bỏ</button>
                        <button type="button" class="btn" style="background: #f43f5e; color: #ffffff; border: none; font-size: 0.8rem; font-weight: 700; padding: 0.45rem 1.25rem; border-radius: 6px; cursor: pointer;" onclick="window.TourmaDoubleElimination.confirmResetBracket()">
                            <i class="fa-solid fa-rotate-right"></i> Xác Nhận Reset
                        </button>
                    </div>
                </div>
            </div>

            <!-- DIRECT EMPTY ALERT CONTAINER (Shown when team count < 2) -->
            <div id="deEmptyAlertContainer" style="display: none; width: 100%;"></div>

            <!-- 1. DUAL VIEWPORTS WORKSPACE (Upper Bracket & Lower Bracket) -->
            <div id="deDualViewportWorkspace" class="de-dual-viewport-workspace">
                
                <!-- UPPER BRACKET SECTION (Includes Grand Finals) -->
                <div class="de-viewport-section">
                    <div class="de-section-header">
                        <div class="de-section-title-wrap">
                            <h2 class="de-viewport-title upper">Upper Bracket</h2>
                        </div>
                    </div>

                    <!-- Upper Viewport Frame with Floating Zoom Toolbar -->
                    <div id="upperViewportFrame" class="de-viewport-frame">
                        <div id="upperZoomToolbar" class="bracket-zoom-toolbar">
                            <button type="button" class="btn-zoom" onclick="window.TourmaDoubleElimination.zoomUpper(-0.1)" title="Thu nhỏ (-)">
                                <i class="fa-solid fa-minus"></i>
                            </button>
                            <span id="upperZoomBadge" class="zoom-level-badge">100%</span>
                            <button type="button" class="btn-zoom" onclick="window.TourmaDoubleElimination.zoomUpper(0.1)" title="Phóng to (+)">
                                <i class="fa-solid fa-plus"></i>
                            </button>
                            <button type="button" class="btn-zoom" onclick="window.TourmaDoubleElimination.resetZoomUpper()" title="Reset (100%)">
                                <i class="fa-solid fa-rotate-right"></i>
                            </button>
                        </div>

                        <div id="upperViewportContainer" class="de-viewport-container">
                            <div id="upperViewportCanvas" class="de-viewport-canvas">
                                <div id="upperBracketColumnsWrapper" class="de-bracket-columns-wrapper"></div>
                                <svg id="upperSvgConnectorsLayer" class="de-svg-connectors-layer"></svg>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- LOWER BRACKET SECTION -->
                <div class="de-viewport-section">
                    <div class="de-section-header">
                        <div class="de-section-title-wrap">
                            <h2 class="de-viewport-title lower">Lower Bracket</h2>
                        </div>
                    </div>

                    <!-- Lower Viewport Frame with Floating Zoom Toolbar -->
                    <div id="lowerViewportFrame" class="de-viewport-frame">
                        <div id="lowerZoomToolbar" class="bracket-zoom-toolbar">
                            <button type="button" class="btn-zoom" onclick="window.TourmaDoubleElimination.zoomLower(-0.1)" title="Thu nhỏ (-)">
                                <i class="fa-solid fa-minus"></i>
                            </button>
                            <span id="lowerZoomBadge" class="zoom-level-badge">100%</span>
                            <button type="button" class="btn-zoom" onclick="window.TourmaDoubleElimination.zoomLower(0.1)" title="Phóng to (+)">
                                <i class="fa-solid fa-plus"></i>
                            </button>
                            <button type="button" class="btn-zoom" onclick="window.TourmaDoubleElimination.resetZoomLower()" title="Reset (100%)">
                                <i class="fa-solid fa-rotate-right"></i>
                            </button>
                        </div>

                        <div id="lowerViewportContainer" class="de-viewport-container">
                            <div id="lowerViewportCanvas" class="de-viewport-canvas">
                                <div id="lowerBracketColumnsWrapper" class="de-bracket-columns-wrapper"></div>
                                <svg id="lowerSvgConnectorsLayer" class="de-svg-connectors-layer"></svg>
                            </div>
                        </div>
                    </div>
                </div>

            </div>

            <!-- 2. MATCHES LIST VIEW CONTAINER -->
            <div id="deListViewContainer" class="de-list-view-container"></div>

        </main>

        <!-- Dedicated Modal Popup for Score Entry & Winner Selection -->
        <jsp:include page="/common/component/popup.jsp"/>

        <!-- Context Path Injection for AJAX Operations -->
        <script>
            window.TourmaContextPath = '${pageContext.request.contextPath}';
        </script>

        <!-- Engine Scripts -->
        <script src="${pageContext.request.contextPath}/js/bracket-viewport.js"></script>
        <script src="${pageContext.request.contextPath}/js/double-elimination-algorithm.js"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-algorithm.js"></script>
        <script src="${pageContext.request.contextPath}/js/round-robin-algorithm.js"></script>
        <script src="${pageContext.request.contextPath}/js/random-service.js"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-card.js"></script>
        <script src="${pageContext.request.contextPath}/js/match-card.js"></script>
        <script src="${pageContext.request.contextPath}/js/popup.js"></script>
        <script src="${pageContext.request.contextPath}/js/final-stage-popup.js"></script>
        <script src="${pageContext.request.contextPath}/js/empty-team-alert.js"></script>
        <script src="${pageContext.request.contextPath}/js/double-elimination.js"></script>

        <!-- Page Bootstrap Execution -->
        <script>
            document.addEventListener('DOMContentLoaded', function () {
                var tourneyId = '<%= (tourneyId != null && !tourneyId.trim().isEmpty()) ? tourneyId : "demo" %>';
                var tourneyName = '<%= tourneyName %>';
                var preloadedTeams = <%= deTeamsJson %>;
                var cutTarget = <%= cutTarget %>;
                var tournamentType = '<%= tournamentType %>';
                try { localStorage.setItem('tourma_type_' + tourneyId, tournamentType); } catch(e) {}
                var currentStage = <%= currentStage %>;

                // Resolve advance count from config or localStorage
                var advCount = 0;
                try {
                    var multiCfg = JSON.parse(localStorage.getItem('tourma_multi_config_' + tourneyId));
                    if (multiCfg && multiCfg.stage1Config) {
                        advCount = multiCfg.stage1Config.advanceCount || multiCfg.stage1Config.totalAdvanceCount || 0;
                    }
                } catch(e) {}
                if (!advCount || advCount <= 1) {
                    var rawCut = localStorage.getItem('tourma_advance_count_' + tourneyId) ||
                                 localStorage.getItem('tourma_cut_target_' + tourneyId);
                    if (rawCut) advCount = parseInt(rawCut, 10);
                }
                if (!advCount || advCount <= 1) {
                    advCount = <%= cutTarget %>;
                }

                var totalTeamsCount = (preloadedTeams && preloadedTeams.length > 0) ? preloadedTeams.length : 8;
                if (advCount >= totalTeamsCount || advCount <= 1) {
                    var p2 = 4;
                    while (p2 * 2 <= totalTeamsCount) p2 *= 2;
                    advCount = Math.max(2, p2 / 2);
                }
                cutTarget = advCount;

                // Check stage2Teams from localStorage
                var stage2TeamsRaw = null;
                try { stage2TeamsRaw = JSON.parse(localStorage.getItem('tourma_stage2_teams_' + tourneyId)); } catch(e) {}

                // Resolve team list — server-provided preloadedTeams is authoritative
                var finalTeams = [];
                if (preloadedTeams && preloadedTeams.length > 0) {
                    // Server (JSP/DB) has the correct, authoritative team list
                    finalTeams = preloadedTeams;
                } else if (currentStage === 2 && stage2TeamsRaw && stage2TeamsRaw.length > 0) {
                    // Fallback: use localStorage stage2Teams only when server sent nothing (demo/offline mode)
                    finalTeams = stage2TeamsRaw;
                }

                // For Stage 2: enforce advanceCount only when server didn't pre-slice (i.e. finalTeams came from localStorage)
                if (currentStage === 2) {
                    if (preloadedTeams && preloadedTeams.length === 0 && advCount && advCount > 1 && finalTeams.length > advCount) {
                        finalTeams = finalTeams.slice(0, advCount);
                    }
                    cutTarget = 0; // Stage 2 plays to Grand Final champion!
                }

                window.TourmaDoubleElimination.init({
                    tournamentId: tourneyId,
                    tournamentName: tourneyName,
                    teamsList: finalTeams,
                    cutTarget: cutTarget,
                    tournamentType: tournamentType,
                    stage: currentStage
                });
            });
        </script>
    </body>
</html>
