<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@page import="dao.TournamentDAO"%>
<%@page import="dao.ParticipantDAO"%>
<%@page import="dao.SingleEliminationDAO"%>
<%@page import="model.Tournament"%>
<%@page import="model.Team"%>
<%@page import="java.util.List"%>
<%
    String tourneyId = (String) request.getAttribute("tournamentId");
    if (tourneyId == null || tourneyId.trim().isEmpty()) {
        tourneyId = request.getParameter("id");
    }
    if (tourneyId == null || tourneyId.trim().isEmpty()) {
        tourneyId = request.getParameter("tournamentId");
    }
    if (tourneyId == null || tourneyId.trim().isEmpty()) {
        tourneyId = request.getParameter("tourneyId");
    }
    String tourneyName = "Giải Đấu Single Elimination";
    String teamsJson = "[]";
    String dbMatchesJson = "[]";
    if (request.getAttribute("dbMatchesJson") != null) {
        String reqM = (String) request.getAttribute("dbMatchesJson");
        if (reqM != null && !reqM.trim().isEmpty() && !reqM.trim().equals("[]")) {
            dbMatchesJson = reqM;
        }
    }
    String dbTournamentStatus = "DRAFT";
    int cutTarget = 0;
    String tournamentType = "SINGLE_STAGE";
    String stageParam = request.getParameter("stage");
    int currentStage = (stageParam != null && "2".equals(stageParam.trim())) ? 2 : 1;
    String activeStepVal = (currentStage == 2) ? "stage2" : "stage1";

    String seriesIdVal = request.getParameter("seriesId");
    if (seriesIdVal == null) seriesIdVal = "";

    String dbStage1Status = "PENDING";
    String dbStage2Teams = null;
    String dbMultiStageConfig = null;
    if (request.getAttribute("dbStage2Teams") != null) {
        dbStage2Teams = (String) request.getAttribute("dbStage2Teams");
    }
    if (request.getAttribute("dbMultiStageConfig") != null) {
        dbMultiStageConfig = (String) request.getAttribute("dbMultiStageConfig");
    }

    if (tourneyId != null && !tourneyId.trim().isEmpty()) {
        try {
            TournamentDAO tDao = new TournamentDAO();
            Tournament t = tDao.getTournamentById(tourneyId);
            if (t != null) {
                if (t.getSeriesId() != null && !t.getSeriesId().trim().isEmpty()) {
                    seriesIdVal = t.getSeriesId().trim();
                }
                if (t.getName() != null && !t.getName().trim().isEmpty()) {
                    tourneyName = t.getName();
                }
                if (t.getStatus() != null && !t.getStatus().trim().isEmpty()) {
                    dbTournamentStatus = t.getStatus().trim();
                }
                if (t.getStage1Status() != null && !t.getStage1Status().trim().isEmpty()) {
                    dbStage1Status = t.getStage1Status().trim();
                }
                if (t.getTournamentType() != null) {
                    tournamentType = t.getTournamentType();
                }
                if ("MULTI_STAGE".equals(tournamentType) && currentStage == 1) {
                    cutTarget = t.getAdvancingSeatsCount();
                }
                if (dbStage2Teams == null) {
                    dbStage2Teams = t.getStage2Teams();
                }
                if (dbMultiStageConfig == null) {
                    dbMultiStageConfig = t.getMultiStageConfig();
                }
            }
            ParticipantDAO pDao = new ParticipantDAO();
            List<Team> plist = pDao.getTeamsByTournamentId(tourneyId);
            if (plist != null && !plist.isEmpty()) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < plist.size(); i++) {
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
                teamsJson = sb.toString();
            }

            SingleEliminationDAO seDao = new SingleEliminationDAO();
            String jsonM = seDao.getMatchesJsonForFrontend(tourneyId, currentStage);
            if (jsonM != null && !jsonM.trim().isEmpty() && !jsonM.trim().equals("[]")) {
                dbMatchesJson = jsonM;
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
        <title><%= tourneyName %> - Single Elimination - Tourma</title>

        <!-- Google Font Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">

        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

        <!-- Main Stylesheets -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-viewport.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-card.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/match-card.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/single-elimination.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/final-stage-popup.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/stage-end-popup.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/empty-team-alert.css?v=<%= System.currentTimeMillis() %>">
    </head>
    <body>
        <!-- Empty Team Alert Component -->
        <jsp:include page="/common/component/empty-team-alert.jsp" />

        <!-- Final Stage Popup Banner -->
        <jsp:include page="/common/component/final-stage-popup.jsp" />

        <!-- Stage End Popup Component -->
        <jsp:include page="/common/component/stage-end-popup.jsp" />

        <!-- Stage Finish Alert Component (Locked Stage 2) -->
        <jsp:include page="/common/component/stage-finish-alert.jsp" />

        <!-- Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="tournaments" />
        </jsp:include>

        <!-- Sidebar Component (Step 4: Vòng Đấu / Sơ Đồ Nhánh) -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="activeStep" value="bracket" />
            <jsp:param name="id" value="<%= tourneyId %>" />
            <jsp:param name="seriesId" value="<%= seriesIdVal %>" />
        </jsp:include>

        <!-- Main Content Area Shifted Right by Sidebar -->
        <main class="container has-sidebar">

            <!-- Top Tournament Navigation & Control Bar Component -->
            <jsp:include page="/common/component/tournament-navbar.jsp">
                <jsp:param name="tourneyName" value="<%= tourneyName %>" />
                <jsp:param name="format" value="Single Elimination" />
                <jsp:param name="formatBadgeClass" value="format-badge-single" />
                <jsp:param name="tournamentType" value="<%= tournamentType %>" />
                <jsp:param name="cutTarget" value="<%= cutTarget %>" />
                <jsp:param name="showQuickMode" value="true" />
                <jsp:param name="showReset" value="true" />
                <jsp:param name="resetLabel" value="Reset Nhánh" />
                <jsp:param name="resetModalTitle" value="Xác Nhận Reset Nhánh Đấu" />
                <jsp:param name="resetWarningText" value="Hành động này sẽ XÓA TOÀN BỘ tỷ số và kết quả các trận đã đấu, reset lại sơ đồ Single Elimination nguyên bản ban đầu từ danh sách hạt giống." />
                <jsp:param name="engineName" value="SingleEliminationEngine" />
                <jsp:param name="view1Icon" value="fa-diagram-project" />
                <jsp:param name="view1Label" value="Sơ Đồ Cây" />
                <jsp:param name="view2Icon" value="fa-list-ol" />
                <jsp:param name="view2Label" value="Danh Sách Trận" />
            </jsp:include>

            <!-- DIRECT EMPTY ALERT CONTAINER (Shown when team count < 2) -->
            <div id="singleEmptyAlertContainer" style="display: none; width: 100%;"></div>

            <!-- MODE 1: BRACKET TREE VIEW (Inside Drag-to-Pan Viewport Frame) -->
            <div id="bracketViewportFrame" class="bracket-viewport-frame">

                <!-- Floating Zoom Toolbar (Fixed at Top-Right Corner of Viewport Frame) -->
                <div class="bracket-zoom-toolbar">
                    <button type="button" class="btn-zoom"
                        onclick="window.TourmaViewport && window.TourmaViewport.zoomOut()"
                        title="Thu nhỏ (-)">
                        <i class="fa-solid fa-minus"></i>
                    </button>
                    <span id="zoomLevelBadge" class="zoom-level-badge">100%</span>
                    <button type="button" class="btn-zoom"
                        onclick="window.TourmaViewport && window.TourmaViewport.zoomIn()"
                        title="Phóng to (+)">
                        <i class="fa-solid fa-plus"></i>
                    </button>
                    <button type="button" class="btn-zoom"
                        onclick="window.TourmaViewport && window.TourmaViewport.resetZoom()"
                        title="Reset (100%)">
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
        <jsp:include page="/common/component/popup.jsp" />

        <script src="${pageContext.request.contextPath}/js/bracket-algorithm.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/random-service.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/round-control-helper.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-card.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/match-card.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-viewport.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/final-stage-popup.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/empty-team-alert.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/single-elimination.js?v=<%= System.currentTimeMillis() %>"></script>

        <script>
            window.TourmaContextPath = '${pageContext.request.contextPath}';
            window.TourmaTournamentId = "<%= (tourneyId != null && !tourneyId.trim().isEmpty()) ? tourneyId : "demo" %>";
            window.TourmaDbStage1Status = window.TourmaDbStage1Status || {};
            window.TourmaDbStage1Status["<%= (tourneyId != null && !tourneyId.trim().isEmpty()) ? tourneyId : "demo" %>"] = "<%= dbStage1Status %>";
            
            window.addEventListener('DOMContentLoaded', function () {
                var tourneyId = "<%= (tourneyId != null && !tourneyId.trim().isEmpty()) ? tourneyId : "demo" %>";
                var preloadedTeams = <%= teamsJson %>;
                var cutTarget = <%= cutTarget %>;
                var currentStage = <%= currentStage %>;
                var dbMatches = <%= dbMatchesJson %>;

                // Handle Stage 2 team loading
                if (currentStage === 2) {
                    var s2TeamsRaw = <%= (dbStage2Teams != null && !dbStage2Teams.trim().isEmpty() && !dbStage2Teams.trim().equals("[]")) ? dbStage2Teams : "null" %>;
                    if (s2TeamsRaw && s2TeamsRaw.length > 0) {
                        preloadedTeams = s2TeamsRaw;
                    }
                    cutTarget = 0; // Stage 2 plays to find a champion
                }

                window.TourmaContextDbMatches = dbMatches;
                window.SingleEliminationEngine.init(tourneyId, dbMatches, preloadedTeams, cutTarget, currentStage);
            });
        </script>
    </body>
</html>