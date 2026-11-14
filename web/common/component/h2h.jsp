<%@page contentType="text/html" pageEncoding="UTF-8"%>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/h2h.css?v=4">
<dialog id="tourmaH2h" class="h2h-dialog" aria-labelledby="h2hTitle"
        data-endpoint="${pageContext.request.contextPath}/api/h2h">
    <header class="h2h-header">
        <h2 id="h2hTitle">Lịch sử đối đầu</h2>
        <form method="dialog"><button class="h2h-button h2h-close" aria-label="Đóng" autofocus>×</button></form>
    </header>
    <div class="h2h-body">
        <div class="h2h-pair">
            <div><strong data-h2h-first></strong><span data-h2h-first-wins></span></div>
            <div class="h2h-center"><span data-h2h-total>vs</span><span data-h2h-draws></span></div>
            <div><strong data-h2h-second></strong><span data-h2h-second-wins></span></div>
        </div>
        <p class="h2h-status" data-h2h-status role="status"></p>
        <button type="button" class="h2h-button" data-h2h-retry hidden>Thử lại</button>
        <section data-h2h-results hidden>
            <h3 class="h2h-list-title">Các trận đã gặp</h3>
            <ol class="h2h-history" data-h2h-history aria-label="Các trận đã gặp" tabindex="0"></ol>
        </section>
    </div>
</dialog>
<script src="${pageContext.request.contextPath}/js/h2h.js?v=4"></script>
