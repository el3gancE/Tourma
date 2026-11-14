<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, model.Team, model.PartnerParticipant, model.SeriesStanding, service.RollingWindowPointService.RollingStandingDTO, java.util.List, java.util.ArrayList, java.util.Map, java.util.HashMap"%>
<%!
    private String partnerAttr(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }
%>
<%
    Map<String, List<Team>> seriesTournamentTeams = (Map<String, List<Team>>) request.getAttribute("seriesTournamentTeams");
    if (seriesTournamentTeams == null) seriesTournamentTeams = java.util.Collections.emptyMap();
    Tournament tournament = (Tournament) request.getAttribute("tournament");
    Series series = (Series) request.getAttribute("series");
    List<Team> currentTeams = (List<Team>) request.getAttribute("currentTeams");
    List<PartnerParticipant> partnerList = (List<PartnerParticipant>) request.getAttribute("partnerList");
    List<SeriesStanding> standingsList = (List<SeriesStanding>) request.getAttribute("standingsList");
    List<RollingStandingDTO> standingsDTOList = (List<RollingStandingDTO>) request.getAttribute("standingsDTOList");
    List<Tournament> tournamentsList = (List<Tournament>) request.getAttribute("tournamentsList");
    if (tournamentsList != null && tournamentsList.size() > 1) {
        tournamentsList = new java.util.ArrayList<>(tournamentsList);
        tournamentsList.sort((a, b) -> {
            int idxA = a.getTournamentIndexInSeries();
            int idxB = b.getTournamentIndexInSeries();
            if (idxA != idxB) return Integer.compare(idxA, idxB);
            if (a.getCreatedAt() != null && b.getCreatedAt() != null) return a.getCreatedAt().compareTo(b.getCreatedAt());
            return 0;
        });
    }

    String tourneyId = (tournament != null) ? tournament.getId() : "";
    String seriesIdVal = (series != null && series.getId() != null) ? series.getId() : "";
    String seriesName = (series != null) ? series.getName() : "Series";
    int partnerCount = (partnerList != null) ? partnerList.size() : 0;
    int phaseSize = (series != null && series.getPhaseSize() > 0) ? series.getPhaseSize() : 3;

    String targetTeamCountParam = request.getParameter("targetTeamCount");
    int targetTeamCountVal = 0;
    if (targetTeamCountParam != null && !targetTeamCountParam.trim().isEmpty()) {
        try { targetTeamCountVal = Integer.parseInt(targetTeamCountParam.trim()); } catch (Exception ignore) {}
    }

    // Order partnerList by Series Rolling Standings rank (Rank 1, 2, 3...)
    List<PartnerParticipant> orderedPartners = new ArrayList<>();
    Map<String, Integer> partnerRankMap = new HashMap<>();
    Map<String, Integer> partnerPointsMap = new HashMap<>();
    java.util.Set<String> addedPartnerIds = new java.util.HashSet<>();

    if (standingsDTOList != null && !standingsDTOList.isEmpty()) {
        for (RollingStandingDTO dto : standingsDTOList) {
            String tName = dto.getTeamName();
            int r = dto.getRank();
            int pts = dto.getTotalActivePoints();
            if (tName != null) {
                String key = tName.trim().toLowerCase();
                partnerRankMap.put(key, r);
                partnerPointsMap.put(key, pts);
                if (partnerList != null) {
                    for (PartnerParticipant p : partnerList) {
                        if (p.getId() != null && !addedPartnerIds.contains(p.getId())) {
                            if ((dto.getPartnerParticipantId() != null && dto.getPartnerParticipantId().equals(p.getId()))
                                    || (p.getName() != null && p.getName().trim().equalsIgnoreCase(tName.trim()))) {
                                orderedPartners.add(p);
                                addedPartnerIds.add(p.getId());
                                break;
                            }
                        }
                    }
                }
            }
        }
    } else if (standingsList != null && !standingsList.isEmpty()) {
        for (int r = 0; r < standingsList.size(); r++) {
            SeriesStanding st = standingsList.get(r);
            if (st.getNormalizedTeamName() != null) {
                String key = st.getNormalizedTeamName().trim().toLowerCase();
                partnerRankMap.put(key, r + 1);
                partnerPointsMap.put(key, st.getTotalRollingPoints());
                if (partnerList != null) {
                    for (PartnerParticipant p : partnerList) {
                        if (p.getId() != null && !addedPartnerIds.contains(p.getId())) {
                            if (p.getName() != null && p.getName().trim().equalsIgnoreCase(st.getNormalizedTeamName().trim())) {
                                orderedPartners.add(p);
                                addedPartnerIds.add(p.getId());
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    if (partnerList != null) {
        for (PartnerParticipant p : partnerList) {
            if (p.getId() != null && !addedPartnerIds.contains(p.getId())) {
                orderedPartners.add(p);
                addedPartnerIds.add(p.getId());
                String key = (p.getName() != null) ? p.getName().trim().toLowerCase() : "";
                if (!partnerRankMap.containsKey(key)) {
                    partnerRankMap.put(key, orderedPartners.size());
                    partnerPointsMap.put(key, 0);
                }
            }
        }
    }
%>
        <!-- MODAL: SELECT QUICKLY FROM PARTNER TEAMS ORDERED BY STANDINGS -->
        <div id="partnerSelectModal" class="team-modal-overlay" style="display: none;">
            <div class="team-modal-card partner-select-card">
                <div data-ui-style="rolling-tournament-teams-jsp-046" style="display: flex;">
                    <h3 data-ui-style="rolling-tournament-teams-jsp-047">
                        <i class="fa-solid fa-check-double text-mint"></i> Chọn Đội Partner Trong Series
                    </h3>
                    <button type="button" onclick="closePartnerSelectModal()" data-ui-style="rolling-tournament-teams-jsp-048">
                        <i class="fa-solid fa-xmark"></i>
                    </button>
                </div>

                <p data-ui-style="rolling-tournament-teams-jsp-049">
                    Tích chọn các đội Partner chính thức của Series <strong><%= partnerAttr(seriesName) %></strong> để đưa vào giải con này:
                </p>

                <form id="partnerSelectForm" method="POST" action="${pageContext.request.contextPath}/rolling/tournament-teams">
                    <input type="hidden" name="action" value="addPartnerTeams">
                    <input type="hidden" name="tournamentId" value="<%= tourneyId %>">
                    <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">

                <div class="partner-selection-columns">
                <section class="partner-selection-teams" aria-labelledby="partnerSelectionTitle">
                    <h4 id="partnerSelectionTitle">Chọn đội Partner</h4>
                <!-- QUICK SELECTION TOOLBAR -->
                <div data-ui-style="rolling-tournament-teams-jsp-050">
                    <div data-ui-style="rolling-tournament-teams-jsp-051" style="display: flex;">
                        <div data-ui-style="rolling-tournament-teams-jsp-052" style="display: flex;">
                            <span data-ui-style="rolling-tournament-teams-jsp-053">Số đội chọn:</span>
                            <input type="number" id="quickSelectNum" value="<%= (targetTeamCountVal > 0) ? String.valueOf(targetTeamCountVal) : "" %>" min="2" max="<%= partnerCount %>" oninput="this.value = this.value.replace(/^0+(?=\d)/, '')" class="form-control" data-ui-style="rolling-tournament-teams-jsp-054">
                        </div>
                        <div data-ui-style="rolling-tournament-teams-jsp-055" style="display: flex;">
                            <button type="button" onclick="quickSelectTeams('TOP')" class="btn btn-mint" data-ui-style="rolling-tournament-teams-jsp-056">
                                <i class="fa-solid fa-arrow-trend-up"></i> Top Đầu
                            </button>
                            <button type="button" onclick="quickSelectTeams('BOTTOM')" class="btn" data-ui-style="rolling-tournament-teams-jsp-057">
                                <i class="fa-solid fa-arrow-trend-down"></i> Top Cuối
                            </button>
                            <button type="button" onclick="quickSelectTeams('RANDOM')" class="btn" data-ui-style="rolling-tournament-teams-jsp-058">
                                <i class="fa-solid fa-shuffle"></i> Ngẫu Nhiên
                            </button>
                        </div>
                    </div>
                    <div data-ui-style="rolling-tournament-teams-jsp-059" style="display: flex;">
                        <span>Chọn theo BXH hoặc tích thủ công (Tổng: <%= partnerCount %> đội)</span>
                        <div>
                            <button type="button" onclick="selectAllPartnerCheckboxes(true)" data-ui-style="rolling-tournament-teams-jsp-060">Chọn tất cả</button>
                            <button type="button" onclick="selectAllPartnerCheckboxes(false)" data-ui-style="rolling-tournament-teams-jsp-061">Bỏ chọn</button>
                        </div>
                    </div>
                </div>

                    <!-- SINGLE COLUMN VERTICAL LIST - ORDERED BY ROLLING STANDINGS -->
                    <div class="partner-checkbox-list" id="partnerModalCheckboxList">
                        <% if (orderedPartners != null && !orderedPartners.isEmpty()) {
                            for (int pIdx = 0; pIdx < orderedPartners.size(); pIdx++) {
                                PartnerParticipant p = orderedPartners.get(pIdx);
                                String pKey = (p.getName() != null) ? p.getName().trim().toLowerCase() : "";
                                int pRank = partnerRankMap.containsKey(pKey) ? partnerRankMap.get(pKey) : (pIdx + 1);
                                int pPts = partnerPointsMap.containsKey(pKey) ? partnerPointsMap.get(pKey) : 0;

                                List<String> playedTournaments = new ArrayList<>();
                                for (Map.Entry<String, List<Team>> played : seriesTournamentTeams.entrySet()) {
                                    for (Team member : played.getValue()) {
                                        if (member.getRawName() != null && member.getRawName().trim().equalsIgnoreCase(p.getName().trim())) {
                                            playedTournaments.add(played.getKey());
                                            break;
                                        }
                                    }
                                }
                                boolean isAlreadyIn = false;
                                if (currentTeams != null) {
                                    for (Team ct : currentTeams) {
                                        if (ct.getRawName() != null && ct.getRawName().equalsIgnoreCase(p.getName())) {
                                            isAlreadyIn = true;
                                            break;
                                        }
                                    }
                                }
                        %>
                            <label class="partner-checkbox-item" data-played-tournaments="<%= partnerAttr(String.join(" ", playedTournaments)) %>" data-already-in="<%= isAlreadyIn %>" data-index="<%= pIdx %>" data-team-name="<%= partnerAttr(p.getName()) %>" data-rank="<%= pRank %>" data-points="<%= pPts %>" data-ui-style="rolling-tournament-teams-jsp-062" style="display: flex;">
                                <div data-ui-style="rolling-tournament-teams-jsp-063" style="display: flex;">
                                    <input type="checkbox" name="selectedTeamNames" value="<%= partnerAttr(p.getName()) %>" class="partner-cb-input" data-index="<%= pIdx %>" <%= isAlreadyIn ? "checked disabled" : "" %>>
                                    <span class="rank-badge rank-<%= pRank %>" data-ui-style="rolling-tournament-teams-jsp-064" style="display: inline-flex; color: <%= (pRank == 1) ? "#fbbf24" : (pRank == 2 ? "#e2e8f0" : (pRank == 3 ? "#f97316" : "#cbd5e1")) %>; border: 1px solid <%= (pRank == 1) ? "rgba(251,191,36,0.3)" : (pRank == 2 ? "rgba(226,232,240,0.2)" : (pRank == 3 ? "rgba(249,115,22,0.3)" : "rgba(255,255,255,0.1)")) %>;">#<%= pRank %></span>
                                    <span data-ui-style="rolling-tournament-teams-jsp-065" style="color: <%= isAlreadyIn ? "var(--ui-muted)" : "var(--ui-text)" %>;">
                                        <%= partnerAttr(p.getName()) %> <%= isAlreadyIn ? "<span style='font-size: 0.75rem; font-weight: 500; color: var(--ui-muted); margin-left: 0.35rem;'>(Đã thêm)</span>" : "" %>
                                    </span>
                                    <small class="partner-excluded-label" hidden>Đã tham gia giải loại trừ</small>
                                </div>
                                <div data-ui-style="rolling-tournament-teams-jsp-066">
                                    <%= pPts %> pts
                                </div>
                            </label>
                        <% }
                        } else { %>
                            <div data-ui-style="rolling-tournament-teams-jsp-067">
                                Chưa có đội Partner nào trong Series.
                            </div>
                        <% } %>
                    </div>

                </section>
                <aside class="partner-exclusion-filter" aria-labelledby="partnerExclusionTitle">
                    <h4 id="partnerExclusionTitle">Không chọn đội từ… <span id="partnerExclusionCount">0 giải</span></h4>
                    <p>Các đội có mặt trong bất kỳ giải nào được chọn sẽ bị khóa chọn.</p>
                    <div class="partner-exclusion-tournaments">
                        <% int exclusionOptions = 0;
                           if (tournamentsList != null) for (int exclusionIndex = tournamentsList.size() - 1; exclusionIndex >= 0; exclusionIndex--) {
                               Tournament other = tournamentsList.get(exclusionIndex);
                               if (other == null || tourneyId.equals(other.getId()) || !seriesIdVal.equals(other.getSeriesId())) continue;
                               exclusionOptions++;
                        %>
                        <label>
                            <input type="checkbox" class="partner-exclusion-input" name="excludedTournamentIds"
                                   form="partnerSelectForm" value="<%= partnerAttr(other.getId()) %>">
                            <span><%= partnerAttr(other.getName()) %></span>
                            <small>#<%= other.getTournamentIndexInSeries() %></small>
                        </label>
                        <% } if (exclusionOptions == 0) { %>
                        <p>Chưa có giải con khác trong Series.</p>
                        <% } %>
                    </div>
                <div id="partnerExclusionHint" class="partner-exclusion-hint" aria-live="polite"></div>
                </aside>

                </div>
                    <div class="partner-selection-footer">
                        <button type="button" onclick="closePartnerSelectModal()" class="btn btn-secondary">Hủy Bỏ</button>
                        <button type="submit" class="btn btn-mint" data-ui-style="rolling-tournament-teams-jsp-068">
                            Thêm Đội Đã Chọn VÀO Giải Con
                        </button>
                    </div>
                </form>
            </div>
        </div>


<script type="application/json" id="partnerStandingsData">
<%
    List<Map<String, Object>> schedule = new ArrayList<>();
    for (Tournament item : tournamentsList) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", item.getId());
        row.put("name", item.getName());
        row.put("index", item.getTournamentIndexInSeries() > 0 ? item.getTournamentIndexInSeries() : schedule.size() + 1);
        row.put("format", item.getFormat() == null ? "SINGLE_ELIMINATION" : item.getFormat());
        row.put("tournamentType", item.getTournamentType() == null ? "SINGLE_STAGE" : item.getTournamentType());
        row.put("isMultiStage", "MULTI_STAGE".equalsIgnoreCase(item.getTournamentType()));
        row.put("tierName", item.getTierName() == null ? "A" : item.getTierName());
        row.put("pointsConfig", item.getSeriesPointsConfig());
        schedule.add(row);
    }
    List<Map<String, String>> partners = new ArrayList<>();
    for (PartnerParticipant partner : partnerList) {
        if (partner.getName() != null) partners.add(Map.of("id", partner.getId(), "name", partner.getName()));
    }
    Map<String, Object> rankMap = new HashMap<>();
    partnerRankMap.forEach((name, rank) -> rankMap.put(name,
            Map.of("rank", rank, "points", partnerPointsMap.getOrDefault(name, 0))));
    Map<String, Object> data = new HashMap<>();
    data.put("seriesSubTournaments", schedule);
    data.put("seriesPartners", partners);
    data.put("serverTourneyPoints", request.getAttribute("serverTourneyPoints"));
    data.put("serverTourneyParticipation", request.getAttribute("serverTourneyParticipation"));
    data.put("seriesPhaseSize", phaseSize);
    data.put("seriesStandingsRankMap", rankMap);
%>
<%= controller.SetupJson.encode(data) %>
</script>
