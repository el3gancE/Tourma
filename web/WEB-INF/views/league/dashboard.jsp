<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><c:out value="${empty leagueSeasonDashboard ? leagueSeries.name : leagueSeasonDashboard.name}" /> · League · Tourma</title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
    <script src="${pageContext.request.contextPath}/js/theme.js?v=default-layout-1"></script>
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=default-layout-1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=18">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/workspace.css?v=21">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-workspace.css?v=18">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css?v=20261031-tabs">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/league/league-dashboard.css?v=6">
</head>
<body class="ui-page workspace-page tournament-workspace league-dashboard-page">
    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="my-series"/>
        <jsp:param name="themeToggle" value="true"/>
    </jsp:include>

    <div class="tournament-shell">
        <jsp:include page="/WEB-INF/views/league/sidebar.jsp"/>

        <main class="league-dashboard">
            <c:url var="seriesUrl" value="/league/dashboard"><c:param name="id" value="${leagueSeries.id}" /></c:url>
            <c:url var="setupUrl" value="/league/season/create"><c:param name="id" value="${leagueSeries.id}" /></c:url>
            <c:url var="nextSeasonUrl" value="/league/season/next"><c:param name="id" value="${leagueSeries.id}" /></c:url>
            <c:choose>
                <c:when test="${empty leagueSeasonDashboard}">
                    <header class="league-heading">
                        <a class="league-back" href="<c:url value='/my-series' />"><i class="fa-solid fa-arrow-left" aria-hidden="true"></i> Chuỗi giải của tôi</a>
                        <h1><c:out value="${leagueSeries.name}" /></h1>
                        <p>League System</p>
                    </header>
                    <section class="league-season-panel" id="league-seasons" aria-labelledby="league-seasons-title">
                        <h2 id="league-seasons-title"><i class="fa-regular fa-calendar" aria-hidden="true"></i> Mùa giải</h2>
                        <c:choose>
                            <c:when test="${empty leagueSeasons}">
                                <div class="league-season-empty">
                                    <h3>Chưa có mùa giải</h3>
                                    <a class="btn btn-mint" href="<c:out value='${setupUrl}' />">Tạo mùa đầu tiên</a>
                                </div>
                            </c:when>
                            <c:otherwise>
                                <ul class="league-season-list">
                                    <c:forEach var="season" items="${leagueSeasons}">
                                        <c:url var="seasonUrl" value="/league/season/dashboard">
                                            <c:param name="id" value="${leagueSeries.id}" /><c:param name="seasonId" value="${season.id}" />
                                        </c:url>
                                        <li>
                                            <a class="league-season-index-row" href="<c:out value='${seasonUrl}' />">
                                                <strong><c:out value="${season.name}" /></strong>
                                                <span>${season.status == 'DRAFT' ? 'Chưa mở mùa' : (season.status == 'ACTIVE' ? 'Đang diễn ra' : 'Đã chốt')}</span>
                                                <i class="fa-solid fa-chevron-right" aria-hidden="true"></i>
                                            </a>
                                        </li>
                                    </c:forEach>
                                </ul>
                            </c:otherwise>
                        </c:choose>
                    </section>
                </c:when>
                <c:otherwise>
                    <c:set var="season" value="${leagueSeasonDashboard}" />
                    <c:url var="reviewUrl" value="/league/dashboard">
                        <c:param name="id" value="${leagueSeries.id}" /><c:param name="reviewSeason" value="${season.id}" />
                    </c:url>
                    <header class="league-heading">
                        <a class="league-back" href="<c:out value='${seriesUrl}' />"><i class="fa-solid fa-arrow-left" aria-hidden="true"></i> <c:out value="${leagueSeries.name}" /></a>
                        <h1><c:out value="${season.name}" /></h1>
                        <div class="league-season-row">
                            <span class="league-season-status">${season.status == 'DRAFT' ? 'Chưa mở mùa' : (season.status == 'ACTIVE' ? 'Đang diễn ra' : 'Đã chốt')}</span>
                            <c:if test="${season.status == 'DRAFT' and season.id == leagueLatestSeason.id}">
                                <a class="btn btn-secondary" href="<c:out value='${setupUrl}' />">Thiết lập mùa</a>
                                <c:if test="${not empty leagueCurrentDivisions}">
                                    <form method="post" action="<c:out value='${seriesUrl}' />"
                                          onsubmit="return confirm('Mở mùa và tạo các giải đấu? Danh sách và phân hạng sẽ được khóa.');">
                                        <input type="hidden" name="action" value="openSeason">
                                        <input type="hidden" name="seasonId" value="<c:out value='${season.id}' />">
                                        <button type="submit" class="btn btn-mint">Mở mùa</button>
                                    </form>
                                </c:if>
                            </c:if>
                            <c:if test="${season.status == 'ACTIVE' and empty leagueCurrentPending}">
                                <a class="btn btn-mint" href="<c:out value='${reviewUrl}' />">Chốt mùa</a>
                            </c:if>
                            <c:if test="${season.status == 'COMPLETED'}">
                                <a class="btn btn-secondary" href="<c:out value='${reviewUrl}' />">Kết quả chuyển hạng</a>
                                <c:if test="${season.id == leagueLatestSeason.id}">
                                    <a class="btn btn-mint" href="<c:out value='${nextSeasonUrl}' />">Tạo mùa kế tiếp</a>
                                </c:if>
                            </c:if>
                        </div>
                    </header>
                    <section aria-labelledby="league-competitions-title">
                        <h2 id="league-competitions-title" class="league-competitions-title">Nội dung thi đấu</h2>
                        <c:if test="${not empty leagueCurrentPending}">
                            <p class="league-pending">Chưa hoàn tất:
                                <c:forEach var="event" items="${leagueCurrentPending}" varStatus="item"><c:if test="${not item.first}">, </c:if><c:out value="${event}" /></c:forEach>
                            </p>
                        </c:if>
                        <div class="league-result-grid">
                            <c:forEach var="division" items="${leagueCurrentDivisions}">
                                <article class="league-competition-card">
                                    <h3><i class="fa-solid fa-layer-group" aria-hidden="true"></i> Hạng ${division.level}</h3>
                                    <p>${division.teamCount} đội · ${division.legs} lượt vòng tròn</p>
                                    <c:if test="${not empty division.tournamentId}">
                                        <c:url var="divisionUrl" value="/my-tournaments"><c:param name="action" value="matches" /><c:param name="id" value="${division.tournamentId}" /></c:url>
                                        <a class="btn btn-secondary" href="<c:out value='${divisionUrl}' />" aria-label="Mở giải Hạng ${division.level}">Mở giải</a>
                                    </c:if>
                                </article>
                            </c:forEach>
                            <c:if test="${season.cup}">
                                <article class="league-competition-card">
                                    <h3><i class="fa-solid fa-trophy" aria-hidden="true"></i> Cúp SE</h3>
                                    <p>Loại trực tiếp · Tất cả đội trong mùa</p>
                                    <c:if test="${not empty season.cupId}">
                                        <c:url var="cupUrl" value="/my-tournaments"><c:param name="action" value="matches" /><c:param name="id" value="${season.cupId}" /></c:url>
                                        <a class="btn btn-secondary" href="<c:out value='${cupUrl}' />" aria-label="Mở giải Cúp SE">Mở giải</a>
                                    </c:if>
                                </article>
                            </c:if>
                        </div>
                    </section>
                </c:otherwise>
            </c:choose>
        </main>
    </div>
</body>
</html>
