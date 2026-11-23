<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="tourma" tagdir="/WEB-INF/tags"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <tourma:page-title label="${leagueReviewClosed ? 'Kết quả chuyển hạng' : 'Chốt mùa'}" contextName="${leagueReviewName}" />
    <link rel="icon" type="image/svg+xml" href="<c:url value='/images/trophy-gradient-icon.svg' />">
    <script src="<c:url value='/js/theme.js' />"></script>
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="<c:url value='/css/design-tokens.css' />">
    <link rel="stylesheet" href="<c:url value='/css/ui-base.css' />">
    <link rel="stylesheet" href="<c:url value='/css/workspace.css' />">
    <link rel="stylesheet" href="<c:url value='/css/tournament-workspace.css' />">
    <link rel="stylesheet" href="<c:url value='/css/sidebar.css' />">
    <link rel="stylesheet" href="<c:url value='/css/league/league-dashboard.css?v=5' />">
</head>
<body class="ui-page workspace-page tournament-workspace series-page league-dashboard-page">
    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="my-series"/>
        <jsp:param name="themeToggle" value="true"/>
    </jsp:include>
    <c:url var="dashboardUrl" value="/league/dashboard"><c:param name="id" value="${leagueSeries.id}" /></c:url>
    <c:url var="seasonUrl" value="/league/season/dashboard">
        <c:param name="id" value="${leagueSeries.id}" /><c:param name="seasonId" value="${leagueReviewId}" />
    </c:url>
    <div class="tournament-shell">
        <jsp:include page="/WEB-INF/views/league/sidebar.jsp"/>
        <main class="league-dashboard">
            <header class="league-heading">
                <a class="league-back" href="<c:out value='${seasonUrl}' />"><i class="fa-solid fa-arrow-left" aria-hidden="true"></i> Tổng quan mùa</a>
                <h1>${leagueReviewClosed ? 'Kết quả chuyển hạng' : 'Chốt mùa'}</h1>
                <p><c:out value="${leagueReviewName}" /></p>
            </header>
            <c:choose>
                <c:when test="${not empty leagueReviewPending}">
                    <p>Chưa hoàn tất:
                        <c:forEach var="event" items="${leagueReviewPending}" varStatus="item"><c:if test="${not item.first}">, </c:if><c:out value="${event}" /></c:forEach>
                    </p>
                </c:when>
                <c:when test="${empty leagueResults}"><p>Mùa này chưa có bảng kết quả chuyển hạng đã lưu.</p></c:when>
                <c:otherwise>
                    <div class="league-result-grid">
                        <section class="league-result-card" aria-labelledby="league-champions">
                            <h2 id="league-champions"><i class="fa-solid fa-trophy" aria-hidden="true"></i> Vô địch từng hạng</h2>
                            <ul>
                                <c:forEach var="result" items="${leagueResults}">
                                    <c:if test="${result.rank == 1}">
                                        <li><strong><c:out value="${result.name}" /></strong><span>Hạng ${result.level}</span></li>
                                    </c:if>
                                </c:forEach>
                            </ul>
                        </section>
                        <section class="league-result-card" aria-labelledby="league-promoted">
                            <h2 id="league-promoted"><i class="fa-solid fa-arrow-up league-promotion" aria-hidden="true"></i> Lên hạng</h2>
                            <c:set var="hasPromotion" value="${false}" />
                            <ul>
                                <c:forEach var="result" items="${leagueResults}">
                                    <c:if test="${result.next lt result.level}">
                                        <c:set var="hasPromotion" value="${true}" />
                                        <li><strong><c:out value="${result.name}" /></strong><span>Hạng ${result.level} → Hạng ${result.next}</span></li>
                                    </c:if>
                                </c:forEach>
                                <c:if test="${not hasPromotion}"><li>Không có đội lên hạng.</li></c:if>
                            </ul>
                        </section>
                        <section class="league-result-card" aria-labelledby="league-relegated">
                            <h2 id="league-relegated"><i class="fa-solid fa-arrow-down league-relegation" aria-hidden="true"></i> Xuống hạng</h2>
                            <c:set var="hasRelegation" value="${false}" />
                            <ul>
                                <c:forEach var="result" items="${leagueResults}">
                                    <c:if test="${result.next gt result.level}">
                                        <c:set var="hasRelegation" value="${true}" />
                                        <li><strong><c:out value="${result.name}" /></strong><span>Hạng ${result.level} → Hạng ${result.next}</span></li>
                                    </c:if>
                                </c:forEach>
                                <c:if test="${not hasRelegation}"><li>Không có đội xuống hạng.</li></c:if>
                            </ul>
                        </section>
                    </div>
                    <c:if test="${not leagueReviewClosed}">
                        <form class="league-close-form" method="post" action="<c:out value='${dashboardUrl}' />">
                            <input type="hidden" name="action" value="closeSeason">
                            <input type="hidden" name="seasonId" value="<c:out value='${leagueReviewId}' />">
                            <input type="hidden" name="revision" value="<c:out value='${leagueRevision}' />">
                            <button type="submit" class="btn btn-mint">Xác nhận chốt mùa</button>
                        </form>
                    </c:if>
                </c:otherwise>
            </c:choose>
        </main>
    </div>
</body>
</html>
