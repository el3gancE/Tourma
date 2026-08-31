/**
 * Tourma Group Stage JS Engine
 * Group stage match schedule engine & match card renderer.
 * Match schedule default view, matches popup modal edit, group standings navigation.
 * Round Robin style Control Bar, Group Filter Selector Pills, and Berger Round Scheduling.
 */

(function () {
    'use strict';

    window.TourmaGroupSync = {
        syncGroupAssignments: function (tournamentId, teamsList) {
            if (!tournamentId || !teamsList || !Array.isArray(teamsList)) return { valid: true };

            var gRaw = localStorage.getItem('tourma_group_assignments_' + tournamentId);
            if (!gRaw) return { valid: true };

            var groups = {};
            try { groups = JSON.parse(gRaw); } catch (e) { return { valid: true }; }

            var groupKeys = Object.keys(groups);
            if (groupKeys.length === 0) return { valid: true };

            // Build lookup of current valid teams
            var validTeamIds = {};
            var validTeamNames = {};
            for (var i = 0; i < teamsList.length; i++) {
                var tm = teamsList[i];
                var tId = (typeof tm === 'object') ? tm.id : (i + 1);
                var tName = (typeof tm === 'object') ? tm.name : String(tm);
                if (tId !== undefined) validTeamIds[String(tId)] = true;
                if (tName) validTeamNames[String(tName).trim().toLowerCase()] = true;
            }

            // Clean group assignments: remove teams that no longer exist
            var updatedGroups = {};
            var deficientGroupKey = null;
            var isModified = false;

            for (var k = 0; k < groupKeys.length; k++) {
                var gKey = groupKeys[k];
                var rawList = groups[gKey] || [];
                var cleanedList = [];

                for (var m = 0; m < rawList.length; m++) {
                    var item = rawList[m];
                    var itemId = (typeof item === 'object') ? item.id : item;
                    var itemName = (typeof item === 'object') ? item.name : String(item);

                    var isMatch = false;
                    if (itemId !== undefined && validTeamIds[String(itemId)]) {
                        isMatch = true;
                    } else if (itemName && validTeamNames[String(itemName).trim().toLowerCase()]) {
                        isMatch = true;
                    }

                    if (isMatch) {
                        cleanedList.push(item);
                    }
                }

                if (cleanedList.length !== rawList.length) {
                    isModified = true;
                }

                updatedGroups[gKey] = cleanedList;

                if (cleanedList.length < 2 && deficientGroupKey === null) {
                    deficientGroupKey = gKey;
                }
            }

            // Save cleaned assignments
            localStorage.setItem('tourma_group_assignments_' + tournamentId, JSON.stringify(updatedGroups));

            // Only reset match schedule if teams were actually deleted/modified from groups
            if (isModified) {
                localStorage.removeItem('tourma_group_matches_' + tournamentId);
                localStorage.removeItem('tourma_matches_' + tournamentId);
            }

            if (deficientGroupKey !== null) {
                return {
                    valid: false,
                    deficientGroupKey: deficientGroupKey,
                    reason: 'DEFICIENT_GROUP'
                };
            }

            return { valid: true, groups: updatedGroups };
        }
    };

    window.TourmaGroupStage = {
        tournamentId: '',
        tournamentName: 'Giải Đấu Vòng Bảng',
        teamsList: [],
        groups: {},
        groupMatches: {},
        matchesMap: {},
        advanceCount: 2,
        legsCount: 1,
        winPoints: 3,
        drawPoints: 1,
        lossPoints: 0,
        selectedGroupFilter: 'ALL',

        init: function () {
            var urlParams = new URLSearchParams(window.location.search);
            this.tournamentId = urlParams.get('id') || 'demo';

            this.loadMetadata();
            this.loadTeams();
            this.loadOrGenerateGroupsAndMatches();
            this.bindEvents();
            this.renderAll();
        },

        loadMetadata: function () {
            if (!this.tournamentId) return;

            var titleElem = document.getElementById('gsTournamentTitle');
            if (titleElem && titleElem.innerText.trim()) {
                this.tournamentName = titleElem.innerText.trim();
            }

            try {
                var directAdv = localStorage.getItem('tourma_advance_count_' + this.tournamentId);
                if (directAdv && !isNaN(parseInt(directAdv)) && parseInt(directAdv) > 0) {
                    this.advanceCount = parseInt(directAdv);
                }

                var mCfgRaw = localStorage.getItem('tourma_multi_config_' + this.tournamentId);
                if (mCfgRaw) {
                    var mCfg = JSON.parse(mCfgRaw);
                    if (mCfg && mCfg.stage1Config) {
                        var c = mCfg.stage1Config;
                        var advVal = c.totalAdvanceCount || c.advanceCount;
                        if (advVal && !isNaN(parseInt(advVal)) && parseInt(advVal) > 0) {
                            this.advanceCount = parseInt(advVal);
                        }
                        if (c.legsCount) this.legsCount = Math.max(1, parseInt(c.legsCount));
                        if (c.winPoints !== undefined) this.winPoints = parseInt(c.winPoints);
                        if (c.drawPoints !== undefined) this.drawPoints = parseInt(c.drawPoints);
                        if (c.lossPoints !== undefined) this.lossPoints = parseInt(c.lossPoints);
                    }
                }
            } catch (e) {}
        },

        loadTeams: function () {
            // PRIORITY 1: Always use DB serverTeams as authoritative source when available
            if (window.serverTeams && window.serverTeams.length > 0) {
                this.teamsList = window.serverTeams;
                try {
                    localStorage.setItem('tourma_teams_' + this.tournamentId, JSON.stringify(this.teamsList));
                } catch(e) {}
                return;
            }

            // PRIORITY 2: localStorage cache
            var rawTeams = localStorage.getItem('tourma_teams_' + this.tournamentId);
            if (rawTeams) {
                try {
                    var parsed = JSON.parse(rawTeams);
                    if (Array.isArray(parsed) && parsed.length > 0) {
                        this.teamsList = parsed;
                        return;
                    }
                } catch (e) {}
            }

            // PRIORITY 3: Extract teams from saved group assignments if still empty
            var gRaw = localStorage.getItem('tourma_group_assignments_' + this.tournamentId);
            if (gRaw) {
                try {
                    var groupsObj = JSON.parse(gRaw);
                    var extracted = [];
                    Object.keys(groupsObj).forEach(function(gKey) {
                        var list = groupsObj[gKey] || [];
                        for (var i = 0; i < list.length; i++) {
                            extracted.push(list[i]);
                        }
                    });
                    if (extracted.length > 0) {
                        this.teamsList = extracted;
                        localStorage.setItem('tourma_teams_' + this.tournamentId, JSON.stringify(this.teamsList));
                    }
                } catch(e) {}
            }
        },

        divideTeamsIntoGroups: function () {
            var totalTeams = this.teamsList ? this.teamsList.length : 0;
            var minRequired = Math.max(1, this.advanceCount) + 1;
            this.groups = {};

            if (totalTeams < minRequired) {
                return false;
            }

            var numG = Math.max(1, Math.floor(totalTeams / 2));
            var groupNames = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'];
            for (var g = 0; g < numG; g++) {
                var gName = groupNames[g] || ('Bảng ' + (g + 1));
                this.groups[gName] = [];
            }

            for (var i = 0; i < totalTeams; i++) {
                var targetIndex = i % numG;
                var gName = groupNames[targetIndex] || ('Bảng ' + (targetIndex + 1));
                this.groups[gName].push(this.teamsList[i]);
            }

            return true;
        },

        loadOrGenerateGroupsAndMatches: function () {
            var totalTeams = this.teamsList ? this.teamsList.length : 0;
            if (totalTeams < 2) {
                this.groups = {};
                this.groupMatches = {};
                this.matchesMap = {};
                return;
            }

            // Execute Smart Group Assignment Sync (preserves groups when teams are added/removed unless a group drops below 2 teams)
            if (window.TourmaGroupSync) {
                var syncRes = window.TourmaGroupSync.syncGroupAssignments(this.tournamentId, this.teamsList);
                if (!syncRes.valid && syncRes.reason === 'DEFICIENT_GROUP') {
                    if (window.location.pathname.includes('group-stage.jsp')) {
                        var alertKey = 'tourma_deficient_alert_' + this.tournamentId;
                        if (!sessionStorage.getItem(alertKey)) {
                            sessionStorage.setItem(alertKey, 'true');
                            alert('⚠️ Sau khi bớt đội, Bảng ' + syncRes.deficientGroupKey + ' hiện tại chỉ còn 1 đội (dưới tối thiểu 2 đội/bảng)!\n\nVui lòng di chuyển hoặc bổ sung đội bóng sao cho mỗi bảng đấu đủ tối thiểu 2 đội.');
                        }
                        window.location.href = 'manage-group.jsp?id=' + this.tournamentId;
                        return;
                    }
                }
            }

            var savedGroupsRaw = localStorage.getItem('tourma_group_assignments_' + this.tournamentId);
            if (savedGroupsRaw) {
                try {
                    this.groups = JSON.parse(savedGroupsRaw);
                } catch (e) {
                    this.divideTeamsIntoGroups();
                }
            } else {
                this.divideTeamsIntoGroups();
            }

            var needsRegenerate = false;
            var savedMatchesRaw = localStorage.getItem('tourma_group_matches_' + this.tournamentId);
            if (savedMatchesRaw) {
                try {
                    this.groupMatches = JSON.parse(savedMatchesRaw);
                    this.matchesMap = {};
                    var hasValidRounds = false;

                    for (var gKey in this.groupMatches) {
                        var mList = this.groupMatches[gKey];
                        if (!mList || mList.length === 0) continue;
                        
                        for (var m = 0; m < mList.length; m++) {
                            var item = mList[m];
                            this.matchesMap[item.matchId] = item;
                            if (item.roundNumber && item.roundNumber > 1) {
                                hasValidRounds = true;
                            }
                        }
                    }

                    // If group matches were saved under old cache without round numbers, regenerate!
                    if (!hasValidRounds) {
                        needsRegenerate = true;
                    }
                } catch (e) {
                    needsRegenerate = true;
                }
            } else {
                needsRegenerate = true;
            }

            if (needsRegenerate) {
                this.generateAllGroupMatches();
            }
        },

        generateGroupRounds: function (groupTeams, legCount) {
            var teams = groupTeams.map(function(t, idx) {
                if (typeof t === 'string') return { id: idx + 1, name: t };
                if (t && !t.id) t.id = idx + 1;
                return t;
            });

            var hasBye = (teams.length % 2 !== 0);
            if (hasBye) {
                teams.push({ id: -1, name: 'BYE', isBye: true });
            }

            var T = teams.length;
            var roundsPerLeg = T - 1;
            var matchesPerRound = T / 2;

            var rounds = [];

            for (var leg = 1; leg <= legCount; leg++) {
                var isReverse = (leg % 2 === 0);
                var circle = [];
                for (var c = 0; c < T; c++) circle.push(c);

                for (var r = 0; r < roundsPerLeg; r++) {
                    var roundNumber = (leg - 1) * roundsPerLeg + (r + 1);
                    var matches = [];

                    for (var m = 0; m < matchesPerRound; m++) {
                        var homeIdx = circle[m];
                        var awayIdx = circle[T - 1 - m];

                        if (m === 0 && r % 2 === 1) {
                            var temp = homeIdx;
                            homeIdx = awayIdx;
                            awayIdx = temp;
                        }

                        if (isReverse) {
                            var swap = homeIdx;
                            homeIdx = awayIdx;
                            awayIdx = swap;
                        }

                        var t1 = teams[homeIdx];
                        var t2 = teams[awayIdx];

                        if (t1.name === 'BYE' || t2.name === 'BYE') {
                            continue;
                        }

                        matches.push({
                            team1: { id: t1.id, name: t1.name, score: '' },
                            team2: { id: t2.id, name: t2.name, score: '' },
                            winnerId: null,
                            status: 'SCHEDULED'
                        });
                    }

                    rounds.push({
                        roundNumber: roundNumber,
                        leg: leg,
                        matches: matches
                    });

                    // Rotate circle array clockwise (keep pivot index 0 fixed)
                    var last = circle.pop();
                    circle.splice(1, 0, last);
                }
            }

            return rounds;
        },

        generateAllGroupMatches: function () {
            this.groupMatches = {};
            this.matchesMap = {};
            var globalMatchId = 1;

            for (var gKey in this.groups) {
                var teamArr = this.groups[gKey] || [];
                if (teamArr.length < 2) continue;

                var rounds = this.generateGroupRounds(teamArr, this.legsCount);
                var gMatches = [];

                for (var r = 0; r < rounds.length; r++) {
                    var rd = rounds[r];
                    for (var m = 0; m < rd.matches.length; m++) {
                        var mObj = rd.matches[m];
                        mObj.matchId = 'GS_' + gKey + '_' + (globalMatchId++);
                        mObj.matchNumber = gMatches.length + 1;
                        mObj.groupKey = gKey;
                        mObj.roundNumber = rd.roundNumber;
                        mObj.leg = rd.leg;
                        mObj.allowDraw = true;

                        gMatches.push(mObj);
                        this.matchesMap[mObj.matchId] = mObj;
                    }
                }

                this.groupMatches[gKey] = gMatches;
            }

            this.saveMatchesState();
        },

        saveMatchesState: function () {
            if (!this.tournamentId) return;
            try {
                localStorage.setItem('tourma_group_assignments_' + this.tournamentId, JSON.stringify(this.groups));
                localStorage.setItem('tourma_group_matches_' + this.tournamentId, JSON.stringify(this.groupMatches));
            } catch (e) {}
            this.checkAndTriggerStage2Cut();
            if (window.StageEndPopup) {
                window.StageEndPopup.update(this.tournamentId, 'GROUP_STAGE', this.matchesMap, this.teamsList, this.config, this.groupMatches, 1);
            }
        },

        checkAndTriggerStage2Cut: function () {
            if (!this.tournamentId) return;

            var multiCfgRaw = localStorage.getItem('tourma_multi_config_' + this.tournamentId);
            if (!multiCfgRaw) return;

            var multiCfg = null;
            try { multiCfg = JSON.parse(multiCfgRaw); } catch (e) { return; }
            if (!multiCfg || multiCfg.stage1Format !== 'GROUP_STAGE') return;

            // Check if all non-BYE matches across all groups are COMPLETED
            var groupKeys = Object.keys(this.groupMatches);
            if (groupKeys.length === 0) return;

            var totalMatches = 0;
            var completedMatches = 0;

            for (var k = 0; k < groupKeys.length; k++) {
                var mList = this.groupMatches[groupKeys[k]] || [];
                for (var m = 0; m < mList.length; m++) {
                    var match = mList[m];
                    var t1 = match.team1 ? match.team1.name : '';
                    var t2 = match.team2 ? match.team2.name : '';
                    if (!t1 || !t2 || t1 === 'BYE' || t2 === 'BYE') continue;

                    totalMatches++;
                    var s1 = (match.team1 && match.team1.score !== '' && match.team1.score != null && !isNaN(Number(match.team1.score))) ? Number(match.team1.score) : null;
                    var s2 = (match.team2 && match.team2.score !== '' && match.team2.score != null && !isNaN(Number(match.team2.score))) ? Number(match.team2.score) : null;
                    if (s1 !== null && s2 !== null) {
                        completedMatches++;
                    }
                }
            }

            if (totalMatches === 0 || completedMatches < totalMatches) {
                try {
                    localStorage.removeItem('tourma_stage1_completed_' + this.tournamentId);
                } catch(e) {}
                return; // Not all group matches finished yet!
            }

            // All group stage matches finished! Calculate standings and crossover pairing for Stage 2!
            var advCount = 0;
            if (multiCfg.stage1Config) {
                advCount = multiCfg.stage1Config.advanceCount || multiCfg.stage1Config.totalAdvanceCount || 0;
            }
            if (!advCount || advCount <= 1) {
                try {
                    var rawAdv = localStorage.getItem('tourma_advance_count_' + this.tournamentId) || localStorage.getItem('tourma_cut_target_' + this.tournamentId);
                    if (rawAdv) advCount = parseInt(rawAdv, 10);
                } catch(e) {}
            }
            if (!advCount || advCount <= 1) advCount = 4;

            var finalStage2Teams = [];
            if (window.TourmaGroupStanding && typeof window.TourmaGroupStanding.getQualifiedTeamsCrossover === 'function') {
                finalStage2Teams = window.TourmaGroupStanding.getQualifiedTeamsCrossover(this.tournamentId, this.groups, this.groupMatches, advCount);
            }

            if (!finalStage2Teams || finalStage2Teams.length === 0) return;

            localStorage.setItem('tourma_stage2_teams_' + this.tournamentId, JSON.stringify(finalStage2Teams));

            var s2Format = multiCfg.stage2Format || 'SINGLE_ELIMINATION';

            if (s2Format === 'SINGLE_ELIMINATION' && window.TourmaSingleElimAlgorithm) {
                var seBracket = window.TourmaSingleElimAlgorithm.generateBracket(finalStage2Teams, 0);
                if (seBracket) {
                    localStorage.setItem('tourma_bracket_stage2_' + this.tournamentId, JSON.stringify(seBracket));
                    localStorage.setItem('tourma_matches_stage2_' + this.tournamentId, JSON.stringify(seBracket.matchesMap || {}));
                }
            } else if (s2Format === 'DOUBLE_ELIMINATION' && (window.TourmaDoubleElimAlgorithm || window.TourmaDoubleEliminationAlgorithm)) {
                var doubleEngine = window.TourmaDoubleElimAlgorithm || window.TourmaDoubleEliminationAlgorithm;
                var deBracket = doubleEngine.generateDoubleElimination(finalStage2Teams, 0);
                if (deBracket) {
                    localStorage.setItem('tourma_de_matches_stage2_' + this.tournamentId, JSON.stringify(deBracket));
                    localStorage.setItem('tourma_matches_stage2_' + this.tournamentId, JSON.stringify(deBracket.matchesMap || {}));
                }
            } else if (s2Format === 'ROUND_ROBIN' && window.TourmaRoundRobinAlgorithm) {
                var rrBracket = window.TourmaRoundRobinAlgorithm.generateRoundRobin(finalStage2Teams, multiCfg.stage2Config);
                if (rrBracket) {
                    localStorage.setItem('tourma_rr_matches_stage2_' + this.tournamentId, JSON.stringify(rrBracket));
                    localStorage.setItem('tourma_matches_stage2_' + this.tournamentId, JSON.stringify(rrBracket.matchesMap || {}));
                }
            }

            multiCfg.stage2MatchesCreated = true;
            localStorage.setItem('tourma_multi_config_' + this.tournamentId, JSON.stringify(multiCfg));
            localStorage.setItem('tourma_stage1_completed_' + this.tournamentId, 'true');
        },

        bindEvents: function () {
            var self = this;

            document.addEventListener('click', function (e) {
                var card = e.target.closest('.match-card-item');
                if (!card) return;

                var matchId = card.dataset.matchId;
                if (!matchId || !self.matchesMap[matchId]) return;

                var mData = self.matchesMap[matchId];
                var t1Name = mData.team1 ? mData.team1.name : '';
                var t2Name = mData.team2 ? mData.team2.name : '';
                var t1Score = (mData.team1 && mData.team1.score !== undefined) ? mData.team1.score : '';
                var t2Score = (mData.team2 && mData.team2.score !== undefined) ? mData.team2.score : '';

                if (window.TourmaScoreModal && typeof window.TourmaScoreModal.open === 'function') {
                    window.TourmaScoreModal.open({
                        matchId: mData.matchId,
                        roundName: 'Bảng ' + (mData.groupKey || '') + ' - Vòng ' + (mData.roundNumber || 1),
                        team1Name: t1Name,
                        team1Seed: (mData.team1 ? mData.team1.id : ''),
                        team1Score: t1Score,
                        team2Name: t2Name,
                        team2Seed: (mData.team2 ? mData.team2.id : ''),
                        team2Score: t2Score,
                        winnerId: mData.winnerId,
                        status: mData.status,
                        allowDraw: true
                    }, function (result) {
                        self.onScoreSave(matchId, result);
                    });
                }
            });

            window.addEventListener('tourmaScoreSaved', function (e) {
                if (e.detail && e.detail.matchId) {
                    self.onScoreSave(e.detail.matchId, e.detail);
                }
            });
        },

        onScoreSave: function (matchId, result) {
            if (!this.matchesMap[matchId] || !result) return;
            var mObj = this.matchesMap[matchId];

            mObj.team1.score = (result.team1Score !== undefined && result.team1Score !== null) ? result.team1Score : '';
            mObj.team2.score = (result.team2Score !== undefined && result.team2Score !== null) ? result.team2Score : '';
            mObj.winnerId = result.winnerId || null;

            var s1 = (mObj.team1.score !== '');
            var s2 = (mObj.team2.score !== '');
            mObj.status = (s1 && s2) ? 'COMPLETED' : 'SCHEDULED';

            this.saveMatchesState();
            this.renderMatchesView();
        },

        randomizeAllMatches: function () {
            var groupKeys = Object.keys(this.groupMatches);
            if (groupKeys.length === 0) {
                alert('Chưa có trận đấu nào để sinh tỉ số ngẫu nhiên!');
                return;
            }

            var totalRandomized = 0;

            for (var k = 0; k < groupKeys.length; k++) {
                var gKey = groupKeys[k];
                var mList = this.groupMatches[gKey] || [];

                for (var i = 0; i < mList.length; i++) {
                    var m = mList[i];
                    var t1Name = m.team1 ? m.team1.name : '';
                    var t2Name = m.team2 ? m.team2.name : '';

                    if (!t1Name || !t2Name || t1Name === 'BYE' || t2Name === 'BYE') continue;

                    var s1 = Math.floor(Math.random() * 5);
                    var s2 = Math.floor(Math.random() * 5);

                    m.team1.score = String(s1);
                    m.team2.score = String(s2);
                    m.status = 'COMPLETED';

                    if (s1 > s2) {
                        m.winnerId = 'team1';
                    } else if (s2 > s1) {
                        m.winnerId = 'team2';
                    } else {
                        m.winnerId = 'draw';
                    }

                    totalRandomized++;
                }
            }

            this.saveMatchesState();
            this.renderMatchesView();
        },

        resetAllMatches: function () {
            if (!confirm('Bạn có chắc chắn muốn xóa toàn bộ kết quả thi đấu và đưa giải đấu về từ đầu?')) {
                return;
            }

            var groupKeys = Object.keys(this.groupMatches);
            for (var k = 0; k < groupKeys.length; k++) {
                var gKey = groupKeys[k];
                var mList = this.groupMatches[gKey] || [];

                for (var i = 0; i < mList.length; i++) {
                    var m = mList[i];
                    m.team1.score = '';
                    m.team2.score = '';
                    m.winnerId = null;
                    m.status = 'SCHEDULED';
                }
            }

            this.saveMatchesState();
            this.renderMatchesView();
        },

        randomizeRoundMatches: function (gKey, rNum, rMatches, targetWinScore) {
            if (!rMatches || rMatches.length === 0) return;

            for (var i = 0; i < rMatches.length; i++) {
                var m = rMatches[i];
                var t1Name = m.team1 ? m.team1.name : '';
                var t2Name = m.team2 ? m.team2.name : '';
                if (!t1Name || !t2Name || t1Name === 'BYE' || t2Name === 'BYE') continue;

                var res;
                if (window.TourmaRandomService && typeof window.TourmaRandomService.generateMatchScore === 'function') {
                    res = window.TourmaRandomService.generateMatchScore(targetWinScore);
                } else {
                    var winVal = (targetWinScore && parseInt(targetWinScore) > 0) ? parseInt(targetWinScore) : (Math.floor(Math.random() * 4) + 2);
                    var isT1 = Math.random() < 0.5;
                    var loseVal = Math.floor(Math.random() * winVal);
                    res = {
                        team1Score: isT1 ? winVal : loseVal,
                        team2Score: isT1 ? loseVal : winVal,
                        winner: isT1 ? 'team1' : 'team2'
                    };
                }

                m.team1.score = String(res.team1Score);
                m.team2.score = String(res.team2Score);
                m.winnerId = res.winner;
                m.status = 'COMPLETED';

                if (this.matchesMap[m.matchId]) {
                    this.matchesMap[m.matchId].team1.score = String(res.team1Score);
                    this.matchesMap[m.matchId].team2.score = String(res.team2Score);
                    this.matchesMap[m.matchId].winnerId = res.winner;
                    this.matchesMap[m.matchId].status = 'COMPLETED';
                }
            }

            this.saveMatchesState();
            this.renderMatchesView();
        },

        resetRoundMatches: function (gKey, rNum, rMatches) {
            if (!rMatches || rMatches.length === 0) return;

            for (var i = 0; i < rMatches.length; i++) {
                var m = rMatches[i];
                m.team1.score = '';
                m.team2.score = '';
                m.winnerId = null;
                m.status = 'SCHEDULED';

                if (this.matchesMap[m.matchId]) {
                    this.matchesMap[m.matchId].team1.score = '';
                    this.matchesMap[m.matchId].team2.score = '';
                    this.matchesMap[m.matchId].winnerId = null;
                    this.matchesMap[m.matchId].status = 'SCHEDULED';
                }
            }

            this.saveMatchesState();
            this.renderMatchesView();
        },

        renderAll: function () {
            var totalTeams = this.teamsList ? this.teamsList.length : 0;

            var alertContainer = document.getElementById('gsEmptyAlertContainer');
            var mainContent = document.getElementById('gsMainContent');

            if (totalTeams < 2) {
                if (mainContent) mainContent.style.display = 'none';
                if (alertContainer) {
                    alertContainer.style.display = 'flex';
                    if (window.TourmaEmptyTeamAlert) {
                        window.TourmaEmptyTeamAlert.checkAndRender(this.tournamentId, this.teamsList, alertContainer);
                    }
                }
                return;
            }

            if (alertContainer) alertContainer.style.display = 'none';
            if (mainContent) mainContent.style.display = 'block';

            this.updateHeaderInfo();
            this.renderGroupSelectorBar();
            this.renderMatchesView();

            if (window.StageEndPopup) {
                window.StageEndPopup.update(this.tournamentId, 'GROUP_STAGE', this.matchesMap, this.teamsList, this.config, this.groupMatches, 1);
            }
        },

        updateHeaderInfo: function () {
            var tName = document.getElementById('gsTournamentTitle');
            var tBadge = document.getElementById('gsTeamCountBadge');
            var advText = document.getElementById('gsAdvanceText');
            var numG = Object.keys(this.groups).length || 2;
            if (tName) tName.innerText = this.tournamentName;
            if (tBadge) tBadge.innerText = (this.teamsList ? this.teamsList.length : 0) + ' Đội (' + numG + ' Bảng)';
            if (advText) advText.innerText = (this.advanceCount || 2) + ' Đội Đi Tiếp';
        },

        renderGroupSelectorBar: function () {
            var bar = document.getElementById('gsGroupSelectorBar');
            if (!bar) return;
            bar.innerHTML = '';
            var self = this;

            var groupKeys = Object.keys(this.groupMatches);
            if (groupKeys.length === 0) return;

            // 1. "Tất cả các bảng (N)" Pill
            var allBtn = document.createElement('button');
            allBtn.type = 'button';
            allBtn.className = 'rr-round-tab-btn' + (this.selectedGroupFilter === 'ALL' ? ' active' : '');
            allBtn.innerText = 'Tất cả các bảng (' + groupKeys.length + ')';
            allBtn.onclick = function () {
                self.selectedGroupFilter = 'ALL';
                self.renderGroupSelectorBar();
                self.renderMatchesView();
            };
            bar.appendChild(allBtn);

            // 2. Individual Group Pills (Bảng A, Bảng B...)
            for (var i = 0; i < groupKeys.length; i++) {
                (function (gKey) {
                    var btn = document.createElement('button');
                    btn.type = 'button';
                    btn.className = 'rr-round-tab-btn' + (self.selectedGroupFilter === gKey ? ' active' : '');
                    btn.innerText = gKey.startsWith('Bảng') ? gKey : ('Bảng ' + gKey);
                    btn.onclick = function () {
                        self.selectedGroupFilter = gKey;
                        self.renderGroupSelectorBar();
                        self.renderMatchesView();
                    };
                    bar.appendChild(btn);
                })(groupKeys[i]);
            }
        },

        renderMatchesView: function () {
            var container = document.getElementById('gsMatchesContainer');
            if (!container) return;
            container.innerHTML = '';

            var groupKeys = Object.keys(this.groupMatches);
            if (groupKeys.length === 0) {
                container.innerHTML = '<div style="text-align:center; padding:3rem; color:#94a3b8;">Chưa có lịch thi đấu. Vui lòng bấm "Quản Lý Chia Bảng" để tạo bảng đấu.</div>';
                return;
            }

            for (var k = 0; k < groupKeys.length; k++) {
                var gKey = groupKeys[k];

                // Filter by group pill selection
                if (this.selectedGroupFilter !== 'ALL' && this.selectedGroupFilter !== gKey) {
                    continue;
                }

                var mList = this.groupMatches[gKey] || [];
                if (mList.length === 0) continue;

                // Group matches by roundNumber
                var roundsMap = {};
                for (var i = 0; i < mList.length; i++) {
                    var rNum = mList[i].roundNumber || 1;
                    if (!roundsMap[rNum]) roundsMap[rNum] = [];
                    roundsMap[rNum].push(mList[i]);
                }

                var rKeys = Object.keys(roundsMap);

                // Outer Container for Group (Unboxed Layout)
                var groupPackage = document.createElement('div');
                groupPackage.style.marginBottom = '2.25rem';

                // Group Header (No Icon)
                var groupHeader = document.createElement('div');
                groupHeader.style.fontSize = '1.15rem';
                groupHeader.style.fontWeight = '800';
                groupHeader.style.color = '#fbbf24';
                groupHeader.style.paddingBottom = '0.6rem';
                groupHeader.style.marginBottom = '1.25rem';
                groupHeader.style.borderBottom = '1px solid rgba(255, 255, 255, 0.1)';
                groupHeader.style.display = 'flex';
                groupHeader.style.justifyContent = 'space-between';
                groupHeader.style.alignItems = 'center';

                groupHeader.innerHTML = '<div>BẢNG ' + gKey + '</div>' +
                    '<span class="gs-group-package-badge">' + mList.length + ' Trận • ' + rKeys.length + ' Vòng</span>';
                groupPackage.appendChild(groupHeader);

                // Inner Subpackages for Rounds (No Icon, with Round Random Controls)
                var self = this;
                for (var r = 0; r < rKeys.length; r++) {
                    (function(rNum, rMatches) {
                        var subPackage = document.createElement('div');
                        subPackage.style.marginBottom = '1.25rem';

                        var roundSubtitle = document.createElement('div');
                        roundSubtitle.style.fontSize = '0.85rem';
                        roundSubtitle.style.fontWeight = '700';
                        roundSubtitle.style.color = '#2dd4bf';
                        roundSubtitle.style.marginBottom = '0.6rem';
                        roundSubtitle.style.display = 'flex';
                        roundSubtitle.style.justifyContent = 'space-between';
                        roundSubtitle.style.alignItems = 'center';

                        var titleSpan = document.createElement('span');
                        titleSpan.innerText = 'Vòng ' + rNum;
                        roundSubtitle.appendChild(titleSpan);

                        // Round Action Controls (Random Score Input, Random & Reset Buttons)
                        var rndControls = document.createElement('div');
                        rndControls.className = 'round-header-random-controls';
                        rndControls.style.display = 'flex';
                        rndControls.style.alignItems = 'center';
                        rndControls.style.gap = '0.35rem';

                        var rndInput = document.createElement('input');
                        rndInput.type = 'number';
                        rndInput.className = 'round-random-input';
                        rndInput.placeholder = '-';
                        rndInput.min = '1';
                        rndInput.max = '999';
                        rndInput.value = (self.roundRandomInputs && self.roundRandomInputs[gKey + '_' + rNum] !== undefined) ? self.roundRandomInputs[gKey + '_' + rNum] : '';
                        rndInput.title = 'Điểm đội thắng (dùng cho Random)';
                        rndInput.style.width = '32px';
                        rndInput.style.height = '22px';
                        rndInput.style.fontSize = '0.72rem';
                        rndInput.style.textAlign = 'center';
                        rndInput.style.background = '#0b0d12';
                        rndInput.style.color = '#f8fafc';
                        rndInput.style.border = '1px solid rgba(255, 255, 255, 0.15)';
                        rndInput.style.borderRadius = '4px';

                        var rndBtn = document.createElement('button');
                        rndBtn.type = 'button';
                        rndBtn.className = 'btn-round-random';
                        rndBtn.innerText = 'Random';
                        rndBtn.title = 'Random kết quả các trận trong vòng này';

                        var rndResetBtn = document.createElement('button');
                        rndResetBtn.type = 'button';
                        rndResetBtn.className = 'btn-round-reset';
                        rndResetBtn.innerHTML = '<i class="fa-solid fa-rotate-right"></i>';
                        rndResetBtn.title = 'Reset kết quả vòng này';

                        rndInput.addEventListener('input', function () {
                            if (!self.roundRandomInputs) self.roundRandomInputs = {};
                            if (rndInput.value === '') {
                                delete self.roundRandomInputs[gKey + '_' + rNum];
                            } else {
                                self.roundRandomInputs[gKey + '_' + rNum] = rndInput.value;
                            }
                        });

                        rndBtn.onclick = function () {
                            self.randomizeRoundMatches(gKey, rNum, rMatches, rndInput.value);
                        };

                        rndResetBtn.onclick = function () {
                            self.resetRoundMatches(gKey, rNum, rMatches);
                        };

                        rndControls.appendChild(rndInput);
                        rndControls.appendChild(rndBtn);
                        rndControls.appendChild(rndResetBtn);
                        roundSubtitle.appendChild(rndControls);

                        subPackage.appendChild(roundSubtitle);

                        for (var m = 0; m < rMatches.length; m++) {
                            var matchData = rMatches[m];
                            if (window.TourmaMatchCard && typeof window.TourmaMatchCard.createCardElement === 'function') {
                                var card = window.TourmaMatchCard.createCardElement(matchData);
                                if (card) subPackage.appendChild(card);
                            }
                        }

                        groupPackage.appendChild(subPackage);
                    })(rKeys[r], roundsMap[rKeys[r]]);
                }

                container.appendChild(groupPackage);
            }
        }
    };

    document.addEventListener('DOMContentLoaded', function () {
        if (document.getElementById('gsMainContainer') || document.querySelector('.round-robin-container')) {
            window.TourmaGroupStage.init();
        }
    });
})();
