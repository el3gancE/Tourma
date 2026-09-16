<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, model.SeriesStanding, model.PartnerParticipant, java.util.List"%>
<%
    Series series = (Series) request.getAttribute("series");
    List<Tournament> tournamentsList = (List<Tournament>) request.getAttribute("tournamentsList");
    List<SeriesStanding> standingsList = (List<SeriesStanding>) request.getAttribute("standingsList");
    List<PartnerParticipant> partnerList = (List<PartnerParticipant>) request.getAttribute("partnerList");
    int partnerCount = (partnerList != null) ? partnerList.size() : 0;

    String seriesIdVal = (series != null && series.getId() != null) ? series.getId() : "";
    String seriesName = (series != null) ? series.getName() : "Series Circuit";
    int phaseSize = (series != null && series.getPhaseSize() > 0) ? series.getPhaseSize() : 10;
    String status = (series != null && series.getStatus() != null) ? series.getStatus() : "ACTIVE";

    int tourneyCount = (tournamentsList != null) ? tournamentsList.size() : 0;
    int totalRankedTeams = (standingsList != null) ? standingsList.size() : 0;

    String top1Team = (totalRankedTeams > 0 && standingsList.get(0).getNormalizedTeamName() != null) ? standingsList.get(0).getNormalizedTeamName() : "Đang cập nhật";
    int top1Pts = (totalRankedTeams > 0) ? standingsList.get(0).getTotalRollingPoints() : 0;

    String top2Team = (totalRankedTeams > 1 && standingsList.get(1).getNormalizedTeamName() != null) ? standingsList.get(1).getNormalizedTeamName() : "Đang cập nhật";
    int top2Pts = (totalRankedTeams > 1) ? standingsList.get(1).getTotalRollingPoints() : 0;

    String top3Team = (totalRankedTeams > 2 && standingsList.get(2).getNormalizedTeamName() != null) ? standingsList.get(2).getNormalizedTeamName() : "Đang cập nhật";
    int top3Pts = (totalRankedTeams > 2) ? standingsList.get(2).getTotalRollingPoints() : 0;
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title><%= seriesName %> - Dashboard</title>
        
        <!-- Google Fonts & FontAwesome -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800;900&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Design System CSS, Team Badges CSS & Dedicated Rolling Dashboard CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/team-badges.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-dashboard.css?v=<%= System.currentTimeMillis() %>">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/add-team-popup.css">
    </head>
    <body>
        <!-- Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-series"/>
        </jsp:include>

        <!-- Dynamic 3-Mode Sidebar Component -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
            <jsp:param name="activeStep" value="dashboard"/>
        </jsp:include>

        <main class="container has-sidebar rolling-dashboard-wrapper">
            
            <!-- Breadcrumb Navigation -->
            <div class="dashboard-breadcrumb">
                <a href="${pageContext.request.contextPath}/my-series">
                    <i class="fa-solid fa-arrow-left"></i> Quay lại Chuỗi Giải Của Tôi
                </a>
            </div>

            <!-- Series Title & Create Sub-Tournament CTA Row -->
            <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem; margin-bottom: 1.25rem;">
                <h1 class="series-main-title" style="margin: 0;"><%= seriesName %></h1>
                <a href="${pageContext.request.contextPath}/rolling/create-tournament?seriesId=<%= seriesIdVal %>" class="btn-create-tourney-cta">
                    <i class="fa-solid fa-plus"></i> Tạo Giải Con Mới
                </a>
            </div>

            <!-- Dashboard Overview Section Subheader -->
            <div class="dashboard-overview-header">
                <h2>Dashboard Tổng Quan Chuỗi Giải</h2>
                <p>Tổng hợp các chỉ số quan trọng và trạng thái điều hành của Chuỗi Giải Đấu.</p>
            </div>

            <!-- Top 4 KPI Metrics Grid -->
            <div class="dashboard-kpi-grid">
                
                <!-- KPI Card 1: Đội Đối Tác -->
                <div class="dashboard-kpi-card">
                    <div>
                        <div class="kpi-label">ĐỘI ĐỐI TÁC (PARTNER TEAMS)</div>
                        <div class="kpi-value-row">
                            <div class="kpi-main-val">
                                <span class="kpi-large-num"><%= (partnerCount > 0 ? partnerCount : totalRankedTeams) %></span>
                                <span class="kpi-unit">Đội</span>
                            </div>
                            <div class="kpi-icon-box gold">
                                <i class="fa-solid fa-users"></i>
                            </div>
                        </div>
                    </div>
                    <div>
                        <a href="${pageContext.request.contextPath}/rolling/team-list?seriesId=<%= seriesIdVal %>" class="kpi-bottom-link">
                            Xem danh sách đội <i class="fa-solid fa-arrow-right-long"></i>
                        </a>
                    </div>
                </div>

                <!-- KPI Card 2: Số Giải Đấu Đã Diễn Ra -->
                <div class="dashboard-kpi-card">
                    <div>
                        <div class="kpi-label">SỐ GIẢI ĐẤU ĐÃ DIỄN RA</div>
                        <div class="kpi-value-row">
                            <div class="kpi-main-val">
                                <span class="kpi-large-num"><%= tourneyCount %></span>
                                <span class="kpi-sub-num"> / <%= tourneyCount %></span>
                                <span class="kpi-unit">Giải</span>
                            </div>
                            <div class="kpi-icon-box gold">
                                <i class="fa-solid fa-trophy"></i>
                            </div>
                        </div>
                    </div>
                    <div>
                        <a href="${pageContext.request.contextPath}/rolling/tournament-list?seriesId=<%= seriesIdVal %>" class="kpi-bottom-link">
                            Xem các giải con <i class="fa-solid fa-arrow-right-long"></i>
                        </a>
                    </div>
                </div>

                <!-- KPI Card 3: Đội Dẫn Đầu Hiện Tại -->
                <div class="dashboard-kpi-card">
                    <div>
                        <div class="kpi-label">ĐỘI DẪN ĐẦU HIỆN TẠI</div>
                        <div class="kpi-value-row">
                            <div class="kpi-main-val">
                                <div class="kpi-top-team-name" title="<%= top1Team %>"><%= top1Team %></div>
                            </div>
                            <div class="kpi-icon-box gold">
                                <i class="fa-solid fa-crown"></i>
                            </div>
                        </div>
                    </div>
                    <div>
                        <a href="${pageContext.request.contextPath}/rolling/standings?seriesId=<%= seriesIdVal %>" class="kpi-bottom-link gold-link">
                            <%= top1Pts %> Points <i class="fa-solid fa-arrow-right-long"></i>
                        </a>
                    </div>
                </div>

                <!-- KPI Card 4: Số Giải Được Tính Điểm -->
                <div class="dashboard-kpi-card">
                    <div>
                        <div class="kpi-label">SỐ GIẢI ĐƯỢC TÍNH ĐIỂM</div>
                        <div class="kpi-value-row">
                            <div class="kpi-main-val">
                                <span class="kpi-large-num cyan"><%= phaseSize %></span>
                                <span class="kpi-unit">Giải mới nhất</span>
                            </div>
                            <div class="kpi-icon-box cyan" onclick="openEditSeriesPopup()" style="cursor: pointer;" title="Bấm để chỉnh sửa cửa sổ trượt W">
                                <i class="fa-solid fa-clock-rotate-left"></i>
                            </div>
                        </div>
                    </div>
                    <div class="kpi-subtext">
                        Từ giải thứ <%= (phaseSize + 1) %> trở về trước bắt đầu trừ điểm
                    </div>
                </div>

            </div>

            <!-- Bottom 2-Column Main Section -->
            <div class="dashboard-main-columns">
                
                <!-- Left Panel: Top Dẫn Đầu Chuỗi -->
                <div class="dashboard-section-card">
                    <div class="section-header-bar">
                        <div class="section-title">
                            <i class="fa-solid fa-crown text-gold"></i> Top Dẫn Đầu Chuỗi
                        </div>
                        <a href="${pageContext.request.contextPath}/rolling/standings?seriesId=<%= seriesIdVal %>" class="btn-view-full-table">
                            Xem Bảng Đầy Đủ <i class="fa-solid fa-arrow-right-long"></i>
                        </a>
                    </div>

                    <div class="top-rank-list">
                        <% if (totalRankedTeams > 0) {
                            for (int i = 0; i < Math.min(totalRankedTeams, 3); i++) {
                                SeriesStanding st = standingsList.get(i);
                                int rank = i + 1;
                                String rankClass = "rank-" + rank;
                                String tName = (st.getNormalizedTeamName() != null) ? st.getNormalizedTeamName() : "Đội #" + rank;
                                int pts = st.getTotalRollingPoints();
                        %>
                            <div class="top-rank-item">
                                <div class="rank-item-left">
                                    <span class="rank-badge-pill <%= rankClass %>">#<%= rank %></span>
                                    <a href="${pageContext.request.contextPath}/team-profile?seriesId=<%= seriesIdVal %>&teamName=<%= java.net.URLEncoder.encode(tName, "UTF-8") %>" class="rank-team-name" title="<%= tName %>">
                                        <%= tName %>
                                    </a>
                                </div>
                                <span class="rank-team-pts"><%= pts %> pts</span>
                            </div>
                        <% } 
                        } else { %>
                            <div style="text-align: center; padding: 2.5rem 1rem; color: #94a3b8;">
                                <i class="fa-solid fa-trophy" style="font-size: 2rem; color: #fbbf24; opacity: 0.45; margin-bottom: 0.65rem; display: block;"></i>
                                <span style="font-size: 0.88rem; font-weight: 600;">Chưa có dữ liệu bảng xếp hạng</span>
                                <p style="font-size: 0.78rem; margin-top: 0.25rem; color: #64748b;">Hãy tạo giải con và bắt đầu thi đấu để ghi nhận điểm tích lũy.</p>
                            </div>
                        <% } %>
                    </div>
                </div>

                <!-- Right Panel: Danh Sách Tag Danh Hiệu (Showcase Tất Cả Danh Hiệu Đã Xây Dựng) -->
                <div class="dashboard-section-card">
                    <div class="section-header-bar">
                        <div class="section-title">
                            <i class="fa-solid fa-tag text-gold"></i> Danh Sách Tag Danh Hiệu
                        </div>
                        <span class="panel-hint-text">
                            <i class="fa-solid fa-circle-info"></i> Rê chuột xem mô tả
                        </span>
                    </div>

                    <!-- Showcase of all 9 implemented badges in exact profile format -->
                    <div class="tag-showcase-grid team-profile-badges-top" style="justify-content: flex-start; gap: 0.65rem;">
                        
                        <!-- 1. Defending Champion -->
                        <div class="tourma-badge-pill tourma-badge-defending" tabindex="0">
                            <svg class="tourma-badge-svg-border" aria-hidden="true">
                                <defs>
                                    <linearGradient id="defendingBeamGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                                        <stop offset="0%" stop-color="#facc15" stop-opacity="0.1" />
                                        <stop offset="40%" stop-color="#fde047" stop-opacity="0.7" />
                                        <stop offset="85%" stop-color="#ffffff" stop-opacity="1" />
                                        <stop offset="100%" stop-color="#ffffff" stop-opacity="1" />
                                    </linearGradient>
                                </defs>
                                <rect x="0.75" y="0.75" width="calc(100% - 1.5px)" height="calc(100% - 1.5px)" rx="13.25" ry="13.25" fill="none" stroke="url(#defendingBeamGrad)" stroke-width="1.5" stroke-linecap="round" pathLength="100" class="defending-beam-stroke" />
                            </svg>
                            <i class="fa-solid fa-shield-halved badge-icon"></i>
                            <span class="tourma-badge-name">Defending Champion</span>
                            <div class="tourma-badge-tooltip">
                                <div class="tourma-badge-tooltip-header">
                                    <span class="tourma-badge-tooltip-title"><i class="fa-solid fa-shield-halved"></i> Defending Champion</span>
                                    <span class="tourma-badge-tooltip-rarity">Đương Kim</span>
                                </div>
                                <div class="tourma-badge-tooltip-desc">Nhà vô địch của giải đấu gần nhất trong chuỗi giải.</div>
                                <div class="tourma-badge-tooltip-footer"><i class="fa-solid fa-shield-halved"></i> Vô địch giải gần nhất</div>
                            </div>
                        </div>

                        <!-- 2. Inaugural Champion -->
                        <div class="tourma-badge-pill tourma-badge-gold" tabindex="0">
                            <i class="fa-solid fa-crown badge-icon"></i>
                            <span class="tourma-badge-name">Inaugural Champion</span>
                            <div class="tourma-badge-tooltip">
                                <div class="tourma-badge-tooltip-header">
                                    <span class="tourma-badge-tooltip-title"><i class="fa-solid fa-crown"></i> Inaugural Champion</span>
                                    <span class="tourma-badge-tooltip-rarity">Huyền Thoại</span>
                                </div>
                                <div class="tourma-badge-tooltip-desc">Nhà vô địch giải đấu đầu tiên trong lịch sử chuỗi giải.</div>
                                <div class="tourma-badge-tooltip-footer"><i class="fa-solid fa-trophy"></i> Vô địch giải mở màn</div>
                            </div>
                        </div>

                        <!-- 3. Back-2-Back -->
                        <div class="tourma-badge-pill tourma-badge-streak" tabindex="0">
                            <i class="fa-solid fa-fire badge-icon"></i>
                            <span class="tourma-badge-name">Back-2-Back</span>
                            <div class="tourma-badge-tooltip">
                                <div class="tourma-badge-tooltip-header">
                                    <span class="tourma-badge-tooltip-title"><i class="fa-solid fa-fire"></i> Back-2-Back</span>
                                    <span class="tourma-badge-tooltip-rarity">Đặc Biệt</span>
                                </div>
                                <div class="tourma-badge-tooltip-desc">Vô địch 2 giải đấu liên tiếp trong chuỗi giải.</div>
                                <div class="tourma-badge-tooltip-footer"><i class="fa-solid fa-fire"></i> Vô địch 2 giải liên tiếp</div>
                            </div>
                        </div>

                        <!-- 4. Winning Streak -->
                        <div class="tourma-badge-pill tourma-badge-streak-fire" tabindex="0">
                            <i class="fa-solid fa-fire-flame-curved badge-icon"></i>
                            <span class="tourma-badge-name">Winning Streak</span>
                            <div class="tourma-badge-tooltip">
                                <div class="tourma-badge-tooltip-header">
                                    <span class="tourma-badge-tooltip-title"><i class="fa-solid fa-fire-flame-curved"></i> Winning Streak</span>
                                    <span class="tourma-badge-tooltip-rarity">Huyền Thoại</span>
                                </div>
                                <div class="tourma-badge-tooltip-desc">Vô địch từ 3 giải đấu liên tiếp trở lên trong chuỗi giải.</div>
                                <div class="tourma-badge-tooltip-footer"><i class="fa-solid fa-fire-flame-curved"></i> Vô địch 3+ giải liên tiếp</div>
                            </div>
                        </div>

                        <!-- 5. S-Tier Winner -->
                        <div class="tourma-badge-pill tourma-badge-tier-s" tabindex="0">
                            <i class="fa-solid fa-trophy badge-icon"></i>
                            <span class="tourma-badge-name">S-Tier Winner</span>
                            <div class="tourma-badge-tooltip">
                                <div class="tourma-badge-tooltip-header">
                                    <span class="tourma-badge-tooltip-title"><i class="fa-solid fa-trophy"></i> S-Tier Winner</span>
                                    <span class="tourma-badge-tooltip-rarity">Tier S</span>
                                </div>
                                <div class="tourma-badge-tooltip-desc">Đội đã đạt tối thiểu 1 lần vô địch các giải đấu Cấp độ S (Tier S).</div>
                                <div class="tourma-badge-tooltip-footer"><i class="fa-solid fa-award"></i> Danh hiệu vô địch Tier S</div>
                            </div>
                        </div>

                        <!-- 6. A-Tier Winner -->
                        <div class="tourma-badge-pill tourma-badge-tier-a" tabindex="0">
                            <i class="fa-solid fa-trophy badge-icon"></i>
                            <span class="tourma-badge-name">A-Tier Winner</span>
                            <div class="tourma-badge-tooltip">
                                <div class="tourma-badge-tooltip-header">
                                    <span class="tourma-badge-tooltip-title"><i class="fa-solid fa-trophy"></i> A-Tier Winner</span>
                                    <span class="tourma-badge-tooltip-rarity">Tier A</span>
                                </div>
                                <div class="tourma-badge-tooltip-desc">Đội đã đạt từ 2 lần vô địch các giải đấu Cấp độ A (Tier A) trở lên.</div>
                                <div class="tourma-badge-tooltip-footer"><i class="fa-solid fa-award"></i> Danh hiệu vô địch Tier A</div>
                            </div>
                        </div>

                        <!-- 7. B-Tier Winner -->
                        <div class="tourma-badge-pill tourma-badge-tier-b" tabindex="0">
                            <i class="fa-solid fa-trophy badge-icon"></i>
                            <span class="tourma-badge-name">B-Tier Winner</span>
                            <div class="tourma-badge-tooltip">
                                <div class="tourma-badge-tooltip-header">
                                    <span class="tourma-badge-tooltip-title"><i class="fa-solid fa-trophy"></i> B-Tier Winner</span>
                                    <span class="tourma-badge-tooltip-rarity">Tier B</span>
                                </div>
                                <div class="tourma-badge-tooltip-desc">Đội đã đạt từ 3 lần vô địch các giải đấu Cấp độ B (Tier B) trở lên.</div>
                                <div class="tourma-badge-tooltip-footer"><i class="fa-solid fa-award"></i> Danh hiệu vô địch Tier B</div>
                            </div>
                        </div>

                        <!-- 8. C-Tier Winner -->
                        <div class="tourma-badge-pill tourma-badge-tier-c" tabindex="0">
                            <i class="fa-solid fa-trophy badge-icon"></i>
                            <span class="tourma-badge-name">C-Tier Winner</span>
                            <div class="tourma-badge-tooltip">
                                <div class="tourma-badge-tooltip-header">
                                    <span class="tourma-badge-tooltip-title"><i class="fa-solid fa-trophy"></i> C-Tier Winner</span>
                                    <span class="tourma-badge-tooltip-rarity">Tier C</span>
                                </div>
                                <div class="tourma-badge-tooltip-desc">Đội đã đạt từ 4 lần vô địch các giải đấu Cấp độ C (Tier C) trở lên.</div>
                                <div class="tourma-badge-tooltip-footer"><i class="fa-solid fa-award"></i> Danh hiệu vô địch Tier C</div>
                            </div>
                        </div>

                        <!-- 9. D-Tier Winner -->
                        <div class="tourma-badge-pill tourma-badge-tier-d" tabindex="0">
                            <i class="fa-solid fa-trophy badge-icon"></i>
                            <span class="tourma-badge-name">D-Tier Winner</span>
                            <div class="tourma-badge-tooltip">
                                <div class="tourma-badge-tooltip-header">
                                    <span class="tourma-badge-tooltip-title"><i class="fa-solid fa-trophy"></i> D-Tier Winner</span>
                                    <span class="tourma-badge-tooltip-rarity">Tier D</span>
                                </div>
                                <div class="tourma-badge-tooltip-desc">Đội đã đạt từ 5 lần vô địch các giải đấu Cấp độ D (Tier D) trở lên.</div>
                                <div class="tourma-badge-tooltip-footer"><i class="fa-solid fa-award"></i> Danh hiệu vô địch Tier D</div>
                            </div>
                        </div>

                    </div>
                </div>

            </div>

        </main>

        <!-- Reusable Edit Series Popup Component -->
        <jsp:include page="/common/component/edit-series-popup.jsp">
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
            <jsp:param name="formActionUrl" value="${pageContext.request.contextPath}/rolling/dashboard"/>
        </jsp:include>

        <!-- Rolling Dashboard Interactive Script -->
        <script src="${pageContext.request.contextPath}/js/rolling/rolling-dashboard.js?v=<%= System.currentTimeMillis() %>"></script>
    </body>
</html>
