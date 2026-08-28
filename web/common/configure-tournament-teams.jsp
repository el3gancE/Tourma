<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="dao.ParticipantDAO"%>
<%@page import="model.Team"%>
<%@page import="java.util.List"%>
<%
    String tournamentId = request.getParameter("id");
    String tourneyFormat = request.getParameter("format");
    List<Team> existingTeams = null;
    if (tournamentId != null && !tournamentId.trim().isEmpty()) {
        ParticipantDAO pDao = new ParticipantDAO();
        existingTeams = pDao.getTeamsByTournamentId(tournamentId);
        if (tourneyFormat == null || tourneyFormat.trim().isEmpty()) {
            try {
                dao.TournamentDAO tDao = new dao.TournamentDAO();
                model.Tournament t = tDao.getTournamentById(tournamentId);
                if (t != null && t.getFormat() != null) {
                    tourneyFormat = t.getFormat();
                }
            } catch(Exception e) {}
        }
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
        
        <!-- Main Design System CSS & Dedicated Team Configuration CSS & Modal CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/create-tournament.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/configure-tournament-teams.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/popup.css">
    </head>
    <body>
        <!-- Include Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="create-tournament"/>
        </jsp:include>

        <!-- Include Shared Tournament Sidebar Component -->
        <jsp:include page="/common/component/sidebar.jsp">
            <jsp:param name="activeStep" value="teams"/>
            <jsp:param name="id" value="${param.id}"/>
        </jsp:include>

        <main class="container has-sidebar">
            <!-- CENTERED MAIN TITLE -->
            <h1 class="page-main-title" style="margin-top: 0;">
                Quản lý danh sách đội
            </h1>

                    <form id="configureTeamsForm" action="${pageContext.request.contextPath}/save-teams" method="POST" onsubmit="return prepareFormSubmission(event)">
                        <input type="hidden" name="id" value="${param.id}">
                        <input type="hidden" name="format" value="<%= (tourneyFormat != null) ? tourneyFormat : "" %>">
                        <input type="hidden" id="finalTeamsInput" name="teamListRaw" value="">

                        <!-- TWO-COLUMN WORKSPACE GRID (MATCHING FIGMA PERFECTLY) -->
                        <div class="team-mgmt-grid">

                            <!-- LEFT COLUMN: BULK IMPORT WORKSPACE -->
                            <div class="team-panel-card">
                                <div class="team-panel-header">
                                    <div class="team-panel-title">
                                        <i class="fa-solid fa-list-check text-mint"></i> Nhập danh sách đội
                                    </div>
                                    <span class="count-badge" id="inputCountDisplay">0 Đội</span>
                                </div>

                                <div class="bulk-input-container">
                                    <textarea id="teamTextarea" class="team-textarea-input"
                                              placeholder="Nhập hoặc dán danh sách đội (mỗi đội một dòng)...&#10;Ví dụ:&#10;Hà Nội FC&#10;Hải Phòng FC&#10;Saigon Heat"
                                              oninput="window.handleTextareaTyping()"></textarea>
                                </div>

                                <div class="input-actions-bar">
                                    <button type="button" class="btn btn-secondary" onclick="window.clearTextarea()">
                                        <i class="fa-solid fa-eraser"></i> Xóa Hết
                                    </button>
                                    <button type="button" class="btn btn-mint" onclick="window.addTeamsFromInput()">
                                        <i class="fa-solid fa-plus"></i> Thêm Vào Danh Sách
                                    </button>
                                </div>
                            </div>

                            <!-- RIGHT COLUMN: SEEDING & ORDER MANAGEMENT -->
                            <div class="team-panel-card">
                                <div class="team-panel-header">
                                    <div class="team-panel-title">
                                        <i class="fa-solid fa-ranking-star text-gold"></i> Quản lý hạt giống & phân cặp
                                    </div>
                                    <span class="count-badge" id="managedCountDisplay">0 Đội</span>
                                </div>

                                <div class="manage-toolbar">
                                    <div class="manage-toolbar-left">
                                        <button type="button" class="btn btn-secondary" onclick="window.shuffleTeams()">
                                            <i class="fa-solid fa-shuffle"></i> Xáo Trộn Ngẫu Nhiên
                                        </button>
                                    </div>
                                    <div class="manage-toolbar-right">
                                        <button type="button" class="btn btn-secondary" style="color: #ef4444; border-color: rgba(239, 68, 68, 0.3);" onclick="window.deleteSelectedTeams()">
                                            <i class="fa-solid fa-trash-can"></i> Xóa Đã Chọn
                                        </button>
                                    </div>
                                </div>

                                <!-- SEEDING TABLE -->
                                <div class="table-container">
                                    <div class="table-scroll-wrapper">
                                        <table class="table" id="teamsTable">
                                            <thead>
                                                <tr>
                                                    <th style="width: 48px; text-align: center;">No.</th>
                                                    <th>Tên Đội</th>
                                                    <th style="width: 50px; text-align: center;">Kéo</th>
                                                    <th style="width: 45px; text-align: center;">Xóa</th>
                                                    <th style="width: 38px; text-align: center;">
                                                        <input type="checkbox" id="selectAllCheckbox" onchange="window.toggleSelectAll(this)" title="Chọn tất cả">
                                                    </th>
                                                </tr>
                                            </thead>
                                            <tbody id="teamsTableBody">
                                                <!-- Dynamic JS Rows Injected Here -->
                                            </tbody>
                                        </table>
                                    </div>
                                </div>

                                <div style="font-size: 0.75rem; color: var(--text-muted); text-align: right; margin-top: 0.5rem;">
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

        <!-- RESET BRACKET MODAL UPON ANY TEAM MODIFICATION -->
        <div id="resetBracketModalBackdrop" class="tourma-modal-backdrop" onclick="if(event.target === this) closeResetModal();">
            <div class="tourma-modal-card" style="max-width: 480px; border-color: rgba(244, 63, 94, 0.4);" onclick="event.stopPropagation();">
                <div class="modal-header-bar" style="border-bottom: 1px solid rgba(244, 63, 94, 0.2);">
                    <div class="modal-header-title" style="color: #f43f5e; font-size: 0.95rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem;">
                        <i class="fa-solid fa-triangle-exclamation"></i>
                        <span>Xác Nhận Thay Đổi & Reset Nhánh Đấu</span>
                    </div>
                    <button type="button" class="modal-close-btn" onclick="closeResetModal()" title="Hủy bỏ">
                        <i class="fa-solid fa-xmark"></i>
                    </button>
                </div>
                
                <div class="modal-body-content" style="padding: 1.25rem 1rem;">
                    <div style="background: rgba(244, 63, 94, 0.08); border: 1px solid rgba(244, 63, 94, 0.2); border-radius: 8px; padding: 0.85rem; margin-bottom: 1rem; color: #cbd5e1; font-size: 0.88rem; line-height: 1.5;">
                        <strong style="color: #f43f5e;">⚠️ Cảnh báo:</strong><br>
                        Việc này sẽ reset toàn bộ nhánh thi đấu của bạn, bạn có muốn thay đổi không?
                    </div>
                    <p style="color: #94a3b8; font-size: 0.8rem; margin: 0;">
                        Nếu bấm <strong>Hủy Bỏ</strong>, hệ thống sẽ hoàn tác và giữ nguyên danh sách đội & nhánh đấu trước đó.
                    </p>
                </div>

                <div class="modal-footer-bar" style="display: flex; justify-content: flex-end; gap: 0.65rem;">
                    <button type="button" class="btn btn-secondary" onclick="closeResetModal()" style="font-size: 0.8rem; padding: 0.45rem 1rem;">Hủy Bỏ</button>
                    <button type="button" class="btn" style="background: #f43f5e; color: #ffffff; border: none; font-size: 0.8rem; font-weight: 700; padding: 0.45rem 1.25rem; border-radius: 6px; cursor: pointer;" onclick="confirmResetAndSubmit()">
                        <i class="fa-solid fa-rotate-right"></i> Xác Nhận Đổi
                    </button>
                </div>
            </div>
        </div>

        <!-- SHUFFLE TEAMS CONFIRMATION WARNING MODAL -->
        <div id="shuffleWarningModalBackdrop" class="tourma-modal-backdrop" onclick="if(event.target === this) closeShuffleModal();">
            <div class="tourma-modal-card" style="max-width: 480px; border-color: rgba(251, 191, 36, 0.4);" onclick="event.stopPropagation();">
                <div class="modal-header-bar" style="border-bottom: 1px solid rgba(251, 191, 36, 0.2);">
                    <div class="modal-header-title" style="color: #fbbf24; font-size: 0.95rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem;">
                        <i class="fa-solid fa-shuffle"></i>
                        <span>Xác Nhận Xáo Trộn Hạt Giống</span>
                    </div>
                    <button type="button" class="modal-close-btn" onclick="closeShuffleModal()" title="Đóng">
                        <i class="fa-solid fa-xmark"></i>
                    </button>
                </div>
                
                <div class="modal-body-content" style="padding: 1.25rem 1rem;">
                    <div style="background: rgba(251, 191, 36, 0.08); border: 1px solid rgba(251, 191, 36, 0.2); border-radius: 8px; padding: 0.85rem; margin-bottom: 1rem; color: #cbd5e1; font-size: 0.82rem; line-height: 1.5;">
                        <strong style="color: #fbbf24;">⚠️ Cảnh báo trận đấu đã diễn ra:</strong><br>
                        Giải đấu này đã có các trận đấu diễn ra. Việc xáo trộn danh sách hạt giống sẽ thay đổi toàn bộ các cặp đấu và <strong style="color: #ffffff;">XÓA TOÀN BỘ kết quả các trận cũ</strong> khi bạn bấm Sinh Lại Sơ Đồ.
                    </div>
                    <p style="color: #94a3b8; font-size: 0.8rem; margin: 0;">
                        Bạn có chắc chắn muốn xáo trộn ngẫu nhiên thứ tự các đội không?
                    </p>
                </div>

                <div class="modal-footer-bar" style="display: flex; justify-content: flex-end; gap: 0.65rem;">
                    <button type="button" class="btn btn-secondary" onclick="closeShuffleModal()" style="font-size: 0.8rem; padding: 0.45rem 1rem;">Hủy Bỏ</button>
                    <button type="button" class="btn" style="background: #fbbf24; color: #0b0d12; border: none; font-size: 0.8rem; font-weight: 700; padding: 0.45rem 1.25rem; border-radius: 6px; cursor: pointer;" onclick="confirmShuffleAndExecute()">
                        <i class="fa-solid fa-shuffle"></i> Xác Nhận Xáo Trộn
                    </button>
                </div>
            </div>
        </div>

        <script>
            var tournamentId = "<%= (tournamentId != null) ? tournamentId : "" %>";
            var tourneyFormatServer = "<%= (tourneyFormat != null) ? tourneyFormat.toUpperCase() : "" %>";
            var localStorageKey = "tourma_teams_" + tournamentId;

            // Helper to check if format is Round Robin
            function checkIsRoundRobin() {
                if (tourneyFormatServer === 'ROUND_ROBIN' || tourneyFormatServer === 'ROUNDROBIN') return true;
                var urlFormat = new URLSearchParams(window.location.search).get('format');
                if (urlFormat && (urlFormat.toUpperCase() === 'ROUND_ROBIN' || urlFormat.toUpperCase() === 'ROUNDROBIN')) return true;
                if (tournamentId) {
                    var localFmt = localStorage.getItem('tourma_format_' + tournamentId);
                    if (localFmt && localFmt.toUpperCase() === 'ROUND_ROBIN') return true;
                    if (localStorage.getItem('tourma_rr_matches_' + tournamentId)) return true;
                }
                return false;
            }

            // Helper to check if two team lists contain the exact same team names (ignoring order)
            function isSameTeamSet(listA, listB) {
                if (!listA || !listB) return false;
                if (listA.length !== listB.length) return false;
                var sortedA = listA.slice().sort();
                var sortedB = listB.slice().sort();
                for (var i = 0; i < sortedA.length; i++) {
                    if (sortedA[i] !== sortedB[i]) return false;
                }
                return true;
            }

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
            var initialTeamsSnapshot = null;

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

            // Restore initial teams snapshot for change detection
            if (tournamentId) {
                try {
                    initialTeamsSnapshot = JSON.parse(localStorage.getItem('tourma_teams_snapshot_' + tournamentId));
                } catch(e) {
                    initialTeamsSnapshot = null;
                }
            }

            // If snapshot not saved yet but bracket exists, initialize snapshot
            if (!initialTeamsSnapshot && tournamentId && (localStorage.getItem('tourma_matches_' + tournamentId) || localStorage.getItem('tourma_de_matches_' + tournamentId) || localStorage.getItem('tourma_rr_matches_' + tournamentId))) {
                initialTeamsSnapshot = JSON.parse(JSON.stringify(currentTeamsList));
                try {
                    localStorage.setItem('tourma_teams_snapshot_' + tournamentId, JSON.stringify(initialTeamsSnapshot));
                } catch(e) {}
            }

            var dragSrcIndex = null;

            // Auto-save currentTeamsList to localStorage
            function persistState() {
                if (tournamentId) {
                    localStorage.setItem(localStorageKey, JSON.stringify(currentTeamsList));
                }
            }

            // Normalize team name: trim leading/trailing spaces and collapse multiple inner spaces into 1
            window.normalizeTeamName = function(name) {
                if (!name) return '';
                return name.trim().replace(/\s+/g, ' ');
            };

            // Extract valid team names from raw textarea string
            window.extractNamesFromTextarea = function() {
                var textarea = document.getElementById('teamTextarea');
                if (!textarea || !textarea.value) return [];
                
                var lines = textarea.value.split('\n');
                var names = [];
                for (var i = 0; i < lines.length; i++) {
                    var cleaned = window.normalizeTeamName(lines[i]);
                    if (cleaned.length > 0) {
                        names.push(cleaned);
                    }
                }
                return names;
            };

            // Handle realtime input counter
            window.handleTextareaTyping = function() {
                var names = window.extractNamesFromTextarea();
                var countDisplay = document.getElementById('inputCountDisplay');
                if (countDisplay) {
                    if (names.length > 24) {
                        countDisplay.innerHTML = '<span style="color:#f43f5e; font-weight:700;">' + names.length + ' / 24 Đội (Vượt tối đa)</span>';
                    } else {
                        countDisplay.innerText = names.length + " / 24 Đội";
                    }
                }
            };

            // Clear textarea helper
            window.clearTextarea = function() {
                var textarea = document.getElementById('teamTextarea');
                if (textarea) {
                    textarea.value = '';
                    window.handleTextareaTyping();
                }
            };

            // Add teams from left bulk input to right managed list with exact duplicate prevention
            window.addTeamsFromInput = function() {
                var newNames = window.extractNamesFromTextarea();
                if (newNames.length === 0) return;

                // 1. Check duplicate within the textarea input itself
                var seenInput = {};
                for (var a = 0; a < newNames.length; a++) {
                    var nameA = newNames[a];
                    if (seenInput[nameA]) {
                        alert('Phát hiện tên đội bị trùng lặp trong ô nhập: "' + nameA + '"! Vui lòng đặt tên khác nhau.');
                        return;
                    }
                    seenInput[nameA] = true;
                }

                // 2. Check duplicate against existing managed teams (exact match, case-sensitive)
                for (var b = 0; b < newNames.length; b++) {
                    var nameB = newNames[b];
                    for (var c = 0; c < currentTeamsList.length; c++) {
                        if (currentTeamsList[c] === nameB) {
                            alert('Tên đội "' + nameB + '" đã tồn tại trong danh sách! Vui lòng không đặt tên đội trùng nhau.');
                            return;
                        }
                    }
                }

                if (currentTeamsList.length >= 24) {
                    alert("Số lượng đội tham gia đã đạt tối đa 24 đội!");
                    return;
                }

                var availableSlots = 24 - currentTeamsList.length;
                if (newNames.length > availableSlots) {
                    alert("Hệ thống chỉ hỗ trợ tối đa 24 đội. Đã tự động thêm " + availableSlots + " đội đầu tiên!");
                    newNames = newNames.slice(0, availableSlots);
                }

                for (var i = 0; i < newNames.length; i++) {
                    currentTeamsList.push(newNames[i]);
                }

                persistState();
                window.clearTextarea();
                window.renderTable();
            };

            // Check if tournament has matches already started or completed
            window.checkTournamentHasOngoingMatches = function() {
                if (!tournamentId) return false;
                try {
                    var matchesObj = JSON.parse(localStorage.getItem('tourma_matches_' + tournamentId));
                    if (matchesObj) {
                        var keys = Object.keys(matchesObj);
                        for (var i = 0; i < keys.length; i++) {
                            var m = matchesObj[keys[i]];
                            if (m.status === 'COMPLETED' || m.status === 'done' || (m.team1 && m.team1.score !== '') || (m.team2 && m.team2.score !== '')) {
                                return true;
                            }
                        }
                    }
                } catch(e) {}
                return false;
            };

            window.openShuffleModal = function() {
                var modal = document.getElementById('shuffleWarningModalBackdrop');
                if (modal) {
                    modal.classList.add('show');
                    document.body.style.overflow = 'hidden';
                }
            };

            window.closeShuffleModal = function() {
                var modal = document.getElementById('shuffleWarningModalBackdrop');
                if (modal) {
                    modal.classList.remove('show');
                    document.body.style.overflow = '';
                }
            };

            window.executeShuffleLogic = function() {
                for (var i = currentTeamsList.length - 1; i > 0; i--) {
                    var j = Math.floor(Math.random() * (i + 1));
                    var temp = currentTeamsList[i];
                    currentTeamsList[i] = currentTeamsList[j];
                    currentTeamsList[j] = temp;
                }
                persistState();
                window.renderTable();
            };

            // Shuffle current teams randomly (instantly shuffles table order)
            window.shuffleTeams = function() {
                if (currentTeamsList.length <= 1) return;
                window.executeShuffleLogic();
            };

            // Render right table rows dynamically
            window.renderTable = function() {
                var tbody = document.getElementById('teamsTableBody');
                tbody.innerHTML = '';

                // Update Header Managed Count Badge
                var managedCountBadge = document.getElementById('managedCountDisplay');
                if (managedCountBadge) {
                    managedCountBadge.innerText = currentTeamsList.length + " Đội";
                }

                for (var i = 0; i < currentTeamsList.length; i++) {
                    var teamName = currentTeamsList[i];
                    var seedNum = i + 1;

                    var tr = document.createElement('tr');
                    tr.className = 'team-table-row';
                    tr.draggable = true;
                    tr.setAttribute('data-index', i);

                    tr.innerHTML = 
                        '<td style="text-align: center;">' +
                            '<span class="seed-badge">' + seedNum + '</span>' +
                        '</td>' +
                        '<td>' +
                            '<input type="text" class="team-name-input" value="' + escapeHtml(teamName) + '" onchange="window.updateTeamName(' + i + ', this.value)">' +
                        '</td>' +
                        '<td style="text-align: center;">' +
                            '<div class="drag-handle" title="Kéo để đổi hạt giống">' +
                                '<i class="fa-solid fa-grip-vertical"></i>' +
                            '</div>' +
                        '</td>' +
                        '<td style="text-align: center;">' +
                            '<button type="button" class="btn-delete-row" title="Xóa đội" onclick="window.deleteSingleTeam(' + i + ')">' +
                                '<i class="fa-solid fa-xmark"></i>' +
                            '</button>' +
                        '</td>' +
                        '<td style="text-align: center;">' +
                            '<input type="checkbox" class="team-select-cb" data-index="' + i + '">' +
                        '</td>';

                    // Attach HTML5 Drag & Drop events
                    tr.addEventListener('dragstart', window.handleDragStart);
                    tr.addEventListener('dragover', window.handleDragOver);
                    tr.addEventListener('drop', window.handleDrop);
                    tr.addEventListener('dragend', window.handleDragEnd);

                    tbody.appendChild(tr);
                }
            };

            // Update team name in place
            window.updateTeamName = function(index, newName) {
                var cleaned = window.normalizeTeamName(newName);
                if (cleaned.length === 0) {
                    window.renderTable();
                    return;
                }

                // Check if this cleaned name already exists at any OTHER index in currentTeamsList (exact match)
                for (var i = 0; i < currentTeamsList.length; i++) {
                    if (i !== index && currentTeamsList[i] === cleaned) {
                        alert('Tên đội "' + cleaned + '" đã tồn tại trong danh sách! Vui lòng không đặt tên đội trùng nhau.');
                        window.renderTable();
                        return;
                    }
                }

                currentTeamsList[index] = cleaned;
                persistState();
                window.renderTable();
            };

            /* --- Drag & Drop Handlers --- */
            window.handleDragStart = function(e) {
                dragSrcIndex = parseInt(this.getAttribute('data-index'));
                this.classList.add('dragging');
                e.dataTransfer.effectAllowed = 'move';
            };

            window.handleDragOver = function(e) {
                e.preventDefault();
                e.dataTransfer.dropEffect = 'move';
                return false;
            };

            window.handleDrop = function(e) {
                e.stopPropagation();
                var targetIndex = parseInt(this.getAttribute('data-index'));
                if (dragSrcIndex !== null && dragSrcIndex !== targetIndex) {
                    var movedItem = currentTeamsList.splice(dragSrcIndex, 1)[0];
                    currentTeamsList.splice(targetIndex, 0, movedItem);
                    persistState();
                    window.renderTable();
                }
                return false;
            };

            window.handleDragEnd = function() {
                var rows = document.querySelectorAll('.team-table-row');
                for (var i = 0; i < rows.length; i++) {
                    rows[i].classList.remove('dragging');
                }
                dragSrcIndex = null;
            };

            /* --- Delete Operations --- */
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

            /* --- Confirmation Warning Modal Logic --- */
            var bypassWarning = false;

            window.openResetModal = function() {
                var modal = document.getElementById('resetBracketModalBackdrop');
                if (modal) {
                    modal.classList.add('show');
                    document.body.style.overflow = 'hidden';
                }
            };

            window.closeResetModal = function() {
                var modal = document.getElementById('resetBracketModalBackdrop');
                if (modal) {
                    modal.classList.remove('show');
                    document.body.style.overflow = '';
                }
                // If user cancels and there was an existing snapshot, revert back to the snapshot!
                if (initialTeamsSnapshot && initialTeamsSnapshot.length > 0) {
                    currentTeamsList = JSON.parse(JSON.stringify(initialTeamsSnapshot));
                    persistState();
                    window.renderTable();
                }
            };

            window.confirmResetAndSubmit = function() {
                if (tournamentId) {
                    try {
                        localStorage.removeItem('tourma_matches_' + tournamentId);
                        localStorage.removeItem('tourma_de_matches_' + tournamentId);
                        localStorage.removeItem('tourma_rr_matches_' + tournamentId);
                        localStorage.removeItem('tourma_rr_round_inputs_' + tournamentId);
                        localStorage.removeItem('tourma_matches_demo');
                        localStorage.removeItem('tourma_de_matches_demo');
                        localStorage.removeItem('tourma_rr_matches_demo');
                        localStorage.removeItem('tourma_rr_round_inputs_demo');
                        localStorage.setItem('tourma_teams_snapshot_' + tournamentId, JSON.stringify(currentTeamsList));
                    } catch (e) {}
                }
                persistState();
                bypassWarning = true;
                var modal = document.getElementById('resetBracketModalBackdrop');
                if (modal) {
                    modal.classList.remove('show');
                    document.body.style.overflow = '';
                }
                document.getElementById('finalTeamsInput').value = currentTeamsList.join('\n');
                document.getElementById('configureTeamsForm').submit();
            };

            window.prepareFormSubmission = function(e) {
                if (bypassWarning) return true;

                var textarea = document.getElementById('teamTextarea');
                if (textarea && textarea.value && textarea.value.trim().length > 0) {
                    window.addTeamsFromInput();
                }

                // Clean and normalize all names in currentTeamsList, then verify no duplicates
                var seenNames = {};
                for (var t = 0; t < currentTeamsList.length; t++) {
                    var tName = window.normalizeTeamName(currentTeamsList[t]);
                    currentTeamsList[t] = tName;
                    if (seenNames[tName]) {
                        alert('Phát hiện tên đội bị trùng lặp: "' + tName + '"! Mỗi đội cần có tên riêng biệt.');
                        if (e && e.preventDefault) e.preventDefault();
                        return false;
                    }
                    seenNames[tName] = true;
                }

                if (currentTeamsList.length < 2) {
                    alert('Cần ít nhất 2 đội bóng để sinh sơ đồ thi đấu.');
                    if (e && e.preventDefault) e.preventDefault();
                    return false;
                }

                var isRR = checkIsRoundRobin();

                // Check if bracket was previously generated
                var hasPriorBracket = (initialTeamsSnapshot && initialTeamsSnapshot.length > 0);
                if (!hasPriorBracket && tournamentId && (localStorage.getItem('tourma_matches_' + tournamentId) || localStorage.getItem('tourma_de_matches_' + tournamentId) || localStorage.getItem('tourma_rr_matches_' + tournamentId))) {
                    hasPriorBracket = true;
                }

                // Check if modification occurred
                var isModified = false;
                if (hasPriorBracket && initialTeamsSnapshot) {
                    if (isRR) {
                        // In Round Robin: ONLY consider modified if teams were added, removed, or renamed.
                        // Shuffling the same team list does NOT reset the RR schedule!
                        isModified = !isSameTeamSet(currentTeamsList, initialTeamsSnapshot);
                    } else {
                        // For SE and DE: any seed order change alters the bracket tree pairings
                        isModified = (JSON.stringify(currentTeamsList) !== JSON.stringify(initialTeamsSnapshot));
                    }
                }

                // If bracket existed and teams were modified, show the confirmation popup
                if (hasPriorBracket && isModified) {
                    if (e && e.preventDefault) e.preventDefault();
                    window.openResetModal();
                    return false;
                }

                // Save current snapshot
                if (tournamentId) {
                    try {
                        localStorage.setItem('tourma_teams_snapshot_' + tournamentId, JSON.stringify(currentTeamsList));
                    } catch(e) {}
                }

                persistState();
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