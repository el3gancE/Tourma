<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.PartnerParticipant, java.util.List, java.util.Map, java.util.HashMap"%>
<%
    Series series = (Series) request.getAttribute("series");
    List<PartnerParticipant> partnerList = (List<PartnerParticipant>) request.getAttribute("partnerList");
    Map<String, Integer> tourneysCountMap = (Map<String, Integer>) request.getAttribute("tourneysCountMap");

    String seriesIdVal = (series != null && series.getId() != null) ? series.getId() : "";
    String seriesName = (series != null) ? series.getName() : "VBA Pro League 2026 Circuit";
    int partnerCount = (partnerList != null) ? partnerList.size() : 0;
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Danh Sách Đội Partner - <%= seriesName %></title>

        <!-- Google Fonts & FontAwesome -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

        <!-- Design System CSS & Dedicated Rolling Team List CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-team-list.css">
    <script src="${pageContext.request.contextPath}/js/theme.js?v=20261031-classic"></script>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=20261031m">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=18">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/workspace.css?v=20">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-workspace.css?v=18">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css?v=6">
</head>
    <body class="ui-page workspace-page tournament-workspace series-page rolling-team-list-page">
        <!-- Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-series"/><jsp:param name="themeToggle" value="true"/>
        </jsp:include>

        <!-- Dynamic 3-Mode Sidebar Component -->
        <div class="tournament-shell"><details class="tournament-navigation" open><summary>Điều hướng chuỗi giải</summary><jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
            <jsp:param name="activeStep" value="team-list"/>
        </jsp:include></details>

        <main class="series-main" data-ui-style="rolling-team-list-jsp-001">



            <!-- Series Header Banner Card -->
            <div class="team-list-header-card">
                <div data-ui-style="rolling-team-list-jsp-004" style="display: flex;">
                    <div>
                        <h1 class="rolling-series-name" data-ui-style="rolling-team-list-jsp-005">
                            Danh sách đội tuyển
                        </h1>
                        <p class="text-muted" data-ui-style="rolling-team-list-jsp-006">
                            <strong><%= partnerCount %> đội</strong> đăng ký tham gia chuỗi giải <%= seriesName %>.
                        </p>
                    </div>

                    <div data-ui-style="rolling-team-list-jsp-007" style="display: flex;">
                        <button type="button" onclick="openBulkViewModal()" class="btn" data-ui-style="rolling-team-list-jsp-008" style="display: inline-flex;">
                            <i class="fa-solid fa-align-left text-mint"></i> Xem dạng văn bản
                        </button>
                        <button type="button" onclick="openAddTeamPopup()" class="btn btn-mint" data-ui-style="rolling-team-list-jsp-009" style="display: inline-flex;">
                            <i class="fa-solid fa-user-plus"></i> Đăng ký đội
                        </button>
                    </div>
                </div>
            </div>

            <!-- Main Partner Team List Table Card -->
            <div class="team-list-card">
                <div data-ui-style="rolling-team-list-jsp-010" style="display: flex;">
                    <h3 data-ui-style="rolling-team-list-jsp-011">
                        Danh Sách Đội Partner (<%= partnerCount %> Đội)
                    </h3>
                    <div data-ui-style="rolling-team-list-jsp-012" style="display: flex;">
                        <input type="text" class="form-control" placeholder="Tìm kiếm tên đội..." onkeyup="filterPartnerTeams(this.value)" data-ui-style="rolling-team-list-jsp-013">
                        <button type="button" onclick="openBulkViewModal()" class="btn" title="Xem danh sách dạng văn bản / Bulk" data-ui-style="rolling-team-list-jsp-014" style="display: inline-flex;">
                            <i class="fa-solid fa-align-left text-mint"></i> Dạng văn bản
                        </button>
                    </div>
                </div>

                <table class="team-list-table" id="partnerTeamTable">
                    <thead>
                        <tr>
                            <th data-ui-style="rolling-team-list-jsp-015">STT</th>
                            <th class="sortable-th" onclick="sortTable('name')" data-ui-style="rolling-team-list-jsp-016">
                                Tên Đội <i class="fa-solid fa-sort-up sort-icon sort-icon-name" data-ui-style="rolling-team-list-jsp-017"></i>
                            </th>
                            <th class="sortable-th" onclick="sortTable('tourneys')" data-ui-style="rolling-team-list-jsp-018">
                                Số giải tham dự <i class="fa-solid fa-sort sort-icon sort-icon-tourneys" data-ui-style="rolling-team-list-jsp-019"></i>
                            </th>
                            <th class="sortable-th" onclick="sortTable('date')" data-ui-style="rolling-team-list-jsp-020">
                                Ngày Đăng Ký <i class="fa-solid fa-sort sort-icon sort-icon-date" data-ui-style="rolling-team-list-jsp-021"></i>
                            </th>
                            <th data-ui-style="rolling-team-list-jsp-022">Thao Tác</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% if (partnerList != null && !partnerList.isEmpty()) {
                            for (int idx = 0; idx < partnerList.size(); idx++) {
                                PartnerParticipant p = partnerList.get(idx);
                                int tCount = (tourneysCountMap != null && tourneysCountMap.containsKey(p.getId())) ? tourneysCountMap.get(p.getId()) : 0;
                                long createdMillis = (p.getCreatedAt() != null) ? p.getCreatedAt().getTime() : 0L;
                        %>
                            <tr data-partner-id="<%= p.getId() %>" data-team-name="<%= p.getName() != null ? p.getName().replace("\"", "&quot;") : "" %>" data-tourney-count="<%= tCount %>" data-created-at="<%= createdMillis %>">
                                <td class="row-stt" data-ui-style="rolling-team-list-jsp-023"><%= idx + 1 %></td>
                                <td data-ui-style="rolling-team-list-jsp-024">
                                    <a href="${pageContext.request.contextPath}/team-profile?seriesId=<%= seriesIdVal %>&partnerId=<%= p.getId() %>&teamName=<%= java.net.URLEncoder.encode(p.getName(), "UTF-8") %>" data-ui-style="rolling-team-list-jsp-025" onmouseover="this.style.color='var(--ui-accent)'" onmouseout="this.style.color='var(--ui-text)'">
                                        <%= p.getName() %>
                                    </a>
                                </td>
                                <td data-ui-style="rolling-team-list-jsp-026">
                                    <span class="tourneys-participated-val" data-partner-id="<%= p.getId() %>" data-team-name="<%= p.getName() != null ? p.getName().replace("\"", "&quot;") : "" %>"><%= tCount %></span>
                                </td>
                                <td data-ui-style="rolling-team-list-jsp-027">
                                    <%= p.getCreatedAt() != null ? p.getCreatedAt().toString().substring(0, 16) : "-" %>
                                </td>
                                <td data-ui-style="rolling-team-list-jsp-028">
                                    <form method="POST" action="${pageContext.request.contextPath}/rolling/team-list" style="display: inline;" onsubmit="return confirm('Bạn có chắc chắn muốn hủy đăng ký đội <%= p.getName() %> khỏi Series này?');">
                                        <input type="hidden" name="action" value="deletePartner">
                                        <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">
                                        <input type="hidden" name="partnerId" value="<%= p.getId() %>">
                                        <button type="submit" class="btn" data-ui-style="rolling-team-list-jsp-029">
                                            <i class="fa-solid fa-trash-can"></i> Hủy Đăng Ký
                                        </button>
                                    </form>
                                </td>
                            </tr>
                        <% }
                        } else { %>
                            <tr>
                                <td colspan="5" data-ui-style="rolling-team-list-jsp-030">
                                    <i class="fa-solid fa-users-slash" data-ui-style="rolling-team-list-jsp-031" style="display: block;"></i>
                                    Chưa có đội bóng nào đăng ký cho Series này. Hãy bấm "+ Đăng Ký Đội Bóng Mới" để bắt đầu.
                                </td>
                            </tr>
                        <% } %>
                    </tbody>
                </table>
            </div>

        </main></div>

        <!-- MODAL: BULK VIEW PARTNER TEAMS (VIEW AS BULK) -->
        <div id="modalBulkView" class="team-modal-overlay" style="display: none;">
            <div class="team-modal-card" data-ui-style="rolling-team-list-jsp-032">
                <!-- Header -->
                <div data-ui-style="rolling-team-list-jsp-033" style="display: flex;">
                    <div data-ui-style="rolling-team-list-jsp-034" style="display: flex;">
                        <div data-ui-style="rolling-team-list-jsp-035" style="display: flex;">
                            <i class="fa-solid fa-align-left"></i>
                        </div>
                        <div>
                            <h3 data-ui-style="rolling-team-list-jsp-036">
                                Danh Sách Đội Đối Tác (Dạng văn bản)
                            </h3>
                            <span data-ui-style="rolling-team-list-jsp-037">
                                Dạng văn bản thô (mỗi đội một dòng)
                            </span>
                        </div>
                    </div>
                    <button type="button" onclick="closeBulkViewModal()" data-ui-style="rolling-team-list-jsp-038" onmouseover="this.style.color='var(--ui-text)'" onmouseout="this.style.color='var(--ui-muted)'">
                        <i class="fa-solid fa-xmark"></i>
                    </button>
                </div>

                <!-- Info Bar -->
                <div data-ui-style="rolling-team-list-jsp-039" style="display: flex;">
                    <span data-ui-style="rolling-team-list-jsp-040">
                        Tổng số: <strong data-ui-style="rolling-team-list-jsp-041"><%= partnerCount %> Đội</strong>
                    </span>
                    <span id="bulkCopyToast" data-ui-style="rolling-team-list-jsp-042">
                        <i class="fa-solid fa-circle-check"></i> Đã sao chép vào Clipboard!
                    </span>
                </div>

                <!-- Bulk Textarea -->
                <textarea id="bulkPartnerTextarea" readonly class="form-control" data-ui-style="rolling-team-list-jsp-043"><%
                    if (partnerList != null && !partnerList.isEmpty()) {
                        for (int i = 0; i < partnerList.size(); i++) {
                            PartnerParticipant p = partnerList.get(i);
                            if (p != null && p.getName() != null) {
                                out.print(p.getName().trim() + (i < partnerList.size() - 1 ? "\n" : ""));
                            }
                        }
                    }
                %></textarea>

                <!-- Actions Footer -->
                <div data-ui-style="rolling-team-list-jsp-044" style="display: flex;">
                    <button type="button" onclick="selectAllBulkText()" class="btn" data-ui-style="rolling-team-list-jsp-045">
                        <i class="fa-solid fa-object-group"></i> Chọn Tất Cả
                    </button>
                    <div data-ui-style="rolling-team-list-jsp-046" style="display: flex;">
                        <button type="button" onclick="copyBulkPartnerTeams()" class="btn btn-mint" data-ui-style="rolling-team-list-jsp-047" style="display: inline-flex;">
                            <i class="fa-regular fa-copy"></i> Sao Chép Toàn Bộ
                        </button>
                        <button type="button" onclick="closeBulkViewModal()" class="btn" data-ui-style="rolling-team-list-jsp-048">
                            Đóng
                        </button>
                    </div>
                </div>
            </div>
        </div>

        <!-- Generic Reusable Add Team Popup Component (Bulk Add) -->
        <jsp:include page="/common/component/add-team-popup.jsp">
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
        </jsp:include>

        <script src="${pageContext.request.contextPath}/js/rolling/rolling-team-list.js?v=<%= System.currentTimeMillis() %>"></script>
    </body>
</html>
