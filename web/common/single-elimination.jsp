<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@page import="dao.TournamentDAO"%>
<%@page import="dao.ParticipantDAO"%>
<%@page import="model.Tournament"%>
<%@page import="model.Team"%>
<%@page import="java.util.List"%>
<%
    String tourneyId = request.getParameter("id");
    String tourneyName = "Giải Đấu Single Elimination";
    String teamsJson = "[]";
    int cutTarget = 0;
    String tournamentType = "SINGLE_STAGE";
    String stageParam = request.getParameter("stage");
    int currentStage = (stageParam != null && "2".equals(stageParam.trim())) ? 2 : 1;
    String activeStepVal = (currentStage == 2) ? "stage2" : "stage1";

    if (tourneyId != null && !tourneyId.trim().isEmpty()) {
        try {
            TournamentDAO tDao = new TournamentDAO();
            Tournament t = tDao.getTournamentById(tourneyId);
            if (t != null) {
                if (t.getName() != null && !t.getName().trim().isEmpty()) {
                    tourneyName = t.getName();
                }
                // cutTarget only applies to MULTI_STAGE Stage 1 (Stage 1 → Stage 2 cut)
                // Single Stage & Stage 2 always play all rounds to find a champion
                if (t.getTournamentType() != null) {
                    tournamentType = t.getTournamentType();
                }
                if ("MULTI_STAGE".equals(tournamentType) && currentStage == 1) {
                    cutTarget = t.getAdvancingSeatsCount();
                }
            }
            ParticipantDAO pDao = new ParticipantDAO();
            List<Team> plist = pDao.getTeamsByTournamentId(tourneyId);
            if (plist != null && !plist.isEmpty()) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < plist.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(plist.get(i).getRawName().replace("\"", "\\\"")).append("\"");
                }
                sb.append("]");
                teamsJson = sb.toString();
            }
        } catch (Exception e) {}
    }
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Sơ Đồ Thi Đấu Single Elimination - TOURMA</title>
        
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
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/final-stage-popup.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/empty-team-alert.css">
    </head>
    <body>
        <!-- Empty Team Alert Component -->
        <jsp:include page="/common/component/empty-team-alert.jsp"/>

        <!-- Final Stage Popup Banner -->
        <jsp:include page="/common/component/final-stage-popup.jsp"/>

        <!-- Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="tournaments"/>
        </jsp:include>

        <!-- Sidebar Component (Step 4: Vòng Đấu) -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="activeStep" value="<%= activeStepVal %>"/>
            <jsp:param name="id" value="${not empty param.id ? param.id : (tournament != null ? tournament.id : '')}"/>
        </jsp:include>

        <!-- Main Content Area Shifted Right by Sidebar -->
        <main class="container has-sidebar">
            
            <!-- Top Control Bar (Tournament Title, Format Badge, Team Count Badge & View Mode Toggle) -->
            <div class="single-elimination-control-bar">
                <div class="tournament-info-badge-group">
                    <h1 class="tournament-name-title">
                        <i class="fa-solid fa-trophy text-gold"></i> 
                        <span id="tournamentNameDisplay"><%= tourneyName %></span>
                    </h1>
                    <span class="format-badge-single">Single Elimination</span>
                    <span id="tournamentTeamCountBadge" class="team-count-badge">0 Đội</span>
                    <span id="tournamentAdvancingBadge" class="advancing-count-badge" style="background: rgba(45, 212, 191, 0.15); color: #2dd4bf; border: 1px solid rgba(45, 212, 191, 0.3); font-size: 0.75rem; font-weight: 600; padding: 0.3rem 0.65rem; border-radius: 20px; display: inline-flex; align-items: center; gap: 0.4rem; <%= ("MULTI_STAGE".equals(tournamentType) && cutTarget > 1) ? "" : "display: none;" %>">
                        <i class="fa-solid fa-arrow-right-to-bracket"></i> <%= cutTarget %> Đội đi tiếp
                    </span>
                </div>

                <!-- Right Action Bar: Standalone Reset Button + Quick Mode Toggle + View Mode Toggle Buttons -->
                <div class="control-actions-right-group" style="display: flex; align-items: center; gap: 0.75rem;">
                    <!-- Quick Mode Toggle Button -->
                    <button type="button" id="singleBtnQuickMode" class="btn-quick-mode-toggle" onclick="window.SingleEliminationEngine.toggleQuickMode()" title="Chế độ phân định thắng thua nhanh (1-click chọn đội thắng)">
                        <i class="fa-solid fa-bolt"></i> Quick Mode: <span class="quick-mode-status-text">OFF</span>
                    </button>

                    <!-- Standalone Reset Bracket Button -->
                    <button type="button" id="seBtnResetBracket" class="btn-reset-bracket-action" onclick="window.SingleEliminationEngine.openResetModal()" title="Xóa kết quả và reset lại sơ đồ ban đầu">
                        <i class="fa-solid fa-rotate-right"></i> Reset Nhánh
                    </button>

                    <!-- View Mode Toggle Buttons (Bracket ↔ List View) -->
                    <div class="view-mode-toggle-group">
                        <button type="button" id="btnViewBracket" class="btn-view-toggle active" onclick="window.SingleEliminationEngine.switchViewMode('bracket')">
                            <i class="fa-solid fa-diagram-project"></i> Sơ Đồ Cây
                        </button>
                        <button type="button" id="btnViewList" class="btn-view-toggle" onclick="window.SingleEliminationEngine.switchViewMode('list')">
                            <i class="fa-solid fa-list-ol"></i> Danh Sách Trận
                        </button>
                    </div>
                </div>
            </div>

            <!-- RESET BRACKET CONFIRMATION MODAL -->
            <div id="seResetModalBackdrop" class="tourma-modal-backdrop" onclick="if(event.target === this) window.SingleEliminationEngine.closeResetModal();">
                <div class="tourma-modal-card" style="max-width: 480px; border-color: rgba(244, 63, 94, 0.4);" onclick="event.stopPropagation();">
                    <div class="modal-header-bar" style="border-bottom: 1px solid rgba(244, 63, 94, 0.2);">
                        <div class="modal-header-title" style="color: #f43f5e; font-size: 0.95rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem;">
                            <i class="fa-solid fa-rotate-right"></i>
                            <span>Xác Nhận Reset Nhánh Đấu</span>
                        </div>
                        <button type="button" class="modal-close-btn" onclick="window.SingleEliminationEngine.closeResetModal()" title="Đóng">
                            <i class="fa-solid fa-xmark"></i>
                        </button>
                    </div>
                    
                    <div class="modal-body-content" style="padding: 1.25rem 1rem;">
                        <div style="background: rgba(244, 63, 94, 0.08); border: 1px solid rgba(244, 63, 94, 0.2); border-radius: 8px; padding: 0.85rem; margin-bottom: 1rem; color: #cbd5e1; font-size: 0.82rem; line-height: 1.5;">
                            <strong style="color: #f43f5e;">⚠️ Cảnh báo quan trọng:</strong><br>
                            Hành động này sẽ <strong style="color: #ffffff;">XÓA TOÀN BỘ tỷ số và kết quả các trận đã đấu</strong>, reset lại sơ đồ Single Elimination nguyên bản ban đầu từ danh sách hạt giống.
                        </div>
                        <p style="color: #94a3b8; font-size: 0.8rem; margin: 0;">
                            Bạn có chắc chắn muốn thiết lập lại toàn bộ nhánh đấu không?
                        </p>
                    </div>

                    <div class="modal-footer-bar" style="display: flex; justify-content: flex-end; gap: 0.65rem;">
                        <button type="button" class="btn btn-secondary" onclick="window.SingleEliminationEngine.closeResetModal()" style="font-size: 0.8rem; padding: 0.45rem 1rem;">Hủy Bỏ</button>
                        <button type="button" class="btn" style="background: #f43f5e; color: #ffffff; border: none; font-size: 0.8rem; font-weight: 700; padding: 0.45rem 1.25rem; border-radius: 6px; cursor: pointer;" onclick="window.SingleEliminationEngine.confirmResetBracket()">
                            <i class="fa-solid fa-rotate-right"></i> Xác Nhận Reset
                        </button>
                    </div>
                </div>
            </div>

            <!-- DIRECT EMPTY ALERT CONTAINER (Shown when team count < 2) -->
            <div id="singleEmptyAlertContainer" style="display: none; width: 100%;"></div>

            <!-- MODE 1: BRACKET TREE VIEW (Inside Drag-to-Pan Viewport Frame) -->
            <div id="bracketViewportFrame" class="bracket-viewport-frame">
                
                <!-- Floating Zoom Toolbar (Fixed at Top-Right Corner of Viewport Frame) -->
                <div class="bracket-zoom-toolbar">
                    <button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.zoomOut()" title="Thu nhỏ (-)">
                        <i class="fa-solid fa-minus"></i>
                    </button>
                    <span id="zoomLevelBadge" class="zoom-level-badge">100%</span>
                    <button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.zoomIn()" title="Phóng to (+)">
                        <i class="fa-solid fa-plus"></i>
                    </button>
                    <button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.resetZoom()" title="Reset (100%)">
                        <i class="fa-solid fa-rotate-right"></i>
                    </button>
                </div>

                <!-- Scrollable Canvas Viewport Container -->
                <div id="bracketViewportContainer" class="bracket-viewport-container">
                    <!-- Inner Canvas Wrapper (Dynamic Bracket Columns Injected Here) -->
                    <div id="bracketViewportCanvas" class="bracket-viewport-canvas">
                        <div id="singleBracketColumnsWrapper" class="single-bracket-columns-wrapper">
                            <!-- Dynamic Round Columns rendered by JavaScript Engine -->
                        </div>
                    </div>
                </div>

            </div>

            <!-- MODE 2: MATCHES LIST VIEW (Dynamic List Cards Injected Here) -->
            <div id="singleListViewContainer" class="single-list-view-container">
                <!-- Dynamic List Cards rendered by JavaScript Engine -->
            </div>

        </main>

        <!-- Score Edit Popup Component -->
        <jsp:include page="/common/component/popup.jsp"/>

        <script src="${pageContext.request.contextPath}/js/bracket-algorithm.js"></script>
        <script src="${pageContext.request.contextPath}/js/random-service.js"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-card.js"></script>
        <script src="${pageContext.request.contextPath}/js/match-card.js"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-viewport.js"></script>
        <script src="${pageContext.request.contextPath}/js/final-stage-popup.js"></script>
        <script src="${pageContext.request.contextPath}/js/empty-team-alert.js"></script>
        <script src="${pageContext.request.contextPath}/js/single-elimination.js"></script>

        <script>
            window.addEventListener('DOMContentLoaded', function () {
                var tourneyId = "<%= (tourneyId != null && !tourneyId.trim().isEmpty()) ? tourneyId : "demo" %>";
                var preloadedTeams = <%= teamsJson %>;
                var cutTarget = <%= cutTarget %>; // from DB
                var isMultiStage = <%= "MULTI_STAGE".equals(tournamentType) ? "true" : "false" %>;
                var currentStage = <%= currentStage %>;

                if (currentStage === 2) {
                    // Stage 2 SE: Load qualified teams from Stage 1 completion
                    var s2TeamsRaw = null;
                    try { s2TeamsRaw = JSON.parse(localStorage.getItem('tourma_stage2_teams_' + tourneyId)); } catch(e) {}
                    if (s2TeamsRaw && s2TeamsRaw.length > 0) {
                        preloadedTeams = s2TeamsRaw;
                    }
                    cutTarget = 0; // Stage 2 always plays to find a champion!
                } else if (!isMultiStage) {
                    // Single Stage = tìm vô địch, chơi hết rounds — clear stale cut config
                    try { localStorage.removeItem('tourma_advance_count_' + tourneyId); } catch(e) {}
                    try { localStorage.removeItem('tourma_cut_target_' + tourneyId); } catch(e) {}
                    cutTarget = 0;
                } else {
                    // Multi-Stage Stage 1: read cutTarget from tourma_multi_config_ localStorage (most reliable source)
                    try {
                        var multiCfg = JSON.parse(localStorage.getItem('tourma_multi_config_' + tourneyId));
                        if (multiCfg && multiCfg.stage1Config) {
                            var cfgAdv = multiCfg.stage1Config.advanceCount || multiCfg.stage1Config.totalAdvanceCount || 0;
                            if (cfgAdv > 1) {
                                cutTarget = cfgAdv;
                                // Sync to the simple keys too
                                localStorage.setItem('tourma_advance_count_' + tourneyId, cfgAdv);
                                localStorage.setItem('tourma_cut_target_' + tourneyId, cfgAdv);
                            }
                        }
                    } catch(e) {}
                    // Fallback: try tourma_advance_count_ key
                    if (!cutTarget || cutTarget <= 1) {
                        try {
                            var adv = localStorage.getItem('tourma_advance_count_' + tourneyId)
                                   || localStorage.getItem('tourma_cut_target_' + tourneyId);
                            if (adv) cutTarget = parseInt(adv, 10);
                        } catch(e) {}
                    }
                }
                // DEBUG
                console.log('[JSP init] tourneyId=', tourneyId,
                    '| stage=', currentStage,
                    '| isMultiStage=', isMultiStage,
                    '| cutTarget(DB)=', <%= cutTarget %>,
                    '| tourma_multi_config_=', localStorage.getItem('tourma_multi_config_' + tourneyId),
                    '| tourma_advance_count_=', localStorage.getItem('tourma_advance_count_' + tourneyId));
                window.SingleEliminationEngine.init(tourneyId, null, preloadedTeams, cutTarget, currentStage);
                console.log('[JSP init] final cutTarget passed to engine=', cutTarget);
            });
        </script>
    </body>
</html>
