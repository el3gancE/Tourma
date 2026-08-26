<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@page import="dao.TournamentDAO"%>
<%@page import="model.Tournament"%>
<%
    String tourneyId = request.getParameter("id");
    String tourneyName = "Giải Đấu Single Elimination";
    if (tourneyId != null && !tourneyId.trim().isEmpty()) {
        try {
            TournamentDAO tDao = new TournamentDAO();
            Tournament t = tDao.getTournamentById(tourneyId);
            if (t != null && t.getName() != null && !t.getName().trim().isEmpty()) {
                tourneyName = t.getName();
            }
        } catch (Exception e) {}
    }
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Sơ Đồ Thi Đấu Single Elimination - TOURMA</title>
        
        <!-- Google Font Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        
        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Main Stylesheets -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-viewport.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-card.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/match-card.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/single-elimination.css">
    </head>
    <body>
        <!-- Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="tournaments"/>
        </jsp:include>

        <!-- Sidebar Component (Step 4: Vòng Đấu) -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="activeStep" value="bracket"/>
            <jsp:param name="id" value="${not empty param.id ? param.id : (tournament != null ? tournament.id : '')}"/>
        </jsp:include>

        <!-- Main Content Area Shifted Right by Sidebar -->
        <main class="container has-sidebar">
            
            <!-- Top Control Bar (Tournament Title, Format Badge, Team Count Badge & View Mode Toggle) -->
            <div class="single-elimination-control-bar">
                <div class="tournament-info-badge-group">
                    <h1 class="tournament-name-title">
                        <i class="fa-solid fa-trophy text-gold"></i> 
                        <span id="tournamentNameDisplay"><%= tourneyName %></span>
                    </h1>
                    <span class="format-badge-single">Single Elimination</span>
                    <span id="tournamentTeamCountBadge" class="team-count-badge">0 Đội</span>
                </div>

                <!-- View Mode Toggle Buttons (Bracket ↔ List View) -->
                <div class="view-mode-toggle-group">
                    <button type="button" id="btnViewBracket" class="btn-view-toggle active" onclick="window.SingleEliminationEngine.switchViewMode('bracket')">
                        <i class="fa-solid fa-diagram-project"></i> Sơ Đồ Cây
                    </button>
                    <button type="button" id="btnViewList" class="btn-view-toggle" onclick="window.SingleEliminationEngine.switchViewMode('list')">
                        <i class="fa-solid fa-list-ol"></i> Danh Sách Trận
                    </button>
                </div>
            </div>

            <!-- MODE 1: BRACKET TREE VIEW (Inside Drag-to-Pan Viewport Frame) -->
            <div id="bracketViewportFrame" class="bracket-viewport-frame">
                
                <!-- Floating Zoom Toolbar (Fixed at Top-Right Corner of Viewport Frame) -->
                <div class="bracket-zoom-toolbar">
                    <button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.zoomOut()" title="Thu nhỏ (-)">
                        <i class="fa-solid fa-minus"></i>
                    </button>
                    <span id="zoomLevelBadge" class="zoom-level-badge">100%</span>
                    <button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.zoomIn()" title="Phóng to (+)">
                        <i class="fa-solid fa-plus"></i>
                    </button>
                    <button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.resetZoom()" title="Reset (100%)">
                        <i class="fa-solid fa-rotate-right"></i>
                    </button>
                </div>

                <!-- Scrollable Canvas Viewport Container -->
                <div id="bracketViewportContainer" class="bracket-viewport-container">
                    <!-- Inner Canvas Wrapper (Dynamic Bracket Columns Injected Here) -->
                    <div id="bracketViewportCanvas" class="bracket-viewport-canvas">
                        <div id="singleBracketColumnsWrapper" class="single-bracket-columns-wrapper">
                            <!-- Dynamic Round Columns rendered by JavaScript Engine -->
                        </div>
                    </div>
                </div>

            </div>

            <!-- MODE 2: MATCHES LIST VIEW (Dynamic List Cards Injected Here) -->
            <div id="singleListViewContainer" class="single-list-view-container">
                <!-- Dynamic List Cards rendered by JavaScript Engine -->
            </div>

        </main>

        <!-- Score Edit Popup Component -->
        <jsp:include page="/common/component/popup.jsp"/>

        <script src="${pageContext.request.contextPath}/js/bracket-algorithm.js"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-card.js"></script>
        <script src="${pageContext.request.contextPath}/js/match-card.js"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-viewport.js"></script>
        <script src="${pageContext.request.contextPath}/js/single-elimination.js"></script>

        <script>
            window.addEventListener('DOMContentLoaded', function () {
                var tourneyId = "${not empty param.id ? param.id : (tournament != null ? tournament.id : 'demo')}";
                window.SingleEliminationEngine.init(tourneyId);
            });
        </script>
    </body>
</html>
