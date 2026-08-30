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
    if (format == null || format.trim().isEmpty()) {
        format = "SINGLE_ELIMINATION";
        if (!tournamentId.trim().isEmpty()) {
            try {
                TournamentDAO tDao = new TournamentDAO();
                Tournament t = tDao.getTournamentById(tournamentId);
                if (t != null && t.getFormat() != null) {
                    format = t.getFormat();
                }
            } catch (Exception e) {
                // Keep default
            }
        }
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

    String tournamentName = "Giải Đấu Tourma";
    if (!tournamentId.trim().isEmpty()) {
        try {
            TournamentDAO tDao = new TournamentDAO();
            Tournament t = tDao.getTournamentById(tournamentId);
            if (t != null && t.getName() != null) {
                tournamentName = t.getName();
            }
        } catch (Exception e) {
            // Keep default name
        }
    }
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

        <!-- BƯỚC 3: VÒNG ĐẤU / LỊCH THI ĐẤU / VÒNG BẢNG / VÒNG SWISS -->
        <li class="sidebar-menu-item">
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