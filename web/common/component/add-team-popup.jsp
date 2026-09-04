<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series"%>
<%
    Series seriesObj = (Series) request.getAttribute("series");
    String seriesIdParam = request.getParameter("seriesId");
    if (seriesIdParam == null || seriesIdParam.trim().isEmpty()) {
        seriesIdParam = (seriesObj != null && seriesObj.getId() != null) ? seriesObj.getId() : "";
    }
%>

<link rel="stylesheet" href="${pageContext.request.contextPath}/css/add-team-popup.css">

<!-- REUSABLE POPUP COMPONENT: THÊM ĐỘI BÓNG HÀNG LOẠT (BULK ADD TEAMS) -->
<div id="modalAddTeamPopup" class="add-team-modal-overlay" style="display: none;">
    <div class="add-team-modal-card">
        <div class="add-team-modal-header">
            <h3 class="add-team-modal-title">
                <i class="fa-solid fa-user-plus text-mint"></i> Đăng Ký Đội Bóng Partner
            </h3>
            <button type="button" class="add-team-modal-close" onclick="closeAddTeamPopup()">&times;</button>
        </div>

        <form method="POST" action="${pageContext.request.contextPath}/rolling/team-list">
            <input type="hidden" name="action" value="bulkAddPartners">
            <input type="hidden" name="seriesId" value="<%= seriesIdParam %>">

            <div style="margin-bottom: 1.25rem;">
                <label style="display: block; font-size: 0.88rem; font-weight: 700; color: #e2e8f0; margin-bottom: 0.4rem;">
                    Danh Sách Tên Đội Bóng (Mỗi đội 1 dòng) <span style="color: #ef4444;">*</span>
                </label>
                <textarea name="bulkTeamNames" required class="add-team-textarea" placeholder="Nhập hoặc dán danh sách tên các đội bóng tại đây...&#10;Ví dụ:&#10;Saigon Heat&#10;Thang Long Warriors&#10;Hanoi Buffaloes&#10;Danang Dragons"></textarea>
                <p style="font-size: 0.78rem; color: #94a3b8; margin-top: 0.35rem; margin-bottom: 0;">
                    <i class="fa-solid fa-info-circle text-mint"></i> Mã Partner ID sẽ được tự động sinh ngẫu nhiên và điểm khởi đầu mặc định là 0.
                </p>
            </div>

            <!-- MODAL FOOTER BUTTONS -->
            <div style="display: flex; justify-content: flex-end; gap: 0.75rem; border-top: 1px solid rgba(255, 255, 255, 0.1); padding-top: 1rem;">
                <button type="button" class="btn" onclick="closeAddTeamPopup()" style="background: rgba(255, 255, 255, 0.08); color: #cbd5e1; font-weight: 700; border-radius: 8px;">
                    Hủy Bỏ
                </button>
                <button type="submit" class="btn btn-mint" style="font-weight: 700; border-radius: 8px; padding: 0.65rem 1.35rem;">
                    <i class="fa-solid fa-check-circle"></i> Xác Nhận Đăng Ký
                </button>
            </div>
        </form>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/add-team-popup.js"></script>
