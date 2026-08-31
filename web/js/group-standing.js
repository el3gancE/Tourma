/**
 * Tourma Group Standing JS Engine
 * Calculates dynamic standings per group (MP, W, D, L, GF, GA, GD, PTS)
 * using custom win/draw/loss points rules.
 * Direct qualification (Green #22c55e), Wildcard qualification (Mint #2dd4bf),
 * and Best Rank X Wildcard Comparison Table (shown strictly when remainder > 0).
 */

(function () {
    'use strict';

    window.TourmaGroupStanding = {
        selectedGroupFilter: 'ALL',
        groups: {},
        groupMatches: {},
        rules: { winPoints: 3, drawPoints: 1, lossPoints: 0, advanceCount: 2 },

        renderAllGroupStandings: function (containerId, groups, groupMatches, rules) {
            var container = typeof containerId === 'string' ? document.getElementById(containerId) : containerId;
            if (!container) return;

            // Fallback load from localStorage if arguments are missing/empty
            var urlParams = new URLSearchParams(window.location.search);
            var tid = urlParams.get('id') || 'demo';

            if (!groups || Object.keys(groups).length === 0) {
                var gRaw = localStorage.getItem('tourma_group_assignments_' + tid);
                if (gRaw) {
                    try { groups = JSON.parse(gRaw); } catch(e) {}
                }
            }

            if (!groupMatches || Object.keys(groupMatches).length === 0) {
                var mRaw = localStorage.getItem('tourma_group_matches_' + tid);
                if (mRaw) {
                    try { groupMatches = JSON.parse(mRaw); } catch(e) {}
                }
            }

            this.groups = groups || {};
            this.groupMatches = groupMatches || {};
            if (rules) {
                if (rules.winPoints !== undefined) this.rules.winPoints = Number(rules.winPoints);
                if (rules.drawPoints !== undefined) this.rules.drawPoints = Number(rules.drawPoints);
                if (rules.lossPoints !== undefined) this.rules.lossPoints = Number(rules.lossPoints);
                if (rules.advanceCount !== undefined) this.rules.advanceCount = Number(rules.advanceCount);
            }

            try {
                var directAdv = localStorage.getItem('tourma_advance_count_' + tid);
                if (directAdv && !isNaN(parseInt(directAdv)) && parseInt(directAdv) > 0) {
                    this.rules.advanceCount = parseInt(directAdv);
                }

                var mCfgRaw = localStorage.getItem('tourma_multi_config_' + tid);
                if (mCfgRaw) {
                    var mCfg = JSON.parse(mCfgRaw);
                    if (mCfg && mCfg.stage1Config) {
                        var c = mCfg.stage1Config;
                        var advVal = c.totalAdvanceCount || c.advanceCount;
                        if (advVal && !isNaN(parseInt(advVal)) && parseInt(advVal) > 0) {
                            this.rules.advanceCount = parseInt(advVal);
                        }
                    }
                }
            } catch (e) {}

            var self = this;
            var gstBadge = document.getElementById('gstTeamCountBadge');
            var gstAdvText = document.getElementById('gstAdvanceText');
            var totalTeams = 0;
            var numG = Object.keys(this.groups).length || 1;
            Object.keys(this.groups).forEach(function (g) {
                if (Array.isArray(self.groups[g])) totalTeams += self.groups[g].length;
            });
            if (gstBadge) gstBadge.innerText = totalTeams + ' Đội (' + numG + ' Bảng)';
            if (gstAdvText) gstAdvText.innerText = (this.rules.advanceCount || 2) + ' Đội Đi Tiếp';

            this.renderGroupFilterPills();
            this.renderStandingsTables(container);
        },

        getAdvanceConfig: function () {
            var groupKeys = Object.keys(this.groups);
            var numGroups = groupKeys.length || 1;
            var rawAdvance = (this.rules && Number(this.rules.advanceCount) > 0) ? Number(this.rules.advanceCount) : 2;

            var directPerGroup = 0;
            var remainder = 0;

            if (rawAdvance >= numGroups) {
                directPerGroup = Math.floor(rawAdvance / numGroups);
                remainder = rawAdvance % numGroups;
            } else {
                directPerGroup = 0;
                remainder = rawAdvance;
            }

            var wildcardRankIndex = directPerGroup; // 0-indexed rank competing for wildcard

            return {
                numGroups: numGroups,
                rawAdvance: rawAdvance,
                directPerGroup: directPerGroup,
                remainder: remainder,
                wildcardRankIndex: wildcardRankIndex
            };
        },

        renderGroupFilterPills: function () {
            var selectorBar = document.getElementById('gsGroupSelectorBar');
            if (!selectorBar) return;
            selectorBar.innerHTML = '';

            var groupKeys = Object.keys(this.groups);
            if (groupKeys.length === 0) return;

            var cfg = this.getAdvanceConfig();
            var self = this;

            // All Groups Pill
            var allBtn = document.createElement('button');
            allBtn.type = 'button';
            allBtn.className = 'rr-round-tab-btn' + (this.selectedGroupFilter === 'ALL' ? ' active' : '');
            allBtn.innerText = 'Tất cả các bảng (' + groupKeys.length + ')';
            allBtn.onclick = function () {
                self.selectedGroupFilter = 'ALL';
                self.renderGroupFilterPills();
                var container = document.getElementById('gsStandingsContainer');
                if (container) self.renderStandingsTables(container);
            };
            selectorBar.appendChild(allBtn);

            // Per-Group Pills (Bảng A, Bảng B...)
            for (var i = 0; i < groupKeys.length; i++) {
                (function (gKey) {
                    var btn = document.createElement('button');
                    btn.type = 'button';
                    btn.className = 'rr-round-tab-btn' + (self.selectedGroupFilter === gKey ? ' active' : '');
                    btn.innerText = gKey.startsWith('Bảng') ? gKey : ('Bảng ' + gKey);
                    btn.onclick = function () {
                        self.selectedGroupFilter = gKey;
                        self.renderGroupFilterPills();
                        var container = document.getElementById('gsStandingsContainer');
                        if (container) self.renderStandingsTables(container);
                    };
                    selectorBar.appendChild(btn);
                })(groupKeys[i]);
            }

            // WILDCARD PILL: ONLY APPEARS IF REMAINDER > 0
            if (cfg.remainder > 0) {
                var wildcardBtn = document.createElement('button');
                wildcardBtn.type = 'button';
                wildcardBtn.className = 'rr-round-tab-btn' + (this.selectedGroupFilter === 'WILDCARD' ? ' active' : '');
                wildcardBtn.innerText = 'Đội Hạng ' + (cfg.wildcardRankIndex + 1) + ' Xuất Sắc';
                wildcardBtn.onclick = function () {
                    self.selectedGroupFilter = 'WILDCARD';
                    self.renderGroupFilterPills();
                    var container = document.getElementById('gsStandingsContainer');
                    if (container) self.renderStandingsTables(container);
                };
                selectorBar.appendChild(wildcardBtn);
            }
        },

        calculateAllGroupStandings: function () {
            var winPts = this.rules.winPoints;
            var drawPts = this.rules.drawPoints;
            var lossPts = this.rules.lossPoints;

            var results = {};
            var groupKeys = Object.keys(this.groups);

            for (var k = 0; k < groupKeys.length; k++) {
                var gKey = groupKeys[k];
                var teamList = this.groups[gKey] || [];
                var mList = this.groupMatches[gKey] || [];

                var standingsMap = {};
                var teamIdToKey = {};
                var teamNameToKey = {};

                for (var t = 0; t < teamList.length; t++) {
                    var tm = teamList[t];
                    var tId = (tm && tm.id !== undefined) ? tm.id : (t + 1);
                    var tName = (tm && tm.name) ? tm.name : String(tm);
                    var mainKey = 'team_' + tId;

                    var record = {
                        groupKey: gKey,
                        teamId: tId,
                        name: tName,
                        mp: 0,
                        w: 0,
                        d: 0,
                        l: 0,
                        gf: 0,
                        ga: 0,
                        gd: 0,
                        pts: 0
                    };

                    standingsMap[mainKey] = record;
                    teamIdToKey[String(tId)] = mainKey;
                    if (tName) teamNameToKey[String(tName).trim().toLowerCase()] = mainKey;
                }

                function findTeamRecord(tObj) {
                    if (!tObj) return null;
                    if (tObj.id !== undefined && teamIdToKey[String(tObj.id)]) {
                        return standingsMap[teamIdToKey[String(tObj.id)]];
                    }
                    if (tObj.name && teamNameToKey[String(tObj.name).trim().toLowerCase()]) {
                        return standingsMap[teamNameToKey[String(tObj.name).trim().toLowerCase()]];
                    }
                    if (typeof tObj === 'string' && teamNameToKey[tObj.trim().toLowerCase()]) {
                        return standingsMap[teamNameToKey[tObj.trim().toLowerCase()]];
                    }
                    return null;
                }

                for (var m = 0; m < mList.length; m++) {
                    var match = mList[m];
                    if (!match || !match.team1 || !match.team2) continue;

                    var isCompleted = (match.status === 'COMPLETED');
                    var s1Raw = match.team1.score;
                    var s2Raw = match.team2.score;
                    var hasScores = (s1Raw !== undefined && s1Raw !== null && String(s1Raw).trim() !== '' &&
                                 s2Raw !== undefined && s2Raw !== null && String(s2Raw).trim() !== '');

                    if (isCompleted || hasScores) {
                        var s1 = parseInt(String(s1Raw).trim(), 10);
                        var s2 = parseInt(String(s2Raw).trim(), 10);

                        if (!isNaN(s1) && !isNaN(s2)) {
                            var st1 = findTeamRecord(match.team1);
                            var st2 = findTeamRecord(match.team2);

                            if (st1 && st2) {
                                st1.mp++;
                                st2.mp++;
                                st1.gf += s1;
                                st1.ga += s2;
                                st2.gf += s2;
                                st2.ga += s1;

                                if (s1 > s2) {
                                    st1.w++;
                                    st1.pts += winPts;
                                    st2.l++;
                                    st2.pts += lossPts;
                                } else if (s2 > s1) {
                                    st2.w++;
                                    st2.pts += winPts;
                                    st1.l++;
                                    st1.pts += lossPts;
                                } else {
                                    st1.d++;
                                    st1.pts += drawPts;
                                    st2.d++;
                                    st2.pts += drawPts;
                                }

                                st1.gd = st1.gf - st1.ga;
                                st2.gd = st2.gf - st2.ga;
                            }
                        }
                    }
                }

                var standingsArray = [];
                for (var key in standingsMap) {
                    standingsArray.push(standingsMap[key]);
                }

                standingsArray.sort(function (a, b) {
                    if (b.pts !== a.pts) return b.pts - a.pts;
                    if (b.gd !== a.gd) return b.gd - a.gd;
                    if (b.gf !== a.gf) return b.gf - a.gf;
                    return a.name.localeCompare(b.name);
                });

                results[gKey] = standingsArray;
            }

            return results;
        },

        renderStandingsTables: function (container) {
            container.innerHTML = '';

            var groupKeys = Object.keys(this.groups);
            if (groupKeys.length === 0) {
                container.innerHTML = '<div style="text-align:center; padding:3rem; color:#94a3b8;">Chưa có bảng đấu. Vui lòng bấm "Quản Lý Chia Bảng" để chia bảng.</div>';
                return;
            }

            var cfg = this.getAdvanceConfig();
            var groupStandingsData = this.calculateAllGroupStandings();

            // IF WILDCARD FILTER IS SELECTED AND REMAINDER > 0
            if (this.selectedGroupFilter === 'WILDCARD' && cfg.remainder > 0) {
                this.renderWildcardTable(container, groupStandingsData, cfg);
                return;
            }

            // REGULAR GROUP TABLES
            for (var k = 0; k < groupKeys.length; k++) {
                var gKey = groupKeys[k];

                if (this.selectedGroupFilter !== 'ALL' && this.selectedGroupFilter !== gKey) {
                    continue;
                }

                var standingsArray = groupStandingsData[gKey] || [];

                var block = document.createElement('div');
                block.className = 'gst-group-block';
                block.style.marginBottom = '2.25rem';

                var headerHtml = '<div style="font-size: 1.15rem; font-weight: 800; color: #fbbf24; padding-bottom: 0.6rem; margin-bottom: 1.25rem; border-bottom: 1px solid rgba(255, 255, 255, 0.1); display: flex; justify-content: space-between; align-items: center;">' +
                    '<div>BẢNG ' + gKey + '</div>' +
                    '</div>';

                var tableHtml = '<div class="rr-standings-table-wrapper">' +
                    '<table class="rr-table">' +
                    '<thead>' +
                    '<tr>' +
                    '<th style="width: 48px; text-align: center;">#</th>' +
                    '<th style="text-align: left;">Đội bóng</th>' +
                    '<th style="width: 70px; text-align: center;">Trận</th>' +
                    '<th style="width: 60px; text-align: center; color: #2dd4bf;">T</th>' +
                    '<th style="width: 60px; text-align: center; color: #2dd4bf;">H</th>' +
                    '<th style="width: 60px; text-align: center; color: #2dd4bf;">B</th>' +
                    '<th style="width: 90px; text-align: center;">BT/BB</th>' +
                    '<th style="width: 70px; text-align: center;">HS</th>' +
                    '<th style="width: 80px; text-align: center; color: #fbbf24;">Điểm</th>' +
                    '</tr>' +
                    '</thead>' +
                    '<tbody>';

                for (var rank = 0; rank < standingsArray.length; rank++) {
                    var row = standingsArray[rank];

                    var isDirect = (rank < cfg.directPerGroup);
                    var isWildcard = (cfg.remainder > 0 && rank === cfg.wildcardRankIndex);

                    var qClass = '';
                    if (isDirect) {
                        qClass = ' class="qualified-direct"';
                    } else if (isWildcard) {
                        qClass = ' class="qualified-wildcard"';
                    }

                    tableHtml += '<tr' + qClass + '>' +
                        '<td style="text-align: center;"><span class="rr-rank-badge">' + (rank + 1) + '</span></td>' +
                        '<td style="font-weight: 700; color: #f8fafc; text-align: left;">' + row.name + '</td>' +
                        '<td style="text-align: center;" class="rr-stat-cell">' + row.mp + '</td>' +
                        '<td style="text-align: center;" class="rr-stat-green">' + row.w + '</td>' +
                        '<td style="text-align: center;" class="rr-stat-green">' + row.d + '</td>' +
                        '<td style="text-align: center;" class="rr-stat-green">' + row.l + '</td>' +
                        '<td style="text-align: center;" class="rr-stat-cell">' + row.gf + '/' + row.ga + '</td>' +
                        '<td style="text-align: center;" class="rr-stat-cell">' + (row.gd > 0 ? '+' + row.gd : row.gd) + '</td>' +
                        '<td style="text-align: center; font-weight: 800; color: #fbbf24; font-size: 1.05rem;">' + row.pts + '</td>' +
                        '</tr>';
                }
                tableHtml += '</tbody></table></div>';

                block.innerHTML = headerHtml + tableHtml;
                container.appendChild(block);
            }
        },

        renderWildcardTable: function (container, groupStandingsData, cfg) {
            var wildcardCandidates = [];
            var groupKeys = Object.keys(groupStandingsData);

            for (var k = 0; k < groupKeys.length; k++) {
                var gKey = groupKeys[k];
                var stList = groupStandingsData[gKey] || [];
                if (stList.length > cfg.wildcardRankIndex) {
                    wildcardCandidates.push(stList[cfg.wildcardRankIndex]);
                }
            }

            // Sort candidates: PTS desc, GD desc, GF desc, Name asc
            wildcardCandidates.sort(function (a, b) {
                if (b.pts !== a.pts) return b.pts - a.pts;
                if (b.gd !== a.gd) return b.gd - a.gd;
                if (b.gf !== a.gf) return b.gf - a.gf;
                return a.name.localeCompare(b.name);
            });

            var block = document.createElement('div');
            block.className = 'gst-group-block';
            block.style.marginBottom = '2.25rem';

            var targetRankNum = cfg.wildcardRankIndex + 1;
            var headerHtml = '<div style="font-size: 1.15rem; font-weight: 800; color: #fbbf24; padding-bottom: 0.6rem; margin-bottom: 1.25rem; border-bottom: 1px solid rgba(255, 255, 255, 0.1); display: flex; justify-content: space-between; align-items: center;">' +
                '<div>BẢNG SO SÁNH CÁC ĐỘI HẠNG ' + targetRankNum + ' XUẤT SẮC</div>' +
                '</div>';

            var tableHtml = '<div class="rr-standings-table-wrapper">' +
                '<table class="rr-table">' +
                '<thead>' +
                '<tr>' +
                '<th style="width: 48px; text-align: center;">#</th>' +
                '<th style="text-align: left;">Đội bóng</th>' +
                '<th style="width: 90px; text-align: center;">Bảng</th>' +
                '<th style="width: 70px; text-align: center;">Trận</th>' +
                '<th style="width: 60px; text-align: center; color: #2dd4bf;">T</th>' +
                '<th style="width: 60px; text-align: center; color: #2dd4bf;">H</th>' +
                '<th style="width: 60px; text-align: center; color: #2dd4bf;">B</th>' +
                '<th style="width: 90px; text-align: center;">BT/BB</th>' +
                '<th style="width: 70px; text-align: center;">HS</th>' +
                '<th style="width: 80px; text-align: center; color: #fbbf24;">Điểm</th>' +
                '</tr>' +
                '</thead>' +
                '<tbody>';

            for (var rank = 0; rank < wildcardCandidates.length; rank++) {
                var row = wildcardCandidates[rank];
                var isWildcardWinner = (rank < cfg.remainder);
                var qClass = isWildcardWinner ? ' class="qualified-wildcard"' : '';

                tableHtml += '<tr' + qClass + '>' +
                    '<td style="text-align: center;"><span class="rr-rank-badge">' + (rank + 1) + '</span></td>' +
                    '<td style="font-weight: 700; color: #f8fafc; text-align: left;">' + row.name + '</td>' +
                    '<td style="text-align: center; font-weight: 700; color: #fbbf24;">Bảng ' + row.groupKey + '</td>' +
                    '<td style="text-align: center;" class="rr-stat-cell">' + row.mp + '</td>' +
                    '<td style="text-align: center;" class="rr-stat-green">' + row.w + '</td>' +
                    '<td style="text-align: center;" class="rr-stat-green">' + row.d + '</td>' +
                    '<td style="text-align: center;" class="rr-stat-green">' + row.l + '</td>' +
                    '<td style="text-align: center;" class="rr-stat-cell">' + row.gf + '/' + row.ga + '</td>' +
                    '<td style="text-align: center;" class="rr-stat-cell">' + (row.gd > 0 ? '+' + row.gd : row.gd) + '</td>' +
                    '<td style="text-align: center; font-weight: 800; color: #fbbf24; font-size: 1.05rem;">' + row.pts + '</td>' +
                    '</tr>';
            }

            tableHtml += '</tbody></table></div>';

            block.innerHTML = headerHtml + tableHtml;
            container.appendChild(block);
        },

        render: function (groups, groupMatches, rules) {
            this.renderAllGroupStandings('gsStandingsContainer', groups, groupMatches, rules);
        },

        getQualifiedTeamsCrossover: function (tournamentId, groups, groupMatches, totalAdvanceCount) {
            this.groups = groups || this.groups || {};
            this.groupMatches = groupMatches || this.groupMatches || {};
            if (totalAdvanceCount) this.rules.advanceCount = Number(totalAdvanceCount);

            var standings = this.calculateAllGroupStandings();
            var groupKeys = Object.keys(standings).sort();
            var numGroups = groupKeys.length || 1;
            var advCount = this.rules.advanceCount || 4;

            var directPerGroup = Math.floor(advCount / numGroups);
            var remainder = advCount % numGroups;

            var qualifiedList = [];

            // 1. Direct qualifiers
            for (var k = 0; k < groupKeys.length; k++) {
                var gKey = groupKeys[k];
                var gList = standings[gKey] || [];
                var limit = Math.min(directPerGroup, gList.length);
                for (var i = 0; i < limit; i++) {
                    var tm = gList[i];
                    qualifiedList.push({
                        id: tm.teamId,
                        name: tm.name,
                        groupKey: gKey,
                        groupRank: i + 1,
                        points: tm.pts,
                        goalDifference: tm.gd,
                        goalsFor: tm.gf,
                        isWildcard: false
                    });
                }
            }

            // 2. Wildcards
            if (remainder > 0) {
                var wildcardCandidates = [];
                var wildcardRankIndex = directPerGroup;

                for (var k = 0; k < groupKeys.length; k++) {
                    var gKey = groupKeys[k];
                    var gList = standings[gKey] || [];
                    if (gList.length > wildcardRankIndex) {
                        var candidate = gList[wildcardRankIndex];
                        wildcardCandidates.push({
                            id: candidate.teamId,
                            name: candidate.name,
                            groupKey: gKey,
                            groupRank: wildcardRankIndex + 1,
                            points: candidate.pts,
                            goalDifference: candidate.gd,
                            goalsFor: candidate.gf,
                            isWildcard: true
                        });
                    }
                }

                wildcardCandidates.sort(function (a, b) {
                    if (b.points !== a.points) return b.points - a.points;
                    if (b.goalDifference !== a.goalDifference) return b.goalDifference - a.goalDifference;
                    if (b.goalsFor !== a.goalsFor) return b.goalsFor - a.goalsFor;
                    return (a.name || '').localeCompare(b.name || '');
                });

                var takeCount = Math.min(remainder, wildcardCandidates.length);
                for (var w = 0; w < takeCount; w++) {
                    qualifiedList.push(wildcardCandidates[w]);
                }
            }

            // 3. Dynamic Crossover Pairing
            return this.pairCrossoverForKnockout(qualifiedList);
        },

        pairCrossoverForKnockout: function (qualifiedTeams) {
            if (!qualifiedTeams || qualifiedTeams.length === 0) return [];

            var count = qualifiedTeams.length;
            var numGroups = 0;
            var groupLookup = {};
            for (var i = 0; i < qualifiedTeams.length; i++) {
                var gt = qualifiedTeams[i];
                if (!groupLookup[gt.groupKey]) {
                    groupLookup[gt.groupKey] = {};
                    numGroups++;
                }
                groupLookup[gt.groupKey][gt.groupRank] = gt.name;
            }

            // CASE 1: 2 GROUPS (A, B) -> 4 Teams (Semi-Finals)
            // Desired bracket matches:
            // Match 1: 1A vs 2B
            // Match 2: 1B vs 2A
            if (numGroups === 2 && count === 4 && groupLookup['A'] && groupLookup['B']) {
                var t1A = groupLookup['A'][1] || '1A';
                var t2A = groupLookup['A'][2] || '2A';
                var t1B = groupLookup['B'][1] || '1B';
                var t2B = groupLookup['B'][2] || '2B';
                // Bracket pairing (1 vs 4, 2 vs 3) -> 1A vs 2B, 1B vs 2A
                return [t1A, t1B, t2A, t2B];
            }

            // CASE 2: 2 GROUPS (A, B) -> 8 Teams (Top 4 each)
            // Desired bracket matches:
            // Match 1: 1A vs 4B
            // Match 2: 2B vs 3A
            // Match 3: 1B vs 4A
            // Match 4: 2A vs 3B
            if (numGroups === 2 && count === 8 && groupLookup['A'] && groupLookup['B']) {
                var t1A = groupLookup['A'][1], t2A = groupLookup['A'][2], t3A = groupLookup['A'][3], t4A = groupLookup['A'][4];
                var t1B = groupLookup['B'][1], t2B = groupLookup['B'][2], t3B = groupLookup['B'][3], t4B = groupLookup['B'][4];
                return [t1A, t1B, t2A, t2B, t3A, t3B, t4A, t4B];
            }

            // CASE 3: 4 GROUPS (A, B, C, D) -> 8 Teams (Quarter-Finals)
            // Desired bracket matches:
            // Match 1: 1A vs 2B
            // Match 2: 1C vs 2D
            // Match 3: 1B vs 2A
            // Match 4: 1D vs 2C
            if (numGroups === 4 && count === 8 && groupLookup['A'] && groupLookup['B'] && groupLookup['C'] && groupLookup['D']) {
                var t1A = groupLookup['A'][1], t2A = groupLookup['A'][2];
                var t1B = groupLookup['B'][1], t2B = groupLookup['B'][2];
                var t1C = groupLookup['C'][1], t2C = groupLookup['C'][2];
                var t1D = groupLookup['D'][1], t2D = groupLookup['D'][2];
                return [t1A, t1B, t1D, t1C, t2D, t2C, t2A, t2B];
            }

            // CASE 4: 4 GROUPS (A, B, C, D) -> 16 Teams (Round of 16)
            if (numGroups === 4 && count === 16 && groupLookup['A'] && groupLookup['B'] && groupLookup['C'] && groupLookup['D']) {
                var t1A = groupLookup['A'][1], t2A = groupLookup['A'][2], t3A = groupLookup['A'][3], t4A = groupLookup['A'][4];
                var t1B = groupLookup['B'][1], t2B = groupLookup['B'][2], t3B = groupLookup['B'][3], t4B = groupLookup['B'][4];
                var t1C = groupLookup['C'][1], t2C = groupLookup['C'][2], t3C = groupLookup['C'][3], t4C = groupLookup['C'][4];
                var t1D = groupLookup['D'][1], t2D = groupLookup['D'][2], t3D = groupLookup['D'][3], t4D = groupLookup['D'][4];
                return [t1A, t1B, t1D, t1C, t2D, t2C, t2A, t2B, t3A, t3B, t3D, t3C, t4D, t4C, t4A, t4B];
            }

            // General Fallback
            var rank1 = [];
            var others = [];
            for (var i = 0; i < qualifiedTeams.length; i++) {
                if (qualifiedTeams[i].groupRank === 1) rank1.push(qualifiedTeams[i].name);
                else others.push(qualifiedTeams[i].name);
            }
            return rank1.concat(others);
        }
    };
})();
