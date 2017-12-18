'use strict';

angular.module('amt-admin.services', [])
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
      updateAssignmentCompleted: updateAssignmentCompleted
    },
    sandbox = false;

  return service;

  function isSandbox() {
    return sandbox;
  }

  function setSandbox(s) {
    sandbox = s;
  }

  function getAccountBalance() {
    return $http.get('/amtadmin/getAccountBalance' + ((sandbox) ? '?sandbox=true' : ''))
      .then(function (response) {
          return $q.when(response);
        },
        function (response) {
          return $q.reject(response);
        });
  }

  function listHITs(nextToken) {
    var url = '/amtadmin/listHITs' + buildQueryStringParameters({'sandbox': sandbox, 'nextToken': nextToken});
    return $http.get(url)
      .then(function (response) {
          return $q.when(response);
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
    console.log('listAssignmentsForHIT', url);
    return $http.get(url)
      .then(function (response) {
          return $q.when(response);
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
    console.log('listBonusPaymentsForHIT', url);
    return $http.get(url)
      .then(function (response) {
          return $q.when(response);
        },
        function (response) {
          return $q.reject(response);
        });
  }

  function approveAssignment(assignmentId) {
    /* Test Code
    var deferred = $q.defer();
    $timeout(function() {
      if (Math.random() < .5) {
        deferred.resolve();
      } else {
        deferred.reject("AMT returned an error.");
      }
    }, Math.random() * 10000);
    return deferred.promise;
    */
    return $http.get('/amtadmin/approveAssignment/' + assignmentId + ((sandbox) ? '?sandbox=true' : ''))
      .then(function (response) {
          return $q.when(response);
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
          return $q.when(response);
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
          return $q.when(response);
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
          return $q.when(response);
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
          return $q.when(response);
        },
        function (response) {
          return $q.reject(response);
        });
  }

}

