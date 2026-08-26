<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%
    String tournamentId = request.getParameter("id");
    String format = request.getParameter("format");

    String targetPage = "single-elimination.jsp";
    if ("DOUBLE_ELIMINATION".equalsIgnoreCase(format)) {
        targetPage = "double-elimination.jsp";
    } else if ("ROUND_ROBIN".equalsIgnoreCase(format)) {
        targetPage = "round-robin.jsp";
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
