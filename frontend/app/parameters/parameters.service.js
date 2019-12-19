angular.module('breadboard.parameters.service', [])
  .factory('ParametersSrv', ParametersSrv);

ParametersSrv.$inject = ['$http', '$q'];

function ParametersSrv($http, $q) {
  return {
    createParameter: createParameter,
    removeParameter: removeParameter
  };

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

  function removeParameter(experimentId, parameterId) {
    return $http.delete('parameters/' + experimentId + "/" + parameterId)
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

