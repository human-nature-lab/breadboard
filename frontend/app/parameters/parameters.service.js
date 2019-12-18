angular.module('breadboard.parameters.service', [])
  .factory('ParametersSrv', ParametersSrv);

ParametersSrv.$inject = ['$http', '$q'];

function ParametersSrv($http, $q) {
  return {
    getParameters: getParameters,
    createParameter: createParameter,
    removeParameter: removeParameter
  };

  function getParameters(experimentId) {
    return $http.get('parameters/' + experimentId)
      .then(function (response) {
          if (response.status < 400) {
            return $q.when(response);
          }
          return $q.reject(response);
        },
        function (response) {
          return $q.reject(response);
        });
  }

  function createParameter(experimentId, name, type, minVal, maxVal, defaultVal, description) {
    return $http.put('parameters/' + experimentId, { name, type, minVal, maxVal, defaultVal, description })
      .then(function (response) {
          if (response.status < 400) {
            return $q.when(response);
          }
          return $q.reject(response);
        },
        function (response) {
          return $q.reject(response);
        });
  }

  function removeParameter(parameterId) {
    return $http.delete('parameters/' + parameterId)
      .then(function (response) {
          if (response.status < 400) {
            return $q.when(response);
          }
          return $q.reject(response);
        },
        function (response) {
          return $q.reject(response);
        });
  }
}

