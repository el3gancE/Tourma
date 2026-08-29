/**
 * TOURMA - EMPTY TEAM ALERT COMPONENT ENGINE (empty-team-alert.js)
 * Automatically checks team count and renders the empty-team-alert component for SE, DE, RR screens.
 */

(function () {
    'use strict';

    window.TourmaEmptyTeamAlert = {
        /**
         * Check if tournament has teams and render alert if empty
         * @param {string} tournamentId
         * @param {Array} teamsList
         * @param {string|HTMLElement} targetContainer
         * @returns {boolean} true if empty alert rendered, false if teams exist
         */
        checkAndRender: function (tournamentId, teamsList, targetContainer) {
            var wrapper = document.getElementById('emptyTeamAlertContainer');
            var targetNode = (typeof targetContainer === 'string') ? document.getElementById(targetContainer) : targetContainer;

            // Determine if tournament has teams
            var hasTeams = (teamsList && Array.isArray(teamsList) && teamsList.length > 0);

            if (!hasTeams) {
                var isCommonPath = window.location.pathname.indexOf('/common/') !== -1;
                var baseUrl = isCommonPath ? 'configure-tournament-teams.jsp' : 'common/configure-tournament-teams.jsp';
                var targetHref = baseUrl + '?id=' + encodeURIComponent(tournamentId || '');

                if (targetNode) {
                    targetNode.innerHTML = '';
                    if (wrapper) {
                        var clone = wrapper.cloneNode(true);
                        clone.id = '';
                        clone.style.display = 'flex';
                        var cloneBtn = clone.querySelector('.btn-empty-team-add') || clone.querySelector('#emptyTeamAlertAddBtn');
                        if (cloneBtn) {
                            cloneBtn.href = targetHref;
                        }
                        targetNode.appendChild(clone);
                    } else {
                        // Fallback HTML if JSP template wrapper not present
                        targetNode.innerHTML = 
                            '<div class="empty-team-alert-wrapper" style="display: flex;">' +
                                '<div class="empty-team-alert-card">' +
                                    '<div class="empty-team-alert-icon-box">' +
                                        '<i class="fa-solid fa-users-slash empty-team-alert-icon"></i>' +
                                    '</div>' +
                                    '<h3 class="empty-team-alert-title">Chưa có đội bóng nào trong giải đấu</h3>' +
                                    '<p class="empty-team-alert-desc">Giải đấu hiện tại chưa có thông tin đội tham gia. Vui lòng thêm danh sách các đội bóng để hệ thống tự động sinh sơ đồ nhánh đấu và lịch thi đấu.</p>' +
                                    '<a href="' + targetHref + '" class="btn-empty-team-add">' +
                                        '<i class="fa-solid fa-plus"></i> Thêm Đội Bóng Ngay' +
                                    '</a>' +
                                '</div>' +
                            '</div>';
                    }
                } else if (wrapper) {
                    wrapper.style.display = 'flex';
                }
                return true; // Alert shown
            } else {
                if (wrapper) {
                    wrapper.style.display = 'none';
                }
                return false; // Teams present
            }
        }
    };
})();
