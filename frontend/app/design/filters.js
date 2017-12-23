/* Filters */

angular.module('breadboard.filters', [])
    .filter('to_trusted', ['$sce', function($sce) {
        return function(text) {
            return $sce.trustAsHtml(text);
        };
    }])
    .filter('asNumber', asNumber)
    .filter('shortId', shortId);

function asNumber() {
  return function(input) {
    return Number(input);
  };
}

function shortId() {
  return function(input) {
    let maxLength = 10;
    if ((typeof input === 'string' || input instanceof String) && input.length > maxLength) {
      return input.substr(0, maxLength) + "...";
    }
    return input;
  };
}
