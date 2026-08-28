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

    <!-- LEGS CHANGE / RR CONFIG RESET WARNING MODAL -->
    <div id="legsChangeModalBackdrop" class="tourma-modal-backdrop" onclick="if(event.target === this) closeLegsChangeModal();">
        <div class="tourma-modal-card" style="max-width: 480px; border-color: rgba(244, 63, 94, 0.4);" onclick="event.stopPropagation();">
            <div class="modal-header-bar" style="border-bottom: 1px solid rgba(244, 63, 94, 0.2);">
                <div class="modal-header-title" style="color: #f43f5e; font-size: 0.95rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem;">
                    <i class="fa-solid fa-triangle-exclamation"></i>
                    <span>Xác Nhận Đổi Số Lượt & Reset Lịch Đấu</span>
                </div>
                <button type="button" class="modal-close-btn" onclick="closeLegsChangeModal()" title="Hủy bỏ">
                    <i class="fa-solid fa-xmark"></i>
                </button>
            </div>
            
            <div class="modal-body-content" style="padding: 1.25rem 1rem;">
                <div style="background: rgba(244, 63, 94, 0.08); border: 1px solid rgba(244, 63, 94, 0.2); border-radius: 8px; padding: 0.85rem; margin-bottom: 1rem; color: #cbd5e1; font-size: 0.88rem; line-height: 1.5;">
                    <strong style="color: #f43f5e;">⚠️ Cảnh báo quan trọng:</strong><br>
                    Việc thay đổi <strong>Số lần gặp nhau</strong> sẽ tạo lại cấu trúc giải và <strong style="color: #ffffff;">RESET toàn bộ lịch thi đấu cùng kết quả các trận</strong> của bạn.
                </div>
                <p style="color: #94a3b8; font-size: 0.8rem; margin: 0;">
                    Bạn có chắc chắn muốn áp dụng thay đổi này không?
                </p>
            </div>

            <div class="modal-footer-bar" style="display: flex; justify-content: flex-end; gap: 0.65rem;">
                <button type="button" class="btn btn-secondary" onclick="closeLegsChangeModal()" style="font-size: 0.8rem; padding: 0.45rem 1rem;">Hủy Bỏ</button>
                <button type="button" class="btn" style="background: #f43f5e; color: #ffffff; border: none; font-size: 0.8rem; font-weight: 700; padding: 0.45rem 1.25rem; border-radius: 6px; cursor: pointer;" onclick="confirmLegsChangeAndSubmit()">
                    <i class="fa-solid fa-rotate-right"></i> Xác Nhận Đổi
                </button>
            </div>
        </div>
    </div>

    <script>
        var tournamentId = "<%= (tournamentId != null) ? tournamentId : "" %>";
        var storageKeyFormat = "tourma_format_" + tournamentId;
        var originalFormat = "<%= currentFormat %>";
        var hasOngoingMatches = false;
        var bypassWarning = false;
        var initialLegsCount = 1;

        // Check if bracket already has completed / scored matches (SE or DE)
        if (tournamentId) {
            try {
                var matchesObj = JSON.parse(localStorage.getItem("tourma_matches_" + tournamentId));
                var deObj = JSON.parse(localStorage.getItem("tourma_de_matches_" + tournamentId));
                var combined = matchesObj || (deObj ? deObj.matchesMap : null);
                if (combined) {
                    var keys = Object.keys(combined);
                    for (var i = 0; i < keys.length; i++) {
                        var m = combined[keys[i]];
                        if (m && (m.status === 'COMPLETED' || m.status === 'done' || (m.team1 && m.team1.score !== '') || (m.team2 && m.team2.score !== ''))) {
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
                if (selectedVal === 'ROUND_ROBIN') {
                    var winInp = document.querySelector('input[name="winPoints"]');
                    var drawInp = document.querySelector('input[name="drawPoints"]');
                    var lossInp = document.querySelector('input[name="lossPoints"]');
                    var legsInp = document.querySelector('input[name="legsCount"]');
                    var rrConfig = {
                        winPoints: winInp ? (parseInt(winInp.value) || 3) : 3,
                        drawPoints: drawInp ? (parseInt(drawInp.value) || 1) : 1,
                        lossPoints: lossInp ? (parseInt(lossInp.value) || 0) : 0,
                        legsCount: legsInp ? (parseInt(legsInp.value) || 1) : 1
                    };
                    localStorage.setItem('tourma_rr_config_' + tournamentId, JSON.stringify(rrConfig));
                }
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

        function openLegsChangeModal() {
            var modal = document.getElementById('legsChangeModalBackdrop');
            if (modal) {
                modal.classList.add('show');
                document.body.style.overflow = 'hidden';
            }
        }

        function closeLegsChangeModal() {
            var modal = document.getElementById('legsChangeModalBackdrop');
            if (modal) {
                modal.classList.remove('show');
                document.body.style.overflow = '';
            }
            var legsInp = document.querySelector('input[name="legsCount"]');
            if (legsInp) legsInp.value = initialLegsCount;
        }

        function confirmLegsChangeAndSubmit() {
            if (tournamentId) {
                try {
                    localStorage.removeItem('tourma_rr_matches_' + tournamentId);
                    localStorage.removeItem('tourma_rr_round_inputs_' + tournamentId);
                    localStorage.removeItem('tourma_matches_' + tournamentId);
                } catch (e) {}
            }
            persistFormatSelection();
            bypassWarning = true;
            var modal = document.getElementById('legsChangeModalBackdrop');
            if (modal) {
                modal.classList.remove('show');
                document.body.style.overflow = '';
            }
            document.getElementById('configureFormatForm').submit();
        }

        function validateAndSubmitFormat(e) {
            if (bypassWarning) return true;

            var currentSelected = document.getElementById('selectedFormat').value;

            // If tournament has started and format changed, block and show popup!
            if (hasOngoingMatches && currentSelected !== originalFormat) {
                if (e && e.preventDefault) e.preventDefault();
                openFormatLockedModal();
                return false;
            }

            // If Round Robin and existing schedule exists, check if legsCount changed
            if (currentSelected === 'ROUND_ROBIN' && tournamentId) {
                var hasPriorRRSchedule = !!localStorage.getItem('tourma_rr_matches_' + tournamentId);
                var legsInp = document.querySelector('input[name="legsCount"]');
                var newLegsCount = legsInp ? (parseInt(legsInp.value) || 1) : 1;

                if (hasPriorRRSchedule && newLegsCount !== initialLegsCount) {
                    if (e && e.preventDefault) e.preventDefault();
                    openLegsChangeModal();
                    return false;
                }
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

            // Restore Round Robin Config inputs (legsCount, winPoints, etc.)
            if (tournamentId && localStorage.getItem('tourma_rr_config_' + tournamentId)) {
                try {
                    var savedCfg = JSON.parse(localStorage.getItem('tourma_rr_config_' + tournamentId));
                    if (savedCfg) {
                        var winInp = document.querySelector('input[name="winPoints"]');
                        var drawInp = document.querySelector('input[name="drawPoints"]');
                        var lossInp = document.querySelector('input[name="lossPoints"]');
                        var legsInp = document.querySelector('input[name="legsCount"]');
                        if (winInp && savedCfg.winPoints !== undefined) winInp.value = savedCfg.winPoints;
                        if (drawInp && savedCfg.drawPoints !== undefined) drawInp.value = savedCfg.drawPoints;
                        if (lossInp && savedCfg.lossPoints !== undefined) lossInp.value = savedCfg.lossPoints;
                        if (legsInp && savedCfg.legsCount !== undefined) {
                            legsInp.value = savedCfg.legsCount;
                            initialLegsCount = parseInt(savedCfg.legsCount);
                        }
                    }
                } catch(e) {}
            }
        });
    </script>
</body>

</html>