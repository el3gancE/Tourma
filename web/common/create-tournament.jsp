<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%
    String sIdParam = request.getParameter("seriesId");
    if (sIdParam != null && !sIdParam.trim().isEmpty()) {
        response.sendRedirect(request.getContextPath() + "/rolling/create-tournament?seriesId=" + sIdParam.trim());
        return;
    }
%>
<!DOCTYPE html>
<html lang="vi" data-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Tạo giải đấu | Tourma</title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
    <script src="${pageContext.request.contextPath}/js/theme.js?v=silk-1"></script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/create-flow.css?v=1">
</head>
<body class="ui-page create-flow-page">
    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="create-tournament"/>
        <jsp:param name="themeToggle" value="true"/>
    </jsp:include>
    <main class="create-flow">
        <a class="create-flow-back" href="${pageContext.request.contextPath}/my-tournaments"><span aria-hidden="true">←</span> Giải Đấu Của Tôi</a>
        <header class="create-flow-heading"><h1>Tạo giải đấu</h1><p>Đặt tên giải để bắt đầu thiết lập.</p></header>
        <div class="create-flow-grid">
            <form class="create-flow-panel" id="createTournamentForm" action="${pageContext.request.contextPath}/create-tournament" method="POST">
                <header class="create-flow-panel-heading"><h2>Thông tin giải</h2><small>* Bắt buộc</small></header>
                <div class="create-flow-body">
                    <c:if test="${not empty errorMessage}"><p class="create-flow-error" role="alert"><c:out value="${errorMessage}"/></p></c:if>
                    <div class="create-flow-field">
                        <label for="tournamentName">Tên giải đấu <span aria-hidden="true">*</span></label>
                        <input type="text" id="tournamentName" name="name" placeholder="Ví dụ: Hanoi Open 2026" value="<c:out value='${param.name}'/>" required autocomplete="off">
                    </div>
                    <div class="create-flow-field">
                        <label for="tournamentDescription">Mô tả <small>Không bắt buộc</small></label>
                        <textarea id="tournamentDescription" name="description" rows="4" placeholder="Nhập mô tả…"><c:out value="${param.description}"/></textarea>
                        <small class="create-flow-count" id="descriptionCount" aria-hidden="true"></small>
                    </div>
                </div>
                <footer class="create-flow-actions">
                    <a href="${pageContext.request.contextPath}/my-tournaments">Hủy</a>
                    <button id="createSubmit" type="submit">Tạo &amp; tiếp tục <span aria-hidden="true">→</span></button>
                </footer>
            </form>
            <aside class="create-flow-aside" aria-labelledby="tournament-steps">
                <h2 id="tournament-steps">Các bước thiết lập</h2>
                <ol>
                    <li aria-current="step">Thông tin giải</li>
                    <li>Thể thức và đội tham gia</li>
                    <li>Chia bảng nếu cần</li>
                    <li>Thi đấu</li>
                </ol>
            </aside>
        </div>
    </main>
    <script src="${pageContext.request.contextPath}/js/create-tournament.js?v=3"></script>
</body>
</html>
