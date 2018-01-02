angular.module('breadboard.create-new-experiment.service', [])
  .factory('CreateNewExperimentSrv', CreateNewExperimentSrv);

CreateNewExperimentSrv.$inject = ['$http', '$q'];

function CreateNewExperimentSrv($http, $q) {
  let service = {
    createNewExperiment: createNewExperiment
  };

  return service;

  function createNewExperiment(newExperimentName, copyExperimentId) {
    const payload = {
      'newExperimentName': newExperimentName,
      'copyExperimentId': copyExperimentId
    };

    return $http.put('experiment', payload)
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
