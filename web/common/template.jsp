<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Template UI Components - TOURMA</title>
        
        <!-- Google Font Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        
        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Main Design System & Template Showcase CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/template.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-viewport.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-card.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/match-card.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css">
    </head>
    <body>
        <!-- Include Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="template"/>
        </jsp:include>

        <!-- Include Shared Fixed Left Sidebar Component -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="activeStep" value="format"/>
            <jsp:param name="id" value="${param.id}"/>
        </jsp:include>

        <!-- Main Content Area Shifted Right by Sidebar -->
        <main class="container has-sidebar">
            
            <!-- Hero Title Bar -->
            <div class="template-hero-bar">
                <div>
                    <h1 class="template-title">
                        <i class="fa-solid fa-palette text-gold"></i> Thư Viện Template UI Component
                    </h1>
                    <p class="template-subtitle">
                        Mẫu linh kiện giao diện Match Card (Bracket & List View), Pop Up Edit Tỷ Số và Khung Khái Quát Viewport Kéo Thả.
                    </p>
                </div>
            </div>

            <!-- SECTION 1: MATCH CARD DẠNG BRACKET TRONG KHUNG VIEWPORT KÉO THẢ -->
            <section class="template-section">
                <h2 class="template-section-title">
                    <i class="fa-solid fa-up-down-left-right"></i> 1. Khung Viewport Sơ Đồ Cây Kéo Thả (Bracket Viewport Canvas)
                </h2>
                
                <div id="bracketViewportContainer" class="bracket-viewport-container">
                    <!-- Zoom Toolbar -->
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

                    <!-- Drag Hint -->
                    <div class="bracket-drag-hint">
                        <i class="fa-solid fa-hand-pointer text-mint"></i> Nhấn giữ chuột và kéo để di chuyển sơ đồ
                    </div>

                    <!-- Inner Expandable Canvas -->
                    <div id="bracketViewportCanvas" class="bracket-viewport-canvas">
                        <!-- Column 1: Vòng Tứ Kết -->
                        <div class="bracket-column-box">
                            <div class="bracket-col-title">Vòng Tứ Kết</div>
                            
                            <jsp:include page="/common/component/bracket-card.jsp">
                                <jsp:param name="matchId" value="1"/>
                                <jsp:param name="matchNumber" value="1"/>
                                <jsp:param name="team1Name" value="Hà Nội FC"/>
                                <jsp:param name="team1Seed" value="1"/>
                                <jsp:param name="team1Score" value="3"/>
                                <jsp:param name="team2Name" value="Hải Phòng FC"/>
                                <jsp:param name="team2Seed" value="8"/>
                                <jsp:param name="team2Score" value="1"/>
                                <jsp:param name="winner" value="team1"/>
                                <jsp:param name="status" value="done"/>
                            </jsp:include>

                            <jsp:include page="/common/component/bracket-card.jsp">
                                <jsp:param name="matchId" value="2"/>
                                <jsp:param name="matchNumber" value="2"/>
                                <jsp:param name="team1Name" value="Saigon Heat"/>
                                <jsp:param name="team1Seed" value="4"/>
                                <jsp:param name="team1Score" value="0"/>
                                <jsp:param name="team2Name" value="SHB Đà Nẵng"/>
                                <jsp:param name="team2Seed" value="5"/>
                                <jsp:param name="team2Score" value="2"/>
                                <jsp:param name="winner" value="team2"/>
                                <jsp:param name="status" value="done"/>
                            </jsp:include>
                        </div>

                        <!-- Column 2: Vòng Bán Kết -->
                        <div class="bracket-column-box">
                            <div class="bracket-col-title">Vòng Bán Kết</div>
                            
                            <jsp:include page="/common/component/bracket-card.jsp">
                                <jsp:param name="matchId" value="3"/>
                                <jsp:param name="matchNumber" value="3"/>
                                <jsp:param name="team1Name" value="Hà Nội FC"/>
                                <jsp:param name="team1Seed" value="1"/>
                                <jsp:param name="team1Score" value="2"/>
                                <jsp:param name="team2Name" value="SHB Đà Nẵng"/>
                                <jsp:param name="team2Seed" value="5"/>
                                <jsp:param name="team2Score" value="1"/>
                                <jsp:param name="winner" value="team1"/>
                                <jsp:param name="status" value="done"/>
                            </jsp:include>
                        </div>

                        <!-- Column 3: Chung Kết -->
                        <div class="bracket-column-box">
                            <div class="bracket-col-title">Chung Kết</div>
                            
                            <jsp:include page="/common/component/bracket-card.jsp">
                                <jsp:param name="matchId" value="4"/>
                                <jsp:param name="matchNumber" value="4"/>
                                <jsp:param name="team1Name" value="W #3"/>
                                <jsp:param name="team1Seed" value="1"/>
                                <jsp:param name="team1Score" value="0"/>
                                <jsp:param name="team2Name" value="L #2"/>
                                <jsp:param name="team2Seed" value="2"/>
                                <jsp:param name="team2Score" value="0"/>
                                <jsp:param name="winner" value="none"/>
                                <jsp:param name="status" value="pending"/>
                            </jsp:include>
                        </div>
                    </div>
                </div>
            </section>

            <!-- SECTION 2: MATCH CARD DẠNG LIST -->
            <section class="template-section">
                <h2 class="template-section-title">
                    <i class="fa-solid fa-list-ol"></i> 2. Match Card Dạng List (List View Component)
                </h2>

                <div class="template-match-list">
                    <jsp:include page="/common/component/match-card.jsp">
                        <jsp:param name="matchId" value="1"/>
                        <jsp:param name="matchNumber" value="1"/>
                        <jsp:param name="team1Name" value="Hà Nội FC"/>
                        <jsp:param name="team1Seed" value="1"/>
                        <jsp:param name="team1Score" value="3"/>
                        <jsp:param name="team2Name" value="Hải Phòng FC"/>
                        <jsp:param name="team2Seed" value="8"/>
                        <jsp:param name="team2Score" value="1"/>
                        <jsp:param name="winner" value="team1"/>
                        <jsp:param name="status" value="done"/>
                    </jsp:include>

                    <jsp:include page="/common/component/match-card.jsp">
                        <jsp:param name="matchId" value="2"/>
                        <jsp:param name="matchNumber" value="2"/>
                        <jsp:param name="team1Name" value="Saigon Heat"/>
                        <jsp:param name="team1Seed" value="4"/>
                        <jsp:param name="team1Score" value="2"/>
                        <jsp:param name="team2Name" value="SHB Đà Nẵng"/>
                        <jsp:param name="team2Seed" value="5"/>
                        <jsp:param name="team2Score" value="1"/>
                        <jsp:param name="winner" value="team1"/>
                        <jsp:param name="status" value="done"/>
                    </jsp:include>

                    <jsp:include page="/common/component/match-card.jsp">
                        <jsp:param name="matchId" value="3"/>
                        <jsp:param name="matchNumber" value="3"/>
                        <jsp:param name="team1Name" value="Becamex Bình Dương"/>
                        <jsp:param name="team1Seed" value="2"/>
                        <jsp:param name="team1Score" value="0"/>
                        <jsp:param name="team2Name" value="Hoàng Anh Gia Lai"/>
                        <jsp:param name="team2Seed" value="7"/>
                        <jsp:param name="team2Score" value="0"/>
                        <jsp:param name="winner" value="none"/>
                        <jsp:param name="status" value="pending"/>
                    </jsp:include>
                </div>
            </section>

        </main>

        <!-- Include Shared Score Edit Popup Component -->
        <jsp:include page="/common/component/popup.jsp"/>

        <script src="${pageContext.request.contextPath}/js/bracket-viewport.js"></script>
        <script>
            // Build a minimal matchesMap from the demo card relationships
            var templateMatchesMap = {
                '1': { matchId: '1', nextMatchId: '3', nextMatchSlot: 1, status: 'done' },
                '2': { matchId: '2', nextMatchId: '3', nextMatchSlot: 2, status: 'done' },
                '3': { matchId: '3', nextMatchId: '4', nextMatchSlot: 1, status: 'SCHEDULED' },
                '4': { matchId: '4', nextMatchId: null, nextMatchSlot: null, status: 'SCHEDULED' }
            };

            function drawTemplateSvgLines() {
                var canvas = document.getElementById('bracketViewportCanvas');
                var wrapper = canvas; // cards are direct children of canvas in template
                if (!canvas || !window.TourmaViewport) return;
                var scale = window.TourmaViewport.currentScale || 1;
                window.TourmaViewport.drawConnectors(canvas, canvas, templateMatchesMap, scale);
            }

            window.addEventListener('DOMContentLoaded', function () {
                requestAnimationFrame(drawTemplateSvgLines);
                setTimeout(drawTemplateSvgLines, 60);
                setTimeout(drawTemplateSvgLines, 250);
            });
            window.addEventListener('resize', drawTemplateSvgLines);
        </script>
    </body>
</html>
