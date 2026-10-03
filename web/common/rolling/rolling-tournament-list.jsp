<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, dao.SeriesDAO, dao.TournamentDAO, dao.ParticipantDAO, model.Team, java.util.List, java.util.Map, java.util.HashMap"%>
<%
    String seriesIdVal = request.getParameter("id");
    if (seriesIdVal == null || seriesIdVal.trim().isEmpty()) {
        seriesIdVal = request.getParameter("seriesId");
    }

    SeriesDAO seriesDAO = new SeriesDAO();
    TournamentDAO tournamentDAO = new TournamentDAO();
    ParticipantDAO participantDAO = new ParticipantDAO();

    String actionParam = request.getParameter("action");
    String delTourneyId = request.getParameter("tournamentId");
    if ("POST".equalsIgnoreCase(request.getMethod()) && "delete".equalsIgnoreCase(actionParam) && delTourneyId != null) {
        tournamentDAO.deleteTournament(delTourneyId.trim());
        if (seriesIdVal != null && !seriesIdVal.trim().isEmpty()) {
            service.RollingWindowPointService.getInstance().recalculateAndPersistStandings(seriesIdVal.trim());
        }
    }

    Series series = (Series) request.getAttribute("series");
    if (series == null && seriesIdVal != null && !seriesIdVal.trim().isEmpty()) {
        series = seriesDAO.getSeriesById(seriesIdVal.trim());
    }

    if (series == null) {
        List<Series> allSeries = seriesDAO.getAllSeries();
        for (Series s : allSeries) {
            if ("ROLLING_WINDOW".equalsIgnoreCase(s.getRankingModel())) {
                series = s;
                break;
            }
        }
    }

    List<Tournament> tournamentsList = (List<Tournament>) request.getAttribute("tournamentsList");
    if (tournamentsList == null && series != null) {
        tournamentsList = seriesDAO.getTournamentsBySeriesId(series.getId());
    }

    Map<String, Integer> teamCountMap = (Map<String, Integer>) request.getAttribute("teamCountMap");
    if (teamCountMap == null) {
        teamCountMap = new HashMap<>();
        if (tournamentsList != null) {
            for (Tournament t : tournamentsList) {
                List<Team> teams = participantDAO.getTeamsByTournamentId(t.getId());
                int count = (teams != null) ? teams.size() : 0;
                teamCountMap.put(t.getId(), count);
            }
        }
    }

    Map<String, List<String>> stageFormatsMap = (Map<String, List<String>>) request.getAttribute("stageFormatsMap");
    if (stageFormatsMap == null) {
        stageFormatsMap = new HashMap<>();
        if (tournamentsList != null) {
            for (Tournament t : tournamentsList) {
                stageFormatsMap.put(t.getId(), tournamentDAO.getStageFormats(t.getId()));
            }
        }
    }

    if (seriesIdVal == null && series != null) {
        seriesIdVal = series.getId();
    }
    String seriesName = (series != null) ? series.getName() : "Series Circuit";
    int tourneyCount = (tournamentsList != null) ? tournamentsList.size() : 0;
%>
<%!
    private String getFmtName(String fmt) {
        if (fmt == null) return "Single Elimination";
        String f = fmt.toUpperCase();
        if ("DOUBLE_ELIMINATION".equals(f)) return "Double Elimination";
        if ("ROUND_ROBIN".equals(f)) return "Round Robin";
        if ("GROUP_STAGE".equals(f)) return "Group Stage";
        if ("SWISS_LITE".equals(f) || "SWISS".equals(f)) return "Swiss System";
        return "Single Elimination";
    }
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Danh Sách Giải Con - <%= seriesName %></title>
        
        <!-- Google Fonts & FontAwesome -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Design System CSS & Dedicated CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/my-tournaments.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-tournament-list.css">
    </head>
    <body>
        <!-- Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-series"/>
        </jsp:include>

        <!-- Dynamic 3-Mode Sidebar Component -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
            <jsp:param name="activeStep" value="tournament-list"/>
        </jsp:include>

        <main class="container has-sidebar" style="max-width: 950px; padding: 1.5rem 1rem;">
            
            <!-- Breadcrumb Navigation -->
            <div style="margin-bottom: 1rem;">
                <a href="${pageContext.request.contextPath}/rolling/dashboard?id=<%= seriesIdVal %>" class="text-muted" style="font-size: 0.85rem; font-weight: 600; text-decoration: none;">
                    <i class="fa-solid fa-arrow-left"></i> Quay lại Dashboard Series
                </a>
            </div>

            <!-- Series Header Banner Card -->
            <div class="team-list-header-card" style="margin-bottom: 1.5rem;">
                <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
                    <div>
                        <h1 class="rolling-series-name" style="font-size: 1.85rem; font-weight: 800; color: #ffffff; margin: 0 0 0.25rem 0;">
                            <i class="fa-solid fa-trophy text-gold"></i> Danh Sách Giải Con
                        </h1>
                        <p class="text-muted" style="font-size: 0.85rem; margin-top: 0.25rem;">
                            Quản lý tất cả các giải đấu con thuộc Series <strong><%= seriesName %></strong> (<%= tourneyCount %> Giải Con)
                        </p>
                    </div>
                    <div>
                        <a href="${pageContext.request.contextPath}/rolling/create-tournament?seriesId=<%= seriesIdVal %>" class="btn btn-mint" style="font-weight: 800; font-size: 0.9rem; padding: 0.65rem 1.25rem; border-radius: 10px; text-decoration: none;">
                            <i class="fa-solid fa-plus-circle"></i> + Thêm Giải Con Mới
                        </a>
                    </div>
                </div>
            </div>

            <!-- Search Toolbar Row -->
            <div style="display: flex; justify-content: flex-end; align-items: center; margin-bottom: 1.25rem;">
                <div class="search-input-box">
                    <i class="fa-solid fa-magnifying-glass"></i>
                    <input type="text" id="searchInput" class="form-control" placeholder="Tìm kiếm tên giải con..." onkeyup="filterTournaments(this.value)">
                </div>
            </div>

            <!-- Tournament Cards Grid -->
            <div class="tourney-cards-grid" id="tourneyCardList">
                <% if (tournamentsList != null && !tournamentsList.isEmpty()) { 
                    // Render newest sub-tournament first by iterating backwards without mutating the list
                    for (int i = tournamentsList.size() - 1; i >= 0; i--) {
                        Tournament t = tournamentsList.get(i);
                        int nTeams = teamCountMap.containsKey(t.getId()) ? teamCountMap.get(t.getId()) : 0;
                        String tierName = (t.getTierName() != null) ? t.getTierName().toUpperCase() : "S";
                        String fmt = (t.getFormat() != null) ? t.getFormat().toUpperCase() : "SINGLE_ELIMINATION";
                        
                        boolean isMulti = "MULTI_STAGE".equalsIgnoreCase(t.getTournamentType());
                        List<String> stgFormats = (stageFormatsMap != null) ? stageFormatsMap.get(t.getId()) : null;
                        String s1Fmt = (stgFormats != null && !stgFormats.isEmpty()) ? stgFormats.get(0) : fmt;
                        String s2Fmt = (stgFormats != null && stgFormats.size() > 1) ? stgFormats.get(1) : "SINGLE_ELIMINATION";

                        String s1Label = getFmtName(s1Fmt);
                        String s2Label = getFmtName(s2Fmt);
                        String fmtLabel = isMulti ? (s1Label + " ➔ " + s2Label) : s1Label;

                        String bracketUrl = "/common/single-elimination.jsp";
                        if ("DOUBLE_ELIMINATION".equals(s1Fmt)) bracketUrl = "/common/double-elimination.jsp";
                        else if ("ROUND_ROBIN".equals(s1Fmt)) bracketUrl = "/common/round-robin.jsp";
                        else if ("GROUP_STAGE".equals(s1Fmt)) bracketUrl = "/common/group-stage.jsp";
                        else if ("SWISS_LITE".equals(s1Fmt) || "SWISS".equals(s1Fmt)) bracketUrl = "/common/swiss-stage.jsp";

                        String championName = t.getChampionName();
                        boolean isFinished = (championName != null && !championName.trim().isEmpty()) || "COMPLETED".equalsIgnoreCase(t.getStatus());
                        String statusStr = isFinished ? "Completed" : ("ONGOING".equalsIgnoreCase(t.getStatus()) ? "In Progress" : "Incoming");
                        String statusClass = isFinished ? "completed" : ("ONGOING".equalsIgnoreCase(t.getStatus()) ? "in-progress" : "incoming");
                        String stageTypeLabel = isMulti ? "MULTI STAGE" : "SINGLE STAGE";
                %>
                    <div class="tourney-card" data-id="<%= t.getId() %>" data-stage-type="<%= isMulti ? "MULTI_STAGE" : "SINGLE_STAGE" %>" data-stage1-format="<%= s1Fmt %>" data-stage2-format="<%= s2Fmt %>" data-db-status="<%= t.getStatus() != null ? t.getStatus() : "" %>" data-db-champion="<%= championName != null ? championName.replace("\"", "&quot;") : "" %>" data-name="<%= t.getName() %>">
                        <div class="tourney-card-main">
                            <div class="tourney-card-top-row">
                                <h3 class="tourney-card-title" style="margin: 0; display: inline-flex; align-items: center;">
                                    <%= t.getName() %>
                                    <span class="tier-tag tier-<%= tierName.toLowerCase() %>" style="margin-left: 0.55rem; font-size: 0.95rem; font-weight: 800;">[<%= tierName %>]</span>
                                </h3>
                                <span class="tourney-badge-type"><%= stageTypeLabel %></span>
                                <span class="status-pill <%= statusClass %>">
                                    <%= statusStr %>
                                </span>
                            </div>

                            <div class="tourney-card-meta" style="margin-top: 0.4rem;">
                                <span class="tourney-teams-meta">
                                    <i class="fa-solid fa-users text-mint"></i> 
                                    <span class="team-count-val" style="color: #f8fafc; font-weight: 700;"><%= nTeams %> Đội</span>
                                </span>
                                <span class="meta-divider">•</span>
                                <span><i class="fa-solid fa-layer-group text-gold"></i> Thuộc Series</span>
                                <span class="meta-divider">•</span>
                                <span class="tourney-format-span">
                                    <i class="fa-solid fa-diagram-project text-mint"></i> <%= fmtLabel %>
                                </span>
                                <span id="championMeta_<%= t.getId() %>" class="tourney-champion-meta" style="<%= (isFinished) ? "display: inline-flex;" : "display: none;" %>">
                                    <span class="meta-divider">•</span>
                                    <i class="fa-solid fa-trophy text-gold"></i> Nhà vô địch: <span class="champion-name-val" style="color: #fbbf24;"><%= championName != null ? championName : "" %></span>
                                </span>
                            </div>
                        </div>

                        <div class="tourney-card-footer">
                            <div class="tourney-card-actions">
                                <button type="button" class="btn-delete-tourney" title="Xóa" onclick="openDeleteTourneyModal('<%= t.getId() %>', '<%= (t.getName() != null) ? t.getName().replace("'", "\\'").replace("\"", "&quot;") : "" %>')">
                                    <i class="fa-solid fa-trash-can"></i> Xóa
                                </button>
                                <a href="${pageContext.request.contextPath}/rolling/tournament-teams?id=<%= t.getId() %>&seriesId=<%= seriesIdVal %>" class="btn-details-tourney" title="Quản lý đội">
                                    <i class="fa-solid fa-users-gear"></i> QL Đội
                                </a>
                                <a href="${pageContext.request.contextPath}/rolling/point-config?id=<%= t.getId() %>&seriesId=<%= seriesIdVal %>" class="btn-details-tourney" title="Set điểm thưởng">
                                    <i class="fa-solid fa-star text-gold"></i> Set Điểm
                                </a>
                                <a href="${pageContext.request.contextPath}<%= bracketUrl %>?id=<%= t.getId() %>&seriesId=<%= seriesIdVal %>" class="btn-view-bracket-card">
                                    Trận Đấu ➔
                                </a>
                            </div>
                        </div>
                    </div>
                <% } 
                } else { %>
                        <div style="text-align: center; padding: 3rem; background: rgba(18, 22, 31, 0.7); border: 1px dashed rgba(255, 255, 255, 0.15); border-radius: 16px; color: #94a3b8;">
                            <i class="fa-solid fa-trophy" style="font-size: 2.5rem; margin-bottom: 0.75rem; color: #fbbf24; opacity: 0.5;"></i>
                            <h4 style="color: #ffffff; font-weight: 700; margin-bottom: 0.5rem;">Chưa có giải con nào trong Series này</h4>
                            <p style="font-size: 0.85rem; margin-bottom: 1.25rem;">Hãy bấm nút bên dưới để tạo giải đấu con đầu tiên cho Series.</p>
                            <a href="${pageContext.request.contextPath}/rolling/create-tournament?seriesId=<%= seriesIdVal %>" class="btn btn-mint" style="font-weight: 800; text-decoration: none;">
                                <i class="fa-solid fa-plus-circle"></i> + Tạo Giải Con Đầu Tiên
                            </a>
                        </div>
                    <% } %>
                </div>
            </div>

            <!-- DELETE SUB-TOURNAMENT CONFIRMATION MODAL -->
            <div id="deleteTourneyModalBackdrop" class="tourma-modal-backdrop" style="display: none;" onclick="if(event.target === this) closeDeleteTourneyModal();">
                <div class="tourma-modal-card" style="border-color: rgba(244, 63, 94, 0.4);" onclick="event.stopPropagation();">
                    <div class="modal-header-bar" style="border-bottom: 1px solid rgba(244, 63, 94, 0.2);">
                        <div class="modal-header-title" style="color: #f43f5e; font-size: 0.95rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem;">
                            <i class="fa-solid fa-trash-can"></i>
                            <span>Xác Nhận Xóa Giải Con</span>
                        </div>
                        <button type="button" class="modal-close-btn" onclick="closeDeleteTourneyModal()" title="Đóng">
                            <i class="fa-solid fa-xmark"></i>
                        </button>
                    </div>

                    <div class="modal-body-content" style="padding: 1.25rem 1rem;">
                        <div style="background: rgba(244, 63, 94, 0.08); border: 1px solid rgba(244, 63, 94, 0.2); border-radius: 8px; padding: 0.85rem; margin-bottom: 1rem; color: #cbd5e1; font-size: 0.82rem; line-height: 1.5;">
                            <strong style="color: #f43f5e;">⚠️ Cảnh báo xóa vĩnh viễn:</strong><br>
                            Hành động này sẽ <strong style="color: #ffffff;">xóa hoàn toàn giải đấu con này</strong> cùng toàn bộ danh sách đội tuyển, cấu hình thể thức, kết quả các trận đấu và tự động cập nhật lại bảng xếp hạng tích lũy Series.
                        </div>
                        <p style="color: #cbd5e1; font-size: 0.85rem; margin: 0 0 0.35rem 0;">
                            Bạn có chắc chắn muốn xóa giải con: <strong id="deleteTourneyTargetName" style="color: #f43f5e;"></strong> (ID: <span id="deleteTourneyTargetId" class="text-muted"></span>)?
                        </p>
                    </div>

                    <div class="modal-footer-bar" style="display: flex; justify-content: flex-end; gap: 0.65rem; padding: 0.85rem 1.25rem; border-top: 1px solid rgba(255, 255, 255, 0.08); background: rgba(0, 0, 0, 0.2);">
                        <button type="button" class="btn btn-secondary" onclick="closeDeleteTourneyModal()" style="font-size: 0.8rem; padding: 0.45rem 1rem;">Hủy Bỏ</button>
                        <button type="button" class="btn" style="background: #f43f5e; color: #ffffff; border: none; font-size: 0.8rem; font-weight: 700; padding: 0.45rem 1.25rem; border-radius: 6px; cursor: pointer;" onclick="confirmDeleteTourney()">
                            <i class="fa-solid fa-trash-can"></i> Xác Nhận Xóa
                        </button>
                    </div>
                </div>
            </div>

            <!-- CLONE SUB-TOURNAMENT CONFIRMATION MODAL -->
            <div id="cloneTourneyModalBackdrop" class="tourma-modal-backdrop" style="display: none;" onclick="if(event.target === this) closeCloneTourneyModal();">
                <div class="tourma-modal-card" style="border-color: rgba(45, 212, 191, 0.4); max-width: 490px;" onclick="event.stopPropagation();">
                    <div class="modal-header-bar" style="border-bottom: 1px solid rgba(45, 212, 191, 0.2);">
                        <div class="modal-header-title" style="color: #2dd4bf; font-size: 0.95rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem;">
                            <i class="fa-solid fa-copy"></i>
                            <span>Sao Chép Cấu Hình Giải Con</span>
                        </div>
                        <button type="button" class="modal-close-btn" onclick="closeCloneTourneyModal()" title="Đóng">
                            <i class="fa-solid fa-xmark"></i>
                        </button>
                    </div>

                    <form id="cloneTourneyForm" method="POST" action="${pageContext.request.contextPath}/clone-tournament">
                        <input type="hidden" name="sourceTournamentId" id="cloneSourceTournamentId" value="">
                        <input type="hidden" name="redirectContext" id="cloneRedirectContext" value="series">

                        <div class="modal-body-content" style="padding: 1.25rem 1rem;">
                            <div style="background: rgba(45, 212, 191, 0.08); border: 1px solid rgba(45, 212, 191, 0.2); border-radius: 8px; padding: 0.75rem 0.85rem; margin-bottom: 1rem; color: #cbd5e1; font-size: 0.82rem; line-height: 1.5;">
                                <i class="fa-solid fa-circle-info text-mint"></i> 
                                Hệ thống sẽ tạo một giải đấu con mới trong Series này ở trạng thái <strong>DRAFT</strong> với toàn bộ cấu hình thể thức, phân bảng, multi-stage từ giải gốc.
                            </div>

                            <div style="margin-bottom: 1rem;">
                                <label style="display: block; font-size: 0.78rem; font-weight: 700; color: #cbd5e1; margin-bottom: 0.35rem;">
                                    Tên giải đấu mới:
                                </label>
                                <input type="text" id="cloneNewTourneyName" name="newName" class="form-control" required style="width: 100%; font-size: 0.88rem; font-weight: 600; border-radius: 8px; box-sizing: border-box;" placeholder="Nhập tên giải mới...">
                            </div>

                            <div style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 8px; padding: 0.75rem 0.85rem; margin-bottom: 0.5rem; display: flex; align-items: flex-start; gap: 0.6rem;">
                                 <i class="fa-solid fa-users text-mint" style="margin-top: 0.15rem; font-size: 0.9rem;"></i>
                                 <div style="font-size: 0.8rem; color: #cbd5e1; line-height: 1.45;">
                                     <strong>Ràng buộc số lượng:</strong> Danh sách đội sẽ để trống để bạn nhập hoặc chọn từ Partner ở bước tiếp theo, và <strong>bắt buộc phải khớp đúng số lượng đội</strong> của giải gốc.
                                 </div>
                            </div>
                        </div>

                        <div class="modal-footer-bar" style="display: flex; justify-content: flex-end; gap: 0.65rem; padding: 0.85rem 1.25rem; border-top: 1px solid rgba(255, 255, 255, 0.08); background: rgba(0, 0, 0, 0.2);">
                            <button type="button" class="btn btn-secondary" onclick="closeCloneTourneyModal()" style="font-size: 0.8rem; padding: 0.45rem 1rem;">Hủy Bỏ</button>
                            <button type="submit" class="btn btn-mint" style="font-size: 0.8rem; font-weight: 700; padding: 0.45rem 1.25rem; border-radius: 6px; cursor: pointer; display: inline-flex; align-items: center; gap: 0.4rem;">
                                <i class="fa-solid fa-copy"></i> Tạo Bản Sao
                            </button>
                        </div>
                    </form>
                </div>
            </div>

        </main>

        <script>
            let pendingDeleteId = null;

            function openDeleteTourneyModal(id, name) {
                pendingDeleteId = id;
                var targetIdEl = document.getElementById('deleteTourneyTargetId');
                if (targetIdEl) targetIdEl.innerText = id;
                var targetNameEl = document.getElementById('deleteTourneyTargetName');
                if (targetNameEl) targetNameEl.innerText = name;
                var modal = document.getElementById('deleteTourneyModalBackdrop');
                if (modal) {
                    modal.style.display = 'flex';
                    document.body.style.overflow = 'hidden';
                }
            }

            function closeDeleteTourneyModal() {
                pendingDeleteId = null;
                var modal = document.getElementById('deleteTourneyModalBackdrop');
                if (modal) {
                    modal.style.display = 'none';
                    document.body.style.overflow = '';
                }
            }

            function confirmDeleteTourney() {
                if (!pendingDeleteId) return;
                var tid = pendingDeleteId;
                var seriesId = "<%= (seriesIdVal != null) ? seriesIdVal : "" %>";

                // 1. Clean localStorage for this sub-tournament
                try {
                    localStorage.removeItem("tourma_matches_" + tid);
                    localStorage.removeItem("tourma_de_matches_" + tid);
                    localStorage.removeItem("tourma_rr_matches_" + tid);
                    localStorage.removeItem("tourma_group_matches_" + tid);
                    localStorage.removeItem("tourma_bracket_" + tid);
                    localStorage.removeItem("tourma_bracket_stage2_" + tid);
                    localStorage.removeItem("tourma_teams_" + tid);
                    localStorage.removeItem("tourma_format_" + tid);
                    localStorage.removeItem("tourma_type_" + tid);
                    localStorage.removeItem("tourma_champion_" + tid);
                    localStorage.removeItem("tourma_final_champion_" + tid);
                    localStorage.removeItem("tourma_final_locked_" + tid);
                    localStorage.removeItem("tourma_group_assignments_" + tid);
                    localStorage.removeItem("tourma_multi_config_" + tid);
                    localStorage.removeItem("tourma_point_config_" + tid);
                } catch(e) {}

                // 2. Remove card from DOM instantly with smooth fade-out animation
                var card = document.querySelector('.tourney-card[data-id="' + tid + '"]');
                if (card) {
                    card.style.transition = 'all 0.3s ease';
                    card.style.opacity = '0';
                    card.style.transform = 'scale(0.9)';
                    setTimeout(function() {
                        if (card.parentNode) card.parentNode.removeChild(card);
                        var remaining = document.querySelectorAll('.tourney-card');
                        if (remaining.length === 0) {
                            window.location.reload();
                        }
                    }, 300);
                }

                closeDeleteTourneyModal();

                // 3. Send async delete request to backend in background
                fetch('${pageContext.request.contextPath}/delete-tournament?id=' + encodeURIComponent(tid) + '&seriesId=' + encodeURIComponent(seriesId), {
                    method: 'POST',
                    headers: {
                        'X-Requested-With': 'XMLHttpRequest',
                        'Accept': 'application/json'
                    }
                })
                .then(function() { console.log('Sub-tournament ' + tid + ' deleted on backend.'); })
                .catch(function(err) { console.warn('Backend delete fetch note:', err); });
            }

            function filterTournaments(query) {
                var q = (query || '').toLowerCase().trim();
                var cards = document.querySelectorAll('.tourney-card');
                cards.forEach(function (card) {
                    var name = card.getAttribute('data-name') || card.innerText;
                    if (!q || name.toLowerCase().includes(q)) {
                        card.style.display = 'flex';
                    } else {
                        card.style.display = 'none';
                    }
                });
            }

            function extractWinnerFromMatchesMap(matchesMap, isRealTeam) {
                if (!matchesMap || typeof matchesMap !== 'object') return null;

                // 1. Check Grand Final Reset / Grand Final
                var gfReset = matchesMap['GF_RESET'];
                if (gfReset) {
                    var wObj = gfReset.winner || ((gfReset.winnerId === 'team1') ? gfReset.team1 : ((gfReset.winnerId === 'team2') ? gfReset.team2 : null));
                    var cName = (typeof wObj === 'object') ? (wObj.name || wObj.rawName) : wObj;
                    if (isRealTeam(cName)) return String(cName).trim();
                }

                var gf = matchesMap['GF'];
                if (gf) {
                    var wObj = gf.winner || ((gf.winnerId === 'team1') ? gf.team1 : ((gf.winnerId === 'team2') ? gf.team2 : null));
                    var cName = (typeof wObj === 'object') ? (wObj.name || wObj.rawName) : wObj;
                    if (isRealTeam(cName)) return String(cName).trim();
                }

                // 2. Scan matches for highest round / final match
                var matchKeys = Object.keys(matchesMap);
                var maxRound = 0;
                matchKeys.forEach(function(k) {
                    var m = matchesMap[k];
                    if (m && !m.isThirdPlace) {
                        var r = (m.roundNumber !== undefined) ? Number(m.roundNumber) : ((m.roundIndex !== undefined) ? Number(m.roundIndex) + 1 : 0);
                        if (r > maxRound) maxRound = r;
                    }
                });

                var finalWinner = null;
                matchKeys.forEach(function(k) {
                    var m = matchesMap[k];
                    if (m && !m.isThirdPlace) {
                        var r = (m.roundNumber !== undefined) ? Number(m.roundNumber) : ((m.roundIndex !== undefined) ? Number(m.roundIndex) + 1 : 0);
                        if (m.isFinalMatch && (m.winner || m.winnerId)) {
                            var wObj = m.winner || ((m.winnerId === 'team1') ? m.team1 : ((m.winnerId === 'team2') ? m.team2 : null));
                            var n = (typeof wObj === 'object') ? (wObj.name || wObj.rawName) : wObj;
                            if (isRealTeam(n)) finalWinner = String(n).trim();
                        } else if (maxRound > 0 && r === maxRound && (m.winner || m.winnerId)) {
                            var wObj = m.winner || ((m.winnerId === 'team1') ? m.team1 : ((m.winnerId === 'team2') ? m.team2 : null));
                            var n = (typeof wObj === 'object') ? (wObj.name || wObj.rawName) : wObj;
                            if (isRealTeam(n)) finalWinner = String(n).trim();
                        }
                    }
                });
                return finalWinner;
            }

            function findChampionName(card) {
                var tid = card.getAttribute('data-id');
                var dbChamp = card.getAttribute('data-db-champion');
                var saved = localStorage.getItem("tourma_champion_" + tid) || localStorage.getItem("tourma_final_champion_" + tid);
                if (saved && saved.trim() !== "" && saved.trim() !== "BYE" && saved.trim() !== "TBD") return saved.trim();
                if (dbChamp && dbChamp.trim() !== "" && dbChamp.trim() !== "BYE" && dbChamp.trim() !== "TBD") return dbChamp.trim();

                var isRealTeam = function(n) {
                    if (!n) return false;
                    var s = String(n).trim();
                    return s !== "" && s !== "BYE" && s !== "TBD" && !s.startsWith("W #") && !s.startsWith("L #") && !s.startsWith("Winner ") && !s.startsWith("Loser ");
                };

                // 1. Stage 2 (Final Stage) match keys for multi-stage tournaments
                var stage2Keys = ["tourma_stage2_matches_", "tourma_matches_stage2_", "tourma_bracket_stage2_", "tourma_final_matches_"];
                for (var s = 0; s < stage2Keys.length; s++) {
                    try {
                        var raw2 = localStorage.getItem(stage2Keys[s] + tid);
                        if (!raw2) continue;
                        var d2 = JSON.parse(raw2);
                        var mMap2 = d2.matchesMap || d2;
                        if (!mMap2 || typeof mMap2 !== 'object') continue;
                        var c2 = extractWinnerFromMatchesMap(mMap2, isRealTeam);
                        if (c2) return c2;
                    } catch(e) {}
                }

                // 2. If tournament is MULTI_STAGE, NEVER read Stage 1 matches to determine tournament champion!
                var stageType = card.getAttribute('data-stage-type') || '';
                var localType = localStorage.getItem("tourma_type_" + tid);
                var multiConfigRaw = localStorage.getItem("tourma_multi_config_" + tid);
                if (stageType === 'MULTI_STAGE' || localType === 'MULTI_STAGE' || multiConfigRaw !== null) {
                    return "";
                }

                // 3. Single-stage tournament match keys
                var singleKeys = ["tourma_de_matches_", "tourma_matches_", "tourma_rr_matches_", "tourma_group_matches_", "tourma_swiss_matches_"];
                for (var i = 0; i < singleKeys.length; i++) {
                    try {
                        var raw = localStorage.getItem(singleKeys[i] + tid);
                        if (!raw) continue;
                        var data = JSON.parse(raw);
                        var matchesMap = data.matchesMap || data;
                        if (!matchesMap || typeof matchesMap !== 'object') continue;
                        var c = extractWinnerFromMatchesMap(matchesMap, isRealTeam);
                        if (c) return c;
                    } catch(e) {}
                }
                return "";
            }

            function getFormatDisplayName(fmt) {
                if (!fmt) return 'Single Elimination';
                var f = fmt.toUpperCase();
                if (f === 'DOUBLE_ELIMINATION') return 'Double Elimination';
                if (f === 'ROUND_ROBIN') return 'Round Robin';
                if (f === 'GROUP_STAGE') return 'Group Stage';
                if (f === 'SWISS_LITE' || f === 'SWISS') return 'Swiss System';
                return 'Single Elimination';
            }

            function updateCardStatuses() {
                var cards = document.querySelectorAll('.tourney-card');
                cards.forEach(function(card) {
                    var tid = card.getAttribute('data-id');
                    if (!tid) return;

                    var dbStatus = card.getAttribute('data-db-status');
                    var championName = findChampionName(card);
                    var isFinished = (championName && championName.trim() !== '') || dbStatus === 'COMPLETED' || (localStorage.getItem('tourma_stage2_locked_' + tid) === 'true' && championName && championName.trim() !== '');

                    var statusPill = card.querySelector('.status-pill');
                    var championMeta = card.querySelector('.tourney-champion-meta');

                    var hasMatchesPlayed = !!(
                        localStorage.getItem('tourma_bracket_' + tid) ||
                        localStorage.getItem('tourma_de_matches_' + tid) ||
                        localStorage.getItem('tourma_rr_matches_' + tid) ||
                        localStorage.getItem('tourma_group_matches_' + tid) ||
                        localStorage.getItem('tourma_swiss_matches_' + tid) ||
                        localStorage.getItem('tourma_matches_' + tid) ||
                        localStorage.getItem('tourma_stage2_matches_' + tid) ||
                        localStorage.getItem('tourma_matches_stage2_' + tid) ||
                        localStorage.getItem('tourma_bracket_stage2_' + tid) ||
                        localStorage.getItem('tourma_stage1_locked_' + tid)
                    );

                    if (isFinished) {
                        card.setAttribute('data-status', 'COMPLETED');
                        if (statusPill) {
                            statusPill.className = 'status-pill completed';
                            statusPill.innerText = 'Completed';
                        }
                        if (championMeta) {
                            var nameValEl = championMeta.querySelector('.champion-name-val');
                            if (nameValEl) nameValEl.innerText = championName;
                            championMeta.style.display = 'inline-flex';
                        }
                    } else if (dbStatus === 'ONGOING' || hasMatchesPlayed) {
                        card.setAttribute('data-status', 'IN_PROGRESS');
                        if (statusPill) {
                            statusPill.className = 'status-pill in-progress';
                            statusPill.innerText = 'In Progress';
                        }
                        if (championMeta) championMeta.style.display = 'none';
                    } else {
                        card.setAttribute('data-status', 'INCOMING');
                        if (statusPill) {
                            statusPill.className = 'status-pill incoming';
                            statusPill.innerText = 'Incoming';
                        }
                    }

                    // Multi-stage check & format label update
                    var localType = localStorage.getItem("tourma_type_" + tid);
                    var multiConfigRaw = localStorage.getItem("tourma_multi_config_" + tid);
                    var multiConfig = null;
                    if (multiConfigRaw) {
                        try { multiConfig = JSON.parse(multiConfigRaw); } catch(e) {}
                    }

                    var dbStageType = card.getAttribute('data-stage-type');
                    var isMulti = (localType === 'MULTI_STAGE' || multiConfig !== null || dbStageType === 'MULTI_STAGE');

                    var s1Fmt = card.getAttribute('data-stage1-format') || 'SINGLE_ELIMINATION';
                    var s2Fmt = card.getAttribute('data-stage2-format') || 'SINGLE_ELIMINATION';

                    if (multiConfig) {
                        if (multiConfig.stage1Format) s1Fmt = multiConfig.stage1Format;
                        if (multiConfig.stage2Format) s2Fmt = multiConfig.stage2Format;
                    } else if (localStorage.getItem("tourma_format_" + tid)) {
                        s1Fmt = localStorage.getItem("tourma_format_" + tid);
                    }

                    var badgeType = card.querySelector('.tourney-badge-type');
                    if (badgeType) {
                        if (isMulti) {
                            badgeType.innerText = 'MULTI STAGE';
                            badgeType.style.background = 'rgba(251, 191, 36, 0.15)';
                            badgeType.style.color = '#fbbf24';
                            badgeType.style.border = '1px solid rgba(251, 191, 36, 0.3)';
                        } else {
                            badgeType.innerText = 'SINGLE STAGE';
                            badgeType.style.background = '';
                            badgeType.style.color = '';
                            badgeType.style.border = '';
                        }
                    }

                    var formatSpan = card.querySelector('.tourney-format-span');
                    if (formatSpan) {
                        if (isMulti) {
                            var s1Name = getFormatDisplayName(s1Fmt);
                            var s2Name = getFormatDisplayName(s2Fmt);
                            formatSpan.innerHTML = '<i class="fa-solid fa-diagram-project text-mint"></i> ' + s1Name + ' <span style="color: #94a3b8; margin: 0 2px;">➔</span> ' + s2Name;
                        } else {
                            var s1Name = getFormatDisplayName(s1Fmt);
                            formatSpan.innerHTML = '<i class="fa-solid fa-diagram-project text-mint"></i> ' + s1Name;
                        }
                    }

                    // Dynamically update Trận Đấu ➔ link based on Stage 1 format
                    var btnView = card.querySelector('.btn-view-bracket-card');
                    if (btnView) {
                        var ctx = "${pageContext.request.contextPath}";
                        var seriesId = "<%= seriesIdVal %>";
                        var s1Upper = s1Fmt.toUpperCase();
                        if (s1Upper === 'DOUBLE_ELIMINATION') {
                            btnView.href = ctx + '/common/double-elimination.jsp?id=' + tid + '&seriesId=' + seriesId;
                        } else if (s1Upper === 'ROUND_ROBIN') {
                            btnView.href = ctx + '/common/round-robin.jsp?id=' + tid + '&seriesId=' + seriesId;
                        } else if (s1Upper === 'GROUP_STAGE') {
                            btnView.href = ctx + '/common/group-stage.jsp?id=' + tid + '&seriesId=' + seriesId;
                        } else if (s1Upper === 'SWISS_LITE' || s1Upper === 'SWISS') {
                            btnView.href = ctx + '/common/swiss-stage.jsp?id=' + tid + '&seriesId=' + seriesId;
                        } else {
                            btnView.href = ctx + '/common/single-elimination.jsp?id=' + tid + '&seriesId=' + seriesId;
                        }
                    }
                });
            }

            function openCloneTourneyModal(id, currentName) {
                var srcInput = document.getElementById('cloneSourceTournamentId');
                var nameInput = document.getElementById('cloneNewTourneyName');
                var modal = document.getElementById('cloneTourneyModalBackdrop');
                if (srcInput) srcInput.value = id;
                if (nameInput) {
                    nameInput.value = (currentName ? currentName : 'Giải Con') + ' (Bản sao)';
                }
                if (modal) {
                    modal.style.display = 'flex';
                    document.body.style.overflow = 'hidden';
                    if (nameInput) {
                        setTimeout(function() { nameInput.focus(); nameInput.select(); }, 50);
                    }
                }
            }

            function closeCloneTourneyModal() {
                var modal = document.getElementById('cloneTourneyModalBackdrop');
                if (modal) {
                    modal.style.display = 'none';
                    document.body.style.overflow = '';
                }
            }

            document.addEventListener('keydown', function(e) {
                if (e.key === 'Escape') {
                    closeDeleteTourneyModal();
                    closeCloneTourneyModal();
                }
            });

            document.addEventListener('DOMContentLoaded', updateCardStatuses);
        </script>
    </body>
</html>
