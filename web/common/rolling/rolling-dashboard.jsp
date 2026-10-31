<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, model.SeriesStanding, model.PartnerParticipant, java.util.List"%>
<%
    Series series = (Series) request.getAttribute("series");
    List<Tournament> tournamentsList = (List<Tournament>) request.getAttribute("tournamentsList");
    List<SeriesStanding> standingsList = (List<SeriesStanding>) request.getAttribute("standingsList");
    List<PartnerParticipant> partnerList = (List<PartnerParticipant>) request.getAttribute("partnerList");
    int partnerCount = (partnerList != null) ? partnerList.size() : 0;

    String seriesIdVal = (series != null && series.getId() != null) ? series.getId() : "";
    String seriesName = (series != null) ? series.getName() : "Series Circuit";
    int phaseSize = (series != null && series.getPhaseSize() > 0) ? series.getPhaseSize() : 10;
    String status = (series != null && series.getStatus() != null) ? series.getStatus() : "ACTIVE";

    int tourneyCount = (tournamentsList != null) ? tournamentsList.size() : 0;
    int totalRankedTeams = (standingsList != null) ? standingsList.size() : 0;

    String top1Team = (totalRankedTeams > 0 && standingsList.get(0).getNormalizedTeamName() != null) ? standingsList.get(0).getNormalizedTeamName() : "Đang cập nhật";
    int top1Pts = (totalRankedTeams > 0) ? standingsList.get(0).getTotalRollingPoints() : 0;

    String top2Team = (totalRankedTeams > 1 && standingsList.get(1).getNormalizedTeamName() != null) ? standingsList.get(1).getNormalizedTeamName() : "Đang cập nhật";
    int top2Pts = (totalRankedTeams > 1) ? standingsList.get(1).getTotalRollingPoints() : 0;

    String top3Team = (totalRankedTeams > 2 && standingsList.get(2).getNormalizedTeamName() != null) ? standingsList.get(2).getNormalizedTeamName() : "Đang cập nhật";
    int top3Pts = (totalRankedTeams > 2) ? standingsList.get(2).getTotalRollingPoints() : 0;
%>
<%!
    private String dashboardText(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title><%= seriesName %> - Dashboard</title>

        <!-- Google Fonts & FontAwesome -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

        <!-- Design System CSS, Team Badges CSS & Dedicated Rolling Dashboard CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/team-badges.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-dashboard.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/add-team-popup.css">
            <script src="${pageContext.request.contextPath}/js/theme.js?v=20261101-settings"></script>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=20261101-settings">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=18">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-workspace.css?v=18">
    </head>
    <body class="ui-page tournament-workspace series-dashboard-page">
        <!-- Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-series"/><jsp:param name="themeToggle" value="true"/>
        </jsp:include>

        <!-- Dynamic 3-Mode Sidebar Component -->
        <div class="tournament-shell"><details class="tournament-navigation" open><summary>Điều hướng chuỗi giải</summary><jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
            <jsp:param name="activeStep" value="dashboard"/>
        </jsp:include></details>

        <main class="rolling-dashboard-wrapper" aria-label="Dashboard chuỗi giải">
<%
    String seriesQuery = java.net.URLEncoder.encode(seriesIdVal, "UTF-8");
    boolean eloModel = series != null && "FIFA_ELO".equalsIgnoreCase(series.getRankingModel());
    int completedCount = 0;
    int ongoingCount = 0;
    java.util.Set<String> uniqueChampions = new java.util.HashSet<>();
    java.util.List<Tournament> recentTournaments = new java.util.ArrayList<>();
    if (tournamentsList != null) {
        for (Tournament t : tournamentsList) {
            if (t == null) continue;
            recentTournaments.add(t);
            if (t.getChampionName() != null && !t.getChampionName().trim().isEmpty()) uniqueChampions.add(t.getChampionName().trim().toLowerCase(java.util.Locale.ROOT));
            if ("COMPLETED".equalsIgnoreCase(t.getStatus()) || "FINISHED".equalsIgnoreCase(t.getStatus())) completedCount++;
            else if ("ONGOING".equalsIgnoreCase(t.getStatus()) || "IN_PROGRESS".equalsIgnoreCase(t.getStatus())) ongoingCount++;
        }
    }
    recentTournaments.sort((a,b) -> Integer.compare(b.getTournamentIndexInSeries(), a.getTournamentIndexInSeries()));
%>
    <header class="series-dashboard-heading">
        <div>
            <a class="dashboard-back" href="${pageContext.request.contextPath}/my-series"><i class="fa-solid fa-arrow-left" aria-hidden="true"></i> Chuỗi giải của tôi</a>
            <h1><%= dashboardText(seriesName) %></h1>
            <div class="dashboard-meta"><span><%= eloModel ? "FIFA Elo Rating" : "Rolling Window" %></span><span>Phase <%= series != null ? series.getCurrentPhase() : 1 %></span><span><%= "ACTIVE".equalsIgnoreCase(status) ? "Đang hoạt động" : dashboardText(status) %></span></div>
        </div>
        <a class="dashboard-button primary" href="${pageContext.request.contextPath}/rolling/create-tournament?seriesId=<%= seriesQuery %>"><i class="fa-solid fa-plus" aria-hidden="true"></i> Tạo giải con</a>
    </header>

    <section class="dashboard-metrics" aria-label="Tổng quan">
        <a class="dashboard-metric" href="${pageContext.request.contextPath}/rolling/team-list?id=<%= seriesQuery %>"><span>Đội đối tác <i class="fa-solid fa-users" aria-hidden="true"></i></span><strong><%= partnerCount %></strong><small>Xem danh sách đội <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></small></a>
        <a class="dashboard-metric" href="${pageContext.request.contextPath}/rolling/tournament-list?seriesId=<%= seriesQuery %>"><span>Giải đấu <i class="fa-solid fa-trophy" aria-hidden="true"></i></span><strong><%= tourneyCount %></strong><small><%= completedCount %> hoàn thành · <%= ongoingCount %> đang diễn ra</small></a>
        <a class="dashboard-metric" href="${pageContext.request.contextPath}/rolling/standings?seriesId=<%= seriesQuery %>"><span>Đội trên BXH <i class="fa-solid fa-ranking-star" aria-hidden="true"></i></span><strong><%= totalRankedTeams %></strong><small><%= eloModel ? "FIFA Elo Rating" : "Window: " + phaseSize + " giải" %></small></a>
        <a class="dashboard-metric" href="${pageContext.request.contextPath}/rolling-hof?id=<%= seriesQuery %>"><span>Nhà vô địch <i class="fa-solid fa-crown" aria-hidden="true"></i></span><strong><%= uniqueChampions.size() %></strong><small>Xem bảng vàng <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></small></a>
    </section>

    <div class="dashboard-columns">
        <section class="dashboard-panel" aria-labelledby="recentTournamentsHeading">
            <div class="dashboard-panel-heading"><h2 id="recentTournamentsHeading">Giải gần đây</h2><a href="${pageContext.request.contextPath}/rolling/tournament-list?seriesId=<%= seriesQuery %>">Tất cả <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></a></div>
            <div class="dashboard-tournaments">
            <% if (recentTournaments.isEmpty()) { %>
                <div class="dashboard-empty"><i class="fa-solid fa-trophy" aria-hidden="true"></i><p>Chưa có giải đấu</p><a href="${pageContext.request.contextPath}/rolling/create-tournament?seriesId=<%= seriesQuery %>">Tạo giải con đầu tiên</a></div>
            <% } else { for (int i=0; i<Math.min(5,recentTournaments.size()); i++) { Tournament t=recentTournaments.get(i); String ts=t.getStatus(); boolean done="COMPLETED".equalsIgnoreCase(ts)||"FINISHED".equalsIgnoreCase(ts); boolean live="ONGOING".equalsIgnoreCase(ts)||"IN_PROGRESS".equalsIgnoreCase(ts); %>
                <a class="dashboard-tournament" href="${pageContext.request.contextPath}/my-tournaments?action=matches&amp;id=<%= java.net.URLEncoder.encode(t.getId(), "UTF-8") %>">
                    <span class="dashboard-event-index">#<%= t.getTournamentIndexInSeries() %></span>
                    <div class="dashboard-event-info"><h3><%= dashboardText(t.getName()) %></h3><p><span class="series-tier" data-tier="<%= dashboardText(t.getTierName()) %>"><%= dashboardText(t.getTierName()) %></span><% if(t.getChampionName()!=null&&!t.getChampionName().trim().isEmpty()){ %><span>·</span><i class="fa-solid fa-trophy" aria-hidden="true"></i> <%= dashboardText(t.getChampionName()) %><% } %></p></div>
                    <span class="dashboard-state <%= live ? "is-live" : "" %>"><%= done ? "Hoàn thành" : live ? "Đang diễn ra" : "Chưa bắt đầu" %></span><i class="fa-solid fa-chevron-right dashboard-chevron" aria-hidden="true"></i>
                </a>
            <% }} %>
            </div>
        </section>

        <section class="dashboard-panel" aria-labelledby="seriesLeadersHeading">
            <div class="dashboard-panel-heading"><h2 id="seriesLeadersHeading">Dẫn đầu BXH</h2><a href="${pageContext.request.contextPath}/rolling/standings?seriesId=<%= seriesQuery %>">Xem BXH <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></a></div>
            <% if(standingsList==null||standingsList.isEmpty()){ %><div class="dashboard-empty"><i class="fa-solid fa-ranking-star" aria-hidden="true"></i><p>Chưa có dữ liệu xếp hạng</p></div><% } else { %>
            <ol class="dashboard-leaders">
                <% for(int i=0;i<Math.min(5,standingsList.size());i++){SeriesStanding row=standingsList.get(i); %>
                <li><span class="dashboard-rank"><%= i+1 %></span><span class="dashboard-leader-name"><%= dashboardText(row.getNormalizedTeamName()) %></span><strong><%= eloModel ? String.format(java.util.Locale.ROOT,"%.0f",row.getCurrentElo()) : String.valueOf(row.getTotalRollingPoints()) %><small><%= eloModel ? "Elo" : "điểm" %></small></strong></li>
                <% } %>
            </ol><% } %>
        </section>
    </div>
</main></div>

        <!-- Rolling Dashboard Interactive Script -->
    </body>
</html>
