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
%>

<div class="bracket-node-card" data-match-id="${param.matchId != null ? param.matchId : '1'}" onclick="window.TourmaBracketCard && window.TourmaScoreModal && window.TourmaScoreModal.open({matchId: '${param.matchId != null ? param.matchId : '1'}', roundName: 'Trận #<%= matchNum %>', team1Name: '<%= t1Name %>', team1Seed: '${param.team1Seed != null ? param.team1Seed : '1'}', team1Score: ${param.team1Score != null ? param.team1Score : 0}, team2Name: '<%= t2Name %>', team2Seed: '${param.team2Seed != null ? param.team2Seed : '2'}', team2Score: ${param.team2Score != null ? param.team2Score : 0}})">
    <div class="bracket-connector-in"></div>
    
    <div class="bracket-node-header">
        <span class="bracket-match-id">#<%= matchNum %></span>
        <span class="bracket-status-badge ${param.status == 'done' || param.status == 'COMPLETED' ? 'done' : 'pending'}">
            ${param.status == 'done' || param.status == 'COMPLETED' ? 'DONE' : 'PENDING'}
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
            <span class="bracket-score-box">${param.team1Score != null ? param.team1Score : 0}</span>
        </div>

        <div class="bracket-team-row ${param.winner == 'team2' ? 'winner' : ''}">
            <div class="bracket-team-info">
                <span class="bracket-seed-badge">${param.team2Seed != null ? param.team2Seed : '2'}</span>
                <span class="bracket-team-name <%= isT2Placeholder ? "placeholder" : "" %>" title="<%= t2Name %>">
                    <%= t2Name %>
                </span>
            </div>
            <span class="bracket-score-box">${param.team2Score != null ? param.team2Score : 0}</span>
        </div>
    </div>

    <div class="bracket-connector-out"></div>
</div>

<script src="${pageContext.request.contextPath}/js/bracket-card.js"></script>
