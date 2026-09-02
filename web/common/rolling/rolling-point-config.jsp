<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, java.util.List, java.util.ArrayList, java.util.Map, java.util.HashMap"%>
<%
    Tournament tournament = (Tournament) request.getAttribute("tournament");
    Series series = (Series) request.getAttribute("series");
    Integer teamCountObj = (Integer) request.getAttribute("teamCount");
    int teamCount = (teamCountObj != null) ? teamCountObj : 0;

    String tourneyId = (tournament != null) ? tournament.getId() : "";
    String tourneyName = (tournament != null) ? tournament.getName() : "Giải Đấu Con";
    String seriesIdVal = (series != null && series.getId() != null) ? series.getId() : "";
    String seriesName = (series != null) ? series.getName() : "Series";
    String tierName = (tournament != null && tournament.getTierName() != null) ? tournament.getTierName().toUpperCase() : "S";
    String tourneyFormat = (tournament != null && tournament.getFormat() != null) ? tournament.getFormat().toUpperCase() : "SINGLE_ELIMINATION";

    // Retrieve saved points if available (F5 / revisit retention)
    Integer savedChampPoints = (tournament != null) ? tournament.getSeriesRewardPoints() : null;
    String savedJson = (tournament != null) ? tournament.getSeriesPointsConfig() : null;

    Map<String, Integer> savedPointsMap = new HashMap<>();
    if (savedJson != null && !savedJson.trim().isEmpty()) {
        try {
            String clean = savedJson.trim().replaceAll("[{}\"]", "");
            String[] pairs = clean.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    savedPointsMap.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
                }
            }
        } catch (Exception ignore) {}
    }

    int nTeams = (teamCount > 0) ? teamCount : 8;

    // Dynamically build position/round list
    class PositionItem {
        String key;
        String label;
        PositionItem(String k, String l) { key = k; label = l; }
    }
    List<PositionItem> posList = new ArrayList<>();

    if ("SINGLE_ELIMINATION".equals(tourneyFormat)) {
        posList.add(new PositionItem("1", "Vô Địch (1st Place)"));
        if (nTeams >= 2) posList.add(new PositionItem("2", "Á Quân (2nd Place)"));
        if (nTeams >= 4) posList.add(new PositionItem("3-4", "Bán Kết (3rd - 4th Place)"));
        if (nTeams >= 8) posList.add(new PositionItem("5-8", "Tứ Kết (5th - 8th Place)"));
        if (nTeams >= 16) posList.add(new PositionItem("9-16", "Vòng 16 (9th - 16th Place)"));
        if (nTeams >= 32) posList.add(new PositionItem("17-32", "Vòng 32 (17th - 32nd Place)"));
    } else if ("DOUBLE_ELIMINATION".equals(tourneyFormat)) {
        posList.add(new PositionItem("1", "Vô Địch (1st Place)"));
        if (nTeams >= 2) posList.add(new PositionItem("2", "Á Quân (2nd Place)"));
        if (nTeams >= 3) posList.add(new PositionItem("3", "Hạng 3 (3rd Place)"));
        if (nTeams >= 4) posList.add(new PositionItem("4", "Hạng 4 (4th Place)"));
        if (nTeams >= 6) posList.add(new PositionItem("5-6", "Hạng 5 - 6 (5th - 6th Place)"));
        if (nTeams >= 8) posList.add(new PositionItem("7-8", "Hạng 7 - 8 (7th - 8th Place)"));
        if (nTeams >= 12) posList.add(new PositionItem("9-12", "Hạng 9 - 12 (9th - 12th Place)"));
        if (nTeams >= 16) posList.add(new PositionItem("13-16", "Hạng 13 - 16 (13th - 16th Place)"));
    } else {
        // ROUND_ROBIN / LEAGUE / SWISS / MULTI_STAGE
        for (int p = 1; p <= Math.min(nTeams, 16); p++) {
            String label = "Hạng " + p;
            if (p == 1) label = "Hạng 1 (Vô Địch)";
            else if (p == 2) label = "Hạng 2 (Á Quân)";
            else if (p == 3) label = "Hạng 3 (Hạng Ba)";
            posList.add(new PositionItem(String.valueOf(p), label));
        }
    }
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title><%= tourneyName %> - Thiết Lập Điểm Thưởng - TOURMA</title>
        
        <!-- Google Fonts & FontAwesome -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Main Design System CSS & Dedicated Form CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/create-tournament.css">
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
            <jsp:param name="activeStep" value="point-config"/>
        </jsp:include>

        <main class="container has-sidebar" style="max-width: 650px; padding: 1.5rem 1rem;">
            
            <!-- Breadcrumb Navigation -->
            <div style="margin-bottom: 0.75rem;">
                <a href="${pageContext.request.contextPath}/rolling/tournament-teams?id=<%= tourneyId %>&seriesId=<%= seriesIdVal %>" class="text-muted" style="font-size: 0.85rem; font-weight: 600; text-decoration: none;">
                    <i class="fa-solid fa-arrow-left"></i> Quay lại Bước 3: Quản Lý Đội
                </a>
            </div>

            <!-- Form Container Box -->
            <div class="form-container-box">
                <h1 class="form-header-title" style="font-size: 1.5rem;">
                    <i class="fa-solid fa-star text-gold"></i> Thiết Lập Điểm Thưởng Giải Con
                </h1>
                <p class="text-muted" style="margin-bottom: 1rem; font-size: 0.82rem;">
                    Bước 4 / 5: Dựa trên số đội (<strong><%= nTeams %> Đội</strong>), Cấp độ <strong>Tier <%= tierName %></strong> và Thể thức <strong><%= tourneyFormat %></strong>, các vị trí bên dưới đã được sinh ra tự động. Bạn vui lòng **tự nhập tay số điểm thưởng** cho từng vị trí:
                </p>

                <!-- RULE NOTICE ALERT BOX -->
                <div style="background: rgba(245, 158, 11, 0.08); border: 1px solid rgba(245, 158, 11, 0.3); border-radius: 10px; padding: 0.75rem 1rem; margin-bottom: 1.25rem; font-size: 0.78rem; color: #fef3c7; line-height: 1.5;">
                    <div style="font-weight: 800; color: #fbbf24; margin-bottom: 0.25rem; display: flex; align-items: center; gap: 0.4rem;">
                        <i class="fa-solid fa-circle-info"></i> Quy Tắc Tính Điểm Chuỗi (Series Rules):
                    </div>
                    • <strong>Ô để trống</strong> sẽ được tính là <strong>0 điểm</strong>.<br>
                    • <strong>Đội thêm ngoài (không phải Partner của Series)</strong> sẽ <strong>KHÔNG ĐƯỢC CỘNG ĐIỂM</strong> vào Bảng Xếp Hạng Series dù đạt bất kỳ thành tích nào.
                </div>

                <form id="pointConfigForm" action="${pageContext.request.contextPath}/rolling/point-config" method="POST">
                    <input type="hidden" name="tournamentId" value="<%= tourneyId %>">
                    <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">

                    <!-- DYNAMICALLY GENERATED POSITION / ROUND POINT INPUTS -->
                    <div style="background: rgba(11, 13, 18, 0.6); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 12px; padding: 1.25rem; margin-bottom: 1.5rem;">
                        <h4 style="font-size: 0.95rem; font-weight: 800; color: #ffffff; margin: 0 0 1rem 0; display: flex; align-items: center; justify-content: space-between;">
                            <span><i class="fa-solid fa-pen-to-square text-mint"></i> Nhập Điểm Tích Lũy Theo Vòng Đấu</span>
                            <span style="font-size: 0.78rem; font-weight: 700; color: #fbbf24; background: rgba(251, 191, 36, 0.1); padding: 0.2rem 0.6rem; border-radius: 6px;">
                                Tier <%= tierName %>
                            </span>
                        </h4>

                        <div style="display: flex; flex-direction: column; gap: 0.85rem;">
                            <% for (int i = 0; i < posList.size(); i++) {
                                PositionItem item = posList.get(i);
                                if (i == 0) {
                                    String champVal = "";
                                    if (savedChampPoints != null && savedChampPoints >= 0) {
                                        champVal = String.valueOf(savedChampPoints);
                                    } else if (savedPointsMap.containsKey("1")) {
                                        champVal = String.valueOf(savedPointsMap.get("1"));
                                    }
                            %>
                                <!-- 1st Place / Champion Champion Points -->
                                <div style="display: flex; align-items: center; justify-content: space-between; background: rgba(251, 191, 36, 0.08); border: 1px solid rgba(251, 191, 36, 0.25); padding: 0.75rem 1rem; border-radius: 8px;">
                                    <div>
                                        <div style="font-weight: 800; color: #fbbf24; font-size: 0.9rem;">
                                            <i class="fa-solid fa-trophy" style="margin-right: 0.35rem;"></i> <%= item.label %>
                                        </div>
                                        <div style="font-size: 0.75rem; color: #94a3b8; margin-top: 0.1rem;">Điểm thưởng chính của Quán Quân giải con</div>
                                    </div>
                                    <div style="display: flex; align-items: center; gap: 0.35rem;">
                                        <input type="number" name="champPoints" value="<%= champVal %>" placeholder="0" min="0" class="form-control" style="width: 110px; font-size: 0.95rem; font-weight: 800; color: #fbbf24; text-align: right; border-color: rgba(251, 191, 36, 0.4);">
                                        <span style="font-size: 0.8rem; font-weight: 700; color: #fbbf24;">pts</span>
                                    </div>
                                </div>
                            <% } else {
                                String posVal = "";
                                if (savedPointsMap.containsKey(item.key)) {
                                    posVal = String.valueOf(savedPointsMap.get(item.key));
                                }
                            %>
                                <!-- Sub Rounds / Finishing Positions -->
                                <div style="display: flex; align-items: center; justify-content: space-between; background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.08); padding: 0.65rem 1rem; border-radius: 8px;">
                                    <div style="font-weight: 700; color: #f8fafc; font-size: 0.85rem;">
                                        <%= item.label %>
                                    </div>
                                    <div style="display: flex; align-items: center; gap: 0.35rem;">
                                        <input type="number" name="point_pos_<%= item.key %>" value="<%= posVal %>" placeholder="0" min="0" class="form-control" style="width: 100px; font-size: 0.88rem; font-weight: 700; text-align: right;">
                                        <span style="font-size: 0.78rem; font-weight: 600; color: #94a3b8;">pts</span>
                                    </div>
                                </div>
                            <% } 
                            } %>
                        </div>
                    </div>

                    <!-- SUBMIT NAVIGATION BUTTONS -->
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 1.75rem;">
                        <a href="${pageContext.request.contextPath}/rolling/tournament-teams?id=<%= tourneyId %>&seriesId=<%= seriesIdVal %>" class="btn btn-secondary" style="font-weight: 700;">
                            <i class="fa-solid fa-arrow-left"></i> Quay Lại Bước 3
                        </a>
                        <button type="submit" class="btn btn-mint" style="font-weight: 800; padding: 0.7rem 1.75rem; border-radius: 10px;">
                            <i class="fa-solid fa-check"></i> LƯU & HOÀN TẤT GIẢI CON ➔
                        </button>
                    </div>

                </form>
            </div>
        </main>

        <script>
            document.getElementById('pointConfigForm').addEventListener('submit', function () {
                var tourneyId = '<%= tourneyId %>';
                if (!tourneyId) return;

                var config = {};
                var inputs = this.querySelectorAll('input[type="number"]');
                inputs.forEach(function (inp) {
                    var name = inp.name;
                    var val = parseInt(inp.value, 10) || 0;
                    if (name === 'champPoints') {
                        config["1"] = val;
                    } else if (name.startsWith('point_pos_')) {
                        var key = name.replace('point_pos_', '');
                        config[key] = val;
                    }
                });

                try {
                    localStorage.setItem('tourma_points_config_' + tourneyId, JSON.stringify(config));
                } catch (e) {}
            });
        </script>
    </body>
</html>
