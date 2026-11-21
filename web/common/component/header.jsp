<%@page contentType="text/html" pageEncoding="UTF-8" %>
<%
    // Match the page being viewed, not its parent section or include parameters.
    String headerRoute = (String) request.getAttribute("jakarta.servlet.forward.request_uri");
    if (headerRoute == null) headerRoute = request.getRequestURI();
    headerRoute = headerRoute.substring(request.getContextPath().length());
    String headerActive = "";
    if ("/".equals(headerRoute) || "/index.jsp".equals(headerRoute)) headerActive = "home";
    else if ("/common/create-tournament.jsp".equals(headerRoute)) headerActive = "create-tournament";
    else if ("/common/create-series.jsp".equals(headerRoute)) headerActive = "create-series";
    else if ("/my-tournaments".equals(headerRoute) && !"matches".equals(request.getParameter("action"))) headerActive = "my-tournaments";
    else if ("/my-series".equals(headerRoute)) headerActive = "my-series";
    pageContext.setAttribute("headerActive", headerActive);
%>
<jsp:include page="/common/component/tournament-config-state.jsp" />
<% if (!"home".equals(headerActive)) { %>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=mono-1">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/tourma-shell.css?v=landing-1">
<% } %>
<div class="tourma-silk" aria-hidden="true"></div>
<nav class="navbar" aria-label="Điều hướng Tourma">
    <a href="${pageContext.request.contextPath}/index.jsp" class="brand-logo">
        <span>Tourma</span>
    </a>

    <ul class="nav-links">
        <!-- 1. Trang Chủ -->
        <li>
            <a href="${pageContext.request.contextPath}/index.jsp"
                class="nav-link ${headerActive == 'home' ? 'active' : ''}">
                <i class="fa-solid fa-house"></i> Trang Chủ
            </a>
        </li>

        <!-- 2. Tạo Giải Đấu -->
        <li>
            <a href="${pageContext.request.contextPath}/common/create-tournament.jsp"
                class="nav-link ${headerActive == 'create-tournament' ? 'active' : ''}">
                <i class="fa-solid fa-circle-plus"></i> Tạo Giải Đấu
            </a>
        </li>

        <!-- 3. Tạo Chuỗi Giải -->
        <li>
            <a href="${pageContext.request.contextPath}/common/create-series.jsp"
                class="nav-link ${headerActive == 'create-series' ? 'active' : ''}">
                <i class="fa-solid fa-folder-plus"></i> Tạo Chuỗi Giải
            </a>
        </li>

        <!-- 4. Giải Đấu Của Tôi (Calls Servlet /my-tournaments to query DB) -->
        <li>
            <a href="${pageContext.request.contextPath}/my-tournaments"
                class="nav-link ${headerActive == 'my-tournaments' ? 'active' : ''}">
                <i class="fa-solid fa-sitemap"></i> Giải Đấu Của Tôi
            </a>
        </li>

        <!-- 5. Chuỗi Giải Của Tôi (Calls Servlet /my-series to query DB) -->
        <li>
            <a href="${pageContext.request.contextPath}/my-series"
                class="nav-link ${headerActive == 'my-series' ? 'active' : ''}">
                <i class="fa-solid fa-layer-group"></i> Chuỗi Giải Của Tôi
            </a>
        </li>
    </ul>
    <% if ("true".equals(request.getParameter("themeToggle"))) { %>
    <div class="appearance-switches" role="group" aria-label="Giao diện">
        <button type="button" data-silk-toggle aria-pressed="true">Nền động</button>
        <button type="button" class="theme-toggle" data-theme-toggle><span data-theme-label>Đổi giao diện</span></button>
        <% if (!"home".equals(headerActive)) { %>
        <details class="display-settings">
            <summary class="theme-toggle">Hiển thị</summary>
            <div class="display-settings-panel" role="group" aria-label="Cài đặt hiển thị">
                <strong>Cài đặt hiển thị</strong>
                <button type="button" class="special-colors-toggle" role="switch" aria-checked="true" data-special-colors-toggle>
                    <span>Màu phân biệt nhánh/nhóm</span><span class="theme-switch" aria-hidden="true"></span>
                </button>
                <p>Áp dụng cho nhánh đấu, final, các nhóm Swiss và bảng GSL.</p>
            </div>
        </details>
        <% } %>
    </div>
    <% } %>
</nav>

<script>
(function () {
    var header = document.querySelector('nav.navbar');
    if (!header) return;
    var currentLink = header.querySelector('.nav-link.active');
    if (currentLink) currentLink.setAttribute('aria-current', 'page');
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
