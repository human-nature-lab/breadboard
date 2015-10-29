'use strict';

/* Controllers */
function ChoicesCtrl($scope, $clientFactory) {
  $scope.custom = undefined;

  $scope.customComplete = function() {
    if ($scope.custom) {
      angular.forEach(".param", function(value, key) {
        if (value.$error)
          console.log(value.$error);
      });
    }
  };

  $scope.makeChoice = function (uid) {
    if (angular.element('input[name=params]')) {
      $scope.params = {};
      angular.forEach(angular.element(".param"), function(value, key){
        var param = angular.element(value);
        if (param.attr("name")) {
          $scope.params[param.attr("name")] = param.val();
        }
      });
    }
    var sendData = {
      "action": "MakeChoice",
      "choiceUID": uid,
      "params": ($scope.params == undefined) ? "{}" : JSON.stringify($scope.params)
    };
    $scope.params = {};
    $clientFactory.send(sendData);
  };

  $scope.$watch('client.player.choices', function (newValue) {
    if (newValue && newValue[0] && newValue[0].custom) {
      $scope.custom = newValue[0].custom;
    } else {
      $scope.custom = undefined;
    }
    $scope.childChoices = newValue;
  }, true);
}

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
      console.log("newValue", newValue);
      // Remove deleted timers
      for (var key in $scope.timers) {
        console.log("key in $scope.timers", key);
        if (! newValue.hasOwnProperty(key)) {
          console.log("got here");
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

          console.log("adding timer", timer);
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

function ClientCtrl($scope, $clientFactory) {
  $clientFactory.onmessage(function (message) {
    try {
      if ($scope.client == undefined) {
        $scope.client = {};
        $scope.client.graph = {};
      }

      var data = JSON.parse(message.data);
      // For debugging:
      //console.log(data);
      if (data.queuedMessages != undefined) {
        for (var i = 0; i < data.queuedMessages.length; i++) {
          _.extend($scope.client, data.queuedMessages[i]);
        }
      } else {
        if (data.hasOwnProperty("graph")) {
          $scope.client.graph = _(data.graph).clone();
        }
        if (data.hasOwnProperty("player")) {
          $scope.client.player = _(data.player).clone();
          console.log("data.player", $scope.client.player);
        }
        if (data.hasOwnProperty("style")) {
          $scope.client.style = _(data.style).clone();
          applyStyle();
        }
        //$scope.client = data;
        //_.extend($scope.client, data);
      }

      /*
      if ($scope.client.style != undefined) {
        applyStyle();
      }
      */
    }
    catch (e) {
      // TODO: add error object to scope and handle error client-side
      console.log("Parse error: " + e.toString());
    }
  });

  /* Graph here */
  $scope.clientGraph = new Graph((Math.min($(window).width() * 0.50, $(window).height())), (Math.min($(window).width() * 0.50, $(window).height())), clientVars.clientId);

  $scope.$watch('client.graph', function (newValue) {
    $scope.clientGraph.updateGraph(newValue);
  }, true);

  //$scope.$watch('client.player.timers', function(newValue) { $scope.setTimers(newValue); }, true);


  var applyStyle = function () {
    $('#style').text($scope.client.style);
  };

  var beep = new Audio('/assets/snd/countdown_beeps.ogg');
}

TimersCtrl.$inject = ['$scope', '$filter', '$interval'];
ClientCtrl.$inject = ['$scope', 'clientFactory'];
ChoicesCtrl.$inject = ['$scope', 'clientFactory'];
