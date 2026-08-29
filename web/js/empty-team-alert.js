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
            var addBtn = document.getElementById('emptyTeamAlertAddBtn');
            var targetNode = (typeof targetContainer === 'string') ? document.getElementById(targetContainer) : targetContainer;

            // Determine if tournament has teams
            var hasTeams = (teamsList && Array.isArray(teamsList) && teamsList.length > 0);

            if (!hasTeams) {
                // If button exists, update link to configure-tournament-teams.jsp?id=...
                if (addBtn && tournamentId) {
                    var isCommonPath = window.location.pathname.indexOf('/common/') !== -1;
                    var baseUrl = isCommonPath ? 'configure-tournament-teams.jsp' : 'common/configure-tournament-teams.jsp';
                    addBtn.href = baseUrl + '?id=' + encodeURIComponent(tournamentId);
                }

                // If target container specified and wrapper exists, move wrapper inside target container or toggle visibility
                if (wrapper && targetNode) {
                    targetNode.innerHTML = '';
                    targetNode.appendChild(wrapper);
                    wrapper.style.display = 'flex';
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
