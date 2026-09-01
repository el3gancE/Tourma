<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="dao.TournamentDAO" %>
<%@ page import="model.Tournament" %>
<%
    String tournamentId = request.getParameter("id");
    if (tournamentId == null || tournamentId.trim().isEmpty()) {
        tournamentId = "demo";
    }

    TournamentDAO tDao = new TournamentDAO();
    Tournament tourney = tDao.getTournamentById(tournamentId);

    String tourneyName = (tourney != null && tourney.getName() != null) ? tourney.getName() : "Giải Đấu Vòng Bảng";
%>
<!DOCTYPE html>
<html lang="vi">
<head>
        <!-- Favicon -->
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <link rel="alternate icon" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Quản Lý Chia Bảng - <%= tourneyName %></title>

    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/manage-group.css">
</head>
<body style="background: #0b0d12; color: #f8fafc;">

    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="tournaments"/>
    </jsp:include>
    <jsp:include page="/common/component/sidebar.jsp">
        <jsp:param name="activeStep" value="manage-group" />
        <jsp:param name="id" value="<%= tournamentId %>" />
        <jsp:param name="format" value="GROUP_STAGE" />
    </jsp:include>

    <div class="mg-container has-sidebar">
        
        <!-- HEADER BAR -->
        <div class="mg-header">
            <div>
                <h1 class="mg-title">
                    <i class="fa-solid fa-pen-to-square"></i> Quản Lý & Điều Chỉnh Chia Bảng
                </h1>
                <p style="margin: 0.25rem 0 0 0; font-size: 0.8rem; color: #94a3b8;">
                    Bạn có thể chọn bảng đấu để di chuyển các đội bóng giữa các bảng trước khi thi đấu.
                </p>
            </div>

            <div style="display: flex; gap: 0.5rem; align-items: center;">
                <button type="button" class="btn-mg-add" onclick="TourmaManageGroup.addNewGroup()">
                    <i class="fa-solid fa-plus"></i> Thêm Bảng Mới
                </button>
                <button type="button" class="btn-mg-distribute" onclick="TourmaManageGroup.handleAutoDistribute()">
                    Ngẫu Nhiên
                </button>
                <button type="button" class="btn btn-mint" onclick="TourmaManageGroup.saveAndReturn()" style="font-size: 0.8rem; padding: 0.45rem 0.9rem;">
                    <i class="fa-solid fa-check"></i> Lưu & Vào Vòng Bảng
                </button>
            </div>
        </div>

        <!-- WORKSPACE GRID -->
        <div id="mgWorkspace" class="mg-workspace"></div>
    </div>

    <!-- MODAL POPUP: CHỌN ĐỘI VÀO BẢNG -->
    <div id="addTeamToGroupModalBackdrop" class="tourma-modal-backdrop" onclick="if(event.target === this) TourmaManageGroup.closeAddTeamModal();" style="display:none; position:fixed; top:0; left:0; right:0; bottom:0; background:rgba(0,0,0,0.75); z-index:9999; justify-content:center; align-items:center;">
        <div class="tourma-modal-card" style="background:#121620; border:1px solid rgba(45,212,191,0.3); border-radius:12px; max-width:480px; width:90%; padding:1.25rem; box-shadow:0 8px 32px rgba(0,0,0,0.5);" onclick="event.stopPropagation();">
            <div style="display:flex; justify-content:space-between; align-items:center; border-bottom:1px solid rgba(255,255,255,0.1); padding-bottom:0.75rem; margin-bottom:1rem;">
                <div style="font-weight:800; font-size:1rem; color:#2dd4bf; display:flex; align-items:center; gap:0.5rem;">
                    <i class="fa-solid fa-user-plus"></i> <span id="modalGroupTitleDisplay">Thêm Đội Vào Bảng</span>
                </div>
                <button type="button" onclick="TourmaManageGroup.closeAddTeamModal()" style="background:transparent; border:none; color:#94a3b8; font-size:1.2rem; cursor:pointer;">
                    <i class="fa-solid fa-xmark"></i>
                </button>
            </div>
            
            <div style="max-height:280px; overflow-y:auto; padding-right:0.25rem; margin-bottom:1.25rem;" id="modalTeamChecklistContainer">
                <!-- Checklist items injected via JS -->
            </div>

            <div style="display:flex; justify-content:space-between; align-items:center; border-top:1px solid rgba(255,255,255,0.1); padding-top:0.85rem;">
                <div style="font-size:0.78rem; color:#94a3b8;">
                    Đã chọn: <strong id="modalSelectedCountDisplay" style="color:#2dd4bf;">0</strong> đội
                </div>
                <div style="display:flex; gap:0.5rem;">
                    <button type="button" class="btn btn-secondary" onclick="TourmaManageGroup.closeAddTeamModal()" style="font-size:0.8rem; padding:0.45rem 0.9rem;">Hủy Bỏ</button>
                    <button type="button" class="btn btn-mint" onclick="TourmaManageGroup.confirmAddTeamsToGroup()" style="font-size:0.8rem; padding:0.45rem 1rem;">
                        <i class="fa-solid fa-check"></i> Xác Nhận Thêm
                    </button>
                </div>
            </div>
        </div>
    </div>

    <script src="${pageContext.request.contextPath}/js/manage-group.js"></script>
</body>
</html>
