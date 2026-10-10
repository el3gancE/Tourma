<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="dao.TournamentDAO"%>
<%@page import="model.Tournament"%>
<%@page import="java.util.List"%>
<%
    String tournamentId = request.getParameter("id");
    String format = request.getParameter("format");
    String seriesId = request.getParameter("seriesId");
    String stageParam = request.getParameter("stage");

    if (tournamentId != null && !tournamentId.trim().isEmpty()) {
        try {
            TournamentDAO tDao = new TournamentDAO();
            Tournament t = tDao.getTournamentById(tournamentId);
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
        String targetPage = "single-elimination.jsp";
        if ("DOUBLE_ELIMINATION".equals(cleanFmt)) {
            targetPage = "double-elimination.jsp";
        } else if ("ROUND_ROBIN".equals(cleanFmt)) {
            targetPage = "round-robin.jsp";
        } else if ("GROUP_STAGE".equals(cleanFmt)) {
            targetPage = "group-stage.jsp";
        } else if ("GSL".equals(cleanFmt)) {
            targetPage = "gsl.jsp";
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
</head>
<body style="background: #0b0d12; color: #f8fafc; font-family: sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0;">
    <div style="text-align: center;">
        <div style="font-size: 1.1rem; margin-bottom: 0.5rem; color: #2dd4bf;">Đang tải sơ đồ giải đấu...</div>
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
            var targetPage = 'single-elimination.jsp';
            if (targetFormat === 'DOUBLE_ELIMINATION') targetPage = 'double-elimination.jsp';
            else if (targetFormat === 'ROUND_ROBIN') targetPage = 'round-robin.jsp';
            else if (targetFormat === 'GROUP_STAGE') targetPage = 'group-stage.jsp';
            else if (targetFormat === 'GSL') targetPage = 'gsl.jsp';
            else if (targetFormat === 'SWISS_LITE' || targetFormat === 'SWISS') targetPage = 'swiss-stage.jsp';

            window.location.replace(ctx + '/common/' + targetPage + '?id=' + encodeURIComponent(tid) + '&format=' + targetFormat + sParam);
        })();
    </script>
</body>
</html>