<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html lang="vi" data-theme="light">
    <head>
        <!-- Favicon -->
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <link rel="alternate icon" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Giải Đấu Của Tôi - TOURMA</title>

        <!-- Google Font Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">

        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

        <script src="${pageContext.request.contextPath}/js/theme.js"></script>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=10-11">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=2">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/workspace.css?v=1">
        <!-- Main Design System CSS & Dedicated My Tournaments CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/my-tournaments.css?v=20261028-9">
    </head>
    <body class="ui-page workspace-page tournaments-page">
        <!-- Include Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-tournaments"/>
            <jsp:param name="themeToggle" value="true"/>
        </jsp:include>

        <main class="container workspace-main">
            <div class="workspace-utility"><span>KHÔNG GIAN QUẢN LÝ / GIẢI ĐẤU</span></div>

            <!-- Page Header Row -->
            <div class="page-header-row">
                <div>
                    <h1 class="page-title">
                        <i class="fa-solid fa-sitemap text-mint"></i> Giải Đấu Của Tôi
                    </h1>
                    <p class="text-muted" data-ui-style="my-tournaments-jsp-001">
                        Tất cả cuộc đấu của bạn, trong cùng một không gian.
                    </p>
                </div>
                <div>
                    <a href="${pageContext.request.contextPath}/common/create-tournament.jsp" class="btn btn-mint">
                        <i class="fa-solid fa-plus"></i> Tạo Giải Đấu Mới
                    </a>
                </div>
            </div>

            <!-- Toolbar & Filters -->
            <form id="tournamentFilters" class="toolbar-bar" method="get" action="${pageContext.request.contextPath}/my-tournaments">
                <input type="hidden" name="filter" id="tournamentFilterInput" value="<c:out value="${pageInfo.filter}" />">
                <div class="filter-pills-group">
                    <button type="button" class="filter-pill-btn ${pageInfo.filter == 'ALL' ? 'active' : ''}" onclick="filterTournaments('ALL', this)">Tất Cả</button>
                    <button type="button" class="filter-pill-btn ${pageInfo.filter == 'STANDALONE' ? 'active' : ''}" onclick="filterTournaments('STANDALONE', this)">Giải Đơn Lẻ</button>
                    <button type="button" class="filter-pill-btn ${pageInfo.filter == 'SERIES' ? 'active' : ''}" onclick="filterTournaments('SERIES', this)">Thuộc Series</button>
                    <button type="button" class="filter-pill-btn ${pageInfo.filter == 'INCOMING' ? 'active' : ''}" onclick="filterTournaments('INCOMING', this)">Sắp diễn ra</button>
                    <button type="button" class="filter-pill-btn ${pageInfo.filter == 'IN_PROGRESS' ? 'active' : ''}" onclick="filterTournaments('IN_PROGRESS', this)">Đang diễn ra</button>
                    <button type="button" class="filter-pill-btn ${pageInfo.filter == 'COMPLETED' ? 'active' : ''}" onclick="filterTournaments('COMPLETED', this)">Đã hoàn thành</button>
                </div>

                <div class="search-input-box">
                    <i class="fa-solid fa-magnifying-glass"></i>
                    <input type="text" aria-label="Tìm kiếm giải đấu" id="searchInput" class="form-control" name="q" value="<c:out value="${pageInfo.query}" />" placeholder="Tìm theo tên giải đấu…">
                    <button type="submit" class="tourney-search-submit">Tìm</button>
                </div>
            </form>

            <!-- Tournament Cards Container -->
            <c:choose>
                <c:when test="${not empty tournamentList}">
                    <div class="tourney-cards-grid" id="tourneyGrid">
                        <c:forEach var="t" items="${tournamentList}">
                            <article class="tourney-card"
                                 data-id="${t.id}"
                                 data-db-status="${t.status}"
                                 data-db-champion="${t.championName}"
                                 data-stage-type="${t.tournamentType}"
                                 data-context="${empty t.seriesId ? 'STANDALONE' : 'SERIES'}"
                                 data-team-count="${t.teamCount}"
                                 data-status="${t.status == 'COMPLETED' ? 'COMPLETED' : (t.status == 'ONGOING' || t.status == 'IN_PROGRESS' ? 'IN_PROGRESS' : 'INCOMING')}"
                                 data-name="${t.name}">

                                <div class="tourney-card-info">
                                    <h3 class="tourney-card-title" title="<c:out value="${t.name}" />"><c:out value="${t.name}" /></h3>
                                    <p class="tourney-info-line">
                                        <span id="teamMeta_${t.id}" class="team-count-val">${t.teamCount > 0 ? t.teamCount : 0} đội</span>
                                        <span aria-hidden="true">·</span>
                                        <span class="tourney-format-span"><c:out value="${t.formatSummary}" /></span>
                                    </p>
                                    <p class="tourney-series-line"><c:if test="${not empty t.seriesId}"><c:out value="${seriesNames[t.seriesId]}" /></c:if></p>
                                    <p id="championMeta_${t.id}" class="tourney-champion-meta"><c:if test="${not empty t.championName}"><span class="champion-name-val"><c:out value="${t.championName}" /></span></c:if></p>
                                </div>
                                <div class="tourney-card-tools">
                                    <div class="tourney-progress" aria-label="Tiến độ giải đấu">
                                        <div class="tourney-progress-track" role="progressbar" aria-label="Tiến độ giải đấu" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${t.progressPercent}">
                                            <span class="tourney-progress-fill" style="width: ${t.progressPercent}%"></span>
                                        </div>
                                        <span class="tourney-progress-percent">${t.progressPercent}%</span>
                                    </div>
                                    <button type="button" class="btn-delete-tourney"
                                            data-tourney-id="<c:out value="${t.id}" />" data-tourney-name="<c:out value="${t.name}" />"
                                            onclick="openDeleteTourneyModal(this.dataset.tourneyId, this.dataset.tourneyName)"
                                            title="Xóa giải đấu" aria-label="Xóa giải đấu">
                                        <i class="fa-solid fa-trash-can" aria-hidden="true"></i>
                                    </button>
                                    <a href="${pageContext.request.contextPath}/my-tournaments?action=matches&amp;id=${t.id}" class="btn-view-bracket-card">Trận đấu <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></a>
                                </div>
                            </article>
                        </c:forEach>
                    </div>
                </c:when>

                <c:otherwise>
                    <!-- Empty State Box when no tournament data exists -->
                    <div class="empty-state-box">
                        <div data-ui-style="my-tournaments-jsp-004">
                            <i class="fa-solid fa-folder-open"></i>
                        </div>
                        <h3 data-ui-style="my-tournaments-jsp-005">${not empty pageInfo.query || pageInfo.filter != 'ALL' ? 'Không có giải đấu phù hợp' : 'Chưa có giải đấu nào được tạo'}</h3>
                        <p class="text-muted" data-ui-style="my-tournaments-jsp-006">
                            ${not empty pageInfo.query || pageInfo.filter != 'ALL' ? 'Thử đổi từ khóa hoặc chọn bộ lọc khác.' : 'Hãy bắt đầu khởi tạo giải đấu đầu tiên.'}
                        </p>
                        <a href="${pageContext.request.contextPath}/common/create-tournament.jsp" class="btn btn-mint">
                            <i class="fa-solid fa-plus"></i> Tạo Giải Đấu Mới
                        </a>
                    </div>
                </c:otherwise>
            </c:choose>

            <c:if test="${not empty pageInfo && pageInfo.totalCount > 0}">
                <nav class="tourney-pagination" aria-label="Phân trang giải đấu">
                    <span class="tourney-page-summary">${(pageInfo.page - 1) * 25 + 1}–${pageInfo.page * 25 < pageInfo.totalCount ? pageInfo.page * 25 : pageInfo.totalCount} / ${pageInfo.totalCount} giải</span>
                    <div class="tourney-page-buttons">
                        <c:choose>
                            <c:when test="${pageInfo.page > 1}">
                                <c:url var="previousPageUrl" value="/my-tournaments"><c:param name="page" value="${pageInfo.page - 1}" /><c:param name="q" value="${pageInfo.query}" /><c:param name="filter" value="${pageInfo.filter}" /></c:url>
                                <a href="<c:out value='${previousPageUrl}' />" class="tourney-page-btn" rel="prev">‹ Trước</a>
                            </c:when>
                            <c:otherwise><span class="tourney-page-btn is-disabled" aria-disabled="true">‹ Trước</span></c:otherwise>
                        </c:choose>
                        <c:forEach var="pageNumber" begin="${pageInfo.page > 2 ? pageInfo.page - 2 : 1}" end="${pageInfo.page + 2 < pageInfo.totalPages ? pageInfo.page + 2 : pageInfo.totalPages}">
                            <c:choose>
                                <c:when test="${pageNumber == pageInfo.page}"><span class="tourney-page-btn is-current" aria-current="page">${pageNumber}</span></c:when>
                                <c:otherwise>
                                    <c:url var="numberedPageUrl" value="/my-tournaments"><c:param name="page" value="${pageNumber}" /><c:param name="q" value="${pageInfo.query}" /><c:param name="filter" value="${pageInfo.filter}" /></c:url>
                                    <a href="<c:out value='${numberedPageUrl}' />" class="tourney-page-btn" aria-label="Trang ${pageNumber}">${pageNumber}</a>
                                </c:otherwise>
                            </c:choose>
                        </c:forEach>
                        <c:choose>
                            <c:when test="${pageInfo.page < pageInfo.totalPages}">
                                <c:url var="nextPageUrl" value="/my-tournaments"><c:param name="page" value="${pageInfo.page + 1}" /><c:param name="q" value="${pageInfo.query}" /><c:param name="filter" value="${pageInfo.filter}" /></c:url>
                                <a href="<c:out value='${nextPageUrl}' />" class="tourney-page-btn" rel="next">Sau ›</a>
                            </c:when>
                            <c:otherwise><span class="tourney-page-btn is-disabled" aria-disabled="true">Sau ›</span></c:otherwise>
                        </c:choose>
                        <span class="tourney-page-total">Trang ${pageInfo.page}/${pageInfo.totalPages}</span>
                    </div>
                </nav>
            </c:if>
            <!-- DELETE TOURNAMENT CONFIRMATION MODAL -->
            <div id="deleteTourneyModalBackdrop" class="tourma-modal-backdrop" style="display: none;" onclick="if(event.target === this) closeDeleteTourneyModal();">
                <div class="tourma-modal-card" data-ui-style="my-tournaments-jsp-007" onclick="event.stopPropagation();">
                    <div class="modal-header-bar" data-ui-style="my-tournaments-jsp-008">
                        <div class="modal-header-title" data-ui-style="my-tournaments-jsp-009" style="display: flex;">
                            <i class="fa-solid fa-trash-can"></i>
                            <span>Xác Nhận Xóa Giải Đấu</span>
                        </div>
                        <button type="button" class="modal-close-btn" onclick="closeDeleteTourneyModal()" title="Đóng">
                            <i class="fa-solid fa-xmark"></i>
                        </button>
                    </div>

                    <div class="modal-body-content" data-ui-style="my-tournaments-jsp-010">
                        <div data-ui-style="my-tournaments-jsp-011">
                            <strong data-ui-style="my-tournaments-jsp-012">⚠️ Cảnh báo xóa vĩnh viễn:</strong><br>
                            Hành động này sẽ <strong data-ui-style="my-tournaments-jsp-013">xóa hoàn toàn giải đấu</strong> cùng toàn bộ danh sách đội tuyển, cấu hình thể thức và kết quả các trận đấu liên quan.
                        </div>
                        <p data-ui-style="my-tournaments-jsp-014">
                            Bạn có chắc chắn muốn xóa giải: <strong id="deleteTourneyTargetName" data-ui-style="my-tournaments-jsp-015"></strong> (ID: <span id="deleteTourneyTargetId" class="text-muted"></span>)?
                        </p>
                    </div>

                    <div class="modal-footer-bar" data-ui-style="my-tournaments-jsp-016" style="display: flex;">
                        <button type="button" class="btn btn-secondary" onclick="closeDeleteTourneyModal()" data-ui-style="my-tournaments-jsp-017">Hủy Bỏ</button>
                        <button type="button" class="btn" data-ui-style="my-tournaments-jsp-018" onclick="confirmDeleteTourney()">
                            <i class="fa-solid fa-trash-can"></i> Xác Nhận Xóa
                        </button>
                    </div>
                </div>
            </div>

            <!-- CLONE TOURNAMENT CONFIRMATION MODAL -->
            <div id="cloneTourneyModalBackdrop" class="tourma-modal-backdrop" style="display: none;" onclick="if(event.target === this) closeCloneTourneyModal();">
                <div class="tourma-modal-card" data-ui-style="my-tournaments-jsp-019" onclick="event.stopPropagation();">
                    <div class="modal-header-bar" data-ui-style="my-tournaments-jsp-020">
                        <div class="modal-header-title" data-ui-style="my-tournaments-jsp-021" style="display: flex;">
                            <i class="fa-solid fa-copy"></i>
                            <span>Sao Chép Cấu Hình Giải Đấu</span>
                        </div>
                        <button type="button" class="modal-close-btn" onclick="closeCloneTourneyModal()" title="Đóng">
                            <i class="fa-solid fa-xmark"></i>
                        </button>
                    </div>

                    <form id="cloneTourneyForm" method="POST" action="${pageContext.request.contextPath}/clone-tournament">
                        <input type="hidden" name="sourceTournamentId" id="cloneSourceTournamentId" value="">
                        <input type="hidden" name="redirectContext" id="cloneRedirectContext" value="standalone">

                        <div class="modal-body-content" data-ui-style="my-tournaments-jsp-022">
                            <div data-ui-style="my-tournaments-jsp-023">
                                <i class="fa-solid fa-circle-info text-mint"></i>
                                Hệ thống sẽ tạo một giải đấu mới ở trạng thái <strong>DRAFT</strong> với toàn bộ cấu hình thể thức, phân bảng, multi-stage từ giải gốc.
                            </div>

                            <div data-ui-style="my-tournaments-jsp-024">
                                <label data-ui-style="my-tournaments-jsp-025" style="display: block;">
                                    Tên giải đấu mới:
                                </label>
                                <input type="text" id="cloneNewTourneyName" name="newName" class="form-control" required data-ui-style="my-tournaments-jsp-026" placeholder="Nhập tên giải mới...">
                            </div>

                            <div data-ui-style="my-tournaments-jsp-027" style="display: flex;">
                                <i class="fa-solid fa-users text-mint" data-ui-style="my-tournaments-jsp-028"></i>
                                <div data-ui-style="my-tournaments-jsp-029">
                                    <strong>Ràng buộc số lượng:</strong> Danh sách đội sẽ để trống để bạn nhập mới ở bước tiếp theo, và <strong>bắt buộc phải khớp đúng số lượng đội</strong> của giải gốc.
                                </div>
                            </div>
                        </div>

                        <div class="modal-footer-bar" data-ui-style="my-tournaments-jsp-030" style="display: flex;">
                            <button type="button" class="btn btn-secondary" onclick="closeCloneTourneyModal()" data-ui-style="my-tournaments-jsp-031">Hủy Bỏ</button>
                            <button type="submit" class="btn btn-mint" data-ui-style="my-tournaments-jsp-032" style="display: inline-flex;">
                                <i class="fa-solid fa-copy"></i> Tạo Bản Sao
                            </button>
                        </div>
                    </form>
                </div>
            </div>

        </main>

        <script>
            let pendingDeleteId = null;

            function filterTournaments(filterType) {
                document.getElementById('tournamentFilterInput').value = filterType;
                document.getElementById('tournamentFilters').requestSubmit();
            }
            function openDeleteTourneyModal(id, name) {
                pendingDeleteId = id;
                document.getElementById('deleteTourneyTargetId').innerText = id;
                document.getElementById('deleteTourneyTargetName').innerText = name;
                var modal = document.getElementById('deleteTourneyModalBackdrop');
                if (modal) {
                    modal.style.display = 'flex';
                    document.body.style.overflow = 'hidden';
                }
            }

            function closeDeleteTourneyModal() {
                pendingDeleteId = null;
                var modal = document.getElementById('deleteTourneyModalBackdrop');
                if (modal) {
                    modal.style.display = 'none';
                    document.body.style.overflow = '';
                }
            }

            function confirmDeleteTourney() {
                if (!pendingDeleteId) return;
                var tid = pendingDeleteId;

                // 1. Clean localStorage
                try {
                    localStorage.removeItem("tourma_matches_" + tid);
                    localStorage.removeItem("tourma_de_matches_" + tid);
                    localStorage.removeItem("tourma_rr_matches_" + tid);
                    localStorage.removeItem("tourma_teams_" + tid);
                    localStorage.removeItem("tourma_format_" + tid);
                    localStorage.removeItem("tourma_type_" + tid);
                    localStorage.removeItem("tourma_champion_" + tid);
                    localStorage.removeItem("tourma_final_champion_" + tid);
                    localStorage.removeItem("tourma_final_locked_" + tid);
                } catch(e) {}

                // 2. Remove card from DOM instantly with smooth fade-out animation
                var card = document.querySelector('.tourney-card[data-id="' + tid + '"]');
                if (card) {
                    card.style.transition = 'all 0.3s ease';
                    card.style.opacity = '0';
                    card.style.transform = 'scale(0.9)';
                    setTimeout(function() {
                        if (card.parentNode) card.parentNode.removeChild(card);
                        var remaining = document.querySelectorAll('.tourney-card');
                        if (remaining.length === 0) {
                            window.location.reload();
                        }
                    }, 300);
                }

                closeDeleteTourneyModal();

                // 3. Send async delete request to backend in background
                fetch('${pageContext.request.contextPath}/delete-tournament?id=' + tid, { method: 'GET' })
                    .then(function() { console.log('Tournament ' + tid + ' deleted on backend.'); })
                    .catch(function(err) { console.warn('Backend delete fetch note:', err); });
            }

            function openCloneTourneyModal(id, currentName) {
                var srcInput = document.getElementById('cloneSourceTournamentId');
                var nameInput = document.getElementById('cloneNewTourneyName');
                var modal = document.getElementById('cloneTourneyModalBackdrop');
                if (srcInput) srcInput.value = id;
                if (nameInput) {
                    nameInput.value = (currentName ? currentName : 'Giải Đấu') + ' (Bản sao)';
                }
                if (modal) {
                    modal.style.display = 'flex';
                    document.body.style.overflow = 'hidden';
                    if (nameInput) {
                        setTimeout(function() { nameInput.focus(); nameInput.select(); }, 50);
                    }
                }
            }

            function closeCloneTourneyModal() {
                var modal = document.getElementById('cloneTourneyModalBackdrop');
                if (modal) {
                    modal.style.display = 'none';
                    document.body.style.overflow = '';
                }
            }

            document.addEventListener('keydown', function(e) {
                if (e.key === 'Escape') {
                    closeDeleteTourneyModal();
                    closeCloneTourneyModal();
                }
            });
        </script>
    </body>
</html>
