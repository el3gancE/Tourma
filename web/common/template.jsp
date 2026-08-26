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
                        Mẫu linh kiện giao diện Match Card (Bracket & List View) và Pop Up Edit Tỷ Số. Click vào bất kỳ thẻ trận đấu nào để dùng thử Pop up!
                    </p>
                </div>
            </div>

            <!-- SECTION 1: MATCH CARD DẠNG BRACKET -->
            <section class="template-section">
                <h2 class="template-section-title">
                    <i class="fa-solid fa-diagram-project"></i> 1. Match Card Dạng Bracket (Tree Node Component)
                </h2>
                
                <div class="template-bracket-grid">
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
    </body>
</html>
