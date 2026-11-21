<%@page contentType="text/html" pageEncoding="UTF-8"%>

<%@page import="model.Series"%>
<%
    Series currentSeriesForModal = (Series) request.getAttribute("series");
    String sId = request.getParameter("seriesId");
    if (sId == null || sId.trim().isEmpty()) {
        sId = (currentSeriesForModal != null && currentSeriesForModal.getId() != null) ? currentSeriesForModal.getId() : "";
    }
    String sName = (currentSeriesForModal != null && currentSeriesForModal.getName() != null) ? currentSeriesForModal.getName() : "";
    int sPhaseSize = (currentSeriesForModal != null && currentSeriesForModal.getPhaseSize() > 0) ? currentSeriesForModal.getPhaseSize() : 10;
    String sStatus = (currentSeriesForModal != null && currentSeriesForModal.getStatus() != null) ? currentSeriesForModal.getStatus() : "ACTIVE";
    String formActionUrl = request.getParameter("formActionUrl");
    if (formActionUrl == null || formActionUrl.trim().isEmpty()) {
        formActionUrl = request.getContextPath() + "/rolling/dashboard";
    }
%>


<!-- REUSABLE POPUP COMPONENT: CHỈNH SỬA CẤU HÌNH SERIES & SỐ GIẢI TÍCH LŨY (PHASE SIZE W) -->
<div id="modalEditSeriesPopup" class="add-team-modal-overlay">
    <div class="add-team-modal-card">
        <div class="add-team-modal-header">
            <h3 class="add-team-modal-title">
                <i class="fa-solid fa-sliders text-gold"></i> Cấu Hình Chuỗi Giải (Series)
            </h3>
            <button type="button" class="add-team-modal-close" onclick="closeEditSeriesPopup()">&times;</button>
        </div>

        <form id="editSeriesForm" method="POST" action="<%= formActionUrl %>">
            <input type="hidden" name="action" value="updateSettings">
            <input type="hidden" name="seriesId" id="editSeriesIdInput" value="<%= sId %>">

            <!-- 1. TÊN CHUỖI GIẢI -->
            <div>
                <label>
                    Tên Chuỗi Giải <span>*</span>
                </label>
                <input type="text" name="name" id="editSeriesNameInput" required class="form-control"
                       value="<%= sName %>" placeholder="Ví dụ: VBA Pro League 2026 Circuit"
>
            </div>

            <!-- 2. SỐ GIẢI TÍCH LŨY ĐIỂM (CỬA SỔ TRƯỢT W) -->
            <div>
                <label>
                    <span><i class="fa-solid fa-chart-line"></i> Số Giải Tích Lũy Điểm (Cửa Sổ Trượt W) <span>*</span></span>
                </label>

                <div>
                    <button type="button" class="btn btn-secondary" onclick="adjustPhaseSize(-1)">
                        <i class="fa-solid fa-minus"></i>
                    </button>
                    <input type="number" name="phaseSize" id="editSeriesPhaseSizeInput" required class="form-control"
                           value="<%= sPhaseSize %>" min="1" max="100"
>
                    <button type="button" class="btn btn-secondary" onclick="adjustPhaseSize(1)">
                        <i class="fa-solid fa-plus"></i>
                    </button>
                </div>

                <!-- PRESET QUICK BUTTONS -->
                <div>
                    <span>Gợi ý:</span>
                    <button type="button" onclick="setPresetPhaseSize(3)" class="btn">3 giải</button>
                    <button type="button" onclick="setPresetPhaseSize(5)" class="btn">5 giải</button>
                    <button type="button" onclick="setPresetPhaseSize(10)" class="btn">10 giải</button>
                    <button type="button" onclick="setPresetPhaseSize(26)" class="btn">26 giải</button>
                </div>

                <p>
                    <i class="fa-solid fa-info-circle text-gold"></i> <strong>Cơ chế Rolling Window:</strong> Bảng Xếp Hạng chỉ cộng dồn điểm của <strong>W giải đấu gần nhất</strong>. Khi giải mới được hoàn thành vượt quá W, điểm của giải cũ nhất ngoài cửa sổ sẽ tự động hết hạn và khấu trừ khỏi tổng điểm.
                </p>
            </div>

            <!-- 3. TRẠNG THÁI SERIES -->
            <div>
                <label>
                    Trạng Thái Mùa Giải
                </label>
                <select name="status" id="editSeriesStatusSelect" class="form-control">
                    <option value="ACTIVE" <%= "ACTIVE".equalsIgnoreCase(sStatus) ? "selected" : "" %>>Đang Diễn Ra (ACTIVE)</option>
                    <option value="COMPLETED" <%= "COMPLETED".equalsIgnoreCase(sStatus) ? "selected" : "" %>>Đã Kết Thúc (COMPLETED)</option>
                </select>
            </div>

            <!-- MODAL FOOTER BUTTONS -->
            <div>
                <button type="button" class="btn" onclick="closeEditSeriesPopup()">
                    Hủy Bỏ
                </button>
                <button type="submit" class="btn btn-mint">
                    <i class="fa-solid fa-floppy-disk"></i> Lưu Thay Đổi & Cập Nhật BXH
                </button>
            </div>
        </form>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/edit-series-popup.js?v=<%= System.currentTimeMillis() %>"></script>
