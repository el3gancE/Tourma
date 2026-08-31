<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="dao.TournamentDAO"%>
<%@page import="model.Tournament"%>
<%
    String activeStep = request.getParameter("activeStep");
    if (activeStep == null || activeStep.trim().isEmpty()) {
        activeStep = "format";
    }

    String tournamentId = request.getParameter("id");
    if (tournamentId == null) tournamentId = "";

    String format = request.getParameter("format");
    String tournamentName = "";
    String tournamentType = "SINGLE_STAGE";

    if (!tournamentId.trim().isEmpty()) {
        try {
            TournamentDAO tDao = new TournamentDAO();
            Tournament t = tDao.getTournamentById(tournamentId);
            if (t != null) {
                if (t.getName() != null) tournamentName = t.getName();
                if (t.getFormat() != null) format = t.getFormat();
                if (t.getTournamentType() != null) tournamentType = t.getTournamentType();
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
%>

<link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css">

<aside class="app-left-sidebar">

    <!-- CHI TIẾT GIẢI ĐẤU -->
    <div class="sidebar-section-header">
        <i class="fa-solid fa-bolt"></i> CHI TIẾT GIẢI ĐẤU
    </div>

    <div class="sidebar-subtourney-badge">
        <i class="fa-solid fa-volleyball text-mint"></i>
        <span><%= tournamentName %></span>
    </div>

    <ul class="sidebar-menu-list">
        <!-- BƯỚC 1: THỂ THỨC (Link to configure-tournament-format.jsp) -->
        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/common/configure-tournament-format.jsp?id=<%= tournamentId %>&format=<%= format %>"
               class="sidebar-menu-link <%= "format".equals(activeStep) || "step1".equals(activeStep) ? "active" : "" %>">
                <i class="fa-solid fa-diagram-project menu-icon"></i>
                <span>Tổng Quan Giải</span>
            </a>
        </li>

        <!-- BƯỚC 2: QUẢN LÝ ĐỘI -->
        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/common/configure-tournament-teams.jsp?id=<%= tournamentId %>&format=<%= format %>"
               class="sidebar-menu-link <%= "teams".equals(activeStep) || "step2".equals(activeStep) ? "active" : "" %>">
                <i class="fa-solid fa-user-plus menu-icon"></i>
                <span>Thêm Đội & Quản Lý</span>
            </a>
        </li>

        <!-- MULTI-STAGE: VÒNG 1 -->
        <li class="sidebar-menu-item sidebar-multistage-item" id="sidebarMenuStage1" style="<%= isMultiStage ? "" : "display: none;" %>">
            <a id="sidebarLinkStage1" href="${pageContext.request.contextPath}/common/single-elimination.jsp?id=<%= tournamentId %>&stage=1"
               class="sidebar-menu-link <%= "stage1".equals(activeStep) || ("bracket".equals(activeStep) && !"2".equals(request.getParameter("stage")) && request.getRequestURI().contains("single-elimination")) ? "active" : "" %>">
                <i class="fa-solid fa-trophy menu-icon"></i>
                <span>Vòng 1</span>
            </a>
        </li>

        <!-- MULTI-STAGE: VÒNG 2 -->
        <li class="sidebar-menu-item sidebar-multistage-item" id="sidebarMenuStage2" style="<%= isMultiStage ? "" : "display: none;" %>">
            <a id="sidebarLinkStage2" href="${pageContext.request.contextPath}/common/double-elimination.jsp?id=<%= tournamentId %>&stage=2"
               class="sidebar-menu-link <%= "stage2".equals(activeStep) || ("bracket".equals(activeStep) && ("2".equals(request.getParameter("stage")) || request.getRequestURI().contains("double-elimination"))) ? "active" : "" %>">
                <i class="fa-solid fa-medal menu-icon text-mint"></i>
                <span>Vòng 2</span>
            </a>
        </li>

        <!-- SINGLE-STAGE: SƠ ĐỒ NHÁNH / LỊCH ĐẤU -->
        <li class="sidebar-menu-item" id="sidebarMenuSingleStage" style="<%= isMultiStage ? "display: none;" : "" %>">
            <a href="${pageContext.request.contextPath}/common/<%= targetBracketUrl %>?id=<%= tournamentId %>&format=<%= format %>"
               class="sidebar-menu-link <%= "bracket".equals(activeStep) || "step3".equals(activeStep) ? "active" : "" %>">
                <i class="fa-solid <%= "ROUND_ROBIN".equalsIgnoreCase(format) ? "fa-calendar-days" : ("GROUP_STAGE".equalsIgnoreCase(format) ? "fa-layer-group" : ("SWISS_LITE".equalsIgnoreCase(format) || "SWISS".equalsIgnoreCase(format) ? "fa-diagram-project" : "fa-diagram-project")) %> menu-icon"></i>
                <span><%= "ROUND_ROBIN".equalsIgnoreCase(format) ? "Lịch Thi Đấu" : ("GROUP_STAGE".equalsIgnoreCase(format) ? "Vòng Bảng" : ("SWISS_LITE".equalsIgnoreCase(format) || "SWISS".equalsIgnoreCase(format) ? "Vòng Swiss" : "Sơ Đồ Nhánh")) %></span>
            </a>
        </li>

        <% if ("ROUND_ROBIN".equalsIgnoreCase(format)) { %>
        <!-- MỤC RIÊNG CHO ROUND ROBIN: BẢNG XẾP HẠNG -->
        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/common/round-robin-standings.jsp?id=<%= tournamentId %>&format=<%= format %>"
               class="sidebar-menu-link <%= "standings".equals(activeStep) ? "active" : "" %>">
                <i class="fa-solid fa-ranking-star menu-icon text-gold"></i>
                <span>Bảng Xếp Hạng</span>
            </a>
        </li>
        <% } %>

        <% if ("GROUP_STAGE".equalsIgnoreCase(format)) { %>
        <!-- MỤC RIÊNG CHO GROUP STAGE: QUẢN LÝ BẢNG ĐẤU -->
        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/common/manage-group.jsp?id=<%= tournamentId %>&format=<%= format %>"
               class="sidebar-menu-link <%= "manage-group".equals(activeStep) ? "active" : "" %>">
                <i class="fa-solid fa-pen-to-square menu-icon text-mint"></i>
                <span>Quản Lý Bảng Đấu</span>
            </a>
        </li>

        <!-- MỤC RIÊNG CHO GROUP STAGE: BẢNG XẾP HẠNG VÒNG BẢNG -->
        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/common/group-standing.jsp?id=<%= tournamentId %>&format=<%= format %>"
               class="sidebar-menu-link <%= "group-standing".equals(activeStep) ? "active" : "" %>">
                <i class="fa-solid fa-ranking-star menu-icon text-gold"></i>
                <span>BXH Vòng Bảng</span>
            </a>
        </li>
        <% } %>
    </ul>
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

        if (isMulti) {
            if (m1) m1.style.display = '';
            if (m2) m2.style.display = '';
            if (sSingle) sSingle.style.display = 'none';

            if (multiCfgRaw) {
                var mCfg = JSON.parse(multiCfgRaw);
                var s1Format = mCfg.stage1Format || 'SINGLE_ELIMINATION';
                var s2Format = mCfg.stage2Format || 'DOUBLE_ELIMINATION';

                var getPageForFormat = function(fmt) {
                    if (fmt === 'SINGLE_ELIMINATION') return 'single-elimination.jsp';
                    if (fmt === 'DOUBLE_ELIMINATION') return 'double-elimination.jsp';
                    if (fmt === 'ROUND_ROBIN') return 'round-robin.jsp';
                    if (fmt === 'GROUP_STAGE') return 'group-stage.jsp';
                    if (fmt === 'SWISS_LITE') return 'swiss-stage.jsp';
                    return 'single-elimination.jsp';
                };

                if (link1) link1.href = '${pageContext.request.contextPath}/common/' + getPageForFormat(s1Format) + '?id=' + tid + '&stage=1';
                if (link2) link2.href = '${pageContext.request.contextPath}/common/' + getPageForFormat(s2Format) + '?id=' + tid + '&stage=2';
            }
        }
    } catch (e) {}
})();
</script>