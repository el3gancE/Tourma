<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, model.Team, java.util.List"%>
<%
    Tournament tournament = (Tournament) request.getAttribute("tournament");
    Series series = (Series) request.getAttribute("series");
    List<Team> currentTeams = (List<Team>) request.getAttribute("currentTeams");
    String tourneyId = (tournament != null) ? tournament.getId() : "";
    String tourneyName = (tournament != null) ? tournament.getName() : "Giải Đấu Con";
    String seriesIdVal = (series != null && series.getId() != null) ? series.getId() : "";
    String tourneyFormat = (tournament != null && tournament.getFormat() != null) ? tournament.getFormat().toUpperCase() : "SINGLE_ELIMINATION";
    int teamCount = (currentTeams != null) ? currentTeams.size() : 0;

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

%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title><%= tourneyName %> - Quản Lý Danh Sách Đội - TOURMA</title>

        <!-- Google Fonts & FontAwesome -->

        <!-- Design System CSS, Standalone Configure Teams CSS & Dedicated Teams CSS -->
    <script src="${pageContext.request.contextPath}/js/theme.js?v=20261101-settings"></script>
</head>
    <body class="ui-page workspace-page tournament-workspace series-page series-form-page subteams-page teams-page standalone-setup">
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
            <h1 class="page-main-title">
                Thể thức &amp; đội tham gia
            </h1>

            <jsp:include page="/common/component/tournament-config-state.jsp"/>
            <% request.setAttribute("standaloneFormatEmbedded", Boolean.TRUE); %>
            <jsp:include page="/common/configure-tournament-format.jsp"/>
            <% request.removeAttribute("standaloneFormatEmbedded"); %>

            <% if (targetTeamCountVal > 0) { %>
            <!-- TARGET TEAM COUNT CONSTRAINT BANNER (COPIED CONFIG) -->
            <div id="targetTeamConstraintBanner">
                <div>
                    <div>
                        <i class="fa-solid fa-copy"></i>
                    </div>
                    <div>
                        <div>
                            <span>Cấu hình sao chép từ:</span>
                            <span><%= (copiedSourceTourneyNameParam != null && !copiedSourceTourneyNameParam.trim().isEmpty()) ? copiedSourceTourneyNameParam : "Giải Gốc" %></span>
                        </div>
                        <div>
                            Ràng buộc số lượng: Giải đấu mới bắt buộc phải có đúng <strong><%= targetTeamCountVal %> đội</strong> tham gia.
                        </div>
                    </div>
                </div>
                <div>
                    <div id="targetCountStatusBadge">
                        <i class="fa-solid <%= (teamCount == targetTeamCountVal) ? "fa-circle-check" : "fa-triangle-exclamation" %>"></i>
                        <span><%= (teamCount == targetTeamCountVal) ? ("✓ Đủ " + teamCount + "/" + targetTeamCountVal + " đội") : (teamCount < targetTeamCountVal ? ("Thiếu " + (targetTeamCountVal - teamCount) + " đội (" + teamCount + "/" + targetTeamCountVal + ")") : ("Thừa " + (teamCount - targetTeamCountVal) + " đội (" + teamCount + "/" + targetTeamCountVal + ")")) %></span>
                    </div>
                </div>
            </div>
            <% } %>

            <!-- TWO-COLUMN WORKSPACE GRID (MATCHING STANDALONE TOURNAMENT PERFECTLY) -->
            <div class="team-mgmt-grid">

                <!-- LEFT COLUMN: BULK IMPORT WORKSPACE & PARTNER SELECTION -->
                <div class="team-panel-card team-import-panel">
                    <div class="team-panel-header">
                        <div class="team-panel-title">
                            <i class="fa-solid fa-list-check text-mint"></i> Nhập danh sách đội
                        </div>
                        <span class="count-badge" id="inputCountDisplay"><%= teamCount %> Đội</span>
                    </div>

                    <form id="bulkAddForm" method="POST" action="${pageContext.request.contextPath}/rolling/tournament-teams">
                        <input type="hidden" name="action" value="bulkAdd">
                        <input type="hidden" name="tournamentId" value="<%= tourneyId %>">
                        <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">

                        <div class="team-source-row">
                            <span class="team-input-label">Mỗi đội một dòng</span>
                            <button type="button" onclick="openPartnerSelectModal(this)" class="btn btn-secondary team-partner-action">
                                <i class="fa-solid fa-users" aria-hidden="true"></i> Chọn từ Partner
                            </button>
                        </div>
                        <div class="bulk-input-container">
                            <textarea name="teamNamesText" id="teamTextarea" class="team-textarea-input"
                                      aria-label="Danh sách đội, mỗi đội một dòng" placeholder="Hà Nội FC&#10;Hải Phòng FC&#10;Saigon Heat"></textarea>
                        </div>

                        <div class="input-actions-bar">
                            <button type="button" onclick="document.getElementById('teamTextarea').value=''" class="btn btn-secondary">
                                <i class="fa-solid fa-rotate-left"></i> Xóa ô nhập
                            </button>
                            <button type="submit" class="btn btn-mint">
                                <i class="fa-solid fa-plus"></i> Thêm đội
                            </button>
                        </div>
                    </form>
                </div>

                <!-- RIGHT COLUMN: LIVE SEED & TEAM LIST MANAGEMENT -->
                <div class="team-panel-card team-list-panel">
                    <div class="team-panel-header">
                        <div class="team-panel-title">
                            <i class="fa-solid fa-ranking-star text-gold"></i> Danh sách đội
                        </div>
                        <span class="count-badge" id="managedCountDisplay"><%= teamCount %> Đội</span>
                    </div>

                    <div class="manage-toolbar">
                        <div class="manage-toolbar-left">
                            <label for="lockTopSeedsInput">Giữ top</label>
                            <input type="number" id="lockTopSeedsInput" min="0" max="999"

                                   onfocus="this.select()" onchange="window.saveHideSeedConfig()" oninput="window.saveHideSeedConfig()">
                            <button type="button" class="btn btn-secondary" onclick="shuffleSubtourneyTeams()">
                                Xáo trộn
                            </button>
                            <button type="button" id="btnToggleHideSeed" class="btn btn-secondary" onclick="window.toggleHideSeedMode()"
>
                                Ẩn seed
                            </button>
                                        <% if ("SINGLE_ELIMINATION".equalsIgnoreCase(tourneyFormat) || "DOUBLE_ELIMINATION".equalsIgnoreCase(tourneyFormat)) { %>
                                        <label title="Giữ sơ đồ seed cố định; bỏ chọn để bốc thăm theo nhóm seed">
                                            <input type="checkbox" id="classicDrawInput" <%= new dao.TournamentDAO().isClassicDraw(tourneyId) ? "checked" : "" %> onchange="window.saveHideSeedConfig()">
                                            Classic draw
                                        </label>
                                        <% } %>
                            <button type="button" class="btn btn-secondary" onclick="window.sortByRollingStandings(this)"

                                    title="Sắp xếp danh sách đội tham gia theo Bảng Xếp Hạng tổng sau giải gần nhất">
                                <i class="fa-solid fa-arrow-down-wide-short"></i> Xếp theo BXH
                            </button>
                        </div>
                        <div class="manage-toolbar-right">
                            <input type="text" class="form-control" aria-label="Tìm đội" placeholder="Tìm đội..." onkeyup="filterTournamentTeams(this.value)">
                            <button type="button" class="btn btn-secondary" onclick="deleteSelectedSubtourneyTeams()">
                                Xóa đã chọn
                            </button>
                        </div>
                    </div>

                    <!-- BULK REMOVE TEAMS FORM ENCLOSURE -->
                    <form id="bulkRemoveTeamsForm" method="POST" action="${pageContext.request.contextPath}/rolling/tournament-teams">
                        <input type="hidden" name="action" value="bulkRemoveTeams">
                        <input type="hidden" name="tournamentId" value="<%= tourneyId %>">
                        <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">

                        <!-- SEEDING TABLE -->
                        <div class="table-container">
                            <div class="table-scroll-wrapper">
                                <table class="table" id="subtourneyTeamsTable">
                                    <thead>
                                        <tr>
                                            <th>NO.</th>
                                            <th>TÊN ĐỘI</th>
                                            <th>KÉO</th>
                                            <th>XÓA</th>
                                            <th>
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
                                                <td>
                                                    <span class="seed-badge"><%= idx + 1 %></span>
                                                </td>
                                                <td>
                                                    <span><%= t.getRawName() %></span>
                                                </td>
                                                <td>
                                                    <div class="drag-handle" title="Kéo để đổi hạt giống">
                                                        <i class="fa-solid fa-grip-vertical"></i>
                                                    </div>
                                                </td>
                                                <td>
                                                    <button type="submit" name="deleteTeamIds" value="<%= t.getId() %>" class="btn-delete-row" title="Xóa đội" onclick="return confirm('Xóa đội này khỏi giải con?');">
                                                        <i class="fa-solid fa-xmark"></i>
                                                    </button>
                                                </td>
                                                <td>
                                                    <input type="checkbox" name="deleteTeamIds" class="team-item-cb team-select-cb" value="<%= t.getId() %>">
                                                </td>
                                            </tr>
                                        <% }
                                        } else { %>
                                            <tr>
                                                <td colspan="5">
                                                    <div class="roster-empty-state"><span class="roster-empty-icon"><i class="fa-solid fa-users" aria-hidden="true"></i></span><strong>Chưa có đội tham gia</strong><p>Nhập danh sách hoặc chọn đội từ Partner.</p></div>
                                                </td>
                                            </tr>
                                        <% } %>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </form>

                    <div>
                        <i class="fa-solid fa-hand-pointer text-mint"></i> Kéo thả để đổi thứ tự hạt giống
                    </div>
                </div>

            </div>

            <!-- STEP FOOTER NAVIGATION BAR -->
            <div>
                <a href="${pageContext.request.contextPath}/rolling/tournament-list?id=<%= seriesIdVal %>" class="btn btn-secondary">
                    <i class="fa-solid fa-arrow-left"></i> Danh sách giải con
                </a>
                <button type="button" id="nextStepBtn" onclick="window.proceedToNextStep(event)" class="btn btn-mint">
                    Tiếp theo <i class="fa-solid fa-arrow-right"></i>
                </button>
            </div>

            <form id="saveOrderAndProceedForm" method="POST" action="${pageContext.request.contextPath}/rolling/tournament-teams">
                <input type="hidden" name="action" value="saveTeamsOrder">
                <input type="hidden" name="tournamentId" value="<%= tourneyId %>">
                <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">
                <input type="hidden" name="nextUrl" value="<%= nextStepUrl %>">
                <input type="hidden" name="orderedTeamNames" id="orderedTeamNamesInput" value="">
            </form>

        </main></div>

        <div id="partnerPickerHost"></div>
        <script>
            window.currentSubTourneyId = "<%= tourneyId %>";
            window.seriesIdVal = "<%= seriesIdVal %>";
            window.appContextPath = "${pageContext.request.contextPath}";
            window.targetTeamCount = <%= targetTeamCountVal %>;
            window.copiedSourceTourneyName = "<%= (copiedSourceTourneyNameParam != null) ? copiedSourceTourneyNameParam.replace("\\", "\\\\").replace("\"", "\\\"") : "" %>";
            window.tourneyFormat = "<%= tourneyFormat %>";
        </script>
        <script src="${pageContext.request.contextPath}/js/rolling/rolling-standings-engine.js?v=20261110-setup-perf1"></script>
        <script src="${pageContext.request.contextPath}/js/rolling/rolling-tournament-teams.js?v=20261113-lazy-partner"></script>
        <script src="${pageContext.request.contextPath}/js/standalone-format-layout.js?v=3"></script>
        <script src="${pageContext.request.contextPath}/js/rolling/series-setup.js?v=2-form-action"></script>
        <script src="${pageContext.request.contextPath}/js/setup-presentation.js?v=6-batched"></script>
    </body>
</html>
