import './parameters.app';
import templateUrl from './parameters.template.html';
import ParametersCtrl from './parameters.controller';
angular.module('breadboard.parameters').directive('parameters', function() {
  return {
    restrict: 'E',
    scope: {
      experiment: '=',
      readOnly: '='
    },
    controllerAs: 'vm',
    controller: ParametersCtrl,
    templateUrl: templateUrl
  }
});