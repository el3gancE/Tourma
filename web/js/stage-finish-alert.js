/**
 * ============================================================================
 * TOURMA - STAGE FINISH ALERT COMPONENT ENGINE (stage-finish-alert.js)
 * Automatically blocks access to Stage 2 when Stage 1 is not yet completed
 * or completed but not yet confirmed in stage-end-popup.
 * ============================================================================
 */

(function (window) {
    'use strict';

    var StageFinishAlert = {
        /**
         * Check if Stage 2 access is locked and render alert if not ready
         * @param {string} tournamentId
         * @param {number|string} currentStage
         * @param {string|HTMLElement} targetContainer
         * @returns {boolean} true if alert is shown (Stage 2 locked & viewport must be hidden), false if unlocked
         */
        checkAndRender: function (tournamentId, currentStage, targetContainer) {
            var urlParams = new URLSearchParams(window.location.search);
            var stageParam = urlParams.get('stage');
            var stageNum = (currentStage === 2 || currentStage === '2' || stageParam === '2' || stageParam === 2) ? 2 : 1;

            if (stageNum !== 2) return false;
            if (!tournamentId) return false;

            // 1. Check if Stage 1 has been confirmed and locked
            var isLocked = false;
            try {
                isLocked = (localStorage.getItem('tourma_stage1_locked_' + tournamentId) === 'true');
            } catch (e) {
                isLocked = false;
            }

            // If Stage 1 is already confirmed and locked, Stage 2 is unlocked!
            if (isLocked) {
                var wrapper = document.getElementById('stageFinishAlertContainer');
                if (wrapper) wrapper.style.display = 'none';
                return false;
            }

            // 2. Stage 1 is NOT yet confirmed/locked -> MUST SHOW ALERT AND HIDE VIEWPORT!
            // Detect Stage 1 format to create the return link
            var s1Format = 'GROUP_STAGE';
            try {
                var multiCfgRaw = localStorage.getItem('tourma_multi_config_' + tournamentId);
                if (multiCfgRaw) {
                    var mCfg = JSON.parse(multiCfgRaw);
                    if (mCfg && mCfg.stage1Format) s1Format = mCfg.stage1Format;
                } else if (localStorage.getItem('tourma_group_assignments_' + tournamentId) || localStorage.getItem('tourma_group_matches_' + tournamentId)) {
                    s1Format = 'GROUP_STAGE';
                } else if (localStorage.getItem('tourma_swiss_matches_' + tournamentId)) {
                    s1Format = 'SWISS';
                } else if (localStorage.getItem('tourma_rr_matches_' + tournamentId)) {
                    s1Format = 'ROUND_ROBIN';
                } else {
                    s1Format = 'SINGLE_ELIMINATION';
                }
            } catch (e) {
                s1Format = 'GROUP_STAGE';
            }

            var title = 'Bạn chưa hoàn thành vòng 1';
            var desc = 'Vui lòng hoàn thành và xác nhận kết quả vòng 1 để tiếp tục.';

            // Resolve Stage 1 return link
            var isCommonPath = (window.location.pathname.indexOf('/common/') !== -1);
            var basePrefix = isCommonPath ? '' : 'common/';
            var page = 'group-stage.jsp';
            if (s1Format === 'SINGLE_ELIMINATION') page = 'single-elimination.jsp';
            else if (s1Format === 'DOUBLE_ELIMINATION') page = 'double-elimination.jsp';
            else if (s1Format === 'ROUND_ROBIN') page = 'round-robin.jsp';
            else if (s1Format === 'SWISS' || s1Format === 'SWISS_LITE') page = 'swiss-stage.jsp';

            var targetHref = basePrefix + page + '?id=' + encodeURIComponent(tournamentId) + '&stage=1';

            var targetNode = (typeof targetContainer === 'string') ? document.getElementById(targetContainer) : targetContainer;
            var wrapperElem = document.getElementById('stageFinishAlertContainer');

            var alertHtml = 
                '<div class="stage-finish-alert-wrapper" style="display: flex; width: 100%; justify-content: center; margin: 2rem 0;">' +
                    '<div class="stage-finish-alert-card">' +
                        '<div class="stage-finish-alert-icon-box">' +
                            '<i class="fa-solid fa-lock stage-finish-alert-icon"></i>' +
                        '</div>' +
                        '<h3 class="stage-finish-alert-title">' + title + '</h3>' +
                        '<p class="stage-finish-alert-desc">' + desc + '</p>' +
                        '<a href="' + targetHref + '" class="btn-stage-finish-return">' +
                            '<i class="fa-solid fa-arrow-left"></i> Quay Lại Vòng 1' +
                        '</a>' +
                    '</div>' +
                '</div>';

            if (targetNode) {
                targetNode.innerHTML = alertHtml;
                targetNode.style.display = 'flex';
            } else if (wrapperElem) {
                wrapperElem.innerHTML = 
                    '<div class="stage-finish-alert-card">' +
                        '<div class="stage-finish-alert-icon-box">' +
                            '<i class="fa-solid fa-lock stage-finish-alert-icon"></i>' +
                        '</div>' +
                        '<h3 class="stage-finish-alert-title">' + title + '</h3>' +
                        '<p class="stage-finish-alert-desc">' + desc + '</p>' +
                        '<a href="' + targetHref + '" class="btn-stage-finish-return">' +
                            '<i class="fa-solid fa-arrow-left"></i> Quay Lại Vòng 1' +
                        '</a>' +
                    '</div>';
                wrapperElem.style.display = 'flex';
            } else {
                var mainEl = document.querySelector('main.container');
                if (mainEl) {
                    var div = document.createElement('div');
                    div.id = 'stageFinishAlertContainer';
                    div.className = 'stage-finish-alert-wrapper';
                    div.style.display = 'flex';
                    div.innerHTML = 
                        '<div class="stage-finish-alert-card">' +
                            '<div class="stage-finish-alert-icon-box">' +
                                '<i class="fa-solid fa-lock stage-finish-alert-icon"></i>' +
                            '</div>' +
                            '<h3 class="stage-finish-alert-title">' + title + '</h3>' +
                            '<p class="stage-finish-alert-desc">' + desc + '</p>' +
                            '<a href="' + targetHref + '" class="btn-stage-finish-return">' +
                                '<i class="fa-solid fa-arrow-left"></i> Quay Lại Vòng 1' +
                            '</a>' +
                        '</div>';
                    mainEl.insertBefore(div, mainEl.firstChild);
                }
            }

            // HIDE ALL VIEWPORTS & MATCH WORKSPACES
            var dualWs = document.getElementById('deDualViewportWorkspace');
            if (dualWs) dualWs.style.display = 'none';
            var listV = document.getElementById('deListViewContainer');
            if (listV) listV.style.display = 'none';
            var singleVp = document.getElementById('bracketViewportFrame');
            if (singleVp) singleVp.style.display = 'none';
            var singleList = document.getElementById('singleListViewContainer');
            if (singleList) singleList.style.display = 'none';
            var swissMain = document.getElementById('swissMainContentWrapper');
            if (swissMain) swissMain.style.display = 'none';
            var swissAlert = document.getElementById('swissInvalidTeamAlert');
            if (swissAlert) swissAlert.style.display = 'none';
            var rrFixt = document.getElementById('rrFixturesContainer');
            if (rrFixt) rrFixt.style.display = 'none';
            var rrTabs = document.getElementById('rrRoundSelectorTabs');
            if (rrTabs) rrTabs.style.display = 'none';
            var emptyAlert = document.getElementById('emptyTeamAlertContainer');
            if (emptyAlert) emptyAlert.style.display = 'none';

            return true; // Alert shown -> viewport hidden
        }
    };

    window.StageFinishAlert = StageFinishAlert;

})(window);
