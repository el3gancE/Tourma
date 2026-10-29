<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@page import="model.Series, model.Tournament, dao.SeriesDAO, dao.TournamentDAO, dao.ParticipantDAO, model.Team, java.util.List, java.util.Map, java.util.HashMap"%>
<%
    String seriesIdVal = request.getParameter("id");
    if (seriesIdVal == null || seriesIdVal.trim().isEmpty()) {
        seriesIdVal = request.getParameter("seriesId");
    }

    SeriesDAO seriesDAO = new SeriesDAO();
    TournamentDAO tournamentDAO = new TournamentDAO();
    ParticipantDAO participantDAO = new ParticipantDAO();

    String actionParam = request.getParameter("action");
    String delTourneyId = request.getParameter("tournamentId");
    if ("POST".equalsIgnoreCase(request.getMethod()) && "delete".equalsIgnoreCase(actionParam) && delTourneyId != null) {
        tournamentDAO.deleteTournament(delTourneyId.trim());
        if (seriesIdVal != null && !seriesIdVal.trim().isEmpty()) {
            service.RollingWindowPointService.getInstance().recalculateAndPersistStandings(seriesIdVal.trim());
        }
    }

    Series series = (Series) request.getAttribute("series");
    if (series == null && seriesIdVal != null && !seriesIdVal.trim().isEmpty()) {
        series = seriesDAO.getSeriesById(seriesIdVal.trim());
    }

    if (series == null) {
        List<Series> allSeries = seriesDAO.getAllSeries();
        for (Series s : allSeries) {
            if ("ROLLING_WINDOW".equalsIgnoreCase(s.getRankingModel())) {
                series = s;
                break;
            }
        }
    }

    List<Tournament> tournamentsList = (List<Tournament>) request.getAttribute("tournamentsList");
    if (tournamentsList == null && series != null) {
        TournamentDAO.TournamentPage pageInfo = tournamentDAO.getTournamentPage(series.getId(),
                request.getParameter("q"), "ALL", request.getParameter("page"));
        tournamentsList = pageInfo.getTournaments();
        request.setAttribute("pageInfo", pageInfo);
    }

    Map<String, Integer> teamCountMap = (Map<String, Integer>) request.getAttribute("teamCountMap");
    if (teamCountMap == null) {
        teamCountMap = new HashMap<>();
        if (tournamentsList != null) {
            for (Tournament t : tournamentsList) {
                teamCountMap.put(t.getId(), t.getTeamCount());
            }
        }
    }

    Map<String, List<String>> stageFormatsMap = (Map<String, List<String>>) request.getAttribute("stageFormatsMap");
    if (stageFormatsMap == null) {
        stageFormatsMap = series != null ? tournamentDAO.getStageFormatsBySeriesId(series.getId()) : new HashMap<>();
    }

    if (seriesIdVal == null && series != null) {
        seriesIdVal = series.getId();
    }
    String seriesName = (series != null) ? series.getName() : "Series Circuit";
    TournamentDAO.TournamentPage pageInfo = (TournamentDAO.TournamentPage) request.getAttribute("pageInfo");
    int tourneyCount = pageInfo != null ? pageInfo.getTotalCount() : 0;
%>
<%!
    private String getFmtName(String fmt) {
        if (fmt == null) return "Single Elimination";
        String f = fmt.toUpperCase();
        if ("DOUBLE_ELIMINATION".equals(f)) return "Double Elimination";
        if ("ROUND_ROBIN".equals(f)) return "Round Robin";
        if ("GROUP_STAGE".equals(f)) return "Group Stage";
        if ("GSL".equals(f)) return "GSL Format";
        if ("SWISS_LITE".equals(f) || "SWISS".equals(f)) return "Swiss System";
        return "Single Elimination";
    }
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Danh Sách Giải Con - <%= seriesName %></title>

        <!-- Google Fonts & FontAwesome -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

        <!-- Design System CSS & Dedicated CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/my-tournaments.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-tournament-list.css">
    </head>
    <body>
        <!-- Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-series"/>
        </jsp:include>

        <!-- Dynamic 3-Mode Sidebar Component -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
            <jsp:param name="activeStep" value="tournament-list"/>
        </jsp:include>

        <main class="container has-sidebar" data-ui-style="rolling-tournament-list-jsp-001">

            <!-- Breadcrumb Navigation -->
            <div data-ui-style="rolling-tournament-list-jsp-002">
                <a href="${pageContext.request.contextPath}/rolling/dashboard?id=<%= seriesIdVal %>" class="text-muted" data-ui-style="rolling-tournament-list-jsp-003">
                    <i class="fa-solid fa-arrow-left"></i> Quay lại Dashboard Series
                </a>
            </div>

            <!-- Series Header Banner Card -->
            <div class="team-list-header-card" data-ui-style="rolling-tournament-list-jsp-004">
                <div data-ui-style="rolling-tournament-list-jsp-005" style="display: flex;">
                    <div>
                        <h1 class="rolling-series-name" data-ui-style="rolling-tournament-list-jsp-006">
                            <i class="fa-solid fa-trophy text-gold"></i> Danh Sách Giải Con
                        </h1>
                        <p class="text-muted" data-ui-style="rolling-tournament-list-jsp-007">
                            Quản lý tất cả các giải đấu con thuộc Series <strong><%= seriesName %></strong> (<%= tourneyCount %> Giải Con)
                        </p>
                    </div>
                    <div>
                        <a href="${pageContext.request.contextPath}/rolling/create-tournament?seriesId=<%= seriesIdVal %>" class="btn btn-mint" data-ui-style="rolling-tournament-list-jsp-008">
                            <i class="fa-solid fa-plus-circle"></i> + Thêm Giải Con Mới
                        </a>
                    </div>
                </div>
            </div>

            <!-- Search the complete series before applying the 25-row page limit. -->
            <form class="tourney-search-form" method="get" action="${pageContext.request.contextPath}/rolling/tournament-list">
                <input type="hidden" name="seriesId" value="<c:out value='<%= series != null ? series.getId() : "" %>' />">
                <div class="search-input-box">
                    <i class="fa-solid fa-magnifying-glass"></i>
                    <input type="text" id="searchInput" name="q" value="<c:out value="${pageInfo.query}" />" class="form-control" placeholder="Tìm kiếm tên giải con...">
                    <button type="submit" class="tourney-search-submit">Tìm</button>
                </div>
            </form>
            <!-- Tournament Cards Grid -->
            <div class="tourney-cards-grid" id="tourneyCardList">
                <% if (tournamentsList != null && !tournamentsList.isEmpty()) {
                    // SQL returns this page in newest-first order.
                    for (int i = 0; i < tournamentsList.size(); i++) {
                        Tournament t = tournamentsList.get(i);
                        int nTeams = teamCountMap.containsKey(t.getId()) ? teamCountMap.get(t.getId()) : 0;
                        String tierName = (t.getTierName() != null) ? t.getTierName().toUpperCase() : "S";
                        String fmt = (t.getFormat() != null) ? t.getFormat().toUpperCase() : "SINGLE_ELIMINATION";

                        boolean isMulti = "MULTI_STAGE".equalsIgnoreCase(t.getTournamentType());
                        List<String> stgFormats = (stageFormatsMap != null) ? stageFormatsMap.get(t.getId()) : null;
                        String s1Fmt = (stgFormats != null && !stgFormats.isEmpty()) ? stgFormats.get(0) : fmt;
                        String s2Fmt = (stgFormats != null && stgFormats.size() > 1) ? stgFormats.get(1) : "SINGLE_ELIMINATION";

                        String s1Label = getFmtName(s1Fmt);
                        String s2Label = getFmtName(s2Fmt);
                        String fmtLabel = isMulti ? (s1Label + " ➔ " + s2Label) : s1Label;

                        String championName = t.getChampionName();
                        boolean isFinished = "COMPLETED".equalsIgnoreCase(t.getStatus()) || "FINISHED".equalsIgnoreCase(t.getStatus());
                        boolean isOngoing = "ONGOING".equalsIgnoreCase(t.getStatus()) || "IN_PROGRESS".equalsIgnoreCase(t.getStatus());
                        String statusStr = isFinished ? "Completed" : (isOngoing ? "In Progress" : "Incoming");
                        String statusClass = isFinished ? "completed" : (isOngoing ? "in-progress" : "incoming");
                        String stageTypeLabel = isMulti ? "MULTI STAGE" : "SINGLE STAGE";
                %>
                    <div class="tourney-card" data-id="<%= t.getId() %>" data-stage-type="<%= isMulti ? "MULTI_STAGE" : "SINGLE_STAGE" %>" data-stage1-format="<%= s1Fmt %>" data-stage2-format="<%= s2Fmt %>" data-status="<%= isFinished ? "COMPLETED" : (isOngoing ? "IN_PROGRESS" : "INCOMING") %>" data-db-status="<%= t.getStatus() != null ? t.getStatus() : "" %>" data-db-champion="<%= championName != null ? championName.replace("\"", "&quot;") : "" %>" data-name="<%= t.getName() %>">
                        <div class="tourney-card-main">
                            <div class="tourney-card-top-row">
                                <h3 class="tourney-card-title" data-ui-style="rolling-tournament-list-jsp-009" style="display: inline-flex;">
                                    <%= t.getName() %>
                                    <span class="tier-tag tier-<%= tierName.toLowerCase() %>" data-ui-style="rolling-tournament-list-jsp-010">[<%= tierName %>]</span>
                                </h3>
                                <span class="tourney-badge-type"><%= stageTypeLabel %></span>
                                <span class="status-pill <%= statusClass %>">
                                    <%= statusStr %>
                                </span>
                            </div>

                            <div class="tourney-card-meta" data-ui-style="rolling-tournament-list-jsp-011">
                                <span class="tourney-teams-meta">
                                    <i class="fa-solid fa-users text-mint"></i>
                                    <span class="team-count-val" data-ui-style="rolling-tournament-list-jsp-012"><%= nTeams %> Đội</span>
                                </span>
                                <span class="meta-divider">•</span>
                                <span><i class="fa-solid fa-layer-group text-gold"></i> Thuộc Series</span>
                                <span class="meta-divider">•</span>
                                <span class="tourney-format-span">
                                    <i class="fa-solid fa-diagram-project text-mint"></i> <%= fmtLabel %>
                                </span>
                                <span id="championMeta_<%= t.getId() %>" class="tourney-champion-meta" style="<%= (isFinished) ? "display: inline-flex;" : "display: none;" %>">
                                    <i class="fa-solid fa-trophy text-gold"></i> Nhà vô địch: <span class="champion-name-val" data-ui-style="rolling-tournament-list-jsp-013"><%= championName != null ? championName : "" %></span>
                                </span>
                            </div>

                        </div>

                        <div class="tourney-card-footer">
                            <div class="tourney-card-actions">
                                <button type="button" class="btn-delete-tourney" title="Xóa" onclick="openDeleteTourneyModal('<%= t.getId() %>', '<%= (t.getName() != null) ? t.getName().replace("'", "\\'").replace("\"", "&quot;") : "" %>')">
                                    <i class="fa-solid fa-trash-can"></i> Xóa
                                </button>
                                <a href="${pageContext.request.contextPath}/rolling/tournament-teams?id=<%= t.getId() %>&seriesId=<%= seriesIdVal %>" class="btn-details-tourney" title="Quản lý đội">
                                    <i class="fa-solid fa-users-gear"></i> QL Đội
                                </a>
                                <a href="${pageContext.request.contextPath}/rolling/point-config?id=<%= t.getId() %>&seriesId=<%= seriesIdVal %>" class="btn-details-tourney" title="Set điểm thưởng">
                                    <i class="fa-solid fa-star text-gold"></i> Set Điểm
                                </a>
                                <a href="${pageContext.request.contextPath}/my-tournaments?action=matches&amp;id=<%= t.getId() %>" class="btn-view-bracket-card">
                                    Trận Đấu ➔
                                </a>
                            </div>
                            <div class="tourney-progress" title="Số trận đã xong / tổng số trận cả giải, tính trước Stage 2 khi chưa tạo bracket. Không tính BYE; 100% khi xác nhận hoàn thành giải.">
                                <div class="tourney-progress-label"><span>Tiến độ · <%= t.getCompletedMatches() %>/<%= t.getProgressTotalMatches() %> trận</span><strong><%= t.getProgressPercent() %>%</strong></div>
                                <div class="tourney-progress-track" role="progressbar" aria-label="Tiến độ giải đấu" aria-valuemin="0" aria-valuemax="100" aria-valuenow="<%= t.getProgressPercent() %>">
                                    <span class="tourney-progress-fill" style="width: <%= t.getProgressPercent() %>%"></span>
                                </div>
                            </div>
                        </div>
                    </div>
                <% }
                } else { %>
                        <div data-ui-style="rolling-tournament-list-jsp-014">
                            <i class="fa-solid fa-trophy" data-ui-style="rolling-tournament-list-jsp-015"></i>
                            <h4 data-ui-style="rolling-tournament-list-jsp-016">${not empty pageInfo.query ? 'Không có giải con phù hợp' : 'Chưa có giải con nào trong Series này'}</h4>
                            <p data-ui-style="rolling-tournament-list-jsp-017">${not empty pageInfo.query ? 'Thử tìm kiếm bằng từ khóa khác.' : 'Hãy bấm nút bên dưới để tạo giải đấu con đầu tiên cho Series.'}</p>
                            <a href="${pageContext.request.contextPath}/rolling/create-tournament?seriesId=<%= seriesIdVal %>" class="btn btn-mint" data-ui-style="rolling-tournament-list-jsp-018">
                                <i class="fa-solid fa-plus-circle"></i> + Tạo Giải Con Đầu Tiên
                            </a>
                        </div>
                    <% } %>
                </div>
            </div>

            <c:if test="${not empty pageInfo && pageInfo.totalCount > 0}">
                <nav class="tourney-pagination" aria-label="Phân trang giải đấu">
                    <span class="tourney-page-summary">${(pageInfo.page - 1) * 25 + 1}–${pageInfo.page * 25 < pageInfo.totalCount ? pageInfo.page * 25 : pageInfo.totalCount} / ${pageInfo.totalCount} giải</span>
                    <div class="tourney-page-buttons">
                        <c:choose>
                            <c:when test="${pageInfo.page > 1}">
                                <c:url var="previousPageUrl" value="/rolling/tournament-list"><c:param name="page" value="${pageInfo.page - 1}" /><c:param name="q" value="${pageInfo.query}" /><c:param name="filter" value="${pageInfo.filter}" /><c:param name="seriesId" value='<%= series != null ? series.getId() : "" %>' /></c:url>
                                <a href="<c:out value='${previousPageUrl}' />" class="tourney-page-btn" rel="prev">‹ Trước</a>
                            </c:when>
                            <c:otherwise><span class="tourney-page-btn is-disabled" aria-disabled="true">‹ Trước</span></c:otherwise>
                        </c:choose>
                        <c:forEach var="pageNumber" begin="${pageInfo.page > 2 ? pageInfo.page - 2 : 1}" end="${pageInfo.page + 2 < pageInfo.totalPages ? pageInfo.page + 2 : pageInfo.totalPages}">
                            <c:choose>
                                <c:when test="${pageNumber == pageInfo.page}"><span class="tourney-page-btn is-current" aria-current="page">${pageNumber}</span></c:when>
                                <c:otherwise>
                                    <c:url var="numberedPageUrl" value="/rolling/tournament-list"><c:param name="page" value="${pageNumber}" /><c:param name="q" value="${pageInfo.query}" /><c:param name="filter" value="${pageInfo.filter}" /><c:param name="seriesId" value='<%= series != null ? series.getId() : "" %>' /></c:url>
                                    <a href="<c:out value='${numberedPageUrl}' />" class="tourney-page-btn" aria-label="Trang ${pageNumber}">${pageNumber}</a>
                                </c:otherwise>
                            </c:choose>
                        </c:forEach>
                        <c:choose>
                            <c:when test="${pageInfo.page < pageInfo.totalPages}">
                                <c:url var="nextPageUrl" value="/rolling/tournament-list"><c:param name="page" value="${pageInfo.page + 1}" /><c:param name="q" value="${pageInfo.query}" /><c:param name="filter" value="${pageInfo.filter}" /><c:param name="seriesId" value='<%= series != null ? series.getId() : "" %>' /></c:url>
                                <a href="<c:out value='${nextPageUrl}' />" class="tourney-page-btn" rel="next">Sau ›</a>
                            </c:when>
                            <c:otherwise><span class="tourney-page-btn is-disabled" aria-disabled="true">Sau ›</span></c:otherwise>
                        </c:choose>
                        <span class="tourney-page-total">Trang ${pageInfo.page}/${pageInfo.totalPages}</span>
                    </div>
                </nav>
            </c:if>
            <!-- DELETE SUB-TOURNAMENT CONFIRMATION MODAL -->
            <div id="deleteTourneyModalBackdrop" class="tourma-modal-backdrop" style="display: none;" onclick="if(event.target === this) closeDeleteTourneyModal();">
                <div class="tourma-modal-card" data-ui-style="rolling-tournament-list-jsp-019" onclick="event.stopPropagation();">
                    <div class="modal-header-bar" data-ui-style="rolling-tournament-list-jsp-020">
                        <div class="modal-header-title" data-ui-style="rolling-tournament-list-jsp-021" style="display: flex;">
                            <i class="fa-solid fa-trash-can"></i>
                            <span>Xác Nhận Xóa Giải Con</span>
                        </div>
                        <button type="button" class="modal-close-btn" onclick="closeDeleteTourneyModal()" title="Đóng">
                            <i class="fa-solid fa-xmark"></i>
                        </button>
                    </div>

                    <div class="modal-body-content" data-ui-style="rolling-tournament-list-jsp-022">
                        <div data-ui-style="rolling-tournament-list-jsp-023">
                            <strong data-ui-style="rolling-tournament-list-jsp-024">⚠️ Cảnh báo xóa vĩnh viễn:</strong><br>
                            Hành động này sẽ <strong data-ui-style="rolling-tournament-list-jsp-025">xóa hoàn toàn giải đấu con này</strong> cùng toàn bộ danh sách đội tuyển, cấu hình thể thức, kết quả các trận đấu và tự động cập nhật lại bảng xếp hạng tích lũy Series.
                        </div>
                        <p data-ui-style="rolling-tournament-list-jsp-026">
                            Bạn có chắc chắn muốn xóa giải con: <strong id="deleteTourneyTargetName" data-ui-style="rolling-tournament-list-jsp-027"></strong> (ID: <span id="deleteTourneyTargetId" class="text-muted"></span>)?
                        </p>
                    </div>

                    <div class="modal-footer-bar" data-ui-style="rolling-tournament-list-jsp-028" style="display: flex;">
                        <button type="button" class="btn btn-secondary" onclick="closeDeleteTourneyModal()" data-ui-style="rolling-tournament-list-jsp-029">Hủy Bỏ</button>
                        <button type="button" class="btn" data-ui-style="rolling-tournament-list-jsp-030" onclick="confirmDeleteTourney()">
                            <i class="fa-solid fa-trash-can"></i> Xác Nhận Xóa
                        </button>
                    </div>
                </div>
            </div>

            <!-- CLONE SUB-TOURNAMENT CONFIRMATION MODAL -->
            <div id="cloneTourneyModalBackdrop" class="tourma-modal-backdrop" style="display: none;" onclick="if(event.target === this) closeCloneTourneyModal();">
                <div class="tourma-modal-card" data-ui-style="rolling-tournament-list-jsp-031" onclick="event.stopPropagation();">
                    <div class="modal-header-bar" data-ui-style="rolling-tournament-list-jsp-032">
                        <div class="modal-header-title" data-ui-style="rolling-tournament-list-jsp-033" style="display: flex;">
                            <i class="fa-solid fa-copy"></i>
                            <span>Sao Chép Cấu Hình Giải Con</span>
                        </div>
                        <button type="button" class="modal-close-btn" onclick="closeCloneTourneyModal()" title="Đóng">
                            <i class="fa-solid fa-xmark"></i>
                        </button>
                    </div>

                    <form id="cloneTourneyForm" method="POST" action="${pageContext.request.contextPath}/clone-tournament">
                        <input type="hidden" name="sourceTournamentId" id="cloneSourceTournamentId" value="">
                        <input type="hidden" name="redirectContext" id="cloneRedirectContext" value="series">

                        <div class="modal-body-content" data-ui-style="rolling-tournament-list-jsp-034">
                            <div data-ui-style="rolling-tournament-list-jsp-035">
                                <i class="fa-solid fa-circle-info text-mint"></i>
                                Hệ thống sẽ tạo một giải đấu con mới trong Series này ở trạng thái <strong>DRAFT</strong> với toàn bộ cấu hình thể thức, phân bảng, multi-stage từ giải gốc.
                            </div>

                            <div data-ui-style="rolling-tournament-list-jsp-036">
                                <label data-ui-style="rolling-tournament-list-jsp-037" style="display: block;">
                                    Tên giải đấu mới:
                                </label>
                                <input type="text" id="cloneNewTourneyName" name="newName" class="form-control" required data-ui-style="rolling-tournament-list-jsp-038" placeholder="Nhập tên giải mới...">
                            </div>

                            <div data-ui-style="rolling-tournament-list-jsp-039" style="display: flex;">
                                 <i class="fa-solid fa-users text-mint" data-ui-style="rolling-tournament-list-jsp-040"></i>
                                 <div data-ui-style="rolling-tournament-list-jsp-041">
                                     <strong>Ràng buộc số lượng:</strong> Danh sách đội sẽ để trống để bạn nhập hoặc chọn từ Partner ở bước tiếp theo, và <strong>bắt buộc phải khớp đúng số lượng đội</strong> của giải gốc.
                                 </div>
                            </div>
                        </div>

                        <div class="modal-footer-bar" data-ui-style="rolling-tournament-list-jsp-042" style="display: flex;">
                            <button type="button" class="btn btn-secondary" onclick="closeCloneTourneyModal()" data-ui-style="rolling-tournament-list-jsp-043">Hủy Bỏ</button>
                            <button type="submit" class="btn btn-mint" data-ui-style="rolling-tournament-list-jsp-044" style="display: inline-flex;">
                                <i class="fa-solid fa-copy"></i> Tạo Bản Sao
                            </button>
                        </div>
                    </form>
                </div>
            </div>

        </main>

        <script>
            let pendingDeleteId = null;

            function openDeleteTourneyModal(id, name) {
                pendingDeleteId = id;
                var targetIdEl = document.getElementById('deleteTourneyTargetId');
                if (targetIdEl) targetIdEl.innerText = id;
                var targetNameEl = document.getElementById('deleteTourneyTargetName');
                if (targetNameEl) targetNameEl.innerText = name;
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
                var seriesId = "<%= (seriesIdVal != null) ? seriesIdVal : "" %>";

                // 1. Clean localStorage for this sub-tournament
                try {
                    localStorage.removeItem("tourma_matches_" + tid);
                    localStorage.removeItem("tourma_de_matches_" + tid);
                    localStorage.removeItem("tourma_rr_matches_" + tid);
                    localStorage.removeItem("tourma_group_matches_" + tid);
                    localStorage.removeItem("tourma_bracket_" + tid);
                    localStorage.removeItem("tourma_bracket_stage2_" + tid);
                    localStorage.removeItem("tourma_teams_" + tid);
                    localStorage.removeItem("tourma_format_" + tid);
                    localStorage.removeItem("tourma_type_" + tid);
                    localStorage.removeItem("tourma_champion_" + tid);
                    localStorage.removeItem("tourma_final_champion_" + tid);
                    localStorage.removeItem("tourma_final_locked_" + tid);
                    localStorage.removeItem("tourma_group_assignments_" + tid);
                    localStorage.removeItem("tourma_multi_config_" + tid);
                    localStorage.removeItem("tourma_point_config_" + tid);
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
                fetch('${pageContext.request.contextPath}/delete-tournament?id=' + encodeURIComponent(tid) + '&seriesId=' + encodeURIComponent(seriesId), {
                    method: 'POST',
                    headers: {
                        'X-Requested-With': 'XMLHttpRequest',
                        'Accept': 'application/json'
                    }
                })
                .then(function() { console.log('Sub-tournament ' + tid + ' deleted on backend.'); })
                .catch(function(err) { console.warn('Backend delete fetch note:', err); });
            }

            function openCloneTourneyModal(id, currentName) {
                var srcInput = document.getElementById('cloneSourceTournamentId');
                var nameInput = document.getElementById('cloneNewTourneyName');
                var modal = document.getElementById('cloneTourneyModalBackdrop');
                if (srcInput) srcInput.value = id;
                if (nameInput) {
                    nameInput.value = (currentName ? currentName : 'Giải Con') + ' (Bản sao)';
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
