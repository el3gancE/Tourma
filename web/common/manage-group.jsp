<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="dao.TournamentDAO" %>
<%@ page import="dao.ParticipantDAO" %>
<%@ page import="model.Tournament" %>
<%@ page import="model.Team" %>
<%@ page import="java.util.List" %>
<%
    String tournamentId = request.getParameter("id");
    if (tournamentId == null || tournamentId.trim().isEmpty()) {
        tournamentId = "demo";
    }
    String seriesId = request.getParameter("seriesId");
    if (seriesId == null) seriesId = "";

    TournamentDAO tDao = new TournamentDAO();
    Tournament tourney = tDao.getTournamentById(tournamentId);

    if (tourney != null && tourney.getSeriesId() != null) seriesId = tourney.getSeriesId();
    String tourneyName = (tourney != null && tourney.getName() != null) ? tourney.getName() : "Giải Đấu Vòng Bảng";
    String dbGroupAssignments = (tourney != null) ? tourney.getGroupAssignments() : null;
    String s1Format = tDao.getStage1Format(tournamentId);
    if ((s1Format == null || s1Format.trim().isEmpty()) && tourney != null) {
        s1Format = tourney.getFormat();
    }
    if (s1Format == null) s1Format = "";

    ParticipantDAO pDao = new ParticipantDAO();
    List<Team> dbTeams = pDao.getTeamsByTournamentId(tournamentId);
    StringBuilder teamsJsonBuilder = new StringBuilder("[");
    if (dbTeams != null) {
        for (int i = 0; i < dbTeams.size(); i++) {
            if (i > 0) teamsJsonBuilder.append(",");
            Team tm = dbTeams.get(i);
            String tName = (tm.getRawName() != null && !tm.getRawName().isEmpty()) ? tm.getRawName() : tm.getName();
            if (tName == null) tName = "Team " + (i + 1);
            int seedVal = tm.getOriginalSeed() > 0 ? tm.getOriginalSeed() : (i + 1);
            teamsJsonBuilder.append("{\"id\":\"").append(tm.getId() != null ? tm.getId() : String.valueOf(i + 1))
                            .append("\",\"name\":\"").append(tName.replace("\\", "\\\\").replace("\"", "\\\""))
                            .append("\",\"seed\":").append(seedVal)
                            .append("}");
        }
    }
    teamsJsonBuilder.append("]");
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
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/manage-group.css?v=<%= System.currentTimeMillis() %>">
<script src="${pageContext.request.contextPath}/js/theme.js?v=20261031-initial"></script>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=20261031m">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=18">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-workspace.css?v=18">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css?v=6">
</head>
<body class="ui-page tournament-workspace manage-group-page" data-ui-style="manage-group-jsp-001">

    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="tournaments"/><jsp:param name="themeToggle" value="true"/>
    </jsp:include>
    <div class="tournament-shell"><details class="tournament-navigation" open><summary>Điều hướng giải đấu</summary><jsp:include page="/common/component/sidebar.jsp">
        <jsp:param name="activeStep" value="manage-group" />
        <jsp:param name="id" value="<%= tournamentId %>" />
        <jsp:param name="seriesId" value="<%= seriesId %>" />
        <jsp:param name="format" value="<%= (request.getParameter(\"format\") != null && !request.getParameter(\"format\").trim().isEmpty()) ? request.getParameter(\"format\") : ((tourney != null && tourney.getFormat() != null) ? tourney.getFormat() : \"GROUP_STAGE\") %>" />
    </jsp:include></details>

    <main class="mg-container">

        <!-- HEADER BAR -->
        <div class="mg-header">
            <div>
                <h1 class="mg-title">
                    <i class="fa-solid fa-pen-to-square"></i> Quản Lý & Điều Chỉnh Chia Bảng
                </h1>
                <p data-ui-style="manage-group-jsp-002">
                    Bạn có thể chọn bảng đấu để di chuyển các đội bóng giữa các bảng trước khi thi đấu.
                </p>
            </div>

            <div data-ui-style="manage-group-jsp-003" style="display: flex;">
                <!-- Custom Quick Create Dropdown Menu -->
                <div class="mg-dropdown-container" id="quickGroupDropdownContainer">
                    <button type="button" class="btn-mg-quick-dropdown" id="btnQuickGroupDropdown" onclick="TourmaManageGroup.toggleQuickDropdown(event)" title="Tạo nhanh số lượng bảng đấu">
                        <i class="fa-solid fa-bolt text-teal"></i>
                        <span>Tạo nhanh số bảng</span>
                        <i class="fa-solid fa-chevron-down mg-dd-arrow" id="quickGroupDropdownArrow"></i>
                    </button>
                    <div class="mg-dropdown-menu" id="quickGroupDropdownMenu">
                        <div class="mg-dropdown-item" onclick="TourmaManageGroup.quickCreateGroups(2)">
                            <span class="mg-dd-count"><i class="fa-solid fa-layer-group text-teal"></i> 2 Bảng</span>
                            <span class="mg-dd-desc">Bảng A, B</span>
                        </div>
                        <div class="mg-dropdown-item" onclick="TourmaManageGroup.quickCreateGroups(4)">
                            <span class="mg-dd-count"><i class="fa-solid fa-layer-group text-teal"></i> 4 Bảng</span>
                            <span class="mg-dd-desc">Bảng A - D</span>
                        </div>
                        <div class="mg-dropdown-item" onclick="TourmaManageGroup.quickCreateGroups(6)">
                            <span class="mg-dd-count"><i class="fa-solid fa-layer-group text-teal"></i> 6 Bảng</span>
                            <span class="mg-dd-desc">Bảng A - F</span>
                        </div>
                        <div class="mg-dropdown-item" onclick="TourmaManageGroup.quickCreateGroups(8)">
                            <span class="mg-dd-count"><i class="fa-solid fa-layer-group text-teal"></i> 8 Bảng</span>
                            <span class="mg-dd-desc">Bảng A - H</span>
                        </div>
                        <div class="mg-dropdown-item" onclick="TourmaManageGroup.quickCreateGroups(12)">
                            <span class="mg-dd-count"><i class="fa-solid fa-layer-group text-teal"></i> 12 Bảng</span>
                            <span class="mg-dd-desc">Bảng A - L</span>
                        </div>
                        <div class="mg-dropdown-item" onclick="TourmaManageGroup.quickCreateGroups(16)">
                            <span class="mg-dd-count"><i class="fa-solid fa-layer-group text-teal"></i> 16 Bảng</span>
                            <span class="mg-dd-desc">Bảng A - P</span>
                        </div>
                        <div class="mg-dropdown-item" onclick="TourmaManageGroup.quickCreateGroups(24)">
                            <span class="mg-dd-count"><i class="fa-solid fa-layer-group text-teal"></i> 24 Bảng</span>
                            <span class="mg-dd-desc">Bảng A - X</span>
                        </div>
                        <div class="mg-dropdown-item" onclick="TourmaManageGroup.quickCreateGroups(32)">
                            <span class="mg-dd-count"><i class="fa-solid fa-layer-group text-teal"></i> 32 Bảng</span>
                            <span class="mg-dd-desc">Bảng A - AF</span>
                        </div>
                    </div>
                </div>
                <button type="button" class="btn-mg-add" onclick="TourmaManageGroup.addNewGroup()">
                    <i class="fa-solid fa-plus"></i> Thêm Bảng
                </button>
                <button type="button" class="btn-mg-distribute" onclick="TourmaManageGroup.handleAutoDistribute()">
                    Ngẫu Nhiên
                </button>
                <button type="button" class="btn btn-secondary" onclick="TourmaManageGroup.clearAllGroups()" data-ui-style="manage-group-jsp-004" title="Xóa tất cả các bảng và đưa toàn bộ đội về danh sách chưa xếp">
                    <i class="fa-solid fa-rotate-left"></i> Xóa Hết Bảng
                </button>
                <button type="button" class="btn btn-mint" onclick="TourmaManageGroup.saveAndReturn()" data-ui-style="manage-group-jsp-005">
                    Tiếp theo <i class="fa-solid fa-arrow-right" data-ui-style="manage-group-jsp-006"></i>
                </button>
            </div>
        </div>

        <!-- WORKSPACE GRID -->
        <div id="mgWorkspace" class="mg-workspace"></div>
    </main>

    </div>
    <!-- MODAL POPUP: CHỌN ĐỘI VÀO BẢNG -->
    <div id="addTeamToGroupModalBackdrop" class="tourma-modal-backdrop" onclick="if(event.target === this) TourmaManageGroup.closeAddTeamModal();" data-ui-style="manage-group-jsp-007" style="display:none;">
        <div class="tourma-modal-card" data-ui-style="manage-group-jsp-008" onclick="event.stopPropagation();">
            <div data-ui-style="manage-group-jsp-009" style="display:flex;">
                <div data-ui-style="manage-group-jsp-010" style="display:flex;">
                    <i class="fa-solid fa-user-plus"></i> <span id="modalGroupTitleDisplay">Thêm Đội Vào Bảng</span>
                </div>
                <button type="button" onclick="TourmaManageGroup.closeAddTeamModal()" data-ui-style="manage-group-jsp-011">
                    <i class="fa-solid fa-xmark"></i>
                </button>
            </div>

            <!-- Search and Quick Action Bar -->
            <div data-ui-style="manage-group-jsp-012" style="display:flex;">
                <div data-ui-style="manage-group-jsp-013">
                    <i class="fa-solid fa-magnifying-glass" data-ui-style="manage-group-jsp-014"></i>
                    <input type="text" id="modalTeamSearchInput" placeholder="Tìm kiếm tên đội..." oninput="TourmaManageGroup.filterModalTeams(this.value)" data-ui-style="manage-group-jsp-015">
                </div>
                <button type="button" class="btn btn-secondary" onclick="TourmaManageGroup.selectAllModalTeams(true)" data-ui-style="manage-group-jsp-016">Chọn Tất Cả</button>
                <button type="button" class="btn btn-secondary" onclick="TourmaManageGroup.selectAllModalTeams(false)" data-ui-style="manage-group-jsp-017">Bỏ Chọn</button>
            </div>

            <div data-ui-style="manage-group-jsp-018" id="modalTeamChecklistContainer">
                <!-- Checklist items injected via JS -->
            </div>

            <div data-ui-style="manage-group-jsp-019" style="display:flex;">
                <div data-ui-style="manage-group-jsp-020">
                    Đã chọn: <strong id="modalSelectedCountDisplay" data-ui-style="manage-group-jsp-021">0</strong> đội
                </div>
                <div data-ui-style="manage-group-jsp-022" style="display:flex;">
                    <button type="button" class="btn btn-secondary" onclick="TourmaManageGroup.closeAddTeamModal()" data-ui-style="manage-group-jsp-023">Hủy Bỏ</button>
                    <button type="button" class="btn btn-mint" onclick="TourmaManageGroup.confirmAddTeamsToGroup()" data-ui-style="manage-group-jsp-024">
                        <i class="fa-solid fa-check"></i> Xác Nhận Thêm
                    </button>
                </div>
            </div>
        </div>
    </div>

    <script>
        window.contextPath = '${pageContext.request.contextPath}';
        window.TOURNAMENT_SERIES_ID = '<%= seriesId %>';
        window.STAGE1_FORMAT = '<%= s1Format %>';
        window.DB_GROUP_ASSIGNMENTS = <%= (dbGroupAssignments != null && !dbGroupAssignments.trim().isEmpty() && !dbGroupAssignments.trim().equals("{}")) ? dbGroupAssignments : "null" %>;
        window.DB_TEAMS = <%= teamsJsonBuilder.toString() %>;
    </script>
    <script src="${pageContext.request.contextPath}/js/manage-group.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>
