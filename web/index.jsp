<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <!-- Favicon -->
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <link rel="alternate icon" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>TOURMA - Nền Tảng Quản Lý Giải Đấu & Series Chuyên Nghiệp</title>
        
        <!-- Google Font Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        
        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Main Design System CSS -->
        <link rel="stylesheet" href="css/style.css">
    </head>
    <body>
        <!-- Header / Navigation Bar Component -->
        <jsp:include page="common/component/header.jsp">
            <jsp:param name="active" value="home"/>
        </jsp:include>

        <!-- Main Content Area -->
        <main class="container">
            <!-- Hero Section -->
            <section class="hero-section">
                <div class="hero-badge">
                    <i class="fa-solid fa-bolt text-gold"></i> Không cần đăng nhập • Bắt đầu ngay trong 5 giây
                </div>
                
                <h1 class="hero-title">
                    Quản Lý Giải Đấu & Series <br>
                    <span class="highlight-mint">Tối Giản</span> & <span class="highlight-gold">Thuật Toán Đỉnh Cao</span>
                </h1>
                
                <p class="hero-subtitle">
                    Khởi tạo giải đấu đơn lẻ bằng cách dán danh sách đội, tự động sinh sơ đồ thi đấu thông minh và quản lý chuỗi giải với 2 mô hình xếp hạng <strong class="text-gold">Rolling Window</strong> & <strong class="text-mint">FIFA Elo Rating</strong>.
                </p>
                
                <!-- Quick Action Cards -->
                <div class="action-cards-grid">
                    <!-- Action Card 1: Single Tournament -->
                    <div class="action-card card-mint">
                        <div class="card-icon-box">
                            <i class="fa-solid fa-diagram-project"></i>
                        </div>
                        <h3>Khởi Tạo Giải Đấu Đơn Lẻ</h3>
                        <p>Dán danh sách đội bóng, chọn thể thức (Single Elimination, Round Robin) và tự động tạo sơ đồ thi đấu ngay lập tức.</p>
                        <a href="common/create-tournament.jsp" class="btn btn-mint" style="width: 100%;">
                            Tạo Giải Đấu Ngay <i class="fa-solid fa-arrow-right"></i>
                        </a>
                    </div>
                    
                    <!-- Action Card 2: Series -->
                    <div class="action-card card-gold">
                        <div class="card-icon-box">
                            <i class="fa-solid fa-crown"></i>
                        </div>
                        <h3>Khởi Tạo Series Mùa Giải</h3>
                        <p>Tạo chuỗi giải đấu gồm nhiều giải con. Tự động quy đổi điểm thưởng Series hoặc tính điểm biến thiên FIFA Elo.</p>
                        <a href="common/create-series.jsp" class="btn btn-gold-outline" style="width: 100%;">
                            Cấu Hình Series <i class="fa-solid fa-arrow-right"></i>
                        </a>
                    </div>
                </div>
                
                <!-- System Stats Bar -->
                <div class="stats-row">
                    <div class="stat-item">
                        <div class="stat-number text-mint">100%</div>
                        <div class="stat-label">Không Cần Đăng Nhập</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-number text-gold">4+</div>
                        <div class="stat-label">Thể Thức Thi Đấu</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-number text-mint">2</div>
                        <div class="stat-label">Mô Hình Ranking Series</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-number text-gold">Live</div>
                        <div class="stat-label">Bốc Thăm & Parse Đội</div>
                    </div>
                </div>
            </section>

            <!-- Features Highlights Section -->
            <section style="padding: 2rem 0;">
                <div class="section-header">
                    <div>
                        <h2 class="section-title">Tính Năng Thuật Toán Nổi Bật</h2>
                        <p class="section-subtitle">Tự động hóa toàn bộ quy trình vận hành giải đấu thể thao và eSports</p>
                    </div>
                </div>

                <div class="features-grid">
                    <div class="feature-box">
                        <div class="feature-icon text-mint">
                            <i class="fa-solid fa-file-lines"></i>
                        </div>
                        <h4>Live Textarea Parser</h4>
                        <p>Dán danh sách đội bóng dạng văn bản, hệ thống tự động bóc tách tên đội, đếm số lượng và tự động trích xuất số Hạt giống (Seed).</p>
                    </div>

                    <div class="feature-box">
                        <div class="feature-icon text-gold">
                            <i class="fa-solid fa-sitemap"></i>
                        </div>
                        <h4>Tự Động Sinh Sơ Đồ Thi Đấu</h4>
                        <p>Thuật toán tự động tính suất miễn đấu (BYE), xếp cặp hạt giống (Standard Seeding) và tự động đẩy đội thắng lên vòng sau.</p>
                    </div>

                    <div class="feature-box">
                        <div class="feature-icon text-mint">
                            <i class="fa-solid fa-clock-rotate-left"></i>
                        </div>
                        <h4>Rolling Window Ranking</h4>
                        <p>Tích lũy điểm Series theo thứ hạng với cơ chế tự động giảm trừ trọng số và hết hạn điểm theo thời gian (Time Decay Window).</p>
                    </div>

                    <div class="feature-box">
                        <div class="feature-icon text-gold">
                            <i class="fa-solid fa-ranking-star"></i>
                        </div>
                        <h4>FIFA Elo Rating System</h4>
                        <p>Tự động cập nhật điểm Elo trực tiếp sau từng trận đấu dựa vào chênh lệch sức mạnh đối thủ và hệ số cách biệt bàn thắng.</p>
                    </div>
                </div>
            </section>
        </main>

        <!-- Footer -->
        <footer class="footer">
            <div class="container">
                <p>© 2026 <strong>TOURMA</strong> - System Platform for Tournament Organization & Tournament Series.</p>
                <p style="margin-top: 0.5rem; font-size: 0.85rem;" class="text-muted">Designed with Lexend Typography • Dark Charcoal Theme • Mint & Gold Accents</p>
            </div>
        </footer>
    </body>
</html>
