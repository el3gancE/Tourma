<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="dao.TournamentDAO, dao.SeriesDAO"%>
<%@page import="model.Tournament, model.Series, java.util.List"%>
<%
    String activeStep = request.getParameter("activeStep");
    if (activeStep == null || activeStep.trim().isEmpty()) {
        activeStep = "format";
    }

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

    if (!tournamentId.trim().isEmpty()) {
        try {
            TournamentDAO tDao = new TournamentDAO();
            currentTournament = tDao.getTournamentById(tournamentId);
            if (currentTournament != null) {
                if (currentTournament.getName() != null) tournamentName = currentTournament.getName();
                if (currentTournament.getFormat() != null) format = currentTournament.getFormat();
                if (currentTournament.getTournamentType() != null) tournamentType = currentTournament.getTournamentType();
                if (seriesId.trim().isEmpty() && currentTournament.getSeriesId() != null) {
                    seriesId = currentTournament.getSeriesId();
                }
            }
        } catch (Exception e) {}
    }

    Series currentSeries = null;
    List<Tournament> subTourneys = null;
    if (!seriesId.trim().isEmpty()) {
        try {
            SeriesDAO sDao = new SeriesDAO();
            currentSeries = sDao.getSeriesById(seriesId.trim());
            if (currentSeries != null) {
                TournamentDAO tDaoSub = new TournamentDAO();
                subTourneys = tDaoSub.getTournamentsBySeriesId(currentSeries.getId());
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
    } else if ("SWISS_LITE".equalsIgnoreCase(format) || "SWISS".equalsIgnoreCase(format)) {
        targetBracketUrl = "swiss-stage.jsp";
    }

    boolean isMultiStage = "MULTI_STAGE".equalsIgnoreCase(tournamentType);
    boolean hasSeries = (currentSeries != null);
    boolean hasTournament = (currentTournament != null);
%>

<link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css">

<aside class="app-left-sidebar">

    <% if (hasSeries) { %>
    <!-- ========================================================= -->
    <!-- MỤC 1: QUẢN LÝ CHUỖI GIẢI (SERIES SECTION)                -->
    <!-- ========================================================= -->
    <div class="sidebar-section-header" style="color: #fbbf24;">
        <i class="fa-solid fa-layer-group"></i> CHUỖI GIẢI (SERIES)
    </div>

    <div class="sidebar-subtourney-badge" style="border-color: rgba(245, 158, 11, 0.35); background: rgba(245, 158, 11, 0.12);">
        <i class="fa-solid fa-chart-line text-gold"></i>
        <span><%= currentSeries.getName() %></span>
    </div>

    <ul class="sidebar-menu-list" style="margin-bottom: 1.5rem;">
        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/rolling/dashboard?id=<%= currentSeries.getId() %>"
               class="sidebar-menu-link <%= "dashboard".equals(activeStep) ? "active" : "" %>">
                <i class="fa-solid fa-chart-line menu-icon text-gold"></i>
                <span>Dashboard Series</span>
            </a>
        </li>

        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/rolling/standings?id=<%= currentSeries.getId() %>"
               class="sidebar-menu-link <%= "standings".equals(activeStep) ? "active" : "" %>">
                <i class="fa-solid fa-list-ol menu-icon text-gold"></i>
                <span>Bảng Xếp Hạng Series</span>
            </a>
        </li>

        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/rolling/team-list?id=<%= currentSeries.getId() %>"
               class="sidebar-menu-link <%= "team-list".equals(activeStep) ? "active" : "" %>">
                <i class="fa-solid fa-users menu-icon text-mint"></i>
                <span>Danh Sách Đội Partner</span>
            </a>
        </li>

        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/rolling/tournament-list?id=<%= currentSeries.getId() %>"
               class="sidebar-menu-link <%= "tournament-list".equals(activeStep) ? "active" : "" %>">
                <i class="fa-solid fa-trophy menu-icon text-gold"></i>
                <span>Danh Sách Giải Con (<%= subTourneys != null ? subTourneys.size() : 0 %>)</span>
            </a>
        </li>
    </ul>
    <% } %>

    <% if (hasTournament) { %>
    <!-- ========================================================= -->
    <!-- MỤC 2: QUẢN LÝ GIẢI ĐẤU (GIẢI ĐƠN HOẶC GIẢI CON)           -->
    <!-- ========================================================= -->
    <div class="sidebar-section-header">
        <i class="fa-solid fa-bolt"></i> <%= hasSeries ? ("GIẢI CON #" + (currentTournament != null ? currentTournament.getTournamentIndexInSeries() : "")) : "CHI TIẾT GIẢI ĐẤU" %>
    </div>

    <div class="sidebar-subtourney-badge">
        <i class="fa-solid fa-trophy text-mint"></i>
        <span><%= tournamentName %></span>
    </div>

    <ul class="sidebar-menu-list">
        <!-- BƯỚC 1: THỂ THỨC -->
        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/common/configure-tournament-format.jsp?id=<%= tournamentId %>&seriesId=<%= seriesId %>"
               class="sidebar-menu-link <%= "format".equals(activeStep) || "step1".equals(activeStep) ? "active" : "" %>">
                <i class="fa-solid fa-diagram-project menu-icon"></i>
                <span>Tổng Quan Giải</span>
            </a>
        </li>

        <!-- BƯỚC 2: QUẢN LÝ ĐỘI -->
        <li class="sidebar-menu-item">
            <a href="<%= hasSeries ? (request.getContextPath() + "/rolling/tournament-teams?id=" + tournamentId + "&seriesId=" + seriesId) : (request.getContextPath() + "/common/configure-tournament-teams.jsp?id=" + tournamentId) %>"
               class="sidebar-menu-link <%= "teams".equals(activeStep) || "step2".equals(activeStep) || "tournament-teams".equals(activeStep) ? "active" : "" %>">
                <i class="fa-solid fa-user-plus menu-icon"></i>
                <span>Thêm Đội & Quản Lý</span>
            </a>
        </li>

        <% if (hasSeries) { %>
        <!-- BƯỚC 3: SET ĐIỂM THƯỞNG GIẢI CON -->
        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/rolling/point-config?id=<%= tournamentId %>&seriesId=<%= seriesId %>"
               class="sidebar-menu-link <%= "point-config".equals(activeStep) || "points".equals(activeStep) ? "active" : "" %>">
                <i class="fa-solid fa-star menu-icon text-gold"></i>
                <span>Set Điểm Thưởng</span>
            </a>
        </li>
        <% } %>

        <!-- MỤC RIÊNG CHO GROUP STAGE: QUẢN LÝ BẢNG ĐẤU -->
        <li class="sidebar-menu-item" id="sidebarMenuManageGroup" style="<%= "GROUP_STAGE".equalsIgnoreCase(format) ? "" : "display: none;" %>">
            <a href="${pageContext.request.contextPath}/common/manage-group.jsp?id=<%= tournamentId %>&format=GROUP_STAGE"
               class="sidebar-menu-link <%= "manage-group".equals(activeStep) ? "active" : "" %>">
                <i class="fa-solid fa-pen-to-square menu-icon text-mint"></i>
                <span>Quản Lý Bảng Đấu</span>
            </a>
        </li>

        <!-- MỤC RIÊNG CHO ROUND ROBIN: BẢNG XẾP HẠNG (Single Stage) -->
        <li class="sidebar-menu-item" id="sidebarMenuRRStandings" style="<%= ("ROUND_ROBIN".equalsIgnoreCase(format) && !isMultiStage) ? "" : "display: none;" %>">
            <a id="sidebarLinkRRStandings" href="${pageContext.request.contextPath}/common/round-robin-standings.jsp?id=<%= tournamentId %>&format=<%= format %>"
               class="sidebar-menu-link <%= ("standings".equals(activeStep) || "standings1".equals(activeStep)) && !isMultiStage ? "active" : "" %>">
                <i class="fa-solid fa-ranking-star menu-icon text-gold"></i>
                <span id="sidebarTextRRStandings">Bảng Xếp Hạng</span>
            </a>
        </li>

        <!-- MULTI-STAGE: BXH VÒNG 1 (Khi Vòng 1 là Round Robin) -->
        <li class="sidebar-menu-item sidebar-multistage-item" id="sidebarMenuRRStandings1" style="display: none;">
            <a id="sidebarLinkRRStandings1" href="${pageContext.request.contextPath}/common/round-robin-standings.jsp?id=<%= tournamentId %>&format=ROUND_ROBIN&stage=1"
               class="sidebar-menu-link <%= "standings1".equals(activeStep) || ("standings".equals(activeStep) && !"2".equals(request.getParameter("stage"))) ? "active" : "" %>">
                <i class="fa-solid fa-ranking-star menu-icon text-gold"></i>
                <span>BXH Vòng 1</span>
            </a>
        </li>

        <!-- MULTI-STAGE: BXH VÒNG 2 (Khi Vòng 2 là Round Robin) -->
        <li class="sidebar-menu-item sidebar-multistage-item" id="sidebarMenuRRStandings2" style="display: none;">
            <a id="sidebarLinkRRStandings2" href="${pageContext.request.contextPath}/common/round-robin-standings.jsp?id=<%= tournamentId %>&format=ROUND_ROBIN&stage=2"
               class="sidebar-menu-link <%= "standings2".equals(activeStep) || ("standings".equals(activeStep) && "2".equals(request.getParameter("stage"))) ? "active" : "" %>">
                <i class="fa-solid fa-ranking-star menu-icon text-gold"></i>
                <span>BXH Vòng 2</span>
            </a>
        </li>

        <!-- MỤC RIÊNG CHO GROUP STAGE: BẢNG XẾP HẠNG VÒNG BẢNG -->
        <li class="sidebar-menu-item" id="sidebarMenuGroupStandings" style="<%= "GROUP_STAGE".equalsIgnoreCase(format) ? "" : "display: none;" %>">
            <a href="${pageContext.request.contextPath}/common/group-standing.jsp?id=<%= tournamentId %>&format=GROUP_STAGE"
               class="sidebar-menu-link <%= "group-standing".equals(activeStep) ? "active" : "" %>">
                <i class="fa-solid fa-ranking-star menu-icon text-gold"></i>
                <span>BXH Vòng Bảng</span>
            </a>
        </li>

        <!-- MULTI-STAGE: VÒNG 1 (Stage 1 - SƠ ĐỒ / LỊCH ĐẤU & TỈ SỐ) -->
        <li class="sidebar-menu-item sidebar-multistage-item" id="sidebarMenuStage1" style="<%= isMultiStage ? "" : "display: none;" %>">
            <a id="sidebarLinkStage1" href="${pageContext.request.contextPath}/common/<%= targetBracketUrl %>?id=<%= tournamentId %>&stage=1<%= (seriesId != null && !seriesId.isEmpty()) ? ("&seriesId=" + seriesId) : "" %>"
               class="sidebar-menu-link <%= "stage1".equals(activeStep) || ("bracket".equals(activeStep) && "1".equals(request.getParameter("stage"))) || ("bracket".equals(activeStep) && request.getParameter("stage") == null && isMultiStage) ? "active" : "" %>">
                <i class="fa-solid fa-diagram-project menu-icon text-mint"></i>
                <span id="sidebarTextStage1">Trận Đấu Vòng 1</span>
            </a>
        </li>

        <!-- MULTI-STAGE: VÒNG 2 (Stage 2 - SƠ ĐỒ / LỊCH ĐẤU & TỈ SỐ) -->
        <li class="sidebar-menu-item sidebar-multistage-item" id="sidebarMenuStage2" style="<%= isMultiStage ? "" : "display: none;" %>">
            <a id="sidebarLinkStage2" href="${pageContext.request.contextPath}/common/<%= targetBracketUrl %>?id=<%= tournamentId %>&stage=2<%= (seriesId != null && !seriesId.isEmpty()) ? ("&seriesId=" + seriesId) : "" %>"
               class="sidebar-menu-link <%= "stage2".equals(activeStep) || ("bracket".equals(activeStep) && "2".equals(request.getParameter("stage"))) ? "active" : "" %>">
                <i class="fa-solid fa-trophy menu-icon text-gold"></i>
                <span id="sidebarTextStage2">Trận Đấu Vòng 2</span>
            </a>
        </li>

        <!-- SINGLE-STAGE: SƠ ĐỒ NHÁNH / LỊCH ĐẤU & TỈ SỐ -->
        <li class="sidebar-menu-item" id="sidebarMenuSingleStage" style="<%= isMultiStage ? "display: none;" : "" %>">
            <a href="${pageContext.request.contextPath}/common/<%= targetBracketUrl %>?id=<%= tournamentId %>&format=<%= format %><%= (seriesId != null && !seriesId.isEmpty()) ? ("&seriesId=" + seriesId) : "" %>"
               class="sidebar-menu-link <%= ("bracket".equals(activeStep) || "step3".equals(activeStep)) && !isMultiStage ? "active" : "" %>">
                <i class="fa-solid <%= "ROUND_ROBIN".equalsIgnoreCase(format) ? "fa-calendar-days" : ("GROUP_STAGE".equalsIgnoreCase(format) ? "fa-layer-group" : ("SWISS_LITE".equalsIgnoreCase(format) || "SWISS".equalsIgnoreCase(format) ? "fa-diagram-project" : "fa-diagram-project")) %> menu-icon"></i>
                <span><%= "ROUND_ROBIN".equalsIgnoreCase(format) ? "Lịch Thi Đấu & Tỉ Số" : ("GROUP_STAGE".equalsIgnoreCase(format) ? "Vòng Bảng & Tỉ Số" : ("SWISS_LITE".equalsIgnoreCase(format) || "SWISS".equalsIgnoreCase(format) ? "Vòng Swiss & Tỉ Số" : "Sơ Đồ Nhánh & Tỉ Số")) %></span>
            </a>
        </li>
    </ul>
    <% } %>

</aside>

<script>
(function() {
    var tid = '<%= tournamentId %>';
    if (!tid) return;
    try {
        var tType = localStorage.getItem('tourma_type_' + tid);
        var multiCfgRaw = localStorage.getItem('tourma_multi_config_' + tid);
        var isMulti = (tType === 'MULTI_STAGE' || !!multiCfgRaw);
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
            if (m1) m1.style.display = '';
            if (m2) m2.style.display = '';
            if (sSingle) sSingle.style.display = 'none';
            if (menuRR) menuRR.style.display = 'none';

            if (multiCfgRaw) {
                var mCfg = JSON.parse(multiCfgRaw);
                var s1Format = mCfg.stage1Format || 'SINGLE_ELIMINATION';
                var s2Format = mCfg.stage2Format || 'DOUBLE_ELIMINATION';

                var getPageForFormat = function(fmt) {
                    if (fmt === 'SINGLE_ELIMINATION') return 'single-elimination.jsp';
                    if (fmt === 'DOUBLE_ELIMINATION') return 'double-elimination.jsp';
                    if (fmt === 'ROUND_ROBIN') return 'round-robin.jsp';
                    if (fmt === 'GROUP_STAGE') return 'group-stage.jsp';
                    if (fmt === 'SWISS_LITE' || fmt === 'SWISS') return 'swiss-stage.jsp';
                    return 'single-elimination.jsp';
                };

                var seriesId = '<%= seriesId %>';
                var sParam = (seriesId && seriesId !== 'null' && seriesId !== '') ? ('&seriesId=' + seriesId) : '';
                if (link1) link1.href = '${pageContext.request.contextPath}/common/' + getPageForFormat(s1Format) + '?id=' + tid + '&stage=1' + sParam;
                if (link2) link2.href = '${pageContext.request.contextPath}/common/' + getPageForFormat(s2Format) + '?id=' + tid + '&stage=2' + sParam;

                // Display BXH Vong 1 if Stage 1 is Round Robin
                if (s1Format === 'ROUND_ROBIN' && menuRR1) {
                    menuRR1.style.display = '';
                } else if (menuRR1) {
                    menuRR1.style.display = 'none';
                }

                // Display BXH Vong 2 if Stage 2 is Round Robin
                if (s2Format === 'ROUND_ROBIN' && menuRR2) {
                    menuRR2.style.display = '';
                } else if (menuRR2) {
                    menuRR2.style.display = 'none';
                }

                // Display Group Stage items if Stage 1 is Group Stage
                if (s1Format === 'GROUP_STAGE') {
                    if (menuMG) menuMG.style.display = '';
                    if (menuGS) menuGS.style.display = '';
                }
            }
        }

        var localFmt = localStorage.getItem('tourma_format_' + tid);
        if (!isMulti && localFmt && sSingle) {
            localFmt = localFmt.toUpperCase();
            var page = 'single-elimination.jsp';
            if (localFmt === 'DOUBLE_ELIMINATION') page = 'double-elimination.jsp';
            else if (localFmt === 'ROUND_ROBIN') page = 'round-robin.jsp';
            else if (localFmt === 'GROUP_STAGE') page = 'group-stage.jsp';
            else if (localFmt === 'SWISS_LITE' || localFmt === 'SWISS') page = 'swiss-stage.jsp';
            
            var aLink = sSingle.querySelector('a');
            if (aLink) {
                var seriesId = '<%= seriesId %>';
                aLink.href = '${pageContext.request.contextPath}/common/' + page + '?id=' + tid + '&format=' + localFmt + (seriesId ? ('&seriesId=' + seriesId) : '');
            }
        }
    } catch (e) {}
})();
</script>