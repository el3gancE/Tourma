<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%-- Reusable announcement. Host supplies newNumberOneEligible and newNumberOneName. --%>
<c:if test="${newNumberOneEligible and not empty newNumberOneName}">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/components/new-number-one.css?v=2-banner">
    <section class="new-number-one" data-new-number-one data-view-selector="<c:out value="${param.viewSelector}"/>" aria-labelledby="newNumberOneTitle" aria-describedby="newNumberOneName">
        <div class="new-number-one__art" aria-hidden="true"><span></span><span></span><span></span></div>
        <div class="new-number-one__content">
            <div class="new-number-one__headline"><p class="new-number-one__eyebrow">BẢNG XẾP HẠNG SERIES</p>
            <h2 id="newNumberOneTitle">New #1</h2></div>
            <p id="newNumberOneName" class="new-number-one__name"><c:out value="${newNumberOneName}"/></p>
        </div>
    </section>
    <script src="${pageContext.request.contextPath}/js/components/new-number-one.js?v=2-banner" defer></script>
</c:if>
