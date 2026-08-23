<%@page contentType="text/html" pageEncoding="UTF-8" %>
<!-- Dedicated Header Component Stylesheet -->
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/header.css">

<!-- Header / Navigation Bar Component -->
<nav class="navbar">
    <a href="${pageContext.request.contextPath}/index.jsp" class="brand-logo">
        <div class="brand-icon">
            <i class="fa-solid fa-trophy"></i>
        </div>
        <span>TOUR<span class="highlight">MA</span></span>
    </a>

    <ul class="nav-links">
        <!-- 1. Trang Chủ -->
        <li>
            <a href="${pageContext.request.contextPath}/index.jsp"
                class="nav-link ${param.active == 'home' || empty param.active ? 'active' : ''}">
                <i class="fa-solid fa-house"></i> Trang Chủ
            </a>
        </li>

        <!-- 2. Tạo Giải Đấu -->
        <li>
            <a href="${pageContext.request.contextPath}/common/create-tournament.jsp"
                class="nav-link ${param.active == 'create-tournament' ? 'active' : ''}">
                <i class="fa-solid fa-circle-plus"></i> Tạo Giải Đấu
            </a>
        </li>

        <!-- 3. Tạo Chuỗi Giải -->
        <li>
            <a href="${pageContext.request.contextPath}/common/create-series.jsp"
                class="nav-link ${param.active == 'create-series' ? 'active' : ''}">
                <i class="fa-solid fa-folder-plus"></i> Tạo Chuỗi Giải
            </a>
        </li>

        <!-- 4. Giải Đấu Của Tôi -->
        <li>
            <a href="${pageContext.request.contextPath}/common/my-tournaments.jsp"
                class="nav-link ${param.active == 'my-tournaments' ? 'active' : ''}">
                <i class="fa-solid fa-sitemap"></i> Giải Đấu Của Tôi
            </a>
        </li>

        <!-- 5. Chuỗi Giải Của Tôi -->
        <li>
            <a href="${pageContext.request.contextPath}/common/my-series.jsp"
                class="nav-link ${param.active == 'my-series' ? 'active' : ''}">
                <i class="fa-solid fa-layer-group"></i> Chuỗi Giải Của Tôi
            </a>
        </li>
    </ul>
</nav>