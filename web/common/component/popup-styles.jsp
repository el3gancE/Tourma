<%@page contentType="text/html" pageEncoding="UTF-8"%>
<% if (request.getAttribute("tourmaPopupStylesLoaded") == null) {
    request.setAttribute("tourmaPopupStylesLoaded", Boolean.TRUE);
%>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css?v=mono-1">
<% } %>
