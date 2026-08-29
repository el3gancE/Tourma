<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="dao.TournamentDAO"%>
<%@page import="model.Tournament"%>
<%
    String tournamentId = request.getParameter("id");
    String currentFormat = request.getParameter("format");
    String currentType = request.getParameter("type"); // SINGLE_STAGE or MULTI_STAGE
    if ((currentFormat == null || currentFormat.trim().isEmpty()) && tournamentId != null && !tournamentId.trim().isEmpty()) {
        try {
            TournamentDAO tDao = new TournamentDAO();
            Tournament t = tDao.getTournamentById(tournamentId);
            if (t != null) {
                if (t.getFormat() != null) currentFormat = t.getFormat();
                if (t.getTournamentType() != null) currentType = t.getTournamentType();
            }
        } catch (Exception e) {
            // Keep empty
        }
    }
    if (currentFormat == null || currentFormat.trim().isEmpty()) {
        currentFormat = "SINGLE_ELIMINATION";
    }
    if (currentType == null || currentType.trim().isEmpty()) {
        currentType = "SINGLE_STAGE";
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

        <!-- Top Stage Model Toggle Bar (Single Stage ↔ Multi-Stage) -->
        <div class="stage-toggle-bar">
            <button type="button" id="btnToggleSingleStage" class="stage-toggle-btn active" onclick="selectStageType('SINGLE_STAGE')">
                <i class="fa-solid fa-trophy text-mint"></i> Single Stage
            </button>
            <button type="button" id="btnToggleMultiStage" class="stage-toggle-btn" onclick="selectStageType('MULTI_STAGE')">
                <i class="fa-solid fa-layer-group"></i> Multi-Stage
            </button>
        </div>

        <!-- Main Config Card Box -->
        <div class="form-container-box" style="margin-top: 0;">

            <!-- Stage Badge -->
            <div style="margin-bottom: 0.6rem;">
                <span id="stageBadgeDisplay" class="stage-badge" style="font-size: 0.72rem; padding: 0.2rem 0.65rem;">SINGLE STAGE</span>
            </div>

            <!-- Main Card Title -->
            <h1 class="form-header-title" style="font-size: 1.4rem; margin-bottom: 1.25rem;">
                Chọn thể thức thi đấu
            </h1>

            <form id="configureFormatForm" action="${pageContext.request.contextPath}/common/configure-tournament-teams.jsp" method="GET" onsubmit="return validateAndSubmitFormat(event)">
                <input type="hidden" name="id" value="<%= (tournamentId != null) ? tournamentId : "" %>">
                <input type="hidden" id="selectedTournamentType" name="tournamentType" value="<%= currentType %>">
                <input type="hidden" id="selectedFormat" name="format" value="<%= currentFormat %>">
                
                <!-- Multi-stage specific hidden values -->
                <input type="hidden" id="stage1Format" name="stage1Format" value="ROUND_ROBIN">
                <input type="hidden" id="stage2Format" name="stage2Format" value="SINGLE_ELIMINATION">

                <!-- ════════════════════════════════════════════════════════════════ -->
                <!-- 1. SINGLE STAGE PANEL                                            -->
                <!-- ════════════════════════════════════════════════════════════════ -->
                <div id="singleStagePanel">
                    <div class="form-group">
                        <label class="form-label" style="font-size: 0.85rem; color: var(--text-muted); margin-bottom: 0.5rem;">
                            Thể thức giải đấu 1 giai đoạn:
                        </label>
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

                    <!-- CHI TIẾT CẤU HÌNH ĐIỂM (Round Robin Single Stage) -->
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
                </div>

                <!-- ════════════════════════════════════════════════════════════════ -->
                <!-- 2. MULTI-STAGE PANEL                                             -->
                <!-- ════════════════════════════════════════════════════════════════ -->
                <div id="multiStagePanel" style="display: none;">
                    
                    <!-- STAGE 1 -->
                    <div style="background: rgba(11, 13, 18, 0.7); border: 1px solid rgba(45, 212, 191, 0.25); border-radius: 12px; padding: 1.25rem; margin-bottom: 1.25rem;">
                        <div style="font-size: 0.95rem; font-weight: 800; color: #2dd4bf; margin-bottom: 0.85rem; display: flex; align-items: center; gap: 0.5rem;">
                            <i class="fa-solid fa-layer-group"></i> STAGE 1
                        </div>

                        <label class="form-label" style="font-size: 0.78rem; color: var(--text-muted); margin-bottom: 0.5rem;">
                            Thể thức:
                        </label>
                        <div class="format-pill-grid" style="margin-bottom: 1rem;">
                            <button type="button" class="format-pill-btn active" id="pillStage1RR" onclick="selectStage1Format('ROUND_ROBIN')">
                                Round Robin
                            </button>
                            <button type="button" class="format-pill-btn" id="pillStage1GR" onclick="selectStage1Format('GROUP_STAGE')">
                                Group Stage
                            </button>
                            <button type="button" class="format-pill-btn" id="pillStage1SE" onclick="selectStage1Format('SINGLE_ELIMINATION')">
                                Single Elimination
                            </button>
                            <button type="button" class="format-pill-btn" id="pillStage1DE" onclick="selectStage1Format('DOUBLE_ELIMINATION')">
                                Double Elimination
                            </button>
                            <button type="button" class="format-pill-btn" id="pillStage1Swiss" onclick="selectStage1Format('SWISS_LITE')">
                                Swiss System
                            </button>
                        </div>

                        <!-- DYNAMIC SUB-PANEL FOR STAGE 1 FORMAT CONFIG -->
                        <div id="stage1ConfigContainer" style="background: #181d29; border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 10px; padding: 1rem;">
                            
                            <!-- 1. Round Robin Fields -->
                            <div id="stage1FieldsRR">
                                <div style="margin-bottom: 0.75rem;">
                                    <label class="form-label" style="font-size: 0.78rem; color: var(--text-muted);">Số Đội Đi Tiếp <span style="color: #f43f5e;">*</span></label>
                                    <input type="number" id="stage1AdvanceRR" name="stage1AdvanceRR" class="form-control" style="background: #0b0d12; color: #ffffff; border-color: rgba(255, 255, 255, 0.15);" value="" min="1" placeholder="Nhập số đội đi tiếp...">
                                </div>
                                <div style="background: rgba(11, 13, 18, 0.6); border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 8px; padding: 0.85rem;">
                                    <div style="font-size: 0.75rem; font-weight: 700; color: #fbbf24; margin-bottom: 0.5rem;">
                                        <i class="fa-solid fa-sliders"></i> QUY TẮC CỘNG ĐIỂM & SỐ LẦN GẶP NHAU
                                    </div>
                                    <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.5rem;">
                                        <div>
                                            <label class="form-label" style="font-size: 0.72rem; color: var(--text-muted);">Điểm Thắng</label>
                                            <input type="number" id="stage1WinPointsRR" name="stage1WinPointsRR" class="form-control" style="background: #0b0d12; color: #ffffff; border-color: rgba(255, 255, 255, 0.15);" value="3" min="0" max="10">
                                        </div>
                                        <div>
                                            <label class="form-label" style="font-size: 0.72rem; color: var(--text-muted);">Điểm Hòa</label>
                                            <input type="number" id="stage1DrawPointsRR" name="stage1DrawPointsRR" class="form-control" style="background: #0b0d12; color: #ffffff; border-color: rgba(255, 255, 255, 0.15);" value="1" min="0" max="10">
                                        </div>
                                        <div>
                                            <label class="form-label" style="font-size: 0.72rem; color: var(--text-muted);">Điểm Thua</label>
                                            <input type="number" id="stage1LossPointsRR" name="stage1LossPointsRR" class="form-control" style="background: #0b0d12; color: #ffffff; border-color: rgba(255, 255, 255, 0.15);" value="0" min="0" max="10">
                                        </div>
                                        <div>
                                            <label class="form-label" style="font-size: 0.72rem; color: var(--text-muted);">Số Lượt Gặp Nhau</label>
                                            <input type="number" id="stage1LegsCountRR" name="stage1LegsCountRR" class="form-control" style="background: #0b0d12; color: #ffffff; border-color: rgba(255, 255, 255, 0.15);" value="1" min="1" max="10">
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- 2. Group Stage Fields -->
                            <div id="stage1FieldsGR" style="display: none;">
                                <div style="margin-bottom: 0.75rem;">
                                    <label class="form-label" style="font-size: 0.78rem; color: var(--text-muted);">Tổng Số Đội Đi Tiếp <span style="color: #f43f5e;">*</span></label>
                                    <input type="number" id="stage1AdvanceGR" name="stage1AdvanceGR" class="form-control" style="background: #0b0d12; color: #ffffff; border-color: rgba(255, 255, 255, 0.15);" value="" min="1" placeholder="Nhập tổng số đội đi tiếp...">
                                </div>
                                <div style="background: rgba(11, 13, 18, 0.6); border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 8px; padding: 0.85rem;">
                                    <div style="font-size: 0.75rem; font-weight: 700; color: #fbbf24; margin-bottom: 0.5rem;">
                                        <i class="fa-solid fa-sliders"></i> QUY TẮC CỘNG ĐIỂM & SỐ LẦN GẶP NHAU
                                    </div>
                                    <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.5rem;">
                                        <div>
                                            <label class="form-label" style="font-size: 0.72rem; color: var(--text-muted);">Điểm Thắng</label>
                                            <input type="number" id="stage1WinPointsGR" name="stage1WinPointsGR" class="form-control" style="background: #0b0d12; color: #ffffff; border-color: rgba(255, 255, 255, 0.15);" value="3" min="0" max="10">
                                        </div>
                                        <div>
                                            <label class="form-label" style="font-size: 0.72rem; color: var(--text-muted);">Điểm Hòa</label>
                                            <input type="number" id="stage1DrawPointsGR" name="stage1DrawPointsGR" class="form-control" style="background: #0b0d12; color: #ffffff; border-color: rgba(255, 255, 255, 0.15);" value="1" min="0" max="10">
                                        </div>
                                        <div>
                                            <label class="form-label" style="font-size: 0.72rem; color: var(--text-muted);">Điểm Thua</label>
                                            <input type="number" id="stage1LossPointsGR" name="stage1LossPointsGR" class="form-control" style="background: #0b0d12; color: #ffffff; border-color: rgba(255, 255, 255, 0.15);" value="0" min="0" max="10">
                                        </div>
                                        <div>
                                            <label class="form-label" style="font-size: 0.72rem; color: var(--text-muted);">Số Lượt Gặp Nhau</label>
                                            <input type="number" id="stage1LegsCountGR" name="stage1LegsCountGR" class="form-control" style="background: #0b0d12; color: #ffffff; border-color: rgba(255, 255, 255, 0.15);" value="1" min="1" max="10">
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- 3. Single Elimination Fields -->
                            <div id="stage1FieldsSE" style="display: none;">
                                <label class="form-label" style="font-size: 0.78rem; color: var(--text-muted);">Số Đội Đi Tiếp <span style="color: #f43f5e;">*</span></label>
                                <input type="number" id="stage1AdvanceSE" name="stage1AdvanceSE" class="form-control" style="background: #0b0d12; color: #ffffff; border-color: rgba(255, 255, 255, 0.15);" value="" min="1" placeholder="Nhập số đội đi tiếp...">
                            </div>

                            <!-- 4. Double Elimination Fields (Must be Power of 2) -->
                            <div id="stage1FieldsDE" style="display: none;">
                                <label class="form-label" style="font-size: 0.78rem; color: var(--text-muted);">Số Đội Đi Tiếp <span style="color: #f43f5e;">*</span> <span style="font-size: 0.72rem; color: #94a3b8;">(Bắt buộc là số mũ của 2: 2, 4, 8, 16...)</span></label>
                                <input type="number" id="stage1AdvanceDE" name="stage1AdvanceDE" class="form-control" style="background: #0b0d12; color: #ffffff; border-color: rgba(255, 255, 255, 0.15);" value="" min="2" step="2" placeholder="Ví dụ: 2, 4, 8, 16..." onchange="validatePowerOfTwoInput(this)">
                                <div id="dePowerErrorMsg1" style="display: none; color: #f43f5e; font-size: 0.75rem; margin-top: 0.4rem; font-weight: 600;">
                                    ⚠️ Số đội đi tiếp của Double Elimination bắt buộc phải là số mũ của 2 (2, 4, 8, 16...)
                                </div>
                            </div>

                            <!-- 5. Swiss System Fields (Fixed 16 in / 8 advance) -->
                            <div id="stage1FieldsSwiss" style="display: none;">
                                <div style="display: flex; align-items: center; justify-content: space-between; background: rgba(45, 212, 191, 0.08); border: 1px solid rgba(45, 212, 191, 0.3); border-radius: 8px; padding: 0.75rem 1rem;">
                                    <div style="color: #cbd5e1; font-size: 0.82rem; font-weight: 600;">
                                        <i class="fa-solid fa-lock" style="color: #2dd4bf; margin-right: 0.4rem;"></i>
                                        Cấu hình cố định Swiss System:
                                    </div>
                                    <div style="display: flex; gap: 0.5rem;">
                                        <span class="stage-badge" style="background: rgba(255,255,255,0.08); color: #ffffff; border-color: rgba(255,255,255,0.2);">16 Đội Tham Gia</span>
                                        <span class="stage-badge" style="background: rgba(45, 212, 191, 0.2); color: #2dd4bf; border-color: rgba(45, 212, 191, 0.4);">8 Đội Đi Tiếp</span>
                                    </div>
                                </div>
                            </div>

                        </div>
                    </div>

                    <!-- STAGE 2 -->
                    <div style="background: rgba(11, 13, 18, 0.7); border: 1px solid rgba(45, 212, 191, 0.25); border-radius: 12px; padding: 1.25rem; margin-bottom: 1.25rem;">
                        <div style="font-size: 0.95rem; font-weight: 800; color: #2dd4bf; margin-bottom: 0.85rem; display: flex; align-items: center; gap: 0.5rem;">
                            <i class="fa-solid fa-trophy"></i> STAGE 2
                        </div>

                        <label class="form-label" style="font-size: 0.78rem; color: var(--text-muted); margin-bottom: 0.5rem;">
                            Thể thức:
                        </label>
                        <div class="format-pill-grid" style="margin-bottom: 0.5rem;">
                            <button type="button" class="format-pill-btn active" id="pillStage2SE" onclick="selectStage2Format('SINGLE_ELIMINATION')">
                                Single Elimination
                            </button>
                            <button type="button" class="format-pill-btn" id="pillStage2DE" onclick="selectStage2Format('DOUBLE_ELIMINATION')">
                                Double Elimination
                            </button>
                            <button type="button" class="format-pill-btn" id="pillStage2RR" onclick="selectStage2Format('ROUND_ROBIN')">
                                Round Robin
                            </button>
                        </div>

                        <!-- DYNAMIC SUB-PANEL FOR STAGE 2 FORMAT CONFIG -->
                        <div id="stage2ConfigContainer" style="display: none; background: #181d29; border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 10px; padding: 1rem; margin-top: 0.85rem;">
                            <!-- Round Robin Fields (Stage 2) -->
                            <div id="stage2FieldsRR">
                                <div style="background: rgba(11, 13, 18, 0.6); border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 8px; padding: 0.85rem;">
                                    <div style="font-size: 0.75rem; font-weight: 700; color: #fbbf24; margin-bottom: 0.5rem;">
                                        <i class="fa-solid fa-sliders"></i> QUY TẮC CỘNG ĐIỂM & SỐ LẦN GẶP NHAU (STAGE 2)
                                    </div>
                                    <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.5rem;">
                                        <div>
                                            <label class="form-label" style="font-size: 0.72rem; color: var(--text-muted);">Điểm Thắng</label>
                                            <input type="number" id="stage2WinPointsRR" name="stage2WinPointsRR" class="form-control" style="background: #0b0d12; color: #ffffff; border-color: rgba(255, 255, 255, 0.15);" value="3" min="0" max="10">
                                        </div>
                                        <div>
                                            <label class="form-label" style="font-size: 0.72rem; color: var(--text-muted);">Điểm Hòa</label>
                                            <input type="number" id="stage2DrawPointsRR" name="stage2DrawPointsRR" class="form-control" style="background: #0b0d12; color: #ffffff; border-color: rgba(255, 255, 255, 0.15);" value="1" min="0" max="10">
                                        </div>
                                        <div>
                                            <label class="form-label" style="font-size: 0.72rem; color: var(--text-muted);">Điểm Thua</label>
                                            <input type="number" id="stage2LossPointsRR" name="stage2LossPointsRR" class="form-control" style="background: #0b0d12; color: #ffffff; border-color: rgba(255, 255, 255, 0.15);" value="0" min="0" max="10">
                                        </div>
                                        <div>
                                            <label class="form-label" style="font-size: 0.72rem; color: var(--text-muted);">Số Lượt Gặp Nhau</label>
                                            <input type="number" id="stage2LegsCountRR" name="stage2LegsCountRR" class="form-control" style="background: #0b0d12; color: #ffffff; border-color: rgba(255, 255, 255, 0.15);" value="1" min="1" max="10">
                                        </div>
                                    </div>
                                </div>
                            </div>
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
        var originalType = "<%= currentType %>";
        var hasOngoingMatches = false;
        var bypassWarning = false;
        var initialLegsCount = 1;

        function isPowerOfTwo(n) {
            var val = Number(n);
            return val > 0 && (val & (val - 1)) === 0;
        }

        function validatePowerOfTwoInput(inputEl) {
            var val = Number(inputEl.value);
            var isErr = inputEl && inputEl.value.trim() !== '' && !isPowerOfTwo(val);
            var err1 = document.getElementById('dePowerErrorMsg1');
            var err2 = document.getElementById('dePowerErrorMsg2');
            if (err1) err1.style.display = isErr ? 'block' : 'none';
            if (err2) err2.style.display = isErr ? 'block' : 'none';
            return !isErr;
        }

        var hasStage1Matches = false;
        var hasStage2Matches = false;

        // Check if bracket already has completed / scored matches for Stage 1 vs Stage 2
        if (tournamentId) {
            try {
                var gObj = JSON.parse(localStorage.getItem("tourma_group_matches_" + tournamentId));
                var rrObj = JSON.parse(localStorage.getItem("tourma_rr_matches_" + tournamentId));
                var uObj = JSON.parse(localStorage.getItem("tourma_matches_" + tournamentId));
                var deObj = JSON.parse(localStorage.getItem("tourma_de_matches_" + tournamentId));

                function checkMatchesPlayed(mObj) {
                    if (!mObj) return false;
                    var matchesMap = mObj.matchesMap || mObj;
                    if (typeof matchesMap !== 'object') return false;
                    var keys = Object.keys(matchesMap);
                    for (var i = 0; i < keys.length; i++) {
                        var m = matchesMap[keys[i]];
                        if (m && (m.status === 'COMPLETED' || m.status === 'done' || 
                                 (m.team1 && m.team1.score !== undefined && String(m.team1.score).trim() !== '') || 
                                 (m.team2 && m.team2.score !== undefined && String(m.team2.score).trim() !== ''))) {
                            return true;
                        }
                    }
                    return false;
                }

                if (checkMatchesPlayed(gObj) || checkMatchesPlayed(rrObj)) {
                    hasStage1Matches = true;
                }

                if (checkMatchesPlayed(uObj) || checkMatchesPlayed(deObj)) {
                    var mCfgRaw = localStorage.getItem("tourma_multi_config_" + tournamentId);
                    if (mCfgRaw) {
                        try {
                            var mCfg = JSON.parse(mCfgRaw);
                            if (mCfg && mCfg.stage2MatchesCreated) {
                                hasStage2Matches = true;
                            } else {
                                hasStage1Matches = true;
                            }
                        } catch(e) { hasStage1Matches = true; }
                    } else {
                        hasStage1Matches = true;
                    }
                }
            } catch (e) {}
        }

        hasOngoingMatches = hasStage1Matches || hasStage2Matches;

        // Select Stage Model (SINGLE_STAGE vs MULTI_STAGE)
        function selectStageType(typeValue) {
            if (hasStage1Matches) {
                alert('🔒 Stage 1 đã có trận đấu diễn ra. Không thể thay đổi mô hình giải đấu!');
                return;
            }
            document.getElementById('selectedTournamentType').value = typeValue;

            var btnSingle = document.getElementById('btnToggleSingleStage');
            var btnMulti = document.getElementById('btnToggleMultiStage');
            var singlePanel = document.getElementById('singleStagePanel');
            var multiPanel = document.getElementById('multiStagePanel');
            var badgeDisplay = document.getElementById('stageBadgeDisplay');

            if (typeValue === 'MULTI_STAGE') {
                if (btnSingle) btnSingle.classList.remove('active');
                if (btnMulti) btnMulti.classList.add('active');
                if (singlePanel) singlePanel.style.display = 'none';
                if (multiPanel) multiPanel.style.display = 'block';
                if (badgeDisplay) {
                    badgeDisplay.innerText = 'MULTI STAGE';
                    badgeDisplay.style.background = 'rgba(45, 212, 191, 0.12)';
                    badgeDisplay.style.color = '#2dd4bf';
                    badgeDisplay.style.borderColor = 'rgba(45, 212, 191, 0.3)';
                }
            } else {
                if (btnSingle) btnSingle.classList.add('active');
                if (btnMulti) btnMulti.classList.remove('active');
                if (singlePanel) singlePanel.style.display = 'block';
                if (multiPanel) multiPanel.style.display = 'none';
                if (badgeDisplay) {
                    badgeDisplay.innerText = 'SINGLE STAGE';
                    badgeDisplay.style.background = 'rgba(251, 191, 36, 0.12)';
                    badgeDisplay.style.color = '#fbbf24';
                    badgeDisplay.style.borderColor = 'rgba(251, 191, 36, 0.3)';
                }
            }
        }

        // Single Stage format selection
        function selectFormat(formatValue) {
            if (hasStage1Matches) {
                alert('🔒 Stage 1 đã có trận đấu diễn ra. Thể thức Stage 1 đã bị khóa!');
                return;
            }
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

        // Multi Stage: Stage 1 format selection
        function selectStage1Format(formatVal) {
            if (hasStage1Matches) {
                alert('🔒 Stage 1 đã có trận đấu diễn ra. Thể thức Stage 1 đã bị khóa!');
                return;
            }
            document.getElementById('stage1Format').value = formatVal;
            document.getElementById('selectedFormat').value = formatVal;
            var ids = ['pillStage1RR', 'pillStage1GR', 'pillStage1SE', 'pillStage1DE', 'pillStage1Swiss'];
            var vals = ['ROUND_ROBIN', 'GROUP_STAGE', 'SINGLE_ELIMINATION', 'DOUBLE_ELIMINATION', 'SWISS_LITE'];
            for (var i = 0; i < ids.length; i++) {
                var btn = document.getElementById(ids[i]);
                if (btn) btn.classList.toggle('active', vals[i] === formatVal);
            }

            // Toggle sub-panels for dynamic input fields
            var pRR = document.getElementById('stage1FieldsRR');
            var pGR = document.getElementById('stage1FieldsGR');
            var pSE = document.getElementById('stage1FieldsSE');
            var pDE = document.getElementById('stage1FieldsDE');
            var pSwiss = document.getElementById('stage1FieldsSwiss');

            if (pRR) pRR.style.display = (formatVal === 'ROUND_ROBIN') ? 'block' : 'none';
            if (pGR) pGR.style.display = (formatVal === 'GROUP_STAGE') ? 'block' : 'none';
            if (pSE) pSE.style.display = (formatVal === 'SINGLE_ELIMINATION') ? 'block' : 'none';
            if (pDE) pDE.style.display = (formatVal === 'DOUBLE_ELIMINATION') ? 'block' : 'none';
            if (pSwiss) pSwiss.style.display = (formatVal === 'SWISS_LITE') ? 'block' : 'none';
        }

        // Multi Stage: Stage 2 format selection (Can still be changed if Stage 2 has not started!)
        function selectStage2Format(formatVal) {
            if (hasStage2Matches) {
                alert('🔒 Stage 2 đã có trận đấu diễn ra. Thể thức Stage 2 đã bị khóa!');
                return;
            }
            document.getElementById('stage2Format').value = formatVal;
            var btnSE = document.getElementById('pillStage2SE');
            var btnDE = document.getElementById('pillStage2DE');
            var btnRR = document.getElementById('pillStage2RR');

            if (btnSE) btnSE.classList.toggle('active', formatVal === 'SINGLE_ELIMINATION');
            if (btnDE) btnDE.classList.toggle('active', formatVal === 'DOUBLE_ELIMINATION');
            if (btnRR) btnRR.classList.toggle('active', formatVal === 'ROUND_ROBIN');

            var s2ConfigContainer = document.getElementById('stage2ConfigContainer');
            if (s2ConfigContainer) {
                s2ConfigContainer.style.display = (formatVal === 'ROUND_ROBIN') ? 'block' : 'none';
            }
        }

        function persistFormatSelection() {
            var selectedType = document.getElementById('selectedTournamentType').value;
            var selectedVal = document.getElementById('selectedFormat').value;

            if (selectedType === 'MULTI_STAGE') {
                var s1F = document.getElementById('stage1Format').value;
                selectedVal = s1F;
                document.getElementById('selectedFormat').value = s1F;
            }

            if (tournamentId) {
                localStorage.setItem("tourma_type_" + tournamentId, selectedType);
                localStorage.setItem(storageKeyFormat, selectedVal);
                
                var advanceCountToSave = 0;
                if (selectedType === 'MULTI_STAGE') {
                    var s1F = document.getElementById('stage1Format').value;
                    var s2F = document.getElementById('stage2Format').value;
                    var s1Cfg = getStageConfigValues(1, s1F);
                    var s2Cfg = getStageConfigValues(2, s2F);

                    var multiConfig = {
                        stage1Format: s1F,
                        stage2Format: s2F,
                        stage1Config: s1Cfg,
                        stage2Config: s2Cfg
                    };
                    localStorage.setItem('tourma_multi_config_' + tournamentId, JSON.stringify(multiConfig));
                    if (s1Cfg) advanceCountToSave = s1Cfg.totalAdvanceCount || s1Cfg.advanceCount || 0;
                } else {
                    var sSingleCfg = getStageConfigValues(1, selectedVal);
                    if (sSingleCfg) advanceCountToSave = sSingleCfg.totalAdvanceCount || sSingleCfg.advanceCount || 0;

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

                if (advanceCountToSave > 0) {
                    localStorage.setItem('tourma_advance_count_' + tournamentId, advanceCountToSave);
                }
            }
        }

        function getStageConfigValues(stageNum, formatVal) {
            var prefix = 'stage' + stageNum;
            if (formatVal === 'ROUND_ROBIN') {
                var adv = document.getElementById(prefix + 'AdvanceRR');
                var win = document.getElementById(prefix + 'WinPointsRR');
                var draw = document.getElementById(prefix + 'DrawPointsRR');
                var loss = document.getElementById(prefix + 'LossPointsRR');
                var legs = document.getElementById(prefix + 'LegsCountRR');
                return {
                    advanceCount: adv ? (parseInt(adv.value) || 0) : 0,
                    winPoints: win ? (parseInt(win.value) || 3) : 3,
                    drawPoints: draw ? (parseInt(draw.value) || 1) : 1,
                    lossPoints: loss ? (parseInt(loss.value) || 0) : 0,
                    legsCount: legs ? (parseInt(legs.value) || 1) : 1
                };
            } else if (formatVal === 'GROUP_STAGE') {
                var adv = document.getElementById(prefix + 'AdvanceGR');
                var win = document.getElementById(prefix + 'WinPointsGR');
                var draw = document.getElementById(prefix + 'DrawPointsGR');
                var loss = document.getElementById(prefix + 'LossPointsGR');
                var legs = document.getElementById(prefix + 'LegsCountGR');
                return {
                    totalAdvanceCount: adv ? (parseInt(adv.value) || 0) : 0,
                    winPoints: win ? (parseInt(win.value) || 3) : 3,
                    drawPoints: draw ? (parseInt(draw.value) || 1) : 1,
                    lossPoints: loss ? (parseInt(loss.value) || 0) : 0,
                    legsCount: legs ? (parseInt(legs.value) || 1) : 1
                };
            } else if (formatVal === 'SINGLE_ELIMINATION') {
                var adv = document.getElementById(prefix + 'AdvanceSE');
                return { advanceCount: adv ? (parseInt(adv.value) || 0) : 0 };
            } else if (formatVal === 'DOUBLE_ELIMINATION') {
                var adv = document.getElementById(prefix + 'AdvanceDE');
                return { advanceCount: adv ? (parseInt(adv.value) || 0) : 0 };
            } else if (formatVal === 'SWISS_LITE') {
                return { numTeams: 16, advanceCount: 8 };
            }
            return {};
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
            selectStageType(originalType);
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

        function getTotalTeamsCount() {
            if (tournamentId) {
                var rawTeams = localStorage.getItem('tourma_teams_' + tournamentId);
                if (rawTeams) {
                    try {
                        var parsed = JSON.parse(rawTeams);
                        if (Array.isArray(parsed) && parsed.length > 0) return parsed.length;
                    } catch (e) {}
                }
                var gRaw = localStorage.getItem('tourma_group_assignments_' + tournamentId);
                if (gRaw) {
                    try {
                        var gObj = JSON.parse(gRaw);
                        var total = 0;
                        Object.keys(gObj).forEach(function(k) { if (Array.isArray(gObj[k])) total += gObj[k].length; });
                        if (total > 0) return total;
                    } catch (e) {}
                }
            }
            if (window.serverTeams && window.serverTeams.length > 0) return window.serverTeams.length;
            return 0;
        }

        function validateAdvCountLessThanTotal(advVal, inputEl, e, label) {
            var numAdv = Number(advVal);
            var totalTeams = getTotalTeamsCount();
            if (totalTeams > 0 && numAdv >= totalTeams) {
                if (e && e.preventDefault) e.preventDefault();
                alert('⚠️ Số đội đi tiếp (' + numAdv + ' đội) phải luôn NHỎ HƠN tổng số đội hiện có của giải đấu (' + totalTeams + ' đội)!\n\nVui lòng nhập số đội đi tiếp nhỏ hơn ' + totalTeams + '.');
                if (inputEl) inputEl.focus();
                return false;
            }
            return true;
        }

        function validateStageInputs(stageNum, formatVal, e) {
            var prefix = 'stage' + stageNum;
            var stageName = 'Stage ' + stageNum;

            if (formatVal === 'ROUND_ROBIN') {
                var el = document.getElementById(prefix + 'AdvanceRR');
                var val = el ? el.value.trim() : '';
                if (!val || Number(val) <= 0) {
                    if (e && e.preventDefault) e.preventDefault();
                    alert('Vui lòng nhập số đội đi tiếp ở ' + stageName + '!');
                    if (el) el.focus();
                    return false;
                }
                if (!validateAdvCountLessThanTotal(val, el, e, stageName)) return false;
            } else if (formatVal === 'GROUP_STAGE') {
                var advG = document.getElementById(prefix + 'AdvanceGR');
                var advGVal = advG ? advG.value.trim() : '';

                if (!advGVal || Number(advGVal) <= 0) {
                    if (e && e.preventDefault) e.preventDefault();
                    alert('Vui lòng nhập tổng số đội đi tiếp ở ' + stageName + '!');
                    if (advG) advG.focus();
                    return false;
                }
                if (!validateAdvCountLessThanTotal(advGVal, advG, e, stageName)) return false;
            } else if (formatVal === 'SINGLE_ELIMINATION') {
                var el = document.getElementById(prefix + 'AdvanceSE');
                var val = el ? el.value.trim() : '';
                if (!val || Number(val) <= 0) {
                    if (e && e.preventDefault) e.preventDefault();
                    alert('Vui lòng nhập số đội đi tiếp ở ' + stageName + '!');
                    if (el) el.focus();
                    return false;
                }
                if (!validateAdvCountLessThanTotal(val, el, e, stageName)) return false;
            } else if (formatVal === 'DOUBLE_ELIMINATION') {
                var deInp = document.getElementById(prefix + 'AdvanceDE');
                var val = deInp ? deInp.value.trim() : '';
                if (!val || Number(val) <= 0) {
                    if (e && e.preventDefault) e.preventDefault();
                    alert('Vui lòng nhập số đội đi tiếp ở ' + stageName + '!');
                    if (deInp) deInp.focus();
                    return false;
                }
                if (!validateAdvCountLessThanTotal(val, deInp, e, stageName)) return false;
                if (!validatePowerOfTwoInput(deInp)) {
                    if (e && e.preventDefault) e.preventDefault();
                    alert('Số đội đi tiếp của thể thức Double Elimination ở ' + stageName + ' bắt buộc phải là số mũ của 2 (ví dụ: 2, 4, 8, 16...)');
                    if (deInp) deInp.focus();
                    return false;
                }
            }
            return true;
        }

        function validateAndSubmitFormat(e) {
            if (bypassWarning) return true;

            var currentType = document.getElementById('selectedTournamentType').value;
            var currentSelected = document.getElementById('selectedFormat').value;

            // Validate Multi Stage required fields for Stage 1
            if (currentType === 'MULTI_STAGE') {
                var s1F = document.getElementById('stage1Format').value;
                if (!validateStageInputs(1, s1F, e)) return false;
            }

            // If Stage 1 has started and Stage 1 format or type changed, block and show popup!
            if (hasStage1Matches && (currentSelected !== originalFormat || currentType !== originalType)) {
                if (e && e.preventDefault) e.preventDefault();
                openFormatLockedModal();
                return false;
            }

            // If Round Robin and existing schedule exists, check if legsCount changed
            if (currentType === 'SINGLE_STAGE' && currentSelected === 'ROUND_ROBIN' && tournamentId) {
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
            var savedType = "<%= currentType %>";
            var savedFormat = "<%= currentFormat %>";
            if (tournamentId) {
                if (localStorage.getItem("tourma_type_" + tournamentId)) {
                    savedType = localStorage.getItem("tourma_type_" + tournamentId);
                }
                if (localStorage.getItem(storageKeyFormat)) {
                    savedFormat = localStorage.getItem(storageKeyFormat);
                }
            }
            originalType = savedType || 'SINGLE_STAGE';
            originalFormat = savedFormat || 'SINGLE_ELIMINATION';

            selectStageType(originalType);
            selectFormat(originalFormat);

            // Restore Multi Stage config if exists
            if (tournamentId && localStorage.getItem('tourma_multi_config_' + tournamentId)) {
                try {
                    var mCfg = JSON.parse(localStorage.getItem('tourma_multi_config_' + tournamentId));
                    if (mCfg) {
                        if (mCfg.stage1Format) selectStage1Format(mCfg.stage1Format);
                        if (mCfg.stage2Format) selectStage2Format(mCfg.stage2Format);

                        var restoreStage = function (stageNum, sFormat, cfg) {
                            var prefix = 'stage' + stageNum;
                            if (!cfg) return;
                            if (sFormat === 'ROUND_ROBIN') {
                                var el = document.getElementById(prefix + 'AdvanceRR');
                                var win = document.getElementById(prefix + 'WinPointsRR');
                                var draw = document.getElementById(prefix + 'DrawPointsRR');
                                var loss = document.getElementById(prefix + 'LossPointsRR');
                                var legs = document.getElementById(prefix + 'LegsCountRR');
                                if (el && cfg.advanceCount) el.value = cfg.advanceCount;
                                if (win && cfg.winPoints !== undefined) win.value = cfg.winPoints;
                                if (draw && cfg.drawPoints !== undefined) draw.value = cfg.drawPoints;
                                if (loss && cfg.lossPoints !== undefined) loss.value = cfg.lossPoints;
                                if (legs && cfg.legsCount !== undefined) legs.value = cfg.legsCount;
                            } else if (sFormat === 'GROUP_STAGE') {
                                var advEl = document.getElementById(prefix + 'AdvanceGR');
                                var winEl = document.getElementById(prefix + 'WinPointsGR');
                                var drawEl = document.getElementById(prefix + 'DrawPointsGR');
                                var lossEl = document.getElementById(prefix + 'LossPointsGR');
                                var legsEl = document.getElementById(prefix + 'LegsCountGR');
                                if (advEl && cfg.totalAdvanceCount) advEl.value = cfg.totalAdvanceCount;
                                if (winEl && cfg.winPoints !== undefined) winEl.value = cfg.winPoints;
                                if (drawEl && cfg.drawPoints !== undefined) drawEl.value = cfg.drawPoints;
                                if (lossEl && cfg.lossPoints !== undefined) lossEl.value = cfg.lossPoints;
                                if (legsEl && cfg.legsCount !== undefined) legsEl.value = cfg.legsCount;
                            } else if (sFormat === 'SINGLE_ELIMINATION' && cfg.advanceCount) {
                                var el = document.getElementById(prefix + 'AdvanceSE');
                                if (el) el.value = cfg.advanceCount;
                            } else if (sFormat === 'DOUBLE_ELIMINATION' && cfg.advanceCount) {
                                var el = document.getElementById(prefix + 'AdvanceDE');
                                if (el) el.value = cfg.advanceCount;
                            }
                        };

                        restoreStage(1, mCfg.stage1Format, mCfg.stage1Config);
                        restoreStage(2, mCfg.stage2Format, mCfg.stage2Config);
                    }
                } catch(e) {}
            }

            // Apply Stage Locks UI
            if (hasStage1Matches) {
                var btnSingle = document.getElementById('btnToggleSingleStage');
                var btnMulti = document.getElementById('btnToggleMultiStage');
                if (btnSingle) { btnSingle.disabled = true; btnSingle.style.opacity = '0.55'; btnSingle.title = '🔒 Stage 1 đã có trận đấu diễn ra, không thể thay đổi mô hình giải!'; }
                if (btnMulti) { btnMulti.disabled = true; btnMulti.style.opacity = '0.55'; btnMulti.title = '🔒 Stage 1 đã có trận đấu diễn ra, không thể thay đổi mô hình giải!'; }

                var s1Pills = ['pillStage1RR', 'pillStage1GR', 'pillStage1SE', 'pillStage1DE', 'pillStage1Swiss', 'pillSingleElim', 'pillDoubleElim', 'pillRoundRobin'];
                s1Pills.forEach(function(id) {
                    var btn = document.getElementById(id);
                    if (btn) {
                        btn.disabled = true;
                        btn.style.opacity = '0.55';
                        btn.style.cursor = 'not-allowed';
                        btn.title = '🔒 Stage 1 đã có trận đấu diễn ra, thể thức Stage 1 đã bị khóa!';
                    }
                });
            }

            if (hasStage2Matches) {
                var s2Pills = ['pillStage2SE', 'pillStage2DE', 'pillStage2RR'];
                s2Pills.forEach(function(id) {
                    var btn = document.getElementById(id);
                    if (btn) {
                        btn.disabled = true;
                        btn.style.opacity = '0.55';
                        btn.style.cursor = 'not-allowed';
                        btn.title = '🔒 Stage 2 đã có trận đấu diễn ra, thể thức Stage 2 đã bị khóa!';
                    }
                });
            }
        });
    </script>
</body>

</html>