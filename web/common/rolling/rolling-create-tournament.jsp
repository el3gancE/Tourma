<%@page contentType="text/html" pageEncoding="UTF-8" %>
    <%@page import="model.Series, model.Tournament, java.util.List" %>
        <% List<Series> allSeries = (List<Series>) request.getAttribute("allSeries");
                Series selectedSeries = (Series) request.getAttribute("selectedSeries");
                Integer nextIndex = (Integer) request.getAttribute("nextIndex");
                if (nextIndex == null) nextIndex = 1;

                String selectedSeriesId = (selectedSeries != null) ? selectedSeries.getId() : "";
                String seriesName = (selectedSeries != null) ? selectedSeries.getName() : "Series";
                String defaultTourneyName = (selectedSeries != null) ? (selectedSeries.getName() + " - Stop #" +
                nextIndex) : "Giải Đấu Con #" + nextIndex;
                %>
                <!DOCTYPE html>
                <html lang="vi">

                <head>
                    <!-- Favicon -->
                    <link rel="icon" type="image/svg+xml"
                        href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
                    <link rel="alternate icon"
                        href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Khởi Tạo Giải Đấu Con - TOURMA</title>

                    <!-- Google Font Lexend -->
                    <link rel="preconnect" href="https://fonts.googleapis.com">
                    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                    <link
                        href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap"
                        rel="stylesheet">

                    <!-- FontAwesome Icons -->
                    <link rel="stylesheet"
                        href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

                    <!-- Main Design System CSS & Dedicated Create Tournament CSS -->
                    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
                    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/create-tournament.css">
                </head>

                <body>
                    <!-- Include Shared Navigation Header Component -->
                    <jsp:include page="/common/component/header.jsp">
                        <jsp:param name="active" value="my-series" />
                    </jsp:include>

                    <!-- Include Dynamic 3-Mode Sidebar Component -->
                    <jsp:include page="/common/component/sidebar.jsp">
                        <jsp:param name="seriesId" value="<%= selectedSeriesId %>" />
                        <jsp:param name="activeStep" value="create-subtourney" />
                    </jsp:include>

                    <main class="container has-sidebar" style="max-width: 580px; padding: 1.5rem 1rem;">
                        <div style="margin-top: 0.5rem;">
                            <a href="${pageContext.request.contextPath}/rolling/dashboard?id=<%= selectedSeriesId %>"
                                class="text-muted" style="font-size: 0.8rem; text-decoration: none;">
                                <i class="fa-solid fa-arrow-left"></i> Quay lại Dashboard Series
                            </a>
                        </div>

                        <!-- Standard Template Form Container Box -->
                        <div class="form-container-box">
                            <h1 class="form-header-title">
                                <i class="fa-solid fa-trophy text-mint"></i> Khởi Tạo Giải Đấu Con - <%= seriesName %>
                            </h1>
                            <p class="text-muted" style="margin-bottom: 1.5rem; font-size: 0.82rem;">
                                Nhập tên và cấp độ giải đấu con thuộc Series <strong>
                                    <%= seriesName %>
                                </strong>.
                            </p>

                            <!-- Error Alert Message -->
                            <% if (request.getAttribute("errorMessage") !=null) { %>
                                <div
                                    style="background: rgba(239, 68, 68, 0.15); border: 1px solid #ef4444; color: #fca5a5; padding: 0.65rem 1rem; border-radius: 8px; font-size: 0.82rem; margin-bottom: 1.25rem;">
                                    <i class="fa-solid fa-triangle-exclamation"></i>
                                    <%= request.getAttribute("errorMessage") %>
                                </div>
                                <% } %>

                                    <form id="createTournamentForm"
                                        action="${pageContext.request.contextPath}/rolling/create-tournament"
                                        method="POST">
                                        <input type="hidden" name="seriesId" value="<%= selectedSeriesId %>">
                                        <input type="hidden" name="indexInSeries" value="<%= nextIndex %>">

                                        <!-- 1. TÊN GIẢI ĐẤU CON -->
                                        <div class="form-group">
                                            <label class="form-label" for="tournamentName">Tên Giải Đấu Con (*)</label>
                                            <input type="text" id="tournamentName" name="name" class="form-control"
                                                value="<%= defaultTourneyName %>" required autofocus>
                                        </div>

                                        <!-- 2. CẤP ĐỘ GIẢI ĐẤU (TIER S, A, B, C, D) -->
                                        <div class="form-group">
                                            <label class="form-label" for="tierName">Cấp Độ Giải Đấu (Tier) (*)</label>
                                            <select id="tierName" name="tierName" class="form-control" required style="font-weight: 700;">
                                                <option value="" selected disabled style="color: #94a3b8; font-weight: normal; background: #12161f;">-- Chọn Cấp Độ Giải Đấu (Tier) --</option>
                                                <option value="S" style="color: #fbbf24; font-weight: 800; background: #12161f;">Tier S</option>
                                                <option value="A" style="color: #c084fc; font-weight: 800; background: #12161f;">Tier A</option>
                                                <option value="B" style="color: #34d399; font-weight: 800; background: #12161f;">Tier B</option>
                                                <option value="C" style="color: #60a5fa; font-weight: 800; background: #12161f;">Tier C</option>
                                                <option value="D" style="color: #ffffff; font-weight: 800; background: #12161f;">Tier D</option>
                                            </select>
                                            <div id="tierErrorMsg" style="display: none; color: #f87171; font-size: 0.82rem; margin-top: 0.4rem; font-weight: 600;">
                                                <i class="fa-solid fa-circle-exclamation"></i> Vui lòng chọn Tier cho giải đấu! Đây là thông tin bắt buộc.
                                            </div>
                                        </div>

                                        <!-- SUBMIT BUTTONS -->
                                        <div
                                            style="display: flex; justify-content: flex-end; gap: 0.75rem; margin-top: 1.75rem;">
                                            <a href="${pageContext.request.contextPath}/rolling/dashboard?id=<%= selectedSeriesId %>"
                                                class="btn btn-secondary">Hủy Bỏ</a>
                                            <button type="submit" class="btn btn-mint">
                                                TẠO GIẢI ĐẤU & TIẾP TỤC ➔
                                            </button>
                                        </div>

                                    </form>
                        </div>
                    </main>

                    <script>
                        document.addEventListener('DOMContentLoaded', function() {
                            var form = document.getElementById('createTournamentForm');
                            var tierSelect = document.getElementById('tierName');
                            var errorMsg = document.getElementById('tierErrorMsg');

                            var tierColors = {
                                'S': '#fbbf24',
                                'A': '#c084fc',
                                'B': '#34d399',
                                'C': '#60a5fa',
                                'D': '#ffffff'
                            };

                            function updateSelectColor() {
                                if (!tierSelect) return;
                                var val = tierSelect.value;
                                if (tierColors[val]) {
                                    tierSelect.style.color = tierColors[val];
                                    tierSelect.style.fontWeight = '800';
                                } else {
                                    tierSelect.style.color = '#94a3b8';
                                    tierSelect.style.fontWeight = 'normal';
                                }
                            }

                            if (form && tierSelect) {
                                updateSelectColor();

                                form.addEventListener('submit', function(e) {
                                    if (!tierSelect.value || tierSelect.value.trim() === '') {
                                        e.preventDefault();
                                        tierSelect.focus();
                                        tierSelect.style.borderColor = '#ef4444';
                                        tierSelect.style.boxShadow = '0 0 0 3px rgba(239, 68, 68, 0.25)';
                                        if (errorMsg) errorMsg.style.display = 'block';
                                        alert('Vui lòng chọn Cấp Độ (Tier) cho giải đấu! Đây là thông tin bắt buộc.');
                                        return false;
                                    }
                                });

                                tierSelect.addEventListener('change', function() {
                                    updateSelectColor();
                                    if (this.value) {
                                        this.style.borderColor = '';
                                        this.style.boxShadow = '';
                                        if (errorMsg) errorMsg.style.display = 'none';
                                    }
                                });
                            }
                        });
                    </script>
                </body>

                </html>