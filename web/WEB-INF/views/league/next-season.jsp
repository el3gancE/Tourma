<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="tourma" tagdir="/WEB-INF/tags"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <tourma:page-title label="Thiết lập mùa ${nextNumber}" contextName="${leagueSeries.name}" />
    <link rel="icon" type="image/svg+xml" href="<c:url value='/images/trophy-gradient-icon.svg' />">
    <script src="<c:url value='/js/theme.js' />"></script>
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="<c:url value='/css/design-tokens.css' />">
    <link rel="stylesheet" href="<c:url value='/css/ui-base.css' />">
    <link rel="stylesheet" href="<c:url value='/css/workspace.css' />">
    <link rel="stylesheet" href="<c:url value='/css/tournament-workspace.css' />">
    <link rel="stylesheet" href="<c:url value='/css/sidebar.css' />">
    <link rel="stylesheet" href="<c:url value='/css/league/create-season.css?v=6' />">
</head>
<body class="ui-page workspace-page tournament-workspace league-create-season-page">
    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="my-series"/><jsp:param name="themeToggle" value="true"/>
    </jsp:include>
    <div class="tournament-shell">
        <jsp:include page="/WEB-INF/views/league/sidebar.jsp"/>
        <main>
            <p class="text-muted"><c:out value="${leagueSeries.name}" /></p>
            <h1><i class="fa-regular fa-calendar" aria-hidden="true"></i> Thiết lập mùa ${nextNumber}</h1>
            <c:url var="setupUrl" value="/league/season/next"><c:param name="id" value="${leagueSeries.id}" /></c:url>
            <c:url var="teamsUrl" value="/league/season/teams"><c:param name="id" value="${leagueSeries.id}" /></c:url>
            <form method="post" action="<c:out value='${setupUrl}' />">
                <input type="hidden" name="latestId" value="<c:out value='${latestId}' />">
                <input type="hidden" name="revision" value="<c:out value='${revision}' />">
                <section class="league-setup-panel">
                    <h2>Thông tin mùa</h2>
                    <label for="next-season-name">Tên mùa</label>
                    <input id="next-season-name" name="seasonName" class="form-control" required maxlength="255" value="<c:out value='${seasonName}' />">
                    <label class="league-cup-option"><input type="checkbox" name="cup" ${seasonCup ? 'checked' : ''}> Tổ chức Cúp SE cho tất cả đội</label>
                </section>
                <details class="league-setup-panel league-withdrawals">
                    <summary>Đội rút khỏi mùa</summary>
                    <p>Chọn đội rút rồi bấm <strong>Tính lại phân hạng</strong> để xem đội được đôn lên.</p>
                    <div class="league-withdrawal-grid">
                        <c:forEach var="team" items="${registeredTeams}">
                            <label class="league-cup-option">
                                <input type="checkbox" name="withdraw" value="<c:out value='${team.key}' />" ${withdrawals[team.key] ? 'checked' : ''}>
                                <c:out value="${team.value}" />
                            </label>
                        </c:forEach>
                    </div>
                    <button type="submit" name="intent" value="review" class="btn btn-secondary">Tính lại phân hạng</button>
                </details>
                <section class="league-setup-panel">
                    <div class="league-setup-toolbar">
                        <h2>Hạng đấu mùa ${nextNumber}</h2>
                        <a href="<c:out value='${teamsUrl}' />">Đăng ký đội mới</a>
                    </div>
                    <p class="text-muted">Phân hạng theo kết quả <c:out value="${previousName}" />. Đội mới vào hạng thấp nhất.</p>
                    <c:forEach var="legs" items="${seasonLegs}" varStatus="row">
                        <fieldset class="league-division">
                            <legend>Hạng ${row.count}</legend>
                            <label>Số lượt vòng tròn <input class="form-control" name="legs" type="number" min="1" max="10" required value="${legs}"></label>
                            <c:choose>
                                <c:when test="${row.last}"><input type="hidden" name="slots" value="0"></c:when>
                                <c:otherwise>
                                    <label>Hạng ${row.count} xuống / Hạng ${row.count + 1} lên
                                        <input class="form-control" name="slots" type="number" min="0" max="2147483647" required value="${seasonSlots[row.index]}">
                                    </label>
                                </c:otherwise>
                            </c:choose>
                            <ul class="league-division-teams league-division-roster">
                                <c:forEach var="placement" items="${placements}">
                                    <c:if test="${placement.level == row.count}">
                                        <li><span><c:out value="${placement.name}" /></span>
                                            <c:if test="${not empty placement.reason}"><small><c:out value="${placement.reason}" /></small></c:if>
                                        </li>
                                    </c:if>
                                </c:forEach>
                            </ul>
                        </fieldset>
                    </c:forEach>
                </section>
                <c:if test="${not empty setupMessage}"><p id="league-setup-error" role="alert"><c:out value="${setupMessage}" /></p></c:if>
                <button type="submit" name="intent" value="save" id="save-league-season" class="btn btn-mint">Lưu mùa</button>
            </form>
        </main>
    </div>
</body>
</html>
