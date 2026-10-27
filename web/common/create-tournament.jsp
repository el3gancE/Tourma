<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%
    String sIdParam = request.getParameter("seriesId");
    if (sIdParam != null && !sIdParam.trim().isEmpty()) {
        response.sendRedirect(request.getContextPath() + "/rolling/create-tournament?seriesId=" + sIdParam.trim());
        return;
    }
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <!-- Favicon -->
        <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <link rel="alternate icon" href="${pageContext.request.contextPath}/images/trophy-gradient-icon.svg">
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Tạo Giải Đấu Mới - TOURMA</title>

        <!-- Google Font Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">

        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

        <!-- Main Design System CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <!-- Separate Tournament Creation Page CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/create-tournament.css">
    </head>
    <body>
        <!-- Include Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="tournaments"/>
        </jsp:include>

        <main class="container" data-ui-style="create-tournament-jsp-001">
            <div data-ui-style="create-tournament-jsp-002">
                <a href="${pageContext.request.contextPath}/index.jsp" class="text-muted" data-ui-style="create-tournament-jsp-003">
                    <i class="fa-solid fa-arrow-left"></i> Quay lại Trang chủ
                </a>
            </div>

            <!-- Form Container Box -->
            <div class="form-container-box">
                <h1 class="form-header-title">
                    <i class="fa-solid fa-trophy text-mint"></i> Khởi Tạo Giải Đấu Mới
                </h1>
                <p class="text-muted" data-ui-style="create-tournament-jsp-004">
                    Nhập tên giải đấu và mô tả cơ bản để bắt đầu quá trình thiết lập.
                </p>

                <!-- Error Alert Message -->
                <% if (request.getAttribute("errorMessage") != null) { %>
                    <div data-ui-style="create-tournament-jsp-005">
                        <i class="fa-solid fa-triangle-exclamation"></i> <%= request.getAttribute("errorMessage") %>
                    </div>
                <% } %>

                <form id="createTournamentForm" action="${pageContext.request.contextPath}/create-tournament" method="POST">

                    <!-- 1. TÊN GIẢI ĐẤU -->
                    <div class="form-group">
                        <label class="form-label" for="tournamentName">Tên Giải Đấu (*)</label>
                        <input type="text" id="tournamentName" name="name" class="form-control"
                               placeholder="Ví dụ: Giải Bóng Rổ Hà Nội Open 2026" required autofocus>
                    </div>

                    <!-- 2. MÔ TẢ GIẢI ĐẤU -->
                    <div class="form-group">
                        <label class="form-label" for="tournamentDescription">Mô Tả Giải Đấu</label>
                        <textarea id="tournamentDescription" name="description" class="form-control"
                                  data-ui-style="create-tournament-jsp-006"
                                  placeholder="Nhập mô tả ngắn gọn về giải đấu, quy mô, hoặc ghi chú ban tổ chức..."></textarea>
                    </div>

                    <!-- SUBMIT BUTTONS -->
                    <div data-ui-style="create-tournament-jsp-007" style="display: flex;">
                        <a href="${pageContext.request.contextPath}/index.jsp" class="btn btn-secondary">Hủy Bỏ</a>
                        <button type="submit" class="btn btn-mint">
                            TẠO GIẢI ĐẤU & TIẾP TỤC ➔
                        </button>
                    </div>
                </form>
            </div>
        </main>
    </body>
</html>
