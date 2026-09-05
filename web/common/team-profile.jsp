<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, model.PartnerParticipant, controller.TeamProfileServlet.TourneyPerformanceDTO, controller.TeamProfileServlet.ChampionTournamentDTO, java.util.List, java.util.Map, java.text.SimpleDateFormat"%>
<%
    Series series = (Series) request.getAttribute("series");
    PartnerParticipant partner = (PartnerParticipant) request.getAttribute("partner");
    String teamName = (String) request.getAttribute("teamName");
    if (teamName == null || teamName.isEmpty()) teamName = "Đội Bóng";

    String seriesIdVal = (series != null && series.getId() != null) ? series.getId() : "";
    String seriesName = (series != null && series.getName() != null) ? series.getName() : "Series Circuit";

    int currentRank = (Integer) request.getAttribute("currentRank");
    int highestRank = (Integer) request.getAttribute("highestRank");
    String highestRankTourneyName = (String) request.getAttribute("highestRankTourneyName");
    String highestRankTourneyUrl = (String) request.getAttribute("highestRankTourneyUrl");
    int currentPoints = (Integer) request.getAttribute("currentPoints");
    int totalAccumulatedPoints = (Integer) request.getAttribute("totalAccumulatedPoints");
    int totalWins = (Integer) request.getAttribute("totalWins");
    int totalLosses = (Integer) request.getAttribute("totalLosses");
    int totalTourneysPlayed = (Integer) request.getAttribute("totalTourneysPlayed");

    int champCount = (Integer) request.getAttribute("champCount");
    int runnerUpCount = (Integer) request.getAttribute("runnerUpCount");
    int semiCount = (Integer) request.getAttribute("semiCount");
    int quarterCount = (Integer) request.getAttribute("quarterCount");

    List<ChampionTournamentDTO> championTourneys = (List<ChampionTournamentDTO>) request.getAttribute("championTourneys");
    List<TourneyPerformanceDTO> performanceList = (List<TourneyPerformanceDTO>) request.getAttribute("performanceList");

    String joinDateStr = "-";
    if (partner != null && partner.getCreatedAt() != null) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        joinDateStr = sdf.format(partner.getCreatedAt());
    } else if (series != null && series.getCreatedAt() != null) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        joinDateStr = sdf.format(series.getCreatedAt());
    }
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <!-- Favicon -->
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title><%= teamName %> - Hồ Sơ Đội Bóng - TOURMA</title>
        
        <!-- Google Fonts & FontAwesome -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Main Design System CSS, Sidebar CSS & Dedicated Team Profile CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/team-profile.css?v=<%= System.currentTimeMillis() %>">
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

        <main class="container has-sidebar" style="max-width: 1050px; padding: 1.5rem 1.25rem 3rem 1.25rem;">
            
            <!-- Breadcrumb Back Navigation -->
            <a href="${pageContext.request.contextPath}/rolling/standings?id=<%= seriesIdVal %>" class="team-profile-back-link">
                <i class="fa-solid fa-arrow-left"></i> Quay lại Bảng Xếp Hạng Series (<%= seriesName %>)
            </a>

            <!-- Top Profile Card -->
            <div class="team-profile-main-card">
                
                <!-- Team Name & Join Date Row -->
                <div class="team-profile-title-row">
                    <h1 class="team-profile-name"><%= teamName %></h1>
                    <div class="team-profile-join-date">
                        <i class="fa-solid fa-calendar-days text-mint"></i> Ngày tham gia chuỗi: <%= joinDateStr %>
                    </div>
                </div>

                <!-- 6 Metric Cards Row -->
                <div class="team-stat-grid-row1">
                    <!-- 1. Hạng Hiện Tại -->
                    <div class="team-stat-box <%= (currentRank == 1) ? "highlight-gold" : "" %>">
                        <div class="team-stat-label">
                            <i class="fa-solid fa-crown text-gold"></i> HẠNG HIỆN TẠI
                        </div>
                        <div class="team-stat-val <%= (currentRank == 1) ? "color-gold" : "" %>" id="profCurrentRank">
                            <%= (currentRank > 0) ? ("#" + currentRank) : "-" %>
                        </div>
                    </div>

                    <!-- 2. Hạng Cao Nhất -->
                    <div class="team-stat-box <%= (highestRank == 1 && currentPoints > 0) ? "highlight-gold" : "" %>">
                        <div class="team-stat-label">
                            <i class="fa-solid fa-crown text-gold"></i> HẠNG CAO NHẤT
                        </div>
                        <div class="team-stat-val <%= (highestRank == 1 && currentPoints > 0) ? "color-gold" : "" %>" id="profHighestRank">
                            <%= (highestRank > 0) ? ("#" + highestRank) : "-" %><% if (highestRank > 0 && highestRankTourneyName != null && !highestRankTourneyName.isEmpty()) { %><span class="rank-tourney-name">(<a href="<%= (highestRankTourneyUrl != null && !highestRankTourneyUrl.isEmpty()) ? highestRankTourneyUrl : "#" %>" class="rank-tourney-link" style="color: inherit; text-decoration: underline; text-underline-offset: 3px;" title="Xem giai đoạn cuối giải <%= highestRankTourneyName %>"><%= highestRankTourneyName %></a>)</span><% } %>
                        </div>
                    </div>

                    <!-- 3. Điểm Hiện Tại -->
                    <div class="team-stat-box">
                        <div class="team-stat-label">
                            <i class="fa-solid fa-star text-gold"></i> ĐIỂM HIỆN TẠI
                        </div>
                        <div class="team-stat-val color-gold" id="profCurrentPoints">
                            <%= currentPoints %><span class="pts-unit">pts</span>
                        </div>
                    </div>

                    <!-- 4. Tổng Điểm Tích Lũy -->
                    <div class="team-stat-box">
                        <div class="team-stat-label">
                            <i class="fa-solid fa-coins text-mint"></i> TỔNG ĐIỂM TÍCH LŨY
                        </div>
                        <div class="team-stat-val color-mint" id="profTotalPoints">
                            <%= totalAccumulatedPoints %><span class="pts-unit">pts</span>
                        </div>
                    </div>

                    <!-- 5. Thắng / Thua -->
                    <div class="team-stat-box box-green">
                        <div class="team-stat-label">
                            <i class="fa-solid fa-handshake text-mint"></i> THẮNG / THUA
                        </div>
                        <div class="team-stat-val color-green" id="profWinLoss">
                            <%= totalWins %>W - <%= totalLosses %>L
                        </div>
                    </div>

                    <!-- 6. Số Giải ĐÃ Đấu -->
                    <div class="team-stat-box">
                        <div class="team-stat-label">
                            <i class="fa-solid fa-trophy text-gold"></i> SỐ GIẢI ĐÃ ĐẤU
                        </div>
                        <div class="team-stat-val" id="profTourneysPlayed">
                            <%= totalTourneysPlayed %><span class="pts-unit" style="font-size: 0.95rem; font-weight: 700;">Giải</span>
                        </div>
                    </div>
                </div>

                <!-- 4 Achievement Cards Row -->
                <div class="team-stat-grid-row2">
                    <!-- 1. Số Lần Vô Địch -->
                    <div class="team-stat-box">
                        <div class="team-stat-label">
                            <i class="fa-solid fa-trophy text-gold"></i> SỐ LẦN VÔ ĐỊCH
                        </div>
                        <div class="team-stat-val color-gold" id="profChampCount">
                            <%= champCount %><span class="pts-unit" style="font-size: 0.95rem; font-weight: 700; color: #ffffff;">Lần</span>
                        </div>
                    </div>

                    <!-- 2. Số Lần Á Quân -->
                    <div class="team-stat-box">
                        <div class="team-stat-label">
                            <i class="fa-solid fa-award" style="color: #cbd5e1;"></i> SỐ LẦN Á QUÂN
                        </div>
                        <div class="team-stat-val" id="profRunnerUpCount" style="color: #cbd5e1;">
                            <%= runnerUpCount %><span class="pts-unit" style="font-size: 0.95rem; font-weight: 700; color: #ffffff;">Lần</span>
                        </div>
                    </div>

                    <!-- 3. Số Lần Vào Bán Kết -->
                    <div class="team-stat-box">
                        <div class="team-stat-label">
                            <i class="fa-solid fa-medal" style="color: #f97316;"></i> SỐ LẦN VÀO BÁN KẾT
                        </div>
                        <div class="team-stat-val" id="profSemiCount" style="color: #f97316;">
                            <%= semiCount %><span class="pts-unit" style="font-size: 0.95rem; font-weight: 700; color: #ffffff;">Lần</span>
                        </div>
                    </div>

                    <!-- 4. Số Lần Vào Tứ Kết -->
                    <div class="team-stat-box">
                        <div class="team-stat-label">
                            <i class="fa-solid fa-shield-halved text-mint"></i> SỐ LẦN VÀO TỨ KẾT
                        </div>
                        <div class="team-stat-val color-mint" id="profQuarterCount">
                            <%= quarterCount %><span class="pts-unit" style="font-size: 0.95rem; font-weight: 700; color: #ffffff;">Lần</span>
                        </div>
                    </div>
                </div>

                <!-- Champion Badges (Only show if team has won at least 1 championship) -->
                <div class="team-champion-section" id="profChampionSection" style="<%= (champCount > 0) ? "" : "display: none;" %>">
                    <div class="team-champion-title">
                        <i class="fa-solid fa-trophy"></i> CÁC GIẢI ĐẤU ĐÃ VÔ ĐỊCH
                    </div>
                    <div class="team-champion-badges-flex" id="profChampionBadges">
                        <% if (champCount > 0 && championTourneys != null) { 
                            for (ChampionTournamentDTO ct : championTourneys) { 
                                String badgeUrl = (ct.getFinalStageUrl() != null && !ct.getFinalStageUrl().isEmpty()) ? ct.getFinalStageUrl() : "#";
                        %>
                            <a href="<%= badgeUrl %>" class="champion-badge-pill" style="text-decoration: none; cursor: pointer;" title="Xem giai đoạn cuối giải <%= ct.getTournamentName() %>">
                                <i class="fa-solid fa-crown"></i> <%= ct.getTournamentName() %> <span class="tier-tag">[<%= ct.getTierName() %>]</span>
                            </a>
                        <% } 
                        } %>
                    </div>
                </div>

            </div>

            <!-- Tournament Performance Table Card -->
            <div class="team-performance-card">
                <div class="team-performance-header">
                    <h3 class="team-performance-title">
                        <i class="fa-solid fa-list-ol text-mint"></i> Thành Tích Qua Các Giải
                    </h3>
                </div>

                <table class="team-performance-table">
                    <thead>
                        <tr>
                            <th style="width: 50px;">STT</th>
                            <th>Tên Giải Đấu</th>
                            <th>Thể Thức</th>
                            <th>Thành Tích</th>
                            <th style="text-align: right;">Điểm Nhận Được</th>
                        </tr>
                    </thead>
                    <tbody id="profPerformanceTbody">
                        <% if (performanceList != null && !performanceList.isEmpty()) { 
                            for (int idx = 0; idx < performanceList.size(); idx++) {
                                TourneyPerformanceDTO perf = performanceList.get(idx);
                                String achClass = "muted";
                                if ("Vô Địch".equalsIgnoreCase(perf.getAchievement())) achClass = "champ";
                                else if ("Á Quân".equalsIgnoreCase(perf.getAchievement())) achClass = "runner-up";
                                else if ("Bán Kết".equalsIgnoreCase(perf.getAchievement())) achClass = "semi";
                                else if ("Tứ Kết".equalsIgnoreCase(perf.getAchievement())) achClass = "quarter";
                        %>
                            <tr>
                                <td style="font-weight: 700; color: var(--text-muted);"><%= (perf.getStt() > 0) ? perf.getStt() : (performanceList.size() - idx) %></td>
                                <td style="font-weight: 700; color: #ffffff;">
                                    <a href="<%= (perf.getFinalStageUrl() != null && !perf.getFinalStageUrl().isEmpty()) ? perf.getFinalStageUrl() : "#" %>" class="tourney-name-link" style="color: #ffffff; text-decoration: none; font-weight: 700; transition: color 0.18s ease;" onmouseover="this.style.color='#2dd4bf'" onmouseout="this.style.color='#ffffff'" title="Xem giai đoạn cuối giải <%= perf.getTournamentName() %>">
                                        <%= perf.getTournamentName() %>
                                    </a>
                                </td>
                                <td style="color: var(--text-muted); font-size: 0.82rem;">
                                    <%= perf.getFormatLabel() %>
                                </td>
                                <td>
                                    <span class="achievement-text <%= achClass %>">
                                        <%= perf.getAchievement() %>
                                    </span>
                                </td>
                                <td style="text-align: right; font-weight: 800; color: #fbbf24;">
                                    +<%= perf.getPointsEarned() %> pts
                                </td>
                            </tr>
                        <% } 
                        } else { %>
                            <tr>
                                <td colspan="5" style="text-align: center; padding: 2.5rem; color: var(--text-muted);">
                                    Chưa có dữ liệu thành tích thi đấu giải con nào trong Series này.
                                </td>
                            </tr>
                        <% } %>
                    </tbody>
                </table>
            </div>

        </main>

        <script>
            window.appContextPath = "${pageContext.request.contextPath}";
            window.seriesIdVal = "<%= seriesIdVal %>";
            window.profileTeamName = "<%= teamName.replace("\\", "\\\\").replace("\"", "\\\"") %>";
            window.seriesPhaseSize = <%= (series != null && series.getPhaseSize() > 0) ? series.getPhaseSize() : 3 %>;
            window.seriesPartners = [
                <% 
                List<PartnerParticipant> partnersList = (List<PartnerParticipant>) request.getAttribute("partnersList");
                if (partnersList != null) {
                    for (int i = 0; i < partnersList.size(); i++) {
                        PartnerParticipant p = partnersList.get(i);
                        String pName = (p != null && p.getName() != null) ? p.getName().replace("\\", "\\\\").replace("\"", "\\\"") : "";
                        String pId = (p != null && p.getId() != null) ? p.getId() : "";
                %>
                    { id: "<%= pId %>", name: "<%= pName %>" }<%= (i < partnersList.size() - 1) ? "," : "" %>
                <% 
                    }
                } 
                %>
            ];
            window.seriesSubTournaments = [
                <% 
                List<Tournament> tournamentsList = (List<Tournament>) request.getAttribute("tournamentsList");
                Map<String, List<String>> stageFormatsMap = (Map<String, List<String>>) request.getAttribute("stageFormatsMap");
                if (tournamentsList != null) {
                    for (int i = 0; i < tournamentsList.size(); i++) {
                        Tournament t = tournamentsList.get(i);
                        String rawCfg = (t != null) ? t.getSeriesPointsConfig() : null;
                        String cfgJson = (rawCfg != null && rawCfg.trim().startsWith("{") && rawCfg.trim().endsWith("}")) 
                            ? rawCfg.trim() : "{\"1\":500,\"2\":200,\"3-4\":100,\"5-8\":0}";
                        String safeName = (t != null && t.getName() != null) ? t.getName().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "") : "";
                        String safeId = (t != null && t.getId() != null) ? t.getId() : "";
                        String tier = (t != null && t.getTierName() != null) ? t.getTierName().toUpperCase() : "A";
                        int tIdx = (t != null && t.getTournamentIndexInSeries() > 0) ? t.getTournamentIndexInSeries() : (i + 1);
                        boolean isMulti = (t != null && "MULTI_STAGE".equalsIgnoreCase(t.getTournamentType()));
                        List<String> stgFormats = (stageFormatsMap != null) ? stageFormatsMap.get(safeId) : null;
                        String s1Fmt = (stgFormats != null && !stgFormats.isEmpty()) ? stgFormats.get(0) : (t != null ? t.getFormat() : "");
                        String s2Fmt = (stgFormats != null && stgFormats.size() > 1) ? stgFormats.get(1) : "SINGLE_ELIMINATION";
                %>
                    {
                        id: "<%= safeId %>",
                        name: "<%= safeName %>",
                        tierName: "<%= tier %>",
                        index: <%= tIdx %>,
                        format: "<%= (t != null && t.getFormat() != null) ? t.getFormat().toUpperCase() : "" %>",
                        isMultiStage: <%= isMulti %>,
                        tournamentType: "<%= (t != null && t.getTournamentType() != null) ? t.getTournamentType().toUpperCase() : "" %>",
                        stage1Format: "<%= (s1Fmt != null) ? s1Fmt.toUpperCase() : "" %>",
                        stage2Format: "<%= (s2Fmt != null) ? s2Fmt.toUpperCase() : "" %>",
                        pointsConfig: <%= cfgJson %>
                    }<%= (i < tournamentsList.size() - 1) ? "," : "" %>
                <% 
                    }
                } 
                %>
            ];
        </script>
        <script src="${pageContext.request.contextPath}/js/team-profile.js?v=<%= System.currentTimeMillis() %>"></script>
    </body>
</html>
