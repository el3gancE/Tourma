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

    String status = request.getParameter("status");
    boolean isDone = "done".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status);

    String s1Param = request.getParameter("team1Score");
    String s2Param = request.getParameter("team2Score");
    String s1Disp = (isDone && s1Param != null && !s1Param.trim().isEmpty()) ? s1Param : "";
    String s2Disp = (isDone && s2Param != null && !s2Param.trim().isEmpty()) ? s2Param : "";
%>

<!-- Reusable Horizontal List Match Card Template (Clickable to Edit) -->
<div class="match-card-item" data-match-id="${param.matchId != null ? param.matchId : '1'}" data-status="<%= isDone ? "COMPLETED" : "SCHEDULED" %>" onclick="window.TourmaScoreModal && window.TourmaScoreModal.open({matchId: '${param.matchId != null ? param.matchId : '1'}', roundName: 'Trận #<%= matchNum %>', team1Name: '${param.team1Name != null ? param.team1Name : 'Đội A'}', team1Seed: '${param.team1Seed != null ? param.team1Seed : '1'}', team1Score: '<%= s1Disp %>', team2Name: '${param.team2Name != null ? param.team2Name : 'Đội B'}', team2Seed: '${param.team2Seed != null ? param.team2Seed : '2'}', team2Score: '<%= s2Disp %>', winnerId: '${param.winner != null ? param.winner : ''}', status: '<%= isDone ? "COMPLETED" : "SCHEDULED" %>'})">
    
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

        <!-- Col 3: Two Separate Score Boxes (Identical styling for winner and loser) -->
        <div class="match-score-container">
            <span class="match-score-single-box">
                <%= s1Disp %>
            </span>
            <span class="match-score-dash">-</span>
            <span class="match-score-single-box">
                <%= s2Disp %>
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

    <!-- Right Section: Status Badge (DONE / PENDING) -->
    <div class="match-card-actions">
        <span class="match-list-status <%= isDone ? "done" : "pending" %>">
            <%= isDone ? "DONE" : "PENDING" %>
        </span>
    </div>

</div>

<script src="${pageContext.request.contextPath}/js/match-card.js"></script>