<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%--
    Document   : popup.jsp (Score Edit Popup Modal Component - Wireframe Layout)
    Description: Reusable modal dialog for editing match scores and declaring winners.
--%>

<link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css?v=5">

<div id="tourmaScoreModalBackdrop" class="tourma-modal-backdrop" onclick="if(event.target === this) window.TourmaScoreModal.close();">
    <div class="tourma-modal-card" role="dialog" aria-modal="true" aria-labelledby="modalMatchTitle" onclick="event.stopPropagation();">

        <!-- Modal Header -->
        <div class="modal-header-bar">
            <div class="modal-header-title">
                <i class="fa-solid fa-pen-to-square text-mint"></i>
                <span id="modalMatchTitle">Cập Nhật Tỷ Số Trận Đấu</span>
            </div>
            <button type="button" class="modal-close-btn" onclick="window.TourmaScoreModal.close()" title="Đóng">
                <i class="fa-solid fa-xmark"></i>
            </button>
        </div>

        <!-- Modal Body Content -->
        <div class="modal-body-content">
            <input type="hidden" id="modalMatchIdInput" value="">

            <!-- Stacked Teams & Score Input Boxes (Matching Wireframe Layout) -->
            <div class="modal-stacked-teams-box">
                <!-- Team 1 Row -->
                <div class="modal-team-row">
                    <div class="modal-team-left-info">
                        <span id="modalTeam1Name" class="modal-team-name">Đội 1</span>
                    </div>
                    <input type="number" id="modalTeam1Score" aria-labelledby="modalTeam1Name" class="modal-score-input-box" value="" placeholder="-" min="0" max="999" oninput="window.TourmaScoreModal.autoDetermineWinner()">
                </div>

                <!-- Team 2 Row -->
                <div class="modal-team-row">
                    <div class="modal-team-left-info">
                        <span id="modalTeam2Name" class="modal-team-name">Đội 2</span>
                    </div>
                    <input type="number" id="modalTeam2Score" aria-labelledby="modalTeam2Name" class="modal-score-input-box" value="" placeholder="-" min="0" max="999" oninput="window.TourmaScoreModal.autoDetermineWinner()">
                </div>
            </div>

            <!-- Segmented Control Bar: Chọn Đội Thắng (Matching Wireframe Segmented Control) -->
            <div class="modal-winner-selection-group">
                <label class="modal-section-label">CHỌN ĐỘI THẮNG</label>
                <div class="segmented-control-bar">
                    <button type="button" id="btnWinnerT1" class="segmented-btn" onclick="window.TourmaScoreModal.selectWinner('team1')">
                        Team 1
                    </button>
                    <button type="button" id="btnWinnerDraw" class="segmented-btn" style="display: none;" onclick="window.TourmaScoreModal.selectWinner('draw')">
                        Hòa
                    </button>
                    <button type="button" id="btnWinnerT2" class="segmented-btn" onclick="window.TourmaScoreModal.selectWinner('team2')">
                        Team 2
                    </button>
                </div>
                <!-- Error message banner when winner is not determined -->
                <div id="modalScoreError" data-ui-style="popup-jsp-001" style="display: none;">
                    <i class="fa-solid fa-triangle-exclamation"></i>
                    <span>Phải xác định 1 đội thắng</span>
                </div>
            </div>

        </div>

        <!-- Modal Footer -->
        <div class="modal-footer-bar">
            <!-- Left: Random Score Controls -->
            <div class="modal-footer-left-random">
                <input type="number" id="modalRandomWinScore" class="modal-random-input" value="" min="1" max="999" placeholder="-" title="Điểm đội thắng">
                <button type="button" id="btnModalRandomScore" class="btn-modal-random" onclick="window.TourmaScoreModal.randomizeAndSave()">
                    Random
                </button>
            </div>

            <!-- Right: Action Buttons -->
            <div class="modal-footer-right-actions">
                <button type="button" class="btn-modal-cancel" onclick="window.TourmaScoreModal.close()">Hủy Bỏ</button>
                <button type="button" class="btn-modal-save" onclick="window.TourmaScoreModal.save()">
                    <i class="fa-solid fa-check"></i> Lưu Kết Quả
                </button>
            </div>
        </div>

    </div>
</div>

<script src="${pageContext.request.contextPath}/js/random-service.js"></script>
<script src="${pageContext.request.contextPath}/js/popup.js"></script>
