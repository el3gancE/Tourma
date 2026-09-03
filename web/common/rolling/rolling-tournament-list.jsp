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
        tournamentsList = tournamentDAO.getTournamentsBySeriesId(series.getId());
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

    if (seriesIdVal == null && series != null) {
        seriesIdVal = series.getId();
    }
    String seriesName = (series != null) ? series.getName() : "Series Circuit";
    int tourneyCount = (tournamentsList != null) ? tournamentsList.size() : 0;
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
                    // Reverse list so newest sub-tournament displays first at top
                    java.util.Collections.reverse(tournamentsList);

                    for (int i = 0; i < tournamentsList.size(); i++) {
                        Tournament t = tournamentsList.get(i);
                        int nTeams = teamCountMap.containsKey(t.getId()) ? teamCountMap.get(t.getId()) : 0;
                        String tierName = (t.getTierName() != null) ? t.getTierName().toUpperCase() : "S";
                        String fmt = (t.getFormat() != null) ? t.getFormat().toUpperCase() : "SINGLE_ELIMINATION";
                        String fmtLabel = "Single Elimination";
                        if ("DOUBLE_ELIMINATION".equals(fmt)) fmtLabel = "Double Elimination";
                        else if ("ROUND_ROBIN".equals(fmt)) fmtLabel = "Round Robin";
                        else if ("GROUP_STAGE".equals(fmt)) fmtLabel = "Group Stage";
                        else if ("SWISS_LITE".equals(fmt) || "SWISS".equals(fmt)) fmtLabel = "Swiss System";

                        String bracketUrl = "/common/single-elimination.jsp";
                        if ("DOUBLE_ELIMINATION".equals(fmt)) bracketUrl = "/common/double-elimination.jsp";
                        else if ("ROUND_ROBIN".equals(fmt)) bracketUrl = "/common/round-robin.jsp";
                        else if ("GROUP_STAGE".equals(fmt)) bracketUrl = "/common/group-stage.jsp";
                        else if ("SWISS_LITE".equals(fmt) || "SWISS".equals(fmt)) bracketUrl = "/common/swiss-stage.jsp";

                        String championName = t.getChampionName();
                        boolean isFinished = (championName != null && !championName.trim().isEmpty()) || "COMPLETED".equalsIgnoreCase(t.getStatus());
                        String statusStr = isFinished ? "Completed" : ("ONGOING".equalsIgnoreCase(t.getStatus()) ? "In Progress" : "Incoming");
                        String statusClass = isFinished ? "completed" : ("ONGOING".equalsIgnoreCase(t.getStatus()) ? "in-progress" : "incoming");
                        String stageTypeLabel = "MULTI_STAGE".equalsIgnoreCase(t.getTournamentType()) ? "MULTI STAGE" : "SINGLE STAGE";
                %>
                    <div class="tourney-card" data-id="<%= t.getId() %>" data-db-status="<%= t.getStatus() != null ? t.getStatus() : "" %>" data-db-champion="<%= championName != null ? championName.replace("\"", "&quot;") : "" %>" data-name="<%= t.getName() %>">
                        <div class="tourney-card-main">
                            <div class="tourney-card-top-row">
                                <h3 class="tourney-card-title" style="margin: 0;"><%= t.getName() %></h3>
                                <span class="tourney-badge-type"><%= stageTypeLabel %></span>
                                <span class="tourney-badge-tier">TIER <%= tierName %></span>
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
                                <form action="${pageContext.request.contextPath}/rolling/tournament-list" method="POST" style="margin: 0; display: inline;" onsubmit="return confirm('Bạn có chắc chắn muốn xóa giải con này?');">
                                    <input type="hidden" name="action" value="delete">
                                    <input type="hidden" name="tournamentId" value="<%= t.getId() %>">
                                    <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">
                                    <button type="submit" class="btn-delete-tourney" title="Xóa">
                                        <i class="fa-solid fa-trash-can"></i> Xóa
                                    </button>
                                </form>
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

        </main>

        <script>
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

            function findChampionName(card) {
                var tid = card.getAttribute('data-id');
                var dbChamp = card.getAttribute('data-db-champion');
                var saved = localStorage.getItem("tourma_champion_" + tid) || localStorage.getItem("tourma_final_champion_" + tid);
                if (saved && saved.trim() !== "") return saved;
                if (dbChamp && dbChamp.trim() !== "") return dbChamp;

                var keys = ["tourma_bracket_", "tourma_bracket_stage2_", "tourma_de_matches_", "tourma_matches_", "tourma_rr_matches_"];
                for (var i = 0; i < keys.length; i++) {
                    try {
                        var raw = localStorage.getItem(keys[i] + tid);
                        if (!raw) continue;
                        var data = JSON.parse(raw);
                        var matchesMap = data.matchesMap || data;
                        if (!matchesMap || typeof matchesMap !== 'object') continue;

                        var gfMatch = matchesMap['GF_RESET'] || matchesMap['GF'];
                        if (gfMatch && gfMatch.winnerId) {
                            var wObj = (gfMatch.winnerId === 'team1') ? gfMatch.team1 : ((gfMatch.winnerId === 'team2') ? gfMatch.team2 : null);
                            if (wObj) {
                                var cName = (typeof wObj === 'object') ? (wObj.name || wObj.rawName) : wObj;
                                if (cName) return cName;
                            }
                        }

                        var matchKeys = Object.keys(matchesMap);
                        var maxRound = -1;
                        var finalWinner = null;
                        matchKeys.forEach(function(k) {
                            var m = matchesMap[k];
                            if (m) {
                                if (m.isFinalMatch && m.winnerId) {
                                    var wObj = (m.winnerId === 'team1') ? m.team1 : ((m.winnerId === 'team2') ? m.team2 : null);
                                    if (wObj) finalWinner = (typeof wObj === 'object') ? (wObj.name || wObj.rawName) : wObj;
                                } else if (m.roundNumber !== undefined && m.roundNumber > maxRound && m.winnerId) {
                                    maxRound = m.roundNumber;
                                    var wObj = (m.winnerId === 'team1') ? m.team1 : ((m.winnerId === 'team2') ? m.team2 : null);
                                    if (wObj) finalWinner = (typeof wObj === 'object') ? (wObj.name || wObj.rawName) : wObj;
                                }
                            }
                        });
                        if (finalWinner) return finalWinner;
                    } catch(e) {}
                }
                return "";
            }

            function updateCardStatuses() {
                var cards = document.querySelectorAll('.tourney-card');
                cards.forEach(function(card) {
                    var tid = card.getAttribute('data-id');
                    if (!tid) return;

                    var dbStatus = card.getAttribute('data-db-status');
                    var championName = findChampionName(card);
                    var isFinished = (championName && championName.trim() !== '') || dbStatus === 'COMPLETED';

                    var statusPill = card.querySelector('.status-pill');
                    var championMeta = card.querySelector('.tourney-champion-meta');

                    var hasMatchesPlayed = !!(localStorage.getItem('tourma_bracket_' + tid) || localStorage.getItem('tourma_de_matches_' + tid) || localStorage.getItem('tourma_rr_matches_' + tid) || localStorage.getItem('tourma_matches_' + tid));

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

                    // Dynamically update Trận Đấu ➔ link based on localStorage format
                    var btnView = card.querySelector('.btn-view-bracket-card');
                    var localFmt = localStorage.getItem("tourma_format_" + tid);
                    if (btnView && localFmt) {
                        localFmt = localFmt.toUpperCase();
                        var ctx = "${pageContext.request.contextPath}";
                        var seriesId = "<%= seriesIdVal %>";
                        if (localFmt === 'DOUBLE_ELIMINATION') {
                            btnView.href = ctx + '/common/double-elimination.jsp?id=' + tid + '&seriesId=' + seriesId;
                        } else if (localFmt === 'ROUND_ROBIN') {
                            btnView.href = ctx + '/common/round-robin.jsp?id=' + tid + '&seriesId=' + seriesId;
                        } else if (localFmt === 'GROUP_STAGE') {
                            btnView.href = ctx + '/common/group-stage.jsp?id=' + tid + '&seriesId=' + seriesId;
                        } else if (localFmt === 'SWISS_LITE' || localFmt === 'SWISS') {
                            btnView.href = ctx + '/common/swiss-stage.jsp?id=' + tid + '&seriesId=' + seriesId;
                        } else if (localFmt === 'SINGLE_ELIMINATION') {
                            btnView.href = ctx + '/common/single-elimination.jsp?id=' + tid + '&seriesId=' + seriesId;
                        }
                    }
                });
            }

            document.addEventListener('DOMContentLoaded', updateCardStatuses);
        </script>
    </body>
</html>
