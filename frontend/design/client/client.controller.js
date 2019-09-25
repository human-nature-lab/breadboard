import _ from 'underscore';
import gremlins from '../util/gremlins.min';

ClientCtrl.$inject = ['$scope', 'clientFactory', '$location', 'clientGraph', 'configService'];
function ClientCtrl($scope, $clientFactory, $location, clientGraph, configService) {
  $scope.languages = [];
  $scope.selectedLanguage = ($location.search().language) ? $location.search().language : "";
  $scope.testmode = false;
  $scope.horde = undefined;
  $scope.gremlinsStarted = false;
  $scope.$watch('client.player.testmode', function(testmode) {
    $scope.testmode = (testmode === true || testmode === 'true');
    startGremlins();
  });

  var emptyLogger = {
    log:   function(msg) {},
    info:  function(msg) {},
    warn:  function(msg) {},
    error: function(msg) {}
  };

  function restartGremlins() {
    console.log('restartGremlins');
    if ($scope.horde !== undefined) {
      $scope.horde.stop();
    }
    $scope.gremlinsStarted = false;
    startGremlins();
  }

  function startGremlins() {
    if ($scope.testmode && (! $scope.gremlinsStarted)) {
      // 2% chance of drop-out
      if (Math.random() < 0.02) return;

      $scope.gremlinStarted = true;
      $scope.horde = gremlins.createHorde()
        .gremlin(gremlins.species.formFiller().triggerInputEvent(true))
        .gremlin(gremlins.species.targetedClicker()
          .clickTypes(['click'])
          .interestingElements(['button'])
          .percentRandom(0))
        .gremlin(gremlins.species.reloader())
        .mogwai(gremlins.mogwais.gizmo())
        .mogwai(gremlins.mogwais.alert())
        .strategy(gremlins.strategies.distribution([0.4, 0.4, 0.2]).delay(100))
        .logger(emptyLogger)
        .after(restartGremlins)
        .unleash();

    } else {
      if ($scope.horde !== undefined) {
        $scope.horde.stop();
      }
    }
  }

  startGremlins();

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
          //console.log("data.player", $scope.client.player);
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