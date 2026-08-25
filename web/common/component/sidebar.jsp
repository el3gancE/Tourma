<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="dao.TournamentDAO"%>
<%@page import="model.Tournament"%>
<%
    String activeStep = request.getParameter("activeStep");
    if (activeStep == null || activeStep.trim().isEmpty()) {
        activeStep = "format";
    }
    
    String tournamentId = request.getParameter("id");
    String tournamentName = "Giải đấu mới";
    if (tournamentId != null && !tournamentId.trim().isEmpty()) {
        TournamentDAO tDao = new TournamentDAO();
        Tournament t = tDao.getTournamentById(tournamentId);
        if (t != null && t.getName() != null) {
            tournamentName = t.getName();
        }
    }
%>

<link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css">

<aside class="tournament-sidebar">
    <div class="sidebar-header">
        <div class="sidebar-title-label">QUẢN LÝ GIẢI ĐẤU</div>
        <div class="sidebar-tournament-name" title="<%= tournamentName %>">
            <i class="fa-solid fa-trophy text-gold" style="margin-right: 0.35rem;"></i><%= tournamentName %>
        </div>
    </div>

    <ul class="sidebar-nav-list">
        <!-- STEP 1: THÔNG TIN GIẢI -->
        <li class="sidebar-nav-item">
            <a href="${pageContext.request.contextPath}/create-tournament.jsp?id=<%= tournamentId != null ? tournamentId : "" %>" 
               class="sidebar-nav-link <%= "info".equals(activeStep) || "step1".equals(activeStep) ? "active" : "completed" %>">
                <span class="sidebar-step-box">1</span>
                <span class="sidebar-nav-text">Thông tin giải</span>
                <i class="fa-solid fa-chevron-right" style="font-size: 0.7rem; opacity: 0.5;"></i>
            </a>
        </li>

        <!-- STEP 2: THỂ THỨC -->
        <li class="sidebar-nav-item">
            <a href="${pageContext.request.contextPath}/common/configure-tournament-format.jsp?id=<%= tournamentId != null ? tournamentId : "" %>" 
               class="sidebar-nav-link <%= "format".equals(activeStep) || "step2".equals(activeStep) ? "active" : (("teams".equals(activeStep) || "bracket".equals(activeStep) || "step3".equals(activeStep) || "step4".equals(activeStep)) ? "completed" : "") %>">
                <span class="sidebar-step-box">2</span>
                <span class="sidebar-nav-text">Thể thức</span>
                <i class="fa-solid fa-chevron-right" style="font-size: 0.7rem; opacity: 0.5;"></i>
            </a>
        </li>

        <!-- STEP 3: QUẢN LÝ ĐỘI -->
        <li class="sidebar-nav-item">
            <a href="${pageContext.request.contextPath}/common/configure-tournament-teams.jsp?id=<%= tournamentId != null ? tournamentId : "" %>" 
               class="sidebar-nav-link <%= "teams".equals(activeStep) || "step3".equals(activeStep) ? "active" : ("bracket".equals(activeStep) || "step4".equals(activeStep) ? "completed" : "") %>">
                <span class="sidebar-step-box">3</span>
                <span class="sidebar-nav-text">Quản lý đội</span>
                <i class="fa-solid fa-chevron-right" style="font-size: 0.7rem; opacity: 0.5;"></i>
            </a>
        </li>

        <!-- STEP 4: VÒNG ĐẤU -->
        <li class="sidebar-nav-item">
            <a href="${pageContext.request.contextPath}/common/tournament-bracket.jsp?id=<%= tournamentId != null ? tournamentId : "" %>" 
               class="sidebar-nav-link <%= "bracket".equals(activeStep) || "step4".equals(activeStep) ? "active" : "" %>">
                <span class="sidebar-step-box">4</span>
                <span class="sidebar-nav-text">Vòng đấu</span>
                <i class="fa-solid fa-chevron-right" style="font-size: 0.7rem; opacity: 0.5;"></i>
            </a>
        </li>
    </ul>
</aside>
