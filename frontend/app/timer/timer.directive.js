import templateUrl from './timer.template.html';
import TimerCtrl from './timer.controller';
angular.module('breadboard.timer', [])
.directive('bbTimer', function(){
  return {
    restrict: 'E',
    replace: true,
    templateUrl: templateUrl,
    controller: TimerCtrl,
    scope: {
      timer : '='
    }
  }
});