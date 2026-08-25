<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Cấu Hình Thể Thức Thi Đấu - TOURMA</title>
        
        <!-- Google Font Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        
        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Main Design System CSS & Dedicated Creation & Format Configuration CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/create-tournament.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/configure-tournament-format.css">
    </head>
    <body>
        <!-- Include Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="create-tournament"/>
        </jsp:include>

        <main class="container" style="max-width: 1100px;">
            <div class="tournament-layout-wrapper">
                <!-- Include Tournament Sidebar Component -->
                <jsp:include page="/common/component/sidebar.jsp">
                    <jsp:param name="activeStep" value="format"/>
                    <jsp:param name="id" value="${param.id}"/>
                </jsp:include>

                <!-- Main Content Area -->
                <div class="tournament-main-content">
                    <!-- Top Stage Model Toggle Bar -->
                    <div class="stage-toggle-bar">
                        <button type="button" class="stage-toggle-btn">
                            <i class="fa-solid fa-layer-group"></i> Multi-Stage
                        </button>
                        <button type="button" class="stage-toggle-btn active">
                            <i class="fa-solid fa-trophy text-mint"></i> Single Stage
                        </button>
                    </div>

                    <!-- Main Config Card Box -->
                    <div class="form-container-box" style="margin-top: 0;">
                        
                            <i class="fa-solid fa-sliders"></i> QUY TẮC CỘNG ĐIỂM & SỐ LẦN GẶP NHAU
                        </div>
                        <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.6rem;">
                            <div>
                                <label class="form-label" style="font-size: 0.75rem; color: var(--text-muted);">Điểm Thắng</label>
                                <input type="number" name="winPoints" class="form-control" value="3" min="0" max="10">
                            </div>
                            <div>
                                <label class="form-label" style="font-size: 0.75rem; color: var(--text-muted);">Điểm Hòa</label>
                                <input type="number" name="drawPoints" class="form-control" value="1" min="0" max="10">
                            </div>
                            <div>
                                <label class="form-label" style="font-size: 0.75rem; color: var(--text-muted);">Điểm Thua</label>
                                <input type="number" name="lossPoints" class="form-control" value="0" min="0" max="10">
                            </div>
                            <div>
                                <label class="form-label" style="font-size: 0.75rem; color: var(--text-muted);">Số Lần Gặp Nhau</label>
                                <input type="number" name="legsCount" class="form-control" value="1" min="1" max="10">
                            </div>
                        </div>
                    </div>

                    <!-- SUBMIT BUTTONS -->
                    <div style="display: flex; justify-content: flex-end; gap: 0.75rem; margin-top: 1.75rem;">
                        <a href="${pageContext.request.contextPath}/common/create-tournament.jsp" class="btn btn-secondary">Quay Lại</a>
                        <button type="submit" class="btn btn-mint">
                            TIẾP TỤC ➔
                        </button>
                    </div>
                </form>
            </div>
        </main>

        <script>
            function selectFormat(formatValue) {
                document.getElementById('selectedFormat').value = formatValue;
                const isRoundRobin = formatValue === 'ROUND_ROBIN';
                
                document.getElementById('wdlPointsPanel').style.display = isRoundRobin ? 'block' : 'none';
                document.getElementById('pillSingleElim').classList.toggle('active', formatValue === 'SINGLE_ELIMINATION');
                document.getElementById('pillDoubleElim').classList.toggle('active', formatValue === 'DOUBLE_ELIMINATION');
                document.getElementById('pillRoundRobin').classList.toggle('active', isRoundRobin);
            }
        </script>
    </body>
</html>
