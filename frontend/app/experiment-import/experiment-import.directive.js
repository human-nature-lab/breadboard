import angular from 'angular';
import './experiment-import.app';
import experimentImportTemplate from './experiment-import.template.html';
import ExperimentImportController from './experiment-import.controller';

angular.module('breadboard.experiment-import')
  .directive("experimentImport", function(){
    return {
      restrict: 'E',
      scope: {},
      replace: true,
      templateUrl: experimentImportTemplate,
      controller: ExperimentImportController
    };
});