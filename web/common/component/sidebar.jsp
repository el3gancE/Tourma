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

        <!-- BƯỚC 3: VÒNG ĐẤU (Dynamic URL based on Format) -->
        <li class="sidebar-menu-item">
            <a href="${pageContext.request.contextPath}/common/<%= targetBracketUrl %>?id=<%= tournamentId %>&format=<%= format %>"
               class="sidebar-menu-link <%= "bracket".equals(activeStep) || "step3".equals(activeStep) ? "active" : "" %>">
                <i class="fa-solid fa-bolt menu-icon"></i>
                <span>Vòng Đấu</span>
            </a>
        </li>
    </ul>
</aside>