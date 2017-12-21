'use strict';

/* Filters */

angular.module('breadboard.filters', [])
    .filter('to_trusted', ['$sce', function($sce) {
        return function(text) {
            return $sce.trustAsHtml(text);
        };
    }])
    .filter('asNumber', asNumber);

function asNumber() {
  return function(input) {
    return Number(input);
  };
}
