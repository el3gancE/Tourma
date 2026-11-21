<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, java.util.List, java.util.Locale"%>
<%@page import="controller.RollingTournamentSummaryServlet.RoundGroupDTO, controller.RollingTournamentSummaryServlet.TeamSummaryDTO, controller.RollingTournamentSummaryServlet.PathMatchDTO"%>
<%!
    private String esc(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String formatLabel(String fmt) {
        if (fmt == null) return "Single Elimination";
        String f = fmt.trim().toUpperCase(Locale.ROOT);
        if ("SINGLE_ELIMINATION".equals(f)) return "Single Elimination";
        if ("DOUBLE_ELIMINATION".equals(f)) return "Double Elimination";
        if ("ROUND_ROBIN".equals(f)) return "Round Robin";
        if ("GROUP_STAGE".equals(f)) return "Group Stage";
        if ("GSL".equals(f)) return "GSL";
        if (f.contains("SWISS")) return "Swiss Stage";
        return fmt;
    }
%>
<%
    Tournament tournament = (Tournament) request.getAttribute("tournament");
    Series series = (Series) request.getAttribute("series");
    Boolean isCompletedObj = (Boolean) request.getAttribute("isCompleted");
    boolean isCompleted = Boolean.TRUE.equals(isCompletedObj);
    Boolean isMultiObj = (Boolean) request.getAttribute("isMultiStage");
    boolean isMultiStage = Boolean.TRUE.equals(isMultiObj);

    String tourneyId = tournament != null && tournament.getId() != null ? tournament.getId() : "";
    String tourneyName = tournament != null && tournament.getName() != null ? tournament.getName() : "Giải Đấu Con";
    String seriesIdVal = series != null && series.getId() != null ? series.getId()
            : (tournament != null && tournament.getSeriesId() != null ? tournament.getSeriesId() : "");
    String seriesName = series != null && series.getName() != null ? series.getName() : "Series";
    String tierName = tournament != null && tournament.getTierName() != null && !tournament.getTierName().trim().isEmpty()
            ? tournament.getTierName().trim().toUpperCase(Locale.ROOT) : "S";

    String s1Format = (String) request.getAttribute("stage1Format");
    String s2Format = (String) request.getAttribute("stage2Format");
    String formatSummary = isMultiStage
            ? (formatLabel(s1Format) + " ➔ " + formatLabel(s2Format))
            : formatLabel(s1Format);

    Integer teamCountObj = (Integer) request.getAttribute("teamCount");
    int teamCount = teamCountObj != null ? teamCountObj : (tournament != null ? tournament.getTeamCount() : 0);
    int totalMatches = tournament != null ? tournament.getTotalMatches() : 0;
    int completedMatches = tournament != null ? tournament.getCompletedMatches() : 0;
    String activeBracketUrl = (String) request.getAttribute("activeBracketUrl");
    if (activeBracketUrl == null || activeBracketUrl.isEmpty()) {
        activeBracketUrl = request.getContextPath() + "/my-tournaments?action=matches&id=" + tourneyId;
    }

    List<RoundGroupDTO> roundGroups = (List<RoundGroupDTO>) request.getAttribute("roundGroups");
    List<TeamSummaryDTO> teamSummaries = (List<TeamSummaryDTO>) request.getAttribute("teamSummaries");
    Integer topSeedCount = (Integer) request.getAttribute("topSeedCount");
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title><%= esc(tourneyName) %> - Tổng Kết Giải Đấu - TOURMA</title>


        <script src="${pageContext.request.contextPath}/js/theme.js?v=20261101-settings"></script>
    </head>
    <body class="ui-page workspace-page tournament-workspace series-page tournament-summary-page">
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-series"/><jsp:param name="themeToggle" value="true"/>
        </jsp:include>

        <div class="tournament-shell">
            <details class="tournament-navigation" open>
                <summary>Điều hướng giải đấu</summary>
                <jsp:include page="/common/component/sidebar.jsp">
                    <jsp:param name="id" value="<%= tourneyId %>"/>
                    <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
                    <jsp:param name="activeStep" value="summary"/>
                </jsp:include>
            </details>

            <main class="series-main">
                <section class="summary-header-card">
                    <div>
                        <div class="summary-title-row">
                            <h1 class="summary-title"><%= esc(tourneyName) %></h1>
                            <span class="tier-tag tier-<%= tierName.toLowerCase(Locale.ROOT) %>">Tier <%= esc(tierName) %></span>
                        </div>
                        <div class="summary-meta-line">
                            <span><%= esc(seriesName) %></span>
                            <span aria-hidden="true">·</span>
                            <span><%= esc(formatSummary) %></span>
                            <span aria-hidden="true">·</span>
                            <span><%= teamCount %> đội</span>
                            <span aria-hidden="true">·</span>
                            <span><%= completedMatches %>/<%= totalMatches %> trận</span>
                        </div>
                    </div>
                </section>

                <% if (!isCompleted) { %>
                <section class="summary-incomplete-alert" role="alert">
                    <div class="summary-alert-body">
                        <div>
                            <h2 class="summary-alert-title">Giải đấu chưa hoàn tất</h2>
                            <p class="summary-alert-desc">
                                Cần hoàn thành toàn bộ các trận đấu và xác nhận kết thúc giải để xem bảng tổng kết thứ hạng theo vòng.
                                Tiến độ hiện tại: <strong><%= completedMatches %>/<%= totalMatches %> trận</strong>.
                            </p>
                        </div>
                    </div>
                    <a href="<%= esc(activeBracketUrl) %>" class="summary-alert-action">Đi Tới Trận Đấu</a>
                </section>
                <% } else { %>
                <section aria-label="Kết quả theo vòng">
                    <div class="summary-rounds-stack">
                        <% if (roundGroups != null && !roundGroups.isEmpty()) {
                            for (RoundGroupDTO group : roundGroups) {
                                List<TeamSummaryDTO> gTeams = group.getTeams();
                        %>
                        <div class="summary-round-group" data-min-rank="<%= group.getMinRank() %>">
                            <div class="summary-round-header">
                                <span class="summary-round-title"><%= esc(group.getGroupTitle()) %></span>
                                <span class="summary-round-meta"><%= gTeams.size() %> đội</span>
                            </div>

                            <div class="summary-round-items">
                                <% for (TeamSummaryDTO tm : gTeams) {
                                    String profileUrl = "";
                                    if (!seriesIdVal.isEmpty() && tm.getTeamName() != null && !tm.getTeamName().isEmpty()) {
                                        profileUrl = request.getContextPath() + "/team-profile?seriesId=" + esc(seriesIdVal)
                                                + "&teamName=" + java.net.URLEncoder.encode(tm.getTeamName(), "UTF-8");
                                        if (tm.getPartnerParticipantId() != null && !tm.getPartnerParticipantId().trim().isEmpty()) {
                                            profileUrl += "&partnerId=" + esc(tm.getPartnerParticipantId());
                                        }
                                    }
                                    String wlText = tm.getWins() + "W - " + tm.getLosses() + "L" + (tm.getDraws() > 0 ? (" - " + tm.getDraws() + "D") : "");
                                %>
                                <button type="button"
                                        class="summary-team-row"
                                        data-rank="<%= tm.getRank() %>"
                                        data-team-id="<%= esc(tm.getTeamId()) %>"
                                        data-team-name="<%= esc(tm.getTeamName()) %>"
                                        data-team-round="<%= esc(group.getGroupTitle()) %>"
                                        data-team-rank-label="<%= esc(tm.getRankLabel()) %>"
                                        data-team-wl="<%= esc(wlText) %>"
                                        data-team-pts="<%= tm.getPointsEarned() %>"
                                        data-profile-url="<%= profileUrl %>"
                                        onclick="openTeamPathModal(this)">
                                    <span class="summary-team-rank"><%= esc(tm.getRankLabel()) %></span>
                                    <span class="summary-team-main">
                                        <span class="summary-team-name"><%= esc(tm.getTeamName()) %></span>
                                        <% if (tm.getSeed() > 0 && (topSeedCount == null || tm.getSeed() <= topSeedCount)) { %>
                                        <span class="summary-team-seed" data-seed="<%= tm.getSeed() %>">Seed <%= tm.getSeed() %></span>
                                        <% } %>
                                    </span>
                                    <span class="summary-team-stats">
                                        <span class="summary-team-wl"><%= esc(wlText) %></span>
                                        <% if (tm.getPointsEarned() > 0) { %>
                                        <span class="summary-team-pts">+<%= tm.getPointsEarned() %> pts</span>
                                        <% } %>
                                    </span>
                                </button>
                                <% } %>
                            </div>
                        </div>
                        <%  }
                           } %>
                    </div>
                </section>

                <% if (teamSummaries != null) {
                    for (TeamSummaryDTO tm : teamSummaries) {
                        List<PathMatchDTO> pathMatches = tm.getMatches();
                %>
                <template id="teamPathTpl_<%= esc(tm.getTeamId()) %>">
                    <% if (pathMatches != null && !pathMatches.isEmpty()) {
                        for (PathMatchDTO m : pathMatches) {
                            boolean isWin = "W".equalsIgnoreCase(m.getResult());
                            boolean isLoss = "L".equalsIgnoreCase(m.getResult());
                            String leftSideClass = isWin ? "winner" : (isLoss ? "loser" : "");
                            String rightSideClass = isLoss ? "winner" : (isWin ? "loser" : "");
                            String leftScoreText = String.valueOf(m.getTeamScore()) + (m.getTeamPenalty() != null ? " (" + m.getTeamPenalty() + ")" : "");
                            String rightScoreText = (m.getOpponentPenalty() != null ? "(" + m.getOpponentPenalty() + ") " : "") + String.valueOf(m.getOpponentScore());
                    %>
                    <div class="match-card-item path-match-card" data-result="<%= esc(m.getResult()) %>">
                        <div class="match-card-meta">
                            <span><%= esc(m.getRoundLabel()) %></span>
                        </div>
                        <div class="match-card-versus">
                            <div class="match-team-side team-left <%= leftSideClass %>">
                                <span class="match-list-name"><%= esc(tm.getTeamName()) %></span>
                            </div>
                            <div class="match-score-container">
                                <div class="match-score-single-box"><%= esc(leftScoreText) %></div>
                                <span class="match-score-divider">-</span>
                                <div class="match-score-single-box"><%= esc(rightScoreText) %></div>
                            </div>
                            <div class="match-team-side team-right <%= rightSideClass %>">
                                <span class="match-list-name"><%= esc(m.getOpponentName()) %></span>
                            </div>
                        </div>
                    </div>
                    <%  }
                       } else { %>
                    <div class="path-empty-msg">Chưa có dữ liệu trận đấu.</div>
                    <% } %>
                </template>
                <%  }
                   } %>

                <div id="teamPathModalBackdrop" class="tourma-modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="teamPathModalTitle" onclick="if (event.target === this) closeTeamPathModal();">
                    <div class="tourma-modal-card" onclick="event.stopPropagation();">
                        <div class="modal-header-bar">
                            <div>
                                <div class="modal-header-title">
                                    <span id="teamPathModalTitle">Hành Trình Đội</span>
                                </div>
                                <div class="path-modal-sub" id="teamPathModalSub"></div>
                            </div>
                            <button type="button" class="modal-close-btn" aria-label="Đóng" onclick="closeTeamPathModal()">&times;</button>
                        </div>
                        <div class="modal-body-content">
                            <div class="path-modal-list" id="teamPathModalList"></div>
                        </div>
                        <div class="modal-footer-bar">
                            <button type="button" onclick="closeTeamPathModal()">Đóng</button>
                            <a id="teamPathProfileBtn" href="#" class="btn-direct-profile">Xem Hồ Sơ Đội</a>
                        </div>
                    </div>
                </div>

                <script>
                    function openTeamPathModal(btn) {
                        if (!btn) return;
                        var teamId = btn.getAttribute('data-team-id') || '';
                        var teamName = btn.getAttribute('data-team-name') || '';
                        var teamRound = btn.getAttribute('data-team-round') || '';
                        var rankLabel = btn.getAttribute('data-team-rank-label') || '';
                        var teamWl = btn.getAttribute('data-team-wl') || '';
                        var teamPts = parseInt(btn.getAttribute('data-team-pts') || '0', 10);
                        var profileUrl = btn.getAttribute('data-profile-url') || '';

                        var titleEl = document.getElementById('teamPathModalTitle');
                        if (titleEl) titleEl.textContent = teamName;

                        var subEl = document.getElementById('teamPathModalSub');
                        if (subEl) {
                            var parts = [];
                            if (rankLabel) parts.push(rankLabel);
                            if (teamRound) parts.push(teamRound);
                            if (teamWl) parts.push(teamWl);
                            if (teamPts > 0) parts.push('+' + teamPts + ' pts');
                            subEl.textContent = parts.join(' · ');
                        }

                        var listEl = document.getElementById('teamPathModalList');
                        var tpl = document.getElementById('teamPathTpl_' + teamId);
                        if (listEl) {
                            listEl.innerHTML = '';
                            if (tpl && tpl.content) {
                                listEl.appendChild(tpl.content.cloneNode(true));
                            }
                        }

                        var profileBtn = document.getElementById('teamPathProfileBtn');
                        if (profileBtn) {
                            if (profileUrl) {
                                profileBtn.href = profileUrl;
                                profileBtn.style.display = 'inline-flex';
                            } else {
                                profileBtn.style.display = 'none';
                            }
                        }

                        var modal = document.getElementById('teamPathModalBackdrop');
                        if (modal) {
                            modal.classList.add('show');
                            document.body.style.overflow = 'hidden';
                        }
                    }

                    function closeTeamPathModal() {
                        var modal = document.getElementById('teamPathModalBackdrop');
                        if (modal) {
                            modal.classList.remove('show');
                            document.body.style.overflow = '';
                        }
                    }

                    document.addEventListener('keydown', function (e) {
                        if (e.key === 'Escape') closeTeamPathModal();
                    });

                    (function syncSummarySeedVisibility() {
                        var tid = '<%= esc(tourneyId) %>';
                        var dbTopSeeds = <%= topSeedCount != null ? topSeedCount : -1 %>;
                        var maxVisible = dbTopSeeds >= 0 ? dbTopSeeds : Infinity;
                        if (tid) {
                            try {
                                var raw = localStorage.getItem('tourma_hide_seed_config_' + tid);
                                if (raw) {
                                    var cfg = JSON.parse(raw);
                                    if (cfg) {
                                        var v = (cfg.visibleCount !== undefined && cfg.visibleCount !== null) ? String(cfg.visibleCount).trim() : '';
                                        var parsed = parseInt(v, 10);
                                        if (cfg.isEnabled) {
                                            maxVisible = (!v || isNaN(parsed) || parsed <= 0) ? 0 : Math.min(maxVisible, parsed);
                                        } else if (!isNaN(parsed) && parsed >= 0 && dbTopSeeds < 0) {
                                            maxVisible = parsed;
                                        }
                                    }
                                }
                            } catch (e) {}
                        }
                        if (maxVisible !== Infinity) {
                            document.querySelectorAll('.summary-team-seed[data-seed]').forEach(function (el) {
                                var s = parseInt(el.getAttribute('data-seed') || '0', 10);
                                if (s <= 0 || s > maxVisible) el.style.display = 'none';
                            });
                        }
                    })();
                </script>
                <% } %>
            </main>
        </div>
    </body>
</html>
