/**
 * TOURMA - Group Stage Engine (group-stage.js)
 * High-Performance, Database-Driven Group Stage Tournament System:
 * - Real-time Match Card Rendering using TourmaMatchCard
 * - Full Score Management & Modal Popup (supports Win/Draw/Loss Points)
 * - Group Filtering (Tất cả các bảng, Bảng A, Bảng B...)
 * - Server DB Synchronization (updateScore, batchSync, randomGroup, randomAll, reset)
 * - Integrated Multi-Stage Pipeline & StageEndPopup / FinalStagePopup
 */

(function () {
    'use strict';

    var tournamentId = window.groupTournamentId || 'demo';
    var selectedGroupFilter = 'ALL'; // 'ALL', 'A', 'B', 'C', 'D'...
    var currentViewMode = 'matches'; // 'matches' (Lịch Thi Đấu) or 'standings' (Bảng Xếp Hạng)
    var groupsMap = {}; // { 'A': [ {id, name, seed} ], 'B': [...] }
    var groupMatches = {}; // { 'A': [ matchObj, ... ], 'B': [...] }
    var teamsList = [];
    var rules = { winPoints: 3, drawPoints: 1, lossPoints: 0, advanceCount: 2 };
    var groupRandomScores = {};

    // Initialize Group Stage Engine
    function initGroupStageEngine() {
        if (!tournamentId || tournamentId === 'demo') {
            var urlParams = new URLSearchParams(window.location.search);
            var queryId = urlParams.get('id');
            if (queryId && queryId.trim().length > 0) {
                tournamentId = queryId.trim();
                window.groupTournamentId = tournamentId;
            }
        }

        var stageParam = new URLSearchParams(window.location.search).get('stage');
        var currentStage = (stageParam === '2' || stageParam === 2) ? 2 : 1;
        var storageKeyTeams = (currentStage === 2) ? ("tourma_stage2_teams_" + tournamentId) : ("tourma_teams_" + tournamentId);
        var storageKeyGroups = "tourma_group_assignments_" + tournamentId;
        var storageKeyMatches = "tourma_group_matches_" + tournamentId;

        // 1. Load Teams List
        if (window.serverTeams && Array.isArray(window.serverTeams) && window.serverTeams.length > 0) {
            teamsList = window.serverTeams;
        } else {
            try {
                var rawTeams = localStorage.getItem(storageKeyTeams);
                if (rawTeams) {
                    var parsedTeams = JSON.parse(rawTeams);
                    if (Array.isArray(parsedTeams) && parsedTeams.length > 0) {
                        teamsList = parsedTeams;
                    }
                }
            } catch (e) {}
        }

        // Handle Stage Finish Alert (Stage 2 access check)
        if (currentStage === 2) {
            if (window.StageFinishAlert && typeof window.StageFinishAlert.checkAndRender === 'function') {
                if (window.StageFinishAlert.checkAndRender(tournamentId, currentStage, document.getElementById('stageFinishAlertContainer') || document.getElementById('gsEmptyAlertContainer'))) {
                    var mainContent = document.getElementById('gsMainContent');
                    if (mainContent) mainContent.style.display = 'none';
                    return;
                }
            }
        }

        if (!teamsList || teamsList.length < 2) {
            teamsList = teamsList || [];
            if (window.TourmaEmptyTeamAlert && typeof window.TourmaEmptyTeamAlert.checkAndRender === 'function') {
                window.TourmaEmptyTeamAlert.checkAndRender(tournamentId, teamsList, document.getElementById('gsEmptyAlertContainer'));
            }
            var mainContent = document.getElementById('gsMainContent');
            if (mainContent) mainContent.style.display = 'none';
            var teamCountBadge = document.getElementById('gsTeamCountBadge') || document.getElementById('tournamentTeamCountBadge');
            if (teamCountBadge) teamCountBadge.innerText = teamsList.length + ' Đội';
            return;
        }

        // 2. Load Group Assignments
        var loadedGroups = null;
        if (window.DB_GROUP_ASSIGNMENTS && typeof window.DB_GROUP_ASSIGNMENTS === 'object' && Object.keys(window.DB_GROUP_ASSIGNMENTS).length > 0) {
            loadedGroups = window.DB_GROUP_ASSIGNMENTS;
        } else {
            try {
                var rawGroups = localStorage.getItem(storageKeyGroups);
                if (rawGroups) {
                    var parsedG = JSON.parse(rawGroups);
                    if (parsedG && typeof parsedG === 'object' && Object.keys(parsedG).length > 0) {
                        loadedGroups = parsedG;
                    }
                }
            } catch (e) {}
        }

        if (loadedGroups && Object.keys(loadedGroups).length > 0) {
            groupsMap = loadedGroups;
        } else {
            groupsMap = {};
        }

        // 3. Load Matches
        var loadedMatches = null;
        try {
            var rawM = localStorage.getItem(storageKeyMatches);
            if (rawM) {
                var parsedM = JSON.parse(rawM);
                if (parsedM && typeof parsedM === 'object' && Object.keys(parsedM).length > 0) {
                    loadedMatches = parsedM;
                }
            }
        } catch (e) {}

        if (!loadedMatches && window.dbGroupMatches && Array.isArray(window.dbGroupMatches) && window.dbGroupMatches.length > 0) {
            loadedMatches = {};
            window.dbGroupMatches.forEach(function (dm) {
                var gk = dm.groupKey || 'A';
                if (!loadedMatches[gk]) loadedMatches[gk] = [];
                loadedMatches[gk].push(dm);
            });
        }

        if (loadedMatches && Object.keys(loadedMatches).length > 0) {
            groupMatches = loadedMatches;
        } else if (Object.keys(groupsMap).length > 0) {
            groupMatches = generateDefaultGroupFixtures(groupsMap);
            saveGroupMatches();
        } else {
            groupMatches = {};
        }

        // 4. Load Tournament Rules
        loadTournamentRules();

        // 5. Update Header Badges
        updateHeaderBadges();

        // 6. Render UI
        renderGroupFilterPills();
        renderCurrentView();
        checkGroupStageCompletion();
    }

    // Default Group Assignments Generator (Snake seed distribution across groups)
    function generateDefaultGroupAssignments(teams) {
        var numTeams = teams ? teams.length : 16;
        var maxPerGroup = 4;
        var numGroups = Math.max(1, Math.ceil(numTeams / maxPerGroup));
        var res = {};

        for (var g = 0; g < numGroups; g++) {
            var gKey = String.fromCharCode(65 + g); // 'A', 'B', 'C', 'D'
            res[gKey] = [];
        }

        for (var i = 0; i < numTeams; i++) {
            var gIdx = i % numGroups;
            var gKey = String.fromCharCode(65 + gIdx);
            var tm = teams[i];
            var tObj = (typeof tm === 'object' && tm !== null) ? tm : { id: 'TEAM_' + (i + 1), name: String(tm), seed: (i + 1) };
            res[gKey].push(tObj);
        }

        return res;
    }

    // Default Group Fixtures Generator (Round Robin within each group)
    function generateDefaultGroupFixtures(groups) {
        var res = {};
        var matchSeq = 1;

        Object.keys(groups).forEach(function (gKey) {
            var grpTeams = groups[gKey] || [];
            res[gKey] = [];
            var n = grpTeams.length;
            var rNum = 1;

            for (var i = 0; i < n; i++) {
                for (var j = i + 1; j < n; j++) {
                    var t1 = grpTeams[i];
                    var t2 = grpTeams[j];
                    var t1Name = t1.name || t1.rawName || ('Đội ' + (i + 1));
                    var t2Name = t2.name || t2.rawName || ('Đội ' + (j + 1));

                    var mKey = 'M_' + tournamentId + '_G_' + gKey + '_' + rNum;
                    res[gKey].push({
                        matchId: mKey,
                        id: mKey,
                        matchKey: mKey,
                        groupKey: gKey,
                        groupName: 'Bảng ' + gKey,
                        roundNumber: rNum,
                        roundIndex: rNum,
                        matchNumber: matchSeq++,
                        team1: { id: t1.id || ('T_' + i), name: t1Name, seed: t1.seed || (i + 1), score: '' },
                        team2: { id: t2.id || ('T_' + j), name: t2Name, seed: t2.seed || (j + 1), score: '' },
                        winnerId: null,
                        status: 'SCHEDULED'
                    });
                    rNum++;
                }
            }
        });

        return res;
    }

    // Load Tournament Rules
    function loadTournamentRules() {
        try {
            var groupCfgRaw = localStorage.getItem('tourma_group_config_' + tournamentId);
            if (groupCfgRaw) {
                var gCfg = JSON.parse(groupCfgRaw);
                if (gCfg && gCfg.advanceCount) rules.advanceCount = parseInt(gCfg.advanceCount);
                if (gCfg && gCfg.advancePerGroup) rules.advanceCount = parseInt(gCfg.advancePerGroup);
            }

            var cfgRaw = localStorage.getItem('tourma_multi_config_' + tournamentId);
            if (cfgRaw) {
                var parsed = JSON.parse(cfgRaw);
                if (parsed && parsed.stage1Config) {
                    var c = parsed.stage1Config;
                    if (c.winPoints !== undefined) rules.winPoints = parseInt(c.winPoints);
                    if (c.drawPoints !== undefined) rules.drawPoints = parseInt(c.drawPoints);
                    if (c.lossPoints !== undefined) rules.lossPoints = parseInt(c.lossPoints);
                    if (c.advancePerGroup) rules.advanceCount = parseInt(c.advancePerGroup);
                    else if (c.advanceCount) rules.advanceCount = parseInt(c.advanceCount);
                    else if (c.totalAdvanceCount) rules.advanceCount = parseInt(c.totalAdvanceCount);
                } else if (parsed && parsed.advanceCount) {
                    rules.advanceCount = parseInt(parsed.advanceCount);
                }
            }
        } catch (e) {}
    }

    // Update Header Badges
    function updateHeaderBadges() {
        var totalTeams = 0;
        var numGroups = Object.keys(groupsMap).length;
        Object.keys(groupsMap).forEach(function (gk) {
            if (Array.isArray(groupsMap[gk])) totalTeams += groupsMap[gk].length;
        });

        var teamCountBadge = document.getElementById('gsTeamCountBadge') || document.getElementById('tournamentTeamCountBadge');
        if (teamCountBadge) {
            teamCountBadge.innerText = totalTeams + ' Đội (' + numGroups + ' Bảng)';
        }

        var advBadge = document.getElementById('gsAdvanceBadge') || document.getElementById('tournamentAdvancingBadge');
        var advText = document.getElementById('gsAdvanceText');
        var advCount = (rules.advanceCount && rules.advanceCount > 0) ? rules.advanceCount : 2;
        if (advText) {
            advText.innerText = advCount + ' Đội Đi Tiếp';
        }
        if (advBadge) {
            advBadge.style.display = 'inline-flex';
        }
    }

    // Render Group Selector Filter Pills
    function renderGroupFilterPills() {
        var bar = document.getElementById('gsGroupSelectorBar');
        if (!bar) return;
        bar.innerHTML = '';

        var groupKeys = Object.keys(groupsMap);
        if (groupKeys.length === 0) return;

        // All Groups Button
        var allBtn = document.createElement('button');
        allBtn.type = 'button';
        allBtn.className = 'rr-round-tab-btn' + (selectedGroupFilter === 'ALL' ? ' active' : '');
        allBtn.innerHTML = '<i class="fa-solid fa-layer-group"></i> Tất cả các bảng (' + groupKeys.length + ')';
        allBtn.onclick = function () {
            selectedGroupFilter = 'ALL';
            renderGroupFilterPills();
            renderCurrentView();
        };
        bar.appendChild(allBtn);

        // Per-Group Buttons (Bảng A, Bảng B...)
        groupKeys.forEach(function (gKey) {
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'rr-round-tab-btn' + (selectedGroupFilter === gKey ? ' active' : '');
            btn.innerText = gKey.startsWith('Bảng') ? gKey : ('Bảng ' + gKey);
            btn.onclick = function () {
                selectedGroupFilter = gKey;
                renderGroupFilterPills();
                renderCurrentView();
            };
            bar.appendChild(btn);
        });
    }

    // Render Current Active View Mode
    function renderCurrentView() {
        var matchesView = document.getElementById('gsMatchesView');
        var standingsView = document.getElementById('gsStandingsView');

        if (currentViewMode === 'standings') {
            if (matchesView) matchesView.style.display = 'none';
            if (standingsView) {
                standingsView.style.display = 'block';
                if (window.TourmaGroupStanding && typeof window.TourmaGroupStanding.renderAllGroupStandings === 'function') {
                    window.TourmaGroupStanding.renderAllGroupStandings('gsStandingsContainer', groupsMap, groupMatches, rules);
                }
            }
            updateViewToggleButtons('standings');
        } else {
            if (standingsView) standingsView.style.display = 'none';
            if (matchesView) {
                matchesView.style.display = 'block';
                renderMatchesView();
            }
            updateViewToggleButtons('matches');
        }
    }

    // Switch View Mode (Lịch Thi Đấu vs Bảng Xếp Hạng)
    function switchViewMode(mode) {
        if (mode === 'standings' || mode === 'list' || mode === 'standing') {
            currentViewMode = 'standings';
        } else {
            currentViewMode = 'matches';
        }
        renderCurrentView();
    }

    function updateViewToggleButtons(activeMode) {
        var btnBracket = document.getElementById('btnViewBracket');
        var btnList = document.getElementById('btnViewList');
        if (btnBracket && btnList) {
            if (activeMode === 'matches') {
                btnBracket.classList.add('active');
                btnList.classList.remove('active');
            } else {
                btnBracket.classList.remove('active');
                btnList.classList.add('active');
            }
        }
    }

    // Render Matches View
    function renderMatchesView() {
        var container = document.getElementById('gsMatchesContainer');
        if (!container) return;
        container.innerHTML = '';

        var groupKeys = Object.keys(groupsMap);
        if (groupKeys.length === 0) {
            container.innerHTML = '<div style="text-align: center; padding: 3rem 1.5rem; background: #121620; border: 1px dashed rgba(255,255,255,0.15); border-radius: 12px; margin: 1.5rem auto; max-width: 540px;">' +
                '<i class="fa-solid fa-layer-group" style="font-size: 2.8rem; color: #2dd4bf; margin-bottom: 1rem;"></i>' +
                '<h3 style="font-size: 1.15rem; font-weight: 800; color: #f8fafc; margin: 0 0 0.5rem 0;">Chưa thiết lập bảng đấu</h3>' +
                '<p style="font-size: 0.85rem; color: #94a3b8; margin: 0 0 1.5rem 0;">Giải đấu chưa được tạo bảng và phân bổ đội bóng. Vui lòng bấm vào nút bên dưới để tạo bảng và chia đội thi đấu.</p>' +
                '<a href="manage-group.jsp?id=' + encodeURIComponent(tournamentId) + '&format=GROUP_STAGE" class="btn btn-mint" style="display: inline-flex; align-items: center; gap: 0.5rem; font-weight: 700; padding: 0.55rem 1.25rem; text-decoration: none;">' +
                '<i class="fa-solid fa-pen-to-square"></i> Quản Lý & Chia Bảng Đấu' +
                '</a>' +
                '</div>';
            return;
        }

        groupKeys.forEach(function (gKey) {
            if (selectedGroupFilter !== 'ALL' && selectedGroupFilter !== gKey) {
                return;
            }

            var mList = groupMatches[gKey] || [];
            var gTeams = groupsMap[gKey] || [];

            var groupSec = document.createElement('div');
            groupSec.className = 'list-round-section';
            groupSec.setAttribute('data-group-key', gKey);

            // Group Round Header (Shared Match Card Style with SE, DE, SW, GSL)
            var secHeader = document.createElement('div');
            secHeader.className = 'list-round-header';

            var titleGroup = document.createElement('div');
            titleGroup.className = 'list-round-title-group';
            titleGroup.innerHTML = '<h3 class="list-round-title"><i class="fa-solid fa-trophy" style="color: #fbbf24;"></i> Bảng ' + gKey + '</h3>'
                + '<span class="list-round-badge">' + gTeams.length + ' Đội • ' + mList.length + ' Trận</span>';
            secHeader.appendChild(titleGroup);

            // Group Random & Reset Actions Bar
            var actionsDiv = document.createElement('div');
            actionsDiv.className = 'list-round-actions';

            var randInp = document.createElement('input');
            randInp.type = 'number';
            randInp.id = 'group_random_score_' + gKey;
            randInp.name = 'group_random_score_' + gKey;
            randInp.className = 'round-random-input';
            randInp.placeholder = '-';
            randInp.min = '1';
            randInp.max = '99';
            randInp.value = groupRandomScores[gKey] || '';
            randInp.autocomplete = 'off';
            randInp.title = 'Nhập điểm thắng tối đa tùy chỉnh';
            randInp.oninput = function () {
                groupRandomScores[gKey] = this.value;
            };

            var randBtn = document.createElement('button');
            randBtn.type = 'button';
            randBtn.className = 'btn-random-round';
            randBtn.title = 'Tạo tỉ số ngẫu nhiên cho Bảng ' + gKey;
            randBtn.innerHTML = '<i class="fa-solid fa-dice"></i> Random Bảng ' + gKey;
            (function (k, inp) {
                randBtn.onclick = function (e) {
                    e.stopPropagation();
                    randomGroupMatches(k, inp.value);
                };
            })(gKey, randInp);

            var resetBtn = document.createElement('button');
            resetBtn.type = 'button';
            resetBtn.className = 'btn-reset-round';
            resetBtn.title = 'Reset kết quả Bảng ' + gKey;
            resetBtn.innerHTML = '<i class="fa-solid fa-rotate-right"></i> Reset Bảng ' + gKey;
            (function (k) {
                resetBtn.onclick = function (e) {
                    e.stopPropagation();
                    resetGroupMatches(k);
                };
            })(gKey);

            actionsDiv.appendChild(randInp);
            actionsDiv.appendChild(randBtn);
            actionsDiv.appendChild(resetBtn);
            secHeader.appendChild(actionsDiv);
            groupSec.appendChild(secHeader);

            // Full-Width Matches Grid (Shared with SE, DE, Swiss)
            var grid = document.createElement('div');
            grid.className = 'list-matches-grid';

            mList.forEach(function (m, idx) {
                var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
                var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
                var s1 = (m.team1 && m.team1.score !== undefined && m.team1.score !== null) ? m.team1.score : '';
                var s2 = (m.team2 && m.team2.score !== undefined && m.team2.score !== null) ? m.team2.score : '';
                var isDone = (m.status === 'COMPLETED' || m.status === 'FINISHED' || (s1 !== '' && s2 !== ''));

                var cardEl = null;
                if (window.TourmaMatchCard && typeof window.TourmaMatchCard.createCardElement === 'function') {
                    cardEl = window.TourmaMatchCard.createCardElement({
                        matchId: m.matchKey || m.matchId || ('M_G_' + gKey + '_' + (idx + 1)),
                        matchNumber: idx + 1,
                        roundName: 'Bảng ' + gKey + ' • Trận ' + (idx + 1),
                        status: isDone ? 'COMPLETED' : 'SCHEDULED',
                        team1: { name: t1Name, score: (isDone && s1 !== '') ? s1 : '', seed: (m.team1 ? m.team1.seed : '') },
                        team2: { name: t2Name, score: (isDone && s2 !== '') ? s2 : '', seed: (m.team2 ? m.team2.seed : '') },
                        winnerId: m.winnerId,
                        allowDraw: true,
                        tournamentId: tournamentId,
                        onClick: function () {
                            openScorePopup(m, gKey);
                        }
                    });
                }

                if (!cardEl) {
                    cardEl = document.createElement('div');
                    cardEl.className = 'match-card-item';
                    cardEl.innerHTML = '<div class="match-card-meta"><div class="match-card-accent-bar"></div><span class="match-card-id">#' + (idx + 1) + '</span></div>'
                        + '<div class="match-card-versus">'
                        + '<div class="match-team-side team-left"><span class="match-list-name">' + t1Name + '</span></div>'
                        + '<div class="match-score-container"><span class="match-score-single-box">' + (isDone ? s1 : '') + '</span><span class="match-score-dash">-</span><span class="match-score-single-box">' + (isDone ? s2 : '') + '</span></div>'
                        + '<div class="match-team-side team-right"><span class="match-list-name">' + t2Name + '</span></div>'
                        + '</div>'
                        + '<div class="match-card-actions"><span class="match-list-status ' + (isDone ? 'done' : 'pending') + '">' + (isDone ? 'DONE' : 'PENDING') + '</span></div>';
                    cardEl.onclick = function () {
                        openScorePopup(m, gKey);
                    };
                }

                grid.appendChild(cardEl);
            });

            groupSec.appendChild(grid);
            container.appendChild(groupSec);
        });
    }

    // Open Score Modal Popup
    function openScorePopup(m, gKey) {
        if (!window.TourmaScoreModal) return;

        var t1Name = m.team1 ? (m.team1.name || m.team1) : 'TBD';
        var t2Name = m.team2 ? (m.team2.name || m.team2) : 'TBD';
        var s1 = (m.team1 && m.team1.score !== undefined && m.team1.score !== null) ? m.team1.score : '';
        var s2 = (m.team2 && m.team2.score !== undefined && m.team2.score !== null) ? m.team2.score : '';

        var popupData = {
            matchId: m.matchKey || m.matchId,
            tournamentId: tournamentId,
            roundName: 'Bảng ' + gKey + ' - Trận ' + (m.roundNumber || 1),
            team1Name: t1Name,
            team1Score: s1,
            team2Name: t2Name,
            team2Score: s2,
            winnerId: m.winnerId,
            status: m.status || 'SCHEDULED',
            allowDraw: true // GROUP STAGE FULLY SUPPORTS DRAWS
        };

        window.TourmaScoreModal.open(popupData, function (res) {
            if (!res) return;
            var newS1 = parseInt((res.team1Score !== undefined && res.team1Score !== '') ? res.team1Score : (res.score1 || 0), 10);
            var newS2 = parseInt((res.team2Score !== undefined && res.team2Score !== '') ? res.team2Score : (res.score2 || 0), 10);
            if (isNaN(newS1)) newS1 = 0;
            if (isNaN(newS2)) newS2 = 0;

            var winnerSide = 'draw';
            if (newS1 > newS2) winnerSide = 'team1';
            else if (newS2 > newS1) winnerSide = 'team2';

            if (!m.team1) m.team1 = {};
            if (!m.team2) m.team2 = {};
            m.team1.score = String(newS1);
            m.team2.score = String(newS2);
            m.team1Score = newS1;
            m.team2Score = newS2;
            m.winnerId = winnerSide;
            m.status = 'COMPLETED';

            saveGroupMatches();
            renderCurrentView();

            // Sync with Server DB
            try {
                var stageParam = new URLSearchParams(window.location.search).get('stage');
                var curStage = (stageParam === '2' || stageParam === 2) ? 2 : 1;
                var p = new URLSearchParams();
                p.append('action', 'updateScore');
                p.append('tournamentId', tournamentId);
                p.append('stage', curStage);
                p.append('matchKey', m.matchKey || m.matchId);
                p.append('team1Score', newS1);
                p.append('team2Score', newS2);
                p.append('winner', winnerSide);

                fetch((window.TourmaContextPath || '') + '/common/group-stage', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                    body: p.toString()
                }).catch(function () {});
            } catch (e) {}
        });
    }

    // Save Group Matches to localStorage and Sync to DB
    function saveGroupMatches() {
        try {
            var stageParam = new URLSearchParams(window.location.search).get('stage');
            var currentStage = (stageParam === '2' || stageParam === 2) ? 2 : 1;
            var storageKeyMatches = "tourma_group_matches_" + tournamentId;
            localStorage.setItem(storageKeyMatches, JSON.stringify(groupMatches));
            checkGroupStageCompletion();
            syncGroupMatchesToDB();
        } catch (e) {}
    }

    // Sync Group Matches via BatchSync to Server DB
    function syncGroupMatchesToDB() {
        if (!tournamentId || !groupMatches) return;
        var list = [];
        Object.keys(groupMatches).forEach(function (gk) {
            var mList = groupMatches[gk] || [];
            mList.forEach(function (m) {
                var s1 = (m.team1 && m.team1.score !== undefined && m.team1.score !== '') ? parseInt(m.team1.score, 10) : 0;
                var s2 = (m.team2 && m.team2.score !== undefined && m.team2.score !== '') ? parseInt(m.team2.score, 10) : 0;
                var isDone = (m.status === 'COMPLETED' || m.status === 'FINISHED' || (m.team1 && m.team1.score !== '' && m.team2 && m.team2.score !== ''));
                if (isDone) {
                    list.push({
                        matchKey: m.matchKey || m.matchId,
                        matchId: m.matchKey || m.matchId,
                        groupKey: gk,
                        team1Score: s1,
                        team2Score: s2,
                        winnerId: m.winnerId || ((s1 > s2) ? 'team1' : ((s2 > s1) ? 'team2' : 'draw')),
                        status: 'COMPLETED'
                    });
                }
            });
        });

        if (list.length === 0) return;

        var stageParam = new URLSearchParams(window.location.search).get('stage');
        var currentStage = (stageParam === '2' || stageParam === 2) ? 2 : 1;
        var p = new URLSearchParams();
        p.append('action', 'batchSync');
        p.append('tournamentId', tournamentId);
        p.append('stage', currentStage);
        p.append('matchesJson', JSON.stringify(list));

        fetch((window.TourmaContextPath || '') + '/common/group-stage', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
            body: p.toString()
        }).catch(function () {});
    }

    // Randomize Matches in Specific Group
    function randomGroupMatches(gKey, maxScore) {
        var mList = groupMatches[gKey] || [];
        if (mList.length === 0) return;

        var maxS = (maxScore && parseInt(maxScore, 10) > 0) ? parseInt(maxScore, 10) : 4;
        mList.forEach(function (m) {
            var s1 = Math.floor(Math.random() * (maxS + 1));
            var s2 = Math.floor(Math.random() * (maxS + 1));
            if (!m.team1) m.team1 = {};
            if (!m.team2) m.team2 = {};
            m.team1.score = String(s1);
            m.team2.score = String(s2);
            m.team1Score = s1;
            m.team2Score = s2;
            m.status = 'COMPLETED';

            if (s1 > s2) m.winnerId = 'team1';
            else if (s2 > s1) m.winnerId = 'team2';
            else m.winnerId = 'draw';
        });

        saveGroupMatches();
        renderCurrentView();

        // Server DB randomGroup call
        try {
            var p = new URLSearchParams();
            p.append('action', 'randomGroup');
            p.append('tournamentId', tournamentId);
            p.append('groupId', 'GRP_' + tournamentId + '_' + gKey);
            p.append('maxScore', maxS);
            fetch((window.TourmaContextPath || '') + '/common/group-stage', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                body: p.toString()
            }).catch(function () {});
        } catch (e) {}
    }

    // Reset Matches in Specific Group
    function resetGroupMatches(gKey) {
        var stageParam = new URLSearchParams(window.location.search).get('stage');
        var currentStage = (stageParam === '2' || stageParam === 2) ? 2 : 1;

        if (currentStage === 1 && window.StageEndPopup && typeof window.StageEndPopup.isStage1Locked === 'function' && window.StageEndPopup.isStage1Locked(tournamentId)) {
            alert('Vòng 1 đã hoàn tất và đang ở trạng thái khóa. Vui lòng bấm "Mở khóa để sửa" trên thanh thông báo nếu bạn muốn thiết lập lại.');
            return;
        }

        if (window.FinalStagePopup && window.FinalStagePopup.isLocked) {
            alert('Giải đấu đã kết thúc và đang ở trạng thái khóa. Vui lòng bấm "Mở khóa" trên thanh thông báo nếu muốn reset giải.');
            return;
        }

        var mList = groupMatches[gKey] || [];
        if (mList.length === 0) return;

        mList.forEach(function (m) {
            if (m.team1) m.team1.score = '';
            if (m.team2) m.team2.score = '';
            m.team1Score = null;
            m.team2Score = null;
            m.winnerId = null;
            m.status = 'SCHEDULED';
        });

        saveGroupMatches();
        renderCurrentView();

        try {
            var p = new URLSearchParams();
            p.append('action', 'resetGroup');
            p.append('tournamentId', tournamentId);
            p.append('groupId', 'GRP_' + tournamentId + '_' + gKey);
            fetch((window.TourmaContextPath || '') + '/common/group-stage', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                body: p.toString()
            }).catch(function () {});
        } catch (e) {}
    }

    // Randomize All Matches Across All Groups
    function randomAllMatches(maxScore) {
        var maxS = (maxScore && parseInt(maxScore, 10) > 0) ? parseInt(maxScore, 10) : 4;
        Object.keys(groupMatches).forEach(function (gKey) {
            var mList = groupMatches[gKey] || [];
            mList.forEach(function (m) {
                var s1 = Math.floor(Math.random() * (maxS + 1));
                var s2 = Math.floor(Math.random() * (maxS + 1));
                if (!m.team1) m.team1 = {};
                if (!m.team2) m.team2 = {};
                m.team1.score = String(s1);
                m.team2.score = String(s2);
                m.team1Score = s1;
                m.team2Score = s2;
                m.status = 'COMPLETED';

                if (s1 > s2) m.winnerId = 'team1';
                else if (s2 > s1) m.winnerId = 'team2';
                else m.winnerId = 'draw';
            });
        });

        saveGroupMatches();
        renderCurrentView();

        // Server DB randomAll call
        try {
            var p = new URLSearchParams();
            p.append('action', 'randomAll');
            p.append('tournamentId', tournamentId);
            p.append('maxScore', maxS);
            fetch((window.TourmaContextPath || '') + '/common/group-stage', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                body: p.toString()
            }).catch(function () {});
        } catch (e) {}
    }

    // Reset All Matches in Group Stage
    function resetAllMatches() {
        var stageParam = new URLSearchParams(window.location.search).get('stage');
        var currentStage = (stageParam === '2' || stageParam === 2) ? 2 : 1;

        if (currentStage === 1 && window.StageEndPopup && typeof window.StageEndPopup.isStage1Locked === 'function' && window.StageEndPopup.isStage1Locked(tournamentId)) {
            alert('Vòng 1 đã hoàn tất và đang ở trạng thái khóa. Vui lòng bấm "Mở khóa để sửa" trên thanh thông báo nếu bạn muốn thiết lập lại.');
            return;
        }

        if (window.TourmaScoreModal && typeof window.TourmaScoreModal.isLocked === 'function') {
            if (window.TourmaScoreModal.isLocked(tournamentId)) {
                if (window.FinalStagePopup && typeof window.FinalStagePopup.promptUnlock === 'function') {
                    window.FinalStagePopup.promptUnlock();
                } else if (window.StageEndPopup && typeof window.StageEndPopup.promptUnlock === 'function') {
                    window.StageEndPopup.promptUnlock();
                }
                return;
            }
        } else if (window.FinalStagePopup && typeof window.FinalStagePopup.isTournamentLocked === 'function') {
            if (window.FinalStagePopup.isTournamentLocked(tournamentId)) {
                window.FinalStagePopup.promptUnlock();
                return;
            }
        }

        if (!confirm('Bạn có chắc chắn muốn xóa toàn bộ tỷ số và đặt lại giai đoạn Vòng Bảng về ban đầu?')) {
            return;
        }

        Object.keys(groupMatches).forEach(function (gKey) {
            var mList = groupMatches[gKey] || [];
            mList.forEach(function (m) {
                if (m.team1) m.team1.score = '';
                if (m.team2) m.team2.score = '';
                m.team1Score = null;
                m.team2Score = null;
                m.winnerId = null;
                m.status = 'SCHEDULED';
            });
        });

        try {
            localStorage.removeItem('tourma_group_matches_' + tournamentId);
            localStorage.removeItem('tourma_final_locked_' + tournamentId);
            localStorage.removeItem('tourma_champion_' + tournamentId);
            localStorage.removeItem('tourma_stage1_locked_' + tournamentId);
            localStorage.removeItem('tourma_stage2_teams_' + tournamentId);
            localStorage.removeItem('tourma_stage1_completed_' + tournamentId);
        } catch (e) {}

        // Unlock UI banners
        if (window.FinalStagePopup) {
            window.FinalStagePopup.isLocked = false;
            var banner = document.getElementById('finalStagePopupBanner');
            if (banner) banner.style.display = 'none';
        }
        if (window.StageEndPopup) {
            var sBanner = document.getElementById('stageEndPopupBanner');
            if (sBanner) sBanner.style.display = 'none';
        }

        // Send reset to backend DB
        try {
            var rParams = new URLSearchParams();
            rParams.append('action', 'reset');
            rParams.append('tournamentId', tournamentId);
            rParams.append('stage', currentStage);
            fetch((window.TourmaContextPath || '') + '/common/group-stage', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                body: rParams.toString()
            }).catch(function () {});
        } catch (e) {}

        renderCurrentView();
    }

    // Check Stage Completion and Trigger Multi-Stage Pipeline or Final Popup
    function checkGroupStageCompletion() {
        if (!groupsMap || Object.keys(groupsMap).length === 0) return;

        var allMatchesDone = true;
        var totalMatches = 0;

        Object.keys(groupMatches).forEach(function (gk) {
            var mList = groupMatches[gk] || [];
            mList.forEach(function (m) {
                totalMatches++;
                var s1 = (m.team1 && m.team1.score !== undefined && m.team1.score !== '') ? m.team1.score : '';
                var s2 = (m.team2 && m.team2.score !== undefined && m.team2.score !== '') ? m.team2.score : '';
                if (m.status !== 'COMPLETED' && m.status !== 'FINISHED' && (s1 === '' || s2 === '')) {
                    allMatchesDone = false;
                }
            });
        });

        if (totalMatches === 0 || !allMatchesDone) return;

        var stageParam = new URLSearchParams(window.location.search).get('stage');
        var currentStage = (stageParam === '2' || stageParam === 2) ? 2 : 1;

        if (currentStage === 1) {
            // Multi-Stage Stage 1 Completion
            if (window.TourmaGroupStanding && typeof window.TourmaGroupStanding.calculateAllGroupStandings === 'function') {
                window.TourmaGroupStanding.groups = groupsMap;
                window.TourmaGroupStanding.groupMatches = groupMatches;
                window.TourmaGroupStanding.rules = rules;

                var standingsResult = window.TourmaGroupStanding.calculateAllGroupStandings();
                var qualifiedTeams = [];
                Object.keys(standingsResult).forEach(function (gk) {
                    var stList = standingsResult[gk] || [];
                    var slots = (rules.advanceCount && rules.advanceCount > 0) ? rules.advanceCount : 2;
                    for (var i = 0; i < Math.min(slots, stList.length); i++) {
                        qualifiedTeams.push({
                            id: stList[i].teamId || ('TEAM_' + (qualifiedTeams.length + 1)),
                            name: stList[i].name,
                            seed: qualifiedTeams.length + 1
                        });
                    }
                });

                if (qualifiedTeams.length > 0) {
                    localStorage.setItem('tourma_stage2_teams_' + tournamentId, JSON.stringify(qualifiedTeams));
                    localStorage.setItem('tourma_stage1_completed_' + tournamentId, 'true');

                    // Save to Servlet
                    try {
                        var pS2 = new URLSearchParams();
                        pS2.append('action', 'saveStage2Teams');
                        pS2.append('tournamentId', tournamentId);
                        pS2.append('stage2Teams', JSON.stringify(qualifiedTeams));
                        fetch((window.TourmaContextPath || '') + '/common/group-stage', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                            body: pS2.toString()
                        }).catch(function () {});
                    } catch (e) {}
                }
            }

            if (window.StageEndPopup) {
                window.StageEndPopup.update(
                    tournamentId,
                    'GROUP_STAGE',
                    groupMatches,
                    teamsList,
                    null,
                    null,
                    1
                );
            }
        }
    }

    // Export TourmaGroupStage Engine
    window.TourmaGroupStage = {
        tournamentId: tournamentId,
        init: initGroupStageEngine,
        switchViewMode: switchViewMode,
        resetAllMatches: resetAllMatches,
        confirmResetBracket: resetAllMatches,
        resetBracket: resetAllMatches,
        resetMatches: resetAllMatches,
        randomAll: randomAllMatches,
        randomGroup: randomGroupMatches,
        checkGroupStageCompletion: checkGroupStageCompletion
    };

    window.GroupStageEngine = window.TourmaGroupStage;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initGroupStageEngine);
    } else {
        initGroupStageEngine();
    }
})();
