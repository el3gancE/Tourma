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
    String stageParam = request.getParameter("stage");
    int currentStage = (stageParam != null && "2".equals(stageParam.trim())) ? 2 : 1;
    String activeStepVal = (currentStage == 2) ? "standings2" : "standings1";
    String currentStageStr = (currentStage == 2) ? "2" : "1";
    int cutTarget = 0;

    if (tourneyId != null && !tourneyId.trim().isEmpty()) {
        try {
            TournamentDAO tDao = new TournamentDAO();
            Tournament t = tDao.getTournamentById(tourneyId);
            if (t != null) {
                if (t.getName() != null && !t.getName().trim().isEmpty()) {
                    tourneyName = t.getName();
                }
                if ("MULTI_STAGE".equals(t.getTournamentType()) && currentStage == 1) {
                    cutTarget = t.getAdvancingSeatsCount();
                }
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
            <jsp:param name="activeStep" value="<%= activeStepVal %>"/>
            <jsp:param name="format" value="ROUND_ROBIN"/>
            <jsp:param name="id" value="<%= safeTourneyId %>"/>
            <jsp:param name="stage" value="<%= currentStageStr %>"/>
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
                    <span class="format-badge-rr"><%= (currentStage == 2) ? "Stage 2: Round Robin" : "Round Robin" %></span>
                    <span id="tournamentTeamCountBadge" class="team-count-badge">0 Đội</span>
                    <span id="rrAdvancingBadge" class="format-badge-pill" style="display: <%= (currentStage == 1 && cutTarget > 1) ? "inline-flex" : "none" %>; background: rgba(45, 212, 191, 0.15); border: 1px solid rgba(45, 212, 191, 0.35); color: #2dd4bf; border-radius: 9999px; font-weight: 800; font-size: 0.8rem; padding: 0.25rem 0.75rem; align-items: center; gap: 0.45rem;">
                        <i class="fa-solid fa-trophy" style="color: #2dd4bf; font-size: 0.85rem;"></i>
                        <span id="rrAdvancingText"><%= cutTarget %> Đội đi tiếp</span>
                    </span>
                </div>

                <div class="rr-actions-group" style="display: flex; align-items: center; gap: 0.75rem;">
                    <!-- View Mode Segmented Toggle Buttons (Fixtures List ↔ Standings Table) -->
                    <div class="view-mode-toggle-group">
                        <a href="${pageContext.request.contextPath}/common/round-robin.jsp?id=<%= safeTourneyId %>&format=ROUND_ROBIN<%= (currentStage == 2) ? "&stage=2" : "" %>" 
                           class="btn-view-toggle" style="text-decoration: none;">
                            <i class="fa-solid fa-calendar-days"></i> Lịch Thi Đấu
                        </a>
                        <a href="${pageContext.request.contextPath}/common/round-robin-standings.jsp?id=<%= safeTourneyId %>&format=ROUND_ROBIN<%= (currentStage == 2) ? "&stage=2" : "" %>" 
                           class="btn-view-toggle active" style="text-decoration: none;">
                            <i class="fa-solid fa-ranking-star"></i> Bảng Xếp Hạng
                        </a>
                    </div>
                </div>
            </div>

            <!-- Standings Table Card -->
            <div class="rr-standings-card">
                <div class="rr-standings-header-bar">
                    <div class="rr-standings-title-left">
                        <i class="fa-solid fa-table-list text-mint"></i>
                        <span>Bảng Điểm Chi Tiết</span>
                    </div>
                    <div class="rr-points-rule-legend">
                        <span class="legend-item"><strong class="text-win">T</strong>: Thắng (3đ)</span>
                        <span class="legend-item"><strong class="text-draw">H</strong>: Hòa (1đ)</span>
                        <span class="legend-item"><strong class="text-loss">B</strong>: Bại (0đ)</span>
                    </div>
                </div>

                <div class="table-responsive">
                    <table class="rr-table">
                        <thead>
                            <tr>
                                <th class="th-rank">#</th>
                                <th class="th-team">Đội Tuyển</th>
                                <th class="th-num" title="Số trận đã đấu">Trận</th>
                                <th class="th-num" title="Thắng">T</th>
                                <th class="th-num" title="Hòa">H</th>
                                <th class="th-num" title="Bại">B</th>
                                <th class="th-num" title="Bàn thắng / Điểm ghi được">BT</th>
                                <th class="th-num" title="Bàn thua / Điểm bị ghi">BB</th>
                                <th class="th-num" title="Hiệu số (BT - BB)">HS</th>
                                <th class="th-pts" title="Tổng điểm">Điểm</th>
                                <th class="th-form" title="Phong độ 5 trận gần nhất">Phong Độ</th>
                            </tr>
                        </thead>
                        <tbody id="rrStandingsTableBody">
                            <!-- Dynamic Rows Rendered by JS -->
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
                var currentStage = <%= currentStage %>;
                var cutTarget = <%= cutTarget %>;

                if (currentStage === 2) {
                    var advCount = <%= cutTarget %>;
                    if (!advCount || advCount <= 1) {
                        try {
                            var multiCfg = JSON.parse(localStorage.getItem('tourma_multi_config_' + tourneyId));
                            if (multiCfg && multiCfg.stage1Config) {
                                advCount = multiCfg.stage1Config.advanceCount || multiCfg.stage1Config.totalAdvanceCount || 0;
                            }
                        } catch(e) {}
                    }
                    if (!advCount || advCount <= 1) {
                        try {
                            var rawAdv = localStorage.getItem('tourma_advance_count_' + tourneyId) || localStorage.getItem('tourma_cut_target_' + tourneyId);
                            if (rawAdv) advCount = parseInt(rawAdv, 10);
                        } catch(e) {}
                    }

                    var s2TeamsRaw = null;
                    try { s2TeamsRaw = JSON.parse(localStorage.getItem('tourma_stage2_teams_' + tourneyId)); } catch(e) {}
                    if (s2TeamsRaw && s2TeamsRaw.length > 0) {
                        preloadedTeams = s2TeamsRaw;
                    } else if (advCount && advCount > 1 && preloadedTeams && preloadedTeams.length > advCount) {
                        preloadedTeams = preloadedTeams.slice(0, advCount);
                    }
                    cutTarget = 0; // Stage 2 plays to find a champion!
                }
                window.TourmaRoundRobinStandings.init(tourneyId, preloadedTeams, null, currentStage, cutTarget);
            });
        </script>
    </body>
</html>
