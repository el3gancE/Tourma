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
            <form id="tournamentFilters" class="toolbar-bar" method="get" action="${pageContext.request.contextPath}/my-tournaments">
                <input type="hidden" name="filter" id="tournamentFilterInput" value="<c:out value="${pageInfo.filter}" />">
                <div class="filter-pills-group">
                    <button type="button" class="filter-pill-btn ${pageInfo.filter == 'ALL' ? 'active' : ''}" onclick="filterTournaments('ALL', this)">Tất Cả</button>
                    <button type="button" class="filter-pill-btn ${pageInfo.filter == 'STANDALONE' ? 'active' : ''}" onclick="filterTournaments('STANDALONE', this)">Giải Đơn Lẻ</button>
                    <button type="button" class="filter-pill-btn ${pageInfo.filter == 'SERIES' ? 'active' : ''}" onclick="filterTournaments('SERIES', this)">Thuộc Series</button>
                    <button type="button" class="filter-pill-btn ${pageInfo.filter == 'INCOMING' ? 'active' : ''}" onclick="filterTournaments('INCOMING', this)">Incoming</button>
                    <button type="button" class="filter-pill-btn ${pageInfo.filter == 'IN_PROGRESS' ? 'active' : ''}" onclick="filterTournaments('IN_PROGRESS', this)">In Progress</button>
                    <button type="button" class="filter-pill-btn ${pageInfo.filter == 'COMPLETED' ? 'active' : ''}" onclick="filterTournaments('COMPLETED', this)">Completed</button>
                </div>

                <div class="search-input-box">
                    <i class="fa-solid fa-magnifying-glass"></i>
                    <input type="text" id="searchInput" class="form-control" name="q" value="<c:out value="${pageInfo.query}" />" placeholder="Tìm kiếm tên giải...">
                    <button type="submit" class="tourney-search-submit">Tìm</button>
                </div>
            </form>

            <!-- Tournament Cards Container -->
            <c:choose>
                <c:when test="${not empty tournamentList}">
                    <div class="tourney-cards-grid" id="tourneyGrid">
                        <c:forEach var="t" items="${tournamentList}">
                            <div class="tourney-card" 
                                 data-id="${t.id}"
                                 data-db-status="${t.status}"
                                 data-db-champion="${t.championName}"
                                 data-stage-type="${t.tournamentType}"
                                 data-context="${empty t.seriesId ? 'STANDALONE' : 'SERIES'}"
                                 data-team-count="${t.teamCount}"
                                 data-status="${t.status == 'COMPLETED' ? 'COMPLETED' : (t.status == 'ONGOING' || t.status == 'IN_PROGRESS' ? 'IN_PROGRESS' : 'INCOMING')}"
                                 data-name="${t.name}">
                                
                                <div class="tourney-card-main">
                                    <div class="tourney-card-top-row">
                                        <h3 class="tourney-card-title">${t.name}</h3>
                                        <span class="tourney-badge-type">
                                            ${t.tournamentType == 'MULTI_STAGE' ? 'MULTI STAGE' : 'SINGLE STAGE'}
                                        </span>
                                        <c:if test="${not empty t.tierName}">
                                            <span class="tourney-badge-tier tier-${t.tierName.toLowerCase()}">TIER ${t.tierName}</span>
                                        </c:if>
                                        <span class="status-pill ${t.status == 'COMPLETED' ? 'completed' : (t.status == 'ONGOING' || t.status == 'IN_PROGRESS' ? 'in-progress' : 'incoming')}" id="statusPill_${t.id}">
                                            ${t.status == 'COMPLETED' ? 'Completed' : (t.status == 'ONGOING' || t.status == 'IN_PROGRESS' ? 'In Progress' : 'Incoming')}
                                        </span>
                                    </div>

                                    <div class="tourney-card-meta">
                                        <!-- TOTAL TEAMS COUNT META -->
                                        <span class="tourney-teams-meta" id="teamMeta_${t.id}">
                                            <i class="fa-solid fa-users text-mint"></i> 
                                            <span class="team-count-val" style="color: #f8fafc; font-weight: 700;">${t.teamCount > 0 ? t.teamCount : 0} Đội</span>
                                        </span>

                                        <span class="meta-divider">•</span>

                                        <c:if test="${not empty t.seriesId}">
                                            <span><i class="fa-solid fa-layer-group text-gold"></i> Thuộc Series</span>
                                            <span class="meta-divider">•</span>
                                            <span><i class="fa-solid fa-flag"></i> Giải thứ ${t.tournamentIndexInSeries} (Phase ${t.phaseNumber})</span>
                                            <span class="meta-divider">•</span>
                                        </c:if>
                                        <span class="tourney-format-span">
                                            <i class="fa-solid fa-diagram-project text-mint"></i>
                                            <c:out value="${t.formatSummary}" />
                                        </span>
                                        <!-- Champion occupies its own metadata row when completed. -->
                                        <span id="championMeta_${t.id}" class="tourney-champion-meta" style="${(t.status == 'COMPLETED' && not empty t.championName) ? 'display: inline-flex;' : 'display: none;'}">
                                            <i class="fa-solid fa-trophy text-gold"></i> Nhà vô địch: <span class="champion-name-val" style="color: #fbbf24;">${not empty t.championName ? t.championName : ''}</span>
                                        </span>
                                    </div>

                                </div>

                                <div class="tourney-card-footer">
                                    <div class="tourney-card-actions">
                                        <button type="button" class="btn-delete-tourney" 
                                                onclick="openDeleteTourneyModal('${t.id}', '${t.name}')" 
                                                title="Xóa giải đấu">
                                            <i class="fa-solid fa-trash-can"></i> Xóa
                                        </button>
                                        <a href="${pageContext.request.contextPath}/common/configure-tournament-format.jsp?id=${t.id}" class="btn-details-tourney" title="Cấu hình & Chi tiết giải đấu">
                                            <i class="fa-solid fa-sliders"></i> Chi Tiết
                                        </a>
                                        <a href="${pageContext.request.contextPath}/my-tournaments?action=matches&amp;id=${t.id}" class="btn-view-bracket-card">
                                            Trận Đấu ➔
                                        </a>
                                    </div>
                                    <div class="tourney-progress" title="Số trận đã xong / tổng số trận cả giải, tính trước Stage 2 khi chưa tạo bracket. Không tính BYE; 100% khi xác nhận hoàn thành giải.">
                                        <div class="tourney-progress-label"><span>Tiến độ · ${t.completedMatches}/${t.progressTotalMatches} trận</span><strong>${t.progressPercent}%</strong></div>
                                        <div class="tourney-progress-track" role="progressbar" aria-label="Tiến độ giải đấu" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${t.progressPercent}">
                                            <span class="tourney-progress-fill" style="width: ${t.progressPercent}%"></span>
                                        </div>
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
                        <h3 style="font-size: 1.1rem; font-weight: 700; margin-bottom: 0.4rem;">${not empty pageInfo.query || pageInfo.filter != 'ALL' ? 'Không có giải đấu phù hợp' : 'Chưa có giải đấu nào được tạo'}</h3>
                        <p class="text-muted" style="font-size: 0.82rem; margin-bottom: 1.25rem;">
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
                <div class="tourma-modal-card" style="border-color: rgba(244, 63, 94, 0.4);" onclick="event.stopPropagation();">
                    <div class="modal-header-bar" style="border-bottom: 1px solid rgba(244, 63, 94, 0.2);">
                        <div class="modal-header-title" style="color: #f43f5e; font-size: 0.95rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem;">
                            <i class="fa-solid fa-trash-can"></i>
                            <span>Xác Nhận Xóa Giải Đấu</span>
                        </div>
                        <button type="button" class="modal-close-btn" onclick="closeDeleteTourneyModal()" title="Đóng">
                            <i class="fa-solid fa-xmark"></i>
                        </button>
                    </div>

                    <div class="modal-body-content" style="padding: 1.25rem 1rem;">
                        <div style="background: rgba(244, 63, 94, 0.08); border: 1px solid rgba(244, 63, 94, 0.2); border-radius: 8px; padding: 0.85rem; margin-bottom: 1rem; color: #cbd5e1; font-size: 0.82rem; line-height: 1.5;">
                            <strong style="color: #f43f5e;">⚠️ Cảnh báo xóa vĩnh viễn:</strong><br>
                            Hành động này sẽ <strong style="color: #ffffff;">xóa hoàn toàn giải đấu</strong> cùng toàn bộ danh sách đội tuyển, cấu hình thể thức và kết quả các trận đấu liên quan.
                        </div>
                        <p style="color: #cbd5e1; font-size: 0.85rem; margin: 0 0 0.35rem 0;">
                            Bạn có chắc chắn muốn xóa giải: <strong id="deleteTourneyTargetName" style="color: #f43f5e;"></strong> (ID: <span id="deleteTourneyTargetId" class="text-muted"></span>)?
                        </p>
                    </div>

                    <div class="modal-footer-bar" style="display: flex; justify-content: flex-end; gap: 0.65rem; padding: 0.85rem 1.25rem; border-top: 1px solid rgba(255, 255, 255, 0.08); background: rgba(0, 0, 0, 0.2);">
                        <button type="button" class="btn btn-secondary" onclick="closeDeleteTourneyModal()" style="font-size: 0.8rem; padding: 0.45rem 1rem;">Hủy Bỏ</button>
                        <button type="button" class="btn" style="background: #f43f5e; color: #ffffff; border: none; font-size: 0.8rem; font-weight: 700; padding: 0.45rem 1.25rem; border-radius: 6px; cursor: pointer;" onclick="confirmDeleteTourney()">
                            <i class="fa-solid fa-trash-can"></i> Xác Nhận Xóa
                        </button>
                    </div>
                </div>
            </div>

            <!-- CLONE TOURNAMENT CONFIRMATION MODAL -->
            <div id="cloneTourneyModalBackdrop" class="tourma-modal-backdrop" style="display: none;" onclick="if(event.target === this) closeCloneTourneyModal();">
                <div class="tourma-modal-card" style="border-color: rgba(45, 212, 191, 0.4); max-width: 490px;" onclick="event.stopPropagation();">
                    <div class="modal-header-bar" style="border-bottom: 1px solid rgba(45, 212, 191, 0.2);">
                        <div class="modal-header-title" style="color: #2dd4bf; font-size: 0.95rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem;">
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

                        <div class="modal-body-content" style="padding: 1.25rem 1rem;">
                            <div style="background: rgba(45, 212, 191, 0.08); border: 1px solid rgba(45, 212, 191, 0.2); border-radius: 8px; padding: 0.75rem 0.85rem; margin-bottom: 1rem; color: #cbd5e1; font-size: 0.82rem; line-height: 1.5;">
                                <i class="fa-solid fa-circle-info text-mint"></i> 
                                Hệ thống sẽ tạo một giải đấu mới ở trạng thái <strong>DRAFT</strong> với toàn bộ cấu hình thể thức, phân bảng, multi-stage từ giải gốc.
                            </div>

                            <div style="margin-bottom: 1rem;">
                                <label style="display: block; font-size: 0.78rem; font-weight: 700; color: #cbd5e1; margin-bottom: 0.35rem;">
                                    Tên giải đấu mới:
                                </label>
                                <input type="text" id="cloneNewTourneyName" name="newName" class="form-control" required style="width: 100%; font-size: 0.88rem; font-weight: 600; border-radius: 8px; box-sizing: border-box;" placeholder="Nhập tên giải mới...">
                            </div>

                            <div style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 8px; padding: 0.75rem 0.85rem; margin-bottom: 0.5rem; display: flex; align-items: flex-start; gap: 0.6rem;">
                                <i class="fa-solid fa-users text-mint" style="margin-top: 0.15rem; font-size: 0.9rem;"></i>
                                <div style="font-size: 0.8rem; color: #cbd5e1; line-height: 1.45;">
                                    <strong>Ràng buộc số lượng:</strong> Danh sách đội sẽ để trống để bạn nhập mới ở bước tiếp theo, và <strong>bắt buộc phải khớp đúng số lượng đội</strong> của giải gốc.
                                </div>
                            </div>
                        </div>

                        <div class="modal-footer-bar" style="display: flex; justify-content: flex-end; gap: 0.65rem; padding: 0.85rem 1.25rem; border-top: 1px solid rgba(255, 255, 255, 0.08); background: rgba(0, 0, 0, 0.2);">
                            <button type="button" class="btn btn-secondary" onclick="closeCloneTourneyModal()" style="font-size: 0.8rem; padding: 0.45rem 1rem;">Hủy Bỏ</button>
                            <button type="submit" class="btn btn-mint" style="font-size: 0.8rem; font-weight: 700; padding: 0.45rem 1.25rem; border-radius: 6px; cursor: pointer; display: inline-flex; align-items: center; gap: 0.4rem;">
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
