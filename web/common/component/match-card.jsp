<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%-- 
    Document   : match-card.jsp (Match Card Component - List View Row)
    Description: High-density horizontal list component for displaying matches in round lists or fixtures.
--%>

<link rel="stylesheet" href="${pageContext.request.contextPath}/css/match-card.css">

<%
    String matchNum = request.getParameter("matchNumber");
    if (matchNum == null || matchNum.trim().isEmpty()) {
        String mId = request.getParameter("matchId");
        matchNum = (mId != null) ? mId.replaceAll("[^0-9]", "") : "1";
        if (matchNum.isEmpty()) matchNum = "1";
    }
%>

<!-- Reusable Horizontal List Match Card Template -->
<div class="match-card-item" data-match-id="${param.matchId != null ? param.matchId : '1'}" data-status="${param.status != null ? param.status : 'COMPLETED'}">
    
    <!-- Left Section: Static Mint Accent Bar + Match Number (#1, #2, #3...) -->
    <div class="match-card-meta">
        <div class="match-card-accent-bar"></div>
        <span class="match-card-id">#<%= matchNum %></span>
    </div>

    <!-- Center Section: 5-Column Grid Versus Display (Seeds at Far Corners) -->
    <div class="match-card-versus">
        <!-- Col 1: Far Left Corner Seed 1 -->
        <span class="match-list-seed">${param.team1Seed != null ? param.team1Seed : '1'}</span>

        <!-- Col 2: Team 1 Name -->
        <div class="match-team-side team-left ${param.winner == 'team1' ? 'winner' : ''}">
            <span class="match-list-name" title="${param.team1Name != null ? param.team1Name : 'Đội A'}">
                ${param.team1Name != null ? param.team1Name : 'Đội A'}
            </span>
        </div>

        <!-- Col 3: Two Separate Score Boxes -->
        <div class="match-score-container">
            <span class="match-score-single-box ${param.winner == 'team1' ? 'winner' : ''}">
                ${param.team1Score != null ? param.team1Score : 0}
            </span>
            <span class="match-score-dash">-</span>
            <span class="match-score-single-box ${param.winner == 'team2' ? 'winner' : ''}">
                ${param.team2Score != null ? param.team2Score : 0}
            </span>
        </div>

        <!-- Col 4: Team 2 Name -->
        <div class="match-team-side team-right ${param.winner == 'team2' ? 'winner' : ''}">
            <span class="match-list-name" title="${param.team2Name != null ? param.team2Name : 'Đội B'}">
                ${param.team2Name != null ? param.team2Name : 'Đội B'}
            </span>
        </div>

        <!-- Col 5: Far Right Corner Seed 2 -->
        <span class="match-list-seed">${param.team2Seed != null ? param.team2Seed : '2'}</span>
    </div>

    <!-- Right Section: Status Badge (DONE / PENDING) & Compact Edit Button -->
    <div class="match-card-actions">
        <span class="match-list-status ${param.status == 'done' || param.status == 'COMPLETED' ? 'done' : 'pending'}">
            ${param.status == 'done' || param.status == 'COMPLETED' ? 'DONE' : 'PENDING'}
        </span>
        <button type="button" class="btn-edit-match-list" title="Sửa Tỷ Số" onclick="window.TourmaScoreModal && window.TourmaScoreModal.open({matchId: '${param.matchId != null ? param.matchId : '1'}', roundName: 'Trận #<%= matchNum %>', team1Name: '${param.team1Name != null ? param.team1Name : 'Đội A'}', team1Seed: '${param.team1Seed != null ? param.team1Seed : '1'}', team1Score: ${param.team1Score != null ? param.team1Score : 0}, team2Name: '${param.team2Name != null ? param.team2Name : 'Đội B'}', team2Seed: '${param.team2Seed != null ? param.team2Seed : '2'}', team2Score: ${param.team2Score != null ? param.team2Score : 0}, winnerId: '${param.winner != null ? param.winner : 'team1'}', status: '${param.status != null ? param.status : 'COMPLETED'}'})">
            <i class="fa-solid fa-pen-to-square"></i>
        </button>
    </div>

</div>

<script src="${pageContext.request.contextPath}/js/match-card.js"></script>