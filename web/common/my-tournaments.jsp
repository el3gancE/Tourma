<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Giải Đấu Của Tôi - TOURMA</title>
        
        <!-- Google Font Lexend -->
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        
        <!-- FontAwesome Icons -->
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        
        <!-- Main Design System CSS & Dedicated My Tournaments CSS -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/my-tournaments.css">
    </head>
    <body>
        <!-- Include Shared Navigation Header Component -->
        <jsp:include page="/common/component/header.jsp">
            <jsp:param name="active" value="my-tournaments"/>
        </jsp:include>

        <main class="container">
            
            <!-- Page Header Row -->
            <div class="page-header-row">
                <div>
                    <h1 class="page-title">
                        <i class="fa-solid fa-sitemap text-mint"></i> Giải Đấu Của Tôi
                    </h1>
                    <p class="text-muted" style="font-size: 0.85rem; margin-top: 0.2rem;">
                        Danh sách các giải đấu đơn lẻ và giải đấu thuộc Series đang quản lý.
                    </p>
                </div>
                <div>
                    <a href="${pageContext.request.contextPath}/common/create-tournament.jsp" class="btn btn-mint">
                        <i class="fa-solid fa-plus"></i> Tạo Giải Đấu Mới
                    </a>
                </div>
            </div>

            <!-- Toolbar & Filters -->
            <div class="toolbar-bar">
                <div class="filter-pills-group">
                    <button type="button" class="filter-pill-btn active" onclick="filterTournaments('ALL', this)">Tất Cả</button>
                    <button type="button" class="filter-pill-btn" onclick="filterTournaments('STANDALONE', this)">Giải Đơn Lẻ</button>
                    <button type="button" class="filter-pill-btn" onclick="filterTournaments('SERIES', this)">Thuộc Series</button>
                    <button type="button" class="filter-pill-btn" onclick="filterTournaments('INCOMING', this)">Incoming</button>
                    <button type="button" class="filter-pill-btn" onclick="filterTournaments('IN_PROGRESS', this)">In Progress</button>
                    <button type="button" class="filter-pill-btn" onclick="filterTournaments('COMPLETED', this)">Completed</button>
                </div>

                <div class="search-input-box">
                    <i class="fa-solid fa-magnifying-glass"></i>
                    <input type="text" id="searchInput" class="form-control" placeholder="Tìm kiếm tên giải..." onkeyup="searchTournaments()">
                </div>
            </div>

            <!-- Tournament Cards Container -->
            <c:choose>
                <c:when test="${not empty tournamentList}">
                    <div class="tourney-cards-grid" id="tourneyGrid">
                        <c:forEach var="t" items="${tournamentList}">
                            <div class="tourney-card" 
                                 data-id="${t.id}"
                                 data-db-status="${t.status}"
                                 data-db-champion="${t.championName}"
                                 data-context="${empty t.seriesId ? 'STANDALONE' : 'SERIES'}"
                                 data-status="${t.status == 'COMPLETED' ? 'COMPLETED' : (t.status == 'ONGOING' ? 'IN_PROGRESS' : 'INCOMING')}"
                                 data-name="${t.name}">
                                
                                <div class="tourney-card-main">
                                    <div class="tourney-card-top-row">
                                        <h3 class="tourney-card-title">${t.name}</h3>
                                        <span class="tourney-badge-type">
                                            ${t.tournamentType == 'MULTI_STAGE' ? 'MULTI STAGE' : 'SINGLE STAGE'}
                                        </span>
                                        <c:if test="${not empty t.tierName}">
                                            <span class="tourney-badge-tier">TIER ${t.tierName}</span>
                                        </c:if>
                                        <span class="status-pill ${t.status == 'COMPLETED' ? 'completed' : (t.status == 'ONGOING' ? 'in-progress' : 'incoming')}" id="statusPill_${t.id}">
                                            ${t.status == 'COMPLETED' ? 'Completed' : (t.status == 'ONGOING' ? 'In Progress' : 'Incoming')}
                                        </span>
                                    </div>

                                    <div class="tourney-card-meta">
                                        <!-- TOTAL TEAMS COUNT META -->
                                        <span class="tourney-teams-meta" id="teamMeta_${t.id}">
                                            <i class="fa-solid fa-users text-mint"></i> 
                                            <span class="team-count-val" style="color: #f8fafc; font-weight: 700;">0 Đội</span>
                                        </span>

                                        <span class="meta-divider">•</span>

                                        <c:choose>
                                            <c:when test="${not empty t.seriesId}">
                                                <span><i class="fa-solid fa-layer-group text-gold"></i> Thuộc Series</span>
                                                <span class="meta-divider">•</span>
                                                <span><i class="fa-solid fa-flag"></i> Giải thứ ${t.tournamentIndexInSeries} (Phase ${t.phaseNumber})</span>
                                            </c:when>
                                            <c:otherwise>
                                                <span class="tourney-format-span">
                                                    <i class="fa-solid fa-diagram-project text-mint"></i> 
                                                    <c:choose>
                                                        <c:when test="${t.format == 'DOUBLE_ELIMINATION'}">Double Elimination</c:when>
                                                        <c:when test="${t.format == 'ROUND_ROBIN'}">Round Robin</c:when>
                                                        <c:when test="${t.format == 'SWISS_LITE'}">Swiss System</c:when>
                                                        <c:when test="${t.format == 'GROUP_STAGE'}">Group Stage</c:when>
                                                        <c:otherwise>Single Elimination</c:otherwise>
                                                    </c:choose>
                                                </span>
                                            </c:otherwise>
                                        </c:choose>

                                        <!-- INLINE CHAMPION META (When Completed) -->
                                        <span id="championMeta_${t.id}" class="tourney-champion-meta" style="${(t.status == 'COMPLETED' || not empty t.championName) ? 'display: inline-flex;' : 'display: none;'}">
                                            <span class="meta-divider">•</span>
                                            <i class="fa-solid fa-trophy text-gold"></i> Nhà vô địch: <span class="champion-name-val" style="color: #fbbf24;">${not empty t.championName ? t.championName : ''}</span>
                                        </span>
                                    </div>
                                </div>

                                <div class="tourney-card-footer">
                                    <div class="tourney-card-actions">
                                        <button type="button" class="btn-delete-tourney" 
                                                onclick="openDeleteTourneyModal('${t.id}', '${t.name}')" 
                                                title="Xóa giải đấu">
                                            <i class="fa-solid fa-trash-can"></i> Xóa
                                        </button>
                                        <a href="${pageContext.request.contextPath}/common/configure-tournament-format.jsp?id=${t.id}" class="btn-details-tourney" title="Cấu hình & Chi tiết giải đấu">
                                            <i class="fa-solid fa-sliders"></i> Chi Tiết
                                        </a>
                                        <c:choose>
                                            <c:when test="${t.format == 'DOUBLE_ELIMINATION'}">
                                                <a href="${pageContext.request.contextPath}/common/double-elimination.jsp?id=${t.id}&format=DOUBLE_ELIMINATION" class="btn-view-bracket-card">
                                                    Trận Đấu ➔
                                                </a>
                                            </c:when>
                                            <c:when test="${t.format == 'ROUND_ROBIN'}">
                                                <a href="${pageContext.request.contextPath}/common/round-robin.jsp?id=${t.id}&format=ROUND_ROBIN" class="btn-view-bracket-card">
                                                    Trận Đấu ➔
                                                </a>
                                            </c:when>
                                            <c:when test="${t.format == 'GROUP_STAGE'}">
                                                <a href="${pageContext.request.contextPath}/common/group-stage.jsp?id=${t.id}&format=GROUP_STAGE" class="btn-view-bracket-card">
                                                    Trận Đấu ➔
                                                </a>
                                            </c:when>
                                            <c:when test="${t.format == 'SWISS_LITE' || t.format == 'SWISS'}">
                                                <a href="${pageContext.request.contextPath}/common/swiss-stage.jsp?id=${t.id}&format=SWISS_LITE" class="btn-view-bracket-card">
                                                    Trận Đấu ➔
                                                </a>
                                            </c:when>
                                            <c:otherwise>
                                                <a href="${pageContext.request.contextPath}/common/single-elimination.jsp?id=${t.id}&format=${t.format}" class="btn-view-bracket-card">
                                                    Trận Đấu ➔
                                                </a>
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </div>
                            </div>
                        </c:forEach>
                    </div>
                </c:when>

                <c:otherwise>
                    <!-- Empty State Box when no tournament data exists -->
                    <div class="empty-state-box">
                        <div style="font-size: 2.5rem; color: var(--text-muted); margin-bottom: 0.75rem;">
                            <i class="fa-solid fa-folder-open"></i>
                        </div>
                        <h3 style="font-size: 1.1rem; font-weight: 700; margin-bottom: 0.4rem;">Chưa có giải đấu nào được tạo</h3>
                        <p class="text-muted" style="font-size: 0.82rem; margin-bottom: 1.25rem;">
                            Bạn chưa tạo giải đấu nào trong hệ thống. Hãy bắt đầu khởi tạo giải đấu đầu tiên ngay.
                        </p>
                        <a href="${pageContext.request.contextPath}/common/create-tournament.jsp" class="btn btn-mint">
                            <i class="fa-solid fa-plus"></i> Tạo Giải Đấu Mới
                        </a>
                    </div>
                </c:otherwise>
            </c:choose>

            <!-- DELETE TOURNAMENT CONFIRMATION MODAL -->
            <div id="deleteTourneyModalBackdrop" class="tourma-modal-backdrop" style="display: none;" onclick="if(event.target === this) closeDeleteTourneyModal();">
                <div class="tourma-modal-card" style="border-color: rgba(244, 63, 94, 0.4);" onclick="event.stopPropagation();">
                    <div class="modal-header-bar" style="border-bottom: 1px solid rgba(244, 63, 94, 0.2);">
                        <div class="modal-header-title" style="color: #f43f5e; font-size: 0.95rem; font-weight: 800; display: flex; align-items: center; gap: 0.5rem;">
                            <i class="fa-solid fa-trash-can"></i>
                            <span>Xác Nhận Xóa Giải Đấu</span>
                        </div>
                        <button type="button" class="modal-close-btn" onclick="closeDeleteTourneyModal()" title="Đóng">
                            <i class="fa-solid fa-xmark"></i>
                        </button>
                    </div>

                    <div class="modal-body-content" style="padding: 1.25rem 1rem;">
                        <div style="background: rgba(244, 63, 94, 0.08); border: 1px solid rgba(244, 63, 94, 0.2); border-radius: 8px; padding: 0.85rem; margin-bottom: 1rem; color: #cbd5e1; font-size: 0.82rem; line-height: 1.5;">
                            <strong style="color: #f43f5e;">⚠️ Cảnh báo xóa vĩnh viễn:</strong><br>
                            Hành động này sẽ <strong style="color: #ffffff;">xóa hoàn toàn giải đấu</strong> cùng toàn bộ danh sách đội tuyển, cấu hình thể thức và kết quả các trận đấu liên quan.
                        </div>
                        <p style="color: #cbd5e1; font-size: 0.85rem; margin: 0 0 0.35rem 0;">
                            Bạn có chắc chắn muốn xóa giải: <strong id="deleteTourneyTargetName" style="color: #f43f5e;"></strong> (ID: <span id="deleteTourneyTargetId" class="text-muted"></span>)?
                        </p>
                    </div>

                    <div class="modal-footer-bar" style="display: flex; justify-content: flex-end; gap: 0.65rem; padding: 0.85rem 1.25rem; border-top: 1px solid rgba(255, 255, 255, 0.08); background: rgba(0, 0, 0, 0.2);">
                        <button type="button" class="btn btn-secondary" onclick="closeDeleteTourneyModal()" style="font-size: 0.8rem; padding: 0.45rem 1rem;">Hủy Bỏ</button>
                        <button type="button" class="btn" style="background: #f43f5e; color: #ffffff; border: none; font-size: 0.8rem; font-weight: 700; padding: 0.45rem 1.25rem; border-radius: 6px; cursor: pointer;" onclick="confirmDeleteTourney()">
                            <i class="fa-solid fa-trash-can"></i> Xác Nhận Xóa
                        </button>
                    </div>
                </div>
            </div>

        </main>

        <script>
            let pendingDeleteId = null;

            function findChampionName(card) {
                var tid = card.getAttribute('data-id');
                var dbChamp = card.getAttribute('data-db-champion');
                var saved = localStorage.getItem("tourma_champion_" + tid) || localStorage.getItem("tourma_final_champion_" + tid);
                if (saved && saved.trim() !== "") return saved;
                if (dbChamp && dbChamp.trim() !== "") return dbChamp;

                var keys = ["tourma_matches_", "tourma_de_matches_", "tourma_rr_matches_"];
                for (var i = 0; i < keys.length; i++) {
                    try {
                        var raw = localStorage.getItem(keys[i] + tid);
                        if (!raw) continue;
                        var data = JSON.parse(raw);
                        var matchesMap = data.matchesMap || data;
                        if (!matchesMap || typeof matchesMap !== 'object') continue;

                        if (matchesMap['GF_RESET'] && matchesMap['GF_RESET'].winner && matchesMap['GF_RESET'].winner.name) {
                            return matchesMap['GF_RESET'].winner.name;
                        }
                        if (matchesMap['GF'] && matchesMap['GF'].winner && matchesMap['GF'].winner.name) {
                            return matchesMap['GF'].winner.name;
                        }

                        var matchKeys = Object.keys(matchesMap);
                        var maxRound = -1;
                        var finalWinner = null;
                        matchKeys.forEach(function(k) {
                            var m = matchesMap[k];
                            if (m) {
                                if (m.isFinalMatch && m.winner && m.winner.name) {
                                    finalWinner = m.winner.name;
                                } else if (m.roundIndex !== undefined && m.roundIndex > maxRound && m.winner && m.winner.name) {
                                    maxRound = m.roundIndex;
                                    finalWinner = m.winner.name;
                                }
                            }
                        });
                        if (finalWinner) return finalWinner;

                        if (data.standings && data.standings.length > 0 && data.standings[0].name) {
                            return data.standings[0].name;
                        }
                        if (data.teamsList && data.teamsList.length > 0 && window.TourmaRoundRobinAlgorithm) {
                            var st = window.TourmaRoundRobinAlgorithm.calculateStandings(data.teamsList, matchesMap, data.config || {});
                            if (st && st.length > 0 && st[0].team && st[0].team.name) {
                                return st[0].team.name;
                            }
                        }
                    } catch(e) {}
                }
                return "";
            }

            function getFormatDisplayName(fmt) {
                if (!fmt) return 'Single Elimination';
                var u = fmt.toUpperCase();
                if (u === 'GROUP_STAGE') return 'Group Stage';
                if (u === 'ROUND_ROBIN') return 'Round Robin';
                if (u === 'SINGLE_ELIMINATION') return 'Single Elimination';
                if (u === 'DOUBLE_ELIMINATION') return 'Double Elimination';
                if (u === 'SWISS_LITE') return 'Swiss System';
                return fmt;
            }

            function getTournamentTeamsCount(tid) {
                // 1. Try tourma_teams_
                try {
                    var rawTeams = localStorage.getItem("tourma_teams_" + tid);
                    if (rawTeams) {
                        var teams = JSON.parse(rawTeams);
                        if (Array.isArray(teams) && teams.length > 0) return teams.length;
                    }
                } catch(e) {}

                // 2. Try tourma_group_assignments_
                try {
                    var rawGroups = localStorage.getItem("tourma_group_assignments_" + tid);
                    if (rawGroups) {
                        var groups = JSON.parse(rawGroups);
                        var count = 0;
                        Object.keys(groups).forEach(function(k) {
                            if (Array.isArray(groups[k])) count += groups[k].length;
                        });
                        if (count > 0) return count;
                    }
                } catch(e) {}

                // 3. Try unique teams from matchesMap
                try {
                    var keys = ["tourma_matches_", "tourma_de_matches_", "tourma_rr_matches_", "tourma_group_matches_"];
                    var uniqueTeams = {};
                    for (var i = 0; i < keys.length; i++) {
                        var rawM = localStorage.getItem(keys[i] + tid);
                        if (!rawM) continue;
                        var mData = JSON.parse(rawM);
                        var matchesMap = mData.matchesMap || mData;
                        if (matchesMap && typeof matchesMap === 'object') {
                            Object.keys(matchesMap).forEach(function(k) {
                                var m = matchesMap[k];
                                if (m) {
                                    if (m.team1 && m.team1.name && m.team1.name !== 'BYE' && !m.team1.name.startsWith('W #') && !m.team1.name.startsWith('L #')) {
                                        uniqueTeams[m.team1.name.trim()] = true;
                                    }
                                    if (m.team2 && m.team2.name && m.team2.name !== 'BYE' && !m.team2.name.startsWith('W #') && !m.team2.name.startsWith('L #')) {
                                        uniqueTeams[m.team2.name.trim()] = true;
                                    }
                                }
                            });
                        }
                    }
                    var uKeys = Object.keys(uniqueTeams);
                    if (uKeys.length > 0) return uKeys.length;
                } catch(e) {}

                return 0;
            }

            function updateCardStatuses() {
                var cards = document.querySelectorAll('.tourney-card');
                cards.forEach(function(card) {
                    var tid = card.getAttribute('data-id');
                    if (!tid) return;

                    var dbStatus = card.getAttribute('data-db-status');
                    var isLocked = localStorage.getItem("tourma_final_locked_" + tid) === "true" || dbStatus === 'COMPLETED';
                    var championName = findChampionName(card);

                    // --- 1. UPDATE TEAMS COUNT ---
                    var teamsCount = getTournamentTeamsCount(tid);
                    var teamMeta = card.querySelector('.tourney-teams-meta');
                    if (teamMeta) {
                        var teamValEl = teamMeta.querySelector('.team-count-val');
                        if (teamValEl) {
                            teamValEl.innerText = teamsCount > 0 ? (teamsCount + ' Đội') : 'Chưa xếp đội';
                        }
                    }

                    // --- 2. UPDATE TOURNAMENT TYPE & MULTI-STAGE FORMATS ---
                    var localType = localStorage.getItem("tourma_type_" + tid);
                    var multiConfigRaw = localStorage.getItem("tourma_multi_config_" + tid);
                    var multiConfig = null;
                    if (multiConfigRaw) {
                        try { multiConfig = JSON.parse(multiConfigRaw); } catch(e) {}
                    }

                    var isMulti = (localType === 'MULTI_STAGE' || multiConfig !== null);
                    
                    var badgeType = card.querySelector('.tourney-badge-type');
                    if (badgeType) {
                        if (isMulti) {
                            badgeType.innerText = 'MULTI STAGE';
                            badgeType.style.background = 'rgba(251, 191, 36, 0.15)';
                            badgeType.style.color = '#fbbf24';
                            badgeType.style.border = '1px solid rgba(251, 191, 36, 0.3)';
                        } else {
                            badgeType.innerText = 'SINGLE STAGE';
                            badgeType.style.background = '';
                            badgeType.style.color = '';
                            badgeType.style.border = '';
                        }
                    }

                    var formatSpan = card.querySelector('.tourney-format-span');
                    var btnView = card.querySelector('.btn-view-bracket');
                    var ctx = "${pageContext.request.contextPath}";

                    if (isMulti && multiConfig) {
                        var s1Name = getFormatDisplayName(multiConfig.stage1Format || 'GROUP_STAGE');
                        var s2Name = getFormatDisplayName(multiConfig.stage2Format || 'SINGLE_ELIMINATION');
                        if (formatSpan) {
                            formatSpan.innerHTML = '<span style="display: inline-flex; align-items: center; gap: 0.4rem; flex-wrap: wrap;">' +
                                '<span><i class="fa-solid fa-layer-group text-mint"></i> <strong>Vòng 1:</strong> ' + s1Name + '</span>' +
                                '<span class="meta-divider">•</span>' +
                                '<span><i class="fa-solid fa-sitemap text-mint"></i> <strong>Vòng 2:</strong> ' + s2Name + '</span>' +
                                '</span>';
                        }
                        if (btnView) {
                            var s1Fmt = (multiConfig.stage1Format || 'GROUP_STAGE').toUpperCase();
                            if (s1Fmt === 'GROUP_STAGE') {
                                btnView.href = ctx + '/common/group-stage.jsp?id=' + tid;
                                btnView.innerText = 'Trận Đấu ➔';
                            } else if (s1Fmt === 'DOUBLE_ELIMINATION') {
                                btnView.href = ctx + '/common/double-elimination.jsp?id=' + tid;
                                btnView.innerText = 'Trận Đấu ➔';
                            } else {
                                btnView.href = ctx + '/common/single-elimination.jsp?id=' + tid;
                                btnView.innerText = 'Trận Đấu ➔';
                            }
                        }
                    } else {
                        var localFmt = localStorage.getItem("tourma_format_" + tid);
                        if (localFmt) {
                            if (localFmt === 'DOUBLE_ELIMINATION') {
                                if (formatSpan) formatSpan.innerHTML = '<i class="fa-solid fa-diagram-project text-mint"></i> Double Elimination';
                                if (btnView) {
                                    btnView.href = ctx + '/common/double-elimination.jsp?id=' + tid + '&format=DOUBLE_ELIMINATION';
                                    btnView.innerHTML = 'Trận Đấu ➔';
                                }
                            } else if (localFmt === 'ROUND_ROBIN') {
                                if (formatSpan) formatSpan.innerHTML = '<i class="fa-solid fa-diagram-project text-mint"></i> Round Robin';
                                if (btnView) {
                                    btnView.href = ctx + '/common/round-robin.jsp?id=' + tid + '&format=ROUND_ROBIN';
                                    btnView.innerHTML = 'Trận Đấu ➔';
                                }
                            } else if (localFmt === 'GROUP_STAGE') {
                                if (formatSpan) formatSpan.innerHTML = '<i class="fa-solid fa-diagram-project text-mint"></i> Group Stage';
                                if (btnView) {
                                    btnView.href = ctx + '/common/group-stage.jsp?id=' + tid + '&format=GROUP_STAGE';
                                    btnView.innerHTML = 'Trận Đấu ➔';
                                }
                            } else if (localFmt === 'SINGLE_ELIMINATION') {
                                if (formatSpan) formatSpan.innerHTML = '<i class="fa-solid fa-diagram-project text-mint"></i> Single Elimination';
                                if (btnView) {
                                    btnView.href = ctx + '/common/single-elimination.jsp?id=' + tid + '&format=SINGLE_ELIMINATION';
                                    btnView.innerHTML = 'Trận Đấu ➔';
                                }
                            } else if (localFmt === 'SWISS_LITE' || localFmt === 'SWISS') {
                                if (formatSpan) formatSpan.innerHTML = '<i class="fa-solid fa-diagram-project text-mint"></i> Swiss System';
                                if (btnView) {
                                    btnView.href = ctx + '/common/swiss-stage.jsp?id=' + tid + '&format=SWISS_LITE';
                                    btnView.innerHTML = 'Trận Đấu ➔';
                                }
                            }
                        }
                    }

                    // --- 3. UPDATE MATCHES & STATUS ---
                    var completedMatchesCount = 0;
                    var checkMatches = function(storageKey) {
                        try {
                            var raw = localStorage.getItem(storageKey);
                            if (!raw) return;
                            var data = JSON.parse(raw);
                            var matchesMap = data.matchesMap || data;
                            if (matchesMap && typeof matchesMap === 'object') {
                                Object.keys(matchesMap).forEach(function(k) {
                                    var m = matchesMap[k];
                                    if (m && (m.status === 'COMPLETED' || m.status === 'done' || (m.winner && m.winner.name) || (m.winnerId && m.winnerId !== ''))) {
                                        completedMatchesCount++;
                                    }
                                });
                            }
                        } catch(e) {}
                    };

                    checkMatches("tourma_matches_" + tid);
                    checkMatches("tourma_de_matches_" + tid);
                    checkMatches("tourma_rr_matches_" + tid);
                    checkMatches("tourma_group_matches_" + tid);

                    var statusPill = card.querySelector('.status-pill');
                    var championMeta = card.querySelector('.tourney-champion-meta');

                    if (isLocked) {
                        card.setAttribute('data-status', 'COMPLETED');
                        if (statusPill) {
                            statusPill.className = 'status-pill completed';
                            statusPill.innerText = 'Completed';
                        }
                        if (championMeta) {
                            var nameValEl = championMeta.querySelector('.champion-name-val');
                            if (nameValEl) nameValEl.innerText = championName || '';
                            championMeta.style.display = championName ? 'inline-flex' : 'none';
                        }
                    } else if (completedMatchesCount > 0 || dbStatus === 'ONGOING') {
                        card.setAttribute('data-status', 'IN_PROGRESS');
                        if (statusPill) {
                            statusPill.className = 'status-pill in-progress';
                            statusPill.innerText = 'In Progress';
                        }
                        if (championMeta) championMeta.style.display = 'none';
                    } else {
                        card.setAttribute('data-status', 'INCOMING');
                        if (statusPill) {
                            statusPill.className = 'status-pill incoming';
                            statusPill.innerText = 'Incoming';
                        }
                        if (championMeta) championMeta.style.display = 'none';
                    }
                });
            }

            function filterTournaments(filterType, btnEl) {
                var btns = document.querySelectorAll('.filter-pill-btn');
                btns.forEach(b => b.classList.remove('active'));
                if (btnEl) btnEl.classList.add('active');

                var cards = document.querySelectorAll('.tourney-card');
                cards.forEach(card => {
                    var context = card.getAttribute('data-context');
                    var status = card.getAttribute('data-status');
                    var show = false;

                    if (filterType === 'ALL') {
                        show = true;
                    } else if (filterType === 'STANDALONE' || filterType === 'SERIES') {
                        show = (context === filterType);
                    } else if (filterType === 'INCOMING' || filterType === 'IN_PROGRESS' || filterType === 'COMPLETED') {
                        show = (status === filterType);
                    }
                    card.style.display = show ? 'flex' : 'none';
                });
            }

            function searchTournaments() {
                var input = document.getElementById('searchInput');
                var filter = input.value.toLowerCase().trim();
                var cards = document.querySelectorAll('.tourney-card');

                cards.forEach(card => {
                    var name = (card.getAttribute('data-name') || '').toLowerCase();
                    if (name.includes(filter)) {
                        card.style.display = 'flex';
                    } else {
                        card.style.display = 'none';
                    }
                });
            }

            function openDeleteTourneyModal(id, name) {
                pendingDeleteId = id;
                document.getElementById('deleteTourneyTargetId').innerText = id;
                document.getElementById('deleteTourneyTargetName').innerText = name;
                var modal = document.getElementById('deleteTourneyModalBackdrop');
                if (modal) {
                    modal.style.display = 'flex';
                    document.body.style.overflow = 'hidden';
                }
            }

            function closeDeleteTourneyModal() {
                pendingDeleteId = null;
                var modal = document.getElementById('deleteTourneyModalBackdrop');
                if (modal) {
                    modal.style.display = 'none';
                    document.body.style.overflow = '';
                }
            }

            function confirmDeleteTourney() {
                if (!pendingDeleteId) return;
                var tid = pendingDeleteId;
                
                // 1. Clean localStorage
                try {
                    localStorage.removeItem("tourma_matches_" + tid);
                    localStorage.removeItem("tourma_de_matches_" + tid);
                    localStorage.removeItem("tourma_rr_matches_" + tid);
                    localStorage.removeItem("tourma_teams_" + tid);
                    localStorage.removeItem("tourma_format_" + tid);
                    localStorage.removeItem("tourma_type_" + tid);
                    localStorage.removeItem("tourma_champion_" + tid);
                    localStorage.removeItem("tourma_final_champion_" + tid);
                    localStorage.removeItem("tourma_final_locked_" + tid);
                } catch(e) {}

                // 2. Remove card from DOM instantly with smooth fade-out animation
                var card = document.querySelector('.tourney-card[data-id="' + tid + '"]');
                if (card) {
                    card.style.transition = 'all 0.3s ease';
                    card.style.opacity = '0';
                    card.style.transform = 'scale(0.9)';
                    setTimeout(function() {
                        if (card.parentNode) card.parentNode.removeChild(card);
                        var remaining = document.querySelectorAll('.tourney-card');
                        if (remaining.length === 0) {
                            window.location.reload();
                        }
                    }, 300);
                }

                closeDeleteTourneyModal();

                // 3. Send async delete request to backend in background
                fetch('${pageContext.request.contextPath}/delete-tournament?id=' + tid, { method: 'GET' })
                    .then(function() { console.log('Tournament ' + tid + ' deleted on backend.'); })
                    .catch(function(err) { console.warn('Backend delete fetch note:', err); });
            }

            window.addEventListener('DOMContentLoaded', function () {
                updateCardStatuses();
            });
        </script>
    </body>
</html>
