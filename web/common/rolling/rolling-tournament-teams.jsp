<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, model.Team, model.PartnerParticipant, model.SeriesStanding, java.util.List, java.util.ArrayList, java.util.Map, java.util.HashMap"%>
<%
    Tournament tournament = (Tournament) request.getAttribute("tournament");
    Series series = (Series) request.getAttribute("series");
    List<Team> currentTeams = (List<Team>) request.getAttribute("currentTeams");
    List<PartnerParticipant> partnerList = (List<PartnerParticipant>) request.getAttribute("partnerList");
    List<SeriesStanding> standingsList = (List<SeriesStanding>) request.getAttribute("standingsList");

    String tourneyId = (tournament != null) ? tournament.getId() : "";
    String tourneyName = (tournament != null) ? tournament.getName() : "Giải Đấu Con";
    String seriesIdVal = (series != null && series.getId() != null) ? series.getId() : "";
    String seriesName = (series != null) ? series.getName() : "Series";
    int teamCount = (currentTeams != null) ? currentTeams.size() : 0;
    int partnerCount = (partnerList != null) ? partnerList.size() : 0;

    // Order partnerList by Series Standings rank (Rank 1, 2, 3...)
    List<PartnerParticipant> orderedPartners = new ArrayList<>();
    if (standingsList != null) {
        for (int r = 0; r < standingsList.size(); r++) {
            SeriesStanding st = standingsList.get(r);
            if (st.getNormalizedTeamName() != null) {
                if (partnerList != null) {
                    for (PartnerParticipant p : partnerList) {
                        if (p.getName() != null && p.getName().equalsIgnoreCase(st.getNormalizedTeamName()) && !orderedPartners.contains(p)) {
                            orderedPartners.add(p);
                            break;
                        }
                    }
                }
            }
        }
    }
    if (partnerList != null) {
        for (PartnerParticipant p : partnerList) {
            if (!orderedPartners.contains(p)) {
                orderedPartners.add(p);
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
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Design System CSS, Standalone Configure Teams CSS & Dedicated Teams CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/create-tournament.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/configure-tournament-teams.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-tournament-teams.css">
    </head>
    <body>
        <!-- Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-series"/>
        </jsp:include>

        <!-- Dynamic 3-Mode Sidebar Component -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="id" value="<%= tourneyId %>"/>
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
            <jsp:param name="activeStep" value="teams"/>
        </jsp:include>

        <main class="container has-sidebar">
            
            <!-- CENTERED MAIN TITLE (MATCHING STANDALONE TOURNAMENT 100%) -->
            <h1 class="page-main-title" style="margin-top: 0;">
                Quản lý danh sách đội
            </h1>

            <!-- TWO-COLUMN WORKSPACE GRID (MATCHING STANDALONE TOURNAMENT PERFECTLY) -->
            <div class="team-mgmt-grid">

                <!-- LEFT COLUMN: BULK IMPORT WORKSPACE & PARTNER SELECTION -->
                <div class="team-panel-card">
                    <div class="team-panel-header">
                        <div class="team-panel-title">
                            <i class="fa-solid fa-list-check text-mint"></i> Nhập danh sách đội
                        </div>
                        <div style="display: flex; align-items: center; gap: 0.5rem;">
                            <span class="count-badge" id="inputCountDisplay"><%= teamCount %> Đội</span>
                            <button type="button" onclick="openPartnerSelectModal()" class="btn btn-mint" style="font-size: 0.78rem; font-weight: 700; padding: 0.35rem 0.75rem; border-radius: 6px;">
                                <i class="fa-solid fa-check-double"></i> Chọn Từ Partner (<%= partnerCount %>)
                            </button>
                        </div>
                    </div>

                    <form id="bulkAddForm" method="POST" action="${pageContext.request.contextPath}/rolling/tournament-teams" style="display: flex; flex-direction: column; flex: 1;">
                        <input type="hidden" name="action" value="bulkAdd">
                        <input type="hidden" name="tournamentId" value="<%= tourneyId %>">
                        <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">

                        <div class="bulk-input-container" style="flex: 1; display: flex; flex-direction: column;">
                            <textarea name="teamNamesText" id="teamTextarea" class="team-textarea-input" style="flex: 1;"
                                      placeholder="Nhập hoặc dán danh sách đội (mỗi đội một dòng)...&#10;Ví dụ:&#10;Hà Nội FC&#10;Hải Phòng FC&#10;Saigon Heat"></textarea>
                        </div>

                        <div class="input-actions-bar" style="margin-top: 0.75rem; display: flex; justify-content: space-between; align-items: center;">
                            <button type="button" onclick="document.getElementById('teamTextarea').value=''" class="btn btn-secondary" style="font-size: 0.78rem;">
                                <i class="fa-solid fa-rotate-left"></i> Xóa Hết
                            </button>
                            <button type="submit" class="btn btn-mint" style="font-weight: 700; font-size: 0.82rem;">
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
                        <div class="manage-toolbar-left">
                            <button type="button" class="btn btn-secondary" style="background: rgba(255, 255, 255, 0.06); color: #cbd5e1; border: 1px solid rgba(255, 255, 255, 0.15);" onclick="shuffleSubtourneyTeams()">
                                <i class="fa-solid fa-shuffle text-mint"></i> Xáo Trộn Ngẫu Nhiên
                            </button>
                        </div>
                        <div class="manage-toolbar-right" style="display: flex; gap: 0.5rem; align-items: center;">
                            <input type="text" class="form-control" placeholder="Tìm kiếm tên đội..." onkeyup="filterTournamentTeams(this.value)" style="max-width: 130px; font-size: 0.78rem; padding: 0.25rem 0.5rem; border-radius: 6px;">
                            <button type="button" class="btn btn-secondary" style="background: rgba(239, 68, 68, 0.1); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.25);" onclick="deleteSelectedSubtourneyTeams()">
                                <i class="fa-solid fa-trash-can"></i> Xóa Đã Chọn
                            </button>
                        </div>
                    </div>

                    <!-- BULK REMOVE TEAMS FORM ENCLOSURE -->
                    <form id="bulkRemoveTeamsForm" method="POST" action="${pageContext.request.contextPath}/rolling/tournament-teams" style="flex: 1; display: flex; flex-direction: column; overflow: hidden;">
                        <input type="hidden" name="action" value="bulkRemoveTeams">
                        <input type="hidden" name="tournamentId" value="<%= tourneyId %>">
                        <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">

                        <!-- SEEDING TABLE -->
                        <div class="table-container" style="flex: 1; overflow-y: auto;">
                            <div class="table-scroll-wrapper">
                                <table class="table" id="subtourneyTeamsTable">
                                    <thead>
                                        <tr>
                                            <th style="width: 48px; text-align: center;">NO.</th>
                                            <th>TÊN ĐỘI</th>
                                            <th style="width: 50px; text-align: center;">KÉO</th>
                                            <th style="width: 45px; text-align: center;">XÓA</th>
                                            <th style="width: 38px; text-align: center;">
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
                                                <td style="text-align: center;">
                                                    <span class="seed-badge"><%= idx + 1 %></span>
                                                </td>
                                                <td>
                                                    <span style="font-weight: 700; color: #ffffff; padding-left: 0.25rem;"><%= t.getRawName() %></span>
                                                </td>
                                                <td style="text-align: center;">
                                                    <div class="drag-handle" title="Kéo để đổi hạt giống">
                                                        <i class="fa-solid fa-grip-vertical"></i>
                                                    </div>
                                                </td>
                                                <td style="text-align: center;">
                                                    <button type="submit" name="deleteTeamIds" value="<%= t.getId() %>" class="btn-delete-row" title="Xóa đội" onclick="return confirm('Xóa đội này khỏi giải con?');">
                                                        <i class="fa-solid fa-xmark"></i>
                                                    </button>
                                                </td>
                                                <td style="text-align: center;">
                                                    <input type="checkbox" name="deleteTeamIds" class="team-item-cb team-select-cb" value="<%= t.getId() %>">
                                                </td>
                                            </tr>
                                        <% } 
                                        } else { %>
                                            <tr>
                                                <td colspan="5" style="text-align: center; padding: 2.5rem 1rem; color: var(--team-text-muted); font-size: 0.85rem;">
                                                    <i class="fa-solid fa-folder-open" style="font-size: 2rem; margin-bottom: 0.5rem; display: block; opacity: 0.5;"></i>
                                                    Chưa có đội nào. Hãy nhập ở bảng bên trái hoặc chọn từ Partner!
                                                </td>
                                            </tr>
                                        <% } %>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </form>

                    <div style="font-size: 0.75rem; color: var(--text-muted); text-align: right; margin-top: 0.5rem;">
                        <i class="fa-solid fa-hand-pointer text-mint"></i> Kéo thả từng hàng để thay đổi thứ tự Hạt Giống
                    </div>
                </div>

            </div>

            <!-- STEP FOOTER NAVIGATION BAR -->
            <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 1.5rem; margin-bottom: 2rem;">
                <a href="${pageContext.request.contextPath}/common/configure-tournament-format.jsp?id=<%= tourneyId %>&seriesId=<%= seriesIdVal %>" class="btn btn-secondary" style="font-weight: 700;">
                    <i class="fa-solid fa-arrow-left"></i> Quay lại Bước 2: Thể Thức
                </a>
                <a href="${pageContext.request.contextPath}/rolling/point-config?id=<%= tourneyId %>&seriesId=<%= seriesIdVal %>" class="btn btn-mint" style="font-weight: 800; border-radius: 10px; padding: 0.65rem 1.5rem;">
                    Tiếp Theo: Set Điểm <i class="fa-solid fa-arrow-right" style="margin-left: 0.4rem;"></i>
                </a>
            </div>

        </main>

        <!-- MODAL: SELECT QUICKLY FROM PARTNER TEAMS ORDERED BY STANDINGS -->
        <div id="partnerSelectModal" class="team-modal-overlay" style="display: none;">
            <div class="team-modal-card" style="max-width: 520px;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">
                    <h3 style="font-size: 1.15rem; font-weight: 800; color: #ffffff; margin: 0;">
                        <i class="fa-solid fa-check-double text-mint"></i> Chọn Đội Partner Trong Series
                    </h3>
                    <button type="button" onclick="closePartnerSelectModal()" style="background: none; border: none; color: #94a3b8; font-size: 1.2rem; cursor: pointer;">
                        <i class="fa-solid fa-xmark"></i>
                    </button>
                </div>
                
                <p style="font-size: 0.82rem; color: #94a3b8; margin-bottom: 0.85rem;">
                    Tích chọn các đội Partner chính thức của Series <strong><%= seriesName %></strong> để đưa vào giải con này:
                </p>

                <!-- QUICK SELECTION TOOLBAR -->
                <div style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 10px; padding: 0.75rem 1rem; margin-bottom: 0.85rem;">
                    <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.5rem; margin-bottom: 0.5rem;">
                        <div style="display: flex; align-items: center; gap: 0.4rem;">
                            <span style="font-size: 0.78rem; font-weight: 700; color: #cbd5e1;">Số đội chọn:</span>
                            <input type="number" id="quickSelectNum" value="<%= Math.min(partnerCount, 4) %>" min="2" max="<%= partnerCount %>" class="form-control" style="width: 65px; font-size: 0.82rem; font-weight: 800; text-align: center; padding: 0.2rem 0.4rem; border-radius: 6px;">
                        </div>
                        <div style="display: flex; gap: 0.4rem; flex-wrap: wrap;">
                            <button type="button" onclick="quickSelectTeams('TOP')" class="btn btn-mint" style="font-size: 0.75rem; font-weight: 700; padding: 0.25rem 0.6rem; border-radius: 6px;">
                                <i class="fa-solid fa-arrow-trend-up"></i> Top Đầu
                            </button>
                            <button type="button" onclick="quickSelectTeams('BOTTOM')" class="btn" style="background: rgba(248, 113, 113, 0.15); color: #f87171; border: 1px solid rgba(248, 113, 113, 0.3); font-size: 0.75rem; font-weight: 700; padding: 0.25rem 0.6rem; border-radius: 6px;">
                                <i class="fa-solid fa-arrow-trend-down"></i> Top Cuối
                            </button>
                            <button type="button" onclick="quickSelectTeams('RANDOM')" class="btn" style="background: rgba(251, 191, 36, 0.15); color: #fbbf24; border: 1px solid rgba(251, 191, 36, 0.3); font-size: 0.75rem; font-weight: 700; padding: 0.25rem 0.6rem; border-radius: 6px;">
                                <i class="fa-solid fa-shuffle"></i> Ngẫu Nhiên
                            </button>
                        </div>
                    </div>
                    <div style="display: flex; justify-content: space-between; align-items: center; font-size: 0.75rem; color: #94a3b8;">
                        <span>Chọn theo BXH hoặc tích thủ công (Tổng: <%= partnerCount %> đội)</span>
                        <div>
                            <button type="button" onclick="selectAllPartnerCheckboxes(true)" style="background: none; border: none; color: #2dd4bf; cursor: pointer; font-weight: 700; margin-right: 0.5rem;">Chọn tất cả</button>
                            <button type="button" onclick="selectAllPartnerCheckboxes(false)" style="background: none; border: none; color: #94a3b8; cursor: pointer; font-weight: 700;">Bỏ chọn</button>
                        </div>
                    </div>
                </div>

                <form method="POST" action="${pageContext.request.contextPath}/rolling/tournament-teams">
                    <input type="hidden" name="action" value="addPartnerTeams">
                    <input type="hidden" name="tournamentId" value="<%= tourneyId %>">
                    <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">

                    <!-- SINGLE COLUMN VERTICAL LIST - ONLY SHOWING TEAM NAME -->
                    <div class="partner-checkbox-list">
                        <% if (orderedPartners != null && !orderedPartners.isEmpty()) {
                            for (int pIdx = 0; pIdx < orderedPartners.size(); pIdx++) {
                                PartnerParticipant p = orderedPartners.get(pIdx);

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
                            <label class="partner-checkbox-item" data-index="<%= pIdx %>">
                                <div style="display: flex; align-items: center; gap: 0.75rem;">
                                    <input type="checkbox" name="selectedTeamNames" value="<%= p.getName() %>" class="partner-cb-input" data-index="<%= pIdx %>" <%= isAlreadyIn ? "checked disabled" : "" %>>
                                    <span style="font-size: 0.9rem; font-weight: 700; color: <%= isAlreadyIn ? "#94a3b8" : "#ffffff" %>;">
                                        <%= p.getName() %> <%= isAlreadyIn ? "(Đã thêm)" : "" %>
                                    </span>
                                </div>
                            </label>
                        <% } 
                        } else { %>
                            <div style="padding: 1rem; color: #94a3b8; font-size: 0.85rem; text-align: center;">
                                Chưa có đội Partner nào trong Series.
                            </div>
                        <% } %>
                    </div>

                    <div style="display: flex; justify-content: flex-end; gap: 0.65rem; margin-top: 1.25rem;">
                        <button type="button" onclick="closePartnerSelectModal()" class="btn btn-secondary">Hủy Bỏ</button>
                        <button type="submit" class="btn btn-mint" style="font-weight: 800;">
                            Thêm Đội Đã Chọn VÀO Giải Con
                        </button>
                    </div>
                </form>
            </div>
        </div>

        <script src="${pageContext.request.contextPath}/js/rolling/rolling-tournament-teams.js"></script>
    </body>
</html>
