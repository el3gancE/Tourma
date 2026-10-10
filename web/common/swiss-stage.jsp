<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List"%>
<%@page import="model.Team"%>
<%@page import="model.Tournament"%>
<%@page import="dao.TournamentDAO"%>
<%@page import="dao.ParticipantDAO"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%
    String safeTourneyId = request.getParameter("id");
    if (safeTourneyId == null || safeTourneyId.trim().isEmpty()) {
        safeTourneyId = "";
    }
    String stageParam = request.getParameter("stage");
    int currentStage = (stageParam != null && "2".equals(stageParam.trim())) ? 2 : 1;
    String activeStepVal = (currentStage == 2) ? "stage2" : "stage1";
    String tourneyName = "Giải Đấu Swiss";
    List<Team> dbTeamsList = null;
    String dbMatchesJson = "[]";
    String dbTournamentStatus = "DRAFT";
    String dbStage1Status = "PENDING";

    if (!safeTourneyId.isEmpty()) {
        try {
            TournamentDAO tDao = new TournamentDAO();
            Tournament t = tDao.getTournamentById(safeTourneyId);
            if (t != null) {
                if (t.getName() != null && !t.getName().trim().isEmpty()) {
                    tourneyName = t.getName();
                }
                if (t.getStatus() != null && !t.getStatus().trim().isEmpty()) {
                    dbTournamentStatus = t.getStatus().trim();
                }
                if (t.getStage1Status() != null && !t.getStage1Status().trim().isEmpty()) {
                    dbStage1Status = t.getStage1Status().trim();
                }
            }
            ParticipantDAO pDao = new ParticipantDAO();
            dbTeamsList = pDao.getTeamsByTournamentId(safeTourneyId);
            dao.SwissSystemDAO sDao = new dao.SwissSystemDAO();
            String jsonM = sDao.getMatchesJsonForFrontend(safeTourneyId, currentStage);
            if (jsonM != null && !jsonM.trim().isEmpty() && !jsonM.trim().equals("[]")) {
                dbMatchesJson = jsonM;
            }
        } catch (Exception ignore) {}
    }
    if (request.getAttribute("dbMatchesJson") != null) {
        String reqJson = (String) request.getAttribute("dbMatchesJson");
        if (reqJson != null && !reqJson.trim().isEmpty() && !reqJson.trim().equals("[]")) {
            dbMatchesJson = reqJson;
        }
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
        <title><%= tourneyName %> - Swiss - Tourma</title>

        <!-- Google Font Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">

        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

        <!-- Shared System Stylesheets -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-navbar.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-viewport.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-card.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/match-card.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/final-stage-popup.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/stage-end-popup.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/stage-finish-alert.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/empty-team-alert.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/swiss-stage.css">
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

            <!-- Top Tournament Navigation & Control Bar Component -->
            <jsp:include page="/common/component/tournament-navbar.jsp">
                <jsp:param name="tourneyName" value="<%= tourneyName %>" />
                <jsp:param name="format" value='<%= (currentStage == 2) ? "Stage 2: Swiss System" : "Swiss System" %>' />
                <jsp:param name="formatBadgeClass" value="format-badge-swiss" />
                <jsp:param name="tournamentType" value="SWISS" />
                <jsp:param name="cutTarget" value="<%= (currentStage == 1) ? 8 : 0 %>" />
                <jsp:param name="showQuickMode" value="true" />
                <jsp:param name="showReset" value="true" />
                <jsp:param name="resetLabel" value="Reset Vòng Đấu" />
                <jsp:param name="resetModalTitle" value="Xác Nhận Reset Toàn Bộ Swiss" />
                <jsp:param name="resetWarningText" value="Hành động này sẽ XÓA TOÀN BỘ tỷ số và kết quả các trận Swiss, reset lại sơ đồ nguyên bản ban đầu từ danh sách hạt giống." />
                <jsp:param name="engineName" value="TourmaSwiss" />
                <jsp:param name="view1Icon" value="fa-diagram-project" />
                <jsp:param name="view1Label" value="Sơ Đồ Nhánh" />
                <jsp:param name="view2Icon" value="fa-list-ol" />
                <jsp:param name="view2Label" value="Danh Sách Trận" />
            </jsp:include>

            <!-- SWISS TEAM COUNT ALERT BANNER (When team count != 16) -->
            <div id="swissInvalidTeamAlert" style="display: none; background: rgba(18, 22, 32, 0.85); backdrop-filter: blur(12px); border: 1px solid rgba(244, 63, 94, 0.35); border-radius: 14px; padding: 3.5rem 2rem; text-align: center; margin-top: 1.5rem; margin-bottom: 2.5rem; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.45);">
                <div style="width: 76px; height: 76px; border-radius: 50%; background: rgba(244, 63, 94, 0.12); border: 1px solid rgba(244, 63, 94, 0.35); display: flex; align-items: center; justify-content: center; margin: 0 auto 1.25rem;">
                    <i class="fa-solid fa-users-slash" style="font-size: 2.3rem; color: #f43f5e;"></i>
                </div>
                <h2 style="font-size: 1.4rem; font-weight: 800; color: #ffffff; margin-bottom: 0.6rem;">
                    Yêu Cầu CHÍNH XÁC 16 Đội Cho Thể Thức Swiss System
                </h2>
                <p id="swissInvalidTeamDesc" style="font-size: 0.88rem; color: #94a3b8; max-width: 600px; margin: 0 auto 1.75rem; line-height: 1.6;">
                    Thể thức Swiss System đòi hỏi bắt buộc phải có đủ <strong>đúng 16 đội bóng</strong> tham gia để ghép cặp theo từng nhóm tỷ số (Record Pool) qua 5 vòng thi đấu.
                </p>
                <a id="btnGoToConfigureTeams" href="${pageContext.request.contextPath}/common/configure-tournament-teams.jsp?id=${not empty tournament.id ? tournament.id : param.id}" class="btn btn-mint" style="font-weight: 700; padding: 0.65rem 1.75rem; display: inline-flex; align-items: center; gap: 0.5rem; text-decoration: none; border-radius: 8px;">
                    <i class="fa-solid fa-user-plus"></i> Thêm / Điều Chỉnh Cho Đủ 16 Đội Ngay
                </a>
            </div>

            <!-- MAIN SWISS CONTENT WRAPPER -->
            <div id="swissMainContentWrapper">

                <!-- ════════════════════════════════════════════════════════════════ -->
                <!-- VIEW MODE 1: DẠNG DANH SÁCH (LIST VIEW)                          -->
                <!-- ════════════════════════════════════════════════════════════════ -->
                <div id="swissListView" style="display: none;">
                    <!-- Round Pills Filter Bar -->
                    <div class="swiss-round-pills-bar" id="swissListRoundPillsContainer">
                        <!-- Injected by swiss-stage.js -->
                    </div>

                    <!-- Fixtures Container -->
                    <div id="swissListFixturesContainer">
                        <!-- Injected by swiss-stage.js -->
                    </div>
                </div>

                <!-- ════════════════════════════════════════════════════════════════ -->
                <!-- VIEW MODE 2: SƠ ĐỒ BRACKET (BRACKET VIEWPORT CANVAS - NO LINES)  -->
                <!-- ════════════════════════════════════════════════════════════════ -->
                <div id="swissBracketView" style="display: block;">
                    <div class="bracket-viewport-frame">
                        
                        <!-- Floating Viewport Zoom Toolbar (Exact SE & DE Styling) -->
                        <div class="bracket-zoom-toolbar">
                            <button type="button" class="btn-zoom" onclick="TourmaViewport.zoomOut('swissViewportContainer')" title="Thu Nhỏ (Zoom Out)">
                                <i class="fa-solid fa-minus"></i>
                            </button>
                            <span id="swissZoomBadge" class="zoom-level-badge">100%</span>
                            <button type="button" class="btn-zoom" onclick="TourmaViewport.zoomIn('swissViewportContainer')" title="Phóng To (Zoom In)">
                                <i class="fa-solid fa-plus"></i>
                            </button>
                            <button type="button" class="btn-zoom" onclick="TourmaViewport.reset('swissViewportContainer')" title="Reset Chế Độ Xem">
                                <i class="fa-solid fa-rotate-right"></i>
                            </button>
                        </div>

                        <!-- Drag/Pan Viewport Container Box -->
                        <div id="swissViewportContainer" class="bracket-viewport-container">
                            <!-- Viewport Canvas (No SVG connector lines) -->
                            <div id="swissViewportCanvas" class="bracket-viewport-canvas" style="padding-top: 3.5rem;">
                                <!-- Injected Round Columns with Record Pool Cards -->
                            </div>
                        </div>

                    </div>
                </div>

            </div>

        </main>

        <!-- Shared Score Edit Popup Component -->
        <jsp:include page="/common/component/popup.jsp"/>

        <!-- Shared Core JS Engine Scripts -->
        <script>
            window.TourmaContextPath = "${pageContext.request.contextPath}";
            window.TourmaDbStage1Status = window.TourmaDbStage1Status || {};
            window.TourmaDbStage1Status["${not empty tournament.id ? tournament.id : param.id}"] = "<%= dbStage1Status %>";
            window.TourmaDbTournamentStatus = window.TourmaDbTournamentStatus || {};
            window.TourmaDbTournamentStatus["${not empty tournament.id ? tournament.id : param.id}"] = "<%= dbTournamentStatus %>";
            window.swissTournamentId = "${not empty tournament.id ? tournament.id : param.id}";
            window.swissContextPath = "${pageContext.request.contextPath}";
            window.swissCurrentStage = <%= currentStage %>;
            window.swissTournamentStatus = '<%= dbTournamentStatus %>';
            // Sync DB tournament status to localStorage for FinalStagePopup
            (function() {
                var tid = window.swissTournamentId;
                if (tid) {
                    if (window.swissTournamentStatus === 'COMPLETED') {
                        try { localStorage.setItem('tourma_final_locked_' + tid, 'true'); } catch(e) {}
                    } else {
                        try {
                            localStorage.removeItem('tourma_final_locked_' + tid);
                            localStorage.removeItem('tourma_champion_' + tid);
                            localStorage.removeItem('tourma_final_champion_' + tid);
                        } catch(e) {}
                    }
                }
            })();
            window.dbSwissMatches = <%= dbMatchesJson %>;
            window.serverTeams = [
                <% if (dbTeamsList != null && !dbTeamsList.isEmpty()) { 
                    for (int i = 0; i < dbTeamsList.size(); i++) {
                        Team tm = dbTeamsList.get(i);
                        String tName = tm.getRawName();
                        if (tName == null || tName.trim().isEmpty()) {
                            tName = tm.getNormalizedName();
                        }
                        if (tName == null) tName = "Đội " + (i + 1);
                        String nameEsc = tName.replace("\"", "\\\"").replace("\n", "").replace("\r", "");
                %>
                    { id: "<%= tm.getId() %>", name: "<%= nameEsc %>" }<%= (i < dbTeamsList.size() - 1) ? "," : "" %>
                <%  } 
                } %>
            ];
        </script>
        <script src="${pageContext.request.contextPath}/js/bracket-algorithm.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/double-elimination-algorithm.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/round-robin-algorithm.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/final-stage-popup.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/stage-end-popup.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/stage-finish-alert.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-card.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/match-card.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/popup.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-viewport.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/empty-team-alert.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/swiss-stage-algorithm.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/round-control-helper.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/swiss-stage.js?v=<%= System.currentTimeMillis() %>"></script>
    </body>
</html>
