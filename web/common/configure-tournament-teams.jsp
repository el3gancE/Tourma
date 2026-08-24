<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="dao.ParticipantDAO"%>
<%@page import="model.Team"%>
<%@page import="java.util.List"%>
<%
    String tournamentId = request.getParameter("id");
    List<Team> existingTeams = null;
    if (tournamentId != null && !tournamentId.trim().isEmpty()) {
        ParticipantDAO pDao = new ParticipantDAO();
        existingTeams = pDao.getTeamsByTournamentId(tournamentId);
    }
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Quản Lý Danh Sách Đội - TOURMA</title>
        
        <!-- Google Font Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        
        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Main Design System CSS & Dedicated Team Configuration CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/create-tournament.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/configure-tournament-teams.css">
    </head>
    <body>
        <!-- Include Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="create-tournament"/>
        </jsp:include>

        <main class="container" style="max-width: 1100px;">
            <div style="margin-top: 1.25rem;">
                <a href="${pageContext.request.contextPath}/common/configure-tournament-format.jsp?id=${param.id}" class="text-muted" style="font-size: 0.8rem;">
                    <i class="fa-solid fa-arrow-left"></i> Quay lại Bước 2: Thể Thức
                </a>
            </div>

            <!-- CENTERED MAIN TITLE -->
            <h1 class="page-main-title">
                Quản lý danh sách đội
            </h1>

            <form id="configureTeamsForm" action="${pageContext.request.contextPath}/save-teams" method="POST" onsubmit="return prepareFormSubmission(event)">
                <input type="hidden" name="id" value="${param.id}">
                <input type="hidden" name="format" value="${param.format}">
                <input type="hidden" id="finalTeamsInput" name="teamListRaw" value="">

                <!-- 2 INDEPENDENT STANDALONE CARDS SIDE-BY-SIDE -->
                <div class="dual-card-container">
                    
                    <!-- LEFT STANDALONE CARD: THÊM ĐỘI BÊN NÀY -->
                    <div class="standalone-card">
                        <div>
                            <div class="standalone-card-header">
                                <h3 class="standalone-card-title text-mint">
                                    <i class="fa-solid fa-circle-plus"></i> Thêm Đội
                                </h3>
                                <span class="text-muted" style="font-size: 0.75rem;">Ô Nhập Liệu</span>
                            </div>

                            <!-- INNER DARK BOX FOR TEXTAREA -->
                            <div class="inner-dark-box">
                                <textarea id="teamTextarea" class="team-textarea-box" 
                                          placeholder="Dán danh sách tên các đội bóng vào đây:&#10;1. Hà Nội FC&#10;2. Saigon Heat&#10;3. Hải Phòng FC&#10;4. SHB Đà Nẵng&#10;5. Becamex Bình Dương&#10;6. Thép Xanh Nam Định&#10;7. Hoàng Anh Gia Lai&#10;8. Đông Á Thanh Hóa" 
                                          oninput="handleTextareaTyping()" onkeyup="handleTextareaTyping()" onchange="handleTextareaTyping()"></textarea>

                                <!-- Live Parser Summary Bar -->
                                <div class="parser-summary-bar">
                                    <div class="parser-count-badge">
                                        <i class="fa-solid fa-calculator"></i> Số Đội Đang Nhập: <span id="inputCountDisplay">0</span> Đội
                                    </div>
                                    <div id="warningBadge" class="parser-warning-badge" style="display: none;">
                                        <i class="fa-solid fa-triangle-exclamation"></i> Trùng tên đội
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- ADD TEAMS BUTTON -->
                        <button type="button" id="btnAddTeams" class="btn btn-mint" style="width: 100%; font-weight: 700; padding: 0.75rem 1rem; cursor: pointer;" onclick="addTeamsFromInput(); return false;">
                            <i class="fa-solid fa-plus"></i> Thêm Đội ➔
                        </button>
                    </div>

                    <!-- RIGHT STANDALONE CARD: QUẢN LÝ BÊN NÀY -->
                    <div class="standalone-card">
                        <div>
                            <div class="standalone-card-header">
                                <h3 class="standalone-card-title text-gold">
                                    <i class="fa-solid fa-list-check"></i> Quản Lý
                                </h3>
                                <div style="display: flex; gap: 0.4rem;">
                                    <button type="button" class="btn-shuffle-sm" onclick="shuffleCurrentTeams(); return false;">
                                        <i class="fa-solid fa-shuffle"></i> Xáo Trộn
                                    </button>
                                    <button type="button" class="btn-delete-sm" onclick="deleteSelectedTeams(); return false;">
                                        <i class="fa-solid fa-trash"></i> Xóa Đội Chọn
                                    </button>
                                </div>
                            </div>

                            <!-- INNER DARK BOX FOR DRAG TABLE -->
                            <div class="inner-dark-box">
                                <div class="drag-table-container">
                                    <table class="drag-table">
                                        <thead>
                                            <tr>
                                                <th style="width: 80px; text-align: center;">Hạt Giống</th>
                                                <th style="width: 40px; text-align: center;">
                                                    <i class="fa-solid fa-up-down-left-right"></i>
                                                </th>
                                                <th>Tên Đội Bóng</th>
                                                <th style="width: 40px; text-align: center;">
                                                    <input type="checkbox" id="selectAllCheckbox" onchange="toggleSelectAll(this)" style="accent-color: var(--mint-primary); cursor: pointer;">
                                                </th>
                                                <th style="width: 40px; text-align: center;">Xóa</th>
                                            </tr>
                                        </thead>
                                        <tbody id="seedTableBody">
                                            <tr>
                                                <td colspan="5" class="text-muted" style="text-align: center; padding: 2.5rem 0;">
                                                    Chưa có đội bóng nào. Nhập danh sách ở bên trái và bấm Thêm Đội.
                                                </td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </div>

                        <div style="font-size: 0.75rem; color: var(--text-muted); text-align: right;">
                            <i class="fa-solid fa-hand-pointer text-mint"></i> Kéo thả từng hàng để thay đổi thứ tự Hạt Giống
                        </div>
                    </div>

                </div>

                <!-- BOTTOM CENTERED ACTION BUTTONS -->
                <div style="display: flex; justify-content: center; gap: 1rem; margin-top: 2rem; margin-bottom: 2.5rem;">
                    <a href="${pageContext.request.contextPath}/common/configure-tournament-format.jsp?id=${param.id}" class="btn btn-secondary">Quay Lại</a>
                    <button type="submit" class="btn btn-mint" style="padding-left: 2rem; padding-right: 2rem;">
                        SINH SƠ ĐỒ THI ĐẤU ➔
                    </button>
                </div>
            </form>
        </main>

        <script>
            var tournamentId = "<%= (tournamentId != null) ? tournamentId : "" %>";
            var localStorageKey = "tourma_teams_" + tournamentId;

            // Load initial teams from Database first, fallback to localStorage
            var dbTeams = [
                <% 
                if (existingTeams != null && !existingTeams.isEmpty()) {
                    for (int i = 0; i < existingTeams.size(); i++) {
                        String cleanName = existingTeams.get(i).getRawName().replace("\"", "\\\"").replace("\n", "");
                %>
                        "<%= cleanName %>"<%= (i < existingTeams.size() - 1) ? "," : "" %>
                <% 
                    }
                } 
                %>
            ];

            var currentTeamsList = [];

            // Restore state logic
            if (dbTeams.length > 0) {
                currentTeamsList = dbTeams;
            } else if (tournamentId && localStorage.getItem(localStorageKey)) {
                try {
                    currentTeamsList = JSON.parse(localStorage.getItem(localStorageKey)) || [];
                } catch(e) {
                    currentTeamsList = [];
                }
            }

            var dragSrcIndex = null;

            // Auto-save currentTeamsList to localStorage
            function persistState() {
                if (tournamentId) {
                    localStorage.setItem(localStorageKey, JSON.stringify(currentTeamsList));
                }
            }

            // Extract valid team names from raw textarea string
            window.extractNamesFromTextarea = function() {
                var textarea = document.getElementById('teamTextarea');
                if (!textarea || !textarea.value) return [];
                
                var lines = textarea.value.split('\n');
                var names = [];
                for (var i = 0; i < lines.length; i++) {
                    var name = lines[i].trim();
                    if (name.length > 0) {
                        name = name.replace(/^[0-9]+[\.\-\)\s]+/, '').trim();
                        if (name.length > 0) {
                            names.push(name);
                        }
                    }
                }
                return names;
            };

            // Handle typing in left textarea to update input counter & warning badge
            window.handleTextareaTyping = function() {
                var names = window.extractNamesFromTextarea();
                document.getElementById('inputCountDisplay').innerText = names.length;

                var seenNames = new Set(currentTeamsList.map(function(t) { return t.toLowerCase(); }));
                var hasDuplicate = false;

                for (var i = 0; i < names.length; i++) {
                    if (seenNames.has(names[i].toLowerCase())) {
                        hasDuplicate = true;
                    }
                }

                document.getElementById('warningBadge').style.display = hasDuplicate ? 'inline-flex' : 'none';
            };

            // Click "Thêm Đội ➔" button: parse textarea lines and add to managed teams list
            window.addTeamsFromInput = function() {
                var textarea = document.getElementById('teamTextarea');
                var names = window.extractNamesFromTextarea();
                
                if (names.length === 0) return;

                var seenNames = new Set(currentTeamsList.map(function(t) { return t.toLowerCase(); }));
                var addedCount = 0;
                var hasDuplicate = false;

                for (var i = 0; i < names.length; i++) {
                    var name = names[i];
                    if (seenNames.has(name.toLowerCase())) {
                        hasDuplicate = true;
                    } else {
                        seenNames.add(name.toLowerCase());
                        currentTeamsList.push(name);
                        addedCount++;
                    }
                }

                if (addedCount > 0) {
                    textarea.value = ''; // Clear textarea input after adding
                    document.getElementById('inputCountDisplay').innerText = 0;
                    persistState();
                    window.renderTable();
                }

                document.getElementById('warningBadge').style.display = hasDuplicate ? 'inline-flex' : 'none';
            };

            // Shuffle managed teams array
            window.shuffleCurrentTeams = function() {
                if (currentTeamsList.length > 0) {
                    for (var i = currentTeamsList.length - 1; i > 0; i--) {
                        var j = Math.floor(Math.random() * (i + 1));
                        var temp = currentTeamsList[i];
                        currentTeamsList[i] = currentTeamsList[j];
                        currentTeamsList[j] = temp;
                    }
                    persistState();
                    window.renderTable();
                }
            };

            // Render Right Standalone Card Drag-and-Drop Table
            window.renderTable = function() {
                var tbody = document.getElementById('seedTableBody');
                document.getElementById('selectAllCheckbox').checked = false;

                if (currentTeamsList.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="5" class="text-muted" style="text-align: center; padding: 2.5rem 0;">Chưa có đội bóng nào. Nhập danh sách ở bên trái và bấm Thêm Đội.</td></tr>';
                    return;
                }

                var html = '';
                for (var index = 0; index < currentTeamsList.length; index++) {
                    var teamName = currentTeamsList[index];
                    var seedNum = index + 1;
                    html += '<tr class="drag-row" draggable="true" data-index="' + index + '" ' +
                            'ondragstart="handleDragStart(event, ' + index + ')" ' +
                            'ondragover="handleDragOver(event, ' + index + ')" ' +
                            'ondragleave="handleDragLeave(event)" ' +
                            'ondrop="handleDrop(event, ' + index + ')" ' +
                            'ondragend="handleDragEnd(event)">' +
                            '<td style="text-align: center;"><span class="seed-number-box">' + seedNum + '</span></td>' +
                            '<td style="text-align: center;"><i class="fa-solid fa-grip-vertical drag-handle"></i></td>' +
                            '<td style="font-weight: 600;">' + escapeHtml(teamName) + '</td>' +
                            '<td style="text-align: center;">' +
                                '<input type="checkbox" class="team-select-cb" data-index="' + index + '" style="accent-color: var(--mint-primary); cursor: pointer;">' +
                            '</td>' +
                            '<td style="text-align: center;">' +
                                '<button type="button" class="btn-delete-sm" onclick="deleteSingleTeam(' + index + ')">' +
                                    '<i class="fa-solid fa-xmark"></i>' +
                                '</button>' +
                            '</td>' +
                        '</tr>';
                }
                tbody.innerHTML = html;
            };

            /* --- Drag and Drop Logic --- */
            window.handleDragStart = function(e, index) {
                dragSrcIndex = index;
                e.currentTarget.classList.add('dragging');
                e.dataTransfer.effectAllowed = 'move';
                e.dataTransfer.setData('text/plain', index);
            };

            window.handleDragOver = function(e, index) {
                if (e.preventDefault) e.preventDefault();
                e.dataTransfer.dropEffect = 'move';
                var row = e.currentTarget;
                if (dragSrcIndex !== index) {
                    row.classList.add('drag-over');
                }
                return false;
            };

            window.handleDragLeave = function(e) {
                e.currentTarget.classList.remove('drag-over');
            };

            window.handleDrop = function(e, targetIndex) {
                if (e.stopPropagation) e.stopPropagation();
                if (dragSrcIndex !== null && dragSrcIndex !== targetIndex) {
                    var itemToMove = currentTeamsList.splice(dragSrcIndex, 1)[0];
                    currentTeamsList.splice(targetIndex, 0, itemToMove);
                    persistState();
                    window.renderTable();
                }
                return false;
            };

            window.handleDragEnd = function(e) {
                var rows = document.querySelectorAll('.drag-row');
                for (var i = 0; i < rows.length; i++) {
                    rows[i].classList.remove('dragging');
                    rows[i].classList.remove('drag-over');
                }
                dragSrcIndex = null;
            };

            /* --- Select & Delete Operations --- */
            window.toggleSelectAll = function(masterCb) {
                var cbs = document.querySelectorAll('.team-select-cb');
                for (var i = 0; i < cbs.length; i++) {
                    cbs[i].checked = masterCb.checked;
                }
            };

            window.deleteSingleTeam = function(index) {
                currentTeamsList.splice(index, 1);
                persistState();
                window.renderTable();
            };

            window.deleteSelectedTeams = function() {
                var checkboxes = document.querySelectorAll('.team-select-cb:checked');
                if (checkboxes.length === 0) return;

                var indicesToDelete = new Set();
                for (var i = 0; i < checkboxes.length; i++) {
                    indicesToDelete.add(parseInt(checkboxes[i].getAttribute('data-index')));
                }

                currentTeamsList = currentTeamsList.filter(function(_, idx) { return !indicesToDelete.has(idx); });
                persistState();
                window.renderTable();
            };

            window.prepareFormSubmission = function(e) {
                var textarea = document.getElementById('teamTextarea');
                if (textarea && textarea.value && textarea.value.trim().length > 0) {
                    window.addTeamsFromInput();
                }

                if (currentTeamsList.length < 2) {
                    alert('Cần ít nhất 2 đội bóng để sinh sơ đồ thi đấu.');
                    return false;
                }

                document.getElementById('finalTeamsInput').value = currentTeamsList.join('\n');
                return true;
            };

            function escapeHtml(text) {
                return text
                    .replace(/&/g, "&amp;")
                    .replace(/</g, "&lt;")
                    .replace(/>/g, "&gt;")
                    .replace(/"/g, "&quot;")
                    .replace(/'/g, "&#039;");
            }

            // Initial render on page load
            window.addEventListener('DOMContentLoaded', () => {
                window.renderTable();
                window.handleTextareaTyping();
            });
        </script>
    </body>
</html>