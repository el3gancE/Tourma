<%@tag pageEncoding="UTF-8"%>
<%@attribute name="label" required="true" type="java.lang.String"%>
<%@attribute name="contextName" required="false" type="java.lang.String"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<title><c:out value="${label}" /><c:if test="${not empty contextName}"> · <c:out value="${contextName}" /></c:if> · Tourma</title>
