import util from '../../util/util';
var buildQueryStringParameters = util.buildQueryStringParameters;

angular.module('breadboard.amt-admin.manage-qualifications.services', [])
  .factory('ManageQualificationsSrv', ManageQualificationsSrv);

ManageQualificationsSrv.$inject = ['$http', '$q', '$timeout'];

function ManageQualificationsSrv($http, $q, $timeout) {
  var service = {
      assignParticipantQualifications: assignParticipantQualifications,
      getExperimentQualificationTypeId: getExperimentQualificationTypeId,
      listWorkersWithQualificationType: listWorkersWithQualificationType,
      disassociateQualificationFromWorker: disassociateQualificationFromWorker,
      listQualificationTypes: listQualificationTypes,
      addQualificationType: addQualificationType
    };

  return service;

  function getExperimentQualificationTypeId(experimentUid, sandbox) {
    return $http.get('/amtadmin/getExperimentQualificationTypeId/' + experimentUid + ((sandbox) ? '?sandbox=true' : ''))
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

  function listWorkersWithQualificationType(qualificationTypeId, nextToken, limit, sandbox) {
    var url = '/amtadmin/listWorkersWithQualificationType/' + qualificationTypeId + buildQueryStringParameters({'sandbox': sandbox, 'nextToken': nextToken, 'maxResults': limit});
    return $http.get(url)
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

  function addQualificationType(experimentUid, sandbox) {
    var url = '/amtadmin/addQualificationType/' + experimentUid + buildQueryStringParameters({'sandbox': sandbox});
    return $http.put(url)
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

  function listQualificationTypes(sandbox) {
    var url = '/amtadmin/listQualificationTypes' + buildQueryStringParameters({ 'sandbox': sandbox });
    return $http.get(url)
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

  function disassociateQualificationFromWorker(qualificationTypeId, workerId, sandbox) {
    var payload = {
      'workerId': workerId
    };
    return $http.post('/amtadmin/disassociateQualificationFromWorker/' + qualificationTypeId + ((sandbox) ? '?sandbox=true' : ''), payload)
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

  function assignParticipantQualifications(qualificationTypeId, workerId, sandbox) {
    var payload = {
      'workerId': workerId,
      'qualificationTypeId': qualificationTypeId
    };
    return $http.post('/amtadmin/assignParticipantQualification' + ((sandbox) ? '?sandbox=true' : ''), payload)
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
