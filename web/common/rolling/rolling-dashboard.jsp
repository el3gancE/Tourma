<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, model.SeriesStanding, model.PartnerParticipant, java.util.List"%>
<%
    Series series = (Series) request.getAttribute("series");
    List<Tournament> tournamentsList = (List<Tournament>) request.getAttribute("tournamentsList");
    List<SeriesStanding> standingsList = (List<SeriesStanding>) request.getAttribute("standingsList");
    List<PartnerParticipant> partnerList = (List<PartnerParticipant>) request.getAttribute("partnerList");
    int partnerCount = (partnerList != null) ? partnerList.size() : 0;

    String seriesIdVal = (series != null && series.getId() != null) ? series.getId() : "";
    String seriesName = (series != null) ? series.getName() : "VBA Pro League 2026 Circuit";
    int phaseSize = (series != null) ? series.getPhaseSize() : 10;
    int currentPhase = (series != null) ? series.getCurrentPhase() : 1;
    String status = (series != null) ? series.getStatus() : "ACTIVE";

    int tourneyCount = (tournamentsList != null) ? tournamentsList.size() : 0;
    int teamsCount = (standingsList != null) ? standingsList.size() : 0;

    String top1Team = (standingsList != null && !standingsList.isEmpty()) ? standingsList.get(0).getNormalizedTeamName() : "Đang cập nhật";
    String top2Team = (standingsList != null && standingsList.size() > 1) ? standingsList.get(1).getNormalizedTeamName() : "Đang cập nhật";
    String top3Team = (standingsList != null && standingsList.size() > 2) ? standingsList.get(2).getNormalizedTeamName() : "Đang cập nhật";

    int top1Pts = (standingsList != null && !standingsList.isEmpty()) ? standingsList.get(0).getTotalRollingPoints() : 0;
    int top2Pts = (standingsList != null && standingsList.size() > 1) ? standingsList.get(1).getTotalRollingPoints() : 0;
    int top3Pts = (standingsList != null && standingsList.size() > 2) ? standingsList.get(2).getTotalRollingPoints() : 0;
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title><%= seriesName %> - Dashboard Rolling Window Series</title>
        
        <!-- Google Fonts & FontAwesome -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Design System CSS & Dedicated Rolling Dashboard CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-dashboard.css">
    </head>
    <body>
        <!-- Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-series"/>
        </jsp:include>

        <!-- Dynamic 3-Mode Sidebar Component -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
            <jsp:param name="activeStep" value="dashboard"/>
        </jsp:include>

        <main class="container has-sidebar" style="max-width: 900px; padding: 1.5rem 1rem;">
            
            <!-- Breadcrumb Navigation -->
            <div style="margin-bottom: 1rem;">
                <a href="${pageContext.request.contextPath}/my-series" class="text-muted" style="font-size: 0.85rem; font-weight: 600; text-decoration: none;">
                    <i class="fa-solid fa-arrow-left"></i> Quay lại Chuỗi Giải Của Tôi
                </a>
            </div>

            <!-- Series Header Banner Card -->
            <div class="rolling-header-card">
                <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 1rem;">
                    <div>
                        <h1 class="rolling-series-name" style="font-size: 1.85rem; font-weight: 800; color: #ffffff; margin: 0 0 0.25rem 0;">
                            <%= seriesName %> - Dashboard
                            <span style="font-size: 0.8rem; padding: 0.25rem 0.65rem; border-radius: 6px; background: rgba(16, 185, 129, 0.15); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.3);">
                                <%= status %>
                            </span>
                        </h1>
                        <p class="text-muted" style="font-size: 0.85rem; margin-top: 0.25rem;">
                            Tích lũy điểm trượt trong <%= phaseSize %> giải đấu gần nhất. Tự động khấu trừ điểm hết hạn khi vượt cửa sổ trượt.
                        </p>
                    </div>
                </div>

                <!-- Quick Stats Row Grid -->
                <div class="rolling-quick-stats-grid">
                    <div class="rolling-stat-box">
                        <div class="rolling-stat-icon gold"><i class="fa-solid fa-trophy"></i></div>
                        <div>
                            <div class="rolling-stat-label">Cửa Sổ Trượt (W)</div>
                            <div class="rolling-stat-value"><%= phaseSize %> Giải</div>
                        </div>
                    </div>

                    <div class="rolling-stat-box">
                        <div class="rolling-stat-icon cyan"><i class="fa-solid fa-sitemap"></i></div>
                        <div>
                            <div class="rolling-stat-label">Số Giải Đã Diễn Ra</div>
                            <div class="rolling-stat-value"><%= tourneyCount %> / <%= phaseSize %></div>
                        </div>
                    </div>

                    <div class="rolling-stat-box">
                        <div class="rolling-stat-icon mint"><i class="fa-solid fa-users"></i></div>
                        <div>
                            <div class="rolling-stat-label">Tổng Số Đội Tích Điểm</div>
                            <div class="rolling-stat-value"><%= teamsCount %> Đội</div>
                        </div>
                    </div>

                    <div class="rolling-stat-box">
                        <div class="rolling-stat-icon purple"><i class="fa-solid fa-crown"></i></div>
                        <div>
                            <div class="rolling-stat-label">Đội Dẫn Đầu Series</div>
                            <div class="rolling-stat-value" style="font-size: 1.05rem;"><%= top1Team %></div>
                        </div>
                    </div>
                </div>
            </div>

        </main>

        <script src="${pageContext.request.contextPath}/js/rolling/rolling-dashboard.js"></script>
    </body>
</html>
