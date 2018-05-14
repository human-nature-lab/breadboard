import util from '../util/util';
var buildQueryStringParameters = util.buildQueryStringParameters;

angular.module('breadboard.amt-admin.services', [])
  .factory('AMTAdminSrv', AMTAdminSrv);

AMTAdminSrv.$inject = ['$http', '$q', '$timeout'];

function AMTAdminSrv($http, $q, $timeout) {
  var service = {
      getAccountBalance: getAccountBalance,
      listHITs: listHITs,
      listAssignmentsForHIT: listAssignmentsForHIT,
      listBonusPaymentsForHIT: listBonusPaymentsForHIT,
      approveAssignment: approveAssignment,
      rejectAssignment: rejectAssignment,
      sendBonus: sendBonus,
      isSandbox: isSandbox,
      setSandbox: setSandbox,
      createDummyHIT: createDummyHIT,
      updateAssignmentCompleted: updateAssignmentCompleted,
      getAMTAssignments: getAMTAssignments,
      getAMTWorkers: getAMTWorkers,
      assignParticipantQualification: assignParticipantQualification,
      removeParticipantQualification: removeParticipantQualification,
      getQualificationScore: getQualificationScore
    },
    sandbox = true;

  return service;

  function isSandbox() {
    return sandbox;
  }

  function setSandbox(s) {
    sandbox = s;
  }

  function getAMTWorkers(experimentId, limit, offset, search) {
    return $http.get('/amtadmin/getAMTWorkers/' + experimentId + '?sandbox=' + sandbox + '&limit=' + limit + '&offset=' + offset + '&search=' + search)
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

  function getAccountBalance() {
    return $http.get('/amtadmin/getAccountBalance' + ((sandbox) ? '?sandbox=true' : ''))
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

  function listHITs(nextToken) {
    var url = '/amtadmin/listHITs' + buildQueryStringParameters({'sandbox': sandbox, 'nextToken': nextToken});
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

  function listAssignmentsForHIT(hitId, nextToken, maxResults) {
    var url = '/amtadmin/listAssignmentsForHIT/' + hitId + buildQueryStringParameters({
      'sandbox': sandbox,
      'nextToken': nextToken,
      'maxResults': maxResults
    });
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

  function listBonusPaymentsForHIT(hitId, nextToken, maxResults) {
    var url = '/amtadmin/listBonusPaymentsForHIT/' + hitId + buildQueryStringParameters({
      'sandbox': sandbox,
      'nextToken': nextToken,
      'maxResults': maxResults
    });
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

  function approveAssignment(assignmentId) {
    return $http.get('/amtadmin/approveAssignment/' + assignmentId + ((sandbox) ? '?sandbox=true' : ''))
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

  function rejectAssignment(assignmentId, requesterFeedback) {
    var payload = {
      'requesterFeedback': requesterFeedback
    };
    return $http.post('/amtadmin/rejectAssignment/' + assignmentId + ((sandbox) ? '?sandbox=true' : ''), payload)
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

  function sendBonus(assignmentId, workerId, bonusAmount, reason) {
    var payload = {
      'workerId': workerId,
      'bonusAmount': bonusAmount + '',
      'reason': reason
    };

    return $http.post('/amtadmin/sendBonus/' + assignmentId + ((sandbox) ? '?sandbox=true' : ''), payload)
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

  function createDummyHIT(workerId, reward, reason) {
    var payload = {
      'workerId': workerId,
      'reward': reward + '',
      'reason': reason
    };

    return $http.post('/amtadmin/createDummyHit' + ((sandbox) ? '?sandbox=true' : ''), payload)
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

  function updateAssignmentCompleted(assignmentId, completed) {
    var payload = {
      'completed': completed
    };

    return $http.post('/amtadmin/updateAssignmentCompleted/' + assignmentId, payload)
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

  function assignParticipantQualification(qualificationTypeId, workerId) {
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

  function removeParticipantQualification(qualificationTypeId, workerId) {
    var payload = {
      'workerId': workerId,
      'qualificationTypeId': qualificationTypeId
    };

    return $http.post('/amtadmin/removeParticipantQualification' + ((sandbox) ? '?sandbox=true' : ''), payload)
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

  function getQualificationScore(qualificationTypeId, workerId) {
    var payload = {
      'workerId': workerId,
      'qualificationTypeId': qualificationTypeId
    };

    return $http.post('/amtadmin/getQualificationScore' + ((sandbox) ? '?sandbox=true' : ''), payload)
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

  function getAMTAssignments(experimentId) {
    return $http.get('/experiment/' + experimentId + '/amtassignments' + ((sandbox) ? '?sandbox=true' : ''))
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

