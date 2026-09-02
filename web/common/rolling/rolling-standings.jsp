<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, model.SeriesStanding, service.RollingWindowPointService, service.RollingWindowPointService.RollingStandingDTO, dao.SeriesDAO, dao.TournamentDAO, java.util.List"%>
<%
    String seriesIdVal = request.getParameter("id");
    if (seriesIdVal == null || seriesIdVal.trim().isEmpty()) {
        seriesIdVal = request.getParameter("seriesId");
    }

    SeriesDAO seriesDAO = new SeriesDAO();
    TournamentDAO tournamentDAO = new TournamentDAO();

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

    if (series != null && (seriesIdVal == null || seriesIdVal.trim().isEmpty())) {
        seriesIdVal = series.getId();
    }

    List<Tournament> tournamentsList = (List<Tournament>) request.getAttribute("tournamentsList");
    if (tournamentsList == null && series != null) {
        tournamentsList = tournamentDAO.getTournamentsBySeriesId(series.getId());
    }

    List<SeriesStanding> standingsList = (List<SeriesStanding>) request.getAttribute("standingsList");
    List<RollingStandingDTO> standingsDTOList = (List<RollingStandingDTO>) request.getAttribute("standingsDTOList");

    if (standingsDTOList == null && series != null) {
        RollingWindowPointService serviceEngine = RollingWindowPointService.getInstance();
        serviceEngine.recalculateAndPersistStandings(series.getId());
        standingsDTOList = serviceEngine.calculateSeriesStandingsWithExpiry(series.getId());
        standingsList = seriesDAO.getStandingsBySeriesId(series.getId());
    }

    String seriesName = (series != null) ? series.getName() : "Series Circuit";
    int phaseSize = (series != null && series.getPhaseSize() > 0) ? series.getPhaseSize() : 3;
    int tourneyCount = (tournamentsList != null) ? tournamentsList.size() : 0;
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Bảng Xếp Hạng Series - <%= seriesName %></title>
        
        <!-- Google Fonts & FontAwesome -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Design System CSS, Shared Team List CSS & Dedicated Rolling Standings CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-team-list.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-standings.css">
    </head>
    <body>
        <!-- Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-series"/>
        </jsp:include>

        <!-- Dynamic 3-Mode Sidebar Component -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
            <jsp:param name="activeStep" value="standings"/>
        </jsp:include>

        <main class="container has-sidebar" style="max-width: 900px; padding: 1.5rem 1rem;">
            
            <!-- Breadcrumb Navigation -->
            <div style="margin-bottom: 1rem;">
                <a href="${pageContext.request.contextPath}/rolling/dashboard?id=<%= seriesIdVal %>" class="text-muted" style="font-size: 0.85rem; font-weight: 600; text-decoration: none;">
                    <i class="fa-solid fa-arrow-left"></i> Quay lại Dashboard Series
                </a>
            </div>

            <!-- Series Header Banner Card -->
            <div class="team-list-header-card" data-phase-size="<%= phaseSize %>">
                <div>
                    <h1 class="rolling-series-name" style="font-size: 1.85rem; font-weight: 800; color: #ffffff; margin: 0 0 0.25rem 0;">
                        <%= seriesName %> - Bảng Xếp Hạng
                    </h1>
                    <p class="text-muted" style="font-size: 0.85rem; margin-top: 0.25rem;">
                        Tích lũy điểm trượt trong <strong><%= phaseSize %> giải đấu gần nhất (W = <%= phaseSize %>)</strong>. Tự động khấu trừ điểm hết hạn khi vượt cửa sổ trượt.
                    </p>
                </div>
            </div>

            <!-- EXPLANATORY NOTICE BOX FOR ROLLING EXPIRY -->
            <div style="background: rgba(14, 165, 233, 0.08); border: 1px solid rgba(14, 165, 233, 0.3); border-radius: 12px; padding: 0.85rem 1.15rem; margin-bottom: 1.25rem; font-size: 0.8rem; color: #e0f2fe;">
                <div style="font-weight: 800; color: #38bdf8; margin-bottom: 0.3rem; display: flex; align-items: center; gap: 0.4rem;">
                    <i class="fa-solid fa-calculator"></i> Cơ Chế Trừ Điểm Trượt (Rolling Window Expiry W = <%= phaseSize %>):
                </div>
                • <strong>Cửa sổ tích điểm:</strong> Chỉ <strong><%= phaseSize %> giải con gần nhất</strong> được cộng điểm vào tổng tích lũy.<br>
                • <strong>Biến động:</strong> Hiển thị điểm đạt ở giải gần nhất và điểm hết hạn từ giải cũ ngoài cửa sổ trượt (trừ điểm trượt).
            </div>

            <!-- Full Standings Table Card -->
            <div class="team-list-card">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem;">
                    <h3 style="font-size: 1.1rem; font-weight: 800; color: #ffffff; margin: 0;">
                        Bảng Xếp Hạng Chi Tiết (Cửa Sổ Trượt W = <%= phaseSize %> Giải)
                    </h3>
                    <input type="text" class="form-control" placeholder="Tìm kiếm tên đội..." onkeyup="filterRollingStandings(this.value)" style="max-width: 250px; font-size: 0.82rem; border-radius: 8px;">
                </div>

                <table class="team-list-table" id="rollingStandingsTable">
                    <thead>
                        <tr>
                            <th style="width: 70px;">Hạng</th>
                            <th>Tên Đội</th>
                            <th style="text-align: center;">Số giải</th>
                            <th style="text-align: right;">Điểm</th>
                            <th style="text-align: right;">Điểm giải gần nhất</th>
                            <th style="text-align: right;">Biến động</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% if (standingsDTOList != null && !standingsDTOList.isEmpty()) { 
                            for (int i = 0; i < standingsDTOList.size(); i++) {
                                RollingStandingDTO dto = standingsDTOList.get(i);
                                int rank = dto.getRank();
                                int totalPts = dto.getTotalActivePoints();
                                int lastPts = dto.getLastTourneyPoints();
                                int expiredPts = dto.getExpiredPoints();
                                int activeTourneys = dto.getActiveTourneysCount();

                                String lastPtsText = (lastPts > 0) ? ("+" + lastPts + " pts") : "0 pts";
                                String lastPtsColor = (lastPts > 0) ? "#2dd4bf" : "#94a3b8";

                                String changeText = "0 pts";
                                String changeColor = "#94a3b8";
                                if (expiredPts > 0) {
                                    changeText = (lastPts > 0 ? ("+" + lastPts) : "0") + " pts <small style='font-size:0.75rem;'>(-" + expiredPts + " trượt)</small>";
                                    changeColor = "#ef4444";
                                } else if (lastPts > 0) {
                                    changeText = "+" + lastPts + " pts";
                                    changeColor = "#2dd4bf";
                                }
                        %>
                            <tr>
                                <td style="font-weight: 800; color: #ffffff;">
                                    <span class="rank-badge rank-<%= rank %>">#<%= rank %></span>
                                </td>
                                <td style="font-weight: 700; color: #ffffff;">
                                    <%= dto.getTeamName() %>
                                </td>
                                <td style="text-align: center; font-weight: 700; color: var(--rolling-text-muted);">
                                    <%= activeTourneys %>
                                </td>
                                <td style="text-align: right; font-weight: 800; font-size: 1.05rem; color: #fbbf24;">
                                    <%= totalPts %> pts
                                </td>
                                <td style="text-align: right; font-weight: 700; color: <%= lastPtsColor %>;">
                                    <%= lastPtsText %>
                                </td>
                                <td style="text-align: right; font-weight: 700; color: <%= changeColor %>;">
                                    <%= changeText %>
                                </td>
                            </tr>
                        <% } 
                        } else if (standingsList != null && !standingsList.isEmpty()) { 
                            for (int i = 0; i < standingsList.size(); i++) {
                                SeriesStanding st = standingsList.get(i);
                                int rank = i + 1;
                        %>
                            <tr>
                                <td style="font-weight: 800; color: #ffffff;">
                                    <span class="rank-badge rank-<%= rank %>">#<%= rank %></span>
                                </td>
                                <td style="font-weight: 700; color: #ffffff;">
                                    <%= st.getNormalizedTeamName() %>
                                </td>
                                <td style="text-align: center; font-weight: 700; color: var(--rolling-text-muted);">
                                    <%= tourneyCount %>
                                </td>
                                <td style="text-align: right; font-weight: 800; font-size: 1.05rem; color: #fbbf24;">
                                    <%= st.getTotalRollingPoints() %> pts
                                </td>
                                <td style="text-align: right; font-weight: 700; color: #94a3b8;">
                                    0 pts
                                </td>
                                <td style="text-align: right; font-weight: 700; color: #94a3b8;">
                                    0 pts
                                </td>
                            </tr>
                        <% } 
                        } else { %>
                            <tr>
                                <td colspan="6" style="text-align: center; padding: 2.5rem; color: var(--rolling-text-muted);">
                                    <i class="fa-solid fa-folder-open" style="font-size: 2rem; margin-bottom: 0.5rem; display: block; opacity: 0.5;"></i>
                                    Chưa có dữ liệu điểm tích lũy cho Series này. Hãy tạo giải đấu con và hoàn tất thi đấu để tích điểm.
                                </td>
                            </tr>
                        <% } %>
                    </tbody>
                </table>
            </div>

        <script>
            window.seriesSubTournaments = [
                <% if (tournamentsList != null) {
                    for (int i = 0; i < tournamentsList.size(); i++) {
                        Tournament t = tournamentsList.get(i);
                        String cfgJson = (t.getSeriesPointsConfig() != null && !t.getSeriesPointsConfig().trim().isEmpty()) 
                            ? t.getSeriesPointsConfig() : "{\"1\":500,\"2\":200,\"3-4\":100,\"5-8\":0}";
                %>
                    {
                        id: "<%= t.getId() %>",
                        name: "<%= t.getName() != null ? t.getName().replace("\"", "\\\"") : "" %>",
                        index: <%= t.getTournamentIndexInSeries() > 0 ? t.getTournamentIndexInSeries() : (i + 1) %>,
                        pointsConfig: <%= cfgJson %>
                    }<%= (i < tournamentsList.size() - 1) ? "," : "" %>
                <%  }
                } %>
            ];
            window.seriesPhaseSize = <%= phaseSize %>;
        </script>
        <script src="${pageContext.request.contextPath}/js/rolling/rolling-standings.js"></script>
    </body>
</html>
