function TestingCtrl($scope, TestingSrv) {
  console.log('scope.nodes', $scope.nodes);

  $scope.getTestClientURL = function(experimentId, experimentInstanceId, playerId) {
    return window.location.protocol + "//" + window.location.hostname + ":" + window.location.port + "/game/" + experimentId + "/" + experimentInstanceId + "/" + playerId + "/connected";
  };

  $scope.testNodes = function() {
    var returnNodes = [];
    angular.forEach($scope.nodes, function(node) {
      if (node.hasOwnProperty('testmode') && node.testmode) {
        returnNodes.push(node);
      }
    });
    return returnNodes;
  }
}

TestingCtrl.$inject = ['$scope', 'TestingSrv'];

export default TestingCtrl;
