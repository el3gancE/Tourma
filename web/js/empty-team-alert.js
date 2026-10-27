/**
 * TOURMA - EMPTY TEAM ALERT COMPONENT ENGINE (empty-team-alert.js)
 * Automatically checks team count and renders the empty-team-alert component for SE, DE, RR, GS, GSL, Swiss screens.
 */

(function () {
    'use strict';

    function getTargetTeamsUrl(tournamentId) {
        var urlParams = new URLSearchParams(window.location.search);
        var seriesId = urlParams.get('seriesId') || '';
        if (!seriesId && tournamentId) {
            try {
                seriesId = localStorage.getItem('tourma_series_id_' + tournamentId) || '';
            } catch (e) {}
        }

        var isCommonPath = window.location.pathname.indexOf('/common/') !== -1;
        var contextPath = window.TourmaContextPath || (isCommonPath ? window.location.pathname.substring(0, window.location.pathname.indexOf('/common/')) : '');
        if (!contextPath && window.location.pathname.indexOf('/') !== -1) {
            var parts = window.location.pathname.split('/');
            if (parts.length > 1 && parts[1] && parts[1] !== 'common' && parts[1] !== 'rolling') {
                contextPath = '/' + parts[1];
            }
        }

        if (seriesId && seriesId.trim().length > 0) {
            return (contextPath ? contextPath : '') + '/rolling/tournament-teams?id=' + encodeURIComponent(tournamentId || '') + '&seriesId=' + encodeURIComponent(seriesId.trim());
        }

        var baseUrl = isCommonPath ? 'configure-tournament-teams.jsp' : 'common/configure-tournament-teams.jsp';
        if (contextPath && baseUrl.indexOf(contextPath) === -1) {
            baseUrl = contextPath + '/common/configure-tournament-teams.jsp';
        }
        return baseUrl + '?id=' + encodeURIComponent(tournamentId || '');
    }

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

            // Determine if tournament has teams (requires at least 2 teams for active brackets/fixtures)
            var hasTeams = (teamsList && Array.isArray(teamsList) && teamsList.length >= 2);

            if (!hasTeams) {
                var targetHref = getTargetTeamsUrl(tournamentId);

                if (targetNode) {
                    targetNode.innerHTML = '';
                    targetNode.style.display = 'block';
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
                    var btn = wrapper.querySelector('.btn-empty-team-add') || wrapper.querySelector('#emptyTeamAlertAddBtn');
                    if (btn) btn.href = targetHref;
                    wrapper.style.display = 'flex';
                }
                return true; // Alert shown
            } else {
                if (targetNode) {
                    targetNode.style.display = 'none';
                }
                if (wrapper) {
                    wrapper.style.display = 'none';
                }
                return false; // Teams present
            }
        },

        /**
         * Directly show the empty team alert
         * @param {string} tournamentId
         * @param {string|HTMLElement} targetContainer
         */
        show: function (tournamentId, targetContainer) {
            this.checkAndRender(tournamentId, [], targetContainer);
        },

        /**
         * Hide the empty team alert
         * @param {string|HTMLElement} targetContainer
         */
        hide: function (targetContainer) {
            var targetNode = (typeof targetContainer === 'string') ? document.getElementById(targetContainer) : targetContainer;
            if (targetNode) targetNode.style.display = 'none';
            var wrapper = document.getElementById('emptyTeamAlertContainer');
            if (wrapper) wrapper.style.display = 'none';
        }
    };
})();
