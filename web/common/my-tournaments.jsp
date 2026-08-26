<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Giải Đấu Của Tôi - TOURMA</title>
        
        <!-- Google Font Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        
        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Main Design System CSS & Dedicated My Tournaments CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/my-tournaments.css">
    </head>
    <body>
        <!-- Include Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-tournaments"/>
        </jsp:include>

        <main class="container">
            
            <!-- Page Header Row -->
            <div class="page-header-row">
                <div>
                    <h1 class="page-title">
                        <i class="fa-solid fa-sitemap text-mint"></i> Giải Đấu Của Tôi
                    </h1>
                    <p class="text-muted" style="font-size: 0.85rem; margin-top: 0.2rem;">
                        Danh sách các giải đấu đơn lẻ và giải đấu thuộc Series đang quản lý.
                    </p>
                </div>
                <div>
                    <a href="${pageContext.request.contextPath}/common/create-tournament.jsp" class="btn btn-mint">
                        <i class="fa-solid fa-plus"></i> Tạo Giải Đấu Mới
                    </a>
                </div>
            </div>

            <!-- Toolbar & Filters -->
            <div class="toolbar-bar">
                <div class="filter-pills-group">
                    <button type="button" class="filter-pill-btn active" onclick="filterTournaments('ALL', this)">Tất Cả</button>
                    <button type="button" class="filter-pill-btn" onclick="filterTournaments('STANDALONE', this)">Giải Đơn Lẻ</button>
                    <button type="button" class="filter-pill-btn" onclick="filterTournaments('SERIES', this)">Thuộc Series</button>
                    <button type="button" class="filter-pill-btn" onclick="filterTournaments('ONGOING', this)">Đang Diễn Ra</button>
                    <button type="button" class="filter-pill-btn" onclick="filterTournaments('COMPLETED', this)">Đã Hoàn Thành</button>
                </div>

                <div class="search-input-box">
                    <i class="fa-solid fa-magnifying-glass"></i>
                    <input type="text" id="searchInput" class="form-control" placeholder="Tìm kiếm tên giải..." onkeyup="searchTournaments()">
                </div>
            </div>

            <!-- Tournament Cards Container -->
            <c:choose>
                <c:when test="${not empty tournamentList}">
                    <div class="tourney-cards-grid" id="tourneyGrid">
                        <c:forEach var="t" items="${tournamentList}">
                            <div class="tourney-card" 
                                 data-context="${empty t.seriesId ? 'STANDALONE' : 'SERIES'}"
                                 data-status="${t.status}"
                                 data-name="${t.name}">
                                
                                <div>
                                    <div class="tourney-card-header">
                                        <div style="display: flex; gap: 0.4rem; align-items: center;">
                                            <span class="tourney-badge-type">
                                                ${t.tournamentType == 'MULTI_STAGE' ? 'MULTI STAGE' : 'SINGLE STAGE'}
                                            </span>
                                            <c:if test="${not empty t.tierName}">
                                                <span class="tourney-badge-tier">TIER ${t.tierName}</span>
                                            </c:if>
                                        </div>

                                        <span class="status-pill ${t.status == 'COMPLETED' ? 'completed' : (t.status == 'ONGOING' ? 'ongoing' : 'draft')}">
                                            ${t.status == 'COMPLETED' ? 'Hoàn Thành' : (t.status == 'ONGOING' ? 'Đang Đấu' : 'Bản Nháp')}
                                        </span>
                                    </div>

                                    <h3 class="tourney-card-title">${t.name}</h3>

                                    <div class="tourney-card-meta">
                                        <c:choose>
                                            <c:when test="${not empty t.seriesId}">
                                                <span><i class="fa-solid fa-layer-group text-gold"></i> Thuộc Series Mùa Giải</span>
                                                <span><i class="fa-solid fa-flag"></i> Giải thứ ${t.tournamentIndexInSeries} - Phase ${t.phaseNumber}</span>
                                            </c:when>
                                            <c:otherwise>
                                                <span><i class="fa-solid fa-flag text-mint"></i> Giải Đơn Lẻ</span>
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </div>

                                <div class="tourney-card-footer">
                                    <span class="text-muted" style="font-size: 0.72rem;">ID: ${t.id}</span>
                                    <div style="display: flex; gap: 0.5rem; align-items: center;">
                                        <a href="${pageContext.request.contextPath}/common/configure-tournament-format.jsp?id=${t.id}" class="btn btn-secondary" style="padding: 0.3rem 0.75rem; font-size: 0.75rem;">
                                            <i class="fa-solid fa-sliders"></i> Chi Tiết
                                        </a>
                                        <a href="${pageContext.request.contextPath}/common/single-elimination.jsp?id=${t.id}" class="btn btn-mint" style="padding: 0.3rem 0.75rem; font-size: 0.75rem;">
                                            Xem Sơ Đồ ➔
                                        </a>
                                    </div>
                                </div>
                            </div>
                        </c:forEach>
                    </div>
                </c:when>

                <c:otherwise>
                    <!-- Empty State Box when no tournament data exists -->
                    <div class="empty-state-box">
                        <div style="font-size: 2.5rem; color: var(--text-muted); margin-bottom: 0.75rem;">
                            <i class="fa-solid fa-folder-open"></i>
                        </div>
                        <h3 style="font-size: 1.1rem; font-weight: 700; margin-bottom: 0.4rem;">Chưa có giải đấu nào được tạo</h3>
                        <p class="text-muted" style="font-size: 0.82rem; margin-bottom: 1.25rem;">
                            Bạn chưa tạo giải đấu nào trong hệ thống. Hãy bắt đầu khởi tạo giải đấu đầu tiên ngay.
                        </p>
                        <a href="${pageContext.request.contextPath}/common/create-tournament.jsp" class="btn btn-mint">
                            <i class="fa-solid fa-plus"></i> Tạo Giải Đấu Mới
                        </a>
                    </div>
                </c:otherwise>
            </c:choose>

        </main>

        <script>
            function filterTournaments(filterType, btn) {
                document.querySelectorAll('.filter-pill-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');

                const cards = document.querySelectorAll('.tourney-card');
                cards.forEach(card => {
                    const ctx = card.getAttribute('data-context');
                    const status = card.getAttribute('data-status');
                    
                    if (filterType === 'ALL') {
                        card.style.display = 'flex';
                    } else if (filterType === 'STANDALONE' && ctx === 'STANDALONE') {
                        card.style.display = 'flex';
                    } else if (filterType === 'SERIES' && ctx === 'SERIES') {
                        card.style.display = 'flex';
                    } else if (filterType === 'ONGOING' && status === 'ONGOING') {
                        card.style.display = 'flex';
                    } else if (filterType === 'COMPLETED' && status === 'COMPLETED') {
                        card.style.display = 'flex';
                    } else {
                        card.style.display = 'none';
                    }
                });
            }

            function searchTournaments() {
                const query = document.getElementById('searchInput').value.toLowerCase();
                const cards = document.querySelectorAll('.tourney-card');
                cards.forEach(card => {
                    const name = card.getAttribute('data-name').toLowerCase();
                    card.style.display = name.includes(query) ? 'flex' : 'none';
                });
            }
        </script>
    </body>
</html>
