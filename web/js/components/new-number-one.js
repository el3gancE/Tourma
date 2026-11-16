/* Reusable inline announcement. Host owns eligibility; there is no modal/storage. */
(function () {
  'use strict';
  var mounted = new WeakSet();
  function resolve(root) { return root || document.querySelector('[data-new-number-one]'); }
  function setVisible(visible, root) {
    var banner = resolve(root);
    if (banner) banner.hidden = !visible;
  }
  function mount(root) {
    (root || document).querySelectorAll('[data-new-number-one]').forEach(function (banner) {
      if (mounted.has(banner)) return;
      mounted.add(banner);
      var selector = banner.getAttribute('data-view-selector');
      var view = selector ? document.querySelector(selector) : null;
      function sync() { setVisible(!view || view.value === 'LATEST', banner); }
      if (view) view.addEventListener('change', sync);
      sync();
    });
  }
  window.TourmaNewNumberOne = {
    mount: mount,
    show: function (root) { setVisible(true, root); },
    hide: function (root) { setVisible(false, root); }
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', function () { mount(); });
  else mount();
})();
