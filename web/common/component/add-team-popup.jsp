<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%
    Series seriesObj = (Series) request.getAttribute("series");
    String seriesIdParam = request.getParameter("seriesId");
    if (seriesIdParam == null || seriesIdParam.trim().isEmpty()) {
        seriesIdParam = (seriesObj != null && seriesObj.getId() != null) ? seriesObj.getId() : "";
    }
%>
<c:choose>
    <c:when test="${not empty leagueSeries}">
        <c:url var="addTeamAction" value="/league/season/teams">
            <c:param name="id" value="${leagueSeries.id}" />
        </c:url>
    </c:when>
    <c:otherwise><c:url var="addTeamAction" value="/rolling/team-list" /></c:otherwise>
</c:choose>

<link rel="stylesheet" href="${pageContext.request.contextPath}/css/add-team-popup.css?v=2">

<!-- REUSABLE POPUP COMPONENT: THÊM ĐỘI BÓNG HÀNG LOẠT (BULK ADD TEAMS) -->
<div id="modalAddTeamPopup" class="add-team-modal-overlay" style="display: none;">
    <div class="add-team-modal-card">
        <div class="add-team-modal-header">
            <h3 class="add-team-modal-title">
                <i class="fa-solid fa-user-plus text-mint" aria-hidden="true"></i> ${empty leagueSeries ? 'Đăng Ký Đội Bóng Partner' : 'Thêm đội tham gia'}
            </h3>
            <button type="button" class="add-team-modal-close" aria-label="Đóng" onclick="closeAddTeamPopup()">&times;</button>
        </div>

        <form method="POST" action="<c:out value='${addTeamAction}' />">
            <input type="hidden" name="action" value="${empty leagueSeries ? 'bulkAddPartners' : 'add'}">
            <c:if test="${empty leagueSeries}">
                <input type="hidden" name="seriesId" value="<%= seriesIdParam %>">
            </c:if>

            <div data-ui-style="add-team-popup-jsp-001">
                <label for="bulkAddTeamNames" data-ui-style="add-team-popup-jsp-002" style="display: block;">
                    ${empty leagueSeries ? 'Danh Sách Tên Đội Bóng (Mỗi đội 1 dòng)' : 'Tên đội (mỗi đội một dòng)'} <span data-ui-style="add-team-popup-jsp-003">*</span>
                </label>
                <textarea id="bulkAddTeamNames" name="bulkTeamNames" required class="add-team-textarea" placeholder="Nhập hoặc dán danh sách tên các đội bóng tại đây...&#10;Ví dụ:&#10;Saigon Heat&#10;Thang Long Warriors&#10;Hanoi Buffaloes&#10;Danang Dragons"></textarea>
                <c:if test="${empty leagueSeries}">
                <p data-ui-style="add-team-popup-jsp-004">
                    <i class="fa-solid fa-info-circle text-mint"></i> Mã Partner ID sẽ được tự động sinh ngẫu nhiên và điểm khởi đầu mặc định là 0.
                </p>
                </c:if>
            </div>

            <!-- MODAL FOOTER BUTTONS -->
            <div data-ui-style="add-team-popup-jsp-005">
                <button type="button" class="btn" onclick="closeAddTeamPopup()" data-ui-style="add-team-popup-jsp-006">
                    Hủy Bỏ
                </button>
                <button type="submit" class="btn btn-mint" data-ui-style="add-team-popup-jsp-007">
                    <i class="fa-solid fa-check-circle" aria-hidden="true"></i> ${empty leagueSeries ? 'Xác Nhận Đăng Ký' : 'Thêm đội'}
                </button>
            </div>
        </form>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/add-team-popup.js"></script>
