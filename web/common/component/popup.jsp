<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%-- 
    Document   : popup.jsp (Score Edit Popup Modal Component - Wireframe Layout)
    Description: Reusable modal dialog for editing match scores and declaring winners.
--%>

<link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css">

<div id="tourmaScoreModalBackdrop" class="tourma-modal-backdrop" onclick="if(event.target === this) window.TourmaScoreModal.close();">
    <div class="tourma-modal-card" onclick="event.stopPropagation();">
        
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
                        <span id="modalTeam1Seed" class="modal-seed-badge">1</span>
                        <span id="modalTeam1Name" class="modal-team-name">Đội 1</span>
                    </div>
                    <input type="number" id="modalTeam1Score" class="modal-score-input-box" value="0" min="0" max="999" oninput="window.TourmaScoreModal.autoDetermineWinner()">
                </div>

                <!-- Team 2 Row -->
                <div class="modal-team-row">
                    <div class="modal-team-left-info">
                        <span id="modalTeam2Seed" class="modal-seed-badge">2</span>
                        <span id="modalTeam2Name" class="modal-team-name">Đội 2</span>
                    </div>
                    <input type="number" id="modalTeam2Score" class="modal-score-input-box" value="0" min="0" max="999" oninput="window.TourmaScoreModal.autoDetermineWinner()">
                </div>
            </div>

            <!-- Segmented Control Bar: Chọn Đội Thắng (Matching Wireframe Segmented Control) -->
            <div class="modal-winner-selection-group">
                <label class="modal-section-label">CHỌN ĐỘI THẮNG</label>
                <div class="segmented-control-bar">
                    <button type="button" id="btnWinnerT1" class="segmented-btn active" onclick="window.TourmaScoreModal.selectWinner('team1')">
                        Team 1
                    </button>
                    <button type="button" id="btnWinnerDraw" class="segmented-btn" style="display: none;" onclick="window.TourmaScoreModal.selectWinner('draw')">
                        Hòa
                    </button>
                    <button type="button" id="btnWinnerT2" class="segmented-btn" onclick="window.TourmaScoreModal.selectWinner('team2')">
                        Team 2
                    </button>
                </div>
            </div>

        </div>

        <!-- Modal Footer -->
        <div class="modal-footer-bar">
            <button type="button" class="btn-modal-cancel" onclick="window.TourmaScoreModal.close()">Hủy Bỏ</button>
            <button type="button" class="btn-modal-save" onclick="window.TourmaScoreModal.save()">
                <i class="fa-solid fa-check"></i> Lưu Kết Quả
            </button>
        </div>

    </div>
</div>

<script src="${pageContext.request.contextPath}/js/popup.js"></script>
