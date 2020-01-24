angular.module('breadboard.steps.service', [])
  .factory('StepsSrv', StepsSrv);

StepsSrv.$inject = ['$http', '$q'];

function StepsSrv($http, $q) {
  let service = {
    getSteps: getSteps,
    updateStep: updateStep,
    createStep: createStep,
    deleteStep: deleteStep
  };

  return service;

  function getSteps(experimentId) {
    return $http.get('steps/' + experimentId)
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

  function updateStep(experimentId, step) {
    const payload = {
      'experimentId': experimentId,
      'stepSource': step.clientSource,
      'name': step.name
    };
    return $http.post('steps/' + step.id, payload)
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

  function createStep(experimentId, stepName) {
    const payload = {
      'stepName': stepName
    };
    return $http.put('steps/' + experimentId, payload)
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

  function deleteStep(stepId) {
    return $http.delete('steps/' + stepId)
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

