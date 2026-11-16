<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@page import="service.NumberOneHistory.Leader, service.NumberOneHistory.Reign"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, controller.RollingHofServlet.TournamentChampionDTO, controller.RollingHofServlet.TeamChampionStatsDTO, java.util.List"%>
<%
    String seriesIdVal = request.getParameter("id");
    if (seriesIdVal == null || seriesIdVal.trim().isEmpty()) {
        seriesIdVal = request.getParameter("seriesId");
    }

    Series series = (Series) request.getAttribute("series");
    if (series != null && (seriesIdVal == null || seriesIdVal.trim().isEmpty())) {
        seriesIdVal = series.getId();
    }

    String seriesName = (series != null) ? series.getName() : "Series Circuit";
    List<Tournament> tournamentsList = (List<Tournament>) request.getAttribute("tournamentsList");
    List<TournamentChampionDTO> tourneyChampList = (List<TournamentChampionDTO>) request.getAttribute("tourneyChampList");
    List<TeamChampionStatsDTO> topChampionsList = (List<TeamChampionStatsDTO>) request.getAttribute("topChampionsList");


    List<Leader> numberOneLeaders = (List<Leader>) request.getAttribute("numberOneHistory");
    Leader latestLeader = null, mostLeader = null, streakLeader = null;
    int leaderMilestones = 0;
    if (numberOneLeaders != null) for (Leader leader : numberOneLeaders) {
        leaderMilestones += leader.getMilestones();
        if (leader.isLatest()) latestLeader = leader;
        if (mostLeader == null || leader.getMilestones() > mostLeader.getMilestones()) mostLeader = leader;
        if (streakLeader == null || leader.getLongestStreak() > streakLeader.getLongestStreak()) streakLeader = leader;
    }

    int totalTourneys = (tournamentsList != null) ? tournamentsList.size() : 0;
    int completedTourneys = 0;
    if (tourneyChampList != null) {
        for (TournamentChampionDTO tc : tourneyChampList) {
            if (tc.getChampionTeamName() != null && !tc.getChampionTeamName().trim().isEmpty()) {
                completedTourneys++;
            }
        }
    }
    int totalChampsCount = (topChampionsList != null) ? topChampionsList.size() : 0;
    String topTeamText = "-";
    if (topChampionsList != null && !topChampionsList.isEmpty()) {
        TeamChampionStatsDTO topT = topChampionsList.get(0);
        topTeamText = topT.getTeamName() + " (" + topT.getTotalChampionships() + " 🏆)";
    }
%>
<!DOCTYPE html>
<html lang="vi">

<head>
    <!-- Favicon -->
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
    <link rel="alternate icon" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Bảng Vàng (Hall of Fame) - <%= seriesName %> - TOURMA</title>

    <!-- Google Font Readex Pro -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap" rel="stylesheet">

    <!-- FontAwesome Icons -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

    <!-- Stylesheets -->
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css?v=18">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-hof.css?v=20261110-hof-equal-tables">
<script src="${pageContext.request.contextPath}/js/theme.js?v=20261101-settings"></script>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=20261101-settings">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=18">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/workspace.css?v=20">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-workspace.css?v=18">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css?v=6">
</head>

<body class="ui-page workspace-page tournament-workspace series-page rolling-hof-page">
    <!-- Navigation Header Component -->
    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="tournaments" />
    <jsp:param name="themeToggle" value="true"/></jsp:include>

    <!-- Sidebar Component -->
    <div class="tournament-shell"><details class="tournament-navigation" open><summary>Điều hướng chuỗi giải</summary><jsp:include page="/common/component/sidebar.jsp">
        <jsp:param name="activeStep" value="hof" />
        <jsp:param name="seriesId" value="<%= seriesIdVal %>" />
    </jsp:include></details>

    <!-- Main Content with Sidebar Layout -->
    <main class="series-main" data-ui-style="rolling-hof-jsp-001">



        <!-- Hall of Fame Header Banner (Only Screen Title + Description) -->
        <div class="hof-header-banner">
            <div class="hof-title-wrap">
                <div class="hof-crown-icon-box">
                    <i class="fa-solid fa-crown"></i>
                </div>
                <div>
                    <h1 class="hof-main-title">
                        Bảng Vàng
                    </h1>
                    <div class="hof-subtitle">
                        <c:out value="<%= seriesName %>"/>
                    </div>
                </div>
            </div>
        </div>


        <div class="hof-metrics" aria-label="Tổng quan Bảng vàng">
            <div class="hof-metric"><span>Giải có nhà vô địch</span><strong><%= completedTourneys %></strong><small>trong <%= totalTourneys %> giải</small></div>
            <div class="hof-metric"><span>Nhà vô địch</span><strong><%= totalChampsCount %></strong><small>người từng giành danh hiệu</small></div>
            <div class="hof-metric"><span>Từng giữ #1 BXH</span><strong><%= numberOneLeaders != null ? numberOneLeaders.size() : 0 %></strong><small>người dẫn đầu tích lũy</small></div>
            <div class="hof-metric"><span>Lịch sử #1</span><strong><%= leaderMilestones %></strong><small>mốc giải đã ghi nhận</small></div>
        </div>

        <% if (numberOneLeaders != null && !numberOneLeaders.isEmpty()) { %>
        <div class="hof-insights-grid">
            <section class="hof-card-panel hof-tenure-chart" aria-labelledby="hofTenureTitle">
                <div class="hof-panel-header"><h2 class="hof-panel-title" id="hofTenureTitle">Dấu ấn trên đỉnh BXH</h2><p class="hof-leader-note">Số mốc giữ #1 sau mỗi giải hoàn tất.</p></div>
                <div class="hof-tenure-rows">
                <% for (Leader leader : numberOneLeaders) {
                    String leaderUrl = request.getContextPath() + "/team-profile?seriesId=" + java.net.URLEncoder.encode(series.getId(), "UTF-8") + "&teamName=" + java.net.URLEncoder.encode(leader.getTeamName(), "UTF-8");
                    String share = String.format(java.util.Locale.ROOT, "%.2f%%", 100.0 * leader.getMilestones() / mostLeader.getMilestones());
                %>
                    <div class="hof-tenure-row<%= leader.isLatest() ? " is-latest" : "" %>">
                        <a href="<%= leaderUrl.replace("&", "&amp;") %>"><c:out value="<%= leader.getTeamName() %>"/></a>
                        <span class="hof-tenure-track" aria-hidden="true"><span style="width:<%= share %>"></span></span>
                        <strong><%= leader.getMilestones() %></strong>
                    </div>
                <% } %>
                </div>
            </section>
            <div class="hof-records">
                <% if (latestLeader != null) { %>
                <section class="hof-card-panel hof-current-leader">
                    <span class="hof-record-label">#1 BXH hiện tại</span>
                    <h2><c:out value="<%= latestLeader.getTeamName() %>"/></h2>
                    <p>Sau <c:out value="<%= latestLeader.getLast().getName() %>"/> · Giải #<%= latestLeader.getLastIndex() %></p>
                    <a href="#numberOneHistoryTitle" class="hof-history-link">Xem lịch sử #1 <i class="fa-solid fa-arrow-down" aria-hidden="true"></i></a>
                </section>
                <% } %>
                <div class="hof-record-pair">
                    <section class="hof-card-panel hof-record"><span class="hof-record-label">Giữ #1 nhiều nhất</span><strong><%= mostLeader.getMilestones() %><small>mốc</small></strong><p><c:out value="<%= mostLeader.getTeamName() %>"/></p></section>
                    <section class="hof-card-panel hof-record"><span class="hof-record-label">Chuỗi #1 dài nhất</span><strong><%= streakLeader.getLongestStreak() %><small>mốc</small></strong><p><c:out value="<%= streakLeader.getTeamName() %>"/></p></section>
                </div>
            </div>
        </div>
        <% } %>

        <div class="hof-detail-heading"><h2>Danh hiệu và lịch sử #1</h2></div>

        <div class="hof-split-grid">
            <div class="hof-card-panel hof-champions-panel">
                <div class="hof-panel-header">
                    <div class="hof-panel-title-wrap">
                        <h3 class="hof-panel-title">
                            Các nhà vô địch
                        </h3>
                        <span class="hof-panel-badge gold" id="hofChampsCount"><%= totalChampsCount %></span>
                    </div>
                    <div class="hof-panel-controls">
                        <div class="hof-tier-filter-pills multi-select" id="rightTierFilters">
                            <button type="button" class="hof-tier-btn active" data-tier="ALL" onclick="toggleRightTierFilter('ALL')">Tất cả</button>
                            <button type="button" class="hof-tier-btn tier-s active" data-tier="S" onclick="toggleRightTierFilter('S')">S</button>
                            <button type="button" class="hof-tier-btn tier-a active" data-tier="A" onclick="toggleRightTierFilter('A')">A</button>
                            <button type="button" class="hof-tier-btn tier-b active" data-tier="B" onclick="toggleRightTierFilter('B')">B</button>
                            <button type="button" class="hof-tier-btn tier-c active" data-tier="C" onclick="toggleRightTierFilter('C')">C</button>
                            <button type="button" class="hof-tier-btn tier-d active" data-tier="D" onclick="toggleRightTierFilter('D')">D</button>
                        </div>
                        <div class="hof-search-box">
                            <i class="fa-solid fa-magnifying-glass"></i>
                            <input type="text" id="rightChampSearch" class="form-control hof-search-input" placeholder="Tìm kiếm..." onkeyup="filterRightTable()">
                        </div>
                    </div>
                </div>

                <div class="hof-table-wrapper">
                    <table class="hof-table" id="hofChampionsTable">
                        <colgroup>
                            <col data-ui-style="rolling-hof-jsp-014">
                            <col data-ui-style="rolling-hof-jsp-015">
                        </colgroup>
                        <thead>
                            <tr>
                                <th class="text-left" data-ui-style="rolling-hof-jsp-016">Tên</th>
                                <th class="text-right" data-ui-style="rolling-hof-jsp-017">Số lần vô địch</th>
                            </tr>
                        </thead>
                        <tbody>
                            <% if (topChampionsList != null && !topChampionsList.isEmpty()) {
                                for (int idx = 0; idx < topChampionsList.size(); idx++) {
                                    TeamChampionStatsDTO champ = topChampionsList.get(idx);
                            %>
                                <tr data-team-name="<%= champ.getTeamName() %>" data-tier-s="<%= champ.getTierSCount() %>" data-tier-a="<%= champ.getTierACount() %>" data-tier-b="<%= champ.getTierBCount() %>" data-tier-c="<%= champ.getTierCCount() %>" data-tier-d="<%= champ.getTierDCount() %>" data-total="<%= champ.getTotalChampionships() %>">
                                    <td class="text-left" data-ui-style="rolling-hof-jsp-018">
                                        <a href="${pageContext.request.contextPath}/team-profile?seriesId=<%= seriesIdVal %>&teamName=<%= java.net.URLEncoder.encode(champ.getTeamName(), "UTF-8") %>" class="hof-team-link" title="Xem hồ sơ đội <%= champ.getTeamName() %>">
                                            <%= champ.getTeamName() %>
                                        </a>
                                    </td>
                                    <td class="text-right" data-ui-style="rolling-hof-jsp-019">
                                        <span class="hof-win-count-text"><%= champ.getTotalChampionships() %></span>
                                    </td>
                                </tr>
                            <% }
                            } else { %>
                                <tr>
                                    <td colspan="2" data-ui-style="rolling-hof-jsp-020">
                                        Chưa có đội bóng nào lên ngôi vô địch.
                                    </td>
                                </tr>
                            <% } %>
                        </tbody>
                    </table>
                </div>

                <div class="hof-pagination" id="rightPagination">
                    <span class="hof-pagination-info" id="rightPageInfo">Trang 1 / 1</span>
                    <div class="hof-pagination-btns" id="rightPageBtns"></div>
                </div>
            </div>

            <div class="hof-card-panel hof-events-panel">
                <div class="hof-panel-header">
                    <div class="hof-panel-title-wrap">
                        <h3 class="hof-panel-title">
                            Các giải đã diễn ra
                        </h3>
                        <span class="hof-panel-badge" id="hofTourneysCount"><%= (tourneyChampList != null ? tourneyChampList.size() : 0) %></span>
                    </div>
                    <div class="hof-panel-controls">
                        <div class="hof-tier-filter-pills single-select" id="leftTierFilters">
                            <button type="button" class="hof-tier-btn active" data-tier="ALL" onclick="setLeftTierFilter('ALL')">Tất cả</button>
                            <button type="button" class="hof-tier-btn tier-s" data-tier="S" onclick="setLeftTierFilter('S')">S</button>
                            <button type="button" class="hof-tier-btn tier-a" data-tier="A" onclick="setLeftTierFilter('A')">A</button>
                            <button type="button" class="hof-tier-btn tier-b" data-tier="B" onclick="setLeftTierFilter('B')">B</button>
                            <button type="button" class="hof-tier-btn tier-c" data-tier="C" onclick="setLeftTierFilter('C')">C</button>
                            <button type="button" class="hof-tier-btn tier-d" data-tier="D" onclick="setLeftTierFilter('D')">D</button>
                        </div>
                        <div class="hof-search-box">
                            <i class="fa-solid fa-magnifying-glass"></i>
                            <input type="text" id="leftTourneySearch" class="form-control hof-search-input" placeholder="Tìm kiếm..." onkeyup="filterLeftTable()">
                        </div>
                    </div>
                </div>

                <div class="hof-table-wrapper">
                    <table class="hof-table" id="hofTourneyTable">
                        <colgroup>
                            <col data-ui-style="rolling-hof-jsp-004">
                            <col data-ui-style="rolling-hof-jsp-005">
                            <col data-ui-style="rolling-hof-jsp-006">
                        </colgroup>
                        <thead>
                            <tr>
                                <th class="text-left" data-ui-style="rolling-hof-jsp-007">Giải</th>
                                <th class="text-left" data-ui-style="rolling-hof-jsp-008">Nhà vô địch</th>
                                <th class="text-right" data-ui-style="rolling-hof-jsp-009">Lần thứ</th>
                            </tr>
                        </thead>
                        <tbody>
                            <% if (tourneyChampList != null && !tourneyChampList.isEmpty()) {
                                for (int i = 0; i < tourneyChampList.size(); i++) {
                                    TournamentChampionDTO tc = tourneyChampList.get(i);
                                    Tournament t = tc.getTournament();
                                    String tier = (t.getTierName() != null) ? t.getTierName().toUpperCase().trim() : "A";
                                    boolean isCompleted = (tc.getChampionTeamName() != null && !tc.getChampionTeamName().trim().isEmpty());
                            %>
                                <tr data-tourney-id="<%= t.getId() %>" data-tier="<%= tier %>">
                                    <td class="text-left" data-ui-style="rolling-hof-jsp-010">
                                        <div class="hof-tourney-name-wrap">
                                            <a href="<%= tc.getFinalStageUrl() %>" class="hof-tourney-link" title="Bấm để xem giai đoạn cuối giải <%= t.getName() %>">
                                                <%= t.getName() %>
                                            </a>
                                            <span class="hof-tier-text tier-<%= tier.toLowerCase() %>">[<%= tier %>]</span>
                                        </div>
                                    </td>
                                    <td class="hof-tourney-champ-cell text-left" data-ui-style="rolling-hof-jsp-011">
                                        <% if (isCompleted) { %>
                                            <a href="${pageContext.request.contextPath}/team-profile?seriesId=<%= seriesIdVal %>&teamName=<%= java.net.URLEncoder.encode(tc.getChampionTeamName(), "UTF-8") %>" class="hof-champ-name-link" title="Xem hồ sơ đội <%= tc.getChampionTeamName() %>">
                                                <%= tc.getChampionTeamName() %>
                                            </a>
                                        <% } else { %>
                                            <span class="hof-champ-empty">-</span>
                                        <% } %>
                                    </td>
                                    <td class="hof-tourney-ordinal-cell text-right" data-ui-style="rolling-hof-jsp-012">
                                        <% if (isCompleted) { %>
                                            <span class="hof-ordinal-text"><%= tc.getChampionshipOrdinal() %></span>
                                        <% } else { %>
                                            <span class="hof-ordinal-empty">-</span>
                                        <% } %>
                                    </td>
                                </tr>
                            <% }
                            } else { %>
                                <tr>
                                    <td colspan="3" data-ui-style="rolling-hof-jsp-013">
                                        Chưa có giải đấu con nào trong chuỗi giải này.
                                    </td>
                                </tr>
                            <% } %>
                        </tbody>
                    </table>
                </div>

                <div class="hof-pagination" id="leftPagination">
                    <span class="hof-pagination-info" id="leftPageInfo">Trang 1 / 1</span>
                    <div class="hof-pagination-btns" id="leftPageBtns"></div>
                </div>
            </div>

            <!-- ========================================================================= -->
            <!-- BẢNG BÊN PHẢI: CÁC NHÀ VÔ ĐỊCH (TÊN, SỐ LẦN VÔ ĐỊCH)                      -->
            <!-- ========================================================================= -->


            <section class="hof-card-panel hof-leader-history" aria-labelledby="numberOneHistoryTitle">
                <% List<Reign> reigns = (List<Reign>) request.getAttribute("numberOneReigns"); %>
                <div class="hof-panel-header">
                    <div class="hof-panel-title-wrap"><h2 class="hof-panel-title" id="numberOneHistoryTitle">Lịch sử #1</h2><span class="hof-panel-badge"><%= reigns != null ? reigns.size() : 0 %> đợt</span></div>
                    <div class="hof-panel-controls"><p class="hof-leader-note">Mới nhất ở trên · STT từ đợt đầu tiên</p><div class="hof-search-box"><i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i><input type="search" id="reignSearch" class="form-control hof-search-input" aria-label="Tìm lịch sử #1" placeholder="Tìm người hoặc giải..." oninput="filterReignTable(true)"></div></div>
                </div>
                <div class="hof-leader-scroll" tabindex="0" role="region" aria-label="Các đợt giữ hạng nhất, mới nhất ở trên">
                    <table class="hof-table hof-leader-table" id="hofReignTable">
                        <thead><tr><th>STT</th><th class="text-left">Người giữ #1</th><th class="text-left">Từ giải → Đến giải</th></tr></thead>
                        <tbody>
                        <% if (reigns != null && !reigns.isEmpty()) {
                            for (Reign reign : reigns) {
                                String profileUrl = request.getContextPath() + "/team-profile?seriesId=" + java.net.URLEncoder.encode(series.getId(), "UTF-8") + "&teamName=" + java.net.URLEncoder.encode(reign.getTeamName(), "UTF-8");
                                if (reign.getPartnerId() != null) profileUrl += "&partnerId=" + java.net.URLEncoder.encode(reign.getPartnerId(), "UTF-8");
                        %>
                            <tr class="<%= reign.isLatest() ? "hof-reign-latest" : "" %>">
                                <td><%= reign.getOrdinal() %></td>
                                <td class="text-left"><a class="hof-team-link" href="<%= profileUrl.replace("&", "&amp;") %>"><c:out value="<%= reign.getTeamName() %>"/></a><% if (reign.isLatest()) { %><small class="hof-leader-latest">Hiện tại</small><% } %></td>
                                <td class="text-left hof-reign-range"><span><c:out value="<%= reign.getFirst().getName() %>"/> <small>#<%= reign.getFirstIndex() %></small></span><span>→ <c:out value="<%= reign.getLast().getName() %>"/> <small>#<%= reign.getLastIndex() %></small></span></td>
                            </tr>
                        <% } } else { %><tr><td colspan="3" class="hof-leader-empty">Chưa có đợt giữ #1 nào.</td></tr><% } %>
                        </tbody>
                    </table>
                </div>
                <div class="hof-pagination" id="reignPagination"><span id="reignPageInfo">Trang 1 / 1</span><div class="hof-pagination-btns" id="reignPageBtns"></div></div>
            </section>

        </div>

    </main></div>

    <!-- Global App Context & Sub-Tournaments Data for Live Sync -->
    <script>
        window.appContextPath = "${pageContext.request.contextPath}";
        window.seriesIdVal = "<%= seriesIdVal %>";
        window.seriesSubTournaments = [
            <% if (tournamentsList != null) {
                for (int i = 0; i < tournamentsList.size(); i++) {
                    Tournament t = tournamentsList.get(i);
                    String safeName = (t != null && t.getName() != null) ? t.getName().replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", " ").replace("\r", "") : "";
                    String safeId = (t != null && t.getId() != null) ? t.getId() : "";
                    int tIdx = (t != null && t.getTournamentIndexInSeries() > 0) ? t.getTournamentIndexInSeries() : (i + 1);
                    String tierName = (t != null && t.getTierName() != null) ? t.getTierName().toUpperCase().trim() : "A";
                    String fmt = (t != null && t.getFormat() != null) ? t.getFormat().toUpperCase() : "SINGLE_ELIMINATION";
                    String tType = (t != null && t.getTournamentType() != null) ? t.getTournamentType() : "SINGLE_STAGE";
            %>
                {
                    id: "<%= safeId %>",
                    name: "<%= safeName %>",
                    index: <%= tIdx %>,
                    tier: "<%= tierName %>",
                    format: "<%= fmt %>",
                    tournamentType: "<%= tType %>",
                    isMultiStage: <%= "MULTI_STAGE".equalsIgnoreCase(tType) %>
                }<%= (i < tournamentsList.size() - 1) ? "," : "" %>
            <%  }
            } %>
        ];
    </script>
    <script src="${pageContext.request.contextPath}/js/round-robin-algorithm.js?v=<%= System.currentTimeMillis() %>"></script>
    <script src="${pageContext.request.contextPath}/js/rolling/rolling-hof.js?v=20261110-reign-pagination"></script>
</body>
</html>
