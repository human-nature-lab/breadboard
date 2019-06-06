angular.module('breadboard.amt-admin.create-hit.select-qualifications.services', [])
  .factory('SelectQualificationsSrv', SelectQualificationsSrv);

SelectQualificationsSrv.$inject = ['$http', '$q'];

function SelectQualificationsSrv($http, $q) {
  var service = {
    createHIT: createHIT
  };

  return service;

  function createHIT(title, description, reward, maxAssignments, hitLifetime, tutorialTime, assignmentDuration, keywords, disallowPrevious, experimentId, experimentInstanceId, sandbox) {
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
      'experimentInstanceId': experimentInstanceId + ''
    };

    console.log('createHIT', payload, sandbox);

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
