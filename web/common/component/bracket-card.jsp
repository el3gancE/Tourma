<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%-- 
    Document   : bracket-card.jsp (Match Card Component - Bracket View Node)
    Description: Compact high-density dark node component for rendering match cards inside bracket trees.
--%>

<link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-card.css">

<%
    String matchNum = request.getParameter("matchNumber");
    if (matchNum == null || matchNum.trim().isEmpty()) {
        String mId = request.getParameter("matchId");
        matchNum = (mId != null) ? mId.replaceAll("[^0-9]", "") : "1";
        if (matchNum.isEmpty()) matchNum = "1";
    }
    
    String t1Name = request.getParameter("team1Name");
    if (t1Name == null || t1Name.trim().isEmpty()) {
        t1Name = "W #" + matchNum;
    }
    
    String t2Name = request.getParameter("team2Name");
    if (t2Name == null || t2Name.trim().isEmpty()) {
        t2Name = "W #" + (Integer.parseInt(matchNum) + 1);
    }

    boolean isT1Placeholder = t1Name.startsWith("W #") || t1Name.startsWith("L #");
    boolean isT2Placeholder = t2Name.startsWith("W #") || t2Name.startsWith("L #");

    String status = request.getParameter("status");
    boolean isDone = "done".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status);

    String s1Param = request.getParameter("team1Score");
    String s2Param = request.getParameter("team2Score");
    String s1Disp = (isDone && s1Param != null && !s1Param.trim().isEmpty()) ? s1Param : "";
    String s2Disp = (isDone && s2Param != null && !s2Param.trim().isEmpty()) ? s2Param : "";
%>

<div class="bracket-node-card" data-match-id="${param.matchId != null ? param.matchId : '1'}" onclick="window.TourmaBracketCard && window.TourmaScoreModal && window.TourmaScoreModal.open({matchId: '${param.matchId != null ? param.matchId : '1'}', roundName: 'Trận #<%= matchNum %>', team1Name: '<%= t1Name %>', team1Seed: '${param.team1Seed != null ? param.team1Seed : '1'}', team1Score: '<%= s1Disp %>', team2Name: '<%= t2Name %>', team2Seed: '${param.team2Seed != null ? param.team2Seed : '2'}', team2Score: '<%= s2Disp %>', status: '<%= isDone ? "COMPLETED" : "SCHEDULED" %>'})">
    <div class="bracket-node-header">
        <span class="bracket-match-id">#<%= matchNum %></span>
        <span class="bracket-status-badge <%= isDone ? "done" : "pending" %>">
            <%= isDone ? "DONE" : "PENDING" %>
        </span>
    </div>

    <div class="bracket-teams-box">
        <div class="bracket-team-row ${param.winner == 'team1' ? 'winner' : ''}">
            <div class="bracket-team-info">
                <span class="bracket-seed-badge">${param.team1Seed != null ? param.team1Seed : '1'}</span>
                <span class="bracket-team-name <%= isT1Placeholder ? "placeholder" : "" %>" title="<%= t1Name %>">
                    <%= t1Name %>
                </span>
            </div>
            <span class="bracket-score-box"><%= s1Disp %></span>
        </div>

        <div class="bracket-team-row ${param.winner == 'team2' ? 'winner' : ''}">
            <div class="bracket-team-info">
                <span class="bracket-seed-badge">${param.team2Seed != null ? param.team2Seed : '2'}</span>
                <span class="bracket-team-name <%= isT2Placeholder ? "placeholder" : "" %>" title="<%= t2Name %>">
                    <%= t2Name %>
                </span>
            </div>
            <span class="bracket-score-box"><%= s2Disp %></span>
        </div>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/bracket-card.js"></script>
