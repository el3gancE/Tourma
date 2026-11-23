<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="tourma" tagdir="/WEB-INF/tags"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <tourma:page-title label="${empty leagueSeasonDashboard ? 'Tổng quan League' : 'Tổng quan mùa'}" contextName="${empty leagueSeasonDashboard ? leagueSeries.name : leagueSeasonDashboard.name}" />
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
    <script src="${pageContext.request.contextPath}/js/theme.js?v=default-layout-1"></script>
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=default-layout-1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=18">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/workspace.css?v=21">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-workspace.css?v=18">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css?v=20261031-tabs">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/league/league-dashboard.css?v=8">
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
                    <header class="league-heading league-season-heading">
                        <a class="league-back" href="<c:url value='/my-series' />"><i class="fa-solid fa-arrow-left" aria-hidden="true"></i> Chuỗi giải của tôi</a>
                        <h1><c:out value="${leagueSeries.name}" /></h1>
                    </header>
                    <dl class="league-season-metrics" aria-label="Tổng quan League">
                        <div>
                            <dt><i class="fa-solid fa-users" aria-hidden="true"></i> Đội đã đăng ký</dt>
                            <dd>${leagueRegisteredTeamCount}</dd>
                        </div>
                        <div>
                            <dt><i class="fa-regular fa-calendar" aria-hidden="true"></i> Số mùa</dt>
                            <dd>${fn:length(leagueSeasons)}</dd>
                        </div>
                        <div>
                            <dt><i class="fa-regular fa-circle-check" aria-hidden="true"></i> Mùa đã chốt</dt>
                            <dd>${leagueClosedSeasonCount}</dd>
                        </div>
                    </dl>
                    <div id="league-seasons">
                        <c:choose>
                            <c:when test="${empty leagueSeasons}">
                                <div class="league-season-empty league-season-panel">
                                    <h3>Chưa có mùa giải</h3>
                                    <a class="btn btn-mint" href="<c:out value='${setupUrl}' />">Tạo mùa đầu tiên</a>
                                </div>
                            </c:when>
                            <c:otherwise>
                                <c:url var="latestUrl" value="/league/season/dashboard">
                                    <c:param name="id" value="${leagueSeries.id}" /><c:param name="seasonId" value="${leagueLatestSeason.id}" />
                                </c:url>
                                <section class="league-season-panel league-series-feature" aria-labelledby="league-latest-title">
                                    <h2 id="league-latest-title"><i class="fa-regular fa-calendar" aria-hidden="true"></i> Mùa gần nhất</h2>
                                    <div class="league-series-feature-body">
                                        <div class="league-season-row">
                                            <h3><c:out value="${leagueLatestSeason.name}" /></h3>
                                            <span class="league-season-status" data-status="${leagueLatestSeason.status}">
                                                ${leagueLatestSeason.status == 'DRAFT' ? 'Chưa mở mùa' : (leagueLatestSeason.status == 'ACTIVE' ? 'Đang diễn ra' : 'Đã chốt')}
                                            </span>
                                            <c:if test="${leagueLatestSeason.cup}"><span class="league-season-status">Cúp SE</span></c:if>
                                        </div>
                                        <div class="league-competition-progress">
                                            <span>${leagueLatestFinishedEvents}/${leagueLatestEventCount} giải hoàn tất · ${leagueLatestFinishedMatches}/${leagueLatestExpectedMatches} trận có kết quả</span>
                                            <progress max="${leagueLatestExpectedMatches gt 0 ? leagueLatestExpectedMatches : 1}" value="${leagueLatestFinishedMatches}" aria-label="Tiến độ mùa gần nhất"></progress>
                                        </div>
                                        <div class="league-season-row">
                                            <a class="btn btn-mint" href="<c:out value='${latestUrl}' />">Vào mùa <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></a>
                                            <c:if test="${leagueLatestSeason.status == 'COMPLETED'}">
                                                <a class="btn btn-secondary" href="<c:out value='${nextSeasonUrl}' />">Tạo mùa kế tiếp</a>
                                            </c:if>
                                        </div>
                                    </div>
                                </section>
                                <c:if test="${fn:length(leagueSeasons) gt 1}">
                                    <section class="league-season-panel" aria-labelledby="league-season-history">
                                        <h2 id="league-season-history"><i class="fa-solid fa-clock-rotate-left" aria-hidden="true"></i> Các mùa trước</h2>
                                        <ul class="league-season-list">
                                            <c:forEach var="season" items="${leagueSeasons}" begin="1">
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
                                    </section>
                                </c:if>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </c:when>
                <c:otherwise>
                    <c:set var="season" value="${leagueSeasonDashboard}" />
                    <c:url var="reviewUrl" value="/league/dashboard">
                        <c:param name="id" value="${leagueSeries.id}" /><c:param name="reviewSeason" value="${season.id}" />
                    </c:url>
                    <header class="league-heading league-season-heading">
                        <a class="league-back" href="<c:out value='${seriesUrl}' />"><i class="fa-solid fa-arrow-left" aria-hidden="true"></i> <c:out value="${leagueSeries.name}" /></a>
                        <div class="league-season-row">
                            <h1><c:out value="${season.name}" /></h1>
                            <span class="league-season-status" data-status="${season.status}">${season.status == 'DRAFT' ? 'Chưa mở mùa' : (season.status == 'ACTIVE' ? 'Đang diễn ra' : 'Đã chốt')}</span>
                        </div>
                        <div class="league-season-row">
                            <c:if test="${season.status == 'DRAFT' and season.id == leagueLatestSeason.id}">
                                <a class="btn btn-secondary" href="<c:out value='${setupUrl}' />">Thiết lập mùa</a>
                                <c:if test="${not empty leagueCompetitionStats}">
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
                    <dl class="league-season-metrics" aria-label="Tổng quan tiến độ">
                        <div>
                            <dt><i class="fa-solid fa-users" aria-hidden="true"></i> Đội tham gia</dt>
                            <dd>${leagueSeasonTeamCount}</dd>
                        </div>
                        <div>
                            <dt><i class="fa-regular fa-circle-check" aria-hidden="true"></i> Giải đã hoàn tất</dt>
                            <dd>${leagueSeasonFinishedEvents}<small> / ${fn:length(leagueCompetitionStats)}</small></dd>
                        </div>
                        <div>
                            <dt><i class="fa-solid fa-list-check" aria-hidden="true"></i> Trận đã có kết quả</dt>
                            <dd>${leagueSeasonFinishedMatches}<small> / ${leagueSeasonExpectedMatches}</small></dd>
                        </div>
                    </dl>
                    <section aria-labelledby="league-competitions-title">
                        <h2 id="league-competitions-title" class="league-competitions-title">Nội dung thi đấu</h2>
                        <c:if test="${not empty leagueCurrentPending}">
                            <p class="league-pending">Chưa hoàn tất:
                                <c:forEach var="event" items="${leagueCurrentPending}" varStatus="item"><c:if test="${not item.first}">, </c:if><c:out value="${event}" /></c:forEach>
                            </p>
                        </c:if>
                        <div class="league-result-grid">
                            <c:forEach var="competition" items="${leagueCompetitionStats}">
                                <article class="league-competition-card">
                                    <div class="league-competition-head">
                                        <h3><i class="fa-solid ${competition.level == 0 ? 'fa-trophy' : 'fa-layer-group'}" aria-hidden="true"></i> <c:out value="${competition.label}" /></h3>
                                        <span class="league-competition-status ${competition.complete ? 'is-complete' : ''}">
                                            <c:choose>
                                                <c:when test="${competition.complete}">Hoàn tất</c:when>
                                                <c:when test="${empty competition.tournamentId}">Chưa mở</c:when>
                                                <c:when test="${competition.finished == 0}">Chưa có kết quả</c:when>
                                                <c:when test="${competition.finished == competition.expected}">Chờ xác nhận</c:when>
                                                <c:otherwise>Đang thi đấu</c:otherwise>
                                            </c:choose>
                                        </span>
                                    </div>
                                    <p>${competition.teamCount} đội · <c:choose><c:when test="${competition.level == 0}">Loại trực tiếp</c:when><c:otherwise>${competition.legs} lượt vòng tròn</c:otherwise></c:choose></p>
                                    <div class="league-competition-progress">
                                        <span>${competition.finished} / ${competition.expected} trận có kết quả</span>
                                        <progress max="${competition.expected gt 0 ? competition.expected : 1}" value="${competition.finished}"
                                                  aria-label="Tiến độ thi đấu <c:out value='${competition.label}' />"></progress>
                                    </div>
                                    <c:if test="${competition.complete and not empty competition.champion}">
                                        <p class="league-champion"><i class="fa-solid fa-trophy" aria-hidden="true"></i> Vô địch <strong><c:out value="${competition.champion}" /></strong></p>
                                    </c:if>
                                    <c:if test="${not empty competition.tournamentId}">
                                        <c:url var="competitionUrl" value="/my-tournaments"><c:param name="action" value="matches" /><c:param name="id" value="${competition.tournamentId}" /></c:url>
                                        <a class="btn btn-secondary" href="<c:out value='${competitionUrl}' />" aria-label="Mở giải <c:out value='${competition.label}' />">
                                            ${competition.level == 0 ? 'Nhánh' : 'Lịch'} &amp; tỷ số <i class="fa-solid fa-arrow-right" aria-hidden="true"></i>
                                        </a>
                                    </c:if>
                                </article>
                            </c:forEach>
                        </div>
                    </section>
                </c:otherwise>
            </c:choose>
        </main>
    </div>
</body>
</html>
