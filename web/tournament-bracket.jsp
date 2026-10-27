<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="dao.TournamentDAO"%>
<%@page import="model.Tournament"%>
<%@page import="java.util.List"%>
<%
    String tournamentId = request.getParameter("id");
    String format = request.getParameter("format");
    String seriesId = request.getParameter("seriesId");
    String stageParam = request.getParameter("stage");

    Tournament t = null;
    if (tournamentId != null && !tournamentId.trim().isEmpty()) {
        try {
            TournamentDAO tDao = new TournamentDAO();
            t = tDao.getTournamentById(tournamentId);
            if (t != null) {
                if (seriesId == null || seriesId.trim().isEmpty()) {
                    seriesId = t.getSeriesId();
                }
                if (format == null || format.trim().isEmpty()) {
                    if ("MULTI_STAGE".equalsIgnoreCase(t.getTournamentType())) {
                        List<String> sFmts = tDao.getStageFormats(tournamentId);
                        if (sFmts != null && !sFmts.isEmpty()) {
                            format = sFmts.get(0);
                        }
                    }
                    if (format == null || format.trim().isEmpty()) {
                        format = t.getFormat();
                    }
                }
            }
        } catch (Exception ignore) {}
    }

    if (format != null && !format.trim().isEmpty()) {
        String cleanFmt = format.trim().toUpperCase();
        boolean hasGroupAssignments = false;
        if (t != null && t.getGroupAssignments() != null && !t.getGroupAssignments().trim().isEmpty() && !t.getGroupAssignments().trim().equals("{}")) {
            hasGroupAssignments = true;
        }

        String targetPage = "single-elimination.jsp";
        if ("DOUBLE_ELIMINATION".equals(cleanFmt)) {
            targetPage = "double-elimination.jsp";
        } else if ("ROUND_ROBIN".equals(cleanFmt)) {
            targetPage = "round-robin.jsp";
        } else if ("GROUP_STAGE".equals(cleanFmt)) {
            targetPage = hasGroupAssignments ? "group-stage.jsp" : "manage-group.jsp";
        } else if ("GSL".equals(cleanFmt)) {
            targetPage = hasGroupAssignments ? "gsl.jsp" : "manage-group.jsp";
        } else if ("SWISS_LITE".equals(cleanFmt) || "SWISS".equals(cleanFmt)) {
            targetPage = "swiss-stage.jsp";
        }

        String targetUrl = request.getContextPath() + "/common/" + targetPage;
        if (tournamentId != null && !tournamentId.trim().isEmpty()) {
            targetUrl += "?id=" + tournamentId;
            targetUrl += "&format=" + cleanFmt;
            if (seriesId != null && !seriesId.trim().isEmpty()) {
                targetUrl += "&seriesId=" + seriesId.trim();
            }
            if (stageParam != null && !stageParam.trim().isEmpty()) {
                targetUrl += "&stage=" + stageParam.trim();
            }
        }
        response.sendRedirect(targetUrl);
        return;
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Chuyển hướng sơ đồ thi đấu...</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/tournament-bracket.css">
</head>
<body data-ui-style="tournament-bracket-jsp-001" style="display: flex;">
    <div data-ui-style="tournament-bracket-jsp-002">
        <div data-ui-style="tournament-bracket-jsp-003">Đang tải sơ đồ giải đấu...</div>
    </div>
    <script>
        (function() {
            var tid = '<%= (tournamentId != null) ? tournamentId : "" %>';
            var ctx = '<%= request.getContextPath() %>';
            var seriesId = '<%= (seriesId != null) ? seriesId : "" %>';
            var sParam = seriesId ? ('&seriesId=' + encodeURIComponent(seriesId)) : '';

            var targetFormat = 'SINGLE_ELIMINATION';
            if (tid) {
                var localType = localStorage.getItem('tourma_type_' + tid);
                var multiCfgRaw = localStorage.getItem('tourma_multi_config_' + tid);
                if (localType === 'MULTI_STAGE' || multiCfgRaw) {
                    try {
                        var mCfg = JSON.parse(multiCfgRaw);
                        if (mCfg && mCfg.stage1Format) targetFormat = mCfg.stage1Format;
                    } catch(e) {}
                } else {
                    var localFmt = localStorage.getItem('tourma_format_' + tid);
                    if (localFmt) targetFormat = localFmt;
                }
            }

            targetFormat = targetFormat.toUpperCase();
            var hasGroupAssignments = false;
            try {
                var localGroups = localStorage.getItem('tourma_group_assignments_' + tid);
                if (localGroups && localGroups !== '{}' && localGroups !== 'null') hasGroupAssignments = true;
            } catch(e) {}

            var targetPage = 'single-elimination.jsp';
            if (targetFormat === 'DOUBLE_ELIMINATION') targetPage = 'double-elimination.jsp';
            else if (targetFormat === 'ROUND_ROBIN') targetPage = 'round-robin.jsp';
            else if (targetFormat === 'GROUP_STAGE') targetPage = hasGroupAssignments ? 'group-stage.jsp' : 'manage-group.jsp';
            else if (targetFormat === 'GSL') targetPage = hasGroupAssignments ? 'gsl.jsp' : 'manage-group.jsp';
            else if (targetFormat === 'SWISS_LITE' || targetFormat === 'SWISS') targetPage = 'swiss-stage.jsp';

            window.location.replace(ctx + '/common/' + targetPage + '?id=' + encodeURIComponent(tid) + '&format=' + targetFormat + sParam);
        })();
    </script>
</body>
</html>