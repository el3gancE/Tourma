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

                    <div style="display: flex; align-items: center; gap: 0.65rem; flex-wrap: wrap;">
                        <button type="button" onclick="openBulkViewModal()" class="btn" style="background: rgba(255, 255, 255, 0.08); border: 1px solid rgba(255, 255, 255, 0.16); color: #f1f5f9; font-weight: 700; padding: 0.5rem 1.1rem; border-radius: 8px; font-size: 0.85rem; display: inline-flex; align-items: center; gap: 0.45rem; cursor: pointer; transition: all 0.2s ease;">
                            <i class="fa-solid fa-align-left text-mint"></i> View as Bulk
                        </button>
                        <button type="button" onclick="openAddTeamPopup()" class="btn btn-mint" style="font-weight: 700; padding: 0.5rem 1.1rem; border-radius: 8px; font-size: 0.85rem; display: inline-flex; align-items: center; gap: 0.45rem;">
                            <i class="fa-solid fa-user-plus"></i> + Đăng Ký Đội Mới
                        </button>
                    </div>
                </div>
            </div>

            <!-- Main Partner Team List Table Card -->
            <div class="team-list-card">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem; flex-wrap: wrap; gap: 0.75rem;">
                    <h3 style="font-size: 1.1rem; font-weight: 800; color: #ffffff; margin: 0;">
                        Danh Sách Đội Partner (<%= partnerCount %> Đội)
                    </h3>
                    <div style="display: flex; align-items: center; gap: 0.5rem;">
                        <input type="text" class="form-control" placeholder="Tìm kiếm tên đội..." onkeyup="filterPartnerTeams(this.value)" style="max-width: 230px; font-size: 0.82rem; border-radius: 8px;">
                        <button type="button" onclick="openBulkViewModal()" class="btn" title="Xem danh sách dạng văn bản / Bulk" style="background: rgba(255, 255, 255, 0.06); border: 1px solid rgba(255, 255, 255, 0.12); color: #cbd5e1; font-size: 0.82rem; font-weight: 700; padding: 0.45rem 0.85rem; border-radius: 8px; cursor: pointer; display: inline-flex; align-items: center; gap: 0.35rem;">
                            <i class="fa-solid fa-align-left text-mint"></i> Bulk View
                        </button>
                    </div>
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
                                    <a href="${pageContext.request.contextPath}/team-profile?seriesId=<%= seriesIdVal %>&partnerId=<%= p.getId() %>&teamName=<%= java.net.URLEncoder.encode(p.getName(), "UTF-8") %>" style="color: #ffffff; text-decoration: none; transition: color 0.18s ease;" onmouseover="this.style.color='#2dd4bf'" onmouseout="this.style.color='#ffffff'">
                                        <%= p.getName() %>
                                    </a>
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

        <!-- MODAL: BULK VIEW PARTNER TEAMS (VIEW AS BULK) -->
        <div id="modalBulkView" class="team-modal-overlay" style="display: none;">
            <div class="team-modal-card" style="max-width: 560px;">
                <!-- Header -->
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
                    <div style="display: flex; align-items: center; gap: 0.65rem;">
                        <div style="width: 38px; height: 38px; border-radius: 9px; background: rgba(45, 212, 191, 0.15); display: flex; align-items: center; justify-content: center; color: #2dd4bf; font-size: 1.15rem;">
                            <i class="fa-solid fa-align-left"></i>
                        </div>
                        <div>
                            <h3 style="font-size: 1.15rem; font-weight: 800; color: #ffffff; margin: 0;">
                                Danh Sách Đội Đối Tác (Bulk View)
                            </h3>
                            <span style="font-size: 0.78rem; color: #94a3b8;">
                                Dạng văn bản thô (mỗi đội một dòng)
                            </span>
                        </div>
                    </div>
                    <button type="button" onclick="closeBulkViewModal()" style="background: none; border: none; color: #94a3b8; font-size: 1.25rem; cursor: pointer; padding: 0.25rem; transition: color 0.15s ease;" onmouseover="this.style.color='#ffffff'" onmouseout="this.style.color='#94a3b8'">
                        <i class="fa-solid fa-xmark"></i>
                    </button>
                </div>

                <!-- Info Bar -->
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">
                    <span style="font-size: 0.82rem; color: #cbd5e1; font-weight: 600;">
                        Tổng số: <strong style="color: #2dd4bf;"><%= partnerCount %> Đội</strong>
                    </span>
                    <span id="bulkCopyToast" style="font-size: 0.78rem; color: #2dd4bf; font-weight: 700; opacity: 0; transition: opacity 0.25s ease;">
                        <i class="fa-solid fa-circle-check"></i> Đã sao chép vào Clipboard!
                    </span>
                </div>

                <!-- Bulk Textarea -->
                <textarea id="bulkPartnerTextarea" readonly class="form-control" style="width: 100%; height: 280px; font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace; font-size: 0.88rem; line-height: 1.65; background: rgba(0, 0, 0, 0.45); border: 1px solid rgba(255, 255, 255, 0.12); border-radius: 10px; color: #f8fafc; padding: 0.85rem 1rem; resize: vertical; outline: none; box-sizing: border-box;"><%
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
                <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 1.25rem; gap: 0.75rem; flex-wrap: wrap;">
                    <button type="button" onclick="selectAllBulkText()" class="btn" style="background: rgba(255, 255, 255, 0.06); border: 1px solid rgba(255, 255, 255, 0.15); color: #cbd5e1; font-weight: 600; font-size: 0.82rem; padding: 0.45rem 0.9rem; border-radius: 8px; cursor: pointer;">
                        <i class="fa-solid fa-object-group"></i> Chọn Tất Cả
                    </button>
                    <div style="display: flex; gap: 0.65rem;">
                        <button type="button" onclick="copyBulkPartnerTeams()" class="btn btn-mint" style="font-weight: 700; font-size: 0.85rem; padding: 0.5rem 1.25rem; border-radius: 8px; display: inline-flex; align-items: center; gap: 0.45rem;">
                            <i class="fa-regular fa-copy"></i> Sao Chép Toàn Bộ
                        </button>
                        <button type="button" onclick="closeBulkViewModal()" class="btn" style="background: rgba(255, 255, 255, 0.08); border: 1px solid rgba(255, 255, 255, 0.15); color: #94a3b8; font-weight: 600; font-size: 0.85rem; padding: 0.5rem 1rem; border-radius: 8px; cursor: pointer;">
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
