'use strict';

/* Filters */

angular.module('client.filters', [])
    .filter('to_trusted', ['$sce', function($sce) {
        return function(text) {
            return $sce.trustAsHtml(text);
        };
    }]);
