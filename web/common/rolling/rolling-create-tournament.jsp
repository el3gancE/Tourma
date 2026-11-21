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
                    <title>Tạo giải con · TOURMA</title>

                    <!-- Google Font Readex Pro -->

                    <!-- FontAwesome Icons -->

                    <!-- Main Design System CSS & Dedicated Create Tournament CSS -->
<script src="${pageContext.request.contextPath}/js/theme.js?v=20261101-settings"></script>
</head>

                <body class="ui-page workspace-page tournament-workspace series-page series-form-page subtournament-create-page">
                    <!-- Include Shared Navigation Header Component -->
                    <jsp:include page="/common/component/header.jsp">
                        <jsp:param name="active" value="my-series" />
                    <jsp:param name="themeToggle" value="true"/></jsp:include>

                    <!-- Include Dynamic 3-Mode Sidebar Component -->
                    <div class="tournament-shell"><details class="tournament-navigation" open><summary>Điều hướng chuỗi giải</summary><jsp:include page="/common/component/sidebar.jsp">
                        <jsp:param name="seriesId" value="<%= selectedSeriesId %>" />
                        <jsp:param name="activeStep" value="create-subtourney" />
                    </jsp:include></details>

                    <main class="series-main">
                        <div>
                            <a href="${pageContext.request.contextPath}/rolling/dashboard?id=<%= selectedSeriesId %>"
                                class="text-muted">
                                <i class="fa-solid fa-arrow-left"></i> Quay lại Dashboard Series
                            </a>
                        </div>

                        <!-- Standard Template Form Container Box -->
                        <div class="form-container-box">
                            <h1 class="form-header-title">
                                <i class="fa-solid fa-trophy text-mint"></i> Tạo giải con · <%= seriesName %>
                            </h1>
                            <p class="text-muted">
                                Nhập tên và cấp độ giải đấu con thuộc Series <strong>
                                    <%= seriesName %>
                                </strong>.
                            </p>

                            <!-- Error Alert Message -->
                            <% if (request.getAttribute("errorMessage") !=null) { %>
                                <div
>
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
                                            <select id="tierName" name="tierName" class="form-control" required>
                                                <option value="" selected disabled>-- Chọn Cấp Độ Giải Đấu (Tier) --</option>
                                                <option value="S">Tier S</option>
                                                <option value="A">Tier A</option>
                                                <option value="B">Tier B</option>
                                                <option value="C">Tier C</option>
                                                <option value="D">Tier D</option>
                                            </select>
                                            <div id="tierErrorMsg">
                                                <i class="fa-solid fa-circle-exclamation"></i> Vui lòng chọn Tier cho giải đấu! Đây là thông tin bắt buộc.
                                            </div>
                                        </div>

                                        <!-- SUBMIT BUTTONS -->
                                        <div
>
                                            <a href="${pageContext.request.contextPath}/rolling/dashboard?id=<%= selectedSeriesId %>"
                                                class="btn btn-secondary">Hủy Bỏ</a>
                                            <button type="submit" class="btn btn-mint">
                                                TẠO GIẢI ĐẤU & TIẾP TỤC ➔
                                            </button>
                                        </div>

                                    </form>
                        </div>
                    </main></div>

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
                                'D': 'var(--ui-text)'
                            };

                            function updateSelectColor() {
                                if (!tierSelect) return;
                                var val = tierSelect.value;
                                if (tierColors[val]) {
                                    tierSelect.style.color = tierColors[val];
                                    tierSelect.style.fontWeight = '800';
                                } else {
                                    tierSelect.style.color = 'var(--ui-muted)';
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