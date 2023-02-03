angular.module('breadboard.testing.services', [])
  .factory('TestingSrv', TestingSrv);

TestingSrv.$inject = ['$http', '$q', '$timeout'];

function TestingSrv($http, $q, $timeout) {
  var service = {};

  return service;
}

