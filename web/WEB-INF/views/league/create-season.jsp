<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Tạo mùa đầu tiên · Tourma</title>
    <link rel="icon" type="image/svg+xml" href="<c:url value='/images/trophy-gradient-icon.svg' />">
    <script src="<c:url value='/js/theme.js' />"></script>
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="<c:url value='/css/design-tokens.css' />">
    <link rel="stylesheet" href="<c:url value='/css/ui-base.css' />">
    <link rel="stylesheet" href="<c:url value='/css/workspace.css' />">
    <link rel="stylesheet" href="<c:url value='/css/tournament-workspace.css' />">
    <link rel="stylesheet" href="<c:url value='/css/sidebar.css' />">
    <link rel="stylesheet" href="<c:url value='/css/popup.css?v=6' />">
    <link rel="stylesheet" href="<c:url value='/css/league/create-season.css?v=5' />">
    <script src="<c:url value='/js/league/create-season.js?v=3' />" defer></script>
</head>
<body class="ui-page workspace-page tournament-workspace league-create-season-page">
    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="my-series"/>
        <jsp:param name="themeToggle" value="true"/>
    </jsp:include>
    <div class="tournament-shell">
        <jsp:include page="/WEB-INF/views/league/sidebar.jsp"/>
        <main>
            <p class="text-muted"><c:out value="${leagueSeries.name}" /></p>
            <h1><i class="fa-regular fa-calendar" aria-hidden="true"></i> ${not empty leagueReadOnlySeasons ? 'Mùa giải' : (empty leagueSeasonName ? 'Tạo mùa đầu tiên' : 'Thiết lập mùa đầu tiên')}</h1>
            <c:url var="teamsUrl" value="/league/season/teams"><c:param name="id" value="${leagueSeries.id}" /></c:url>
            <c:choose>
                <c:when test="${not empty leagueReadOnlySeasons}">
                    <section class="league-setup-panel" aria-label="Danh sách mùa">
                        <c:forEach var="season" items="${leagueReadOnlySeasons}">
                            <div class="league-setup-toolbar">
                                <strong><c:out value="${season.name}" /></strong>
                                <span>${season.status == 'ACTIVE' ? 'Đang diễn ra' : 'Đã chốt'}</span>
                            </div>
                        </c:forEach>
                    </section>
                </c:when>
                <c:when test="${fn:length(leagueTeams) lt 2}">
                    <p>Cần ít nhất 2 đội đã đăng ký để tạo mùa.</p>
                    <a class="btn btn-mint" href="<c:out value='${teamsUrl}' />">Đăng ký đội</a>
                </c:when>
                <c:otherwise>
                    <c:url var="createUrl" value="/league/season/create"><c:param name="id" value="${leagueSeries.id}" /></c:url>
                    <form id="league-season-form" method="post" action="<c:out value='${createUrl}' />">
                        <input type="hidden" name="editing" value="${not empty leagueSeasonName}">
                        <section class="league-setup-panel" aria-labelledby="season-info-title">
                            <h2 id="season-info-title">Thông tin mùa</h2>
                            <label for="season-name">Tên mùa</label>
                            <input id="season-name" name="seasonName" class="form-control" required maxlength="255" value="<c:out value='${empty leagueSeasonName ? "Mùa 1" : leagueSeasonName}' />">
                            <label class="league-cup-option"><input type="checkbox" name="cup" ${leagueSeasonCup ? 'checked' : ''}> Tổ chức Cúp SE cho tất cả đội</label>
                            <label for="league-division-count">Số hạng đấu</label>
                            <input id="league-division-count" class="form-control" type="number" min="1" step="1" required>
                        </section>
                        <section class="league-setup-panel" aria-labelledby="division-title">
                            <div class="league-setup-toolbar">
                                <h2 id="division-title">Hạng đấu và suất lên/xuống</h2>
                                <a href="<c:out value='${teamsUrl}' />">Danh sách đội đã đăng ký</a>
                            </div>
                            <p class="text-muted league-unassigned" id="league-unassigned" aria-live="polite"></p>
                            <c:forEach var="legs" items="${leagueSeasonLegs}" varStatus="row">
                                <span hidden data-saved-legs="${legs}" data-saved-slots="${leagueSeasonSlots[row.index]}"></span>
                            </c:forEach>
                            <div id="league-division-rows"></div>
                            <template id="league-division-template">
                                <fieldset class="league-division">
                                    <legend></legend>
                                    <label>Số lượt vòng tròn <input class="form-control" type="number" name="legs" value="1" step="1" min="1" max="10" required></label>
                                    <label data-exchange><span data-exchange-label></span> <input class="form-control" type="number" name="slots" value="0" step="1" min="0" max="2147483647" required></label>
                                    <div class="league-division-roster">
                                        <div class="league-setup-toolbar">
                                            <span data-division-count class="text-muted"></span>
                                            <button type="button" class="btn btn-secondary" data-add-teams aria-haspopup="dialog">Thêm đội</button>
                                        </div>
                                        <ul class="league-division-teams" data-division-teams></ul>
                                    </div>
                                </fieldset>
                            </template>
                        </section>
                        <c:forEach var="team" items="${leagueTeams}">
                            <input type="hidden" data-assignment data-team="<c:out value='${team}' />" name="division:<c:out value='${team}' />" value="${leagueSavedLevels[team]}">
                        </c:forEach>
                        <p id="league-setup-error" role="alert" hidden></p>
                        <button type="submit" id="save-league-season" class="btn btn-mint" disabled>Lưu mùa</button>
                    </form>
                    <dialog id="league-team-picker" class="tourma-modal-card league-team-picker" aria-labelledby="league-team-picker-title">
                        <form method="dialog">
                            <header class="modal-header-bar">
                                <h2 id="league-team-picker-title"></h2>
                                <button type="submit" value="cancel" class="modal-close-btn" aria-label="Đóng">×</button>
                            </header>
                            <div id="league-team-picker-options" class="modal-body-content"></div>
                            <footer class="modal-footer-bar">
                                <button type="submit" value="cancel">Hủy</button>
                                <button type="submit" value="add" class="btn-modal-save" id="league-team-picker-add">Thêm đội đã chọn</button>
                            </footer>
                        </form>
                    </dialog>
                </c:otherwise>
            </c:choose>
        </main>
    </div>
</body>
</html>
