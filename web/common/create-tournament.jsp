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
    <title>Tạo giải đấu — TOURMA</title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
    <script src="${pageContext.request.contextPath}/js/theme.js?v=20261031-classic"></script>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=20261031m">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=18">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/workspace.css?v=18">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/create-tournament.css?v=3">
</head>
<body class="ui-page workspace-page create-page">
    <jsp:include page="/common/component/header.jsp"><jsp:param name="active" value="create-tournament"/><jsp:param name="themeToggle" value="true"/></jsp:include>
    <main class="workspace-main create-main">
        <nav class="create-breadcrumb" aria-label="Đường dẫn"><a href="${pageContext.request.contextPath}/my-tournaments">Giải đấu của tôi</a><span aria-hidden="true">/</span><span>Tạo giải đấu</span></nav>
        <div class="create-layout">
            <section class="create-form-panel" aria-labelledby="form-title">
                <div class="create-panel-heading"><h1 id="form-title">Tạo giải đấu</h1><span class="create-required-note">* Bắt buộc</span></div>
                <form id="createTournamentForm" action="${pageContext.request.contextPath}/create-tournament" method="POST">
                    <c:if test="${not empty errorMessage}"><div class="create-error" role="alert"><i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i><c:out value="${errorMessage}" /></div></c:if>
                    <div class="create-fields">
                        <div class="create-field"><div class="create-label"><label for="tournamentName">Tên giải đấu <span class="required-mark" aria-hidden="true">*</span></label></div><div class="create-control"><input type="text" id="tournamentName" name="name" class="form-control" placeholder="Ví dụ: Hanoi Open 2026" value="<c:out value="${param.name}" />" required autocomplete="off"></div></div>
                        <div class="create-field"><div class="create-label"><label for="tournamentDescription">Mô tả</label><span>Không bắt buộc</span></div><div class="create-control"><textarea id="tournamentDescription" name="description" class="form-control" rows="5" placeholder="Nhập mô tả…"><c:out value="${param.description}" /></textarea><div class="create-description-footer"><span id="descriptionCount" aria-hidden="true">0 ký tự</span></div></div></div>
                    </div>
                    <footer class="create-form-footer"><a class="create-cancel" href="${pageContext.request.contextPath}/my-tournaments">Hủy</a><button class="btn btn-mint" id="createSubmit" type="submit">Tạo &amp; tiếp tục <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></button></footer>
                </form>
            </section>
        </div>
    </main>
    <script src="${pageContext.request.contextPath}/js/create-tournament.js?v=2"></script>
</body>
</html>
