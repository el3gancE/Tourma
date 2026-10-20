<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!-- TOURMA - Reusable Stage Finish Alert Component (Locked Stage 2) -->
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/stage-finish-alert.css">
<div id="stageFinishAlertContainer" class="stage-finish-alert-wrapper" style="display: none;">
    <div class="stage-finish-alert-card">
        <div class="stage-finish-alert-icon-box">
            <i class="fa-solid fa-lock stage-finish-alert-icon"></i>
        </div>
        <h3 class="stage-finish-alert-title">Bạn chưa hoàn thành vòng 1</h3>
        <p class="stage-finish-alert-desc">
            Vui lòng hoàn thành và xác nhận kết quả vòng 1 để tiếp tục.
        </p>
        <a id="stageFinishAlertReturnBtn" href="#" class="btn-stage-finish-return">
            <i class="fa-solid fa-arrow-left"></i> Quay Lại Vòng 1
        </a>
    </div>
</div>
<%
    String accessTournamentId=request.getParameter("id");
    if(accessTournamentId==null)accessTournamentId=request.getParameter("tournamentId");
    if(accessTournamentId!=null && !accessTournamentId.trim().isEmpty() && !"demo".equals(accessTournamentId)) {
%>
<%
    String accessStateJson;
    try { accessStateJson=new dao.TournamentDAO().getStageAccessStateJson(accessTournamentId); }
    catch(Exception missingTournament){ accessStateJson="{\"ready\":false}"; }
%>
<script>window.TourmaStageAccessState=<%= accessStateJson %>;</script>
<% } %>
<script src="${pageContext.request.contextPath}/js/stage-finish-alert.js"></script>
