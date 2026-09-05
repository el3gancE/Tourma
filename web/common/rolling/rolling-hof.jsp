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

    <!-- Google Font Lexend -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">

    <!-- FontAwesome Icons -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

    <!-- Stylesheets -->
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-hof.css?v=<%= System.currentTimeMillis() %>">
</head>

<body>
    <!-- Navigation Header Component -->
    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="tournaments" />
    </jsp:include>

    <!-- Sidebar Component -->
    <jsp:include page="/common/component/sidebar.jsp">
        <jsp:param name="activeStep" value="hof" />
        <jsp:param name="seriesId" value="<%= seriesIdVal %>" />
    </jsp:include>

    <!-- Main Content with Sidebar Layout -->
    <main class="container has-sidebar" style="max-width: 1280px; padding: 1.5rem 1.25rem;">

        <!-- Breadcrumb -->
        <div style="margin-bottom: 1.1rem;">
            <a href="${pageContext.request.contextPath}/rolling/dashboard?id=<%= seriesIdVal %>" class="text-muted" style="font-size: 0.85rem; font-weight: 600; text-decoration: none;">
                <i class="fa-solid fa-arrow-left"></i> Quay lại Dashboard Series
            </a>
        </div>

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
                        Vinh danh lịch sử các nhà vô địch & thành tích các mùa giải của <strong><%= seriesName %></strong>
                    </div>
                </div>
            </div>
        </div>

        <!-- Split 2-Panel Grid: Left = Các giải đã diễn ra | Right = Các nhà vô địch -->
        <div class="hof-split-grid">

            <!-- ========================================================================= -->
            <!-- BẢNG BÊN TRÁI: CÁC GIẢI ĐÃ DIỄN RA (GIẢI, NHÀ VÔ ĐỊCH, LẦN THỨ)           -->
            <!-- ========================================================================= -->
            <div class="hof-card-panel">
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
                            <col style="width: 44%;">
                            <col style="width: 42%;">
                            <col style="width: 14%;">
                        </colgroup>
                        <thead>
                            <tr>
                                <th class="text-left" style="text-align: left !important;">Giải</th>
                                <th class="text-left" style="text-align: left !important;">Nhà vô địch</th>
                                <th class="text-right" style="text-align: right !important;">Lần thứ</th>
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
                                    <td class="text-left" style="text-align: left !important;">
                                        <div class="hof-tourney-name-wrap">
                                            <a href="<%= tc.getFinalStageUrl() %>" class="hof-tourney-link" title="Bấm để xem giai đoạn cuối giải <%= t.getName() %>">
                                                <%= t.getName() %>
                                            </a>
                                            <span class="hof-tier-text tier-<%= tier.toLowerCase() %>">[<%= tier %>]</span>
                                        </div>
                                    </td>
                                    <td class="hof-tourney-champ-cell text-left" style="text-align: left !important;">
                                        <% if (isCompleted) { %>
                                            <a href="${pageContext.request.contextPath}/team-profile?seriesId=<%= seriesIdVal %>&teamName=<%= java.net.URLEncoder.encode(tc.getChampionTeamName(), "UTF-8") %>" class="hof-champ-name-link" title="Xem hồ sơ đội <%= tc.getChampionTeamName() %>">
                                                <%= tc.getChampionTeamName() %>
                                            </a>
                                        <% } else { %>
                                            <span class="hof-champ-empty">-</span>
                                        <% } %>
                                    </td>
                                    <td class="hof-tourney-ordinal-cell text-right" style="text-align: right !important;">
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
                                    <td colspan="3" style="text-align: center; padding: 2rem; color: #64748b;">
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
            <div class="hof-card-panel">
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
                            <col style="width: 65%;">
                            <col style="width: 35%;">
                        </colgroup>
                        <thead>
                            <tr>
                                <th class="text-left" style="text-align: left !important;">Tên</th>
                                <th class="text-right" style="text-align: right !important;">Số lần vô địch</th>
                            </tr>
                        </thead>
                        <tbody>
                            <% if (topChampionsList != null && !topChampionsList.isEmpty()) {
                                for (int idx = 0; idx < topChampionsList.size(); idx++) {
                                    TeamChampionStatsDTO champ = topChampionsList.get(idx);
                            %>
                                <tr data-team-name="<%= champ.getTeamName() %>" data-tier-s="<%= champ.getTierSCount() %>" data-tier-a="<%= champ.getTierACount() %>" data-tier-b="<%= champ.getTierBCount() %>" data-tier-c="<%= champ.getTierCCount() %>" data-tier-d="<%= champ.getTierDCount() %>" data-total="<%= champ.getTotalChampionships() %>">
                                    <td class="text-left" style="text-align: left !important;">
                                        <a href="${pageContext.request.contextPath}/team-profile?seriesId=<%= seriesIdVal %>&teamName=<%= java.net.URLEncoder.encode(champ.getTeamName(), "UTF-8") %>" class="hof-team-link" title="Xem hồ sơ đội <%= champ.getTeamName() %>">
                                            <%= champ.getTeamName() %>
                                        </a>
                                    </td>
                                    <td class="text-right" style="text-align: right !important;">
                                        <span class="hof-win-count-text"><%= champ.getTotalChampionships() %></span>
                                    </td>
                                </tr>
                            <% }
                            } else { %>
                                <tr>
                                    <td colspan="2" style="text-align: center; padding: 2rem; color: #64748b;">
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

        </div>

    </main>

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
    <script src="${pageContext.request.contextPath}/js/rolling/rolling-hof.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>
