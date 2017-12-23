CSVService.$inject = ['$http', '$q'];
export default function CSVService($http, $q){

  return {
    'getExperimentInstances': getExperimentInstances,
    'getInstanceData': getInstanceData
  };

  function getExperimentInstances(experimentId) {
    return $http.get('/csv/instances/' + experimentId)
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

  function getInstanceData(experimentInstanceId) {
    return $http.get('/csv/data/' + experimentInstanceId)
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