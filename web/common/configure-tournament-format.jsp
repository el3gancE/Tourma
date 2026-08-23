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

        <main class="container" style="max-width: 680px;">
            
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
                
                <!-- Stage Badge -->
                <div style="margin-bottom: 0.6rem;">
                    <span class="stage-badge" style="font-size: 0.72rem; padding: 0.2rem 0.65rem;">SINGLE STAGE</span>
                </div>

                <!-- Main Card Title -->
                <h1 class="form-header-title" style="font-size: 1.4rem; margin-bottom: 1.25rem;">
                    Giai đoạn thi đấu duy nhất <i class="fa-solid fa-trophy text-gold"></i>
                </h1>

                <form id="configureFormatForm" action="${pageContext.request.contextPath}/common/configure-tournament-teams.jsp" method="GET">
                    <input type="hidden" id="selectedFormat" name="format" value="SINGLE_ELIMINATION">

                    <!-- 1. CHỌN THỂ THỨC THI ĐẤU -->
                    <div class="form-group">
                        <div class="section-label-uppercase">CHỌN THỂ THỨC THI ĐẤU</div>
                        
                        <div class="format-pill-grid">
                            <button type="button" class="format-pill-btn active" id="pillSingleElim" onclick="selectFormat('SINGLE_ELIMINATION')">
                                Single Elimination
                            </button>
                            <button type="button" class="format-pill-btn" id="pillDoubleElim" onclick="selectFormat('DOUBLE_ELIMINATION')">
                                Double Elimination
                            </button>
                            <button type="button" class="format-pill-btn" id="pillRoundRobin" onclick="selectFormat('ROUND_ROBIN')">
                                Round Robin
                            </button>
                        </div>
                    </div>

                    <!-- 2. CẤU HÌNH ĐIỂM THẮNG - HÒA - THUA (Chỉ hiển thị khi chọn Round Robin) -->
                    <div id="wdlPointsPanel" style="display: none; background: var(--bg-dark-obsidian); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 1rem; margin-top: 1rem; margin-bottom: 1.25rem;">
                        <div class="section-label-uppercase" style="margin-bottom: 0.6rem; color: var(--gold-primary);">
                            <i class="fa-solid fa-sliders"></i> QUY TẮC CỘNG ĐIỂM TRẬN ĐẤU
                        </div>
                        <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.6rem;">
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
