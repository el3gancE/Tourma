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

        <!-- Design System CSS & Dedicated Rolling Team List CSS -->
    <script src="${pageContext.request.contextPath}/js/theme.js?v=20261101-settings"></script>
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

        <main class="series-main">



            <!-- Series Header Banner Card -->
            <div class="team-list-header-card">
                <div>
                    <div>
                        <h1 class="rolling-series-name">
                            Danh sách đội tuyển
                        </h1>
                        <p class="text-muted">
                            <strong><%= partnerCount %> đội</strong> đăng ký tham gia chuỗi giải <%= seriesName %>.
                        </p>
                    </div>

                    <div>
                        <button type="button" onclick="openBulkViewModal()" class="btn">
                            <i class="fa-solid fa-align-left text-mint"></i> Xem dạng văn bản
                        </button>
                        <button type="button" onclick="openAddTeamPopup()" class="btn btn-mint">
                            <i class="fa-solid fa-user-plus"></i> Đăng ký đội
                        </button>
                    </div>
                </div>
            </div>

            <!-- Main Partner Team List Table Card -->
            <div class="team-list-card">
                <div>
                    <h3>
                        Danh Sách Đội Partner (<%= partnerCount %> Đội)
                    </h3>
                    <div>
                        <input type="text" class="form-control" placeholder="Tìm kiếm tên đội..." onkeyup="filterPartnerTeams(this.value)">
                        <button type="button" onclick="openBulkViewModal()" class="btn" title="Xem danh sách dạng văn bản / Bulk">
                            <i class="fa-solid fa-align-left text-mint"></i> Dạng văn bản
                        </button>
                    </div>
                </div>

                <table class="team-list-table" id="partnerTeamTable">
                    <thead>
                        <tr>
                            <th>STT</th>
                            <th class="sortable-th" onclick="sortTable('name')">
                                Tên Đội <i class="fa-solid fa-sort-up sort-icon sort-icon-name"></i>
                            </th>
                            <th class="sortable-th" onclick="sortTable('tourneys')">
                                Số giải tham dự <i class="fa-solid fa-sort sort-icon sort-icon-tourneys"></i>
                            </th>
                            <th class="sortable-th" onclick="sortTable('date')">
                                Ngày Đăng Ký <i class="fa-solid fa-sort sort-icon sort-icon-date"></i>
                            </th>
                            <th>Thao Tác</th>
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
                                <td class="row-stt"><%= idx + 1 %></td>
                                <td>
                                    <a href="${pageContext.request.contextPath}/team-profile?seriesId=<%= seriesIdVal %>&partnerId=<%= p.getId() %>&teamName=<%= java.net.URLEncoder.encode(p.getName(), "UTF-8") %>">
                                        <%= p.getName() %>
                                    </a>
                                </td>
                                <td>
                                    <span class="tourneys-participated-val" data-partner-id="<%= p.getId() %>" data-team-name="<%= p.getName() != null ? p.getName().replace("\"", "&quot;") : "" %>"><%= tCount %></span>
                                </td>
                                <td>
                                    <%= p.getCreatedAt() != null ? p.getCreatedAt().toString().substring(0, 16) : "-" %>
                                </td>
                                <td>
                                    <form method="POST" action="${pageContext.request.contextPath}/rolling/team-list" onsubmit="return confirm('Bạn có chắc chắn muốn hủy đăng ký đội <%= p.getName() %> khỏi Series này?');">
                                        <input type="hidden" name="action" value="deletePartner">
                                        <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">
                                        <input type="hidden" name="partnerId" value="<%= p.getId() %>">
                                        <button type="submit" class="btn">
                                            <i class="fa-solid fa-trash-can"></i> Hủy Đăng Ký
                                        </button>
                                    </form>
                                </td>
                            </tr>
                        <% }
                        } else { %>
                            <tr>
                                <td colspan="5">
                                    <i class="fa-solid fa-users-slash"></i>
                                    Chưa có đội bóng nào đăng ký cho Series này. Hãy bấm "+ Đăng Ký Đội Bóng Mới" để bắt đầu.
                                </td>
                            </tr>
                        <% } %>
                    </tbody>
                </table>
            </div>

        </main></div>

        <!-- MODAL: BULK VIEW PARTNER TEAMS (VIEW AS BULK) -->
        <div id="modalBulkView" class="team-modal-overlay">
            <div class="team-modal-card">
                <!-- Header -->
                <div>
                    <div>
                        <div>
                            <i class="fa-solid fa-align-left"></i>
                        </div>
                        <div>
                            <h3>
                                Danh Sách Đội Đối Tác (Dạng văn bản)
                            </h3>
                            <span>
                                Dạng văn bản thô (mỗi đội một dòng)
                            </span>
                        </div>
                    </div>
                    <button type="button" onclick="closeBulkViewModal()">
                        <i class="fa-solid fa-xmark"></i>
                    </button>
                </div>

                <!-- Info Bar -->
                <div>
                    <span>
                        Tổng số: <strong><%= partnerCount %> Đội</strong>
                    </span>
                    <span id="bulkCopyToast">
                        <i class="fa-solid fa-circle-check"></i> Đã sao chép vào Clipboard!
                    </span>
                </div>

                <!-- Bulk Textarea -->
                <textarea id="bulkPartnerTextarea" readonly class="form-control"><%
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
                <div>
                    <button type="button" onclick="selectAllBulkText()" class="btn">
                        <i class="fa-solid fa-object-group"></i> Chọn Tất Cả
                    </button>
                    <div>
                        <button type="button" onclick="copyBulkPartnerTeams()" class="btn btn-mint">
                            <i class="fa-regular fa-copy"></i> Sao Chép Toàn Bộ
                        </button>
                        <button type="button" onclick="closeBulkViewModal()" class="btn">
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
