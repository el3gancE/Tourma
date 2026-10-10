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
                // PRIMARY: read from DB-injected value (set by JSP from tournament.stage1_status)
                if (window.TourmaDbStage1Status && window.TourmaDbStage1Status[tournamentId]) {
                    var s = window.TourmaDbStage1Status[tournamentId];
                    if (s === 'LOCKED' || s === 'COMPLETED') {
                        isLocked = true;
                    }
                }
                if (!isLocked) {
                    isLocked = (localStorage.getItem('tourma_stage1_locked_' + tournamentId) === 'true');
                }
            } catch (e) {
                isLocked = false;
            }

            // If Stage 2 has active database matches saved, Stage 2 is unlocked
            if (!isLocked && window.TourmaContextDbMatches && Array.isArray(window.TourmaContextDbMatches) && window.TourmaContextDbMatches.length > 0) {
                isLocked = true;
            }

            // If Stage 1 is confirmed and locked or Stage 2 already active, allow Stage 2 access
            if (isLocked) {
                var wrapper = document.getElementById('stageFinishAlertContainer');
                if (wrapper) wrapper.style.display = 'none';
                return false;
            }

            // 2. Stage 1 is NOT yet confirmed/locked -> MUST SHOW ALERT AND HIDE STAGE 2 WORKSPACES!
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

            s1Format = String(s1Format).toUpperCase();
            var page = 'group-stage.jsp';
            if (s1Format === 'SINGLE_ELIMINATION') page = 'single-elimination.jsp';
            else if (s1Format === 'DOUBLE_ELIMINATION') page = 'double-elimination.jsp';
            else if (s1Format === 'ROUND_ROBIN') page = 'round-robin.jsp';
            else if (s1Format === 'GSL') page = 'gsl.jsp';
            else if (s1Format === 'SWISS' || s1Format === 'SWISS_LITE') page = 'swiss-stage.jsp';

            var seriesId = urlParams.get('seriesId') || '';
            if (!seriesId && tournamentId) {
                try {
                    seriesId = localStorage.getItem('tourma_series_id_' + tournamentId) || '';
                } catch(e) {}
            }

            var isCommonPath = (window.location.pathname.indexOf('/common/') !== -1);
            var contextPath = window.TourmaContextPath || (isCommonPath ? window.location.pathname.substring(0, window.location.pathname.indexOf('/common/')) : '');
            var basePrefix = isCommonPath ? '' : 'common/';
            if (contextPath && basePrefix.indexOf(contextPath) === -1) {
                basePrefix = contextPath + '/common/';
            }
            var targetHref = basePrefix + page + '?id=' + encodeURIComponent(tournamentId) + '&stage=1' + (seriesId ? ('&seriesId=' + encodeURIComponent(seriesId)) : '');

            var title = 'Bạn chưa hoàn thành Vòng 1';
            var desc = 'Vòng 1 của giải đấu chưa được xác nhận hoàn thành. Vui lòng hoàn thành tất cả các trận đấu và bấm xác nhận kết quả ở Vòng 1 để chuyển sang Vòng 2 thi đấu.';

            var targetNode = (typeof targetContainer === 'string') ? document.getElementById(targetContainer) : targetContainer;
            var wrapperElem = document.getElementById('stageFinishAlertContainer');

            var cardContentHtml = 
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

            if (targetNode) {
                targetNode.innerHTML = 
                    '<div class="stage-finish-alert-wrapper" style="display: flex; width: 100%; justify-content: center; margin: 2rem 0;">' +
                        cardContentHtml +
                    '</div>';
                targetNode.style.display = 'block';
            } else if (wrapperElem) {
                wrapperElem.innerHTML = cardContentHtml;
                wrapperElem.style.display = 'flex';
            } else {
                var mainEl = document.querySelector('main.container');
                if (mainEl) {
                    var div = document.createElement('div');
                    div.id = 'stageFinishAlertContainer';
                    div.className = 'stage-finish-alert-wrapper';
                    div.style.display = 'flex';
                    div.innerHTML = cardContentHtml;
                    mainEl.insertBefore(div, mainEl.firstChild);
                }
            }

            // HIDE ALL VIEWPORTS & MATCH WORKSPACES ACROSS ALL FORMATS
            var idsToHide = [
                'bracketViewportFrame',
                'singleListViewContainer',
                'singleEmptyAlertContainer',
                'deDualViewportWorkspace',
                'deListViewContainer',
                'deEmptyAlertContainer',
                'rrRoundSelectorBar',
                'rrRoundSelectorTabs',
                'rrFixturesContainer',
                'rrEmptyAlertContainer',
                'gsMainContent',
                'gsGroupSelectorBar',
                'gsMatchesView',
                'gsStandingsView',
                'gsEmptyAlertContainer',
                'gslGroupsWorkspace',
                'gslListViewContainer',
                'gslEmptyAlertContainer',
                'swissMainContentWrapper',
                'swissInvalidTeamAlert',
                'swissListView',
                'emptyTeamAlertContainer'
            ];

            idsToHide.forEach(function (id) {
                var el = document.getElementById(id);
                if (el) el.style.display = 'none';
            });

            return true; // Alert shown -> viewport hidden
        }
    };

    window.StageFinishAlert = StageFinishAlert;

})(window);
