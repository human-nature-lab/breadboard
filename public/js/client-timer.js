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
          timer.elapsed = curTimer.elapsed;
          timer.duration = curTimer.duration;
          timer.direction = curTimer.direction;
          timer.timerType = curTimer.timerType;
          timer.baseText = curTimer.timerText;
          var totalTime = curTimer;
          var percent = Math.round(((timer.duration - timer.elapsed) / timer.duration)*100);
          // var time = curTimer.endTime - Date.now();
          var time = curTimer.endTime - curTimer.elapsed;
          console.log("Date.now()", Date.now());
          console.log("curTimer.startTime", curTimer.startTime);
          console.log("curTimer.endTime", curTimer.endTime);
          console.log("time", time);
          var currencyAmount = parseInt(curTimer.currencyAmount);
          timer.currencyAmount = currencyAmount;

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
              curTimer.elapsed += 500;
              var p = (curTimer.duration - curTimer.elapsed) / curTimer.duration;
              var time = curTimer.elapsed;
              var percent = p;
              $scope.timers[key].timerValue = (curTimer.direction == "up") ? (curTimer.elapsed / curTimer.duration) * 100: p * 100;

              if (curTimer.timerType == "currency") {
                var amount = (curTimer.direction == "up") ? (curTimer.currencyAmount * (((curTimer.duration - curTimer.elapsed) / curTimer.duration) / 100)) : (curTimer.currencyAmount * ((curTimer.elapsed / curTimer.duration) / 100));
                var formattedAmount = $filter('currency')(amount, "$");
                $scope.timers[key].timerText = curTimer.baseText + " " + formattedAmount;
              } else if (curTimer.timerType == "percent") {
                $scope.timers[key].timerText = curTimer.baseText + " " + percent + "%";
              } else if (curTimer.timerType == "time") {
                var formattedTime = (curTimer.direction == "up") ? (time.toString().toHHMMSS()) : ((curTimer.duration - curTimer.elapsed).toString().toHHMMSS());
                $scope.timers[key].timerText = curTimer.baseText + " " + formattedTime;
              }

              if (time < 0) {
                delete $scope.timers[key];
              }
            }
          } // for (var key in newValue)
        }, 500); // $scope.timerId = $interval(function() {
      }
    } // if (typeof newValue === "object) {
  }, true); // END $scope.$watch('client.player.timers')...

}

TimersCtrl.$inject = ['$scope', '$filter', '$interval'];