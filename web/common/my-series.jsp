<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html lang="vi" data-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Chuỗi Giải Của Tôi - TOURMA</title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
    <script src="${pageContext.request.contextPath}/js/theme.js?v=20261101-settings"></script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/list-workspace.css?v=1">
</head>
<body class="ui-page workspace-page series-page">
    <jsp:include page="/common/component/header.jsp"><jsp:param name="active" value="my-series"/><jsp:param name="themeToggle" value="true"/></jsp:include>
    <main class="workspace-main">

        <div class="page-header-row"><div><h1 class="page-title"><i class="fa-solid fa-layer-group" aria-hidden="true"></i> Chuỗi Giải Của Tôi</h1></div><a class="btn btn-mint" href="${pageContext.request.contextPath}/common/create-series.jsp"><span aria-hidden="true">＋</span> Tạo chuỗi giải mới</a></div>
        <div class="toolbar-bar">
            <div class="filter-pills-group" aria-label="Lọc theo mô hình xếp hạng"><button type="button" class="filter-pill-btn active" data-model-filter="ALL" aria-pressed="true">Tất cả</button><button type="button" class="filter-pill-btn" data-model-filter="ROLLING_WINDOW" aria-pressed="false">Rolling Window</button><button type="button" class="filter-pill-btn" data-model-filter="FIFA_ELO" aria-pressed="false">FIFA Elo Rating</button></div>
            <div class="search-input-box"><i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i><input type="search" id="searchInput" class="form-control" placeholder="Tìm theo tên chuỗi giải…" aria-label="Tìm kiếm chuỗi giải"></div>
        </div>
        <c:choose><c:when test="${not empty seriesList}">
            <div class="series-cards-grid" id="seriesGrid">
                <c:forEach var="s" items="${seriesList}">
                    <article class="series-card" data-model="<c:out value="${s.rankingModel}" />" data-name="<c:out value="${s.name}" />">
                        <div class="series-card-info">
                            <h3 class="series-card-title" title="<c:out value="${s.name}" />"><c:out value="${s.name}" /></h3>
                            <p class="series-model-line">${s.rankingModel == 'ROLLING_WINDOW' ? 'Rolling Window' : 'FIFA Elo Rating'} <span aria-hidden="true">·</span> Phase ${s.currentPhase} <span aria-hidden="true">·</span> <c:choose><c:when test="${s.rankingModel == 'ROLLING_WINDOW'}"><strong class="series-window-value" title="Số giải gần nhất được tính điểm (window)">${s.phaseSize}</strong> giải</c:when><c:otherwise>Elo khởi đầu: ${s.initialElo} <span aria-hidden="true">·</span> Xếp hạng chung &amp; theo nhóm</c:otherwise></c:choose></p>
                        </div>
                        <div class="series-card-actions">
                            <form action="${pageContext.request.contextPath}/my-series" method="POST" class="series-delete-form" data-series-name="<c:out value="${s.name}" />"><input type="hidden" name="action" value="delete"><input type="hidden" name="seriesId" value="<c:out value="${s.id}" />"><button type="submit" class="series-icon-button" aria-label="Xóa chuỗi giải" title="Xóa chuỗi giải">Xóa</button></form>
                            <button type="button" class="series-icon-button series-edit-button" aria-label="Chỉnh sửa chuỗi giải" title="Chỉnh sửa chuỗi giải" data-series-id="<c:out value="${s.id}" />" data-series-name="<c:out value="${s.name}" />" data-phase-size="${s.phaseSize > 0 ? s.phaseSize : 10}" data-status="<c:out value="${s.status}" />" data-action-url="${pageContext.request.contextPath}/my-series">Sửa</button>
                            <c:choose><c:when test="${s.rankingModel == 'ROLLING_WINDOW'}"><a class="series-manage-button" href="${pageContext.request.contextPath}/rolling/dashboard?id=${s.id}">Quản lý <span aria-hidden="true">→</span></a></c:when><c:otherwise><a class="series-manage-button" href="${pageContext.request.contextPath}/leaderboard.jsp?seriesId=${s.id}">Xem BXH <span aria-hidden="true">→</span></a></c:otherwise></c:choose>
                        </div>
                    </article>
                </c:forEach>
            </div>
            <div id="seriesNoResults" class="empty-state-box" hidden><h3>Không có chuỗi giải phù hợp</h3><p class="text-muted">Thử đổi từ khóa hoặc chọn mô hình khác.</p></div>
            <p id="seriesResultCount" class="series-result-count" role="status" aria-live="polite"></p>
        </c:when><c:otherwise><div class="empty-state-box"><div><i class="fa-regular fa-folder-open" aria-hidden="true"></i></div><h3>Chưa có chuỗi giải nào</h3><p class="text-muted">Khởi tạo Series đầu tiên để bắt đầu mùa giải của bạn.</p><a class="btn btn-mint" href="${pageContext.request.contextPath}/common/create-series.jsp">Tạo chuỗi giải mới</a></div></c:otherwise></c:choose>
    </main>
    <jsp:include page="/common/component/edit-series-popup.jsp"><jsp:param name="formActionUrl" value="${pageContext.request.contextPath}/my-series"/></jsp:include>
    <script src="${pageContext.request.contextPath}/js/my-series.js?v=1"></script>
</body>
</html>
