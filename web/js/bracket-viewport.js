/**
 * TOURMA - UNIFIED REUSABLE BRACKET VIEWPORT & SVG CONNECTOR ENGINE (bracket-viewport.js)
 * Features:
 * - Multi-Instance Drag-to-Pan (Figma/Battlefy smooth panning)
 * - Smooth Zoom Engine with Toolbar Fade-Out
 * - Universal Orthogonal SVG Connectors Engine (M x1 y1 H midX V y2 H x2)
 */

(function () {
    'use strict';

    window.TourmaViewport = {
        instances: {},
        currentScale: 1.0,
        minScale: 0.6,
        maxScale: 1.4,

        /**
         * Initialize Drag-to-Pan & Zoom Engine on a container & canvas instance
         */
        init: function (containerId, canvasId, options) {
            var cId = containerId || 'bracketViewportContainer';
            var cvId = canvasId || 'bracketViewportCanvas';

            var container = document.getElementById(cId);
            var canvas = document.getElementById(cvId);
            if (!container || !canvas) return null;

            var self = this;
            var instance = {
                containerId: cId,
                canvasId: cvId,
                container: container,
                canvas: canvas,
                scale: 1.0,
                minScale: (options && options.minScale) || 0.5,
                maxScale: (options && options.maxScale) || 1.6,
                badgeElem: (options && options.badgeId) ? document.getElementById(options.badgeId) : container.querySelector('.zoom-level-badge, .de-zoom-badge'),
                toolbarElem: (options && options.toolbarId) ? document.getElementById(options.toolbarId) : container.querySelector('.bracket-zoom-toolbar, .de-zoom-toolbar'),
                onRedraw: (options && options.onRedraw) || null
            };

            this.instances[cId] = instance;

            var isMouseDown = false;
            var startX = 0, startY = 0;
            var scrollLeft = 0, scrollTop = 0;

            // MOUSE DOWN: Start Drag-to-Pan
            container.addEventListener('mousedown', function (e) {
                if (e.target.closest('.bracket-node-card, .btn-zoom, .de-zoom-btn, .round-header-random-controls, .list-round-actions, .round-random-input, .btn-round-random, .btn-round-reset, .btn-random-round, .btn-reset-round, button, input, select, textarea, a')) {
                    return;
                }
                isMouseDown = true;
                container.style.cursor = 'grabbing';
                startX = e.pageX - container.offsetLeft;
                startY = e.pageY - container.offsetTop;
                scrollLeft = container.scrollLeft;
                scrollTop = container.scrollTop;
            });

            // MOUSE MOVE: Pan Viewport Canvas
            container.addEventListener('mousemove', function (e) {
                if (!isMouseDown) return;
                e.preventDefault();
                var x = e.pageX - container.offsetLeft;
                var y = e.pageY - container.offsetTop;
                container.scrollLeft = scrollLeft - (x - startX);
                container.scrollTop = scrollTop - (y - startY);
            });

            // MOUSE UP & LEAVE: Stop Dragging
            window.addEventListener('mouseup', function () {
                isMouseDown = false;
                if (container) container.style.cursor = 'grab';
            });

            // TOUCH PANNING
            var touchStartX = 0, touchStartY = 0;
            container.addEventListener('touchstart', function (e) {
                if (e.target.closest('.bracket-node-card, .btn-zoom, .de-zoom-btn, .round-header-random-controls, .list-round-actions, .round-random-input, .btn-round-random, .btn-round-reset, .btn-random-round, .btn-reset-round, button, input, select, textarea, a')) {
                    return;
                }
                if (e.touches.length === 1) {
                    touchStartX = e.touches[0].pageX - container.offsetLeft;
                    touchStartY = e.touches[0].pageY - container.offsetTop;
                    scrollLeft = container.scrollLeft;
                    scrollTop = container.scrollTop;
                }
            }, { passive: true });

            container.addEventListener('touchmove', function (e) {
                if (e.touches.length === 1) {
                    var touchX = e.touches[0].pageX - container.offsetLeft;
                    var touchY = e.touches[0].pageY - container.offsetTop;
                    container.scrollLeft = scrollLeft - (touchX - touchStartX);
                    container.scrollTop = scrollTop - (touchY - touchStartY);
                }
            }, { passive: true });

            // SCROLL EVENT: Smoothly fade out zoom toolbar when scrolling down vertically
            var toolbar = instance.toolbarElem;
            if (toolbar) {
                container.addEventListener('scroll', function () {
                    var sTop = container.scrollTop;
                    var threshold = 75;
                    if (sTop <= 4) {
                        toolbar.style.opacity = '1';
                        toolbar.style.pointerEvents = 'auto';
                        toolbar.style.transform = 'translateY(0)';
                    } else if (sTop >= threshold) {
                        toolbar.style.opacity = '0';
                        toolbar.style.pointerEvents = 'none';
                        toolbar.style.transform = 'translateY(-8px)';
                    } else {
                        var p = sTop / threshold;
                        toolbar.style.opacity = (1 - p).toFixed(2);
                        toolbar.style.pointerEvents = (p > 0.6) ? 'none' : 'auto';
                        toolbar.style.transform = 'translateY(' + (-8 * p).toFixed(1) + 'px)';
                    }
                }, { passive: true });
            }

            // Apply initial normalized default zoom (100% display = 0.80 physical scale)
            this.setZoomOnInstance(cId, 1.0);

            return instance;
        },

        /**
         * Get element position relative to canvas container (independent of scroll/zoom)
         */
        getRelativePos: function (elem, root) {
            if (!elem) return { left: 0, top: 0, width: 0, height: 0, right: 0, bottom: 0 };
            root = root || document.getElementById('bracketViewportCanvas');

            var x = 0, y = 0;
            var curr = elem;
            while (curr && curr !== root && curr !== document.body) {
                x += curr.offsetLeft;
                y += curr.offsetTop;
                curr = curr.offsetParent;
            }

            var w = elem.offsetWidth || 200;
            var h = elem.offsetHeight || 70;

            return {
                left: x,
                top: y,
                width: w,
                height: h,
                right: x + w,
                bottom: y + h
            };
        },

        /**
         * Set Zoom on Instance
         */
        /**
         * Set Zoom on Instance (Normalizes: 100% scale = 0.80 visual scale)
         */
        setZoomOnInstance: function (instanceId, scale) {
            var inst = this.instances[instanceId];
            if (!inst) return;

            inst.scale = Math.max(inst.minScale, Math.min(inst.maxScale, scale));
            var visualScale = Number((inst.scale * 0.8).toFixed(3));

            if (inst.canvas) {
                if ('zoom' in inst.canvas.style) {
                    inst.canvas.style.zoom = visualScale;
                } else {
                    inst.canvas.style.transform = 'scale(' + visualScale + ')';
                    inst.canvas.style.transformOrigin = '0 0';
                }
            }
            if (inst.badgeElem) {
                inst.badgeElem.innerText = Math.round(inst.scale * 100) + '%';
            }

            if (typeof inst.onRedraw === 'function') {
                inst.onRedraw(inst.scale);
            }
        },

        // Legacy compatibility shortcuts for Single Viewport
        setZoom: function (scale) {
            this.currentScale = Math.max(this.minScale, Math.min(this.maxScale, scale));
            var visualScale = Number((this.currentScale * 0.8).toFixed(3));

            var canvas = document.getElementById('bracketViewportCanvas');
            var badge = document.getElementById('zoomLevelBadge');
            if (canvas) {
                if ('zoom' in canvas.style) {
                    canvas.style.zoom = visualScale;
                } else {
                    canvas.style.transform = 'scale(' + visualScale + ')';
                    canvas.style.transformOrigin = '0 0';
                }
            }
            if (badge) badge.innerText = Math.round(this.currentScale * 100) + '%';
        },
        // Zoom Control Methods (Supports both instanceId and default single viewport)
        zoomIn: function (instanceId) {
            if (instanceId && this.instances[instanceId]) {
                var current = this.instances[instanceId].scale || 1.0;
                this.setZoomOnInstance(instanceId, Number((current + 0.1).toFixed(2)));
            } else {
                this.setZoom(Number((this.currentScale + 0.1).toFixed(2)));
            }
        },
        zoomOut: function (instanceId) {
            if (instanceId && this.instances[instanceId]) {
                var current = this.instances[instanceId].scale || 1.0;
                this.setZoomOnInstance(instanceId, Number((current - 0.1).toFixed(2)));
            } else {
                this.setZoom(Number((this.currentScale - 0.1).toFixed(2)));
            }
        },
        resetZoom: function (instanceId) {
            if (instanceId && this.instances[instanceId]) {
                var inst = this.instances[instanceId];
                if (inst.container) {
                    inst.container.scrollLeft = 0;
                    inst.container.scrollTop = 0;
                }
                this.setZoomOnInstance(instanceId, 1.0);
            } else {
                var container = document.getElementById('bracketViewportContainer');
                if (container) {
                    container.scrollLeft = 0;
                    container.scrollTop = 0;
                }
                this.setZoom(1.0);
            }
        },

        /**
         * UNIVERSAL ORTHOGONAL SVG CONNECTOR DRAWING ENGINE
         * Iterates each target match card in the viewport and draws clean bracket forks ( ]-- )
         * strictly between its immediate parent matches, preventing continuous vertical cross-bleeding.
         */
        drawConnectors: function (canvasElem, wrapperElem, matchesMap, scale) {
            if (!canvasElem || !wrapperElem || !matchesMap) return;

            var svg = canvasElem.querySelector('.bracket-svg-connectors');
            if (!svg) {
                svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
                svg.setAttribute('class', 'bracket-svg-connectors');
                svg.setAttribute('style', 'position: absolute; top: 0; left: 0; pointer-events: none; z-index: 1; overflow: visible;');
                canvasElem.insertBefore(svg, canvasElem.firstChild);
            }

            var w = Math.max(wrapperElem.offsetWidth || 0, canvasElem.offsetWidth || 0, 1000);
            var h = Math.max(wrapperElem.offsetHeight || 0, canvasElem.offsetHeight || 0, 600);
            svg.setAttribute('width', w);
            svg.setAttribute('height', h);
            svg.setAttribute('viewBox', '0 0 ' + w + ' ' + h);
            svg.style.width = w + 'px';
            svg.style.height = h + 'px';
            svg.innerHTML = '';

            var cards = wrapperElem.querySelectorAll('.bracket-node-card');
            if (!cards || cards.length === 0) return;

            // 1. Build an exact map of DOM cards in the current wrapper
            var cardMap = {};
            var cardList = [];
            for (var c = 0; c < cards.length; c++) {
                var card = cards[c];
                var mId = card.getAttribute('data-match-id') || (card.dataset ? card.dataset.matchId : null);
                if (mId) {
                    var sMid = String(mId).trim();
                    cardMap[sMid] = card;
                    cardList.push({ id: sMid, card: card });
                }
            }

            // Flatten unique matches from matchesMap
            var allMatches = [];
            var seenMatches = [];
            var keys = Object.keys(matchesMap);
            for (var i = 0; i < keys.length; i++) {
                var mat = matchesMap[keys[i]];
                if (mat && seenMatches.indexOf(mat) === -1) {
                    seenMatches.push(mat);
                    allMatches.push(mat);
                }
            }

            var self = this;

            // Helper to find a match object by card ID or numeric ID
            var findMatchForCard = function (cardId) {
                if (!cardId) return null;
                var strId = String(cardId).trim();
                if (matchesMap[strId]) return matchesMap[strId];

                for (var j = 0; j < allMatches.length; j++) {
                    var m = allMatches[j];
                    if (m.matchId && String(m.matchId).trim() === strId) return m;
                    if (m.id && String(m.id).trim() === strId) return m;
                    if (m.rawId && String(m.rawId).trim() === strId) return m;
                    if (m.matchNumber !== undefined && m.matchNumber !== null && String(m.matchNumber).trim() === strId) return m;
                    if (m.matchCode && String(m.matchCode).trim() === strId) return m;
                }

                // Suffix / Prefix fallback
                for (var k = 0; k < allMatches.length; k++) {
                    var m2 = allMatches[k];
                    var m2Id = String(m2.matchId || m2.id || m2.rawId || '');
                    if (m2Id && (m2Id.endsWith('_' + strId) || strId.endsWith('_' + m2Id))) return m2;
                }
                return null;
            };

            // Helper to find the DOM card corresponding to a source match within the current viewport
            var findCardForMatch = function (matchObj) {
                if (!matchObj) return null;
                var idsToCheck = [
                    matchObj.matchId,
                    matchObj.id,
                    matchObj.rawId,
                    (matchObj.matchNumber !== undefined && matchObj.matchNumber !== null) ? String(matchObj.matchNumber) : null,
                    matchObj.matchCode
                ];
                for (var i = 0; i < idsToCheck.length; i++) {
                    var id = idsToCheck[i];
                    if (id !== undefined && id !== null && id !== '') {
                        var strId = String(id).trim();
                        if (cardMap[strId]) return cardMap[strId];
                    }
                }
                for (var j = 0; j < cardList.length; j++) {
                    var item = cardList[j];
                    for (var k = 0; k < idsToCheck.length; k++) {
                        var checkId = idsToCheck[k];
                        if (checkId !== undefined && checkId !== null && checkId !== '') {
                            var sCheck = String(checkId).trim();
                            if (item.id === sCheck || item.id.endsWith('_' + sCheck) || sCheck.endsWith('_' + item.id)) {
                                return item.card;
                            }
                        }
                    }
                }
                return null;
            };

            // 2. Iterate each target card in the viewport and draw connectors from its immediate feeders
            for (var c = 0; c < cards.length; c++) {
                var targetCard = cards[c];
                if (targetCard.classList.contains('bye-empty-slot')) continue;

                var targetCardId = targetCard.getAttribute('data-match-id') || (targetCard.dataset ? targetCard.dataset.matchId : null);
                if (!targetCardId) continue;

                var targetMatch = findMatchForCard(targetCardId);
                if (!targetMatch) continue;

                var rNum = parseInt(targetMatch.roundNumber || 1, 10);
                var targetBType = (targetMatch.bracketType || '').toUpperCase();
                var isTargetLb = (targetBType.indexOf('LOWER') !== -1 || targetBType.indexOf('LOSER') !== -1 || (canvasElem && canvasElem.id === 'lowerViewportCanvas'));
                var isTargetUb = (targetBType.indexOf('UPPER') !== -1 || targetBType.indexOf('WINNER') !== -1 || targetBType === 'MAIN');
                var isTargetGf = (targetBType.indexOf('GRAND') !== -1 || targetBType === 'GF');

                // Round 1 matches in SE, UB, and LB have no preceding rounds in their bracket
                if (rNum <= 1 && !isTargetGf) continue;

                // Find feeder source matches that advance to targetMatch
                var tIds = [
                    targetMatch.matchId ? String(targetMatch.matchId).trim() : null,
                    targetMatch.id ? String(targetMatch.id).trim() : null,
                    targetMatch.rawId ? String(targetMatch.rawId).trim() : null,
                    (targetMatch.matchNumber !== undefined && targetMatch.matchNumber !== null) ? String(targetMatch.matchNumber).trim() : null
                ];

                var feederMatches = [];
                for (var m = 0; m < allMatches.length; m++) {
                    var src = allMatches[m];
                    if (src === targetMatch) continue;

                    var srcNextId = src.nextMatchId || src.next_match_id;
                    if (!srcNextId || srcNextId === 'null' || srcNextId === 'TBD') continue;
                    var sNextStr = String(srcNextId).trim();

                    var isMatched = false;
                    for (var k = 0; k < tIds.length; k++) {
                        var tId = tIds[k];
                        if (tId && (sNextStr === tId || sNextStr.endsWith('_' + tId) || tId.endsWith('_' + sNextStr))) {
                            isMatched = true;
                            break;
                        }
                    }

                    if (!isMatched) continue;

                    // Bracket branch filter: ensure UB matches feed UB, and LB matches feed LB
                    var srcBType = (src.bracketType || '').toUpperCase();
                    var isSrcLb = (srcBType.indexOf('LOWER') !== -1 || srcBType.indexOf('LOSER') !== -1);
                    var isSrcUb = (srcBType.indexOf('UPPER') !== -1 || srcBType.indexOf('WINNER') !== -1 || srcBType === 'MAIN');

                    if (isTargetUb && !isSrcUb) continue;
                    if (isTargetLb && !isSrcLb) continue;

                    feederMatches.push(src);
                }

                if (feederMatches.length === 0) continue;

                // Map feeder matches to DOM cards inside current viewport
                var validSources = [];
                var seenSrcCards = [];
                for (var f = 0; f < feederMatches.length; f++) {
                    var fMatch = feederMatches[f];
                    var srcCard = findCardForMatch(fMatch);
                    if (srcCard && srcCard !== targetCard && !srcCard.classList.contains('bye-empty-slot')) {
                        if (seenSrcCards.indexOf(srcCard) !== -1) continue;
                        seenSrcCards.push(srcCard);

                        var srcPos = this.getRelativePos(srcCard, canvasElem);
                        validSources.push({
                            match: fMatch,
                            card: srcCard,
                            pos: srcPos,
                            x1: srcPos.right,
                            y1: srcPos.top + (srcPos.height / 2)
                        });
                    }
                }

                if (validSources.length === 0) continue;

                var tgtPos = this.getRelativePos(targetCard, canvasElem);
                var x2 = tgtPos.left;
                var yTargetCenter = tgtPos.top + (tgtPos.height / 2);

                var themeColor = isTargetLb ? '#ff4655' : (isTargetGf ? '#fbbf24' : '#2dd4bf');

                if (validSources.length >= 2) {
                    // Standard Bracket Fork ( ]-- ) strictly for this target match
                    validSources.sort(function (a, b) { return a.y1 - b.y1; });
                    var s1 = validSources[0];
                    var s2 = validSources[validSources.length - 1];
                    var maxX1 = Math.max(s1.x1, s2.x1);
                    var midX = maxX1 + (x2 - maxX1) / 2;

                    var minY = s1.y1;
                    var maxY = s2.y1;

                    var isBothDone = (s1.match.status === 'COMPLETED' || s1.match.status === 'done' || s1.match.status === 'FINISHED' || s1.match.winnerId) &&
                                     (s2.match.status === 'COMPLETED' || s2.match.status === 'done' || s2.match.status === 'FINISHED' || s2.match.winnerId);
                    var strokeColor = isBothDone ? themeColor : 'rgba(255, 255, 255, 0.4)';
                    var strokeWidth = isBothDone ? '2.2' : '1.6';

                    var d = 'M ' + s1.x1.toFixed(1) + ' ' + s1.y1.toFixed(1) + ' H ' + midX.toFixed(1) + ' ' +
                            'M ' + s2.x1.toFixed(1) + ' ' + s2.y1.toFixed(1) + ' H ' + midX.toFixed(1) + ' ' +
                            'M ' + midX.toFixed(1) + ' ' + minY.toFixed(1) + ' V ' + maxY.toFixed(1) + ' ' +
                            'M ' + midX.toFixed(1) + ' ' + yTargetCenter.toFixed(1) + ' H ' + x2.toFixed(1);

                    var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                    path.setAttribute('d', d);
                    path.setAttribute('fill', 'none');
                    path.setAttribute('stroke', strokeColor);
                    path.setAttribute('stroke-width', strokeWidth);
                    path.setAttribute('stroke-linecap', 'round');
                    path.setAttribute('stroke-linejoin', 'round');

                    svg.appendChild(path);
                } else {
                    // Single feeder match line (1-to-1 connector)
                    var s = validSources[0];
                    var isDone = (s.match.status === 'COMPLETED' || s.match.status === 'done' || s.match.status === 'FINISHED' || s.match.winnerId);
                    var strokeColor = isDone ? themeColor : 'rgba(255, 255, 255, 0.4)';
                    var strokeWidth = isDone ? '2.2' : '1.6';
                    var d = '';

                    // If almost horizontal (difference <= 8px), draw a 100% clean straight horizontal line
                    if (Math.abs(s.y1 - yTargetCenter) <= 8) {
                        d = 'M ' + s.x1.toFixed(1) + ' ' + yTargetCenter.toFixed(1) +
                            ' H ' + x2.toFixed(1);
                    } else {
                        var midX = s.x1 + (x2 - s.x1) / 2;
                        d = 'M ' + s.x1.toFixed(1) + ' ' + s.y1.toFixed(1) +
                            ' H ' + midX.toFixed(1) +
                            ' V ' + yTargetCenter.toFixed(1) +
                            ' H ' + x2.toFixed(1);
                    }

                    var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                    path.setAttribute('d', d);
                    path.setAttribute('fill', 'none');
                    path.setAttribute('stroke', strokeColor);
                    path.setAttribute('stroke-width', strokeWidth);
                    path.setAttribute('stroke-linecap', 'round');
                    path.setAttribute('stroke-linejoin', 'round');

                    svg.appendChild(path);
                }
            }
        }
    };

    /**
     * UNIVERSAL TEAM PATH TRACKER (Tournament Journey Highlighter)
     */
    window.TourmaPathTracker = {
        activeTeam: null,

        highlightTeam: function (teamName) {
            if (!teamName || teamName === 'BYE' ||
                teamName.startsWith('W #') || teamName.startsWith('L #') ||
                teamName === 'Winner UB' || teamName === 'Winner LB') {
                this.clearHighlight();
                return;
            }

            this.activeTeam = teamName;
            document.body.classList.add('tourma-team-path-active');

            // Highlight all matching team rows in bracket cards only
            var allRows = document.querySelectorAll('.bracket-team-row');
            for (var r = 0; r < allRows.length; r++) {
                var row = allRows[r];
                var name = row.getAttribute('data-team-name') || '';
                if (name === teamName) {
                    row.classList.add('path-focused');
                } else {
                    row.classList.remove('path-focused');
                }
            }
        },

        clearHighlight: function () {
            if (!this.activeTeam) return;
            this.activeTeam = null;
            document.body.classList.remove('tourma-team-path-active');

            var focusedRows = document.querySelectorAll('.bracket-team-row.path-focused');
            for (var r = 0; r < focusedRows.length; r++) focusedRows[r].classList.remove('path-focused');
        }
    };

    // Auto-init on DOMContentLoaded if default elements exist
    document.addEventListener('DOMContentLoaded', function () {
        if (document.getElementById('bracketViewportContainer') && document.getElementById('bracketViewportCanvas')) {
            window.TourmaViewport.init('bracketViewportContainer', 'bracketViewportCanvas');
        }
    });

})();
