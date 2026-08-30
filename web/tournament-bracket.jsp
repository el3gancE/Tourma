<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="dao.TournamentDAO"%>
<%@page import="model.Tournament"%>
<%
    String tournamentId = request.getParameter("id");
    String format = request.getParameter("format");

    if ((format == null || format.trim().isEmpty()) && tournamentId != null && !tournamentId.trim().isEmpty()) {
        try {
            TournamentDAO tDao = new TournamentDAO();
            Tournament t = tDao.getTournamentById(tournamentId);
            if (t != null && t.getFormat() != null) {
                format = t.getFormat();
            }
        } catch (Exception ignore) {}
    }

    String targetPage = "single-elimination.jsp";
    if ("DOUBLE_ELIMINATION".equalsIgnoreCase(format)) {
        targetPage = "double-elimination.jsp";
    } else if ("ROUND_ROBIN".equalsIgnoreCase(format)) {
        targetPage = "round-robin.jsp";
    } else if ("GROUP_STAGE".equalsIgnoreCase(format)) {
        targetPage = "manage-group.jsp";
    } else if ("SWISS_LITE".equalsIgnoreCase(format) || "SWISS".equalsIgnoreCase(format)) {
        targetPage = "swiss-stage.jsp";
    }

    String targetUrl = request.getContextPath() + "/common/" + targetPage;
    if (tournamentId != null && !tournamentId.trim().isEmpty()) {
        targetUrl += "?id=" + tournamentId;
        if (format != null && !format.trim().isEmpty()) {
            targetUrl += "&format=" + format;
        }
    }

    response.sendRedirect(targetUrl);
%>
