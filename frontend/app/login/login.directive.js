import templateUrl from './login.template.html';
import './login.controller';
angular.module('breadboard.login').directive('login', function(){
  return {
    restrict: 'E',
    scope: {
      path: '=',
      onSuccess: '&',
      onError: '&',
    },
    controller: 'LoginCtrl',
    templateUrl: templateUrl
  }
});