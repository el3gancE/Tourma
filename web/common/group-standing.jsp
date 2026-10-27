<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="dao.TournamentDAO" %>
<%@ page import="dao.GroupStageDAO" %>
<%@ page import="model.Tournament" %>
<%
    String tournamentId = request.getParameter("id");
    if (tournamentId == null || tournamentId.trim().isEmpty()) {
        tournamentId = "demo";
    }

    TournamentDAO tDao = new TournamentDAO();
    Tournament tourney = tDao.getTournamentById(tournamentId, false);

    String tourneyName = (tourney != null && tourney.getName() != null) ? tourney.getName() : "Giải Đấu Vòng Bảng";
    String tournamentType = (tourney != null && tourney.getTournamentType() != null) ? tourney.getTournamentType() : "SINGLE_STAGE";
    int cutTarget = (tourney != null) ? tourney.getAdvancingSeatsCount() : 0;

    int currentStage = "2".equals(request.getParameter("stage")) ? 2 : 1;
    String dbMatchesJson = "[]";
    String dbGroupAssignments = (tourney != null) ? tourney.getGroupAssignments() : null;
    try {
        GroupStageDAO gsDao = new GroupStageDAO();
        String j = gsDao.getMatchesJsonForFrontend(tournamentId, currentStage);
        if (j != null && !j.trim().isEmpty() && !j.trim().equals("[]")) {
            dbMatchesJson = j;
        }
    } catch (Exception ignore) {}
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

    <!-- Google Font Lexend -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800;900&display=swap" rel="stylesheet">

    <!-- Global Styling & Icons -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-navbar.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/round-robin.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/group-standing.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/empty-team-alert.css">
</head>
<body style="background: #0b0d12; color: #f8fafc;">

    <!-- EMPTY TEAM ALERT COMPONENT -->
    <jsp:include page="/common/component/empty-team-alert.jsp" />

    <jsp:include page="/common/component/tournament-config-state.jsp"/>
    <jsp:include page="/common/component/stage-end-popup.jsp"/>
    <!-- TOP NAVBAR & SIDEBAR -->
    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="tournaments"/>
    </jsp:include>
    <jsp:include page="/common/component/sidebar.jsp">
        <jsp:param name="activeStep" value="group-standing" />
        <jsp:param name="id" value="<%= tournamentId %>" />
        <jsp:param name="format" value="GROUP_STAGE" />
    </jsp:include>

    <main class="container has-sidebar round-robin-container">
        
        <!-- TOP CONTROL BAR (Round Robin / Group Stage Design Style) -->
        <div class="rr-control-bar">
            <div class="rr-info-group">
                <h1 class="rr-tourney-title">
                    <i class="fa-solid fa-trophy text-gold"></i>
                    <span id="gsTournamentTitle"><%= tourneyName %></span>
                </h1>
                <span class="format-badge-rr" title="Group Stage">GS</span>
                <span id="gsTeamCountBadge" class="team-count-badge">0 Đội</span>
                <span id="gsAdvanceBadge" class="team-count-badge" style="background: rgba(34, 197, 94, 0.15); color: #22c55e; border: 1px solid rgba(34, 197, 94, 0.3); display: inline-flex; align-items: center; gap: 0.35rem;">
                    <i class="fa-solid fa-circle-check" style="font-size: 0.75rem;"></i>
                    <span id="gsAdvanceText">0 Đội đi tiếp</span>
                </span>
            </div>

            <div class="rr-actions-group">
                <button type="button" class="btn-reset-bracket-action" onclick="TourmaGroupStage.resetAllMatches()" title="Xóa toàn bộ kết quả và thiết lập lại từ đầu">
                    <i class="fa-solid fa-rotate-right"></i> Reset Bảng Đấu
                </button>

                <div class="view-mode-toggle-group">
                    <a href="${pageContext.request.contextPath}/common/group-stage.jsp?id=<%= tournamentId %>&format=GROUP_STAGE" class="btn-view-toggle" style="text-decoration: none;">
                        <i class="fa-solid fa-calendar-days"></i> Lịch
                    </a>
                    <a href="${pageContext.request.contextPath}/common/group-standing.jsp?id=<%= tournamentId %>&format=GROUP_STAGE" class="btn-view-toggle active" style="text-decoration: none;">
                        <i class="fa-solid fa-ranking-star"></i> BXH
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

    <script src="${pageContext.request.contextPath}/js/bracket-algorithm.js"></script>
    <script src="${pageContext.request.contextPath}/js/empty-team-alert.js"></script>
    <script src="${pageContext.request.contextPath}/js/group-standing.js"></script>
    <script>
        window.TourmaContextPath = '${pageContext.request.contextPath}';
        window.TourmaCutTarget = <%= cutTarget %>;
        window.TourmaDbStage1Status = window.TourmaDbStage1Status || {};
        window.TourmaDbStage1Status['<%= tournamentId %>'] = '<%= tourney == null || tourney.getStage1Status() == null ? "PENDING" : tourney.getStage1Status() %>';
        window.groupTournamentId = "<%= tournamentId %>";
        window.dbGroupMatches = <%= dbMatchesJson %>;
        window.dbGroupAssignments = <%= (dbGroupAssignments != null && !dbGroupAssignments.trim().isEmpty() && !dbGroupAssignments.trim().equals("{}")) ? dbGroupAssignments : "null" %>;

        // Ensure View Toggle buttons correctly show Bảng Xếp Hạng active
        document.addEventListener('DOMContentLoaded', function () {
            var btnBracket = document.getElementById('btnViewBracket');
            var btnList = document.getElementById('btnViewList');
            if (btnBracket) {
                btnBracket.classList.remove('active');
                btnBracket.onclick = function() {
                    window.location.href = "${pageContext.request.contextPath}/common/group-stage.jsp?id=" + encodeURIComponent(window.groupTournamentId) + "&format=GROUP_STAGE";
                };
            }
            if (btnList) {
                btnList.classList.add('active');
            }

            var urlParams = new URLSearchParams(window.location.search);
            var tid = urlParams.get('id') || 'demo';

            var groups = {};
            var matches = {};
            var rules = { winPoints: 3, drawPoints: 1, lossPoints: 0, advanceCount: 2 };

            groups = window.dbGroupAssignments || {};
            (window.dbGroupMatches || []).forEach(function(match){
                var key=match.groupKey || 'A';
                if(!matches[key]) matches[key]=[];
                matches[key].push(match);
            });
            var saved=(window.TourmaMultiStageConfig || {})['stage<%= currentStage %>Config'] || {};
            ['winPoints','drawPoints','lossPoints'].forEach(function(key){if(saved[key] !== undefined)rules[key]=Number(saved[key]);});
            rules.advanceCount=window.TourmaCutTarget || 2 * Object.keys(groups).length;
            if(<%= currentStage %> === 1 && window.TourmaTournamentType === 'MULTI_STAGE' && window.StageEndPopup){
                window.StageEndPopup.update(tid,'GROUP_STAGE',matches,Object.keys(groups).reduce(function(all,key){return all.concat(groups[key]);},[]),
                    {isMultiStage:true,cutTarget:window.TourmaCutTarget},matches,1);
            }

            var teamCount = 0;
            var numGroups = Object.keys(groups).length;
            for (var g in groups) { teamCount += groups[g].length; }

            var countBadge = document.getElementById('tournamentTeamCountBadge');
            if (countBadge) countBadge.innerText = teamCount + ' Đội (' + numGroups + ' Bảng)';

            if (window.TourmaGroupStanding) {
                window.TourmaGroupStanding.renderAllGroupStandings('gsStandingsContainer', groups, matches, rules);
            }
        });

        // Global Reset Handler for Group Standing
        window.TourmaGroupStage = {
            confirmResetBracket: function() {
                var tid = window.groupTournamentId || 'demo';
                try {
                    localStorage.removeItem('tourma_group_matches_' + tid);
                    localStorage.removeItem('tourma_final_locked_' + tid);
                    localStorage.removeItem('tourma_champion_' + tid);
                    localStorage.removeItem('tourma_stage1_locked_' + tid);
                    localStorage.removeItem('tourma_stage2_teams_' + tid);
                    localStorage.removeItem('tourma_stage1_completed_' + tid);
                } catch(e) {}

                fetch((window.TourmaContextPath || '') + '/common/group-stage', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
                    body: 'action=reset&tournamentId=' + encodeURIComponent(tid) + '&stage=1'
                }).finally(function() {
                    location.reload();
                });
            }
        };
    </script>
</body>
</html>
