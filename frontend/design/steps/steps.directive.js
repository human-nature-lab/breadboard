import './steps.app';
import './step.directive';
import './step-name.directive';
import '../directives/tab-status/tab-status.directive';
import templateUrl from './steps.template.html';
import StepsCtrl from './steps.controller';
angular.module('breadboard.steps').directive('steps', function(){
  return {
    restrict: 'E',
    scope: {
      experimentId: '=',
      experiment: '=',
      actions: '=',
      readOnly: '='
    },
    link: function(scope){
      angular.extend(scope.actions, {
        saveSteps: function(){
          scope.saveSteps();
        }
      });
    },
    controllerAs: 'vm',
    controller: StepsCtrl,
    templateUrl: templateUrl
  }
});
