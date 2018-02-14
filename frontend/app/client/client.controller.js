import _ from 'underscore';
ClientCtrl.$inject = ['$scope', 'clientFactory', '$location', 'clientGraph', 'configService'];
function ClientCtrl($scope, $clientFactory, $location, clientGraph, configService) {
  $scope.languages = [];
  $scope.selectedLanguage = ($location.search().language) ? $location.search().language : "";
  $scope.timers = [];
  $scope.selectLanguage = function(language) {
    $scope.selectedLanguage = language;
    $location.search('language', language);
  };

  var applyStyle = function () {
    $('#style').text($scope.client.style);
  };

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
          if($scope.client.player.timers){
            var timerIds = Object.keys($scope.client.player.timers);
            if(timerIds.length.length !== $scope.timers.length){
              $scope.timers = [];
              for(var i=0; i<timerIds.length; i++){
                var timer = $scope.client.player.timers[timerIds[i]];
                timer.id = timerIds[i];
                $scope.timers.push(timer);
              }
              // All this work for a sort... Makes you wonder
              $scope.timers.sort(function(a, b){
                return a.order > b.order;
              });
            } else {
              for(let i=0; i<$scope.timers.length; i++){
                $scope.client.player.timers[$scope.timers[i].id].id = $scope.timers[i].id;
                $scope.timers[i] = $scope.client.player.timers[$scope.timers[i].id];
              }
            }
          }
        }
        if (data.hasOwnProperty("style")) {
          $scope.client.style = _(data.style).clone();
          applyStyle();
        }
      }
    }
    catch (e) {
      console.error("Parse error: " + e.toString());
    }
  });

  $scope.translateText = function(textJson) {
    //console.log("textJson: ", textJson);
    let textObject = JSON.parse(textJson);
    return (textObject.contentArray[0].text);
  };

  let clientId;
  configService.get('clientId').then(id => {
    clientId = id;
    return clientGraph.load();
  }).then(Graph => {
    let parentElement = document.getElementById('#graph');
    /* Graph here */
    $scope.clientGraph = new Graph(clientId, parentElement);
    $scope.$watch('client.graph', function (newValue) {
      $scope.clientGraph.updateGraph(newValue);
    }, true);
  });



  let beep = new Audio('/assets/snd/countdown_beeps.ogg');
}

//console.log(ClientCtrl);
export default ClientCtrl;