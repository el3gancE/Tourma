<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@page import="dao.TournamentDAO"%>
<%@page import="dao.ParticipantDAO"%>
<%@page import="dao.GSLStageDAO"%>
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
    String stageParam = request.getParameter("stage");
    int currentStage = (stageParam != null && "2".equals(stageParam.trim())) ? 2 : 1;
    String activeStepVal = (currentStage == 2) ? "stage2" : "stage1";
    String tourneyName = "Giải Đấu GSL Format";
    String gslTeamsJson = "[]";
    String dbMatchesJson = "[]";
    String dbTournamentStatus = "DRAFT";
    int cutTarget = 2;
    String tournamentType = "SINGLE_STAGE";

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
            ParticipantDAO pDao = new ParticipantDAO();
            List<Team> plist = pDao.getTeamsByTournamentId(tourneyId);
            int totalTeamsCount = plist != null ? plist.size() : 8;

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
                if (t.getAdvancingSeatsCount() > 0) {
                    cutTarget = t.getAdvancingSeatsCount();
                }
                if (dbStage2Teams == null) dbStage2Teams = t.getStage2Teams();
                if (dbMultiStageConfig == null) dbMultiStageConfig = t.getMultiStageConfig();
            }

            if (plist != null && !plist.isEmpty()) {
                StringBuilder tb = new StringBuilder("[");
                for (int i = 0; i < plist.size(); i++) {
                    Team tm = plist.get(i);
                    if (i > 0) tb.append(",");
                    tb.append("{\"id\":\"").append(tm.getId()).append("\",");
                    tb.append("\"name\":\"").append(tm.getName() != null ? tm.getName().replace("\"", "\\\"") : "").append("\",");
                    tb.append("\"seed\":").append(tm.getOriginalSeed() > 0 ? tm.getOriginalSeed() : (i + 1)).append("}");
                }
                tb.append("]");
                gslTeamsJson = tb.toString();
            }

            GSLStageDAO gslDao = new GSLStageDAO();
            gslDao.ensureGSLInitialized(tourneyId, currentStage);
            dbMatchesJson = gslDao.getMatchesJsonForFrontend(tourneyId, currentStage);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title><%= tourneyName %> | GSL Format - Tourma</title>

        <!-- Favicon -->
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <link rel="alternate icon" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">

        <!-- Google Fonts: Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">

        <!-- Font Awesome -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

        <!-- Core Design System CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-navbar.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-card.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/match-card.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-viewport.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/single-elimination.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/double-elimination.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/final-stage-popup.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/stage-finish-alert.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/stage-end-popup.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/empty-team-alert.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/gsl.css?v=<%= System.currentTimeMillis() %>">
    </head>
    <body class="gsl-page-container">

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
            <jsp:param name="id" value="<%= tourneyId %>"/>
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
            <jsp:param name="format" value="GSL"/>
        </jsp:include>

        <!-- Main Content Area -->
        <main class="container has-sidebar">
            
            <!-- Tournament Navbar Component -->
            <jsp:include page="/common/component/tournament-navbar.jsp">
                <jsp:param name="tourneyName" value="<%= tourneyName %>" />
                <jsp:param name="format" value='<%= (currentStage == 2) ? "Stage 2: GSL Format" : "GSL Format" %>' />
                <jsp:param name="formatBadgeClass" value="format-badge-double" />
                <jsp:param name="tournamentType" value="<%= tournamentType %>" />
                <jsp:param name="cutTarget" value="<%= cutTarget %>" />
                <jsp:param name="showQuickMode" value="true" />
                <jsp:param name="showReset" value="true" />
                <jsp:param name="resetLabel" value="Reset Bảng Đấu" />
                <jsp:param name="resetModalTitle" value="Xác Nhận Reset Toàn Bộ GSL" />
                <jsp:param name="resetWarningText" value="Hành động này sẽ XÓA TOÀN BỘ tỷ số và kết quả các bảng đấu GSL, reset lại sơ đồ nguyên bản ban đầu." />
                <jsp:param name="engineName" value="GSLEngine" />
                <jsp:param name="view1Icon" value="fa-layer-group" />
                <jsp:param name="view1Label" value="Sơ Đồ Bảng" />
                <jsp:param name="view2Icon" value="fa-list-ol" />
                <jsp:param name="view2Label" value="Danh Sách Trận" />
            </jsp:include>

            <!-- DIRECT EMPTY ALERT CONTAINER (Shown when team count < 2) -->
            <div id="gslEmptyAlertContainer" style="display: none; width: 100%;"></div>

            <!-- 1. MULTI-GROUP VIEWPORTS WORKSPACE -->
            <div id="gslGroupsWorkspace" class="gsl-groups-workspace"></div>

            <!-- 2. MATCHES LIST VIEW CONTAINER -->
            <div id="gslListViewContainer" class="gsl-list-view-container"></div>

        </main>

        <!-- Dedicated Modal Popup for Score Entry -->
        <jsp:include page="/common/component/popup.jsp"/>

        <!-- Global Context Setup -->
        <script>
            window.TourmaContextPath = '${pageContext.request.contextPath}';
            window.TourmaTournamentId = "<%= (tourneyId != null && !tourneyId.trim().isEmpty()) ? tourneyId : "demo" %>";
            window.TourmaDbStage1Status = window.TourmaDbStage1Status || {};
            window.TourmaDbStage1Status["<%= (tourneyId != null && !tourneyId.trim().isEmpty()) ? tourneyId : "demo" %>"] = "<%= dbStage1Status %>";
            window.TourmaDbTournamentStatus = window.TourmaDbTournamentStatus || {};
            window.TourmaDbTournamentStatus["<%= (tourneyId != null && !tourneyId.trim().isEmpty()) ? tourneyId : "demo" %>"] = "<%= dbTournamentStatus %>";
        </script>

        <!-- Engine Scripts -->
        <script src="${pageContext.request.contextPath}/js/double-elimination-algorithm.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-algorithm.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/random-service.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/round-control-helper.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-card.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/match-card.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-viewport.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/final-stage-popup.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/stage-end-popup.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/empty-team-alert.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/stage-finish-alert.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/gsl.js?v=<%= System.currentTimeMillis() %>"></script>

        <!-- Page Bootstrap Execution -->
        <script>
            document.addEventListener('DOMContentLoaded', function () {
                var tourneyId = '<%= (tourneyId != null && !tourneyId.trim().isEmpty()) ? tourneyId : "demo" %>';
                var dbTournamentStatus = '<%= dbTournamentStatus %>';
                if (dbTournamentStatus === 'COMPLETED') {
                    try { localStorage.setItem('tourma_final_locked_' + tourneyId, 'true'); } catch(e) {}
                } else {
                    try {
                        localStorage.removeItem('tourma_final_locked_' + tourneyId);
                        localStorage.removeItem('tourma_champion_' + tourneyId);
                        localStorage.removeItem('tourma_final_champion_' + tourneyId);
                    } catch(e) {}
                }
                window.TourmaContextPathTourneyId = tourneyId;
                var tourneyName = '<%= tourneyName %>';
                var preloadedTeams = <%= gslTeamsJson %>;
                var isMultiStage = <%= "MULTI_STAGE".equals(tournamentType) ? "true" : "false" %>;
                var tournamentType = isMultiStage ? 'MULTI_STAGE' : 'SINGLE_STAGE';
                var currentStage = <%= currentStage %>;
                var cutTarget = <%= cutTarget %>;

                var dbMatches = <%= dbMatchesJson %>;
                window.TourmaContextDbMatches = dbMatches;

                window.GSLEngine.init({
                    tournamentId: tourneyId,
                    tournamentName: tourneyName,
                    teamsList: preloadedTeams,
                    cutTarget: cutTarget,
                    tournamentType: tournamentType,
                    stage: currentStage,
                    dbMatches: dbMatches,
                    contextPath: '${pageContext.request.contextPath}'
                });
            });
        </script>
    </body>
</html>
