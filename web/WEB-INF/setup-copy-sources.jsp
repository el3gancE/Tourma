<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Tournament, java.util.List, java.util.Map, java.util.HashMap, java.util.ArrayList"%>
<%!
    private String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    List<Tournament> availableTournaments = (List<Tournament>) request.getAttribute("copySources");
    boolean isRollingSubTournament = Boolean.TRUE.equals(request.getAttribute("copySourcesSeries"));
%>
<% if (!availableTournaments.isEmpty()) { for (Tournament at :
                                            availableTournaments) { String tType=(at.getTournamentType() !=null) ?
                                            at.getTournamentType() : "SINGLE_STAGE" ; boolean isMulti="MULTI_STAGE"
                                            .equalsIgnoreCase(tType); String fmt=(at.getFormat() !=null) ?
                                            at.getFormat() : "SINGLE_ELIMINATION" ; int tCount=at.getTeamCount(); String
                                            tTier=at.getTierName() !=null ? at.getTierName().toUpperCase() : "" ; %>
                                            <div class="copy-tourney-item-card" data-tourney-id="<%= escape(at.getId()) %>"
                                                data-tourney-name="<%= escape(at.getName() == null ? "" : at.getName().toLowerCase(java.util.Locale.ROOT)) %>"
                                                data-ui-style="configure-tournament-format-jsp-123" style="display: flex;">
                                                <div class="copy-tourney-info" data-ui-style="configure-tournament-format-jsp-124">
                                                    <div
                                                        class="copy-tourney-name" data-ui-style="configure-tournament-format-jsp-125">
                                                        <%= escape(at.getName()) %>
                                                    </div>
                                                    <div
                                                        class="copy-tourney-meta" data-ui-style="configure-tournament-format-jsp-126" style="display: flex;">
                                                        <span class="stage-badge"
                                                            data-ui-style="configure-tournament-format-jsp-127">
                                                            <%= isMulti ? "MULTI STAGE" : "SINGLE STAGE" %>
                                                        </span>
                                                        <span class="stage-badge"
                                                            data-ui-style="configure-tournament-format-jsp-128">
                                                            <%= fmt.replace("_", " " ) %>
                                                        </span>
                                                        <% if (!tTier.isEmpty()) { %>
                                                            <span class="tier-tag tier-<%= tTier.toLowerCase() %>"
                                                                data-ui-style="configure-tournament-format-jsp-129">
                                                                Tier <%= tTier %>
                                                            </span>
                                                            <% } %>
                                                                <span class="stage-badge"
                                                                    data-ui-style="configure-tournament-format-jsp-130">
                                                                    <i class="fa-solid fa-users"></i>
                                                                    <%= tCount %> Đội
                                                                </span>
                                                    </div>
                                                </div>
                                                <button type="button" class="btn btn-mint"
                                                    data-copy-apply
                                                    data-ui-style="configure-tournament-format-jsp-131">
                                                    <i class="fa-solid fa-arrow-down-to-bracket"></i> Áp Dụng
                                                </button>
                                            </div>
                                            <% } } else { %>
                                                <div
                                                    data-ui-style="configure-tournament-format-jsp-132">
                                                    <%= isRollingSubTournament ? "Chưa có giải con khác trong Series để sao chép cấu hình." : "Chưa có giải đấu nào khác để sao chép cấu hình." %>
                                                </div>
                                                <% } %>
<script type="application/json" id="copySourcesData">
<%
    List<Map<String, Object>> sources = new ArrayList<>();
    for (Tournament tournament : availableTournaments) {
        Map<String, Object> source = new HashMap<>();
        source.put("id", tournament.getId());
        source.put("name", tournament.getName());
        source.put("tournamentType", tournament.getTournamentType() == null ? "SINGLE_STAGE" : tournament.getTournamentType());
        source.put("format", tournament.getFormat() == null ? "SINGLE_ELIMINATION" : tournament.getFormat());
        source.put("tierName", tournament.getTierName() == null ? "S" : tournament.getTierName().toUpperCase(java.util.Locale.ROOT));
        source.put("teamCount", tournament.getTeamCount());
        source.put("advancingSeatsCount", tournament.getAdvancingSeatsCount());
        source.put("multiStageConfig", tournament.getMultiStageConfig());
        source.put("pointsConfig", tournament.getSeriesPointsConfig());
        sources.add(source);
    }
%>
<%= controller.SetupJson.encode(sources) %>
</script>
