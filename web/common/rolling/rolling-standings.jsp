<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, model.SeriesStanding, model.PartnerParticipant, service.RollingWindowPointService, service.RollingWindowPointService.RollingStandingDTO, dao.SeriesDAO, dao.TournamentDAO, java.util.List"%>
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
        tournamentsList = seriesDAO.getTournamentsBySeriesId(series.getId());
    }

    List<PartnerParticipant> partnerList = (series != null) ? seriesDAO.getPartnerParticipantsBySeriesId(series.getId()) : null;

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

            <!-- Series Header Banner Card (Top Bar Màn BXH) -->
            <div class="team-list-header-card" data-phase-size="<%= phaseSize %>" style="margin-bottom: 1.5rem;">
                <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
                    <div>
                        <h1 class="rolling-series-name" style="font-size: 1.85rem; font-weight: 800; color: #ffffff; margin: 0 0 0.25rem 0;">
                            <%= seriesName %> - Bảng Xếp Hạng
                        </h1>
                        <p class="text-muted" style="font-size: 0.85rem; margin-top: 0.25rem; margin-bottom: 0;">
                            Tích lũy điểm trượt trong <strong><%= phaseSize %> giải đấu gần nhất (W = <%= phaseSize %>)</strong>. Tự động khấu trừ điểm hết hạn khi vượt cửa sổ trượt.
                        </p>
                    </div>
                    <div>
                        <a href="${pageContext.request.contextPath}/rolling/create-tournament?seriesId=<%= seriesIdVal %>" class="btn btn-mint" title="Tạo giải đấu con mới cho chuỗi giải này" style="font-weight: 700; font-size: 0.85rem; padding: 0.55rem 1.15rem; border-radius: 9px; text-decoration: none; display: inline-flex; align-items: center; gap: 0.4rem; white-space: nowrap; box-shadow: 0 4px 12px rgba(45, 212, 191, 0.25);">
                            <i class="fa-solid fa-plus-circle"></i> Tạo giải con
                        </a>
                    </div>
                </div>
            </div>

            <!-- Full Standings Table Card -->
            <div class="team-list-card">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem; flex-wrap: wrap; gap: 0.75rem;">
                    <div>
                        <h3 style="font-size: 1.1rem; font-weight: 800; color: #ffffff; margin: 0;" id="standingsTableTitle">
                            Bảng Xếp Hạng Chi Tiết (Cửa Sổ Trượt W = <%= phaseSize %> Giải)
                        </h3>
                    </div>

                    <div style="display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap;">
                        <!-- Dropdown Chọn Mốc Giải Đấu (Mới nhất hiện trước) -->
                        <div style="display: flex; align-items: center; gap: 0.35rem;">
                            <label for="milestoneSelect" style="font-size: 0.78rem; font-weight: 700; color: #94a3b8; white-space: nowrap;">
                                <i class="fa-solid fa-clock-rotate-left"></i> Tính đến:
                            </label>
                            <select id="milestoneSelect" class="form-control" onchange="onMilestoneChange(this.value)" style="font-size: 0.8rem; padding: 0.35rem 0.65rem; border-radius: 8px; background: rgba(15, 18, 26, 0.9); color: #ffffff; border: 1px solid rgba(255,255,255,0.12); cursor: pointer; max-width: 260px;">
                                <option value="LATEST">Giải mới nhất <%= (tournamentsList != null && !tournamentsList.isEmpty()) ? ("(#" + tournamentsList.size() + " - " + tournamentsList.get(tournamentsList.size() - 1).getName() + ")") : "" %></option>
                                <% if (tournamentsList != null && tournamentsList.size() > 1) { 
                                    for (int idx = tournamentsList.size() - 1; idx >= 0; idx--) {
                                        Tournament t = tournamentsList.get(idx);
                                %>
                                    <option value="<%= idx %>">Giải #<%= (idx + 1) %>: <%= t.getName() %></option>
                                <%  }
                                } %>
                            </select>
                        </div>

                        <!-- Nút Cập Nhật Mới Nhất -->
                        <button type="button" class="btn btn-outline-mint" id="btnSyncLatest" onclick="syncLatestStandings()" title="Tính toán và đồng bộ dữ liệu mới nhất từ kết quả thi đấu" style="font-size: 0.78rem; font-weight: 700; padding: 0.35rem 0.85rem; border-radius: 8px; display: inline-flex; align-items: center; gap: 0.4rem; white-space: nowrap;">
                            <i class="fa-solid fa-arrows-rotate" id="syncLatestIcon"></i> Cập nhật mới nhất
                        </button>

                        <!-- Ô Tìm Kiếm Tên Đội -->
                        <div style="position: relative; display: flex; align-items: center;">
                            <input type="text" class="form-control" placeholder="Tìm kiếm tên đội..." onkeyup="filterRollingStandings(this.value)" style="max-width: 170px; font-size: 0.8rem; border-radius: 8px; padding: 0.35rem 0.65rem;">
                        </div>
                    </div>
                </div>

                <table class="team-list-table" id="rollingStandingsTable">
                    <thead>
                        <tr>
                            <th style="width: 85px;">Hạng</th>
                            <th>Tên Đội</th>
                            <th style="text-align: center;">Số giải</th>
                            <th style="text-align: right;">Điểm</th>
                            <th style="text-align: right;" id="thLastPts">Điểm giải gần nhất</th>
                            <th style="text-align: right;">Biến động</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% if (standingsDTOList != null && !standingsDTOList.isEmpty()) { 
                            for (int i = 0; i < standingsDTOList.size(); i++) {
                                RollingStandingDTO dto = standingsDTOList.get(i);
                                int rank = dto.getRank();
                                int rankChange = dto.getRankChange();
                                int totalPts = dto.getTotalActivePoints();
                                int lastPts = dto.getLastTourneyPoints();
                                int expiredPts = dto.getExpiredPoints();
                                int activeTourneys = dto.getActiveTourneysCount();

                                int netChange = lastPts - expiredPts;
                                String lastPtsText = (lastPts > 0) ? ("+" + lastPts + " pts") : "0 pts";
                                String lastPtsColor = (lastPts > 0) ? "#2dd4bf" : "#94a3b8";

                                String changeText = "0 pts";
                                String changeColor = "#94a3b8";
                                if (netChange > 0) {
                                    changeText = "+" + netChange + " pts";
                                    changeColor = "#2dd4bf";
                                } else if (netChange < 0) {
                                    changeText = netChange + " pts";
                                    changeColor = "#ef4444";
                                }
                        %>
                            <tr>
                                <td style="font-weight: 800; color: #ffffff;">
                                    <div class="rank-wrap">
                                        <span class="rank-badge rank-<%= rank %>">#<%= rank %></span>
                                        <% if (tourneyCount >= 2) { %>
                                            <% if (rankChange > 0) { %>
                                                <span class="rank-change up" title="Tăng <%= rankChange %> bậc"><i class="fa-solid fa-arrow-up"></i> <%= rankChange %></span>
                                            <% } else if (rankChange < 0) { %>
                                                <span class="rank-change down" title="Giảm <%= Math.abs(rankChange) %> bậc"><i class="fa-solid fa-arrow-down"></i> <%= Math.abs(rankChange) %></span>
                                            <% } else { %>
                                                <span class="rank-change same" title="Không đổi bậc">-</span>
                                            <% } %>
                                        <% } else { %>
                                            <span class="rank-change same" title="Chưa có biến động">-</span>
                                        <% } %>
                                    </div>
                                </td>
                                <td style="font-weight: 700; color: #ffffff;">
                                    <a href="${pageContext.request.contextPath}/team-profile?seriesId=<%= seriesIdVal %>&teamName=<%= java.net.URLEncoder.encode(dto.getTeamName(), "UTF-8") %>" style="color: #ffffff; text-decoration: none; transition: color 0.18s ease;" onmouseover="this.style.color='#2dd4bf'" onmouseout="this.style.color='#ffffff'">
                                        <%= dto.getTeamName() %>
                                    </a>
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
                                    <div class="rank-wrap">
                                        <span class="rank-badge rank-<%= rank %>">#<%= rank %></span>
                                        <span class="rank-change same" title="Chưa có biến động">-</span>
                                    </div>
                                </td>
                                <td style="font-weight: 700; color: #ffffff;">
                                    <a href="${pageContext.request.contextPath}/team-profile?seriesId=<%= seriesIdVal %>&teamName=<%= java.net.URLEncoder.encode(st.getNormalizedTeamName(), "UTF-8") %>" style="color: #ffffff; text-decoration: none; transition: color 0.18s ease;" onmouseover="this.style.color='#2dd4bf'" onmouseout="this.style.color='#ffffff'">
                                        <%= st.getNormalizedTeamName() %>
                                    </a>
                                </td>
                                <td style="text-align: center; font-weight: 700; color: var(--rolling-text-muted);">
                                    <%= (st.getTotalRollingPoints() > 0) ? 1 : 0 %>
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
                        String rawCfg = (t != null) ? t.getSeriesPointsConfig() : null;
                        String cfgJson = (rawCfg != null && rawCfg.trim().startsWith("{") && rawCfg.trim().endsWith("}")) 
                            ? rawCfg.trim() : "{\"1\":500,\"2\":200,\"3-4\":100,\"5-8\":0}";
                        String safeName = (t != null && t.getName() != null) ? t.getName().replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", " ").replace("\r", "") : "";
                        String safeId = (t != null && t.getId() != null) ? t.getId() : "";
                        int tIdx = (t != null && t.getTournamentIndexInSeries() > 0) ? t.getTournamentIndexInSeries() : (i + 1);
                        String tType = (t != null && t.getTournamentType() != null) ? t.getTournamentType() : "SINGLE_STAGE";
                        boolean isMulti = (t != null && "MULTI_STAGE".equalsIgnoreCase(t.getTournamentType()));
                        String fmt = (t != null && t.getFormat() != null) ? t.getFormat().toUpperCase() : "SINGLE_ELIMINATION";
                        String tTier = (t != null && t.getTierName() != null) ? t.getTierName().toUpperCase() : "A";
                %>
                    {
                        id: "<%= safeId %>",
                        name: "<%= safeName %>",
                        index: <%= tIdx %>,
                        format: "<%= fmt %>",
                        tournamentType: "<%= tType %>",
                        isMultiStage: <%= isMulti %>,
                        tierName: "<%= tTier %>",
                        pointsConfig: <%= cfgJson %>
                    }<%= (i < tournamentsList.size() - 1) ? "," : "" %>
                <%  }
                } %>
            ];
            window.seriesPartners = [
                <% if (partnerList != null) {
                    for (int i = 0; i < partnerList.size(); i++) {
                        PartnerParticipant p = partnerList.get(i);
                        String pName = (p != null && p.getName() != null) ? p.getName().replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", " ").replace("\r", "") : "";
                        String pId = (p != null && p.getId() != null) ? p.getId() : "";
                %>
                    {
                        id: "<%= pId %>",
                        name: "<%= pName %>"
                    }<%= (i < partnerList.size() - 1) ? "," : "" %>
                <%  }
                } %>
            ];
            window.seriesPhaseSize = <%= phaseSize %>;
            window.seriesIdVal = "<%= seriesIdVal %>";
            window.appContextPath = "${pageContext.request.contextPath}";
        </script>
        <!-- Engine Algorithms for Series Standings Calculation -->
        <script src="${pageContext.request.contextPath}/js/round-robin-algorithm.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-algorithm.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/double-elimination-algorithm.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/rolling/rolling-standings.js?v=<%= System.currentTimeMillis() %>"></script>
    </body>
</html>
