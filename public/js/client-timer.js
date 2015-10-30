function TimersCtrl($scope, $filter, $interval) {
  $scope.timers = {};
  $scope.timerId = undefined;

  $scope.isTimer = function () {
    return ($scope.timers) && (!angular.equals({}, $scope.timers));
  };

  $scope.$watch('client.player.timers', function (newValue) {

    // If a timer is set and there are no timers to update, cancel the timer and delete timers.
    if ($scope.timerId !== undefined && ((newValue) && (!(typeof newValue === "object")) || ((typeof newValue === "object") && Object.keys(newValue).length === 0))) {
      $interval.cancel($scope.timerId);
      $scope.timers = {};
      $scope.timerId = undefined;
    }

    // Initialize new timers
    if (typeof newValue === "object") {
      // Remove deleted timers
      for (var key in $scope.timers) {
        if (! newValue.hasOwnProperty(key)) {
          delete $scope.timers[key];
        }
      }
      // Add/update new timers
      for (var key in newValue) {
        if (newValue.hasOwnProperty(key)) {
          var timer = {};
          var curTimer = newValue[key];
          timer.appearance = curTimer.appearance;
          timer.startTime = curTimer.startTime;
          timer.endTime = curTimer.endTime;
          timer.direction = curTimer.direction;
          timer.timerType = curTimer.timerType;
          timer.baseText = curTimer.timerText;
          var totalTime = curTimer.endTime - curTimer.startTime;
          var time = curTimer.endTime - Date.now();
          var percent = timer.timerValue = (curTimer.direction == "up") ? (Math.round(( ((totalTime - time) / totalTime) * 100))) : (Math.round(( (time / totalTime) * 100)));
          var currencyAmount = parseInt(curTimer.currencyAmount);

          if (curTimer.timerType == "currency") {
            var amount = (curTimer.direction == "up") ? (currencyAmount * (((totalTime - time) / totalTime) / 100)) : (currencyAmount * ((time / totalTime) / 100));
            var formattedAmount = $filter('currency')(amount, '$');
            timer.timerText = curTimer.timerText + " " + formattedAmount;
          } else if (curTimer.timerType == "percent") {
            timer.timerText = curTimer.timerText + " " + percent + "%";
          } else if (curTimer.timerType == "time") {
            var formattedTime = (curTimer.direction == "up") ? ((totalTime - time).toString().toHHMMSS()) : (time.toString().toHHMMSS());
            timer.timerText = curTimer.timerText + " " + formattedTime;
          }

          $scope.timers[key] = timer;
        }
      } // for (var key in newValue)

      // If a timer is not set and there are timers to update, set the timer.
      if ($scope.timerId === undefined) {
        $scope.timerId = $interval(function () {
          for (var key in $scope.timers) {
            if ($scope.timers.hasOwnProperty(key)) {

              var curTimer = $scope.timers[key];
              var totalTime = curTimer.endTime - curTimer.startTime;
              var time = curTimer.endTime - Date.now();
              var percent = $scope.timers[key].timerValue = (curTimer.direction == "up") ? (Math.round(( ((totalTime - time) / totalTime) * 100))) : (Math.round(( (time / totalTime) * 100)));

              if (curTimer.timerType == "currency") {
                var amount = (curTimer.direction == "up") ? (currencyAmount * (((totalTime - time) / totalTime) / 100)) : (currencyAmount * ((time / totalTime) / 100));
                var formattedAmount = $filter('currency')(amount, '$');
                $scope.timers[key].timerText = curTimer.baseText + " " + formattedAmount;
              } else if (curTimer.timerType == "percent") {
                $scope.timers[key].timerText = curTimer.baseText + " " + percent + "%";
              } else if (curTimer.timerType == "time") {
                var formattedTime = (curTimer.direction == "up") ? ((totalTime - time).toString().toHHMMSS()) : (time.toString().toHHMMSS());
                $scope.timers[key].timerText = curTimer.baseText + " " + formattedTime;
              }

              if (time < 0) {
                delete $scope.timers[key];
              }
            }
          } // for (var key in newValue)
        }, 500); // $scope.timerId = setInterval(function() {
      }
    } // if (typeof newValue === "object) {
  }, true); // END $scope.$watch('client.player.timers')...

}

TimersCtrl.$inject = ['$scope', '$filter', '$interval'];
