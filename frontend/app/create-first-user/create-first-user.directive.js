import './create-first-user.app';
import templateUrl from './create-first-user.template.html';
import CreateFirstUserCtrl from './create-first-user.controller';
angular.module('breadboard.create-first-user').directive('createFirstUser', function(){
  return {
    restrict: 'E',
    scope: {
    },
    controller: CreateFirstUserCtrl,
    templateUrl: templateUrl
  }
});
