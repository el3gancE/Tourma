<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, model.SeriesStanding, model.PartnerParticipant, service.RollingWindowPointService, service.RollingWindowPointService.RollingStandingDTO, dao.SeriesDAO, dao.TournamentDAO, java.util.List, java.util.Map"%>
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
    if (tournamentsList != null && tournamentsList.size() > 1) {
        tournamentsList = new java.util.ArrayList<>(tournamentsList);
        tournamentsList.sort((a, b) -> {
            int idxA = a.getTournamentIndexInSeries();
            int idxB = b.getTournamentIndexInSeries();
            if (idxA != idxB) return Integer.compare(idxA, idxB);
            if (a.getCreatedAt() != null && b.getCreatedAt() != null) return a.getCreatedAt().compareTo(b.getCreatedAt());
            return 0;
        });
    }

    List<PartnerParticipant> partnerList = (List<PartnerParticipant>) request.getAttribute("partnerList");
    if (partnerList == null && series != null) {
        partnerList = seriesDAO.getPartnerParticipantsBySeriesId(series.getId());
    }

    List<SeriesStanding> standingsList = (List<SeriesStanding>) request.getAttribute("standingsList");
    List<RollingStandingDTO> standingsDTOList = (List<RollingStandingDTO>) request.getAttribute("standingsDTOList");

    if (standingsDTOList == null && series != null) {
        RollingWindowPointService serviceEngine = RollingWindowPointService.getInstance();
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
        <link href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

        <!-- Design System CSS, Shared Team List CSS & Dedicated Rolling Standings CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-team-list.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-standings.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/add-team-popup.css?v=<%= System.currentTimeMillis() %>">
    <script src="${pageContext.request.contextPath}/js/theme.js?v=20261031-initial"></script>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=20261031m">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=18">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-workspace.css?v=18">
</head>
    <body class="ui-page tournament-workspace series-standings-page">
        <!-- Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-series"/><jsp:param name="themeToggle" value="true"/>
        </jsp:include>

        <!-- Dynamic 3-Mode Sidebar Component -->
        <div class="tournament-shell"><details class="tournament-navigation" open><summary>Điều hướng chuỗi giải</summary><jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
            <jsp:param name="activeStep" value="standings"/>
        </jsp:include></details>

        <main class="series-standings-main" data-ui-style="rolling-standings-jsp-001">

            <!-- Full Standings Table Card -->
            <div class="team-list-card" data-phase-size="<%= phaseSize %>">
                <div class="series-standings-toolbar" data-ui-style="rolling-standings-jsp-011">
                    <div>
                        <h3 data-ui-style="rolling-standings-jsp-012" id="standingsTableTitle">
                            Bảng xếp hạng
                        </h3>
                        <p class="series-standings-description">Điểm tích lũy trong <strong><%= phaseSize %> giải gần nhất</strong>.</p>
                    </div>

                    <div class="series-standings-filters" data-ui-style="rolling-standings-jsp-013">
                        <!-- Dropdown Chọn Mốc Giải Đấu (Mới nhất hiện trước) -->
                        <div data-ui-style="rolling-standings-jsp-014" style="display: flex;">
                            <label for="milestoneSelect" data-ui-style="rolling-standings-jsp-015">
                                Mốc xếp hạng
                            </label>
                            <select id="milestoneSelect" class="form-control" onchange="onMilestoneChange(this.value)" data-ui-style="rolling-standings-jsp-016">
                                <% if (tournamentsList != null && !tournamentsList.isEmpty()) {
                                    int totalTourneys = tournamentsList.size();
                                    Tournament latestTourney = tournamentsList.get(totalTourneys - 1);
                                %>
                                    <option value="LATEST">Giải mới nhất (#<%= totalTourneys %> - <%= latestTourney.getName() %>)</option>
                                    <% for (int idx = totalTourneys - 2; idx >= 0; idx--) {
                                        Tournament t = tournamentsList.get(idx);
                                    %>
                                        <option value="<%= idx %>">Giải #<%= (idx + 1) %>: <%= t.getName() %></option>
                                    <% }
                                } else { %>
                                    <option value="LATEST">Giải mới nhất</option>
                                <% } %>
                            </select>
                        </div>

                        <!-- Ô Tìm Kiếm Tên Đội -->
                        <div data-ui-style="rolling-standings-jsp-017" style="display: flex;">
                            <input type="text" class="form-control" aria-label="Tìm tên đội" placeholder="Tìm tên đội..." onkeyup="filterRollingStandings(this.value)" data-ui-style="rolling-standings-jsp-018">
                        </div>
                    </div>
                </div>

                <table class="team-list-table" id="rollingStandingsTable">
                    <thead>
                        <tr>
                            <th data-ui-style="rolling-standings-jsp-019">Hạng</th>
                            <th>Tên Đội</th>
                            <th data-ui-style="rolling-standings-jsp-020">Số giải</th>
                            <th data-ui-style="rolling-standings-jsp-021">Điểm</th>
                            <th data-ui-style="rolling-standings-jsp-022" id="thLastPts">Điểm giải gần nhất</th>
                            <th data-ui-style="rolling-standings-jsp-023">Biến động</th>
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
                                String lastPtsColor = (lastPts > 0) ? "var(--ui-accent)" : "var(--ui-muted)";

                                String changeText = "0 pts";
                                String changeColor = "var(--ui-muted)";
                                if (netChange > 0) {
                                    changeText = "+" + netChange + " pts";
                                    changeColor = "var(--ui-accent)";
                                } else if (netChange < 0) {
                                    changeText = netChange + " pts";
                                    changeColor = "var(--ui-danger)";
                                }
                                boolean isTop10 = (rank <= 10);
                                String rankColor = isTop10 ? "var(--ui-accent)" : "var(--ui-text)";
                                String nameColor = isTop10 ? "var(--ui-accent)" : "var(--ui-text)";
                                String nameHover = isTop10 ? "var(--ui-accent-hover)" : "var(--ui-accent)";
                        %>
                            <tr class="<%= isTop10 ? "top-10" : "" %>">
                                <td data-ui-style="rolling-standings-jsp-024" style="color: <%= rankColor %>;">
                                    <div class="rank-wrap">
                                        <span class="rank-badge <%= isTop10 ? "top-10" : "" %> rank-<%= rank %>"><%= rank %></span>
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
                                <td data-ui-style="rolling-standings-jsp-025" style="color: <%= nameColor %>;">
                                    <a href="${pageContext.request.contextPath}/team-profile?seriesId=<%= seriesIdVal %>&teamName=<%= java.net.URLEncoder.encode(dto.getTeamName(), "UTF-8") %>" class="team-link" data-ui-style="rolling-standings-jsp-026" style="color: <%= nameColor %>;" onmouseover="this.style.color='<%= nameHover %>'" onmouseout="this.style.color='<%= nameColor %>'">
                                        <%= dto.getTeamName() %>
                                    </a>
                                </td>
                                <td data-ui-style="rolling-standings-jsp-027">
                                    <%= activeTourneys %>
                                </td>
                                <td data-ui-style="rolling-standings-jsp-028">
                                    <%= totalPts %> pts
                                </td>
                                <td data-ui-style="rolling-standings-jsp-029" style="color: <%= lastPtsColor %>;">
                                    <%= lastPtsText %>
                                </td>
                                <td data-ui-style="rolling-standings-jsp-030" style="color: <%= changeColor %>;">
                                    <%= changeText %>
                                </td>
                            </tr>
                        <% }
                        } else if (standingsList != null && !standingsList.isEmpty()) {
                            for (int i = 0; i < standingsList.size(); i++) {
                                SeriesStanding st = standingsList.get(i);
                                int rank = i + 1;
                                boolean isTop10 = (rank <= 10);
                                String rankColor = isTop10 ? "var(--ui-accent)" : "var(--ui-text)";
                                String nameColor = isTop10 ? "var(--ui-accent)" : "var(--ui-text)";
                                String nameHover = isTop10 ? "var(--ui-accent-hover)" : "var(--ui-accent)";
                        %>
                            <tr class="<%= isTop10 ? "top-10" : "" %>">
                                <td data-ui-style="rolling-standings-jsp-031" style="color: <%= rankColor %>;">
                                    <div class="rank-wrap">
                                        <span class="rank-badge <%= isTop10 ? "top-10" : "" %> rank-<%= rank %>"><%= rank %></span>
                                        <span class="rank-change same" title="Chưa có biến động">-</span>
                                    </div>
                                </td>
                                <td data-ui-style="rolling-standings-jsp-032" style="color: <%= nameColor %>;">
                                    <a href="${pageContext.request.contextPath}/team-profile?seriesId=<%= seriesIdVal %>&teamName=<%= java.net.URLEncoder.encode(st.getNormalizedTeamName(), "UTF-8") %>" class="team-link" data-ui-style="rolling-standings-jsp-033" style="color: <%= nameColor %>;" onmouseover="this.style.color='<%= nameHover %>'" onmouseout="this.style.color='<%= nameColor %>'">
                                        <%= st.getNormalizedTeamName() %>
                                    </a>
                                </td>
                                <td data-ui-style="rolling-standings-jsp-034">
                                    <%= (st.getTotalRollingPoints() > 0) ? 1 : 0 %>
                                </td>
                                <td data-ui-style="rolling-standings-jsp-035">
                                    <%= st.getTotalRollingPoints() %> pts
                                </td>
                                <td data-ui-style="rolling-standings-jsp-036">
                                    0 pts
                                </td>
                                <td data-ui-style="rolling-standings-jsp-037">
                                    0 pts
                                </td>
                            </tr>
                        <% }
                        } else { %>
                            <tr>
                                <td colspan="6" data-ui-style="rolling-standings-jsp-038">
                                    <i class="fa-solid fa-folder-open" data-ui-style="rolling-standings-jsp-039" style="display: block;"></i>
                                    Chưa có dữ liệu điểm tích lũy cho Series này. Hãy tạo giải đấu con và hoàn tất thi đấu để tích điểm.
                                </td>
                            </tr>
                        <% } %>
                    </tbody>
                </table>
            </div></main></div>

        <!-- Reusable Edit Series Popup Component -->
        <jsp:include page="/common/component/edit-series-popup.jsp">
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
            <jsp:param name="formActionUrl" value="${pageContext.request.contextPath}/rolling/standings"/>
        </jsp:include>

        <script>
            window.seriesSubTournaments = [
                <%
                java.util.Map<String, java.util.List<String>> stageFormatsMap = (java.util.Map<String, java.util.List<String>>) request.getAttribute("stageFormatsMap");
                if (tournamentsList != null) {
                    for (int i = 0; i < tournamentsList.size(); i++) {
                        Tournament t = tournamentsList.get(i);
                        String rawCfg = (t != null) ? t.getSeriesPointsConfig() : null;
                        String cfgJson = "{}";
                        if (rawCfg != null && rawCfg.trim().startsWith("{") && rawCfg.trim().endsWith("}")) {
                            cfgJson = rawCfg.trim();
                        }
                        String safeName = (t != null && t.getName() != null) ? t.getName().replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", " ").replace("\r", "") : "";
                        String safeId = (t != null && t.getId() != null) ? t.getId() : "";
                        int tIdx = (t != null && t.getTournamentIndexInSeries() > 0) ? t.getTournamentIndexInSeries() : (i + 1);
                        String tType = (t != null && t.getTournamentType() != null) ? t.getTournamentType() : "SINGLE_STAGE";
                        boolean isMulti = (t != null && "MULTI_STAGE".equalsIgnoreCase(t.getTournamentType()));
                        String fmt = (t != null && t.getFormat() != null) ? t.getFormat().toUpperCase() : "SINGLE_ELIMINATION";
                        String tTier = (t != null && t.getTierName() != null) ? t.getTierName().toUpperCase() : "A";
                        List<String> stgFormats = (stageFormatsMap != null) ? stageFormatsMap.get(safeId) : null;
                        String s1Fmt = (stgFormats != null && !stgFormats.isEmpty()) ? stgFormats.get(0) : fmt;
                        String s2Fmt = (stgFormats != null && stgFormats.size() > 1) ? stgFormats.get(1) : "SINGLE_ELIMINATION";
                %>
                    {
                        id: "<%= safeId %>",
                        name: "<%= safeName %>",
                        index: <%= tIdx %>,
                        format: "<%= fmt %>",
                        tournamentType: "<%= tType %>",
                        isMultiStage: <%= isMulti %>,
                        stage1Format: "<%= (s1Fmt != null) ? s1Fmt.toUpperCase() : "" %>",
                        stage2Format: "<%= (s2Fmt != null) ? s2Fmt.toUpperCase() : "" %>",
                        championName: "<%= (t != null && t.getChampionName() != null) ? t.getChampionName().replace("\\", "\\\\").replace("\"", "\\\"") : "" %>",
                        tierName: "<%= tTier %>",
                        pointsConfig: <%= cfgJson %>,
                        teamsJson: <%= (t != null && t.getTeamsJson() != null && !t.getTeamsJson().trim().isEmpty()) ? t.getTeamsJson() : "null" %>
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
            <%
                List<java.util.Map<String, Integer>> serverTourneyPoints = (List<java.util.Map<String, Integer>>) request.getAttribute("serverTourneyPoints");
                if (serverTourneyPoints == null && series != null) {
                    serverTourneyPoints = RollingWindowPointService.getInstance().getTourneyPointsPerTournament(series.getId());
                }
                List<java.util.Map<String, Boolean>> serverTourneyParticipation = (List<java.util.Map<String, Boolean>>) request.getAttribute("serverTourneyParticipation");
                if (serverTourneyParticipation == null && series != null) {
                    serverTourneyParticipation = RollingWindowPointService.getInstance().getTourneyParticipationPerTournament(series.getId());
                }
            %>
            window.serverTourneyPoints = [
                <% if (serverTourneyPoints != null) {
                    for (int sIdx = 0; sIdx < serverTourneyPoints.size(); sIdx++) {
                        java.util.Map<String, Integer> map = serverTourneyPoints.get(sIdx);
                        StringBuilder sb = new StringBuilder("{");
                        if (map != null) {
                            int count = 0;
                            for (java.util.Map.Entry<String, Integer> e : map.entrySet()) {
                                if (count++ > 0) sb.append(",");
                                sb.append("\"").append(e.getKey().replace("\\", "\\\\").replace("\"", "\\\"")).append("\":").append(e.getValue());
                            }
                        }
                        sb.append("}");
                %>
                    <%= sb.toString() %><%= (sIdx < serverTourneyPoints.size() - 1) ? "," : "" %>
                <%  }
                } %>
            ];
            window.serverTourneyParticipation = [
                <% if (serverTourneyParticipation != null) {
                    for (int sIdx = 0; sIdx < serverTourneyParticipation.size(); sIdx++) {
                        java.util.Map<String, Boolean> map = serverTourneyParticipation.get(sIdx);
                        StringBuilder sb = new StringBuilder("{");
                        if (map != null) {
                            int count = 0;
                            for (java.util.Map.Entry<String, Boolean> e : map.entrySet()) {
                                if (count++ > 0) sb.append(",");
                                sb.append("\"").append(e.getKey().replace("\\", "\\\\").replace("\"", "\\\"")).append("\":true");
                            }
                        }
                        sb.append("}");
                %>
                    <%= sb.toString() %><%= (sIdx < serverTourneyParticipation.size() - 1) ? "," : "" %>
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
        <script src="${pageContext.request.contextPath}/js/rolling/rolling-standings-engine.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/rolling/rolling-standings.js?v=<%= System.currentTimeMillis() %>"></script>
    </body>
</html>
