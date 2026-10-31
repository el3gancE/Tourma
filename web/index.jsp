<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi" data-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>TOURMA — Sân chơi của bạn. Dấu ấn của bạn.</title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
    <script src="${pageContext.request.contextPath}/js/theme.js?v=20261031-classic"></script>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=20261031m">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=18">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/index.css?v=20261028-11">
</head>
<body class="ui-page home-page">
    <jsp:include page="common/component/header.jsp">
        <jsp:param name="active" value="home"/>
        <jsp:param name="themeToggle" value="true"/>
    </jsp:include>
    <main class="home-main">
        <div class="home-utility"><span><span class="availability-dot"></span> KHÔNG GIAN TỔ CHỨC GIẢI ĐẤU</span></div>
        <section class="home-hero" aria-labelledby="hero-title">
            <div class="hero-copy">
                <div class="section-kicker"><span class="kicker-line"></span> MỌI CUỘC ĐẤU ĐỀU BẮT ĐẦU TỪ ĐÂY</div>
                <h1 id="hero-title">Sân chơi của bạn.<br><em>Dấu ấn</em> của bạn.</h1>
                <p>Từ một buổi giao hữu đến cả mùa tranh tài. Tạo giải, sắp xếp đội và theo dõi từng vòng đấu trong một không gian.</p>
                <div class="hero-actions"><a class="home-button primary" href="${pageContext.request.contextPath}/common/create-tournament.jsp">Bắt đầu một giải đấu <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></a><a class="home-button secondary" href="${pageContext.request.contextPath}/my-tournaments">Giải đấu của tôi <i class="fa-solid fa-arrow-up-right-from-square" aria-hidden="true"></i></a></div>
                <div class="hero-footnote"><i class="fa-regular fa-circle-check" aria-hidden="true"></i> Bạn chọn thể thức. Tourma lo sơ đồ.</div>
            </div>
        </section>
        <section class="format-strip" aria-label="Các thể thức thi đấu"><span>MỘT NỀN TẢNG.<br>NHIỀU CÁCH TRANH TÀI.</span><div><span>Single Elimination</span><span>Double Elimination</span><span>Round Robin</span><span>Group Stage</span><span>GSL</span><span>Swiss</span></div></section>
        <section class="workspace-section" aria-labelledby="workspace-title">
            <div class="home-section-heading"><div><div class="section-kicker">01 / CHỌN ĐIỂM BẮT ĐẦU</div><h2 id="workspace-title">Một giải đấu. Hay cả một mùa?</h2></div><p>Chọn không gian phù hợp với kế hoạch của bạn.</p></div>
            <div class="workspace-grid">
                <article class="workspace-card tournament-workspace"><div class="workspace-card-top"><span class="workspace-icon"><i class="fa-solid fa-sitemap" aria-hidden="true"></i></span><span class="workspace-number">01</span></div><h3>Tập trung vào cuộc đấu.</h3><p>Tạo giải độc lập, đưa các đội vào cuộc và quản lý kết quả từ vòng đầu đến trận cuối.</p><div class="workspace-tags"><span>Ghép cặp &amp; hạt giống</span><span>Sơ đồ &amp; kết quả</span></div><a class="workspace-link" href="${pageContext.request.contextPath}/common/create-tournament.jsp">Tạo giải đấu <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></a></article>
                <article class="workspace-card series-workspace"><div class="workspace-card-top"><span class="workspace-icon"><i class="fa-solid fa-layer-group" aria-hidden="true"></i></span><span class="workspace-number">02</span></div><h3>Xây dựng cả hành trình.</h3><p>Kết nối nhiều giải trong một Series. Theo dõi điểm tích lũy và thứ hạng xuyên suốt mùa giải.</p><div class="workspace-tags"><span>Rolling Window</span><span>FIFA Elo Rating</span></div><a class="workspace-link" href="${pageContext.request.contextPath}/common/create-series.jsp">Tạo chuỗi giải <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></a></article>
                <aside class="returning-card"><span class="returning-symbol"><i class="fa-solid fa-arrow-rotate-left" aria-hidden="true"></i></span><h3>Tiếp tục <br>cuộc hành trình.</h3><p>Đã có giải đấu?<br>Trở lại nơi bạn đang tổ chức.</p><a href="${pageContext.request.contextPath}/my-tournaments">Giải đấu của tôi <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></a><a href="${pageContext.request.contextPath}/my-series">Chuỗi giải của tôi <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></a></aside>
            </div>
        </section>
        <section class="journey-section" aria-labelledby="journey-title"><div class="journey-intro"><div class="section-kicker">02 / TỪ Ý TƯỞNG ĐẾN TRẬN ĐẤU</div><h2 id="journey-title">Sẵn sàng.<br>Vào cuộc.</h2><a href="${pageContext.request.contextPath}/common/create-tournament.jsp">Tạo giải đầu tiên <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></a></div><ol class="journey-steps"><li><span>01</span><div><h3>Chọn thể thức</h3><p>Thiết lập cách tranh tài cho giải đấu của bạn.</p></div></li><li><span>02</span><div><h3>Đưa các đội vào cuộc</h3><p>Thêm đội, sắp xếp hạt giống và chia bảng theo thể thức.</p></div></li><li><span>03</span><div><h3>Theo dõi từng vòng đấu</h3><p>Nhập kết quả, xem thứ hạng và tìm ra nhà vô địch.</p></div></li></ol></section>
    </main>
    <footer class="home-footer"><a href="${pageContext.request.contextPath}/index.jsp">TOURMA<span>.</span></a><p>Cho những cuộc đấu đáng nhớ.</p><span>© 2026 TOURMA</span></footer>
</body>
</html>
