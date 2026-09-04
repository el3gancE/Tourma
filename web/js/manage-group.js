/**
 * Tourma Manage Group JS Engine
 * Default state: Clean empty workspace initially.
 * Supports HTML5 Drag & Drop moving teams between group columns,
 * Auto distribution ("Chia Đều Đội Về Bảng"), Modal popup team selection,
 * and minimum 2 groups requirement validation.
 */

(function () {
    'use strict';

    window.TourmaManageGroup = {
        tournamentId: '',
        groups: {},
        teamsList: [],
        activeModalGroup: null,

        init: function () {
            var urlParams = new URLSearchParams(window.location.search);
            this.tournamentId = urlParams.get('id') || 'demo';

            this.loadData();
            this.renderWorkspace();
        },

        loadData: function () {
            // Load teams list
            var rawTeams = localStorage.getItem('tourma_teams_' + this.tournamentId);
            if (rawTeams) {
                try { 
                    var parsed = JSON.parse(rawTeams);
                    this.teamsList = parsed.map(function (item, idx) {
                        if (typeof item === 'string') {
                            return { id: idx + 1, name: item };
                        }
                        if (item && !item.id) {
                            item.id = idx + 1;
                        }
                        return item;
                    });
                } catch (e) { this.teamsList = []; }
            }

            // Load saved group assignments (default is empty object {})
            var savedGroupsRaw = localStorage.getItem('tourma_group_assignments_' + this.tournamentId);
            if (savedGroupsRaw) {
                try { this.groups = JSON.parse(savedGroupsRaw); } catch (e) { this.groups = {}; }
            } else {
                this.groups = {}; // Default: NO groups created initially!
            }
        },

        getTeamSeedNumber: function (team) {
            var tid = (typeof team === 'object') ? team.id : team;
            for (var i = 0; i < this.teamsList.length; i++) {
                var item = this.teamsList[i];
                var itemTid = (typeof item === 'object') ? item.id : item;
                if (itemTid == tid) {
                    return i + 1;
                }
            }
            return 1;
        },

        getUnassignedTeams: function () {
            // Find teams that are NOT assigned to any group
            var assignedIds = {};
            for (var gKey in this.groups) {
                var teamList = this.groups[gKey] || [];
                for (var i = 0; i < teamList.length; i++) {
                    var tid = (typeof teamList[i] === 'object') ? teamList[i].id : teamList[i];
                    assignedIds[tid] = true;
                }
            }

            var unassigned = [];
            for (var j = 0; j < this.teamsList.length; j++) {
                var team = this.teamsList[j];
                var tid = (typeof team === 'object') ? team.id : team;
                if (!assignedIds[tid]) {
                    unassigned.push(team);
                }
            }
            return unassigned;
        },

        addNewGroup: function () {
            var letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
            var nextName = '';

            for (var i = 0; i < 26; i++) {
                var charKey = letters.charAt(i);
                if (!this.groups[charKey]) {
                    nextName = charKey;
                    break;
                }
            }
            if (!nextName) {
                for (var i = 26; i < 702; i++) {
                    var first = letters.charAt(Math.floor(i / 26) - 1);
                    var second = letters.charAt(i % 26);
                    var doubleKey = first + second;
                    if (!this.groups[doubleKey]) {
                        nextName = doubleKey;
                        break;
                    }
                }
            }
            if (!nextName) {
                var existingKeys = Object.keys(this.groups);
                nextName = String(existingKeys.length + 1);
            }

            this.groups[nextName] = [];
            this.saveGroupsState();
            this.renderWorkspace();
        },

        deleteGroup: function (gKey) {
            if (!this.groups[gKey]) return;

            var displayName = gKey.startsWith('Bảng') ? gKey : ('Bảng ' + gKey);
            if (confirm('Bạn có chắc chắn muốn xóa ' + displayName + '? Các đội trong bảng này sẽ quay về danh sách đội chưa xếp.')) {
                delete this.groups[gKey];
                this.saveGroupsState();
                this.renderWorkspace();
            }
        },

        handleAutoDistribute: function () {
            var groupKeys = Object.keys(this.groups);
            if (groupKeys.length < 2) {
                alert('⚠️ Cần có tối thiểu 2 BẢNG ĐẤU để chia ngẫu nhiên các đội!\n\nVui lòng bấm "+ Thêm Bảng Mới" để tạo ít nhất 2 bảng (Bảng A, Bảng B...) trước.');
                return;
            }

            var totalTeams = this.teamsList ? this.teamsList.length : 0;
            if (totalTeams < 2) {
                alert('Cần tối thiểu 2 đội bóng để bốc thăm ngẫu nhiên về các bảng!');
                return;
            }

            // Clear existing team lists in created groups
            for (var k = 0; k < groupKeys.length; k++) {
                this.groups[groupKeys[k]] = [];
            }

            var K = groupKeys.length; // Number of created groups

            // Helper to shuffle an array in place (Fisher-Yates shuffle)
            function shuffleArray(arr) {
                var array = arr.slice();
                for (var i = array.length - 1; i > 0; i--) {
                    var j = Math.floor(Math.random() * (i + 1));
                    var temp = array[i];
                    array[i] = array[j];
                    array[j] = temp;
                }
                return array;
            }

            // Seeded Pot Distribution: Group teams into pots of size K (#1..#K, #K+1..#2K, etc.)
            // For each pot, randomly shuffle target groups so top seeds are split 1 per group randomly!
            for (var startIdx = 0; startIdx < totalTeams; startIdx += K) {
                var potTeams = this.teamsList.slice(startIdx, startIdx + K);
                var shuffledGroups = shuffleArray(groupKeys);

                for (var p = 0; p < potTeams.length; p++) {
                    var targetGroup = shuffledGroups[p];
                    this.groups[targetGroup].push(potTeams[p]);
                }
            }

            this.saveGroupsState();
            this.renderWorkspace();
        },

        openAddTeamModal: function (gKey) {
            this.activeModalGroup = gKey;
            var modal = document.getElementById('addTeamToGroupModalBackdrop');
            var titleElem = document.getElementById('modalGroupTitleDisplay');
            var container = document.getElementById('modalTeamChecklistContainer');
            var countElem = document.getElementById('modalSelectedCountDisplay');

            if (titleElem) titleElem.innerText = 'Thêm Đội Vào Bảng ' + gKey;

            var availableTeams = this.getUnassignedTeams();
            if (container) {
                container.innerHTML = '';
                if (availableTeams.length === 0) {
                    container.innerHTML = '<div style="text-align:center; padding: 2rem 0; color: #94a3b8; font-size: 0.85rem;">' +
                        '<i class="fa-solid fa-circle-info" style="color: #2dd4bf;"></i> Không còn đội nào chưa xếp bảng!' +
                        '</div>';
                } else {
                    for (var i = 0; i < availableTeams.length; i++) {
                        var team = availableTeams[i];
                        var tId = (typeof team === 'object') ? team.id : (i + 1);
                        var tName = (typeof team === 'object') ? team.name : team;
                        var seedNum = this.getTeamSeedNumber(team);

                        var row = document.createElement('label');
                        row.style.display = 'flex';
                        row.style.alignItems = 'center';
                        row.style.justifyContent = 'space-between';
                        row.style.background = '#181d29';
                        row.style.border = '1px solid rgba(255, 255, 255, 0.08)';
                        row.style.borderRadius = '8px';
                        row.style.padding = '0.65rem 0.85rem';
                        row.style.marginBottom = '0.4rem';
                        row.style.cursor = 'pointer';

                        row.innerHTML = '<div style="display:flex; align-items:center; gap:0.4rem; font-size:0.85rem; font-weight:600; color:#f8fafc;">' +
                            '<span class="mg-seed-badge">' + seedNum + '</span>' +
                            '<span>' + tName + '</span>' +
                            '</div>' +
                            '<input type="checkbox" class="modal-team-checkbox" value="' + tId + '" onchange="TourmaManageGroup.updateModalSelectedCount()" style="width:16px; height:16px; accent-color:#2dd4bf; cursor:pointer;">';

                        container.appendChild(row);
                    }
                }
            }

            if (countElem) countElem.innerText = '0';
            if (modal) modal.style.display = 'flex';
        },

        closeAddTeamModal: function () {
            var modal = document.getElementById('addTeamToGroupModalBackdrop');
            if (modal) modal.style.display = 'none';
            this.activeModalGroup = null;
        },

        updateModalSelectedCount: function () {
            var checkboxes = document.querySelectorAll('.modal-team-checkbox:checked');
            var countElem = document.getElementById('modalSelectedCountDisplay');
            if (countElem) countElem.innerText = checkboxes.length;
        },

        confirmAddTeamsToGroup: function () {
            if (!this.activeModalGroup || !this.groups[this.activeModalGroup]) return;

            var checkboxes = document.querySelectorAll('.modal-team-checkbox:checked');
            if (checkboxes.length === 0) {
                this.closeAddTeamModal();
                return;
            }

            var selectedIds = [];
            checkboxes.forEach(function (cb) {
                selectedIds.push(cb.value);
            });

            var availableTeams = this.getUnassignedTeams();
            for (var i = 0; i < availableTeams.length; i++) {
                var team = availableTeams[i];
                var tId = (typeof team === 'object') ? team.id : (i + 1);
                if (selectedIds.indexOf(String(tId)) !== -1) {
                    this.groups[this.activeModalGroup].push(team);
                }
            }

            this.saveGroupsState();
            this.closeAddTeamModal();
            this.renderWorkspace();
        },

        removeTeamFromGroup: function (gKey, teamId) {
            if (!this.groups[gKey]) return;
            var list = this.groups[gKey];

            for (var i = 0; i < list.length; i++) {
                var tid = (typeof list[i] === 'object') ? list[i].id : (i + 1);
                if (tid == teamId) {
                    list.splice(i, 1);
                    break;
                }
            }

            this.saveGroupsState();
            this.renderWorkspace();
        },

        moveTeam: function (teamId, fromGroup, toGroup) {
            if (fromGroup === toGroup) return;

            var targetTeam = null;

            if (fromGroup === 'UNASSIGNED') {
                for (var u = 0; u < this.teamsList.length; u++) {
                    var tid = (typeof this.teamsList[u] === 'object') ? this.teamsList[u].id : (u + 1);
                    if (tid == teamId) {
                        targetTeam = this.teamsList[u];
                        break;
                    }
                }
            } else {
                var fromList = this.groups[fromGroup] || [];
                for (var f = 0; f < fromList.length; f++) {
                    var ftid = (typeof fromList[f] === 'object') ? fromList[f].id : (f + 1);
                    if (ftid == teamId) {
                        targetTeam = fromList[f];
                        fromList.splice(f, 1);
                        break;
                    }
                }
            }

            if (targetTeam) {
                if (toGroup !== 'UNASSIGNED') {
                    if (!this.groups[toGroup]) this.groups[toGroup] = [];
                    this.groups[toGroup].push(targetTeam);
                }
                this.saveGroupsState();
                this.renderWorkspace();
            }
        },

        renderWorkspace: function () {
            var container = document.getElementById('mgWorkspace');
            if (!container) return;
            container.innerHTML = '';
            var self = this;

            var groupKeys = Object.keys(this.groups);
            var unassignedTeams = this.getUnassignedTeams();

            // 1. TOP SUMMARY BAR
            var summaryBar = document.createElement('div');
            summaryBar.style.gridColumn = '1 / -1';
            summaryBar.style.display = 'flex';
            summaryBar.style.justifyContent = 'space-between';
            summaryBar.style.alignItems = 'center';
            summaryBar.style.background = 'rgba(18, 22, 32, 0.8)';
            summaryBar.style.border = '1px solid rgba(255, 255, 255, 0.08)';
            summaryBar.style.borderRadius = '10px';
            summaryBar.style.padding = '0.75rem 1.25rem';
            summaryBar.style.marginBottom = '0.5rem';

            var unassignedCount = unassignedTeams.length;
            var totalCount = this.teamsList.length;

            summaryBar.innerHTML = '<div>' +
                '<span style="font-size: 0.85rem; font-weight: 700; color: #f8fafc;">Tổng Số Đội: ' + totalCount + ' Đội</span>' +
                '<span style="font-size: 0.8rem; color: #94a3b8; margin-left: 1rem;">| Đội chưa xếp bảng: <strong style="color: ' + (unassignedCount > 0 ? '#fbbf24' : '#2dd4bf') + ';">' + unassignedCount + ' Đội</strong></span>' +
                '</div>' +
                '<div style="font-size: 0.78rem; color: #94a3b8;">' +
                '<i class="fa-solid fa-hand-pointer text-mint"></i> Kéo thả đội hoặc bấm ô <strong>"+"</strong> để xếp đội vào bảng' +
                '</div>';

            container.appendChild(summaryBar);

            // 2. EMPTY WORKSPACE NOTICE IF NO GROUPS CREATED
            if (groupKeys.length === 0) {
                var emptyNotice = document.createElement('div');
                emptyNotice.className = 'mg-group-column';
                emptyNotice.style.gridColumn = '1 / -1';
                emptyNotice.style.display = 'flex';
                emptyNotice.style.flexDirection = 'column';
                emptyNotice.style.alignItems = 'center';
                emptyNotice.style.justifyContent = 'center';
                emptyNotice.style.padding = '3.5rem 1.5rem';
                emptyNotice.style.textAlign = 'center';
                emptyNotice.style.background = '#121620';
                emptyNotice.style.borderRadius = '12px';
                emptyNotice.style.border = '1px dashed rgba(255, 255, 255, 0.12)';

                emptyNotice.innerHTML = '<i class="fa-solid fa-layer-group" style="font-size: 2.8rem; color: #2dd4bf; margin-bottom: 1rem;"></i>' +
                    '<h3 style="font-size: 1.15rem; font-weight: 800; color: #f8fafc; margin: 0 0 0.5rem 0;">Chưa có bảng đấu nào</h3>' +
                    '<p style="font-size: 0.85rem; color: #94a3b8; margin: 0 0 1.5rem 0; max-width: 420px;">Giải đấu Vòng Bảng bắt buộc cần <strong>tối thiểu 2 BẢNG ĐẤU</strong>.<br>Vui lòng bấm <strong>"+ Thêm Bảng Mới"</strong> hoặc <strong>"Chia Đều Đội Về Bảng"</strong> để tạo bảng.</p>' +
                    '<div style="display:flex; gap: 0.75rem;">' +
                    '<button type="button" class="btn-mg-add" onclick="TourmaManageGroup.addNewGroup()" style="padding: 0.55rem 1.25rem;"><i class="fa-solid fa-plus"></i> Thêm Bảng Mới</button>' +
                    '<button type="button" class="btn-mg-distribute" onclick="TourmaManageGroup.handleAutoDistribute()" style="padding: 0.55rem 1.25rem;">Ngẫu Nhiên</button>' +
                    '</div>';

                container.appendChild(emptyNotice);
                return;
            }

            // 3. RENDER GROUP COLUMNS WITH DRAG & DROP
            for (var k = 0; k < groupKeys.length; k++) {
                (function(gKey) {
                    var teamList = self.groups[gKey] || [];

                    var col = document.createElement('div');
                    col.className = 'mg-group-column';
                    col.dataset.groupKey = gKey;

                    // Drag Over & Drop Events for Group Column
                    col.addEventListener('dragover', function(e) {
                        e.preventDefault();
                        col.classList.add('mg-drag-over');
                    });
                    col.addEventListener('dragleave', function() {
                        col.classList.remove('mg-drag-over');
                    });
                    col.addEventListener('drop', function(e) {
                        e.preventDefault();
                        col.classList.remove('mg-drag-over');
                        try {
                            var data = JSON.parse(e.dataTransfer.getData('text/plain'));
                            if (data && data.teamId && data.fromGroup !== gKey) {
                                self.moveTeam(data.teamId, data.fromGroup, gKey);
                            }
                        } catch (err) {}
                    });

                    var headerHtml = '<div class="mg-group-header">' +
                        '<div class="mg-group-title"><i class="fa-solid fa-layer-group"></i> BẢNG ' + gKey + '</div>' +
                        '<div style="display: flex; align-items: center; gap: 0.5rem;">' +
                        '<span style="font-size: 0.75rem; color: #94a3b8;">' + teamList.length + ' Đội</span>' +
                        '<button type="button" class="btn-delete-group" onclick="TourmaManageGroup.deleteGroup(\'' + gKey + '\')" title="Xóa Bảng ' + gKey + '">' +
                        '<i class="fa-solid fa-trash-can"></i>' +
                        '</button>' +
                        '</div>' +
                        '</div>';

                    col.innerHTML = headerHtml;

                    var listWrapper = document.createElement('div');
                    listWrapper.className = 'mg-team-list-wrapper';

                    if (teamList.length === 0) {
                        // Fully Clickable Middle Dashed Box
                        var emptyBox = document.createElement('div');
                        emptyBox.onclick = function() { self.openAddTeamModal(gKey); };
                        emptyBox.style.fontSize = '0.8rem';
                        emptyBox.style.color = '#94a3b8';
                        emptyBox.style.textAlign = 'center';
                        emptyBox.style.padding = '1.75rem 1rem';
                        emptyBox.style.border = '1px dashed rgba(45, 212, 191, 0.35)';
                        emptyBox.style.borderRadius = '8px';
                        emptyBox.style.cursor = 'pointer';
                        emptyBox.style.background = 'rgba(45, 212, 191, 0.03)';
                        emptyBox.style.transition = 'all 0.2s ease';

                        emptyBox.onmouseover = function() {
                            this.style.borderColor = '#2dd4bf';
                            this.style.background = 'rgba(45, 212, 191, 0.08)';
                        };
                        emptyBox.onmouseout = function() {
                            this.style.borderColor = 'rgba(45, 212, 191, 0.35)';
                            this.style.background = 'rgba(45, 212, 191, 0.03)';
                        };

                        emptyBox.innerHTML = '<div style="width: 32px; height: 32px; border-radius: 50%; background: rgba(45, 212, 191, 0.15); color: #2dd4bf; display: inline-flex; align-items: center; justify-content: center; margin-bottom: 0.5rem;"><i class="fa-solid fa-plus"></i></div><br>' +
                            'Bảng này đang trống.<br><strong style="color: #2dd4bf;">Bấm vào đây để chọn đội vào bảng.</strong>';

                        listWrapper.appendChild(emptyBox);
                    } else {
                        // Render draggable teams with #seed badges
                        for (var i = 0; i < teamList.length; i++) {
                            (function(team) {
                                var item = document.createElement('div');
                                item.className = 'mg-team-card';
                                item.draggable = true;

                                var tId = (typeof team === 'object') ? team.id : (i + 1);
                                var tName = (typeof team === 'object') ? team.name : team;
                                var seedNum = self.getTeamSeedNumber(team);

                                item.addEventListener('dragstart', function(e) {
                                    e.dataTransfer.setData('text/plain', JSON.stringify({ teamId: tId, fromGroup: gKey }));
                                    item.style.opacity = '0.5';
                                });
                                item.addEventListener('dragend', function() {
                                    item.style.opacity = '1';
                                });

                                var infoHtml = '<div class="mg-team-info">' +
                                    '<span class="mg-seed-badge">' + seedNum + '</span>' +
                                    '<span>' + tName + '</span>' +
                                    '</div>';

                                var removeBtnHtml = '<button type="button" onclick="TourmaManageGroup.removeTeamFromGroup(\'' + gKey + '\', ' + tId + ')" title="Bỏ đội ra khỏi bảng" style="background:transparent; border:none; color:#94a3b8; font-size:0.85rem; cursor:pointer; padding:0.2rem 0.4rem; border-radius:4px; transition:color 0.2s ease;">' +
                                    '<i class="fa-solid fa-xmark"></i>' +
                                    '</button>';

                                item.innerHTML = infoHtml + removeBtnHtml;
                                listWrapper.appendChild(item);
                            })(teamList[i]);
                        }

                        // Add team dashed button at bottom of list
                        var addMoreRow = document.createElement('div');
                        addMoreRow.onclick = function() { self.openAddTeamModal(gKey); };
                        addMoreRow.style.fontSize = '0.78rem';
                        addMoreRow.style.color = '#2dd4bf';
                        addMoreRow.style.textAlign = 'center';
                        addMoreRow.style.padding = '0.5rem';
                        addMoreRow.style.border = '1px dashed rgba(45, 212, 191, 0.3)';
                        addMoreRow.style.borderRadius = '6px';
                        addMoreRow.style.marginTop = '0.6rem';
                        addMoreRow.style.cursor = 'pointer';
                        addMoreRow.style.fontWeight = '600';
                        addMoreRow.style.background = 'rgba(45, 212, 191, 0.03)';
                        addMoreRow.innerHTML = '<i class="fa-solid fa-plus"></i> Thêm Đội Vào Bảng ' + gKey;

                        listWrapper.appendChild(addMoreRow);
                    }

                    col.appendChild(listWrapper);
                    container.appendChild(col);
                })(groupKeys[k]);
            }
        },

        saveGroupsState: function () {
            var oldGroupsRaw = localStorage.getItem('tourma_group_assignments_' + this.tournamentId);
            var oldTotalCount = 0;
            if (oldGroupsRaw) {
                try {
                    var oldG = JSON.parse(oldGroupsRaw);
                    Object.keys(oldG).forEach(function(k) { if (Array.isArray(oldG[k])) oldTotalCount += oldG[k].length; });
                } catch(e) {}
            }

            var newTotalCount = 0;
            var self = this;
            Object.keys(this.groups).forEach(function(k) {
                if (Array.isArray(self.groups[k])) newTotalCount += self.groups[k].length;
            });

            localStorage.setItem('tourma_group_assignments_' + this.tournamentId, JSON.stringify(this.groups));

            // Reset match schedule whenever team count or assignments change
            localStorage.removeItem('tourma_group_matches_' + this.tournamentId);
            localStorage.removeItem('tourma_matches_' + this.tournamentId);
        },

        saveAndReturn: function () {
            var groupKeys = Object.keys(this.groups);
            if (groupKeys.length < 2) {
                alert('⚠️ Giải đấu Vòng Bảng bắt buộc phải có tối thiểu 2 BẢNG ĐẤU!\n\nVui lòng bấm "+ Thêm Bảng Mới" hoặc "Chia Đều Đội Về Bảng" để tạo tối thiểu 2 bảng (Bảng A, Bảng B...) trước khi thi đấu.');
                return;
            }

            var unassigned = this.getUnassignedTeams();
            if (unassigned.length > 0) {
                alert('⚠️ Vẫn còn ' + unassigned.length + ' đội chưa được xếp vào bảng nào!\n\nVui lòng di chuyển hoặc chọn tất cả các đội vào các bảng đấu trước khi thi đấu.');
                return;
            }

            for (var k = 0; k < groupKeys.length; k++) {
                var gKey = groupKeys[k];
                var tCount = (this.groups[gKey]) ? this.groups[gKey].length : 0;
                if (tCount < 2) {
                    alert('⚠️ Mỗi bảng đấu bắt buộc phải có tối thiểu 2 đội bóng! (Bảng ' + gKey + ' hiện chỉ có ' + tCount + ' đội).\n\nVui lòng bấm "+ Thêm Đội" để bổ sung đội vào Bảng ' + gKey + ' hoặc xóa bớt bảng đấu thừa!');
                    return;
                }
            }

            this.saveGroupsState();
            var urlParams = new URLSearchParams(window.location.search);
            var seriesId = urlParams.get('seriesId');
            var target = 'group-stage.jsp?id=' + encodeURIComponent(this.tournamentId) + '&format=GROUP_STAGE';
            if (seriesId) {
                target += '&seriesId=' + encodeURIComponent(seriesId);
            }
            window.location.href = target;
        }
    };

    document.addEventListener('DOMContentLoaded', function () {
        if (document.getElementById('mgWorkspace')) {
            window.TourmaManageGroup.init();
        }
    });
})();
