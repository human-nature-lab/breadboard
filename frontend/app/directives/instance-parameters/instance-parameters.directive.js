import './instance-parameters.app';
import templateUrl from './instance-parameters.template.html';

angular.module('breadboard.instance-parameters')
  .directive('instanceParameters', function() {
    return {
      restrict: 'E',
      scope: {
        instance: '='
      },
      templateUrl: templateUrl
    };
});
