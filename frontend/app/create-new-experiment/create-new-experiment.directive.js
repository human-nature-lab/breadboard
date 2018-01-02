import './create-new-experiment.app';
import CreateNewExperimentCtrl from './create-new-experiment.controller';
import tabStatusTemplate from './create-new-experiment.template.html';

angular.module('breadboard.create-new-experiment')
  .directive('createNewExperiment', function(){
    return {
      restrict: 'E',
      scope: {
        userExperiments: '='
      },
      controller: CreateNewExperimentCtrl,
      controllerAs: 'vm',
      templateUrl: tabStatusTemplate
    }
  });
