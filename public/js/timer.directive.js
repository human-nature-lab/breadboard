angular.module('breadboard.timer')
.directive('bbTimer', function(){
  return {
    restrict: 'E',
    replace: true,
    template: '<div class="timer-container" ng-if="!isHidden">' +
        '<progressbar value="val" type="{{timer.appearance}}">' +
                    '{{message}} {{direction}}' +
        '</progressbar>' +
    '</div>',
    controller: 'TimerCtrl',
    scope: {
      timer : '='
    }
  }
});