<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="dao.TournamentDAO"%>
<%@page import="model.Tournament"%>
<%
    String tournamentId = request.getParameter("id");
    String currentFormat = request.getParameter("format");
    if ((currentFormat == null || currentFormat.trim().isEmpty()) && tournamentId != null && !tournamentId.trim().isEmpty()) {
        try {
            TournamentDAO tDao = new TournamentDAO();
            Tournament t = tDao.getTournamentById(tournamentId);
            if (t != null && t.getFormat() != null) {
                currentFormat = t.getFormat();
            }
        } catch (Exception e) {
            // Keep empty
        }
    }
    if (currentFormat == null || currentFormat.trim().isEmpty()) {
        currentFormat = "SINGLE_ELIMINATION";
    }
%>
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

    <!-- Main Design System CSS & Dedicated Creation & Format Configuration CSS & Modal CSS -->
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/create-tournament.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/configure-tournament-format.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/sidebar.css">
</head>

<body>
    <!-- Include Shared Navigation Header Component -->
    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="create-tournament" />
    </jsp:include>

    <!-- Include Shared Tournament Sidebar Component -->
    <jsp:include page="/common/component/sidebar.jsp">
        <jsp:param name="activeStep" value="format" />
        <jsp:param name="id" value="${param.id}" />
    </jsp:include>

    <!-- Main Content Container Shifted Right by Sidebar -->
    <main class="container has-sidebar">

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
                Chọn thể thức
            </h1>

            <form id="configureFormatForm" action="${pageContext.request.contextPath}/common/configure-tournament-teams.jsp" method="GET" onsubmit="return validateAndSubmitFormat(event)">
                <input type="hidden" name="id" value="${param.id}">
                <input type="hidden" id="selectedFormat" name="format" value="<%= currentFormat %>">

                <!-- 1. CHỌN THỂ THỨC THI ĐẤU (Clickable freely) -->
                <div class="form-group">
                    <div class="format-pill-grid">
                        <button type="button" class="format-pill-btn" id="pillSingleElim" onclick="selectFormat('SINGLE_ELIMINATION')">
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

                <!-- 2. CHI TIẾT CẤU HÌNH ĐIỂM (Round Robin) -->
                <div id="wdlPointsPanel" style="display: none; background: var(--bg-dark-obsidian); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 1rem; margin-top: 1rem; margin-bottom: 1.25rem;">
                    <div class="section-label-uppercase" style="margin-bottom: 0.6rem; color: var(--gold-primary);">
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
                    <a href="${pageContext.request.contextPath}/common/my-tournaments.jsp" class="btn btn-secondary">Quay Lại</a>
                    <button type="submit" class="btn btn-mint">
                        TIẾP TỤC ➔
                    </button>
                </div>
            </form>
        </div>
    </main>

    <!-- ONGOING TOURNAMENT CANNOT CHANGE FORMAT WARNING MODAL -->
    <div id="formatLockedModalBackdrop" class="tourma-modal-backdrop" onclick="if(event.target === this) closeFormatLockedModal();">
        <div class="tourma-modal-card" style="max-width: 480px; border-color: rgba(244, 63, 94, 0.4);" onclick="event.stopPropagation();">
            <div class="modal-header-bar" style="border-bottom: 1px solid rgba(244, 63, 94, 0.2);">
                <div class="modal-header-title" style="color: #f43f5e; font-size: 0.95rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem;">
                    <i class="fa-solid fa-triangle-exclamation"></i>
                    <span>Đang Diễn Ra, Không Thể Đổi</span>
                </div>
                <button type="button" class="modal-close-btn" onclick="closeFormatLockedModal()" title="Đóng">
                    <i class="fa-solid fa-xmark"></i>
                </button>
            </div>
            
            <div class="modal-body-content" style="padding: 1.25rem 1rem;">
                <div style="background: rgba(244, 63, 94, 0.08); border: 1px solid rgba(244, 63, 94, 0.2); border-radius: 8px; padding: 0.85rem; margin-bottom: 1rem; color: #cbd5e1; font-size: 0.82rem; line-height: 1.5;">
                    <strong style="color: #f43f5e;">⚠️ Thể thức đã cố định:</strong><br>
                    Giải đấu này đang diễn ra (đã có trận đấu có kết quả). Bạn không thể thay đổi thể thức thi đấu khi giải đấu đang diễn ra.
                </div>
                <p style="color: #94a3b8; font-size: 0.8rem; margin: 0;">
                    Vui lòng giữ nguyên thể thức hiện tại để tiếp tục quản lý giải đấu.
                </p>
            </div>

            <div class="modal-footer-bar" style="display: flex; justify-content: flex-end; gap: 0.65rem;">
                <button type="button" class="btn btn-mint" onclick="closeFormatLockedModal()" style="font-size: 0.8rem; font-weight: 700; padding: 0.45rem 1.25rem;">
                    Đã Hiểu & Đóng
                </button>
            </div>
        </div>
    </div>

    <script>
        var tournamentId = "<%= (tournamentId != null) ? tournamentId : "" %>";
        var storageKeyFormat = "tourma_format_" + tournamentId;
        var originalFormat = "<%= currentFormat %>";
        var hasOngoingMatches = false;

        // Check if bracket already has completed / scored matches
        if (tournamentId) {
            try {
                var matchesObj = JSON.parse(localStorage.getItem("tourma_matches_" + tournamentId));
                if (matchesObj) {
                    var keys = Object.keys(matchesObj);
                    for (var i = 0; i < keys.length; i++) {
                        var m = matchesObj[keys[i]];
                        if (m.status === 'COMPLETED' || m.status === 'done' || (m.team1 && m.team1.score !== '') || (m.team2 && m.team2.score !== '')) {
                            hasOngoingMatches = true;
                            break;
                        }
                    }
                }
            } catch (e) {}
        }

        function selectFormat(formatValue) {
            document.getElementById('selectedFormat').value = formatValue;
            const isRoundRobin = formatValue === 'ROUND_ROBIN';

            var wdlPanel = document.getElementById('wdlPointsPanel');
            if (wdlPanel) wdlPanel.style.display = isRoundRobin ? 'block' : 'none';

            var btnSingle = document.getElementById('pillSingleElim');
            var btnDouble = document.getElementById('pillDoubleElim');
            var btnRound = document.getElementById('pillRoundRobin');

            if (btnSingle) btnSingle.classList.toggle('active', formatValue === 'SINGLE_ELIMINATION');
            if (btnDouble) btnDouble.classList.toggle('active', formatValue === 'DOUBLE_ELIMINATION');
            if (btnRound) btnRound.classList.toggle('active', isRoundRobin);
        }

        function persistFormatSelection() {
            var selectedVal = document.getElementById('selectedFormat').value;
            if (tournamentId) {
                localStorage.setItem(storageKeyFormat, selectedVal);
            }
        }

        function openFormatLockedModal() {
            var modal = document.getElementById('formatLockedModalBackdrop');
            if (modal) {
                modal.classList.add('show');
                document.body.style.overflow = 'hidden';
            }
        }

        function closeFormatLockedModal() {
            var modal = document.getElementById('formatLockedModalBackdrop');
            if (modal) {
                modal.classList.remove('show');
                document.body.style.overflow = '';
            }
            // Revert back to original format
            selectFormat(originalFormat);
        }

        function validateAndSubmitFormat(e) {
            var currentSelected = document.getElementById('selectedFormat').value;

            // If tournament has started and format changed, block and show popup!
            if (hasOngoingMatches && currentSelected !== originalFormat) {
                if (e && e.preventDefault) e.preventDefault();
                openFormatLockedModal();
                return false;
            }

            persistFormatSelection();
            return true;
        }

        // Restore format state on page load
        window.addEventListener('DOMContentLoaded', function () {
            var savedFormat = "<%= currentFormat %>";
            if (tournamentId && localStorage.getItem(storageKeyFormat)) {
                savedFormat = localStorage.getItem(storageKeyFormat);
            }
            originalFormat = savedFormat || 'SINGLE_ELIMINATION';
            selectFormat(originalFormat);
        });
    </script>
</body>

</html>