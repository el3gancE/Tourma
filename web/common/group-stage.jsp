<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="dao.TournamentDAO" %>
<%@ page import="model.Tournament" %>
<%
    String tournamentId = request.getParameter("id");
    if (tournamentId == null || tournamentId.trim().isEmpty()) {
        tournamentId = "demo";
    }

    TournamentDAO tDao = new TournamentDAO();
    Tournament tourney = tDao.getTournamentById(tournamentId);

    String tourneyName = (tourney != null && tourney.getName() != null) ? tourney.getName() : "Giải Đấu Vòng Bảng";
%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><%= tourneyName %> - Vòng Bảng (Group Stage)</title>

    <!-- Global Styling & Icons -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-bracket.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/empty-team-alert.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/match-card.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-viewport.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/round-robin.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/group-stage.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/group-standing.css">
</head>
<body style="background: #0b0d12; color: #f8fafc;">

    <!-- TOP NAVBAR & SIDEBAR -->
    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="tournaments"/>
    </jsp:include>
    <jsp:include page="/common/component/sidebar.jsp">
        <jsp:param name="activeStep" value="bracket" />
        <jsp:param name="id" value="<%= tournamentId %>" />
        <jsp:param name="format" value="GROUP_STAGE" />
    </jsp:include>

    <!-- REUSABLE EMPTY ALERT & POPUP MODAL COMPONENTS -->
    <jsp:include page="/common/component/empty-team-alert.jsp" />
    <jsp:include page="/common/component/popup.jsp" />

    <main class="container has-sidebar round-robin-container">
        
        <!-- TOP CONTROL BAR (Round Robin Design Style) -->
        <div class="rr-control-bar">
            <div class="rr-info-group">
                <h1 class="rr-tourney-title">
                    <i class="fa-solid fa-trophy text-gold"></i>
                    <span id="gsTournamentTitle"><%= tourneyName %></span>
                </h1>
                <span class="format-badge-rr">Group Stage</span>
                <span id="gsTeamCountBadge" class="team-count-badge">0 Đội</span>
            </div>

            <div class="rr-actions-group">
                <button type="button" class="btn-reset-bracket-action" onclick="TourmaGroupStage.resetAllMatches()" title="Xóa toàn bộ kết quả và thiết lập lại từ đầu">
                    <i class="fa-solid fa-rotate-right"></i> Reset Giải
                </button>

                <div class="view-mode-toggle-group">
                    <a href="${pageContext.request.contextPath}/common/group-stage.jsp?id=<%= tournamentId %>&format=GROUP_STAGE" class="btn-view-toggle active" style="text-decoration: none;">
                        <i class="fa-solid fa-calendar-days"></i> Lịch Thi Đấu
                    </a>
                    <a href="${pageContext.request.contextPath}/common/group-standing.jsp?id=<%= tournamentId %>&format=GROUP_STAGE" class="btn-view-toggle" style="text-decoration: none;">
                        <i class="fa-solid fa-ranking-star"></i> Bảng Xếp Hạng
                    </a>
                </div>
            </div>
        </div>

        <!-- CONTAINER FOR EMPTY TEAM ALERT -->
        <div id="gsEmptyAlertContainer" style="display: none; width: 100%;"></div>

        <!-- MAIN WORKSPACE CONTENT -->
        <div id="gsMainContent">
            
            <!-- GROUP SELECTOR FILTER PILLS BAR -->
            <div id="gsGroupSelectorBar" class="rr-round-selector-bar">
                <!-- Dynamic Group Pills (Tất cả các bảng, Bảng A, Bảng B...) -->
            </div>

            <!-- FIXTURES / MATCHES CONTAINER -->
            <div id="gsMatchesView">
                <div id="gsMatchesContainer" class="rr-fixtures-container"></div>
            </div>

        </div>
    </main>

    <!-- JS SCRIPTS -->
    <script src="${pageContext.request.contextPath}/js/random-service.js"></script>
    <script src="${pageContext.request.contextPath}/js/match-card.js"></script>
    <script src="${pageContext.request.contextPath}/js/popup.js"></script>
    <script src="${pageContext.request.contextPath}/js/group-standing.js"></script>
    <script src="${pageContext.request.contextPath}/js/group-stage.js"></script>
</body>
</html>
