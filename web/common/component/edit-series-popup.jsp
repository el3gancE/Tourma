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

<link rel="stylesheet" href="${pageContext.request.contextPath}/css/add-team-popup.css">

<!-- REUSABLE POPUP COMPONENT: CHỈNH SỬA CẤU HÌNH SERIES & SỐ GIẢI TÍCH LŨY (PHASE SIZE W) -->
<div id="modalEditSeriesPopup" class="add-team-modal-overlay" style="display: none;">
    <div class="add-team-modal-card" style="max-width: 520px; border-color: rgba(251, 191, 36, 0.45); box-shadow: 0 25px 60px rgba(0, 0, 0, 0.8), 0 0 25px rgba(251, 191, 36, 0.15);">
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
            <div style="margin-bottom: 1.25rem;">
                <label style="display: block; font-size: 0.88rem; font-weight: 700; color: #e2e8f0; margin-bottom: 0.4rem;">
                    Tên Chuỗi Giải <span style="color: #ef4444;">*</span>
                </label>
                <input type="text" name="name" id="editSeriesNameInput" required class="form-control" 
                       value="<%= sName %>" placeholder="Ví dụ: VBA Pro League 2026 Circuit" 
                       style="font-size: 0.9rem; font-weight: 700; border-radius: 8px;">
            </div>

            <!-- 2. SỐ GIẢI TÍCH LŨY ĐIỂM (CỬA SỔ TRƯỢT W) -->
            <div style="margin-bottom: 1.25rem; background: rgba(251, 191, 36, 0.06); border: 1px solid rgba(251, 191, 36, 0.25); border-radius: 12px; padding: 1rem;">
                <label style="display: flex; justify-content: space-between; align-items: center; font-size: 0.88rem; font-weight: 800; color: #fbbf24; margin-bottom: 0.4rem;">
                    <span><i class="fa-solid fa-chart-line"></i> Số Giải Tích Lũy Điểm (Cửa Sổ Trượt W) <span style="color: #ef4444;">*</span></span>
                </label>
                
                <div style="display: flex; align-items: center; gap: 0.75rem; margin-top: 0.5rem; margin-bottom: 0.5rem;">
                    <button type="button" class="btn btn-secondary" onclick="adjustPhaseSize(-1)" style="font-weight: 800; font-size: 1.1rem; width: 42px; height: 42px; border-radius: 8px; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,0.08);">
                        <i class="fa-solid fa-minus"></i>
                    </button>
                    <input type="number" name="phaseSize" id="editSeriesPhaseSizeInput" required class="form-control" 
                           value="<%= sPhaseSize %>" min="1" max="100" 
                           style="font-size: 1.25rem; font-weight: 800; text-align: center; border-radius: 8px; color: #fbbf24; background: rgba(0, 0, 0, 0.4); border: 1px solid rgba(251, 191, 36, 0.4);">
                    <button type="button" class="btn btn-secondary" onclick="adjustPhaseSize(1)" style="font-weight: 800; font-size: 1.1rem; width: 42px; height: 42px; border-radius: 8px; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,0.08);">
                        <i class="fa-solid fa-plus"></i>
                    </button>
                </div>

                <!-- PRESET QUICK BUTTONS -->
                <div style="display: flex; gap: 0.4rem; flex-wrap: wrap; margin-top: 0.65rem;">
                    <span style="font-size: 0.75rem; color: #94a3b8; align-self: center; margin-right: 0.25rem;">Gợi ý:</span>
                    <button type="button" onclick="setPresetPhaseSize(3)" class="btn" style="font-size: 0.75rem; font-weight: 700; padding: 0.2rem 0.6rem; border-radius: 6px; background: rgba(255,255,255,0.06); color: #e2e8f0; border: 1px solid rgba(255,255,255,0.1);">3 giải</button>
                    <button type="button" onclick="setPresetPhaseSize(5)" class="btn" style="font-size: 0.75rem; font-weight: 700; padding: 0.2rem 0.6rem; border-radius: 6px; background: rgba(255,255,255,0.06); color: #e2e8f0; border: 1px solid rgba(255,255,255,0.1);">5 giải</button>
                    <button type="button" onclick="setPresetPhaseSize(10)" class="btn" style="font-size: 0.75rem; font-weight: 700; padding: 0.2rem 0.6rem; border-radius: 6px; background: rgba(255,255,255,0.06); color: #e2e8f0; border: 1px solid rgba(255,255,255,0.1);">10 giải</button>
                    <button type="button" onclick="setPresetPhaseSize(26)" class="btn" style="font-size: 0.75rem; font-weight: 700; padding: 0.2rem 0.6rem; border-radius: 6px; background: rgba(255,255,255,0.06); color: #e2e8f0; border: 1px solid rgba(255,255,255,0.1);">26 giải</button>
                </div>

                <p style="font-size: 0.78rem; color: #cbd5e1; margin-top: 0.75rem; margin-bottom: 0; line-height: 1.45;">
                    <i class="fa-solid fa-info-circle text-gold"></i> <strong>Cơ chế Rolling Window:</strong> Bảng Xếp Hạng chỉ cộng dồn điểm của <strong>W giải đấu gần nhất</strong>. Khi giải mới được hoàn thành vượt quá W, điểm của giải cũ nhất ngoài cửa sổ sẽ tự động hết hạn và khấu trừ khỏi tổng điểm.
                </p>
            </div>

            <!-- 3. TRẠNG THÁI SERIES -->
            <div style="margin-bottom: 1.5rem;">
                <label style="display: block; font-size: 0.88rem; font-weight: 700; color: #e2e8f0; margin-bottom: 0.4rem;">
                    Trạng Thái Mùa Giải
                </label>
                <select name="status" id="editSeriesStatusSelect" class="form-control" style="font-size: 0.88rem; font-weight: 700; border-radius: 8px;">
                    <option value="ACTIVE" <%= "ACTIVE".equalsIgnoreCase(sStatus) ? "selected" : "" %>>Đang Diễn Ra (ACTIVE)</option>
                    <option value="COMPLETED" <%= "COMPLETED".equalsIgnoreCase(sStatus) ? "selected" : "" %>>Đã Kết Thúc (COMPLETED)</option>
                </select>
            </div>

            <!-- MODAL FOOTER BUTTONS -->
            <div style="display: flex; justify-content: flex-end; gap: 0.75rem; border-top: 1px solid rgba(255, 255, 255, 0.1); padding-top: 1rem;">
                <button type="button" class="btn" onclick="closeEditSeriesPopup()" style="background: rgba(255, 255, 255, 0.08); color: #cbd5e1; font-weight: 700; border-radius: 8px;">
                    Hủy Bỏ
                </button>
                <button type="submit" class="btn btn-mint" style="font-weight: 700; border-radius: 8px; padding: 0.65rem 1.4rem;">
                    <i class="fa-solid fa-floppy-disk"></i> Lưu Thay Đổi & Cập Nhật BXH
                </button>
            </div>
        </form>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/edit-series-popup.js?v=<%= System.currentTimeMillis() %>"></script>
