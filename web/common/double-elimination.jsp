<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@page import="dao.TournamentDAO"%>
<%@page import="dao.ParticipantDAO"%>
<%@page import="dao.DoubleEliminationDAO"%>
<%@page import="model.Tournament"%>
<%@page import="model.Team"%>
<%@page import="java.util.List"%>
<%!
    public static String escapeJsString(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("'", "\\'")
                  .replace("\"", "\\\"")
                  .replace("\r", "\\r")
                  .replace("\n", "\\n");
    }
%>
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
    String tourneyName = "Giải Đấu Double Elimination";
    String deTeamsJson = "[]";
    String dbMatchesJson = "[]";
    String dbTournamentStatus = "DRAFT";
    int cutTarget = 0;
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
                if ("MULTI_STAGE".equals(tournamentType) && currentStage == 1) {
                    cutTarget = t.getAdvancingSeatsCount();
                    if (cutTarget >= totalTeamsCount || cutTarget <= 1) {
                        cutTarget = Math.max(2, (int) Math.pow(2, Math.max(1, (int) Math.floor(Math.log(totalTeamsCount) / Math.log(2)) - 1)));
                    }
                } else {
                    cutTarget = 0;
                }
                if (dbStage2Teams == null) {
                    dbStage2Teams = t.getStage2Teams();
                }
                if (dbMultiStageConfig == null) {
                    dbMultiStageConfig = t.getMultiStageConfig();
                }
            }
            if (currentStage == 2 && dbStage2Teams != null && !dbStage2Teams.trim().isEmpty() && !dbStage2Teams.trim().equals("[]")) {
                deTeamsJson = dbStage2Teams;
            } else if (plist != null && !plist.isEmpty()) {
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
                    sb.append("\"").append(tName.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                }
                sb.append("]");
                deTeamsJson = sb.toString();
            }

            DoubleEliminationDAO deDao = new DoubleEliminationDAO();
            String jsonM = deDao.getMatchesJsonForFrontend(tourneyId, currentStage);
            if (jsonM != null && !jsonM.trim().isEmpty() && !jsonM.trim().equals("[]")) {
                dbMatchesJson = jsonM;
            }
        } catch (Exception e) {}
    }
    if (request.getAttribute("dbMatchesJson") != null) {
        String reqJson = (String) request.getAttribute("dbMatchesJson");
        if (reqJson != null && !reqJson.trim().isEmpty() && !reqJson.trim().equals("[]")) {
            dbMatchesJson = reqJson;
        }
    }
    if (currentStage == 2 && !("LOCKED".equals(dbStage1Status) || "COMPLETED".equals(dbStage1Status))) {
        dbMatchesJson = "[]";
        dbStage2Teams = null;
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

        <!-- Google Font Readex Pro -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap" rel="stylesheet">

        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

        <script src="${pageContext.request.contextPath}/js/theme.js?v=20261031-classic"></script>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=20261031m">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=18">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/workspace.css?v=18">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-workspace.css">
        <!-- Main Stylesheets -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-viewport.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-card.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/match-card.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/single-elimination.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/double-elimination.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/final-stage-popup.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/stage-end-popup.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/empty-team-alert.css?v=<%= System.currentTimeMillis() %>">
    </head>
    <body class="ui-page tournament-workspace de-page">
        <!-- Empty Team Alert Component -->
        <jsp:include page="/common/component/empty-team-alert.jsp"/>

        <!-- Final Stage Popup Banner -->
        <jsp:include page="/common/component/final-stage-popup.jsp"/>

        <!-- Stage End Popup Component -->
        <jsp:include page="/common/component/stage-end-popup.jsp"/>

        <!-- Stage Finish Alert Component (Locked Stage 2) -->

        <!-- Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="tournaments"/>
            <jsp:param name="themeToggle" value="true"/>
        </jsp:include>

        <div class="tournament-shell">
        <details class="tournament-navigation" open><summary>Điều hướng giải đấu <i class="fa-solid fa-chevron-down" aria-hidden="true"></i></summary>
        <!-- Sidebar Component -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="activeStep" value="<%= activeStepVal %>"/>
            <jsp:param name="id" value="<%= tourneyId %>"/>
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
        </jsp:include>

        </details>
        <!-- Main Content Area -->
        <main class="se-main de-main" aria-label="Sơ đồ Double Elimination">

            <!-- Top Tournament Navigation & Control Bar Component -->
            <jsp:include page="/common/component/tournament-navbar.jsp">
                <jsp:param name="tourneyName" value="<%= tourneyName %>" />
                <jsp:param name="format" value='<%= (currentStage == 2) ? "Stage 2: Double Elimination" : "Double Elimination" %>' />
                <jsp:param name="formatBadgeClass" value="format-badge-double" />
                <jsp:param name="tournamentType" value="<%= tournamentType %>" />
                <jsp:param name="cutTarget" value="<%= cutTarget %>" />
                <jsp:param name="showQuickMode" value="true" />
                <jsp:param name="showReset" value="true" />
                <jsp:param name="resetLabel" value="Reset Nhánh" />
                <jsp:param name="resetModalTitle" value="Xác Nhận Reset Nhánh Đấu" />
                <jsp:param name="resetWarningText" value="Hành động này sẽ XÓA TOÀN BỘ tỷ số và kết quả các trận đã đấu, reset lại sơ đồ Double Elimination nguyên bản ban đầu từ danh sách hạt giống." />
                <jsp:param name="engineName" value="DoubleEliminationEngine" />
                <jsp:param name="view1Icon" value="fa-diagram-project" />
                <jsp:param name="view1Label" value="Sơ Đồ Nhánh" />
                <jsp:param name="view2Icon" value="fa-list-ol" />
                <jsp:param name="view2Label" value="Danh Sách Trận" />
            </jsp:include>
            <jsp:include page="/common/component/stage-finish-alert.jsp" />

            <!-- DIRECT EMPTY ALERT CONTAINER (Shown when team count < 2) -->
            <div id="deEmptyAlertContainer" data-ui-style="double-elimination-jsp-001" style="display: none;"></div>

            <!-- 1. DUAL VIEWPORTS WORKSPACE (Upper Bracket & Lower Bracket) -->
            <div id="deDualViewportWorkspace" class="de-dual-viewport-workspace">

                <!-- UPPER BRACKET SECTION (Includes Grand Finals) -->
                <div class="de-viewport-section">
                    <!-- Standalone Viewport Navbar Header Card -->
                    <div id="upperViewportFrame" class="bracket-viewport-frame de-viewport-frame">

                        <div class="de-section-header de-section-header-card">
                        <div class="de-section-title-wrap">
                            <h2 class="de-viewport-title upper">
                                <i class="fa-solid fa-trophy text-mint"></i> Upper Bracket
                            </h2>
                        </div>
                        <div id="deupperLiveStatus" class="tournament-live-status de-branch-live-status" role="status" aria-live="polite" aria-label="Live Status upper bracket">
                            <span class="live-status-count team-count-badge">S: <strong data-category="seed">—/—</strong></span>
                            <span class="live-status-count format-badge-single">US: <strong data-category="unseed">—/—</strong></span>
                            <span class="live-status-count format-badge-double">G: <strong data-category="guest">—/—</strong></span>
                        </div>

<div id="upperZoomToolbar" class="bracket-zoom-toolbar de-fixed-zoom-toolbar">
                            <button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.zoomOut('upperViewportContainer')" title="Thu nhỏ (-)">
                                <i class="fa-solid fa-minus"></i>
                            </button>
                            <span id="upperZoomBadge" class="zoom-level-badge">100%</span>
                            <button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.zoomIn('upperViewportContainer')" title="Phóng to (+)">
                                <i class="fa-solid fa-plus"></i>
                            </button>
                            <button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.resetZoom('upperViewportContainer')" title="Reset (100%)">
                                <i class="fa-solid fa-rotate-right"></i>
                            </button>
                        </div>

                        </div>
                        <div id="upperViewportContainer" class="bracket-viewport-container de-viewport-container">
                            <div id="upperViewportCanvas" class="bracket-viewport-canvas de-viewport-canvas" data-group-color="#2dd4bf" data-ui-style="double-elimination-jsp-002">
                                <div id="upperBracketColumnsWrapper" class="single-bracket-columns-wrapper de-bracket-columns-wrapper" data-group-color="#2dd4bf" data-ui-style="double-elimination-jsp-003"></div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- LOWER BRACKET SECTION -->
                <div class="de-viewport-section">
                    <!-- Standalone Viewport Navbar Header Card -->
                    <div id="lowerViewportFrame" class="bracket-viewport-frame de-viewport-frame">
                        <div class="de-section-header de-section-header-card">
                        <div class="de-section-title-wrap">
                            <h2 class="de-viewport-title lower">
                                <i class="fa-solid fa-shield-halved text-rose"></i> Lower Bracket
                            </h2>
                        </div>
                        <div id="delowerLiveStatus" class="tournament-live-status de-branch-live-status" role="status" aria-live="polite" aria-label="Live Status lower bracket">
                            <span class="live-status-count team-count-badge">S: <strong data-category="seed">—/—</strong></span>
                            <span class="live-status-count format-badge-single">US: <strong data-category="unseed">—/—</strong></span>
                            <span class="live-status-count format-badge-double">G: <strong data-category="guest">—/—</strong></span>
                        </div>

<div id="lowerZoomToolbar" class="bracket-zoom-toolbar de-fixed-zoom-toolbar">
                            <button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.zoomOut('lowerViewportContainer')" title="Thu nhỏ (-)">
                                <i class="fa-solid fa-minus"></i>
                            </button>
                            <span id="lowerZoomBadge" class="zoom-level-badge">100%</span>
                            <button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.zoomIn('lowerViewportContainer')" title="Phóng to (+)">
                                <i class="fa-solid fa-plus"></i>
                            </button>
                            <button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.resetZoom('lowerViewportContainer')" title="Reset (100%)">
                                <i class="fa-solid fa-rotate-right"></i>
                            </button>
                        </div>

                        </div>
                        <div id="lowerViewportContainer" class="bracket-viewport-container de-viewport-container">
                            <div id="lowerViewportCanvas" class="bracket-viewport-canvas de-viewport-canvas lower-canvas" data-group-color="#ff4655" data-ui-style="double-elimination-jsp-004">
                                <div id="lowerBracketColumnsWrapper" class="single-bracket-columns-wrapper de-bracket-columns-wrapper lower-wrapper" data-bracket-type="LOWER" data-group-color="#ff4655" data-ui-style="double-elimination-jsp-005"></div>
                            </div>
                        </div>
                    </div>
                </div>

            </div>

            <!-- 2. MATCHES LIST VIEW CONTAINER -->
            <div id="deListViewContainer" class="de-list-view-container"></div>

        </main>
        </div>

        <!-- Dedicated Modal Popup for Score Entry & Winner Selection -->
        <jsp:include page="/common/component/popup.jsp"/>

        <!-- Context Path Injection for AJAX Operations -->
        <script>
            window.TourmaContextPath = '${pageContext.request.contextPath}';
            window.TourmaTournamentId = "<%= escapeJsString((tourneyId != null && !tourneyId.trim().isEmpty()) ? tourneyId : "demo") %>";
            window.TourmaDbStage1Status = window.TourmaDbStage1Status || {};
            window.TourmaDbStage1Status["<%= escapeJsString((tourneyId != null && !tourneyId.trim().isEmpty()) ? tourneyId : "demo") %>"] = "<%= escapeJsString(dbStage1Status) %>";
            window.TourmaDbTournamentStatus = window.TourmaDbTournamentStatus || {};
            window.TourmaDbTournamentStatus["<%= escapeJsString((tourneyId != null && !tourneyId.trim().isEmpty()) ? tourneyId : "demo") %>"] = "<%= escapeJsString(dbTournamentStatus) %>";
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
        <script src="${pageContext.request.contextPath}/js/empty-team-alert.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/stage-finish-alert.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/double-elimination.js?v=<%= System.currentTimeMillis() %>"></script>

        <!-- Page Bootstrap Execution -->
        <script>
            document.addEventListener('DOMContentLoaded', function () {
                var tourneyId = '<%= escapeJsString((tourneyId != null && !tourneyId.trim().isEmpty()) ? tourneyId : "demo") %>';
                var dbTournamentStatus = '<%= escapeJsString(dbTournamentStatus) %>';
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
                var tourneyName = '<%= escapeJsString(tourneyName) %>';
                var preloadedTeams = <%= deTeamsJson %>;
                var isMultiStage = <%= "MULTI_STAGE".equals(tournamentType) ? "true" : "false" %>;
                var tournamentType = isMultiStage ? 'MULTI_STAGE' : 'SINGLE_STAGE';
                try { localStorage.setItem('tourma_type_' + tourneyId, tournamentType); } catch(e) {}
                var currentStage = <%= currentStage %>;

                var cutTarget = 0;
                if (currentStage === 2) {
                    cutTarget = 0; // Stage 2 always plays to Grand Final champion!
                } else if (!isMultiStage) {
                    try { localStorage.removeItem('tourma_advance_count_' + tourneyId); } catch (e) { }
                    try { localStorage.removeItem('tourma_cut_target_' + tourneyId); } catch (e) { }
                    cutTarget = 0;
                } else {
                    try {
                        var multiCfg = JSON.parse(localStorage.getItem('tourma_multi_config_' + tourneyId));
                        if (multiCfg && multiCfg.stage1Config) {
                            var cfgAdv = multiCfg.stage1Config.advanceCount || multiCfg.stage1Config.totalAdvanceCount || 0;
                            if (cfgAdv > 1) {
                                cutTarget = cfgAdv;
                                localStorage.setItem('tourma_advance_count_' + tourneyId, cfgAdv);
                                localStorage.setItem('tourma_cut_target_' + tourneyId, cfgAdv);
                            }
                        }
                    } catch (e) { }
                    if (!cutTarget || cutTarget <= 1) {
                        try {
                            var adv = localStorage.getItem('tourma_advance_count_' + tourneyId)
                                || localStorage.getItem('tourma_cut_target_' + tourneyId);
                            if (adv) cutTarget = parseInt(adv, 10);
                        } catch (e) { }
                    }
                    if (!cutTarget || cutTarget <= 1) {
                        cutTarget = <%= cutTarget %>;
                    }
                }

                // Check stage2Teams from DB first, then fallback to localStorage
                var stage2TeamsRaw = <%= (dbStage2Teams != null && !dbStage2Teams.trim().isEmpty() && !dbStage2Teams.trim().equals("[]")) ? dbStage2Teams : "null" %>;
                if (!stage2TeamsRaw || stage2TeamsRaw.length === 0) {
                    try { stage2TeamsRaw = JSON.parse(localStorage.getItem('tourma_stage2_teams_' + tourneyId)); } catch(e) {}
                }

                // Resolve team list
                var finalTeams = [];
                if (currentStage === 2) {
                    var s2TeamsRaw = <%= (dbStage2Teams != null && !dbStage2Teams.trim().isEmpty() && !dbStage2Teams.trim().equals("[]")) ? dbStage2Teams : "null" %>;
                    if (!s2TeamsRaw || s2TeamsRaw.length === 0) {
                        try { s2TeamsRaw = JSON.parse(localStorage.getItem('tourma_stage2_teams_' + tourneyId)); } catch(e) {}
                    }
                    if (s2TeamsRaw && s2TeamsRaw.length > 0) {
                        finalTeams = s2TeamsRaw;
                    } else if (preloadedTeams && preloadedTeams.length > 0) {
                        finalTeams = preloadedTeams;
                    }
                    cutTarget = 0; // Stage 2 plays to Grand Final champion!
                } else {
                    finalTeams = preloadedTeams;
                }

                var dbMatches = <%= dbMatchesJson %>;
                window.TourmaContextDbMatches = dbMatches;

                window.DoubleEliminationEngine.init({
                    tournamentId: tourneyId,
                    tournamentName: tourneyName,
                    teamsList: finalTeams,
                    cutTarget: cutTarget,
                    tournamentType: tournamentType,
                    stage: currentStage,
                    dbMatches: dbMatches
                });
            });
        </script>
    </body>
</html>
