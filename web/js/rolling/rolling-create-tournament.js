/**
 * TOURMA - ROLLING WINDOW SERIES STEP 1: CREATE SUB-TOURNAMENT SCRIPT
 * Form validation and dynamic series info update
 */

(function () {
  'use strict';

  window.onSeriesSelectChange = function (selectEl) {
    var selectedOption = selectEl.options[selectEl.selectedIndex];
    var seriesId = selectEl.value;
    if (seriesId) {
      window.location.href = '?seriesId=' + seriesId;
    }
  };

})();
