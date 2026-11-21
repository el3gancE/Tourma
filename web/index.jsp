<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi" data-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Tourma | Trang chủ</title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
    <script src="${pageContext.request.contextPath}/js/theme.js?v=silk-1"></script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=mono-1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/tourma-shell.css?v=landing-1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/home.css?v=promo-1">
</head>
<body class="ui-page home-page">
    <a class="home-skip" href="#content">Đến nội dung</a>
    <jsp:include page="common/component/header.jsp">
        <jsp:param name="active" value="home"/>
        <jsp:param name="themeToggle" value="true"/>
    </jsp:include>
    <main class="home-main" id="content">
        <section class="landing-hero" aria-labelledby="landing-title">
            <div class="landing-copy">
                <p class="landing-kicker">Từ giải độc lập đến cả mùa thi đấu</p>
                <h1 id="landing-title">Tổ chức giải.<br> <em>Vào trận.</em></h1>
                <p>Tạo giải, xếp đội và theo dõi kết quả qua từng vòng. Khi giải lớn dần, kết nối nhiều chặng và tính điểm cả mùa trong Tourma.</p>
                <div class="landing-actions">
                    <a class="landing-button primary" href="${pageContext.request.contextPath}/common/create-tournament.jsp">Tạo giải đấu <span aria-hidden="true">↗</span></a>
                    <a class="landing-button" href="#how-it-works">Xem cách hoạt động <span aria-hidden="true">↓</span></a>
                </div>
            </div>
            <figure class="landing-showcase">
                <figcaption><strong>City Open</strong><span>Giải minh họa</span></figcaption>
                <div class="landing-round"><span>Loại trực tiếp</span><span>Bán kết</span></div>
                <div class="landing-fixture"><span>Saigon Phoenix</span><b>2</b><span>Lotus Club</span><b>0</b></div>
                <div class="landing-fixture"><span>Hanoi Wolves</span><b>2</b><span>Danang Tide</span><b>1</b></div>
                <p class="landing-final"><strong>Chung kết</strong><span>Saigon Phoenix <small>vs</small> Hanoi Wolves</span></p>
            </figure>
        </section>
        <section class="landing-formats" aria-labelledby="landing-formats-title">
            <h2 id="landing-formats-title">Thể thức hỗ trợ</h2>
            <ul>
                <li>Loại trực tiếp</li><li>Loại kép</li><li>Vòng tròn</li>
                <li>Vòng bảng</li><li>GSL</li><li>Swiss</li>
            </ul>
        </section>
        <section class="landing-process" id="how-it-works" aria-labelledby="landing-process-title">
            <header><h2 id="landing-process-title">Từ danh sách đội đến kết quả</h2></header>
            <ol>
                <li><h3>Chọn thể thức</h3><p>Thiết lập một hoặc hai giai đoạn theo cách bạn muốn các đội đi tiếp.</p></li>
                <li><h3>Thêm đội và xếp hạt giống</h3><p>Nhập danh sách đội, sắp thứ tự và chia bảng nếu thể thức yêu cầu.</p></li>
                <li><h3>Theo dõi từng vòng đấu</h3><p>Cập nhật tỉ số, xem sơ đồ và tra bảng xếp hạng sau mỗi vòng.</p></li>
            </ol>
        </section>
        <section class="landing-series" aria-labelledby="landing-series-title">
            <div><h2 id="landing-series-title">Nhiều chặng, một mùa giải.</h2><p>Gom các giải thành một chuỗi và theo dõi thứ hạng xuyên suốt mùa.</p></div>
            <a class="landing-button" href="${pageContext.request.contextPath}/common/create-series.jsp">Tạo chuỗi giải <span aria-hidden="true">↗</span></a>
        </section>
    </main>
</body>
</html>
