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
    var pendingSaves = new Set();

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
        if (window.serverTeams && Array.isArray(window.serverTeams) && true) {
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
        if (Object.prototype.hasOwnProperty.call(window, 'DB_GROUP_ASSIGNMENTS')) {
            loadedGroups = window.DB_GROUP_ASSIGNMENTS || {};
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

        // Persisted matches are authoritative, including an empty schedule after reset.
        applyDbMatches(window.dbGroupMatches || []);

        // 4. Load Tournament Rules
        loadTournamentRules();

        // 5. Update Header Badges
        updateHeaderBadges();

        // 6. Render UI
        renderGroupFilterPills();
        renderCurrentView();
        checkGroupStageCompletion();
    }

    function applyDbMatches(matches) {
        groupMatches = {};
        (matches || []).forEach(function (match) {
            var key = match.groupKey || 'A';
            if (!groupMatches[key]) groupMatches[key] = [];
            groupMatches[key].push(match);
        });
    }

    function requestGroupAction(action, values) {
        var params = new URLSearchParams(values || {});
        params.set('action', action);
        params.set('tournamentId', tournamentId);
        params.set('stage', new URLSearchParams(window.location.search).get('stage') === '2' ? '2' : '1');
        var task = fetch((window.TourmaContextPath || '') + '/common/group-stage', {
            method: 'POST', headers: {'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'}, body: params.toString()
        }).then(function (res) { return res.json(); }).then(function (data) {
            if (!data || data.status !== 'success') throw new Error((data && data.message) || 'Không lưu được kết quả.');
            if (Array.isArray(data.matchesData)) {
                applyDbMatches(data.matchesData);
                renderCurrentView();
                checkGroupStageCompletion();
            } else window.location.reload();
            return true;
        }).catch(function (error) {
            alert(error.message || 'Không lưu được kết quả. Vui lòng thử lại.');
            return false;
        });
        pendingSaves.add(task);
        task.then(function () { pendingSaves.delete(task); });
        return task;
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
        var stage = new URLSearchParams(window.location.search).get('stage') === '2' ? 2 : 1;
        var cfg = window.TourmaMultiStageConfig || {};
        var saved = cfg['stage' + stage + 'Config'] || {};
        ['winPoints','drawPoints','lossPoints'].forEach(function(key){rules[key]=saved[key] !== undefined?Number(saved[key]):({winPoints:3,drawPoints:1,lossPoints:0})[key];});
        var count=Object.keys(groupsMap).length;
        if(stage===1 && window.TourmaCutTarget && count) rules.advanceCount=window.TourmaCutTarget;
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
            container.innerHTML = '<div data-ui-style="group-stage-js-001">' +
                '<i class="fa-solid fa-layer-group" data-ui-style="group-stage-js-002"></i>' +
                '<h3 data-ui-style="group-stage-js-003">Chưa thiết lập bảng đấu</h3>' +
                '<p data-ui-style="group-stage-js-004">Giải đấu chưa được tạo bảng và phân bổ đội bóng. Vui lòng bấm vào nút bên dưới để tạo bảng và chia đội thi đấu.</p>' +
                '<a href="manage-group.jsp?id=' + encodeURIComponent(tournamentId) + '&format=GROUP_STAGE" class="btn btn-mint" data-ui-style="group-stage-js-005" style="display: inline-flex;">' +
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
            titleGroup.innerHTML = '<h3 class="list-round-title"><i class="fa-solid fa-trophy" data-ui-style="group-stage-js-006"></i> Bảng ' + gKey + '</h3>'
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

            requestGroupAction('updateScore', {
                matchKey: m.matchKey || m.matchId,
                team1Score: newS1, team2Score: newS2, winner: winnerSide
            });
        });
    }

    // Randomize Matches in Specific Group
    function randomGroupMatches(gKey, maxScore) {
        var matches = groupMatches[gKey] || [];
        if (!matches.length) return;
        return requestGroupAction('randomGroup', {groupId: matches[0].groupId, maxScore: parseInt(maxScore,10) || 4});
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

        var matches = groupMatches[gKey] || [];
        if (matches.length) return requestGroupAction('resetGroup', {groupId: matches[0].groupId});
    }

    // Randomize All Matches Across All Groups
    function randomAllMatches(maxScore) {
        return requestGroupAction('randomAll', {maxScore: parseInt(maxScore,10) || 4});
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

        return requestGroupAction('reset', {});
    }

    // Check Stage Completion and Trigger Multi-Stage Pipeline or Final Popup
    function checkGroupStageCompletion() {
        var stage = new URLSearchParams(window.location.search).get('stage') === '2' ? 2 : 1;
        if (stage === 1 && window.TourmaTournamentType === 'MULTI_STAGE' && window.StageEndPopup) {
            window.StageEndPopup.update(tournamentId, 'GROUP_STAGE', groupMatches, teamsList,
                {isMultiStage:true, cutTarget:window.TourmaCutTarget}, groupMatches, stage);
        }
    }

    // Export TourmaGroupStage Engine
    window.TourmaGroupStage = {
        waitForPendingSaves: function () { return Promise.all(Array.from(pendingSaves)).then(function(results){if(results.some(function(ok){return !ok;})) throw new Error("Có kết quả chưa lưu được.");}); },
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
