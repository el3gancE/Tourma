<%@page contentType="text/html" pageEncoding="UTF-8" %>
<jsp:include page="/common/component/tournament-config-state.jsp" />
<!-- Dedicated Header Component Stylesheet -->
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/header.css?v=2">

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

        <!-- 4. Giải Đấu Của Tôi (Calls Servlet /my-tournaments to query DB) -->
        <li>
            <a href="${pageContext.request.contextPath}/my-tournaments"
                class="nav-link ${param.active == 'my-tournaments' ? 'active' : ''}">
                <i class="fa-solid fa-sitemap"></i> Giải Đấu Của Tôi
            </a>
        </li>

        <!-- 5. Chuỗi Giải Của Tôi (Calls Servlet /my-series to query DB) -->
        <li>
            <a href="${pageContext.request.contextPath}/my-series"
                class="nav-link ${param.active == 'my-series' ? 'active' : ''}">
                <i class="fa-solid fa-layer-group"></i> Chuỗi Giải Của Tôi
            </a>
        </li>

        <!-- 6. Template UI Showcase -->
        <li>
            <a href="${pageContext.request.contextPath}/common/template.jsp"
                class="nav-link ${param.active == 'template' ? 'active' : ''}">
                <i class="fa-solid fa-palette"></i> Template UI
            </a>
        </li>
    </ul>
    <% if ("true".equals(request.getParameter("themeToggle"))) { %>
    <button class="theme-toggle" type="button" data-theme-toggle aria-label="Chuyển giao diện sáng tối"><i class="fa-solid fa-moon" aria-hidden="true"></i><span data-theme-label>Giao diện tối</span><span class="theme-switch" aria-hidden="true"></span></button>
    <% } %>
</nav>

<script>
(function () {
    var header = document.querySelector('nav.navbar');
    if (!header) return;
    function updateHeaderHeight() {
        document.documentElement.style.setProperty('--tourma-header-height', Math.ceil(header.getBoundingClientRect().height) + 'px');
    }
    updateHeaderHeight();
    if (window.ResizeObserver) new ResizeObserver(updateHeaderHeight).observe(header);
    else window.addEventListener('resize', updateHeaderHeight);
    window.addEventListener('load', updateHeaderHeight);

    var previousScroll = Math.max(0, window.scrollY);
    var scrollFramePending = false;
    window.addEventListener('scroll', function () {
        if (scrollFramePending) return;
        scrollFramePending = true;
        window.requestAnimationFrame(function () {
            scrollFramePending = false;
            var currentScroll = Math.max(0, window.scrollY);
            var nearTop = currentScroll <= header.getBoundingClientRect().height;
            if (!nearTop && Math.abs(currentScroll - previousScroll) < 4) return;
            var hide = !nearTop && currentScroll < previousScroll;
            document.querySelectorAll('main.container > .tournament-navbar-control-bar, main.container > .rr-control-bar').forEach(function (navbar) {
                navbar.classList.toggle('is-scroll-hidden', hide && !navbar.contains(document.activeElement));
            });
            previousScroll = currentScroll;
        });
    }, { passive: true });
})();
</script>
