<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@page import="model.Series, model.Season, model.SeriesDivision, model.Tournament, model.SeriesStanding, java.util.List, java.util.Map"%>
<%
    Series series = (Series) request.getAttribute("series");
    Season currentSeason = (Season) request.getAttribute("currentSeason");
    List<Season> seasonsList = (List<Season>) request.getAttribute("seasonsList");
    List<SeriesDivision> divisionsList = (List<SeriesDivision>) request.getAttribute("divisionsList");
    List<Tournament> tournamentsList = (List<Tournament>) request.getAttribute("tournamentsList");
    Map<Integer, List<SeriesStanding>> divisionStandingsMap = (Map<Integer, List<SeriesStanding>>) request.getAttribute("divisionStandingsMap");

    String seriesIdVal = (series != null && series.getId() != null) ? series.getId() : "";
    String seriesName = (series != null) ? series.getName() : "League System Circuit";
    String status = (series != null && series.getStatus() != null) ? series.getStatus() : "ACTIVE";
    int totalDivs = (divisionsList != null && !divisionsList.isEmpty()) ? divisionsList.size() : (series != null ? series.getTotalDivisions() : 2);
    int totalTourneys = (tournamentsList != null) ? tournamentsList.size() : 0;
%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><%= seriesName %> - League Dashboard</title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">

    <!-- Fonts & Icons -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

    <!-- Design System CSS -->
    <script src="${pageContext.request.contextPath}/js/theme.js?v=20261101-settings"></script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=20261101-settings">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=18">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-workspace.css?v=18">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/league/league-dashboard.css?v=<%= System.currentTimeMillis() %>">
</head>
<body class="ui-page tournament-workspace series-dashboard-page league-dashboard-page">

    <!-- Shared Header -->
    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="my-series"/>
        <jsp:param name="themeToggle" value="true"/>
    </jsp:include>

    <!-- Sidebar Shell -->
    <div class="tournament-shell">
        <details class="tournament-navigation" open>
            <summary>Điều hướng chuỗi giải</summary>
            <jsp:include page="/common/component/sidebar.jsp">
                <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
                <jsp:param name="activeStep" value="dashboard"/>
            </jsp:include>
        </details>

        <!-- Main League Dashboard Area -->
        <main class="league-dashboard-wrapper" aria-label="Dashboard chuỗi giải League">
            
            <!-- Heading Bar -->
            <header class="league-dashboard-heading">
                <div>
                    <a class="dashboard-back" href="${pageContext.request.contextPath}/my-series">
                        <i class="fa-solid fa-arrow-left" aria-hidden="true"></i> Chuỗi giải của tôi
                    </a>
                    <h1><c:out value="<%= seriesName %>" /></h1>
                    <div class="league-header-badges">
                        <span class="league-badge accent"><i class="fa-solid fa-shield-halved" aria-hidden="true"></i> League System</span>
                        <span class="league-badge"><%= totalDivs %> Hạng đấu</span>
                        <span class="league-badge <%= "ACTIVE".equalsIgnoreCase(status) ? "success" : "gold" %>">
                            <i class="fa-solid fa-circle" style="font-size: 6px;" aria-hidden="true"></i>
                            <%= "ACTIVE".equalsIgnoreCase(status) ? "Đang hoạt động" : status %>
                        </span>
                    </div>
                </div>

                <div class="league-heading-actions">
                    <% if (currentSeason == null) { %>
                        <a href="${pageContext.request.contextPath}/league/season-setup?seriesId=<%= seriesIdVal %>" class="league-btn primary">
                            <i class="fa-solid fa-calendar-plus" aria-hidden="true"></i> Thiết lập Season 1
                        </a>
                    <% } else { %>
                        <a href="${pageContext.request.contextPath}/league/season-setup?seriesId=<%= seriesIdVal %>&seasonId=<%= currentSeason.getId() %>" class="league-btn secondary">
                            <i class="fa-solid fa-gear" aria-hidden="true"></i> Cấu hình mùa
                        </a>
                        <% if ("COMPLETED".equalsIgnoreCase(currentSeason.getStatus())) { %>
                            <a href="${pageContext.request.contextPath}/league/season-rollover?seriesId=<%= seriesIdVal %>" class="league-btn primary">
                                <i class="fa-solid fa-forward-step" aria-hidden="true"></i> Chuyển sang Season tiếp theo
                            </a>
                        <% } %>
                    <% } %>
                </div>
            </header>

            <% if (currentSeason == null) { %>
                <!-- EMPTY STATE: NO SEASONS CREATED YET -->
                <div class="league-empty-card">
                    <div class="league-empty-icon">
                        <i class="fa-solid fa-trophy" aria-hidden="true"></i>
                    </div>
                    <h2>Chưa có Mùa giải nào được khởi tạo</h2>
                    <p>
                        Chuỗi giải này hoạt động theo mô hình <strong>League System</strong> với các hạng đấu và lên xuống hạng.
                        Hãy bắt đầu bằng việc thiết lập <strong>Season 1</strong>, nạp danh sách các đội vào từng hạng và tự động tạo lịch thi đấu Round Robin!
                    </p>
                    <a href="${pageContext.request.contextPath}/league/season-setup?seriesId=<%= seriesIdVal %>" class="league-btn primary">
                        <i class="fa-solid fa-play" aria-hidden="true"></i> Bắt đầu thiết lập Season 1 ngay
                    </a>
                </div>
            <% } else { %>
                <!-- SEASON SELECTOR & BAR -->
                <section class="season-selector-bar">
                    <div class="season-selector-left">
                        <span class="season-selector-title"><i class="fa-regular fa-calendar-check" aria-hidden="true"></i> Chọn Mùa giải:</span>
                        <select class="season-select-dropdown" onchange="if(this.value) window.location.href='${pageContext.request.contextPath}/league/dashboard?seriesId=<%= seriesIdVal %>&seasonId=' + this.value;">
                            <% if (seasonsList != null) {
                                for (Season s : seasonsList) { %>
                                    <option value="<%= s.getId() %>" <%= s.getId().equals(currentSeason.getId()) ? "selected" : "" %>>
                                        <%= s.getName() %> (<%= s.getStatus() %>)
                                    </option>
                            <%  }
                               } %>
                        </select>
                        <span class="league-badge <%= "COMPLETED".equalsIgnoreCase(currentSeason.getStatus()) ? "gold" : "success" %>">
                            <%= "COMPLETED".equalsIgnoreCase(currentSeason.getStatus()) ? "Đã kết thúc" : "Đang thi đấu" %>
                        </span>
                    </div>

                    <div>
                        <% if (currentSeason.isHasSpecialCup()) { %>
                            <span class="league-badge gold"><i class="fa-solid fa-crown" aria-hidden="true"></i> Kèm giải Cúp Mùa</span>
                        <% } %>
                    </div>
                </section>

                <!-- METRICS OVERVIEW -->
                <section class="league-metrics-grid">
                    <div class="league-metric-card">
                        <span class="metric-label">Mùa giải <i class="fa-regular fa-calendar" aria-hidden="true"></i></span>
                        <div class="metric-value">Mùa <%= currentSeason.getSeasonNumber() %></div>
                        <span class="metric-sub"><%= currentSeason.getName() %></span>
                    </div>

                    <div class="league-metric-card">
                        <span class="metric-label">Số Hạng đấu <i class="fa-solid fa-layer-group" aria-hidden="true"></i></span>
                        <div class="metric-value"><%= totalDivs %> Hạng</div>
                        <span class="metric-sub">Lên/Xuống hạng trực tiếp</span>
                    </div>

                    <div class="league-metric-card">
                        <span class="metric-label">Giải đấu trong mùa <i class="fa-solid fa-sitemap" aria-hidden="true"></i></span>
                        <div class="metric-value"><%= totalTourneys %></div>
                        <span class="metric-sub">Round Robin các hạng</span>
                    </div>

                    <div class="league-metric-card">
                        <span class="metric-label">Giải Cúp Single Elimination <i class="fa-solid fa-trophy text-gold" aria-hidden="true"></i></span>
                        <div class="metric-value"><%= currentSeason.isHasSpecialCup() ? "Đã kích hoạt" : "Không áp dụng" %></div>
                        <span class="metric-sub"><%= currentSeason.isHasSpecialCup() ? "Nhánh đấu loại trực tiếp" : "Chỉ đấu League" %></span>
                    </div>
                </section>

                <!-- DIVISION TABS -->
                <div class="division-tabs-bar" role="tablist">
                    <% if (divisionsList != null && !divisionsList.isEmpty()) {
                        for (int i = 0; i < divisionsList.size(); i++) {
                            SeriesDivision div = divisionsList.get(i);
                    %>
                        <button type="button" class="division-tab-btn <%= i == 0 ? "active" : "" %>" role="tab"
                                onclick="switchDivisionTab(<%= div.getDivisionLevel() %>, this)">
                            <i class="fa-solid <%= i == 0 ? "fa-trophy text-gold" : "fa-shield-halved" %>" aria-hidden="true"></i>
                            <%= div.getName() %> (<%= div.getTeamQuota() %> đội)
                        </button>
                    <%  }
                       } else { %>
                        <button type="button" class="division-tab-btn active" role="tab">
                            <i class="fa-solid fa-trophy text-gold" aria-hidden="true"></i> Hạng Nhất (10 đội)
                        </button>
                    <% } %>
                    <% if (currentSeason.isHasSpecialCup()) { %>
                        <button type="button" class="division-tab-btn" role="tab" onclick="switchDivisionTab('cup', this)">
                            <i class="fa-solid fa-crown text-gold" aria-hidden="true"></i> Cúp Mùa Giải
                        </button>
                    <% } %>
                </div>

                <!-- DIVISION PANELS -->
                <% if (divisionsList != null && !divisionsList.isEmpty()) {
                    for (int i = 0; i < divisionsList.size(); i++) {
                        SeriesDivision div = divisionsList.get(i);
                        List<SeriesStanding> divStandings = (divisionStandingsMap != null) ? divisionStandingsMap.get(div.getDivisionLevel()) : null;
                %>
                    <div id="panel-division-<%= div.getDivisionLevel() %>" class="division-panel <%= i == 0 ? "active" : "" %>">
                        <div class="division-grid">
                            
                            <!-- LEFT: LEAGUE STANDINGS TABLE -->
                            <div class="league-section-card">
                                <div class="league-section-card-header">
                                    <h3><i class="fa-solid fa-ranking-star text-gold" aria-hidden="true"></i> Bảng Xếp Hạng <%= div.getName() %></h3>
                                    <span class="league-badge"><%= div.getTeamQuota() %> Đội thi đấu</span>
                                </div>

                                <table class="league-standings-table">
                                    <thead>
                                        <tr>
                                            <th class="rank-cell">#</th>
                                            <th>Đội bóng / Cơ thủ</th>
                                            <th style="text-align:center;">Trận</th>
                                            <th style="text-align:center;">T</th>
                                            <th style="text-align:center;">H</th>
                                            <th style="text-align:center;">B</th>
                                            <th style="text-align:center;">BT</th>
                                            <th style="text-align:center;">BB</th>
                                            <th style="text-align:center;">HS</th>
                                            <th style="text-align:right;">Điểm</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <% if (divStandings != null && !divStandings.isEmpty()) {
                                            for (int rank = 0; rank < divStandings.size(); rank++) {
                                                SeriesStanding st = divStandings.get(rank);
                                                int pos = rank + 1;
                                                boolean isChampion = (pos == 1 && div.getDivisionLevel() == 1);
                                                boolean isPromoted = (div.getPromotionSlots() > 0 && pos <= div.getPromotionSlots());
                                                boolean isRelegated = (div.getRelegationSlots() > 0 && pos > (divStandings.size() - div.getRelegationSlots()));
                                                String rowClass = isChampion ? "champion-row" : (isPromoted ? "promoted-row" : (isRelegated ? "relegated-row" : ""));
                                        %>
                                            <tr class="<%= rowClass %>">
                                                <td class="rank-cell">
                                                    <% if (pos == 1) { %><i class="fa-solid fa-crown text-gold"></i><% } else { %><%= pos %><% } %>
                                                </td>
                                                <td class="team-name-cell"><%= st.getNormalizedTeamName() %></td>
                                                <td style="text-align:center;"><%= st.getMatchesPlayed() %></td>
                                                <td style="text-align:center;"><%= st.getWins() %></td>
                                                <td style="text-align:center;"><%= st.getDraws() %></td>
                                                <td style="text-align:center;"><%= st.getLosses() %></td>
                                                <td style="text-align:center;"><%= st.getGoalsFor() %></td>
                                                <td style="text-align:center;"><%= st.getGoalsAgainst() %></td>
                                                <td style="text-align:center;"><%= (st.getGoalDiff() > 0 ? "+" : "") + st.getGoalDiff() %></td>
                                                <td class="pts-cell" style="text-align:right;"><%= st.getTotalRollingPoints() %></td>
                                            </tr>
                                        <%  }
                                           } else { %>
                                            <tr>
                                                <td colspan="10" style="text-align:center; padding:32px; color:var(--ui-muted);">
                                                    Chưa có dữ liệu bảng xếp hạng cho <%= div.getName() %>. Lịch thi đấu sẽ cập nhật ngay khi các trận diễn ra.
                                                </td>
                                            </tr>
                                        <% } %>
                                    </tbody>
                                </table>

                                <!-- LEGEND -->
                                <div class="standings-legend">
                                    <% if (div.getDivisionLevel() == 1) { %>
                                        <span class="legend-item"><span class="legend-dot champion"></span> Vô địch mùa giải</span>
                                    <% } %>
                                    <% if (div.getPromotionSlots() > 0) { %>
                                        <span class="legend-item"><span class="legend-dot promoted"></span> Thăng hạng lên Hạng trên (<%= div.getPromotionSlots() %> suất)</span>
                                    <% } %>
                                    <% if (div.getRelegationSlots() > 0) { %>
                                        <span class="legend-item"><span class="legend-dot relegated"></span> Rớt xuống Hạng dưới (<%= div.getRelegationSlots() %> suất)</span>
                                    <% } %>
                                </div>
                            </div>

                            <!-- RIGHT: TOURNAMENT / MATCH ACTIONS -->
                            <div class="league-section-card">
                                <div class="league-section-card-header">
                                    <h3><i class="fa-solid fa-calendar-days text-gold" aria-hidden="true"></i> Giải đấu & Vòng đấu</h3>
                                </div>
                                <div style="padding: 16px 20px;">
                                    <p style="font-size:12px; color:var(--ui-muted); margin-bottom:14px;">
                                        Thể thức thi đấu: <strong>Round Robin (Vòng tròn tính điểm)</strong>.
                                    </p>
                                    <%
                                        Tournament divTourney = null;
                                        if (tournamentsList != null) {
                                            for (Tournament t : tournamentsList) {
                                                if (t.getDivisionLevel() == div.getDivisionLevel()) {
                                                    divTourney = t;
                                                    break;
                                                }
                                            }
                                        }
                                    %>
                                    <% if (divTourney != null) { %>
                                        <div style="background:var(--ui-bg); border:1px solid var(--ui-line); border-radius:8px; padding:12px 14px; margin-bottom:14px;">
                                            <strong style="display:block; font-size:14px;"><%= divTourney.getName() %></strong>
                                            <span style="font-size:12px; color:var(--ui-muted);"><%= divTourney.getTeamCount() %> đội tham gia · <%= divTourney.getStatus() %></span>
                                        </div>
                                        <a href="${pageContext.request.contextPath}/my-tournaments?action=matches&id=<%= divTourney.getId() %>" class="league-btn primary" style="width:100%; justify-content:center;">
                                            <i class="fa-solid fa-gamepad" aria-hidden="true"></i> Vào thi đấu / Nhập điểm
                                        </a>
                                    <% } else { %>
                                        <div style="text-align:center; padding:16px 0; color:var(--ui-muted);">
                                            <p style="font-size:12px; margin-bottom:12px;">Chưa tạo giải Round Robin cho hạng này.</p>
                                            <a href="${pageContext.request.contextPath}/league/create-division-tourney?seriesId=<%= seriesIdVal %>&seasonId=<%= currentSeason.getId() %>&divisionId=<%= div.getId() %>" class="league-btn secondary" style="width:100%; justify-content:center;">
                                                <i class="fa-solid fa-plus" aria-hidden="true"></i> Tạo giải cho <%= div.getName() %>
                                            </a>
                                        </div>
                                    <% } %>
                                </div>
                            </div>

                        </div>
                    </div>
                <%  }
                   } %>

                <!-- SPECIAL CUP PANEL (IF ENABLED) -->
                <% if (currentSeason.isHasSpecialCup()) { %>
                    <div id="panel-division-cup" class="division-panel">
                        <div class="league-section-card" style="padding: 24px; text-align:center;">
                            <i class="fa-solid fa-crown text-gold" style="font-size:36px; margin-bottom:12px; display:inline-block;"></i>
                            <h2>Giải Cúp Mùa Giải (Single Elimination Cup)</h2>
                            <p style="color:var(--ui-muted); max-width:540px; margin: 0 auto 20px;">
                                Giải đấu knock-out loại trực tiếp gồm các đại diện của các hạng đấu, tranh cúp vô địch độc lập với giải League.
                            </p>
                            <% if (currentSeason.getSpecialCupTournamentId() != null) { %>
                                <a href="${pageContext.request.contextPath}/my-tournaments?action=matches&id=<%= currentSeason.getSpecialCupTournamentId() %>" class="league-btn primary">
                                    <i class="fa-solid fa-sitemap" aria-hidden="true"></i> Xem cây nhánh đấu Cúp Mùa
                                </a>
                            <% } else { %>
                                <a href="${pageContext.request.contextPath}/league/create-cup?seriesId=<%= seriesIdVal %>&seasonId=<%= currentSeason.getId() %>" class="league-btn primary">
                                    <i class="fa-solid fa-plus" aria-hidden="true"></i> Khởi tạo nhánh đấu Cúp Mùa
                                </a>
                            <% } %>
                        </div>
                    </div>
                <% } %>

            <% } %>

        </main>
    </div>

    <!-- Scripts -->
    <script>
        function switchDivisionTab(level, btn) {
            document.querySelectorAll('.division-tab-btn').forEach(function(b) { b.classList.remove('active'); });
            document.querySelectorAll('.division-panel').forEach(function(p) { p.classList.remove('active'); });
            btn.classList.add('active');
            var panel = document.getElementById('panel-division-' + level);
            if (panel) panel.classList.add('active');
        }
    </script>
</body>
</html>
