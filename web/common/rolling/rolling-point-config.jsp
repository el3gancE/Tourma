<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Series, model.Tournament, java.util.List, java.util.ArrayList, java.util.Map, java.util.HashMap"%>
<%
    Tournament tournament = (Tournament) request.getAttribute("tournament");
    Series series = (Series) request.getAttribute("series");
    Integer teamCountObj = (Integer) request.getAttribute("teamCount");
    int teamCount = (teamCountObj != null) ? teamCountObj : 0;

    String tourneyId = (tournament != null) ? tournament.getId() : "";
    String tourneyName = (tournament != null) ? tournament.getName() : "Giải Đấu Con";
    String seriesIdVal = (series != null && series.getId() != null) ? series.getId() : "";
    String seriesName = (series != null) ? series.getName() : "Series";
    String tierName = (tournament != null && tournament.getTierName() != null) ? tournament.getTierName().toUpperCase() : "S";
    String tourneyFormat = (tournament != null && tournament.getFormat() != null) ? tournament.getFormat().toUpperCase() : "SINGLE_ELIMINATION";
    String tournamentType = (tournament != null && tournament.getTournamentType() != null) ? tournament.getTournamentType() : "SINGLE_STAGE";

    // Retrieve saved points if available (F5 / revisit retention)
    Integer savedChampPoints = (tournament != null) ? tournament.getSeriesRewardPoints() : null;
    String savedJson = (tournament != null) ? tournament.getSeriesPointsConfig() : null;

    Map<String, Integer> savedPointsMap = new HashMap<>();
    if (savedJson != null && !savedJson.trim().isEmpty()) {
        try {
            String clean = savedJson.trim().replaceAll("[{}\"]", "");
            String[] pairs = clean.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    savedPointsMap.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
                }
            }
        } catch (Exception ignore) {}
    }

    int nTeams = (teamCount > 0) ? teamCount : 8;

    // Dynamically build position/round list
    class PositionItem {
        String key;
        String label;
        String stageHeader;
        PositionItem(String k, String l) { key = k; label = l; stageHeader = null; }
        PositionItem(String k, String l, String sh) { key = k; label = l; stageHeader = sh; }
    }
    List<PositionItem> posList = new ArrayList<>();

    boolean isMultiStage = "MULTI_STAGE".equalsIgnoreCase(tournamentType);
    String reqS1F = (String) request.getAttribute("stage1Format");
    String reqS2F = (String) request.getAttribute("stage2Format");
    String s1Format = (reqS1F != null && !reqS1F.trim().isEmpty()) ? reqS1F.trim().toUpperCase() : "GROUP_STAGE";
    String s2Format = (reqS2F != null && !reqS2F.trim().isEmpty()) ? reqS2F.trim().toUpperCase() : "SINGLE_ELIMINATION";

    int cutTarget = (tournament != null && tournament.getAdvancingSeatsCount() > 1) ? tournament.getAdvancingSeatsCount() : 0;
    if (s1Format.contains("SWISS") && cutTarget <= 1) {
        cutTarget = 8;
    } else if (cutTarget <= 1) {
        int pow2 = 2;
        while (pow2 * 2 < nTeams) {
            pow2 *= 2;
        }
        cutTarget = Math.max(2, pow2);
    }
    boolean hasGroupStep = "GSL".equals(isMultiStage ? s1Format : tourneyFormat)
            || "GROUP_STAGE".equals(isMultiStage ? s1Format : tourneyFormat);
    String previousStepUrl = request.getContextPath() + (hasGroupStep
            ? "/common/manage-group.jsp?id=" + tourneyId + "&format=" + (isMultiStage ? s1Format : tourneyFormat)
            : "/rolling/tournament-teams?id=" + tourneyId) + "&seriesId=" + seriesIdVal;
    int s2Teams = cutTarget;
    int gslGroupCount = request.getAttribute("gslGroupCount") == null ? 1 : (Integer)request.getAttribute("gslGroupCount");
    int gslGroupSize = request.getAttribute("gslGroupSize") == null ? 4 : (Integer)request.getAttribute("gslGroupSize");

    if (isMultiStage) {
        String s2Header = "STAGE 2: " + s2Format + " (" + s2Teams + " Đội)";
        String s1Header = "STAGE 1: " + s1Format + " (" + nTeams + " Đội → " + cutTarget + " Đi Tiếp)";

        // Stage 2 Finishing Placements (based on s2Teams = cutTarget)
        if ("DOUBLE_ELIMINATION".equals(s2Format)) {
            posList.add(new PositionItem("1", "Champion", s2Header));
            if (s2Teams >= 2) posList.add(new PositionItem("2", "Runner-up"));
            if (s2Teams >= 3) posList.add(new PositionItem("3", "Losers Final"));
            if (s2Teams >= 4) posList.add(new PositionItem("4", "Losers Semi-final"));
            int s2Pow2 = 4;
            while (s2Pow2 < s2Teams && s2Pow2 < 2048) s2Pow2 *= 2;
            int s2UbRounds = (int) Math.round(Math.log(s2Pow2) / Math.log(2));
            int s2LbRounds = (s2UbRounds - 1) * 2;
            for (int offset = 2; offset < s2LbRounds; offset++) {
                int lrNum = s2LbRounds - offset;
                int k = (offset - 2) / 2;
                int tierStart = (int) Math.pow(2, k + 2) + 1;
                int halfSize = (int) Math.pow(2, k + 1);
                int tierEnd = (int) Math.pow(2, k + 3);
                String posKey = (offset % 2 == 0) ? (tierStart + "-" + (tierStart + halfSize - 1)) : ((tierStart + halfSize) + "-" + tierEnd);
                posList.add(new PositionItem(posKey, "Losers Round " + lrNum));
            }
        } else if ("ROUND_ROBIN".equals(s2Format)) {
            posList.add(new PositionItem("1", "Champion", s2Header));
            if (s2Teams >= 2) posList.add(new PositionItem("2", "Runner-up"));
            for (int p = 3; p <= s2Teams; p++) {
                posList.add(new PositionItem(String.valueOf(p), "Hạng " + p));
            }
        } else {
            // SINGLE_ELIMINATION (Dynamic for any number of advancing teams)
            posList.add(new PositionItem("1", "Champion", s2Header));
            if (s2Teams >= 2) posList.add(new PositionItem("2", "Runner-up"));
            int curLim = 4;
            while (curLim / 2 < s2Teams) {
                int sR = (curLim / 2) + 1;
                int eR = curLim;
                String label = (curLim == 4) ? "Semi-final" : ((curLim == 8) ? "Quarter-final" : ("Round of " + curLim));
                posList.add(new PositionItem(sR + "-" + eR, label));
                curLim *= 2;
            }
        }

        // Stage 1 Eliminated Placements
        if ("DOUBLE_ELIMINATION".equals(s1Format)) {
            int pow2 = 4;
            while (pow2 < nTeams && pow2 < 2048) pow2 *= 2;
            int totalUbRounds = (int) Math.round(Math.log(pow2) / Math.log(2));
            int ubQualifiers = Math.max(1, cutTarget / 2);
            int ubStopRound = totalUbRounds - (int) Math.round(Math.log(ubQualifiers) / Math.log(2));
            int lbStopRound = Math.max(1, (ubStopRound - 1) * 2);

            for (int lr = lbStopRound; lr >= 1; lr--) {
                boolean isFinalLbRound = (lr == lbStopRound);
                String label = isFinalLbRound ? "Loser's Qualification (LQ)" : ("Losers Round " + lr);
                String key = "s1_lb_r" + lr;
                String headerToUse = (lr == lbStopRound) ? s1Header : null;
                posList.add(new PositionItem(key, label, headerToUse));
            }
        } else if ("GSL".equals(s1Format)) {
            int groupBracketSize = 4;
            while (groupBracketSize < gslGroupSize) groupBracketSize *= 2;
            int groupAdvance = Math.max(1, cutTarget / gslGroupCount);
            int groupUpperRounds = (int)(Math.log(groupBracketSize) / Math.log(2));
            int groupLowerStop = groupAdvance == 1 ? 2 * (groupUpperRounds - 1)
                : 2 * (groupUpperRounds - (int)(Math.log(groupAdvance / 2) / Math.log(2)) - 1);
            if (groupAdvance == 1) posList.add(new PositionItem("s1_gsl_final", "Group Qualification", s1Header));
            for (int lr = groupLowerStop; lr >= 1; lr--) {
                posList.add(new PositionItem("s1_gsl_lb_r" + lr, "GSL — loại tại LB Round " + lr,
                    groupAdvance > 1 && lr == groupLowerStop ? s1Header : null));
            }
        } else if ("SINGLE_ELIMINATION".equals(s1Format)) {
            int cur = 4;
            while (cur < nTeams && cur < 2048) cur *= 2;
            java.util.List<PositionItem> seRounds = new java.util.ArrayList<>();
            while (cur > cutTarget) {
                int nextCur = cur / 2;
                String posKey = (nextCur + 1) + "-" + cur;
                String label = (cur == 8) ? "Quarter-final" : ((cur == 16) ? "Round of 16" : ((cur == 32) ? "Round of 32" : ((cur == 64) ? "Round of 64" : ("Round of " + cur))));
                seRounds.add(new PositionItem(posKey, label));
                cur = nextCur;
            }
            for (int sr = seRounds.size() - 1; sr >= 0; sr--) {
                PositionItem itm = seRounds.get(sr);
                if (sr == seRounds.size() - 1) itm.stageHeader = s1Header;
                posList.add(itm);
            }
            if (seRounds.isEmpty()) {
                posList.add(new PositionItem("stage1_eliminated", "Vòng loại SE", s1Header));
            }
        } else if ("ROUND_ROBIN".equals(s1Format)) {
            posList.add(new PositionItem("stage1_eliminated", "Vòng loại RR", s1Header));
        } else if ("SWISS_LITE".equalsIgnoreCase(s1Format) || "SWISS".equalsIgnoreCase(s1Format)) {
            posList.add(new PositionItem("swiss_2-3", "Swiss 2-3", s1Header));
            posList.add(new PositionItem("swiss_1-3", "Swiss 1-3"));
            posList.add(new PositionItem("swiss_0-3", "Swiss 0-3"));
        } else {
            posList.add(new PositionItem("stage1_eliminated", "Vòng bảng", s1Header));
        }
    } else if ("SINGLE_ELIMINATION".equals(tourneyFormat)) {
        posList.add(new PositionItem("1", "Champion"));
        if (nTeams >= 2) posList.add(new PositionItem("2", "Runner-up"));
        for (int fieldSize = 4; fieldSize / 2 < nTeams; fieldSize *= 2) {
            String label = fieldSize == 4 ? "Semi-final" : fieldSize == 8 ? "Quarter-final" : "Round of " + fieldSize;
            posList.add(new PositionItem((fieldSize / 2 + 1) + "-" + fieldSize, label));
        }
    } else if ("DOUBLE_ELIMINATION".equals(tourneyFormat)) {
        posList.add(new PositionItem("1", "Champion"));
        if (nTeams >= 2) posList.add(new PositionItem("2", "Runner-up"));
        if (nTeams >= 3) posList.add(new PositionItem("3", "Losers Final"));
        if (nTeams >= 4) posList.add(new PositionItem("4", "Losers Semi-final"));

        int pow2 = 4;
        while (pow2 < nTeams && pow2 < 2048) {
            pow2 *= 2;
        }
        int totalUbRounds = (int) Math.round(Math.log(pow2) / Math.log(2));
        int totalLbRounds = (totalUbRounds - 1) * 2;

        for (int offset = 2; offset < totalLbRounds; offset++) {
            int lrNum = totalLbRounds - offset;
            int k = (offset - 2) / 2;
            int tierStart = (int) Math.pow(2, k + 2) + 1;
            int halfSize = (int) Math.pow(2, k + 1);
            int tierEnd = (int) Math.pow(2, k + 3);

            String posKey;
            if (offset % 2 == 0) {
                int sR = tierStart;
                int eR = tierStart + halfSize - 1;
                posKey = sR + "-" + eR;
            } else {
                int sR = tierStart + halfSize;
                int eR = tierEnd;
                posKey = sR + "-" + eR;
            }
            posList.add(new PositionItem(posKey, "Losers Round " + lrNum));
        }
    } else if ("SWISS_LITE".equals(tourneyFormat) || "SWISS".equals(tourneyFormat)) {
        posList.add(new PositionItem("swiss_2-3", "Swiss 2-3"));
        posList.add(new PositionItem("swiss_1-3", "Swiss 1-3"));
        posList.add(new PositionItem("swiss_0-3", "Swiss 0-3"));
    } else if ("ROUND_ROBIN".equals(tourneyFormat)) {
        for (int p = 1; p <= nTeams; p++) {
            String label = "Rank " + p;
            if (p == 1) label = "Champion";
            else if (p == 2) label = "Runner-up";
            posList.add(new PositionItem(String.valueOf(p), label));
        }
    } else {
        // GROUP_STAGE
        posList.add(new PositionItem("1", "Nhất Bảng / Vô Địch"));
        posList.add(new PositionItem("2", "Nhì Bảng / Á Quân"));
        posList.add(new PositionItem("3-4", "Hạng 3-4"));
        posList.add(new PositionItem("stage1_eliminated", "Vòng bảng"));
    }
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title><%= tourneyName %> - Thiết Lập Điểm Thưởng - TOURMA</title>

        <!-- Google Fonts & FontAwesome -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Readex+Pro:wght@400;500;600;700&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

        <!-- Main Design System CSS & Dedicated Form CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/create-tournament.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/rolling/rolling-point-config.css">
<script src="${pageContext.request.contextPath}/js/theme.js"></script>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/design-tokens.css?v=26">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/ui-base.css?v=18">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/workspace.css?v=20">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-workspace.css?v=18">
</head>
    <body class="ui-page workspace-page tournament-workspace series-page series-points-page">
        <!-- Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-series"/><jsp:param name="themeToggle" value="true"/>
        </jsp:include>

        <!-- Dynamic 3-Mode Sidebar Component -->
        <div class="tournament-shell"><details class="tournament-navigation" open><summary>Điều hướng giải đấu</summary><jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="id" value="<%= tourneyId %>"/>
            <jsp:param name="seriesId" value="<%= seriesIdVal %>"/>
            <jsp:param name="activeStep" value="point-config"/>
        </jsp:include></details>

        <main class="series-main" data-ui-style="rolling-point-config-jsp-001">

            <!-- Breadcrumb Navigation -->
            <div data-ui-style="rolling-point-config-jsp-002">
                <a href="<%= previousStepUrl %>" class="text-muted" data-ui-style="rolling-point-config-jsp-003">
                    <i class="fa-solid fa-arrow-left"></i> Quay lại: <%= hasGroupStep ? "Chia Bảng" : "Quản Lý Đội" %>
                </a>
            </div>

            <!-- Form Container Box -->
            <div class="form-container-box">
                <h1 class="form-header-title" data-ui-style="rolling-point-config-jsp-004">
                    <i class="fa-solid fa-star text-gold"></i> Thiết lập điểm thưởng
                </h1>
                <p class="text-muted" data-ui-style="rolling-point-config-jsp-005">Điểm thưởng theo vị trí cho <strong><%= nTeams %> đội</strong> · Tier <strong><%= tierName %></strong> · <strong id="fmtHeaderDisplay"><%= isMultiStage ? (s1Format + " → " + s2Format) : tourneyFormat %></strong>.</p>

                <!-- RULE NOTICE ALERT BOX -->
                <div data-ui-style="rolling-point-config-jsp-006">
                    <div data-ui-style="rolling-point-config-jsp-007" style="display: flex;">
                        <i class="fa-solid fa-circle-info"></i> Lưu ý
                    </div>
                    Ô trống được tính là <strong>0 điểm</strong>.<br>
                    Chỉ đội đã đăng ký trong Series mới được cộng điểm vào BXH.
                </div>

                <form id="pointConfigForm" action="${pageContext.request.contextPath}/rolling/point-config" method="POST">
                    <input type="hidden" name="tournamentId" value="<%= tourneyId %>">
                    <input type="hidden" name="seriesId" value="<%= seriesIdVal %>">
                    <input type="hidden" name="clientFormat" id="clientFormatInput" value="<%= tourneyFormat %>">
                    <input type="hidden" name="clientStage1Format" id="clientStage1FormatInput" value="<%= s1Format %>">

                    <!-- DYNAMICALLY GENERATED POSITION / ROUND POINT INPUTS -->
                    <div data-ui-style="rolling-point-config-jsp-008">
                        <h4 data-ui-style="rolling-point-config-jsp-009" style="display: flex;">
                            <span><i class="fa-solid fa-pen-to-square text-mint"></i> Điểm theo vị trí</span>
                            <span data-ui-style="rolling-point-config-jsp-010">
                                Tier <%= tierName %>
                            </span>
                        </h4>

                        <div data-ui-style="rolling-point-config-jsp-011" style="display: flex;">
                            <% for (int i = 0; i < posList.size(); i++) {
                                PositionItem item = posList.get(i);
                                if (item.stageHeader != null) {
                                    boolean isS2 = item.stageHeader.contains("STAGE 2");
                                    String badgeColor = isS2 ? "var(--ui-tier-s)" : "var(--ui-accent)";
                                    String badgeBg = isS2 ? "var(--ui-surface-alt)" : "var(--ui-accent-soft)";
                                    String badgeBorder = isS2 ? "var(--ui-line)" : "var(--ui-line)";
                                    String icon = isS2 ? "fa-trophy" : "fa-diagram-project";
                                    String subText = isS2 ? "(Vòng Chung Cuộc)" : "(Vòng Loại / Phân Hạng)";
                            %>
                                <div data-ui-style="rolling-point-config-jsp-012" style="margin-top: <%= isS2 ? "0.25rem" : "1.25rem" %>;">
                                    <span data-ui-style="rolling-point-config-jsp-013" style="color: <%= badgeColor %>; background: <%= badgeBg %>; border: 1px solid <%= badgeBorder %>; display: inline-flex;">
                                        <i class="fa-solid <%= icon %>"></i> <%= item.stageHeader %>
                                    </span>
                                </div>
                            <%  }
                                if (i == 0 || "1".equals(item.key)) {
                                    String champVal = "";
                                    if (savedChampPoints != null && savedChampPoints > 0) {
                                        champVal = String.valueOf(savedChampPoints);
                                    } else if (savedPointsMap.containsKey("1") && savedPointsMap.get("1") != null && savedPointsMap.get("1") > 0) {
                                        champVal = String.valueOf(savedPointsMap.get("1"));
                                    }
                            %>
                                <!-- 1st Place / Champion Champion Points -->
                                <div data-ui-style="rolling-point-config-jsp-014" style="display: flex;">
                                    <div data-ui-style="rolling-point-config-jsp-015">
                                        <i class="fa-solid fa-trophy" data-ui-style="rolling-point-config-jsp-016"></i> <%= item.label %>
                                    </div>
                                    <div data-ui-style="rolling-point-config-jsp-017" style="display: flex;">
                                        <input type="number" name="champPoints" aria-label="Điểm vô địch" value="<%= champVal %>" placeholder="0" min="0" onfocus="this.select()" class="form-control" data-ui-style="rolling-point-config-jsp-018">
                                        <span data-ui-style="rolling-point-config-jsp-019">pts</span>
                                    </div>
                                </div>
                            <% } else {
                                String posVal = "";
                                if (savedPointsMap.containsKey(item.key) && savedPointsMap.get(item.key) != null && savedPointsMap.get(item.key) > 0) {
                                    posVal = String.valueOf(savedPointsMap.get(item.key));
                                }
                            %>
                                <!-- Sub Rounds / Finishing Positions -->
                                <div data-ui-style="rolling-point-config-jsp-020" style="display: flex;">
                                    <div data-ui-style="rolling-point-config-jsp-021">
                                        <%= item.label %>
                                    </div>
                                    <div data-ui-style="rolling-point-config-jsp-022" style="display: flex;">
                                        <input type="number" name="point_pos_<%= item.key %>" aria-label="Điểm vị trí <%= item.key %>" value="<%= posVal %>" placeholder="0" min="0" onfocus="this.select()" class="form-control" data-ui-style="rolling-point-config-jsp-023">
                                        <span data-ui-style="rolling-point-config-jsp-024">pts</span>
                                    </div>
                                </div>
                            <% }
                            } %>
                        </div>
                    </div>

                    <!-- SUBMIT NAVIGATION BUTTONS -->
                    <div data-ui-style="rolling-point-config-jsp-025" style="display: flex;">
                        <a href="<%= previousStepUrl %>" class="btn btn-secondary" data-ui-style="rolling-point-config-jsp-026">
                            <i class="fa-solid fa-arrow-left"></i> Quay lại: <%= hasGroupStep ? "Chia Bảng" : "Quản Lý Đội" %>
                        </a>
                        <button type="submit" class="btn btn-mint" data-ui-style="rolling-point-config-jsp-027">
                            Tiếp theo <i class="fa-solid fa-arrow-right" data-ui-style="rolling-point-config-jsp-028"></i>
                        </button>
                    </div>
                </form>
            </div>

        </main></div>

    </body>
</html>
