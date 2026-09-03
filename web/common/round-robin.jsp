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
    String tourneyName = "Giải Đấu Vòng Tròn Tính Điểm";
    String teamsJson = "[]";
    String stageParam = request.getParameter("stage");
    int currentStage = (stageParam != null && "2".equals(stageParam.trim())) ? 2 : 1;
    String activeStepVal = (currentStage == 2) ? "stage2" : "stage1";
    int cutTarget = 0;
    String tournamentType = "SINGLE_STAGE";

    if (tourneyId != null && !tourneyId.trim().isEmpty()) {
        try {
                TournamentDAO tDao = new TournamentDAO();
                Tournament t = tDao.getTournamentById(tourneyId);
                if (t != null) {
                    if (t.getName() != null && !t.getName().trim().isEmpty()) {
                        tourneyName = t.getName();
                    }
                    if (t.getTournamentType() != null && !t.getTournamentType().trim().isEmpty()) {
                        tournamentType = t.getTournamentType();
                    }
                    if ("MULTI_STAGE".equals(tournamentType) && currentStage == 1) {
                        cutTarget = t.getAdvancingSeatsCount();
                    }
                }
            ParticipantDAO pDao = new ParticipantDAO();
            List<Team> plist = pDao.getTeamsByTournamentId(tourneyId);
            if (plist != null && !plist.isEmpty()) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < plist.size(); i++) {
                    if (i > 0) sb.append(",");
                    Team tm = plist.get(i);
                    String tName = tm != null ? tm.getName() : null;
                    if (tName == null || tName.trim().isEmpty()) {
                        tName = tm != null ? tm.getRawName() : null;
                    }
                    if (tName == null || tName.trim().isEmpty()) {
                        tName = "Đội #" + (i + 1);
                    }
                    sb.append("\"").append(tName.replace("\"", "\\\"")).append("\"");
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
        <!-- Favicon -->
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <link rel="alternate icon" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title><%= tourneyName %> - Round Robin - Tourma</title>
        
        <!-- Google Font Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        
        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Main Stylesheets -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-card.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-viewport.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/match-card.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/round-robin.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/final-stage-popup.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/stage-end-popup.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/empty-team-alert.css">
    </head>
    <body>
        <!-- Empty Team Alert Component -->
        <jsp:include page="/common/component/empty-team-alert.jsp"/>

        <!-- Final Stage Popup Banner -->
        <jsp:include page="/common/component/final-stage-popup.jsp"/>

        <!-- Stage End Popup Component -->
        <jsp:include page="/common/component/stage-end-popup.jsp"/>

        <!-- Stage Finish Alert Component (Locked Stage 2) -->
        <jsp:include page="/common/component/stage-finish-alert.jsp"/>

        <!-- Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="tournaments"/>
        </jsp:include>

        <!-- Sidebar Component -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="activeStep" value="<%= activeStepVal %>"/>
            <jsp:param name="format" value="ROUND_ROBIN"/>
            <jsp:param name="id" value="<%= safeTourneyId %>"/>
        </jsp:include>

        <!-- Main Content Area -->
        <main class="container has-sidebar round-robin-container">
            
            <!-- Top Control Bar -->
            <div class="rr-control-bar">
                <div class="rr-info-group">
                    <h1 class="rr-tourney-title">
                        <i class="fa-solid fa-trophy text-gold"></i> 
                        <span><%= tourneyName %></span>
                    </h1>
                    <span class="format-badge-rr"><%= (currentStage == 2) ? "Stage 2: Round Robin" : "Round Robin" %></span>
                    <span id="tournamentTeamCountBadge" class="team-count-badge">0 Đội</span>
                    <span id="rrAdvancingBadge" class="format-badge-pill" style="display: <%= (currentStage == 1 && cutTarget > 1) ? "inline-flex" : "none" %>; background: rgba(45, 212, 191, 0.15); border: 1px solid rgba(45, 212, 191, 0.35); color: #2dd4bf; border-radius: 9999px; font-weight: 800; font-size: 0.8rem; padding: 0.25rem 0.75rem; align-items: center; gap: 0.45rem;">
                        <i class="fa-solid fa-trophy" style="color: #2dd4bf; font-size: 0.85rem;"></i>
                        <span id="rrAdvancingText"><%= cutTarget %> Đội đi tiếp</span>
                    </span>
                </div>

                <div class="rr-actions-group" style="display: flex; align-items: center; gap: 0.75rem;">
                    <!-- Standalone Reset Bracket / Tournament Action Button -->
                    <button type="button" class="btn-reset-bracket-action" onclick="window.TourmaRoundRobin.openResetModal()" title="Xóa toàn bộ kết quả và thiết lập lại từ đầu">
                        <i class="fa-solid fa-rotate-right"></i> Reset Giải
                    </button>

                    <!-- View Mode Segmented Toggle Buttons (Fixtures List ↔ Standings Table) -->
                    <div class="view-mode-toggle-group">
                        <a href="${pageContext.request.contextPath}/common/round-robin.jsp?id=<%= safeTourneyId %>&format=ROUND_ROBIN<%= (currentStage == 2) ? "&stage=2" : "" %>" 
                           class="btn-view-toggle active" style="text-decoration: none;">
                            <i class="fa-solid fa-calendar-days"></i> Lịch Thi Đấu
                        </a>
                        <a href="${pageContext.request.contextPath}/common/round-robin-standings.jsp?id=<%= safeTourneyId %>&format=ROUND_ROBIN<%= (currentStage == 2) ? "&stage=2" : "" %>" 
                           class="btn-view-toggle" style="text-decoration: none;">
                            <i class="fa-solid fa-ranking-star"></i> Bảng Xếp Hạng
                        </a>
                    </div>
                </div>
            </div>

            <!-- Horizontal Round Selector Bar (Pill Tabs) -->
            <div id="rrRoundSelectorBar" class="rr-round-selector-bar">
                <!-- Injected via JavaScript -->
            </div>

            <!-- Fixtures Match Grid Container -->
            <div id="rrFixturesContainer" class="rr-fixtures-container">
                <!-- Dynamic Round Sections Injected by JS -->
            </div>

            <!-- Confirmation Modal for Reset Tournament -->
            <div id="rrResetModalBackdrop" class="tourma-modal-backdrop" onclick="if(event.target === this) window.TourmaRoundRobin.closeResetModal();">
                <div class="tourma-modal-card" style="max-width: 480px; border-color: rgba(244, 63, 94, 0.4);" onclick="event.stopPropagation();">
                    <div class="modal-header-bar" style="border-bottom: 1px solid rgba(244, 63, 94, 0.2);">
                        <div class="modal-header-title" style="color: #f43f5e; font-size: 0.95rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem;">
                            <i class="fa-solid fa-rotate-right"></i>
                            <span>Xác Nhận Reset Toàn Bộ Giải</span>
                        </div>
                        <button type="button" class="modal-close-btn" onclick="window.TourmaRoundRobin.closeResetModal()" title="Đóng">
                            <i class="fa-solid fa-xmark"></i>
                        </button>
                    </div>

                    <div class="modal-body-content" style="padding: 1.25rem 1rem;">
                        <div style="background: rgba(244, 63, 94, 0.08); border: 1px solid rgba(244, 63, 94, 0.2); border-radius: 8px; padding: 0.85rem; margin-bottom: 1rem; color: #cbd5e1; font-size: 0.82rem; line-height: 1.5;">
                            <strong style="color: #f43f5e;">⚠️ Cảnh báo quan trọng:</strong><br>
                            Hành động này sẽ <strong style="color: #ffffff;">XÓA TOÀN BỘ tỷ số và kết quả của tất cả các vòng</strong>, đưa Bảng Xếp Hạng về 0 điểm ban đầu.
                        </div>
                        <p style="color: #94a3b8; font-size: 0.8rem; margin: 0;">
                            Bạn có chắc chắn muốn thiết lập lại toàn bộ giải đấu không?
                        </p>
                    </div>

                    <div class="modal-footer-bar" style="display: flex; justify-content: flex-end; gap: 0.65rem; padding: 0.75rem 1rem;">
                        <button type="button" class="btn btn-secondary" onclick="window.TourmaRoundRobin.closeResetModal()" style="font-size: 0.8rem; padding: 0.45rem 1rem;">Hủy Bỏ</button>
                        <button type="button" class="btn" style="background: #f43f5e; color: #ffffff; border: none; font-size: 0.8rem; font-weight: 700; padding: 0.45rem 1.25rem; border-radius: 6px; cursor: pointer;" onclick="window.TourmaRoundRobin.confirmResetTournament()">
                            <i class="fa-solid fa-rotate-right"></i> Xác Nhận Reset
                        </button>
                    </div>
                </div>
            </div>

        </main>

        <!-- Score Edit Popup Component -->
        <jsp:include page="/common/component/popup.jsp"/>

        <!-- Scripts -->
        <script src="${pageContext.request.contextPath}/js/bracket-algorithm.js"></script>
        <script src="${pageContext.request.contextPath}/js/double-elimination-algorithm.js"></script>
        <script src="${pageContext.request.contextPath}/js/round-robin-algorithm.js"></script>
        <script src="${pageContext.request.contextPath}/js/match-card.js"></script>
        <script src="${pageContext.request.contextPath}/js/popup.js"></script>
        <script src="${pageContext.request.contextPath}/js/final-stage-popup.js"></script>
        <script src="${pageContext.request.contextPath}/js/empty-team-alert.js"></script>
        <script src="${pageContext.request.contextPath}/js/round-robin.js"></script>

        <script>
            window.addEventListener('DOMContentLoaded', function () {
                var tourneyId = "<%= (tourneyId != null && !tourneyId.trim().isEmpty()) ? tourneyId : "demo" %>";
                var tType = "<%= tournamentType %>";
                try { localStorage.setItem('tourma_type_' + tourneyId, tType); } catch(e) {}
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
                window.TourmaRoundRobin.init(tourneyId, null, preloadedTeams, currentStage, cutTarget);
            });
        </script>
    </body>
</html>
