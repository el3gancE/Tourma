<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="dao.TournamentDAO" %>
<%@ page import="model.Tournament" %>
<%
    String tournamentId = request.getParameter("id");
    if (tournamentId == null || tournamentId.trim().isEmpty()) {
        tournamentId = "demo";
    }

    TournamentDAO tDao = new TournamentDAO();
    Tournament tourney = tDao.getTournamentById(tournamentId);

    String tourneyName = (tourney != null && tourney.getName() != null) ? tourney.getName() : "Giải Đấu Vòng Bảng";
%>
<!DOCTYPE html>
<html lang="vi">
<head>
        <!-- Favicon -->
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <link rel="alternate icon" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Bảng Xếp Hạng Vòng Bảng - <%= tourneyName %></title>

    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/round-robin.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/group-standing.css">
</head>
<body style="background: #0b0d12; color: #f8fafc;">

    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="tournaments"/>
    </jsp:include>
    <jsp:include page="/common/component/sidebar.jsp">
        <jsp:param name="activeStep" value="group-standing" />
        <jsp:param name="id" value="<%= tournamentId %>" />
        <jsp:param name="format" value="GROUP_STAGE" />
    </jsp:include>

    <main class="container has-sidebar round-robin-container">
        
        <!-- TOP CONTROL BAR (Round Robin Design Style) -->
        <div class="rr-control-bar">
            <div class="rr-info-group">
                <h1 class="rr-tourney-title">
                    <i class="fa-solid fa-trophy text-gold"></i>
                    <span id="gstTournamentTitle"><%= tourneyName %></span>
                </h1>
                <span class="format-badge-rr">Group Stage</span>
                <span id="gstTeamCountBadge" class="team-count-badge">0 Đội</span>
                <span id="gstAdvanceBadge" class="team-count-badge" style="background: rgba(34, 197, 94, 0.15); color: #22c55e; border: 1px solid rgba(34, 197, 94, 0.3); display: inline-flex; align-items: center; gap: 0.35rem;">
                    <i class="fa-solid fa-circle-check" style="font-size: 0.75rem;"></i>
                    <span id="gstAdvanceText">0 Đội đi tiếp</span>
                </span>
            </div>

            <div class="rr-actions-group">
                <button type="button" class="btn-reset-bracket-action" onclick="resetAllMatchesStanding('<%= tournamentId %>')" title="Xóa toàn bộ kết quả và thiết lập lại từ đầu">
                    <i class="fa-solid fa-rotate-right"></i> Reset Giải
                </button>

                <div class="view-mode-toggle-group">
                    <a href="${pageContext.request.contextPath}/common/group-stage.jsp?id=<%= tournamentId %>&format=GROUP_STAGE" class="btn-view-toggle" style="text-decoration: none;">
                        <i class="fa-solid fa-calendar-days"></i> Lịch Thi Đấu
                    </a>
                    <a href="${pageContext.request.contextPath}/common/group-standing.jsp?id=<%= tournamentId %>&format=GROUP_STAGE" class="btn-view-toggle active" style="text-decoration: none;">
                        <i class="fa-solid fa-ranking-star"></i> Bảng Xếp Hạng
                    </a>
                </div>
            </div>
        </div>

        <!-- GROUP SELECTOR FILTER PILLS BAR -->
        <div id="gsGroupSelectorBar" class="rr-round-selector-bar">
            <!-- Dynamic Group Pills (Tất cả các bảng, Bảng A, Bảng B...) -->
        </div>

        <!-- STANDINGS CONTAINER -->
        <div id="gsStandingsContainer" class="gst-container" style="padding: 0;"></div>
    </main>

    <script src="${pageContext.request.contextPath}/js/group-standing.js"></script>
    <script>
        document.addEventListener('DOMContentLoaded', function () {
            var urlParams = new URLSearchParams(window.location.search);
            var tid = urlParams.get('id') || 'demo';

            var groups = {};
            var matches = {};
            var rules = { winPoints: 3, drawPoints: 1, lossPoints: 0, advanceCount: 2 };

            try {
                var gRaw = localStorage.getItem('tourma_group_assignments_' + tid);
                if (gRaw) groups = JSON.parse(gRaw);

                var mRaw = localStorage.getItem('tourma_group_matches_' + tid);
                if (mRaw) matches = JSON.parse(mRaw);

                var groupCfgRaw = localStorage.getItem('tourma_group_config_' + tid);
                if (groupCfgRaw) {
                    var gCfg = JSON.parse(groupCfgRaw);
                    if (gCfg && gCfg.advanceCount) rules.advanceCount = parseInt(gCfg.advanceCount);
                    if (gCfg && gCfg.advancePerGroup) rules.advanceCount = parseInt(gCfg.advancePerGroup);
                }

                var cfgRaw = localStorage.getItem('tourma_multi_config_' + tid);
                if (cfgRaw) {
                    var parsed = JSON.parse(cfgRaw);
                    if (parsed && parsed.stage1Config) {
                        var c = parsed.stage1Config;
                        if (c.winPoints !== undefined) rules.winPoints = parseInt(c.winPoints);
                        if (c.drawPoints !== undefined) rules.drawPoints = parseInt(c.drawPoints);
                        if (c.lossPoints !== undefined) rules.lossPoints = parseInt(c.lossPoints);
                        if (c.advancePerGroup) rules.advanceCount = parseInt(c.advancePerGroup);
                        else if (c.advanceCount) rules.advanceCount = parseInt(c.advanceCount);
                        else if (c.totalAdvanceCount) rules.advanceCount = parseInt(c.totalAdvanceCount);
                    } else if (parsed && parsed.advanceCount) {
                        rules.advanceCount = parseInt(parsed.advanceCount);
                    }
                }
            } catch (e) {}

            var teamCount = 0;
            var numGroups = Object.keys(groups).length;
            for (var g in groups) { teamCount += groups[g].length; }

            var countBadge = document.getElementById('gstTeamCountBadge');
            if (countBadge) countBadge.innerText = teamCount + ' Đội (' + numGroups + ' Bảng)';

            if (window.TourmaGroupStanding) {
                window.TourmaGroupStanding.renderAllGroupStandings('gsStandingsContainer', groups, matches, rules);
            }
        });

        function resetAllMatchesStanding(tid) {
            if (!confirm('Bạn có chắc chắn muốn xóa toàn bộ kết quả thi đấu và đưa giải đấu về từ đầu?')) {
                return;
            }

            try {
                var mRaw = localStorage.getItem('tourma_group_matches_' + tid);
                if (!mRaw) return;
                var gMatches = JSON.parse(mRaw);

                for (var gKey in gMatches) {
                    var mList = gMatches[gKey] || [];
                    for (var i = 0; i < mList.length; i++) {
                        var m = mList[i];
                        m.team1.score = '';
                        m.team2.score = '';
                        m.winnerId = null;
                        m.status = 'SCHEDULED';
                    }
                }

                localStorage.setItem('tourma_group_matches_' + tid, JSON.stringify(gMatches));
                location.reload();
            } catch(e) {}
        }

        function randomizeAllMatchesStanding(tid) {
            try {
                var mRaw = localStorage.getItem('tourma_group_matches_' + tid);
                if (!mRaw) return;
                var gMatches = JSON.parse(mRaw);

                for (var gKey in gMatches) {
                    var mList = gMatches[gKey] || [];
                    for (var i = 0; i < mList.length; i++) {
                        var m = mList[i];
                        if (!m.team1 || !m.team2 || m.team1.name === 'BYE' || m.team2.name === 'BYE') continue;

                        var s1 = Math.floor(Math.random() * 5);
                        var s2 = Math.floor(Math.random() * 5);

                        m.team1.score = String(s1);
                        m.team2.score = String(s2);
                        m.status = 'COMPLETED';

                        if (s1 > s2) {
                            m.winnerId = 'team1';
                        } else if (s2 > s1) {
                            m.winnerId = 'team2';
                        } else {
                            m.winnerId = 'draw';
                        }
                    }
                }

                localStorage.setItem('tourma_group_matches_' + tid, JSON.stringify(gMatches));
                location.reload();
            } catch(e) {}
        }
    </script>
</body>
</html>
