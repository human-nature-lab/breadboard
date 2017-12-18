// Declare app level module which depends on filters, and services
angular.module('breadboard', ['breadboard.filters', 'breadboard.services', 'breadboard.directives', 'breadboard.controllers', 'ui.utils', 'ui.codemirror', 'ui.tinymce', 'ngSanitize']);

angular.module('breadboard').config(function($logProvider){
  $logProvider.debugEnabled(true);
});