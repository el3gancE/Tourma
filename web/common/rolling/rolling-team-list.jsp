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
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Design System CSS & Dedicated Rolling Team List CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-team-list.css">
    </head>
    <body>
        <!-- Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-series"/>
        </jsp:include>

        <!-- Dynamic 3-Mode Sidebar Component -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
            <jsp:param name="activeStep" value="team-list"/>
        </jsp:include>

        <main class="container has-sidebar" style="max-width: 900px; padding: 1.5rem 1rem;">
            
            <!-- Breadcrumb Navigation -->
            <div style="margin-bottom: 1rem;">
                <a href="${pageContext.request.contextPath}/rolling/dashboard?id=<%= seriesIdVal %>" class="text-muted" style="font-size: 0.85rem; font-weight: 600; text-decoration: none;">
                    <i class="fa-solid fa-arrow-left"></i> Quay lại Dashboard Series
                </a>
            </div>

            <!-- Series Header Banner Card -->
            <div class="team-list-header-card">
                <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 1rem;">
                    <div>
                        <h1 class="rolling-series-name" style="font-size: 1.85rem; font-weight: 800; color: #ffffff; margin: 0 0 0.25rem 0;">
                            <%= seriesName %> - Danh Sách Đội
                        </h1>
                        <p class="text-muted" style="font-size: 0.85rem; margin-top: 0.25rem;">
                            Tổng cộng <%= partnerCount %> đội đã đăng ký cơ chế Partner chính thức để tích điểm trượt qua các giải đấu con.
                        </p>
                    </div>

                    <button type="button" onclick="openAddTeamPopup()" class="btn btn-mint" style="font-weight: 700; padding: 0.5rem 1.1rem; border-radius: 8px; font-size: 0.85rem;">
                        <i class="fa-solid fa-user-plus"></i> + Đăng Ký Đội Mới
                    </button>
                </div>
            </div>

            <!-- Main Partner Team List Table Card -->
            <div class="team-list-card">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem;">
                    <h3 style="font-size: 1.1rem; font-weight: 800; color: #ffffff; margin: 0;">
                        Danh Sách Đội Partner (<%= partnerCount %> Đội)
                    </h3>
                    <input type="text" class="form-control" placeholder="Tìm kiếm tên đội..." onkeyup="filterPartnerTeams(this.value)" style="max-width: 250px; font-size: 0.82rem; border-radius: 8px;">
                </div>

                <table class="team-list-table" id="partnerTeamTable">
                    <thead>
                        <tr>
                            <th style="width: 50px;">STT</th>
                            <th class="sortable-th" onclick="sortTable('name')" style="cursor: pointer; user-select: none;">
                                Tên Đội <i class="fa-solid fa-sort-up sort-icon sort-icon-name" style="margin-left: 4px; color: #2dd4bf;"></i>
                            </th>
                            <th class="sortable-th" onclick="sortTable('tourneys')" style="text-align: center; width: 170px; cursor: pointer; user-select: none;">
                                Số giải tham dự <i class="fa-solid fa-sort sort-icon sort-icon-tourneys" style="margin-left: 4px; color: #64748b;"></i>
                            </th>
                            <th class="sortable-th" onclick="sortTable('date')" style="cursor: pointer; user-select: none;">
                                Ngày Đăng Ký <i class="fa-solid fa-sort sort-icon sort-icon-date" style="margin-left: 4px; color: #64748b;"></i>
                            </th>
                            <th style="text-align: right;">Thao Tác</th>
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
                                <td class="row-stt" style="font-weight: 700; color: var(--team-text-muted);"><%= idx + 1 %></td>
                                <td style="font-weight: 700; color: #ffffff;">
                                    <%= p.getName() %>
                                </td>
                                <td style="text-align: center; font-weight: 700; color: var(--team-text-muted);">
                                    <span class="tourneys-participated-val" data-partner-id="<%= p.getId() %>" data-team-name="<%= p.getName() != null ? p.getName().replace("\"", "&quot;") : "" %>"><%= tCount %></span>
                                </td>
                                <td style="color: var(--team-text-muted); font-size: 0.82rem;">
                                    <%= p.getCreatedAt() != null ? p.getCreatedAt().toString().substring(0, 16) : "-" %>
                                </td>
                                <td style="text-align: right;">
                                    <form method="POST" action="${pageContext.request.contextPath}/rolling/team-list" style="display: inline;" onsubmit="return confirm('Bạn có chắc chắn muốn hủy đăng ký đội <%= p.getName() %> khỏi Series này?');">
                                        <input type="hidden" name="action" value="deletePartner">
                                        <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">
                                        <input type="hidden" name="partnerId" value="<%= p.getId() %>">
                                        <button type="submit" class="btn" style="background: rgba(239, 68, 68, 0.15); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.3); padding: 0.35rem 0.85rem; border-radius: 6px; font-size: 0.78rem; font-weight: 700; cursor: pointer;">
                                            <i class="fa-solid fa-trash-can"></i> Hủy Đăng Ký
                                        </button>
                                    </form>
                                </td>
                            </tr>
                        <% } 
                        } else { %>
                            <tr>
                                <td colspan="5" style="text-align: center; padding: 3rem; color: var(--team-text-muted);">
                                    <i class="fa-solid fa-users-slash" style="font-size: 2.5rem; margin-bottom: 0.75rem; display: block; opacity: 0.5;"></i>
                                    Chưa có đội bóng nào đăng ký cho Series này. Hãy bấm "+ Đăng Ký Đội Bóng Mới" để bắt đầu.
                                </td>
                            </tr>
                        <% } %>
                    </tbody>
                </table>
            </div>

        </main>

        <!-- Generic Reusable Add Team Popup Component (Bulk Add) -->
        <jsp:include page="/common/component/add-team-popup.jsp">
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
        </jsp:include>

        <script src="${pageContext.request.contextPath}/js/rolling/rolling-team-list.js?v=<%= System.currentTimeMillis() %>"></script>
    </body>
</html>
