<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, dao.SeriesDAO, dao.TournamentDAO, dao.ParticipantDAO, model.Team, java.util.List, java.util.Map, java.util.HashMap"%>
<%
    String seriesIdVal = request.getParameter("id");
    if (seriesIdVal == null || seriesIdVal.trim().isEmpty()) {
        seriesIdVal = request.getParameter("seriesId");
    }

    SeriesDAO seriesDAO = new SeriesDAO();
    TournamentDAO tournamentDAO = new TournamentDAO();
    ParticipantDAO participantDAO = new ParticipantDAO();

    String actionParam = request.getParameter("action");
    String delTourneyId = request.getParameter("tournamentId");
    if ("POST".equalsIgnoreCase(request.getMethod()) && "delete".equalsIgnoreCase(actionParam) && delTourneyId != null) {
        tournamentDAO.deleteTournament(delTourneyId.trim());
        if (seriesIdVal != null && !seriesIdVal.trim().isEmpty()) {
            service.RollingWindowPointService.getInstance().recalculateAndPersistStandings(seriesIdVal.trim());
        }
    }

    Series series = (Series) request.getAttribute("series");
    if (series == null && seriesIdVal != null && !seriesIdVal.trim().isEmpty()) {
        series = seriesDAO.getSeriesById(seriesIdVal.trim());
    }

    if (series == null) {
        List<Series> allSeries = seriesDAO.getAllSeries();
        for (Series s : allSeries) {
            if ("ROLLING_WINDOW".equalsIgnoreCase(s.getRankingModel())) {
                series = s;
                break;
            }
        }
    }

    List<Tournament> tournamentsList = (List<Tournament>) request.getAttribute("tournamentsList");
    if (tournamentsList == null && series != null) {
        tournamentsList = tournamentDAO.getTournamentsBySeriesId(series.getId());
    }

    Map<String, Integer> teamCountMap = (Map<String, Integer>) request.getAttribute("teamCountMap");
    if (teamCountMap == null) {
        teamCountMap = new HashMap<>();
        if (tournamentsList != null) {
            for (Tournament t : tournamentsList) {
                List<Team> teams = participantDAO.getTeamsByTournamentId(t.getId());
                int count = (teams != null) ? teams.size() : 0;
                teamCountMap.put(t.getId(), count);
            }
        }
    }

    if (seriesIdVal == null && series != null) {
        seriesIdVal = series.getId();
    }
    String seriesName = (series != null) ? series.getName() : "Series Circuit";
    int tourneyCount = (tournamentsList != null) ? tournamentsList.size() : 0;
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Danh Sách Giải Con - <%= seriesName %></title>
        
        <!-- Google Fonts & FontAwesome -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Design System CSS & Dedicated Rolling Team List CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-team-list.css">
    </head>
    <body>
        <!-- Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-series"/>
        </jsp:include>

        <!-- Dynamic 3-Mode Sidebar Component -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
            <jsp:param name="activeStep" value="tournament-list"/>
        </jsp:include>

        <main class="container has-sidebar" style="max-width: 950px; padding: 1.5rem 1rem;">
            
            <!-- Breadcrumb Navigation -->
            <div style="margin-bottom: 1rem;">
                <a href="${pageContext.request.contextPath}/rolling/dashboard?id=<%= seriesIdVal %>" class="text-muted" style="font-size: 0.85rem; font-weight: 600; text-decoration: none;">
                    <i class="fa-solid fa-arrow-left"></i> Quay lại Dashboard Series
                </a>
            </div>

            <!-- Series Header Banner Card -->
            <div class="team-list-header-card">
                <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
                    <div>
                        <h1 class="rolling-series-name" style="font-size: 1.85rem; font-weight: 800; color: #ffffff; margin: 0 0 0.25rem 0;">
                            <i class="fa-solid fa-trophy text-gold"></i> Danh Sách Giải Con
                        </h1>
                        <p class="text-muted" style="font-size: 0.85rem; margin-top: 0.25rem;">
                            Quản lý tất cả các giải đấu con thuộc Series <strong><%= seriesName %></strong> (<%= tourneyCount %> Giải Con)
                        </p>
                    </div>
                    <div>
                        <a href="${pageContext.request.contextPath}/rolling/create-tournament?seriesId=<%= seriesIdVal %>" class="btn btn-mint" style="font-weight: 800; font-size: 0.9rem; padding: 0.65rem 1.25rem; border-radius: 10px; text-decoration: none;">
                            <i class="fa-solid fa-plus-circle"></i> + Thêm Giải Con Mới
                        </a>
                    </div>
                </div>
            </div>

            <!-- Table / Cards Container -->
            <div class="team-list-card">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem;">
                    <h3 style="font-size: 1.1rem; font-weight: 800; color: #ffffff; margin: 0;">
                        Danh Sách Chặng Đấu (Stop #1 đến Stop #<%= tourneyCount %>)
                    </h3>
                    <input type="text" class="form-control" placeholder="Tìm kiếm tên giải con..." onkeyup="filterTournaments(this.value)" style="max-width: 250px; font-size: 0.82rem; border-radius: 8px;">
                </div>

                <div id="tourneyCardList" style="display: flex; flex-direction: column; gap: 0.85rem;">
                    <% if (tournamentsList != null && !tournamentsList.isEmpty()) { 
                        for (int i = 0; i < tournamentsList.size(); i++) {
                            Tournament t = tournamentsList.get(i);
                            int stopIndex = t.getTournamentIndexInSeries() > 0 ? t.getTournamentIndexInSeries() : (i + 1);
                            int nTeams = teamCountMap.containsKey(t.getId()) ? teamCountMap.get(t.getId()) : 0;
                            String tierName = (t.getTierName() != null) ? t.getTierName().toUpperCase() : "S";
                            String fmt = (t.getFormat() != null) ? t.getFormat().toUpperCase() : "SINGLE_ELIMINATION";

                            String bracketUrl = "/common/single-elimination.jsp";
                            if ("DOUBLE_ELIMINATION".equals(fmt)) bracketUrl = "/common/double-elimination.jsp";
                            else if ("ROUND_ROBIN".equals(fmt)) bracketUrl = "/common/round-robin.jsp";
                            else if ("GROUP_STAGE".equals(fmt)) bracketUrl = "/common/group-stage.jsp";
                            else if ("SWISS_LITE".equals(fmt) || "SWISS".equals(fmt)) bracketUrl = "/common/swiss-stage.jsp";

                            String championName = t.getChampionName();
                            boolean isFinished = (championName != null && !championName.trim().isEmpty());
                            int rewardPts = (t.getSeriesRewardPoints() != null) ? t.getSeriesRewardPoints() : 0;
                    %>
                        <div class="subtourney-card-item" style="background: rgba(24, 29, 41, 0.9); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 12px; padding: 1rem 1.25rem; display: flex; align-items: center; justify-content: space-between; gap: 1rem;">
                            <div style="display: flex; align-items: center; gap: 1.25rem;">
                                <!-- Tournament Info -->
                                <div>
                                    <div style="font-weight: 800; font-size: 1.05rem; color: #ffffff;">
                                        <a href="${pageContext.request.contextPath}<%= bracketUrl %>?id=<%= t.getId() %>&seriesId=<%= seriesIdVal %>" style="color: #ffffff; text-decoration: none;">
                                            <%= t.getName() %>
                                        </a>
                                    </div>
                                    <div style="display: flex; align-items: center; gap: 0.5rem; margin-top: 0.35rem; font-size: 0.78rem; color: #94a3b8; flex-wrap: wrap;">
                                        <span style="background: rgba(251, 191, 36, 0.1); border: 1px solid rgba(251, 191, 36, 0.3); color: #fbbf24; padding: 0.15rem 0.5rem; border-radius: 6px; font-weight: 700;">
                                            Tier <%= tierName %>
                                        </span>
                                        <span style="background: rgba(255, 255, 255, 0.06); padding: 0.15rem 0.5rem; border-radius: 6px; font-weight: 600;">
                                            <i class="fa-solid fa-diagram-project"></i> <%= fmt %>
                                        </span>
                                        <span style="background: rgba(255, 255, 255, 0.06); padding: 0.15rem 0.5rem; border-radius: 6px; font-weight: 600;">
                                            <i class="fa-solid fa-users"></i> <%= nTeams %> Đội
                                        </span>
                                        <% if (rewardPts > 0) { %>
                                            <span style="background: rgba(251, 191, 36, 0.08); border: 1px solid rgba(251, 191, 36, 0.25); color: #fbbf24; padding: 0.15rem 0.5rem; border-radius: 6px; font-weight: 700;">
                                                <i class="fa-solid fa-star"></i> <%= rewardPts %> pts
                                            </span>
                                        <% } %>
                                        <% if (isFinished) { %>
                                            <span style="background: rgba(52, 211, 153, 0.1); border: 1px solid rgba(52, 211, 153, 0.3); color: #34d399; padding: 0.15rem 0.5rem; border-radius: 6px; font-weight: 700;">
                                                <i class="fa-solid fa-trophy"></i> Vô Địch: <%= championName %>
                                            </span>
                                        <% } %>
                                    </div>
                                </div>
                            </div>

                            <!-- Action Buttons -->
                            <div style="display: flex; align-items: center; gap: 0.5rem; flex-shrink: 0;">
                                <a href="${pageContext.request.contextPath}/rolling/tournament-teams?id=<%= t.getId() %>&seriesId=<%= seriesIdVal %>" class="btn btn-secondary" style="font-size: 0.78rem; font-weight: 700; padding: 0.45rem 0.75rem; border-radius: 8px; text-decoration: none;" title="Thêm Đội & Quản Lý">
                                    <i class="fa-solid fa-users-gear"></i> QL Đội
                                </a>
                                <a href="${pageContext.request.contextPath}/rolling/point-config?id=<%= t.getId() %>&seriesId=<%= seriesIdVal %>" class="btn btn-secondary" style="font-size: 0.78rem; font-weight: 700; padding: 0.45rem 0.75rem; border-radius: 8px; text-decoration: none;" title="Thiết Lập Điểm Thưởng">
                                    <i class="fa-solid fa-star text-gold"></i> Set Điểm
                                </a>
                                <a href="${pageContext.request.contextPath}<%= bracketUrl %>?id=<%= t.getId() %>&seriesId=<%= seriesIdVal %>" class="btn btn-mint" style="font-size: 0.78rem; font-weight: 800; padding: 0.45rem 0.85rem; border-radius: 8px; text-decoration: none;">
                                    <i class="fa-solid fa-play"></i> Sơ Đồ Cây ➔
                                </a>
                                <form action="${pageContext.request.contextPath}/rolling/tournament-list" method="POST" style="margin: 0; display: inline;" onsubmit="return confirm('Bạn có chắc chắn muốn xóa giải con này?');">
                                    <input type="hidden" name="action" value="delete">
                                    <input type="hidden" name="tournamentId" value="<%= t.getId() %>">
                                    <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">
                                    <button type="submit" class="btn btn-secondary" style="font-size: 0.78rem; font-weight: 700; padding: 0.45rem 0.75rem; border-radius: 8px; color: #ef4444; border-color: rgba(239, 68, 68, 0.3);" title="Xóa giải con">
                                        <i class="fa-solid fa-trash-can"></i> Xóa
                                    </button>
                                </form>
                            </div>
                        </div>
                    <% } 
                    } else { %>
                        <div style="text-align: center; padding: 3rem; background: rgba(18, 22, 31, 0.7); border: 1px dashed rgba(255, 255, 255, 0.15); border-radius: 16px; color: #94a3b8;">
                            <i class="fa-solid fa-trophy" style="font-size: 2.5rem; margin-bottom: 0.75rem; color: #fbbf24; opacity: 0.5;"></i>
                            <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.5rem;">Chưa có giải con nào trong Series này</h4>
                            <p style="font-size: 0.85rem; margin-bottom: 1.25rem;">Hãy bấm nút bên dưới để tạo giải đấu con đầu tiên (Stop #1) cho Series.</p>
                            <a href="${pageContext.request.contextPath}/rolling/create-tournament?seriesId=<%= seriesIdVal %>" class="btn btn-mint" style="font-weight: 800; text-decoration: none;">
                                <i class="fa-solid fa-plus-circle"></i> + Tạo Giải Con Đầu Tiên
                            </a>
                        </div>
                    <% } %>
                </div>
            </div>

        </main>

        <script>
            function filterTournaments(query) {
                var q = (query || '').toLowerCase().trim();
                var cards = document.querySelectorAll('.subtourney-card-item');
                cards.forEach(function (card) {
                    var txt = card.innerText.toLowerCase();
                    if (!q || txt.includes(q)) {
                        card.style.display = 'flex';
                    } else {
                        card.style.display = 'none';
                    }
                });
            }
        </script>
    </body>
</html>
