/**
 * TOURMA - REUSABLE BRACKET VIEWPORT CANVAS ENGINE (bracket-viewport.js)
 * Features: Mouse Drag-to-Pan (Figma/Battlefy style) & Smooth Zoom Engine.
 */

(function () {
    'use strict';

    window.TourmaViewport = {
        currentScale: 1.0,
        minScale: 0.6,
        maxScale: 1.4,

        /**
         * Initialize Drag-to-Pan & Zoom Engine on container & canvas
         */
        init: function (containerId, canvasId) {
            var container = document.getElementById(containerId || 'bracketViewportContainer');
            var canvas = document.getElementById(canvasId || 'bracketViewportCanvas');

            if (!container || !canvas) return;

            var isMouseDown = false;
            var startX = 0;
            var startY = 0;
            var scrollLeft = 0;
            var scrollTop = 0;

            // MOUSE DOWN: Start Drag-to-Pan
            container.addEventListener('mousedown', function (e) {
                // Ignore drag if clicking interactive buttons or modal backdrops
                if (e.target.closest('.bracket-node-card') || e.target.closest('.btn-zoom') || e.target.closest('button')) {
                    return;
                }

                isMouseDown = true;
                container.classList.add('grabbing');

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

                var walkX = (x - startX) * 1.25; // Speed multiplier
                var walkY = (y - startY) * 1.25;

                container.scrollLeft = scrollLeft - walkX;
                container.scrollTop = scrollTop - walkY;
            });

            // MOUSE UP & LEAVE: Stop Dragging
            container.addEventListener('mouseup', function () {
                isMouseDown = false;
                container.classList.remove('grabbing');
            });

            container.addEventListener('mouseleave', function () {
                isMouseDown = false;
                container.classList.remove('grabbing');
            });

            // TOUCH EVENTS FOR MOBILE / TABLET PANNING
            var touchStartX = 0;
            var touchStartY = 0;

            container.addEventListener('touchstart', function (e) {
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
            var zoomToolbar = document.querySelector('.bracket-zoom-toolbar');
            if (zoomToolbar) {
                container.addEventListener('scroll', function () {
                    var sTop = container.scrollTop;
                    var fadeThreshold = 75; // Complete fade out threshold in pixels

                    if (sTop <= 4) {
                        zoomToolbar.style.opacity = '1';
                        zoomToolbar.style.pointerEvents = 'auto';
                        zoomToolbar.style.transform = 'translateY(0)';
                    } else if (sTop >= fadeThreshold) {
                        zoomToolbar.style.opacity = '0';
                        zoomToolbar.style.pointerEvents = 'none';
                        zoomToolbar.style.transform = 'translateY(-8px)';
                    } else {
                        var progress = sTop / fadeThreshold;
                        zoomToolbar.style.opacity = (1 - progress).toFixed(2);
                        zoomToolbar.style.pointerEvents = (progress > 0.6) ? 'none' : 'auto';
                        zoomToolbar.style.transform = 'translateY(' + (-8 * progress).toFixed(1) + 'px)';
                    }
                }, { passive: true });
            }
        },

        /**
         * Zoom Engine: Scale canvas
         */
        setZoom: function (scale) {
            var canvas = document.getElementById('bracketViewportCanvas');
            var badge = document.getElementById('zoomLevelBadge');

            this.currentScale = Math.max(this.minScale, Math.min(this.maxScale, scale));
            
            if (canvas) {
                canvas.style.transform = 'scale(' + this.currentScale + ')';
            }

            if (badge) {
                badge.innerText = Math.round(this.currentScale * 100) + '%';
            }
        },

        zoomIn: function () {
            this.setZoom(this.currentScale + 0.1);
        },

        zoomOut: function () {
            this.setZoom(this.currentScale - 0.1);
        },

        resetZoom: function () {
            this.setZoom(1.0);
        }
    };

    // Auto-init on DOMContentLoaded
    document.addEventListener('DOMContentLoaded', function () {
        window.TourmaViewport.init('bracketViewportContainer', 'bracketViewportCanvas');
    });

})();
