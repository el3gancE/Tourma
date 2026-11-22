<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<c:set var="useElo" value="${param.rankingModel == 'FIFA_ELO'}"/>
<!DOCTYPE html>
<html lang="vi" data-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Tạo chuỗi giải | Tourma</title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
    <script src="${pageContext.request.contextPath}/js/theme.js?v=silk-1"></script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/create-flow.css?v=1">
</head>
<body class="ui-page create-flow-page">
    <jsp:include page="/common/component/header.jsp">
        <jsp:param name="active" value="create-series"/>
        <jsp:param name="themeToggle" value="true"/>
    </jsp:include>
    <main class="create-flow">
        <a class="create-flow-back" href="${pageContext.request.contextPath}/index.jsp"><span aria-hidden="true">←</span> Trang Chủ</a>
        <header class="create-flow-heading"><h1>Tạo chuỗi giải</h1><p>Đặt tên chuỗi và chọn cách tính điểm xếp hạng.</p></header>
        <div class="create-flow-grid">
            <form class="create-flow-panel" id="createSeriesForm" action="${pageContext.request.contextPath}/create-series" method="POST">
                <header class="create-flow-panel-heading"><h2>Thông tin chuỗi</h2><small>* Bắt buộc</small></header>
                <div class="create-flow-body">
                    <c:if test="${not empty errorMessage}"><p class="create-flow-error" role="alert"><c:out value="${errorMessage}"/></p></c:if>
                    <div class="create-flow-field">
                        <label for="seriesName">Tên chuỗi giải <span aria-hidden="true">*</span></label>
                        <input type="text" id="seriesName" name="name" placeholder="Ví dụ: City Circuit 2026" value="<c:out value='${param.name}'/>" required>
                    </div>
                    <div class="create-flow-field">
                        <label for="seriesDescription">Mô tả <small>Không bắt buộc</small></label>
                        <textarea id="seriesDescription" name="description" rows="3" placeholder="Nhập mô tả…"><c:out value="${param.description}"/></textarea>
                    </div>
                    <fieldset class="create-flow-model">
                        <legend>Mô hình xếp hạng <span aria-hidden="true">*</span></legend>
                        <div class="create-flow-model-options">
                            <label>
                                <input type="radio" name="rankingModel" value="ROLLING_WINDOW" aria-controls="rollingOptionsPanel" ${useElo ? '' : 'checked'} required>
                                <span><strong>Rolling Window</strong><small>Tích lũy điểm qua nhiều giải trong mỗi Phase.</small></span>
                            </label>
                            <label>
                                <input type="radio" name="rankingModel" value="FIFA_ELO" aria-controls="eloOptionsPanel" ${useElo ? 'checked' : ''}>
                                <span><strong>FIFA Elo Rating</strong><small>Điểm Elo thay đổi theo kết quả từng trận.</small></span>
                            </label>
                        </div>
                    </fieldset>
                    <fieldset class="create-flow-options" id="rollingOptionsPanel" ${useElo ? 'hidden disabled' : ''}>
                        <legend>Thiết lập Rolling Window</legend>
                        <div class="create-flow-field">
                            <label for="phaseSize">Số giải trong một Phase</label>
                            <input type="number" id="phaseSize" name="phaseSize" value="<c:out value='${empty param.phaseSize ? 10 : param.phaseSize}'/>" min="1" max="100" aria-describedby="phaseSizeHelp">
                            <small id="phaseSizeHelp">Ví dụ: 10 giải đầu thuộc Phase 1, 10 giải tiếp theo thuộc Phase 2.</small>
                        </div>
                        <div class="create-flow-field">
                            <label for="initialPoints">Điểm khởi đầu</label>
                            <input type="number" id="initialPoints" name="initialPoints" value="0" readonly>
                        </div>
                    </fieldset>
                    <fieldset class="create-flow-options" id="eloOptionsPanel" ${useElo ? '' : 'hidden disabled'}>
                        <legend>Thiết lập FIFA Elo</legend>
                        <div class="create-flow-field">
                            <label for="initialElo">Điểm Elo khởi điểm</label>
                            <input type="number" id="initialElo" name="initialElo" value="<c:out value='${empty param.initialElo ? 1000 : param.initialElo}'/>" step="10" aria-describedby="initialEloHelp">
                            <small id="initialEloHelp">Áp dụng khi đội mới tham gia chuỗi giải.</small>
                        </div>
                    </fieldset>
                </div>
                <footer class="create-flow-actions">
                    <a href="${pageContext.request.contextPath}/index.jsp">Hủy</a>
                    <button type="submit">Tạo chuỗi giải <span aria-hidden="true">→</span></button>
                </footer>
            </form>
            <aside class="create-flow-aside" aria-labelledby="series-next">
                <h2 id="series-next">Sau khi tạo chuỗi</h2>
                <ol>
                    <li>Thêm đội vào chuỗi</li>
                    <li>Tạo các giải thành phần</li>
                    <li>Theo dõi bảng xếp hạng cả mùa</li>
                </ol>
            </aside>
        </div>
    </main>
    <script src="${pageContext.request.contextPath}/js/create-series.js?v=1"></script>
</body>
</html>
