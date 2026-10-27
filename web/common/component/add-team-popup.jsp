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

            <div data-ui-style="add-team-popup-jsp-001">
                <label data-ui-style="add-team-popup-jsp-002" style="display: block;">
                    Danh Sách Tên Đội Bóng (Mỗi đội 1 dòng) <span data-ui-style="add-team-popup-jsp-003">*</span>
                </label>
                <textarea name="bulkTeamNames" required class="add-team-textarea" placeholder="Nhập hoặc dán danh sách tên các đội bóng tại đây...&#10;Ví dụ:&#10;Saigon Heat&#10;Thang Long Warriors&#10;Hanoi Buffaloes&#10;Danang Dragons"></textarea>
                <p data-ui-style="add-team-popup-jsp-004">
                    <i class="fa-solid fa-info-circle text-mint"></i> Mã Partner ID sẽ được tự động sinh ngẫu nhiên và điểm khởi đầu mặc định là 0.
                </p>
            </div>

            <!-- MODAL FOOTER BUTTONS -->
            <div data-ui-style="add-team-popup-jsp-005" style="display: flex;">
                <button type="button" class="btn" onclick="closeAddTeamPopup()" data-ui-style="add-team-popup-jsp-006">
                    Hủy Bỏ
                </button>
                <button type="submit" class="btn btn-mint" data-ui-style="add-team-popup-jsp-007">
                    <i class="fa-solid fa-check-circle"></i> Xác Nhận Đăng Ký
                </button>
            </div>
        </form>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/add-team-popup.js"></script>
