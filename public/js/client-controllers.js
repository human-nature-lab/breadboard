'use strict';

function ChoicesCtrl($scope, $clientFactory) {
  $scope.custom = undefined;

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
      }
    }
    catch (e) {
      console.log("Parse error: " + e.toString());
    }
  });

  /* Graph here */
  $scope.clientGraph = new Graph((Math.min($(window).width() * 0.50, $(window).height())), (Math.min($(window).width() * 0.50, $(window).height())), clientVars.clientId);

  $scope.$watch('client.graph', function (newValue) {
    $scope.clientGraph.updateGraph(newValue);
  }, true);

  var applyStyle = function () {
    $('#style').text($scope.client.style);
  };

  var beep = new Audio('/assets/snd/countdown_beeps.ogg');
}

ClientCtrl.$inject = ['$scope', 'clientFactory'];
ChoicesCtrl.$inject = ['$scope', 'clientFactory'];
