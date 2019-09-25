import './steps.app';
import templateUrl from './step.template.html';
import StepCtrl from './step.controller';

angular.module('breadboard.steps').directive('step', function(){
  return {
    restrict: 'E',
    replace: true,
    scope: {
      step: '=',
      selectedStep:'=',
      selectStep:'&',
      deleteStep:'&'
    },
    controllerAs: 'vm',
    controller: StepCtrl,
    templateUrl: templateUrl
  }
});


