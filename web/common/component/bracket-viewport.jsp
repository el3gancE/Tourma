<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%-- 
    Document   : bracket-viewport.jsp (Reusable Bracket Viewport Canvas Component)
    Description: Bounded drag-to-pan viewport frame with floating zoom toolbar for rendering bracket trees.
--%>

<link rel="stylesheet" href="${pageContext.request.contextPath}/css/bracket-viewport.css">

<div id="bracketViewportContainer" class="bracket-viewport-container">
    
    <!-- Floating Zoom & Pan Control Toolbar -->
    <div class="bracket-zoom-toolbar">
        <button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.zoomOut()" title="Thu nhỏ (-)">
            <i class="fa-solid fa-minus"></i>
        </button>
        <span id="zoomLevelBadge" class="zoom-level-badge">100%</span>
        <button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.zoomIn()" title="Phóng to (+)">
            <i class="fa-solid fa-plus"></i>
        </button>
        <button type="button" class="btn-zoom" onclick="window.TourmaViewport && window.TourmaViewport.resetZoom()" title="Reset (100%)">
            <i class="fa-solid fa-rotate-right"></i>
        </button>
    </div>

    <!-- Inner Expandable Viewport Canvas Wrapper (Content injected inside) -->
    <div id="bracketViewportCanvas" class="bracket-viewport-canvas">
        <jsp:doBody/>
    </div>

</div>

<script src="${pageContext.request.contextPath}/js/bracket-viewport.js"></script>
