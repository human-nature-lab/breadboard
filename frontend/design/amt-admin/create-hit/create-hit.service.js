import util from '../../util/util';
var buildQueryStringParameters = util.buildQueryStringParameters;

angular.module('breadboard.amt-admin.create-hit.services', [])
  .factory('CreateHitSrv', CreateHitSrv);

CreateHitSrv.$inject = ['$http', '$q', '$timeout'];

function CreateHitSrv($http, $q, $timeout) {
  var service = {
    createHIT: createHIT
  };

  return service;

  function createHIT(title, description, reward, maxAssignments, hitLifetime, tutorialTime, assignmentDuration, keywords, disallowPrevious, experimentId, experimentInstanceId, qualificationRequirements, sandbox) {
    var payload = {
      'title': title,
      'description': description,
      'reward': reward + '',
      'maxAssignments': maxAssignments,
      'hitLifetime': hitLifetime,
      'assignmentDuration': assignmentDuration,
      'keywords': keywords,
      'tutorialTime': tutorialTime,
      'disallowPrevious': disallowPrevious,
      'experimentId': experimentId + '',
      'experimentInstanceId': experimentInstanceId + '',
      'qualificationRequirements': qualificationRequirements
    };

    return $http.post('/amtadmin/createHIT' + ((sandbox) ? '?sandbox=true' : ''), payload)
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
