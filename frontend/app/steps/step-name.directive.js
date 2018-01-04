import './steps.app';
import templateUrl from './step-name.template.html';
import StepNameCtrl from './step-name.controller';

angular.module('breadboard.steps')
  .directive('stepName', ['$timeout', function($timeout){
    return {
      restrict: 'E',
      replace: true,
      scope: {
        step: '='
      },
      link: function(scope, element) {
        $timeout(function() {
          element[0].focus();
          element[0].select();
        });

        element.bind("keydown keypress", function(event) {
          if(event.which === 13) {
            element[0].blur();
            event.preventDefault();
          }
        });
      },
      controllerAs: 'vm',
      controller: StepNameCtrl,
      templateUrl: templateUrl
    }
  }]);


