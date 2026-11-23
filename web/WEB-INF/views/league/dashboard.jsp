<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><c:out value="${leagueSeries.name}" /> · League · Tourma</title>
    <link rel="icon" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
    <script src="${pageContext.request.contextPath}/js/theme.js?v=default-layout-1"></script>
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=default-layout-1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=18">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/workspace.css?v=21">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-workspace.css?v=18">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css?v=20261031-tabs">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/league/league-dashboard.css">
</head>
<body class="ui-page workspace-page tournament-workspace league-dashboard-page">
    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="my-series"/>
        <jsp:param name="themeToggle" value="true"/>
    </jsp:include>

    <div class="tournament-shell">
        <jsp:include page="/WEB-INF/views/league/sidebar.jsp"/>

        <main class="league-dashboard">
            <header class="league-heading">
                <a class="league-back" href="<c:url value='/my-series' />">
                    <i class="fa-solid fa-arrow-left" aria-hidden="true"></i> Chuỗi giải của tôi
                </a>
                <h1><c:out value="${leagueSeries.name}" /></h1>
                <p>League System</p>
            </header>

            <section class="league-season-panel" id="league-seasons" aria-labelledby="league-seasons-title">
                <h2 id="league-seasons-title">Mùa giải</h2>
                <div class="league-season-empty">
                    <i class="fa-regular fa-calendar" aria-hidden="true"></i>
                    <h3>Chưa có mùa giải</h3>
                    <c:url var="seasonTeamsUrl" value="/league/season/teams"><c:param name="id" value="${leagueSeries.id}" /></c:url>
                    <a class="btn btn-mint" href="<c:out value='${seasonTeamsUrl}' />">Tạo mùa đầu tiên</a>
                </div>
            </section>
        </main>
    </div>
</body>
</html>
