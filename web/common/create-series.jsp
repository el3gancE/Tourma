<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <!-- Favicon -->
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <link rel="alternate icon" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Tạo Chuỗi Giải Mới - TOURMA</title>

        <!-- Google Font Readex Pro -->

        <!-- FontAwesome Icons -->

        <!-- Main Design System CSS & Dedicated Creation CSS -->
<script src="${pageContext.request.contextPath}/js/theme.js?v=20261101-settings"></script>
</head>
    <body class="ui-page workspace-page tournament-workspace series-page series-form-page create-series-page">
        <!-- Include Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="create-series"/>
        <jsp:param name="themeToggle" value="true"/></jsp:include>

        <main class="series-create-main">
            <div>
                <a href="${pageContext.request.contextPath}/index.jsp" class="text-muted">
                    <i class="fa-solid fa-arrow-left"></i> Quay lại Trang chủ
                </a>
            </div>

            <!-- Form Container Box -->
            <div class="form-container-box">
                <h1 class="form-header-title">
                    <i class="fa-solid fa-folder-plus text-gold"></i> Tạo chuỗi giải
                </h1>
                <p class="text-muted">
                    Đặt tên chuỗi giải và chọn cách tính điểm xếp hạng.
                </p>

                <!-- Error Alert Message -->
                <% if (request.getAttribute("errorMessage") != null) { %>
                    <div>
                        <i class="fa-solid fa-triangle-exclamation"></i> <%= request.getAttribute("errorMessage") %>
                    </div>
                <% } %>

                <form id="createSeriesForm" action="${pageContext.request.contextPath}/create-series" method="POST">

                    <!-- 1. TÊN CHUỖI GIẢI -->
                    <div class="form-group">
                        <label class="form-label" for="seriesName">Tên Chuỗi Giải (*)</label>
                        <input type="text" id="seriesName" name="name" class="form-control"
                               placeholder="Ví dụ: VBA Pro League 2026 Circuit" required autofocus>
                    </div>

                    <!-- 2. MÔ TẢ CHUỖI GIẢI -->
                    <div class="form-group">
                        <label class="form-label" for="seriesDescription">Mô Tả Chuỗi Giải</label>
                        <textarea id="seriesDescription" name="description" class="form-control"

                                  placeholder="Mô tả về quy mô chuỗi giải đấu, giải thưởng tích lũy..."></textarea>
                    </div>

                    <!-- 3. MÔ HÌNH BẢNG XẾP HẠNG -->
                    <div class="form-group">
                        <label class="form-label">Mô Hình Bảng Xếp Hạng Series (*)</label>
                        <div class="radio-card-grid">
                            <label class="radio-card active" id="cardRolling">
                                <input type="radio" name="rankingModel" value="ROLLING_WINDOW" checked onchange="toggleModelOptions()">
                                <div class="radio-card-content">
                                    <h5 class="text-gold"><i class="fa-solid fa-chart-line"></i> Rolling Window</h5>
                                    <p>Tích lũy điểm theo Phase (ví dụ: 10 giải = 1 Phase).</p>
                                </div>
                            </label>

                            <label class="radio-card" id="cardElo">
                                <input type="radio" name="rankingModel" value="FIFA_ELO" onchange="toggleModelOptions()">
                                <div class="radio-card-content">
                                    <h5 class="text-mint"><i class="fa-solid fa-ranking-star"></i> FIFA Elo Rating</h5>
                                    <p>Tính điểm biến thiên Elo sau từng trận đấu.</p>
                                </div>
                            </label>
                        </div>
                    </div>

                    <!-- ROLLING OPTIONS PANEL -->
                    <div id="rollingOptionsPanel">
                        <div class="form-group">
                            <label class="form-label" for="phaseSize">Số Giải Tích Lũy Trong 1 Phase (*)</label>
                            <input type="number" id="phaseSize" name="phaseSize" class="form-control" value="10" min="1" max="100">
                            <p class="text-muted">Ví dụ: Nhập 10 thì Giải 1-10 là Phase 1, 11-20 là Phase 2.</p>
                        </div>
                        <div class="form-group">
                            <label class="form-label" for="initialPoints">Điểm Khởi Đầu (Initial Points)</label>
                            <input type="number" id="initialPoints" name="initialPoints" class="form-control" value="0" readonly>
                        </div>
                    </div>

                    <!-- ELO OPTIONS PANEL -->
                    <div id="eloOptionsPanel">
                        <div class="form-group">
                            <label class="form-label" for="initialElo">Điểm Elo Khởi Điểm (Initial Elo Rating)</label>
                            <input type="number" id="initialElo" name="initialElo" class="form-control" value="1000" step="10">
                            <p class="text-muted">Mặc định 1000 Elo cho tất cả các đội bóng tham gia.</p>
                        </div>
                    </div>

                    <!-- SUBMIT BUTTONS -->
                    <div>
                        <a href="${pageContext.request.contextPath}/index.jsp" class="btn btn-secondary">Hủy Bỏ</a>
                        <button type="submit" class="btn btn-mint">
                            Tạo chuỗi giải →
                        </button>
                    </div>
                </form>
            </div>
        </main>

        <script>
            function toggleModelOptions() {
                const isRolling = document.querySelector('input[name="rankingModel"]:checked').value === 'ROLLING_WINDOW';
                document.getElementById('rollingOptionsPanel').style.display = isRolling ? 'block' : 'none';
                document.getElementById('eloOptionsPanel').style.display = isRolling ? 'none' : 'block';
                document.getElementById('cardRolling').classList.toggle('active', isRolling);
                document.getElementById('cardElo').classList.toggle('active', !isRolling);
            }
        </script>
    </body>
</html>
