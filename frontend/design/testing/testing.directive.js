import templateUrl from './testing.template.html';
import './testing.app';
import TestingCtrl from './testing.controller';
angular.module('breadboard.testing').directive('testing', function(){
  return {
    restrict: 'E',
    scope: {
      nodes: '=',
      experimentId: '=',
      experimentInstanceId: '='
    },
    controller: TestingCtrl,
    templateUrl: templateUrl
  }
});