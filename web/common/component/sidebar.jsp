<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@page import="dao.TournamentDAO, dao.SeriesDAO"%>
<%@page import="model.Tournament, model.Series, java.util.List"%>
<%
    String sidebarRoute = (String) request.getAttribute("jakarta.servlet.forward.request_uri");
    if (sidebarRoute == null) sidebarRoute = request.getRequestURI();
    sidebarRoute = sidebarRoute.substring(request.getContextPath().length());
    String activeStep = "";
    if ("/rolling/dashboard".equals(sidebarRoute) || "/common/rolling/rolling-dashboard.jsp".equals(sidebarRoute)) activeStep = "dashboard";
    else if ("/rolling/standings".equals(sidebarRoute) || "/common/rolling/rolling-standings.jsp".equals(sidebarRoute)) activeStep = "standings";
    else if ("/rolling-hof".equals(sidebarRoute) || "/common/rolling/rolling-hof.jsp".equals(sidebarRoute)) activeStep = "hof";
    else if ("/rolling/team-list".equals(sidebarRoute) || "/common/rolling/rolling-team-list.jsp".equals(sidebarRoute)) activeStep = "team-list";
    else if ("/rolling/tournament-list".equals(sidebarRoute) || "/common/rolling/rolling-tournament-list.jsp".equals(sidebarRoute)) activeStep = "tournament-list";
    else if ("/common/configure-tournament-format.jsp".equals(sidebarRoute)) activeStep = "format";
    else if ("/rolling/tournament-teams".equals(sidebarRoute) || "/common/rolling/rolling-tournament-teams.jsp".equals(sidebarRoute) || "/common/configure-tournament-teams.jsp".equals(sidebarRoute)) activeStep = "teams";
    else if ("/common/manage-group.jsp".equals(sidebarRoute)) activeStep = "manage-group";
    else if ("/rolling/point-config".equals(sidebarRoute) || "/common/rolling/rolling-point-config.jsp".equals(sidebarRoute)) activeStep = "point-config";
    else if ("/common/round-robin-standings.jsp".equals(sidebarRoute)) activeStep = "2".equals(request.getParameter("stage")) ? "standings2" : "standings1";
    else if ("/common/group-standing.jsp".equals(sidebarRoute)) activeStep = "group-standing";
    else if ("/rolling/tournament-summary".equals(sidebarRoute) || "/common/rolling/rolling-tournament-summary.jsp".equals(sidebarRoute)) activeStep = "summary";
    else if (java.util.Arrays.asList("/common/single-elimination.jsp", "/common/double-elimination.jsp", "/common/swiss-stage.jsp", "/common/gsl.jsp", "/common/group-stage.jsp", "/common/round-robin.jsp").contains(sidebarRoute)) activeStep = "bracket";
    String tournamentId = request.getParameter("id");
    if (tournamentId == null) tournamentId = "";

    String seriesId = request.getParameter("seriesId");
    if (seriesId == null || seriesId.trim().isEmpty()) {
        seriesId = (String) request.getAttribute("seriesId");
    }
    if (seriesId == null) seriesId = "";

    String format = request.getParameter("format");
    String tournamentName = "";
    String tournamentType = "SINGLE_STAGE";
    Tournament currentTournament = null;

    List<String> stageFormats = null;
    String stage1Format = "SINGLE_ELIMINATION";
    String stage2Format = "SINGLE_ELIMINATION";

    if (!tournamentId.trim().isEmpty()) {
        try {
            TournamentDAO tDao = new TournamentDAO();
            currentTournament = (Tournament) request.getAttribute("setupTournament");
            if (currentTournament == null || !tournamentId.equals(currentTournament.getId())) {
                currentTournament = tDao.getTournamentById(tournamentId);
            }
            if (currentTournament != null) {
                if (currentTournament.getName() != null) tournamentName = currentTournament.getName();
                if (currentTournament.getFormat() != null) format = currentTournament.getFormat();
                if (currentTournament.getTournamentType() != null) tournamentType = currentTournament.getTournamentType();
                if (seriesId.trim().isEmpty() && currentTournament.getSeriesId() != null) {
                    seriesId = currentTournament.getSeriesId();
                }
            }
            stageFormats = tDao.getStageFormats(tournamentId);
            if (stageFormats != null && stageFormats.size() > 0) {
                stage1Format = stageFormats.get(0);
            }
            if (stageFormats != null && stageFormats.size() > 1) {
                stage2Format = stageFormats.get(1);
            }
        } catch (Exception e) {}
    }

    Series currentSeries = null;
    int subTourneyCount = 0;
    if (!seriesId.trim().isEmpty()) {
        try {
            SeriesDAO sDao = new SeriesDAO();
            Series setupSeries = (Series) request.getAttribute("setupSeries");
            boolean setupPage = setupSeries != null && seriesId.trim().equals(setupSeries.getId());
            currentSeries = setupPage ? setupSeries : sDao.getSeriesById(seriesId.trim());
            if (currentSeries != null) {
                subTourneyCount = setupPage ? sDao.countTournamentsBySeriesId(currentSeries.getId())
                        : sDao.getTournamentsBySeriesId(currentSeries.getId()).size();
            }
        } catch (Exception e) {}
    }

    if (format == null || format.trim().isEmpty()) {
        format = "SINGLE_ELIMINATION";
    }

    String targetBracketUrl = "single-elimination.jsp";
    if ("DOUBLE_ELIMINATION".equalsIgnoreCase(format)) {
        targetBracketUrl = "double-elimination.jsp";
    } else if ("ROUND_ROBIN".equalsIgnoreCase(format)) {
        targetBracketUrl = "round-robin.jsp";
    } else if ("GROUP_STAGE".equalsIgnoreCase(format)) {
        targetBracketUrl = "group-stage.jsp";
    } else if ("GSL".equalsIgnoreCase(format)) {
        targetBracketUrl = "gsl.jsp";
    } else if ("SWISS_LITE".equalsIgnoreCase(format) || "SWISS".equalsIgnoreCase(format)) {
        targetBracketUrl = "swiss-stage.jsp";
    }

    String targetBracketUrlStage1 = "single-elimination.jsp";
    if ("DOUBLE_ELIMINATION".equalsIgnoreCase(stage1Format)) targetBracketUrlStage1 = "double-elimination.jsp";
    else if ("ROUND_ROBIN".equalsIgnoreCase(stage1Format)) targetBracketUrlStage1 = "round-robin.jsp";
    else if ("GROUP_STAGE".equalsIgnoreCase(stage1Format)) targetBracketUrlStage1 = "group-stage.jsp";
    else if ("GSL".equalsIgnoreCase(stage1Format)) targetBracketUrlStage1 = "gsl.jsp";
    else if ("SWISS_LITE".equalsIgnoreCase(stage1Format) || "SWISS".equalsIgnoreCase(stage1Format)) targetBracketUrlStage1 = "swiss-stage.jsp";

    String targetBracketUrlStage2 = "single-elimination.jsp";
    if ("DOUBLE_ELIMINATION".equalsIgnoreCase(stage2Format)) targetBracketUrlStage2 = "double-elimination.jsp";
    else if ("ROUND_ROBIN".equalsIgnoreCase(stage2Format)) targetBracketUrlStage2 = "round-robin.jsp";
    else if ("GROUP_STAGE".equalsIgnoreCase(stage2Format)) targetBracketUrlStage2 = "group-stage.jsp";
    else if ("GSL".equalsIgnoreCase(stage2Format)) targetBracketUrlStage2 = "gsl.jsp";
    else if ("SWISS_LITE".equalsIgnoreCase(stage2Format) || "SWISS".equalsIgnoreCase(stage2Format)) targetBracketUrlStage2 = "swiss-stage.jsp";

    boolean isMultiStage = "MULTI_STAGE".equalsIgnoreCase(tournamentType);
    boolean hasSeries = (currentSeries != null);
    boolean hasTournament = (currentTournament != null);
    String sidebarFormat = isMultiStage ? stage1Format : format;
    boolean sidebarHasGroups = "GROUP_STAGE".equalsIgnoreCase(sidebarFormat);
    boolean sidebarCanManageGroups = sidebarHasGroups || "GSL".equalsIgnoreCase(sidebarFormat);
    pageContext.setAttribute("sidebarSeriesName", hasSeries ? currentSeries.getName() : "");
    pageContext.setAttribute("sidebarTournamentName", tournamentName);
%>


<aside class="app-left-sidebar tournament-sidebar" aria-label="Điều hướng giải đấu">

    <% if (hasSeries) { %>
    <nav class="sidebar-section" aria-label="Chuỗi giải">
    <!-- ========================================================= -->
    <!-- MỤC 1: QUẢN LÝ CHUỖI GIẢI (SERIES SECTION)                -->
    <!-- ========================================================= -->
    <header class="sidebar-group-heading">
        <span>Chuỗi giải</span>
        <h2><c:out value="${sidebarSeriesName}"/></h2>
    </header>

    <ul class="sidebar-menu-list">
        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/rolling/dashboard?id=<%= currentSeries.getId() %>"
               class="sidebar-menu-link <%= "dashboard".equals(activeStep) ? "active" : "" %>">
                <span>Dashboard Series</span>
            </a>
        </li>

        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/rolling/standings?id=<%= currentSeries.getId() %>"
               class="sidebar-menu-link <%= "standings".equals(activeStep) ? "active" : "" %>">
                <span>Bảng Xếp Hạng Series</span>
            </a>
        </li>

        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/rolling-hof?id=<%= currentSeries.getId() %>"
               class="sidebar-menu-link <%= "hof".equals(activeStep) || "rolling-hof".equals(activeStep) ? "active" : "" %>">
                <span>Bảng Vàng</span>
            </a>
        </li>

        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/rolling/team-list?id=<%= currentSeries.getId() %>"
               class="sidebar-menu-link <%= "team-list".equals(activeStep) ? "active" : "" %>">
                <span>Danh Sách Đội Partner</span>
            </a>
        </li>

        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/rolling/tournament-list?id=<%= currentSeries.getId() %>"
               class="sidebar-menu-link <%= "tournament-list".equals(activeStep) ? "active" : "" %>">
                <span>Danh Sách Giải Con (<%= subTourneyCount %>)</span>
            </a>
        </li>
    </ul>
    </nav>
    <% } %>

    <% if (hasTournament) { %>
    <nav class="sidebar-section" aria-label="Giải đấu">
    <!-- ========================================================= -->
    <!-- MỤC 2: QUẢN LÝ GIẢI ĐẤU (GIẢI ĐƠN HOẶC GIẢI CON)           -->
    <!-- ========================================================= -->
    <header class="sidebar-group-heading">
        <span><%= hasSeries ? "Giải con " + currentTournament.getTournamentIndexInSeries() : "Giải đấu" %></span>
        <h2><c:out value="${sidebarTournamentName}"/></h2>
        <% if (hasSeries) {
             String tTier = (currentTournament != null && currentTournament.getTierName() != null && !currentTournament.getTierName().trim().isEmpty())
                 ? currentTournament.getTierName().toUpperCase() : "S";
        %>
            <span class="tier-tag tier-<%= tTier.toLowerCase() %>">[<%= tTier %>]</span>
        <% } %>
    </header>

    <ul class="sidebar-menu-list">
        <!-- BƯỚC 2: QUẢN LÝ ĐỘI -->
        <li class="sidebar-menu-item">
            <a href="<%= hasSeries ? (request.getContextPath() + "/rolling/tournament-teams?id=" + tournamentId + "&seriesId=" + seriesId) : (request.getContextPath() + "/common/configure-tournament-teams.jsp?id=" + tournamentId) %>"
               class="sidebar-menu-link <%= "teams".equals(activeStep) || "step2".equals(activeStep) || "tournament-teams".equals(activeStep) ? "active" : "" %>">
                <span>Thêm Đội & Quản Lý</span>
            </a>
        </li>

        <!-- MỤC RIÊNG CHO GROUP STAGE & GSL: QUẢN LÝ BẢNG ĐẤU -->
        <li class="sidebar-menu-item" id="sidebarMenuManageGroup" <%= (sidebarCanManageGroups) ? "" : "hidden" %>>
            <a href="${pageContext.request.contextPath}/common/manage-group.jsp?id=<%= tournamentId %>&seriesId=<%= seriesId %>&format=<%= format %>"
               class="sidebar-menu-link <%= "manage-group".equals(activeStep) ? "active" : "" %>">
                <span>Quản Lý Bảng Đấu</span>
            </a>
        </li>

        <% if (hasSeries) { %>
        <!-- BƯỚC 3: SET ĐIỂM THƯỞNG GIẢI CON -->
        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/rolling/point-config?id=<%= tournamentId %>&seriesId=<%= seriesId %>"
               class="sidebar-menu-link <%= "point-config".equals(activeStep) || "points".equals(activeStep) ? "active" : "" %>">
                <span>Set Điểm Thưởng</span>
            </a>
        </li>
        <% } %>

        <!-- MỤC RIÊNG CHO ROUND ROBIN: BẢNG XẾP HẠNG (Single Stage) -->
        <li class="sidebar-menu-item" id="sidebarMenuRRStandings" <%= (!isMultiStage && "ROUND_ROBIN".equalsIgnoreCase(format)) ? "" : "hidden" %>>
            <a id="sidebarLinkRRStandings" href="${pageContext.request.contextPath}/common/round-robin-standings.jsp?id=<%= tournamentId %>&format=<%= format %>"
               class="sidebar-menu-link <%= ("standings".equals(activeStep) || "standings1".equals(activeStep)) && !isMultiStage ? "active" : "" %>">
                <span id="sidebarTextRRStandings">Bảng Xếp Hạng</span>
            </a>
        </li>

        <!-- MULTI-STAGE: BXH VÒNG 1 (Khi Vòng 1 là Round Robin) -->
        <li class="sidebar-menu-item sidebar-multistage-item" id="sidebarMenuRRStandings1" <%= (isMultiStage && "ROUND_ROBIN".equalsIgnoreCase(stage1Format)) ? "" : "hidden" %>>
            <a id="sidebarLinkRRStandings1" href="${pageContext.request.contextPath}/common/round-robin-standings.jsp?id=<%= tournamentId %>&format=ROUND_ROBIN&stage=1"
               class="sidebar-menu-link <%= "standings1".equals(activeStep) || ("standings".equals(activeStep) && !"2".equals(request.getParameter("stage"))) ? "active" : "" %>">
                <span>BXH Vòng 1</span>
            </a>
        </li>

        <!-- MULTI-STAGE: BXH VÒNG 2 (Khi Vòng 2 là Round Robin) -->
        <li class="sidebar-menu-item sidebar-multistage-item" id="sidebarMenuRRStandings2" <%= (isMultiStage && "ROUND_ROBIN".equalsIgnoreCase(stage2Format)) ? "" : "hidden" %>>
            <a id="sidebarLinkRRStandings2" href="${pageContext.request.contextPath}/common/round-robin-standings.jsp?id=<%= tournamentId %>&format=ROUND_ROBIN&stage=2"
               class="sidebar-menu-link <%= "standings2".equals(activeStep) || ("standings".equals(activeStep) && "2".equals(request.getParameter("stage"))) ? "active" : "" %>">
                <span>BXH Vòng 2</span>
            </a>
        </li>

        <!-- MỤC RIÊNG CHO GROUP STAGE: BẢNG XẾP HẠNG VÒNG BẢNG -->
        <li class="sidebar-menu-item" id="sidebarMenuGroupStandings" <%= (sidebarHasGroups) ? "" : "hidden" %>>
            <a href="${pageContext.request.contextPath}/common/group-standing.jsp?id=<%= tournamentId %>&format=GROUP_STAGE"
               class="sidebar-menu-link <%= "group-standing".equals(activeStep) ? "active" : "" %>">
                <span>BXH Vòng Bảng</span>
            </a>
        </li>

        <!-- MULTI-STAGE: VÒNG 1 (Stage 1 - SƠ ĐỒ / LỊCH ĐẤU & TỈ SỐ) -->
        <li class="sidebar-menu-item sidebar-multistage-item" id="sidebarMenuStage1" <%= (isMultiStage) ? "" : "hidden" %>>
            <a id="sidebarLinkStage1" href="${pageContext.request.contextPath}/common/<%= targetBracketUrlStage1 %>?id=<%= tournamentId %>&stage=1<%= (seriesId != null && !seriesId.isEmpty()) ? ("&seriesId=" + seriesId) : "" %>"
               class="sidebar-menu-link <%= "stage1".equals(activeStep) || ("bracket".equals(activeStep) && "1".equals(request.getParameter("stage"))) || ("bracket".equals(activeStep) && request.getParameter("stage") == null && isMultiStage) ? "active" : "" %>">
                <span id="sidebarTextStage1">Trận Đấu Vòng 1</span>
            </a>
        </li>

        <!-- MULTI-STAGE: VÒNG 2 (Stage 2 - SƠ ĐỒ / LỊCH ĐẤU & TỈ SỐ) -->
        <li class="sidebar-menu-item sidebar-multistage-item" id="sidebarMenuStage2" <%= (isMultiStage) ? "" : "hidden" %>>
            <a id="sidebarLinkStage2" href="${pageContext.request.contextPath}/common/<%= targetBracketUrlStage2 %>?id=<%= tournamentId %>&stage=2<%= (seriesId != null && !seriesId.isEmpty()) ? ("&seriesId=" + seriesId) : "" %>"
               class="sidebar-menu-link <%= "stage2".equals(activeStep) || ("bracket".equals(activeStep) && "2".equals(request.getParameter("stage"))) ? "active" : "" %>">
                <span id="sidebarTextStage2">Trận Đấu Vòng 2</span>
            </a>
        </li>

        <!-- SINGLE-STAGE: SƠ ĐỒ NHÁNH / LỊCH ĐẤU & TỈ SỐ -->
        <li class="sidebar-menu-item" id="sidebarMenuSingleStage" <%= (!isMultiStage) ? "" : "hidden" %>>
            <a href="${pageContext.request.contextPath}/common/<%= targetBracketUrl %>?id=<%= tournamentId %>&format=<%= format %><%= (seriesId != null && !seriesId.isEmpty()) ? ("&seriesId=" + seriesId) : "" %>"
               class="sidebar-menu-link <%= ("bracket".equals(activeStep) || "step3".equals(activeStep) || "gsl".equals(activeStep)) && !isMultiStage ? "active" : "" %>">
                <span><%= "ROUND_ROBIN".equalsIgnoreCase(format) ? "Lịch Thi Đấu & Tỉ Số" : ("GROUP_STAGE".equalsIgnoreCase(format) ? "Vòng Bảng & Tỉ Số" : ("GSL".equalsIgnoreCase(format) ? "Bảng Đấu GSL & Tỉ Số" : ("SWISS_LITE".equalsIgnoreCase(format) || "SWISS".equalsIgnoreCase(format) ? "Vòng Swiss & Tỉ Số" : "Sơ Đồ Nhánh & Tỉ Số"))) %></span>
            </a>
        </li>

        <% if (hasSeries) { %>
        <!-- TỔNG KẾT GIẢI ĐẤU -->
        <li class="sidebar-menu-item" id="sidebarMenuSummary">
            <a href="${pageContext.request.contextPath}/rolling/tournament-summary?id=<%= tournamentId %>&seriesId=<%= seriesId %>"
               class="sidebar-menu-link <%= "summary".equals(activeStep) ? "active" : "" %>">
                <span>Tổng Kết Giải Đấu</span>
            </a>
        </li>
        <% } %>
    </ul>
    </nav>
    <% } %>

</aside>

<script>
(function() {
    document.querySelectorAll('.tournament-sidebar .sidebar-menu-link.active').forEach(function(link) {
        link.setAttribute('aria-current', 'page');
    });
    var tid = '<%= tournamentId %>';
    if (!tid) return;
    try {
        var dbIsMulti = <%= isMultiStage ? "true" : "false" %>;
        var dbS1Format = '<%= stage1Format %>';
        var dbS2Format = '<%= stage2Format %>';

        var tType = localStorage.getItem('tourma_type_' + tid);
        var multiCfgRaw = localStorage.getItem('tourma_multi_config_' + tid);
        var hasSavedTournament = <%= hasTournament ? "true" : "false" %>;
        var isMulti = hasSavedTournament ? dbIsMulti : (tType === 'MULTI_STAGE');
        var m1 = document.getElementById('sidebarMenuStage1');
        var m2 = document.getElementById('sidebarMenuStage2');
        var sSingle = document.getElementById('sidebarMenuSingleStage');
        var link1 = document.getElementById('sidebarLinkStage1');
        var link2 = document.getElementById('sidebarLinkStage2');
        var menuRR = document.getElementById('sidebarMenuRRStandings');
        var menuRR1 = document.getElementById('sidebarMenuRRStandings1');
        var menuRR2 = document.getElementById('sidebarMenuRRStandings2');
        var menuMG = document.getElementById('sidebarMenuManageGroup');
        var menuGS = document.getElementById('sidebarMenuGroupStandings');

        if (isMulti) {
            if (m1) m1.hidden = false;
            if (m2) m2.hidden = false;
            if (sSingle) sSingle.hidden = true;
            if (menuRR) menuRR.hidden = true;

            var s1Format = dbS1Format || 'SINGLE_ELIMINATION';
            var s2Format = dbS2Format || 'SINGLE_ELIMINATION';

            if (!hasSavedTournament && multiCfgRaw) {
                try {
                    var mCfg = JSON.parse(multiCfgRaw);
                    if (mCfg.stage1Format) s1Format = mCfg.stage1Format;
                    if (mCfg.stage2Format) s2Format = mCfg.stage2Format;
                } catch(e) {}
            }

                var getPageForFormat = function(fmt) {
                    if (fmt === 'SINGLE_ELIMINATION') return 'single-elimination.jsp';
                    if (fmt === 'DOUBLE_ELIMINATION') return 'double-elimination.jsp';
                    if (fmt === 'ROUND_ROBIN') return 'round-robin.jsp';
                    if (fmt === 'GROUP_STAGE') return 'group-stage.jsp';
                    if (fmt === 'GSL') return 'gsl.jsp';
                    if (fmt === 'SWISS_LITE' || fmt === 'SWISS') return 'swiss-stage.jsp';
                    return 'single-elimination.jsp';
                };

                var seriesId = '<%= seriesId %>';
                var sParam = (seriesId && seriesId !== 'null' && seriesId !== '') ? ('&seriesId=' + seriesId) : '';
                if (link1) link1.href = '${pageContext.request.contextPath}/common/' + getPageForFormat(s1Format) + '?id=' + tid + '&stage=1' + sParam;
                if (link2) link2.href = '${pageContext.request.contextPath}/common/' + getPageForFormat(s2Format) + '?id=' + tid + '&stage=2' + sParam;

                // Display BXH Vong 1 if Stage 1 is Round Robin
                if (s1Format === 'ROUND_ROBIN' && menuRR1) {
                    menuRR1.hidden = false;
                } else if (menuRR1) {
                    menuRR1.hidden = true;
                }

                // Display BXH Vong 2 if Stage 2 is Round Robin
                if (s2Format === 'ROUND_ROBIN' && menuRR2) {
                    menuRR2.hidden = false;
                } else if (menuRR2) {
                    menuRR2.hidden = true;
                }

                // Display Group Stage / GSL items if Stage 1 is Group Stage or GSL
                if (menuMG) menuMG.hidden = s1Format !== 'GROUP_STAGE' && s1Format !== 'GSL';
                if (menuGS) menuGS.hidden = s1Format !== 'GROUP_STAGE';
            }

        var localFmt = localStorage.getItem('tourma_format_' + tid);
        if (!hasSavedTournament && !isMulti && localFmt && sSingle) {
            localFmt = localFmt.toUpperCase();
            var page = 'single-elimination.jsp';
            if (localFmt === 'DOUBLE_ELIMINATION') page = 'double-elimination.jsp';
            else if (localFmt === 'ROUND_ROBIN') page = 'round-robin.jsp';
            else if (localFmt === 'GROUP_STAGE') page = 'group-stage.jsp';
            else if (localFmt === 'GSL') page = 'gsl.jsp';
            else if (localFmt === 'SWISS_LITE' || localFmt === 'SWISS') page = 'swiss-stage.jsp';

            var aLink = sSingle.querySelector('a');
            if (aLink) {
                var seriesId = '<%= seriesId %>';
                aLink.href = '${pageContext.request.contextPath}/common/' + page + '?id=' + tid + '&format=' + localFmt + (seriesId ? ('&seriesId=' + seriesId) : '');
            }
            if (menuMG) {
                menuMG.hidden = localFmt !== 'GROUP_STAGE' && localFmt !== 'GSL';
            }
            if (menuGS) menuGS.hidden = localFmt !== 'GROUP_STAGE';
            if (menuRR) menuRR.hidden = localFmt !== 'ROUND_ROBIN';
        }
    } catch (e) {}
})();
</script>
