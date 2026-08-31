<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List"%>
<%@page import="model.Team"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%
    List<Team> dbTeamsList = (List<Team>) request.getAttribute("dbTeamsList");
    String stageParam = request.getParameter("stage");
    int currentStage = (stageParam != null && "2".equals(stageParam.trim())) ? 2 : 1;
    String activeStepVal = (currentStage == 2) ? "stage2" : "stage1";
    String safeTourneyId = request.getParameter("id");
    if (safeTourneyId == null || safeTourneyId.trim().isEmpty()) {
        safeTourneyId = "";
    }
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>${tournament.name} - Thể Thức Swiss System - TOURMA</title>

        <!-- Google Font Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">

        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

        <!-- Shared System Stylesheets -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-viewport.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-card.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/match-card.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/final-stage-popup.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/empty-team-alert.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/swiss-stage.css">
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

        <!-- Sidebar Component -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="activeStep" value="<%= activeStepVal %>"/>
            <jsp:param name="id" value="${not empty param.id ? param.id : (tournament != null ? tournament.id : '')}"/>
        </jsp:include>

        <!-- Main Content Area Shifted Right by Sidebar -->
        <main class="container has-sidebar">

            <!-- Control Bar (Title, Badges, View Mode Toggle, Quick Mode, Random, Reset Button) -->
            <div class="swiss-control-bar">
                <div class="tournament-info-badge-group">
                    <h1 class="tournament-name-title">
                        <i class="fa-solid fa-diagram-project text-mint"></i> ${tournament.name}
                    </h1>
                    <span class="format-badge-swiss"><%= (currentStage == 2) ? "STAGE 2: SWISS SYSTEM" : "SWISS SYSTEM" %></span>
                    <span id="swissTeamCountBadge" class="team-count-badge">16 Đội</span>
                    <span class="qualified-count-badge"><i class="fa-solid fa-trophy"></i> 8 Đội Đi Tiếp</span>
                </div>

                <!-- Right Action Bar: Quick Mode + Random + Reset + View Mode Toggles + Configure Button -->
                <div style="display: flex; align-items: center; gap: 0.65rem; flex-wrap: wrap;">
                    <!-- Quick Mode Toggle Button -->
                    <button type="button" id="btnSwissQuickMode" class="btn-quick-mode-toggle" onclick="toggleSwissQuickMode()" title="Chế độ phân định thắng thua nhanh (1-click chọn đội thắng)">
                        <i class="fa-solid fa-bolt"></i> Quick Mode: <span id="quickModeText">OFF</span>
                    </button>

                    <!-- Standalone Reset Button -->
                    <button type="button" id="btnSwissReset" class="btn" onclick="resetSwissMatches()" title="Xóa toàn bộ tỷ số và làm mới giai đoạn Swiss" style="background: rgba(244, 63, 94, 0.14); color: #f43f5e; border: 1px solid rgba(244, 63, 94, 0.35); height: 36px; padding: 0 0.85rem; font-size: 0.8rem; font-weight: 700; border-radius: 8px; cursor: pointer; display: inline-flex; align-items: center; gap: 0.4rem; transition: all 0.2s ease;">
                        <i class="fa-solid fa-rotate-right"></i> Reset
                    </button>

                    <div class="swiss-view-mode-toggle-group">
                        <button type="button" id="btnSwissViewList" class="btn-toggle-view" onclick="switchSwissViewMode('LIST')">
                            <i class="fa-solid fa-list-ul"></i> Dạng Danh Sách
                        </button>
                        <button type="button" id="btnSwissViewBracket" class="btn-toggle-view active" onclick="switchSwissViewMode('BRACKET')">
                            <i class="fa-solid fa-sitemap"></i> Sơ Đồ Bracket
                        </button>
                    </div>

                    <a href="${pageContext.request.contextPath}/common/configure-tournament-format.jsp?id=${not empty tournament.id ? tournament.id : param.id}" class="btn" style="background: rgba(255, 255, 255, 0.06); color: #e2e8f0; border: 1px solid rgba(255, 255, 255, 0.15); height: 36px; padding: 0 0.85rem; font-size: 0.8rem; border-radius: 8px;">
                        <i class="fa-solid fa-sliders"></i> Cấu Hình Giải
                    </a>
                </div>
            </div>

            <!-- SWISS TEAM COUNT ALERT BANNER (When team count != 16) -->
            <div id="swissInvalidTeamAlert" style="display: none; background: rgba(18, 22, 32, 0.85); backdrop-filter: blur(12px); border: 1px solid rgba(244, 63, 94, 0.35); border-radius: 14px; padding: 3.5rem 2rem; text-align: center; margin-top: 1.5rem; margin-bottom: 2.5rem; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.45);">
                <div style="width: 76px; height: 76px; border-radius: 50%; background: rgba(244, 63, 94, 0.12); border: 1px solid rgba(244, 63, 94, 0.35); display: flex; align-items: center; justify-content: center; margin: 0 auto 1.25rem;">
                    <i class="fa-solid fa-users-slash" style="font-size: 2.3rem; color: #f43f5e;"></i>
                </div>
                <h2 style="font-size: 1.4rem; font-weight: 800; color: #ffffff; margin-bottom: 0.6rem;">
                    Yêu Cầu CHÍNH XÁC 16 Đội Cho Thể Thức Swiss System
                </h2>
                <p id="swissInvalidTeamDesc" style="font-size: 0.88rem; color: #94a3b8; max-width: 600px; margin: 0 auto 1.75rem; line-height: 1.6;">
                    Thể thức Swiss System đòi hỏi bắt buộc phải có đủ <strong>đúng 16 đội bóng</strong> tham gia để ghép cặp theo từng nhóm tỷ số (Record Pool) qua 5 vòng thi đấu.
                </p>
                <a id="btnGoToConfigureTeams" href="${pageContext.request.contextPath}/common/configure-tournament-teams.jsp?id=${not empty tournament.id ? tournament.id : param.id}" class="btn btn-mint" style="font-weight: 700; padding: 0.65rem 1.75rem; display: inline-flex; align-items: center; gap: 0.5rem; text-decoration: none; border-radius: 8px;">
                    <i class="fa-solid fa-user-plus"></i> Thêm / Điều Chỉnh Cho Đủ 16 Đội Ngay
                </a>
            </div>

            <!-- MAIN SWISS CONTENT WRAPPER -->
            <div id="swissMainContentWrapper">

                <!-- ════════════════════════════════════════════════════════════════ -->
                <!-- VIEW MODE 1: DẠNG DANH SÁCH (LIST VIEW)                          -->
                <!-- ════════════════════════════════════════════════════════════════ -->
                <div id="swissListView" style="display: none;">
                    <!-- Round Pills Filter Bar -->
                    <div class="swiss-round-pills-bar" id="swissListRoundPillsContainer">
                        <!-- Injected by swiss-stage.js -->
                    </div>

                    <!-- Fixtures Container -->
                    <div id="swissListFixturesContainer">
                        <!-- Injected by swiss-stage.js -->
                    </div>
                </div>

                <!-- ════════════════════════════════════════════════════════════════ -->
                <!-- VIEW MODE 2: SƠ ĐỒ BRACKET (BRACKET VIEWPORT CANVAS - NO LINES)  -->
                <!-- ════════════════════════════════════════════════════════════════ -->
                <div id="swissBracketView" style="display: block;">
                    <div class="bracket-viewport-frame">
                        
                        <!-- Floating Viewport Zoom Toolbar (Exact SE & DE Styling) -->
                        <div class="bracket-zoom-toolbar">
                            <button type="button" class="btn-zoom" onclick="TourmaViewport.zoomOut('swissViewportContainer')" title="Thu Nhỏ (Zoom Out)">
                                <i class="fa-solid fa-minus"></i>
                            </button>
                            <span id="swissZoomBadge" class="zoom-level-badge">100%</span>
                            <button type="button" class="btn-zoom" onclick="TourmaViewport.zoomIn('swissViewportContainer')" title="Phóng To (Zoom In)">
                                <i class="fa-solid fa-plus"></i>
                            </button>
                            <button type="button" class="btn-zoom" onclick="TourmaViewport.reset('swissViewportContainer')" title="Reset Chế Độ Xem">
                                <i class="fa-solid fa-rotate-right"></i>
                            </button>
                        </div>

                        <!-- Drag/Pan Viewport Container Box -->
                        <div id="swissViewportContainer" class="bracket-viewport-container">
                            <!-- Viewport Canvas (No SVG connector lines) -->
                            <div id="swissViewportCanvas" class="bracket-viewport-canvas" style="padding-top: 3.5rem;">
                                <!-- Injected Round Columns with Record Pool Cards -->
                            </div>
                        </div>

                    </div>
                </div>

            </div>

        </main>

        <!-- Shared Score Edit Popup Component -->
        <jsp:include page="/common/component/popup.jsp"/>

        <!-- Shared Core JS Engine Scripts -->
        <script>
            window.swissTournamentId = "${not empty tournament.id ? tournament.id : param.id}";
            window.swissContextPath = "${pageContext.request.contextPath}";
            window.serverTeams = [
                <% if (dbTeamsList != null && !dbTeamsList.isEmpty()) { 
                    for (int i = 0; i < dbTeamsList.size(); i++) {
                        Team tm = dbTeamsList.get(i);
                        String tName = tm.getRawName();
                        if (tName == null || tName.trim().isEmpty()) {
                            tName = tm.getNormalizedName();
                        }
                        if (tName == null) tName = "Đội " + (i + 1);
                        String nameEsc = tName.replace("\"", "\\\"").replace("\n", "").replace("\r", "");
                %>
                    { id: "<%= tm.getId() %>", name: "<%= nameEsc %>" }<%= (i < dbTeamsList.size() - 1) ? "," : "" %>
                <%  } 
                } %>
            ];
        </script>
        <script src="${pageContext.request.contextPath}/js/bracket-algorithm.js"></script>
        <script src="${pageContext.request.contextPath}/js/double-elimination-algorithm.js"></script>
        <script src="${pageContext.request.contextPath}/js/round-robin-algorithm.js"></script>
        <script src="${pageContext.request.contextPath}/js/final-stage-popup.js"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-card.js"></script>
        <script src="${pageContext.request.contextPath}/js/match-card.js"></script>
        <script src="${pageContext.request.contextPath}/js/popup.js"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-viewport.js"></script>
        <script src="${pageContext.request.contextPath}/js/empty-team-alert.js"></script>
        <script src="${pageContext.request.contextPath}/js/swiss-stage-algorithm.js"></script>
        <script src="${pageContext.request.contextPath}/js/swiss-stage.js"></script>
    </body>
</html>
