<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@page import="dao.TournamentDAO"%>
<%@page import="dao.ParticipantDAO"%>
<%@page import="model.Tournament"%>
<%@page import="model.Team"%>
<%@page import="java.util.List"%>
<%
    String tourneyId = request.getParameter("id");
    String safeTourneyId = (tourneyId != null) ? tourneyId : "";
    String tourneyName = "Bảng Xếp Hạng Giải Đấu";
    String teamsJson = "[]";
    if (tourneyId != null && !tourneyId.trim().isEmpty()) {
        try {
            TournamentDAO tDao = new TournamentDAO();
            Tournament t = tDao.getTournamentById(tourneyId);
            if (t != null && t.getName() != null && !t.getName().trim().isEmpty()) {
                tourneyName = t.getName();
            }
            ParticipantDAO pDao = new ParticipantDAO();
            List<Team> plist = pDao.getTeamsByTournamentId(tourneyId);
            if (plist != null && !plist.isEmpty()) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < plist.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(plist.get(i).getRawName().replace("\"", "\\\"")).append("\"");
                }
                sb.append("]");
                teamsJson = sb.toString();
            }
        } catch (Exception e) {}
    }
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Bảng Xếp Hạng Round Robin - TOURMA</title>
        
        <!-- Google Font Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        
        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Main Stylesheets -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/round-robin.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/round-robin-standings.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/final-stage-popup.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/empty-team-alert.css">
    </head>
    <body>
        <!-- Empty Team Alert Component -->
        <jsp:include page="/common/component/empty-team-alert.jsp"/>

        <!-- Final Stage Popup Banner -->
        <jsp:include page="/common/component/final-stage-popup.jsp"/>

        <!-- Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="tournaments"/>
        </jsp:include>

        <!-- Sidebar Component (Active: standings) -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="activeStep" value="standings"/>
            <jsp:param name="format" value="ROUND_ROBIN"/>
            <jsp:param name="id" value="<%= safeTourneyId %>"/>
        </jsp:include>

        <!-- Main Content Area -->
        <main class="container has-sidebar round-robin-container">
            
            <!-- Top Control Bar -->
            <div class="rr-control-bar">
                <div class="rr-info-group">
                    <h1 class="rr-tourney-title">
                        <i class="fa-solid fa-ranking-star text-gold"></i> 
                        <span>Bảng Xếp Hạng: <%= tourneyName %></span>
                    </h1>
                    <span class="format-badge-rr">Round Robin</span>
                    <span id="tournamentTeamCountBadge" class="team-count-badge">0 Đội</span>
                </div>

                <div class="rr-actions-group" style="display: flex; align-items: center; gap: 0.75rem;">
                    <!-- View Mode Segmented Toggle Buttons (Fixtures List ↔ Standings Table) -->
                    <div class="view-mode-toggle-group">
                        <a href="${pageContext.request.contextPath}/common/round-robin.jsp?id=<%= safeTourneyId %>&format=ROUND_ROBIN" 
                           class="btn-view-toggle" style="text-decoration: none;">
                            <i class="fa-solid fa-calendar-days"></i> Lịch Thi Đấu
                        </a>
                        <a href="${pageContext.request.contextPath}/common/round-robin-standings.jsp?id=<%= safeTourneyId %>&format=ROUND_ROBIN" 
                           class="btn-view-toggle active" style="text-decoration: none;">
                            <i class="fa-solid fa-ranking-star"></i> Bảng Xếp Hạng
                        </a>
                    </div>
                </div>
            </div>

            <!-- Standings Table Card -->
            <div class="rr-standings-card">
                <div class="rr-standings-header-bar">
                    <div class="rr-standings-title">
                        <i class="fa-solid fa-chart-simple"></i>
                        <span>Bảng Xếp Hạng Trực Tiếp</span>
                    </div>
                    <div style="font-size: 0.76rem; color: #94a3b8;">
                        <span style="margin-right: 0.85rem;"><strong style="color:#fbbf24;">Thắng:</strong> 3đ</span>
                        <span style="margin-right: 0.85rem;"><strong style="color:#facc15;">Hòa:</strong> 1đ</span>
                        <span><strong style="color:#f87171;">Thua:</strong> 0đ</span>
                    </div>
                </div>

                <div class="rr-standings-table-wrapper">
                    <table class="rr-table">
                        <thead>
                            <tr>
                                <th style="width: 50px;">Hạng</th>
                                <th>Đội Tuyển</th>
                                <th style="text-align: center; width: 60px;">Trận</th>
                                <th style="text-align: center; width: 50px;">T</th>
                                <th style="text-align: center; width: 50px;">H</th>
                                <th style="text-align: center; width: 50px;">B</th>
                                <th style="text-align: center; width: 50px;">BT</th>
                                <th style="text-align: center; width: 50px;">BB</th>
                                <th style="text-align: center; width: 60px;">HS</th>
                                <th style="text-align: center; width: 60px;">Điểm</th>
                                <th style="width: 140px;">5 Trận Gần Nhất</th>
                            </tr>
                        </thead>
                        <tbody id="rrStandingsTableBody">
                            <!-- Dynamic Table Rows Rendered by JS Engine -->
                        </tbody>
                    </table>
                </div>
            </div>

        </main>

        <!-- Scripts -->
        <script src="${pageContext.request.contextPath}/js/round-robin-algorithm.js"></script>
        <script src="${pageContext.request.contextPath}/js/final-stage-popup.js"></script>
        <script src="${pageContext.request.contextPath}/js/empty-team-alert.js"></script>
        <script src="${pageContext.request.contextPath}/js/round-robin-standings.js"></script>

        <script>
            window.addEventListener('DOMContentLoaded', function () {
                var tourneyId = "<%= (tourneyId != null && !tourneyId.trim().isEmpty()) ? tourneyId : "demo" %>";
                var preloadedTeams = <%= teamsJson %>;
                window.TourmaRoundRobinStandings.init(tourneyId, preloadedTeams);
            });
        </script>
    </body>
</html>
