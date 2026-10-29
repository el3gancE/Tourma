<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="dao.TournamentDAO" %>
<%@ page import="dao.ParticipantDAO" %>
<%@ page import="model.Tournament" %>
<%@ page import="model.Team" %>
<%@ page import="java.util.List" %>
<%
    String tournamentId = request.getParameter("id");
    if (tournamentId == null || tournamentId.trim().isEmpty()) {
        tournamentId = "demo";
    }

    TournamentDAO tDao = new TournamentDAO();
    Tournament tourney = tDao.getTournamentById(tournamentId, false);
    String tourneyName = (tourney != null && tourney.getName() != null) ? tourney.getName() : "Giải Đấu Vòng Bảng";
    String dbGroupAssignments = (tourney != null) ? tourney.getGroupAssignments() : null;
    String dbStage1Status = (tourney != null && tourney.getStage1Status() != null) ? tourney.getStage1Status() : "PENDING";
    String dbTournamentStatus = (tourney != null && tourney.getStatus() != null) ? tourney.getStatus().trim() : "DRAFT";
    String tournamentType = (tourney != null && tourney.getTournamentType() != null) ? tourney.getTournamentType() : "SINGLE_STAGE";
    int cutTarget = (tourney != null) ? tourney.getAdvancingSeatsCount() : 0;

    int currentStage = "2".equals(request.getParameter("stage")) ? 2 : 1;
    String dbMultiStageConfig = tourney == null ? null : tourney.getMultiStageConfig();
    ParticipantDAO pDao = new ParticipantDAO();
    List<Team> dbTeamsList = null;
    String dbMatchesJson = "[]";
    try {
        dbTeamsList = currentStage == 2 ? new dao.SingleEliminationDAO().getStage2TeamsList(tournamentId) : pDao.getTeamsByTournamentId(tournamentId);
        dao.GroupStageDAO gsDao = new dao.GroupStageDAO();
        String j = gsDao.getMatchesJsonForFrontend(tournamentId, currentStage);
        if (j != null && !j.trim().isEmpty() && !j.trim().equals("[]")) {
            dbMatchesJson = j;
        }
    } catch (Exception ignore) {}
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
    <title><%= tourneyName %> - Group Stage - Tourma</title>

    <!-- Google Font Readex Pro -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap" rel="stylesheet">

    <!-- Global Styling & Icons -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-navbar.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/empty-team-alert.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/match-card.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css?v=5">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-viewport.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/round-robin.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/group-stage.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/group-standing.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/stage-finish-alert.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/stage-end-popup.css">
<script src="${pageContext.request.contextPath}/js/theme.js"></script>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=24">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=18">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-workspace.css?v=18">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-screen.css?v=4">
</head>
<body class="ui-page tournament-workspace league-page" data-ui-style="group-stage-jsp-001">

    <!-- Stage Finish Alert Component (Locked Stage 2) -->

    <!-- Stage End Popup Component -->
    <jsp:include page="/common/component/stage-end-popup.jsp"/>

    <!-- TOP NAVBAR & SIDEBAR -->
    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="tournaments"/><jsp:param name="themeToggle" value="true"/>
    </jsp:include>
    <div class="tournament-shell"><details class="tournament-navigation" open><summary>Điều hướng giải đấu</summary><jsp:include page="/common/component/sidebar.jsp">
        <jsp:param name="activeStep" value="bracket" />
        <jsp:param name="id" value="<%= tournamentId %>" />
        <jsp:param name="format" value="GROUP_STAGE" />
    </jsp:include></details>

    <!-- REUSABLE EMPTY ALERT & POPUP MODAL COMPONENTS -->
    <jsp:include page="/common/component/empty-team-alert.jsp" />
    <jsp:include page="/common/component/popup.jsp" />

    <main class="se-main round-robin-container">

        <!-- TOP CONTROL BAR (Round Robin / Group Stage Design Style) -->
        <div class="rr-control-bar">
            <div class="rr-info-group">
                <h1 class="rr-tourney-title">
                    <i class="fa-solid fa-trophy text-gold"></i>
                    <span id="gsTournamentTitle"><%= tourneyName %></span>
                </h1>
                <span class="format-badge-rr" title="Group Stage">GS</span>
                <span id="gsTeamCountBadge" class="team-count-badge">0 Đội</span>
                <span id="gsAdvanceBadge" class="team-count-badge" data-ui-style="group-stage-jsp-002" style="display: inline-flex;">
                    <i class="fa-solid fa-circle-check" data-ui-style="group-stage-jsp-003"></i>
                    <span id="gsAdvanceText">0 Đội đi tiếp</span>
                </span>
            </div>

            <div class="rr-actions-group">
                <button type="button" class="btn-reset-bracket-action" onclick="TourmaGroupStage.resetAllMatches()" title="Xóa toàn bộ kết quả và thiết lập lại từ đầu">
                    <i class="fa-solid fa-rotate-right"></i> Reset Bảng Đấu
                </button>

                <div class="view-mode-toggle-group">
                    <a href="${pageContext.request.contextPath}/common/group-stage.jsp?id=<%= tournamentId %>&format=GROUP_STAGE" class="btn-view-toggle active" data-ui-style="group-stage-jsp-004">
                        <i class="fa-solid fa-calendar-days"></i> Lịch
                    </a>
                    <a href="${pageContext.request.contextPath}/common/group-standing.jsp?id=<%= tournamentId %>&format=GROUP_STAGE" class="btn-view-toggle" data-ui-style="group-stage-jsp-005">
                        <i class="fa-solid fa-ranking-star"></i> BXH
                    </a>
                </div>
            </div>
        </div>
            <jsp:include page="/common/component/stage-finish-alert.jsp" />

        <!-- CONTAINER FOR EMPTY TEAM ALERT -->
        <div id="gsEmptyAlertContainer" data-ui-style="group-stage-jsp-006" style="display: none;"></div>

        <!-- MAIN WORKSPACE CONTENT -->
        <div id="gsMainContent">

            <!-- GROUP SELECTOR FILTER PILLS BAR -->
            <div id="gsGroupSelectorBar" class="rr-round-selector-bar">
                <!-- Dynamic Group Pills (Tất cả các bảng, Bảng A, Bảng B...) -->
            </div>

            <!-- FIXTURES / MATCHES VIEW -->
            <div id="gsMatchesView">
                <div id="gsMatchesContainer" class="rr-fixtures-container"></div>
            </div>

            <!-- STANDINGS VIEW (Dynamically toggled) -->
            <div id="gsStandingsView" style="display: none;">
                <div id="gsStandingsContainer" class="gst-container" data-ui-style="group-stage-jsp-007"></div>
            </div>

        </div>
    </main></div>

    <jsp:include page="/common/component/tournament-config-state.jsp"/>
    <!-- JS SCRIPTS -->
    <script>
        window.TourmaContextPath = "${pageContext.request.contextPath}";
        window.TourmaDbStage1Status = window.TourmaDbStage1Status || {};
        window.TourmaDbStage1Status["<%= tournamentId %>"] = "<%= dbStage1Status %>";
        window.TourmaDbTournamentStatus = window.TourmaDbTournamentStatus || {};
        window.TourmaDbTournamentStatus["<%= tournamentId %>"] = "<%= dbTournamentStatus %>";
        if ('<%= dbTournamentStatus %>' !== 'COMPLETED') {
            try {
                localStorage.removeItem('tourma_final_locked_' + '<%= tournamentId %>');
                localStorage.removeItem('tourma_champion_' + '<%= tournamentId %>');
                localStorage.removeItem('tourma_final_champion_' + '<%= tournamentId %>');
            } catch(e) {}
        }
        window.TourmaTournamentType = "<%= tournamentType %>";
        window.TourmaCutTarget = <%= cutTarget %>;
        window.groupTournamentId = "<%= tournamentId %>";
        window.dbGroupMatches = <%= dbMatchesJson %>;
        window.DB_GROUP_ASSIGNMENTS = <%= (dbGroupAssignments != null && !dbGroupAssignments.trim().isEmpty() && !dbGroupAssignments.trim().equals("{}")) ? dbGroupAssignments : "null" %>;
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
                { id: "<%= tm.getId() %>", name: "<%= nameEsc %>", seed: <%= tm.getOriginalSeed() > 0 ? tm.getOriginalSeed() : (i + 1) %> }<%= (i < dbTeamsList.size() - 1) ? "," : "" %>
            <%  }
            } %>
        ];
    </script>
    <script src="${pageContext.request.contextPath}/js/bracket-algorithm.js"></script>
    <script src="${pageContext.request.contextPath}/js/random-service.js"></script>
    <script src="${pageContext.request.contextPath}/js/match-card.js"></script>
    <script src="${pageContext.request.contextPath}/js/popup.js"></script>
    <script src="${pageContext.request.contextPath}/js/empty-team-alert.js"></script>
    <script src="${pageContext.request.contextPath}/js/stage-finish-alert.js"></script>
    <script src="${pageContext.request.contextPath}/js/group-standing.js"></script>
    <script src="${pageContext.request.contextPath}/js/group-stage.js"></script>
</body>
</html>
