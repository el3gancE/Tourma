<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="tourma" tagdir="/WEB-INF/tags"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <tourma:page-title label="Danh sách đội League" contextName="${leagueSeries.name}" />
    <link rel="icon" type="image/svg+xml" href="<c:url value='/images/trophy-gradient-icon.svg' />">
    <script src="${pageContext.request.contextPath}/js/theme.js?v=default-layout-1"></script>
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=20">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=18">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/workspace.css?v=21">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-workspace.css?v=18">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css?v=20261031-tabs">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css?v=6">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-team-list.css?v=2">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/league/season-teams.css?v=4">
</head>
<body class="ui-page workspace-page tournament-workspace series-page rolling-team-list-page league-teams-page">
    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="my-series"/>
        <jsp:param name="themeToggle" value="true"/>
    </jsp:include>
    <div class="tournament-shell">
        <jsp:include page="/WEB-INF/views/league/sidebar.jsp"/>
        <main class="series-main">
            <header class="team-list-header-card">
                <div data-ui-style="rolling-team-list-jsp-004" class="league-roster-toolbar">
                    <div>
                        <h1 class="rolling-series-name"><i class="fa-solid fa-users" aria-hidden="true"></i> Danh sách đội tuyển</h1>
                        <p class="text-muted"><strong>${fn:length(leagueTeams)} đội</strong> đăng ký tham gia chuỗi giải <c:out value="${leagueSeries.name}" />.</p>
                    </div>
                    <div class="league-roster-toolbar">
                        <button type="button" class="btn btn-mint" onclick="openAddTeamPopup()">
                            <i class="fa-solid fa-user-plus" aria-hidden="true"></i> Đăng ký đội
                        </button>
                    </div>
                </div>
            </header>
            <c:url var="teamsUrl" value="/league/season/teams"><c:param name="id" value="${leagueSeries.id}" /></c:url>
            <section class="team-list-card" aria-labelledby="league-roster-title">
                <div class="league-roster-toolbar" data-ui-style="rolling-team-list-jsp-010">
                    <h2 id="league-roster-title" data-ui-style="rolling-team-list-jsp-011">Danh sách đội (${fn:length(leagueTeams)} đội)</h2>
                    <div class="league-roster-tools">
                        <input type="search" class="form-control" aria-label="Tìm kiếm tên đội" placeholder="Tìm kiếm tên đội..." oninput="filterPartnerTeams(this.value)">
                        <button type="button" class="btn btn-secondary" aria-haspopup="dialog"
                                onclick="document.getElementById('leagueBulkView').showModal()">
                            <i class="fa-solid fa-align-left" aria-hidden="true"></i> Xem dạng văn bản
                        </button>
                    </div>
                </div>
                <div class="league-table-scroll">
                    <table class="team-list-table" id="partnerTeamTable">
                        <thead>
                            <tr>
                                <th scope="col">STT</th>
                                <th scope="col"><button type="button" class="league-sort" onclick="sortTable('name')">Tên đội <i class="fa-solid fa-sort-up sort-icon sort-icon-name" aria-hidden="true"></i></button></th>
                                <th scope="col"><button type="button" class="league-sort" onclick="sortTable('tourneys')">Số mùa tham dự <i class="fa-solid fa-sort sort-icon sort-icon-tourneys" aria-hidden="true"></i></button></th>
                                <th scope="col">Hạng đấu hiện tại</th>
                                <th scope="col"><button type="button" class="league-sort" onclick="sortTable('date')">Ngày đăng ký <i class="fa-solid fa-sort sort-icon sort-icon-date" aria-hidden="true"></i></button></th>
                                <th scope="col">Thao tác</th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="team" items="${leagueTeams}" varStatus="row">
                                <c:set var="registeredAt" value="${leagueTeamDates[team]}" />
                                <tr data-team-name="<c:out value='${team}' />" data-tourney-count="0" data-created-at="${empty registeredAt ? 0 : registeredAt.time}">
                                    <td class="row-stt">${row.count}</td>
                                    <td><c:out value="${team}" /></td>
                                    <td>0</td>
                                    <td>tbd</td>
                                    <td><c:choose><c:when test="${not empty registeredAt}"><fmt:formatDate value="${registeredAt}" pattern="dd/MM/yyyy HH:mm" timeZone="Asia/Ho_Chi_Minh" /></c:when><c:otherwise>—</c:otherwise></c:choose></td>
                                    <td>
                                            <form method="post" action="<c:out value='${teamsUrl}' />" class="league-row-actions"
                                                  onsubmit="return confirm('Hủy đăng ký đội ' + this.elements.team.value + '?');">
                                                <input type="hidden" name="action" value="remove">
                                                <input type="hidden" name="team" value="<c:out value='${team}' />">
                                                <button type="submit" class="btn" data-ui-style="rolling-team-list-jsp-029"><i class="fa-solid fa-trash-can" aria-hidden="true"></i> Hủy đăng ký</button>
                                            </form>
                                    </td>
                                </tr>
                            </c:forEach>
                            <c:if test="${empty leagueTeams}"><tr><td colspan="6">Chưa có đội tham gia.</td></tr></c:if>
                        </tbody>
                    </table>
                </div>
            </section>
        </main>
    </div>
    <dialog id="leagueBulkView" class="tourma-modal-card league-bulk-dialog" aria-labelledby="league-bulk-title">
        <form method="dialog">
            <header class="modal-header-bar">
                <h2 id="league-bulk-title">Danh sách đội dạng văn bản</h2>
                <button type="submit" class="modal-close-btn" aria-label="Đóng">×</button>
            </header>
            <div class="modal-body-content">
                <textarea id="bulkPartnerTextarea" aria-label="Danh sách đội, mỗi đội một dòng" placeholder="Chưa có đội tham gia" rows="10" readonly><c:forEach var="team" items="${leagueTeams}" varStatus="row"><c:if test="${not row.first}">&#10;</c:if><c:out value="${team}" /></c:forEach></textarea>
            </div>
            <footer class="modal-footer-bar">
                <span id="bulkCopyToast" class="league-copy-status" role="status">Đã sao chép</span>
                <button type="button" class="btn-modal-save" onclick="copyBulkPartnerTeams()" ${empty leagueTeams ? 'disabled' : ''}>Sao chép toàn bộ</button>
            </footer>
        </form>
    </dialog>
    <jsp:include page="/common/component/add-team-popup.jsp"/>
    <script src="${pageContext.request.contextPath}/js/rolling/rolling-team-list.js"></script>
</body>
</html>
