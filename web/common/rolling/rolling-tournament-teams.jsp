<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, model.Team, model.PartnerParticipant, model.SeriesStanding, service.RollingWindowPointService.RollingStandingDTO, java.util.List, java.util.ArrayList, java.util.Map, java.util.HashMap"%>
<%!
    private String partnerAttr(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }
%>
<%
    Map<String, List<Team>> seriesTournamentTeams = (Map<String, List<Team>>) request.getAttribute("seriesTournamentTeams");
    if (seriesTournamentTeams == null) seriesTournamentTeams = java.util.Collections.emptyMap();
    Tournament tournament = (Tournament) request.getAttribute("tournament");
    Series series = (Series) request.getAttribute("series");
    List<Team> currentTeams = (List<Team>) request.getAttribute("currentTeams");
    List<PartnerParticipant> partnerList = (List<PartnerParticipant>) request.getAttribute("partnerList");
    List<SeriesStanding> standingsList = (List<SeriesStanding>) request.getAttribute("standingsList");
    List<RollingStandingDTO> standingsDTOList = (List<RollingStandingDTO>) request.getAttribute("standingsDTOList");
    List<Tournament> tournamentsList = (List<Tournament>) request.getAttribute("tournamentsList");
    if (tournamentsList != null && tournamentsList.size() > 1) {
        tournamentsList = new java.util.ArrayList<>(tournamentsList);
        tournamentsList.sort((a, b) -> {
            int idxA = a.getTournamentIndexInSeries();
            int idxB = b.getTournamentIndexInSeries();
            if (idxA != idxB) return Integer.compare(idxA, idxB);
            if (a.getCreatedAt() != null && b.getCreatedAt() != null) return a.getCreatedAt().compareTo(b.getCreatedAt());
            return 0;
        });
    }

    String tourneyId = (tournament != null) ? tournament.getId() : "";
    String tourneyName = (tournament != null) ? tournament.getName() : "Giải Đấu Con";
    String seriesIdVal = (series != null && series.getId() != null) ? series.getId() : "";
    String seriesName = (series != null) ? series.getName() : "Series";
    String tourneyFormat = (tournament != null && tournament.getFormat() != null) ? tournament.getFormat().toUpperCase() : "SINGLE_ELIMINATION";
    int teamCount = (currentTeams != null) ? currentTeams.size() : 0;
    int partnerCount = (partnerList != null) ? partnerList.size() : 0;
    int phaseSize = (series != null && series.getPhaseSize() > 0) ? series.getPhaseSize() : 3;

    String targetTeamCountParam = request.getParameter("targetTeamCount");
    String copiedSourceTourneyNameParam = request.getParameter("copiedSourceTourneyName");
    int targetTeamCountVal = 0;
    if (targetTeamCountParam != null && !targetTeamCountParam.trim().isEmpty()) {
        try { targetTeamCountVal = Integer.parseInt(targetTeamCountParam.trim()); } catch (Exception ignore) {}
    }

    String nextStepUrl = request.getContextPath() + "/rolling/point-config?id=" + tourneyId + "&seriesId=" + seriesIdVal;
    if ("GSL".equals(tourneyFormat) || "GROUP_STAGE".equals(tourneyFormat)) {
        nextStepUrl = request.getContextPath() + "/common/manage-group.jsp?id=" + tourneyId
                + "&seriesId=" + seriesIdVal + "&format=" + tourneyFormat;
    }
    if (targetTeamCountVal > 0) {
        nextStepUrl += "&targetTeamCount=" + targetTeamCountVal;
    }
    if (copiedSourceTourneyNameParam != null && !copiedSourceTourneyNameParam.trim().isEmpty()) {
        nextStepUrl += "&copiedSourceTourneyName=" + java.net.URLEncoder.encode(copiedSourceTourneyNameParam, "UTF-8");
    }
    String nextStepLabel = "Tiếp theo";

    // Order partnerList by Series Rolling Standings rank (Rank 1, 2, 3...)
    List<PartnerParticipant> orderedPartners = new ArrayList<>();
    Map<String, Integer> partnerRankMap = new HashMap<>();
    Map<String, Integer> partnerPointsMap = new HashMap<>();
    java.util.Set<String> addedPartnerIds = new java.util.HashSet<>();

    if (standingsDTOList != null && !standingsDTOList.isEmpty()) {
        for (RollingStandingDTO dto : standingsDTOList) {
            String tName = dto.getTeamName();
            int r = dto.getRank();
            int pts = dto.getTotalActivePoints();
            if (tName != null) {
                String key = tName.trim().toLowerCase();
                partnerRankMap.put(key, r);
                partnerPointsMap.put(key, pts);
                if (partnerList != null) {
                    for (PartnerParticipant p : partnerList) {
                        if (p.getId() != null && !addedPartnerIds.contains(p.getId())) {
                            if ((dto.getPartnerParticipantId() != null && dto.getPartnerParticipantId().equals(p.getId()))
                                    || (p.getName() != null && p.getName().trim().equalsIgnoreCase(tName.trim()))) {
                                orderedPartners.add(p);
                                addedPartnerIds.add(p.getId());
                                break;
                            }
                        }
                    }
                }
            }
        }
    } else if (standingsList != null && !standingsList.isEmpty()) {
        for (int r = 0; r < standingsList.size(); r++) {
            SeriesStanding st = standingsList.get(r);
            if (st.getNormalizedTeamName() != null) {
                String key = st.getNormalizedTeamName().trim().toLowerCase();
                partnerRankMap.put(key, r + 1);
                partnerPointsMap.put(key, st.getTotalRollingPoints());
                if (partnerList != null) {
                    for (PartnerParticipant p : partnerList) {
                        if (p.getId() != null && !addedPartnerIds.contains(p.getId())) {
                            if (p.getName() != null && p.getName().trim().equalsIgnoreCase(st.getNormalizedTeamName().trim())) {
                                orderedPartners.add(p);
                                addedPartnerIds.add(p.getId());
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    if (partnerList != null) {
        for (PartnerParticipant p : partnerList) {
            if (p.getId() != null && !addedPartnerIds.contains(p.getId())) {
                orderedPartners.add(p);
                addedPartnerIds.add(p.getId());
                String key = (p.getName() != null) ? p.getName().trim().toLowerCase() : "";
                if (!partnerRankMap.containsKey(key)) {
                    partnerRankMap.put(key, orderedPartners.size());
                    partnerPointsMap.put(key, 0);
                }
            }
        }
    }
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title><%= tourneyName %> - Quản Lý Danh Sách Đội - TOURMA</title>

        <!-- Google Fonts & FontAwesome -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

        <!-- Design System CSS, Standalone Configure Teams CSS & Dedicated Teams CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/create-tournament.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/configure-tournament-teams.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-tournament-teams.css?v=20261024-partner-columns">
    <script src="${pageContext.request.contextPath}/js/theme.js?v=20261031-classic"></script>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=20261031m">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=18">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/workspace.css?v=21">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-workspace.css?v=18">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css?v=6">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/configure-tournament-teams.css?v=22">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-tournament-teams.css?v=2">
</head>
    <body class="ui-page workspace-page tournament-workspace series-page series-form-page subteams-page teams-page">
        <!-- Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-series"/>
        <jsp:param name="themeToggle" value="true"/></jsp:include>

        <!-- Dynamic 3-Mode Sidebar Component -->
        <div class="tournament-shell"><details class="tournament-navigation" open><summary>Điều hướng chuỗi giải</summary><jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="id" value="<%= tourneyId %>"/>
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
            <jsp:param name="activeStep" value="teams"/>
        </jsp:include></details>

        <main class="series-main">

            <!-- CENTERED MAIN TITLE (MATCHING STANDALONE TOURNAMENT 100%) -->
            <h1 class="page-main-title" data-ui-style="rolling-tournament-teams-jsp-001">
                Quản lý danh sách đội
            </h1>

            <% if (targetTeamCountVal > 0) { %>
            <!-- TARGET TEAM COUNT CONSTRAINT BANNER (COPIED CONFIG) -->
            <div id="targetTeamConstraintBanner" data-ui-style="rolling-tournament-teams-jsp-002" style="display: flex;">
                <div data-ui-style="rolling-tournament-teams-jsp-003" style="display: flex;">
                    <div data-ui-style="rolling-tournament-teams-jsp-004" style="display: flex;">
                        <i class="fa-solid fa-copy"></i>
                    </div>
                    <div>
                        <div data-ui-style="rolling-tournament-teams-jsp-005" style="display: flex;">
                            <span>Cấu hình sao chép từ:</span>
                            <span data-ui-style="rolling-tournament-teams-jsp-006"><%= (copiedSourceTourneyNameParam != null && !copiedSourceTourneyNameParam.trim().isEmpty()) ? copiedSourceTourneyNameParam : "Giải Gốc" %></span>
                        </div>
                        <div data-ui-style="rolling-tournament-teams-jsp-007">
                            Ràng buộc số lượng: Giải đấu mới bắt buộc phải có đúng <strong><%= targetTeamCountVal %> đội</strong> tham gia.
                        </div>
                    </div>
                </div>
                <div data-ui-style="rolling-tournament-teams-jsp-008" style="display: flex;">
                    <div id="targetCountStatusBadge" data-ui-style="rolling-tournament-teams-jsp-009" style="display: inline-flex; background: <%= (teamCount == targetTeamCountVal) ? "rgba(34, 197, 94, 0.15)" : "rgba(239, 68, 68, 0.15)" %>; color: <%= (teamCount == targetTeamCountVal) ? "#4ade80" : "#f87171" %>; border: 1px solid <%= (teamCount == targetTeamCountVal) ? "rgba(34, 197, 94, 0.3)" : "rgba(239, 68, 68, 0.3)" %>;">
                        <i class="fa-solid <%= (teamCount == targetTeamCountVal) ? "fa-circle-check" : "fa-triangle-exclamation" %>"></i>
                        <span><%= (teamCount == targetTeamCountVal) ? ("✓ Đủ " + teamCount + "/" + targetTeamCountVal + " đội") : (teamCount < targetTeamCountVal ? ("Thiếu " + (targetTeamCountVal - teamCount) + " đội (" + teamCount + "/" + targetTeamCountVal + ")") : ("Thừa " + (teamCount - targetTeamCountVal) + " đội (" + teamCount + "/" + targetTeamCountVal + ")")) %></span>
                    </div>
                </div>
            </div>
            <% } %>

            <!-- TWO-COLUMN WORKSPACE GRID (MATCHING STANDALONE TOURNAMENT PERFECTLY) -->
            <div class="team-mgmt-grid">

                <!-- LEFT COLUMN: BULK IMPORT WORKSPACE & PARTNER SELECTION -->
                <div class="team-panel-card">
                    <div class="team-panel-header">
                        <div class="team-panel-title">
                            <i class="fa-solid fa-list-check text-mint"></i> Nhập danh sách đội
                        </div>
                        <div data-ui-style="rolling-tournament-teams-jsp-010" style="display: flex;">
                            <span class="count-badge" id="inputCountDisplay"><%= teamCount %> Đội</span>
                            <button type="button" onclick="openPartnerSelectModal()" class="btn btn-mint" data-ui-style="rolling-tournament-teams-jsp-011">
                                <i class="fa-solid fa-check-double"></i> Chọn Từ Partner (<%= partnerCount %>)
                            </button>
                        </div>
                    </div>

                    <form id="bulkAddForm" method="POST" action="${pageContext.request.contextPath}/rolling/tournament-teams" data-ui-style="rolling-tournament-teams-jsp-012" style="display: flex;">
                        <input type="hidden" name="action" value="bulkAdd">
                        <input type="hidden" name="tournamentId" value="<%= tourneyId %>">
                        <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">

                        <div class="bulk-input-container" data-ui-style="rolling-tournament-teams-jsp-013" style="display: flex;">
                            <textarea name="teamNamesText" id="teamTextarea" class="team-textarea-input" data-ui-style="rolling-tournament-teams-jsp-014"
                                      placeholder="Nhập hoặc dán danh sách đội (mỗi đội một dòng)...&#10;Ví dụ:&#10;Hà Nội FC&#10;Hải Phòng FC&#10;Saigon Heat"></textarea>
                        </div>

                        <div class="input-actions-bar" data-ui-style="rolling-tournament-teams-jsp-015" style="display: flex;">
                            <button type="button" onclick="document.getElementById('teamTextarea').value=''" class="btn btn-secondary" data-ui-style="rolling-tournament-teams-jsp-016">
                                <i class="fa-solid fa-rotate-left"></i> Xóa Hết
                            </button>
                            <button type="submit" class="btn btn-mint" data-ui-style="rolling-tournament-teams-jsp-017">
                                <i class="fa-solid fa-plus"></i> Thêm Vào Danh Sách
                            </button>
                        </div>
                    </form>
                </div>

                <!-- RIGHT COLUMN: LIVE SEED & TEAM LIST MANAGEMENT -->
                <div class="team-panel-card">
                    <div class="team-panel-header">
                        <div class="team-panel-title">
                            <i class="fa-solid fa-ranking-star text-gold"></i> Quản lý hạt giống & phân cặp
                        </div>
                        <span class="count-badge" id="managedCountDisplay"><%= teamCount %> Đội</span>
                    </div>

                    <div class="manage-toolbar">
                        <div class="manage-toolbar-left" data-ui-style="rolling-tournament-teams-jsp-018" style="display: flex;">
                            <span data-ui-style="rolling-tournament-teams-jsp-019">Giữ top:</span>
                            <input type="number" id="lockTopSeedsInput" min="0" max="999" placeholder="0"
                                   data-ui-style="rolling-tournament-teams-jsp-020"
                                   onfocus="this.select()" onchange="window.saveHideSeedConfig()" oninput="window.saveHideSeedConfig()">
                            <button type="button" class="btn btn-secondary" data-ui-style="rolling-tournament-teams-jsp-021" onclick="shuffleSubtourneyTeams()">
                                Xáo trộn
                            </button>
                            <button type="button" id="btnToggleHideSeed" class="btn btn-secondary" onclick="window.toggleHideSeedMode()"
                                    data-ui-style="rolling-tournament-teams-jsp-022">
                                Ẩn seed
                            </button>
                                        <% if ("SINGLE_ELIMINATION".equalsIgnoreCase(tourneyFormat) || "DOUBLE_ELIMINATION".equalsIgnoreCase(tourneyFormat)) { %>
                                        <label data-ui-style="rolling-tournament-teams-jsp-023" style="display:inline-flex;" title="Giữ sơ đồ seed cố định; bỏ chọn để bốc thăm theo nhóm seed">
                                            <input type="checkbox" id="classicDrawInput" <%= new dao.TournamentDAO().isClassicDraw(tourneyId) ? "checked" : "" %> onchange="window.saveHideSeedConfig()">
                                            Classic draw
                                        </label>
                                        <% } %>
                            <button type="button" class="btn btn-secondary" onclick="window.sortByRollingStandings()"
                                    data-ui-style="rolling-tournament-teams-jsp-024"
                                    title="Sắp xếp danh sách đội tham gia theo Bảng Xếp Hạng tổng sau giải gần nhất">
                                <i class="fa-solid fa-arrow-down-wide-short"></i> Xếp theo BXH
                            </button>
                        </div>
                        <div class="manage-toolbar-right" data-ui-style="rolling-tournament-teams-jsp-025" style="display: flex;">
                            <input type="text" class="form-control" placeholder="Tìm kiếm tên đội..." onkeyup="filterTournamentTeams(this.value)" data-ui-style="rolling-tournament-teams-jsp-026">
                            <button type="button" class="btn btn-secondary" data-ui-style="rolling-tournament-teams-jsp-027" onclick="deleteSelectedSubtourneyTeams()">
                                Xóa đã chọn
                            </button>
                        </div>
                    </div>

                    <!-- BULK REMOVE TEAMS FORM ENCLOSURE -->
                    <form id="bulkRemoveTeamsForm" method="POST" action="${pageContext.request.contextPath}/rolling/tournament-teams" data-ui-style="rolling-tournament-teams-jsp-028" style="display: flex;">
                        <input type="hidden" name="action" value="bulkRemoveTeams">
                        <input type="hidden" name="tournamentId" value="<%= tourneyId %>">
                        <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">

                        <!-- SEEDING TABLE -->
                        <div class="table-container" data-ui-style="rolling-tournament-teams-jsp-029">
                            <div class="table-scroll-wrapper">
                                <table class="table" id="subtourneyTeamsTable">
                                    <thead>
                                        <tr>
                                            <th data-ui-style="rolling-tournament-teams-jsp-030">NO.</th>
                                            <th>TÊN ĐỘI</th>
                                            <th data-ui-style="rolling-tournament-teams-jsp-031">KÉO</th>
                                            <th data-ui-style="rolling-tournament-teams-jsp-032">XÓA</th>
                                            <th data-ui-style="rolling-tournament-teams-jsp-033">
                                                <input type="checkbox" id="selectAllSubtourneyCb" onchange="toggleSelectAllSubtourney(this)" title="Chọn tất cả">
                                            </th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <% if (currentTeams != null && !currentTeams.isEmpty()) {
                                            for (int idx = 0; idx < currentTeams.size(); idx++) {
                                                Team t = currentTeams.get(idx);
                                        %>
                                            <tr class="team-table-row" draggable="true" data-index="<%= idx %>">
                                                <td data-ui-style="rolling-tournament-teams-jsp-034">
                                                    <span class="seed-badge"><%= idx + 1 %></span>
                                                </td>
                                                <td>
                                                    <span data-ui-style="rolling-tournament-teams-jsp-035"><%= t.getRawName() %></span>
                                                </td>
                                                <td data-ui-style="rolling-tournament-teams-jsp-036">
                                                    <div class="drag-handle" title="Kéo để đổi hạt giống">
                                                        <i class="fa-solid fa-grip-vertical"></i>
                                                    </div>
                                                </td>
                                                <td data-ui-style="rolling-tournament-teams-jsp-037">
                                                    <button type="submit" name="deleteTeamIds" value="<%= t.getId() %>" class="btn-delete-row" title="Xóa đội" onclick="return confirm('Xóa đội này khỏi giải con?');">
                                                        <i class="fa-solid fa-xmark"></i>
                                                    </button>
                                                </td>
                                                <td data-ui-style="rolling-tournament-teams-jsp-038">
                                                    <input type="checkbox" name="deleteTeamIds" class="team-item-cb team-select-cb" value="<%= t.getId() %>">
                                                </td>
                                            </tr>
                                        <% }
                                        } else { %>
                                            <tr>
                                                <td colspan="5" data-ui-style="rolling-tournament-teams-jsp-039">
                                                    <i class="fa-solid fa-folder-open" data-ui-style="rolling-tournament-teams-jsp-040" style="display: block;"></i>
                                                    Chưa có đội nào. Hãy nhập ở bảng bên trái hoặc chọn từ Partner!
                                                </td>
                                            </tr>
                                        <% } %>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </form>

                    <div data-ui-style="rolling-tournament-teams-jsp-041">
                        <i class="fa-solid fa-hand-pointer text-mint"></i> Kéo thả từng hàng để thay đổi thứ tự Hạt Giống
                    </div>
                </div>

            </div>

            <!-- STEP FOOTER NAVIGATION BAR -->
            <div data-ui-style="rolling-tournament-teams-jsp-042" style="display: flex;">
                <a href="${pageContext.request.contextPath}/common/configure-tournament-format.jsp?id=<%= tourneyId %>&seriesId=<%= seriesIdVal %>" class="btn btn-secondary" data-ui-style="rolling-tournament-teams-jsp-043">
                    <i class="fa-solid fa-arrow-left"></i> Quay lại Bước 2: Thể Thức
                </a>
                <button type="button" id="nextStepBtn" onclick="window.proceedToNextStep(event)" class="btn btn-mint" data-ui-style="rolling-tournament-teams-jsp-044">
                    Tiếp theo <i class="fa-solid fa-arrow-right" data-ui-style="rolling-tournament-teams-jsp-045"></i>
                </button>
            </div>

            <form id="saveOrderAndProceedForm" method="POST" action="${pageContext.request.contextPath}/rolling/tournament-teams" style="display: none;">
                <input type="hidden" name="action" value="saveTeamsOrder">
                <input type="hidden" name="tournamentId" value="<%= tourneyId %>">
                <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">
                <input type="hidden" name="nextUrl" value="<%= nextStepUrl %>">
                <input type="hidden" name="orderedTeamNames" id="orderedTeamNamesInput" value="">
            </form>

        </main></div>

        <!-- MODAL: SELECT QUICKLY FROM PARTNER TEAMS ORDERED BY STANDINGS -->
        <div id="partnerSelectModal" class="team-modal-overlay" style="display: none;">
            <div class="team-modal-card partner-select-card">
                <div data-ui-style="rolling-tournament-teams-jsp-046" style="display: flex;">
                    <h3 data-ui-style="rolling-tournament-teams-jsp-047">
                        <i class="fa-solid fa-check-double text-mint"></i> Chọn Đội Partner Trong Series
                    </h3>
                    <button type="button" onclick="closePartnerSelectModal()" data-ui-style="rolling-tournament-teams-jsp-048">
                        <i class="fa-solid fa-xmark"></i>
                    </button>
                </div>

                <p data-ui-style="rolling-tournament-teams-jsp-049">
                    Tích chọn các đội Partner chính thức của Series <strong><%= seriesName %></strong> để đưa vào giải con này:
                </p>

                <form id="partnerSelectForm" method="POST" action="${pageContext.request.contextPath}/rolling/tournament-teams">
                    <input type="hidden" name="action" value="addPartnerTeams">
                    <input type="hidden" name="tournamentId" value="<%= tourneyId %>">
                    <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">

                <div class="partner-selection-columns">
                <section class="partner-selection-teams" aria-labelledby="partnerSelectionTitle">
                    <h4 id="partnerSelectionTitle">Chọn đội Partner</h4>
                <!-- QUICK SELECTION TOOLBAR -->
                <div data-ui-style="rolling-tournament-teams-jsp-050">
                    <div data-ui-style="rolling-tournament-teams-jsp-051" style="display: flex;">
                        <div data-ui-style="rolling-tournament-teams-jsp-052" style="display: flex;">
                            <span data-ui-style="rolling-tournament-teams-jsp-053">Số đội chọn:</span>
                            <input type="number" id="quickSelectNum" value="<%= (targetTeamCountVal > 0) ? Math.min(partnerCount, targetTeamCountVal) : Math.min(partnerCount, 4) %>" min="2" max="<%= partnerCount %>" class="form-control" data-ui-style="rolling-tournament-teams-jsp-054">
                        </div>
                        <div data-ui-style="rolling-tournament-teams-jsp-055" style="display: flex;">
                            <button type="button" onclick="quickSelectTeams('TOP')" class="btn btn-mint" data-ui-style="rolling-tournament-teams-jsp-056">
                                <i class="fa-solid fa-arrow-trend-up"></i> Top Đầu
                            </button>
                            <button type="button" onclick="quickSelectTeams('BOTTOM')" class="btn" data-ui-style="rolling-tournament-teams-jsp-057">
                                <i class="fa-solid fa-arrow-trend-down"></i> Top Cuối
                            </button>
                            <button type="button" onclick="quickSelectTeams('RANDOM')" class="btn" data-ui-style="rolling-tournament-teams-jsp-058">
                                <i class="fa-solid fa-shuffle"></i> Ngẫu Nhiên
                            </button>
                        </div>
                    </div>
                    <div data-ui-style="rolling-tournament-teams-jsp-059" style="display: flex;">
                        <span>Chọn theo BXH hoặc tích thủ công (Tổng: <%= partnerCount %> đội)</span>
                        <div>
                            <button type="button" onclick="selectAllPartnerCheckboxes(true)" data-ui-style="rolling-tournament-teams-jsp-060">Chọn tất cả</button>
                            <button type="button" onclick="selectAllPartnerCheckboxes(false)" data-ui-style="rolling-tournament-teams-jsp-061">Bỏ chọn</button>
                        </div>
                    </div>
                </div>

                    <!-- SINGLE COLUMN VERTICAL LIST - ORDERED BY ROLLING STANDINGS -->
                    <div class="partner-checkbox-list" id="partnerModalCheckboxList">
                        <% if (orderedPartners != null && !orderedPartners.isEmpty()) {
                            for (int pIdx = 0; pIdx < orderedPartners.size(); pIdx++) {
                                PartnerParticipant p = orderedPartners.get(pIdx);
                                String pKey = (p.getName() != null) ? p.getName().trim().toLowerCase() : "";
                                int pRank = partnerRankMap.containsKey(pKey) ? partnerRankMap.get(pKey) : (pIdx + 1);
                                int pPts = partnerPointsMap.containsKey(pKey) ? partnerPointsMap.get(pKey) : 0;

                                List<String> playedTournaments = new ArrayList<>();
                                for (Map.Entry<String, List<Team>> played : seriesTournamentTeams.entrySet()) {
                                    for (Team member : played.getValue()) {
                                        if (member.getRawName() != null && member.getRawName().trim().equalsIgnoreCase(p.getName().trim())) {
                                            playedTournaments.add(played.getKey());
                                            break;
                                        }
                                    }
                                }
                                boolean isAlreadyIn = false;
                                if (currentTeams != null) {
                                    for (Team ct : currentTeams) {
                                        if (ct.getRawName() != null && ct.getRawName().equalsIgnoreCase(p.getName())) {
                                            isAlreadyIn = true;
                                            break;
                                        }
                                    }
                                }
                        %>
                            <label class="partner-checkbox-item" data-played-tournaments="<%= partnerAttr(String.join(" ", playedTournaments)) %>" data-already-in="<%= isAlreadyIn %>" data-index="<%= pIdx %>" data-team-name="<%= p.getName() != null ? p.getName() : "" %>" data-rank="<%= pRank %>" data-points="<%= pPts %>" data-ui-style="rolling-tournament-teams-jsp-062" style="display: flex;">
                                <div data-ui-style="rolling-tournament-teams-jsp-063" style="display: flex;">
                                    <input type="checkbox" name="selectedTeamNames" value="<%= p.getName() %>" class="partner-cb-input" data-index="<%= pIdx %>" <%= isAlreadyIn ? "checked disabled" : "" %>>
                                    <span class="rank-badge rank-<%= pRank %>" data-ui-style="rolling-tournament-teams-jsp-064" style="display: inline-flex; color: <%= (pRank == 1) ? "#fbbf24" : (pRank == 2 ? "#e2e8f0" : (pRank == 3 ? "#f97316" : "#cbd5e1")) %>; border: 1px solid <%= (pRank == 1) ? "rgba(251,191,36,0.3)" : (pRank == 2 ? "rgba(226,232,240,0.2)" : (pRank == 3 ? "rgba(249,115,22,0.3)" : "rgba(255,255,255,0.1)")) %>;">#<%= pRank %></span>
                                    <span data-ui-style="rolling-tournament-teams-jsp-065" style="color: <%= isAlreadyIn ? "var(--ui-muted)" : "var(--ui-text)" %>;">
                                        <%= p.getName() %> <%= isAlreadyIn ? "<span style='font-size: 0.75rem; font-weight: 500; color: var(--ui-muted); margin-left: 0.35rem;'>(Đã thêm)</span>" : "" %>
                                    </span>
                                    <small class="partner-excluded-label" hidden>Đã tham gia giải loại trừ</small>
                                </div>
                                <div data-ui-style="rolling-tournament-teams-jsp-066">
                                    <%= pPts %> pts
                                </div>
                            </label>
                        <% }
                        } else { %>
                            <div data-ui-style="rolling-tournament-teams-jsp-067">
                                Chưa có đội Partner nào trong Series.
                            </div>
                        <% } %>
                    </div>

                </section>
                <aside class="partner-exclusion-filter" aria-labelledby="partnerExclusionTitle">
                    <h4 id="partnerExclusionTitle">Không chọn đội từ… <span id="partnerExclusionCount">0 giải</span></h4>
                    <p>Các đội có mặt trong bất kỳ giải nào được chọn sẽ bị khóa chọn.</p>
                    <div class="partner-exclusion-tournaments">
                        <% int exclusionOptions = 0;
                           if (tournamentsList != null) for (int exclusionIndex = tournamentsList.size() - 1; exclusionIndex >= 0; exclusionIndex--) {
                               Tournament other = tournamentsList.get(exclusionIndex);
                               if (other == null || tourneyId.equals(other.getId()) || !seriesIdVal.equals(other.getSeriesId())) continue;
                               exclusionOptions++;
                        %>
                        <label>
                            <input type="checkbox" class="partner-exclusion-input" name="excludedTournamentIds"
                                   form="partnerSelectForm" value="<%= partnerAttr(other.getId()) %>">
                            <span><%= partnerAttr(other.getName()) %></span>
                            <small>#<%= other.getTournamentIndexInSeries() %></small>
                        </label>
                        <% } if (exclusionOptions == 0) { %>
                        <p>Chưa có giải con khác trong Series.</p>
                        <% } %>
                    </div>
                <div id="partnerExclusionHint" class="partner-exclusion-hint" aria-live="polite"></div>
                </aside>

                </div>
                    <div class="partner-selection-footer">
                        <button type="button" onclick="closePartnerSelectModal()" class="btn btn-secondary">Hủy Bỏ</button>
                        <button type="submit" class="btn btn-mint" data-ui-style="rolling-tournament-teams-jsp-068">
                            Thêm Đội Đã Chọn VÀO Giải Con
                        </button>
                    </div>
                </form>
            </div>
        </div>

        <script>
            window.currentSubTourneyId = "<%= tourneyId %>";
            window.seriesIdVal = "<%= seriesIdVal %>";
            window.appContextPath = "${pageContext.request.contextPath}";
            window.targetTeamCount = <%= targetTeamCountVal %>;
            window.copiedSourceTourneyName = "<%= (copiedSourceTourneyNameParam != null) ? copiedSourceTourneyNameParam.replace("\\", "\\\\").replace("\"", "\\\"") : "" %>";
            window.seriesSubTournaments = [
                <% if (tournamentsList != null) {
                    for (int i = 0; i < tournamentsList.size(); i++) {
                        Tournament t = tournamentsList.get(i);
                        String rawCfg = (t != null) ? t.getSeriesPointsConfig() : null;
                        String cfgJson = "{}";
                        if (rawCfg != null && rawCfg.trim().startsWith("{") && rawCfg.trim().endsWith("}")) {
                            cfgJson = rawCfg.trim();
                        }
                        String safeName = (t != null && t.getName() != null) ? t.getName().replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", " ").replace("\r", "") : "";
                        String safeId = (t != null && t.getId() != null) ? t.getId() : "";
                        int tIdx = (t != null && t.getTournamentIndexInSeries() > 0) ? t.getTournamentIndexInSeries() : (i + 1);
                        String tType = (t != null && t.getTournamentType() != null) ? t.getTournamentType() : "SINGLE_STAGE";
                        boolean isMulti = (t != null && "MULTI_STAGE".equalsIgnoreCase(t.getTournamentType()));
                        String fmt = (t != null && t.getFormat() != null) ? t.getFormat().toUpperCase() : "SINGLE_ELIMINATION";
                        String tTier = (t != null && t.getTierName() != null) ? t.getTierName().toUpperCase() : "A";
                %>
                    {
                        id: "<%= safeId %>",
                        name: "<%= safeName %>",
                        index: <%= tIdx %>,
                        format: "<%= fmt %>",
                        tournamentType: "<%= tType %>",
                        isMultiStage: <%= isMulti %>,
                        tierName: "<%= tTier %>",
                        pointsConfig: <%= cfgJson %>
                    }<%= (i < tournamentsList.size() - 1) ? "," : "" %>
                <%  }
                } %>
            ];
            window.seriesPartners = [
                <% if (partnerList != null) {
                    for (int i = 0; i < partnerList.size(); i++) {
                        PartnerParticipant p = partnerList.get(i);
                        String pName = (p != null && p.getName() != null) ? p.getName().replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", " ").replace("\r", "") : "";
                        String pId = (p != null && p.getId() != null) ? p.getId() : "";
                %>
                    {
                        id: "<%= pId %>",
                        name: "<%= pName %>"
                    }<%= (i < partnerList.size() - 1) ? "," : "" %>
                <%  }
                } %>
            ];
            <%
                List<java.util.Map<String, Integer>> serverTourneyPoints = (series != null) ? service.RollingWindowPointService.getInstance().getTourneyPointsPerTournament(series.getId()) : null;
                List<java.util.Map<String, Boolean>> serverTourneyParticipation = (series != null) ? service.RollingWindowPointService.getInstance().getTourneyParticipationPerTournament(series.getId()) : null;
            %>
            window.serverTourneyPoints = [
                <% if (serverTourneyPoints != null) {
                    for (int sIdx = 0; sIdx < serverTourneyPoints.size(); sIdx++) {
                        java.util.Map<String, Integer> map = serverTourneyPoints.get(sIdx);
                        StringBuilder sb = new StringBuilder("{");
                        if (map != null) {
                            int count = 0;
                            for (java.util.Map.Entry<String, Integer> e : map.entrySet()) {
                                if (count++ > 0) sb.append(",");
                                sb.append("\"").append(e.getKey().replace("\\", "\\\\").replace("\"", "\\\"")).append("\":").append(e.getValue());
                            }
                        }
                        sb.append("}");
                %>
                    <%= sb.toString() %><%= (sIdx < serverTourneyPoints.size() - 1) ? "," : "" %>
                <%  }
                } %>
            ];
            window.serverTourneyParticipation = [
                <% if (serverTourneyParticipation != null) {
                    for (int sIdx = 0; sIdx < serverTourneyParticipation.size(); sIdx++) {
                        java.util.Map<String, Boolean> map = serverTourneyParticipation.get(sIdx);
                        StringBuilder sb = new StringBuilder("{");
                        if (map != null) {
                            int count = 0;
                            for (java.util.Map.Entry<String, Boolean> e : map.entrySet()) {
                                if (count++ > 0) sb.append(",");
                                sb.append("\"").append(e.getKey().replace("\\", "\\\\").replace("\"", "\\\"")).append("\":true");
                            }
                        }
                        sb.append("}");
                %>
                    <%= sb.toString() %><%= (sIdx < serverTourneyParticipation.size() - 1) ? "," : "" %>
                <%  }
                } %>
            ];
            window.seriesPhaseSize = <%= phaseSize %>;
            window.tourneyFormat = "<%= tourneyFormat %>";
            window.seriesStandingsRankMap = {
                <%
                int mapIdx = 0;
                for (Map.Entry<String, Integer> entry : partnerRankMap.entrySet()) {
                    int pts = partnerPointsMap.containsKey(entry.getKey()) ? partnerPointsMap.get(entry.getKey()) : 0;
                %>
                    "<%= entry.getKey().replace("\"", "\\\"") %>": { "rank": <%= entry.getValue() %>, "points": <%= pts %> }<%= (mapIdx < partnerRankMap.size() - 1) ? "," : "" %>
                <%
                    mapIdx++;
                }
                %>
            };
        </script>
        <!-- Engine Algorithms for Realtime Series Standings Sync -->
        <script src="${pageContext.request.contextPath}/js/round-robin-algorithm.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/bracket-algorithm.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/double-elimination-algorithm.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/rolling/rolling-standings-engine.js?v=<%= System.currentTimeMillis() %>"></script>
        <script src="${pageContext.request.contextPath}/js/rolling/rolling-tournament-teams.js?v=<%= System.currentTimeMillis() %>"></script>
    </body>
</html>
