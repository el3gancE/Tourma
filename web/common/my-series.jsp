<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <!-- Favicon -->
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <link rel="alternate icon" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Chuỗi Giải Của Tôi - TOURMA</title>
        
        <!-- Google Font Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        
        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Main Design System CSS & Dedicated My Series CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/my-series.css">
    </head>
    <body>
        <!-- Include Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-series"/>
        </jsp:include>

        <main class="container">
            
            <!-- Page Header Row -->
            <div class="page-header-row">
                <div>
                    <h1 class="page-title">
                        <i class="fa-solid fa-layer-group text-gold"></i> Chuỗi Giải Của Tôi
                    </h1>
                    <p class="text-muted" style="font-size: 0.85rem; margin-top: 0.2rem;">
                        Danh sách các Series mùa giải tích lũy điểm Rolling Window hoặc biến thiên FIFA Elo Rating.
                    </p>
                </div>
                <div>
                    <a href="${pageContext.request.contextPath}/common/create-series.jsp" class="btn btn-gold-outline">
                        <i class="fa-solid fa-folder-plus"></i> Tạo Series Mới
                    </a>
                </div>
            </div>

            <!-- Toolbar & Filters -->
            <div class="toolbar-bar">
                <div class="filter-pills-group">
                    <button type="button" class="filter-pill-btn active" onclick="filterSeries('ALL', this)">Tất Cả</button>
                    <button type="button" class="filter-pill-btn" onclick="filterSeries('ROLLING_WINDOW', this)">Rolling Window</button>
                    <button type="button" class="filter-pill-btn" onclick="filterSeries('FIFA_ELO', this)">FIFA Elo Rating</button>
                </div>

                <div class="search-input-box">
                    <i class="fa-solid fa-magnifying-glass"></i>
                    <input type="text" id="searchInput" class="form-control" placeholder="Tìm kiếm tên Series..." onkeyup="searchSeries()">
                </div>
            </div>

            <!-- Series Cards Container -->
            <c:choose>
                <c:when test="${not empty seriesList}">
                    <div class="series-cards-grid" id="seriesGrid">
                        <c:forEach var="s" items="${seriesList}">
                            <div class="series-card" 
                                 data-model="${s.rankingModel}"
                                 data-name="${s.name}">
                                
                                <div class="series-card-main">
                                    <div class="series-card-top-row">
                                        <h3 class="series-card-title">${s.name}</h3>
                                        <span class="series-model-badge ${s.rankingModel == 'ROLLING_WINDOW' ? 'rolling' : 'elo'}">
                                            ${s.rankingModel == 'ROLLING_WINDOW' ? 'ROLLING WINDOW' : 'FIFA ELO'}
                                        </span>
                                        <span class="phase-pill">
                                            Phase ${s.currentPhase}
                                        </span>
                                    </div>

                                    <div class="series-card-meta">
                                        <c:choose>
                                            <c:when test="${s.rankingModel == 'ROLLING_WINDOW'}">
                                                <span><i class="fa-solid fa-chart-line text-gold"></i> Cửa sổ trượt ${s.phaseSize} giải / Phase</span>
                                                <span class="meta-divider">•</span>
                                                <span><i class="fa-solid fa-star text-gold"></i> Điểm khởi đầu: ${s.initialPoints} điểm</span>
                                            </c:when>
                                            <c:otherwise>
                                                <span><i class="fa-solid fa-ranking-star text-mint"></i> Rating Elo khởi điểm: ${s.initialElo} Elo</span>
                                                <span class="meta-divider">•</span>
                                                <span><i class="fa-solid fa-users text-muted"></i> Bảng xếp hạng chung & theo nhóm</span>
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </div>

                                <div class="series-card-footer">
                                    <c:choose>
                                        <c:when test="${s.rankingModel == 'ROLLING_WINDOW'}">
                                            <a href="${pageContext.request.contextPath}/rolling/dashboard?id=${s.id}" class="btn-view-series-card">
                                                <i class="fa-solid fa-chart-line"></i> Quản Lý & xem BXH ➔
                                            </a>
                                        </c:when>
                                        <c:otherwise>
                                            <a href="${pageContext.request.contextPath}/leaderboard.jsp?seriesId=${s.id}" class="btn-view-series-card">
                                                <i class="fa-solid fa-ranking-star"></i> Xem BXH ➔
                                            </a>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                            </div>
                        </c:forEach>
                    </div>
                </c:when>

                <c:otherwise>
                    <!-- Empty State Box when no series data exists -->
                    <div class="empty-state-box">
                        <div style="font-size: 2.5rem; color: var(--text-muted); margin-bottom: 0.75rem;">
                            <i class="fa-solid fa-folder-open"></i>
                        </div>
                        <h3 style="font-size: 1.1rem; font-weight: 700; margin-bottom: 0.4rem;">Chưa có chuỗi giải nào được tạo</h3>
                        <p class="text-muted" style="font-size: 0.82rem; margin-bottom: 1.25rem;">
                            Bạn chưa khởi tạo chuỗi giải Series nào. Hãy bắt đầu cấu hình Series đầu tiên.
                        </p>
                        <a href="${pageContext.request.contextPath}/common/create-series.jsp" class="btn btn-gold-outline">
                            <i class="fa-solid fa-folder-plus"></i> Tạo Series Mới
                        </a>
                    </div>
                </c:otherwise>
            </c:choose>

        </main>

        <script>
            function filterSeries(modelType, btn) {
                document.querySelectorAll('.filter-pill-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');

                const cards = document.querySelectorAll('.series-card');
                cards.forEach(card => {
                    const model = card.getAttribute('data-model');
                    
                    if (modelType === 'ALL') {
                        card.style.display = 'flex';
                    } else if (modelType === 'ROLLING_WINDOW' && model === 'ROLLING_WINDOW') {
                        card.style.display = 'flex';
                    } else if (modelType === 'FIFA_ELO' && model === 'FIFA_ELO') {
                        card.style.display = 'flex';
                    } else {
                        card.style.display = 'none';
                    }
                });
            }

            function searchSeries() {
                const query = document.getElementById('searchInput').value.toLowerCase();
                const cards = document.querySelectorAll('.series-card');
                cards.forEach(card => {
                    const name = card.getAttribute('data-name').toLowerCase();
                    card.style.display = name.includes(query) ? 'flex' : 'none';
                });
            }
        </script>
    </body>
</html>
