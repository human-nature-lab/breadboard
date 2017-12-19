import clientTemplateUrl from '../templates/client.html';

angular.module('client', ['client.services', 'ngSanitize', 'ngRoute', 'ui.bootstrap', 'client.filters', 'client.directives','angular-bind-html-compile']);

angular.module('client').directive('app', function(){
	return {
		restrict: 'E',
		replace: true,
		templateUrl: clientTemplateUrl
	}
});