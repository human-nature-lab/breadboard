

function AMTAdminCtrl($scope, AMTAdminSrv, $q, $filter, $timeout) {
  $scope.accountBalance = null;
  $scope.tokens = [null];
  $scope.curToken = 0;
  $scope.hits = [];
  $scope.selectedHIT = null;
  $scope.showManageHITs = false;
  $scope.showCreateDummyHITs = false;
  $scope.sandbox = AMTAdminSrv.isSandbox();
  $scope.maxAssignments = 20;

  $scope.dummyHIT = {
    'workerIDs' : '',
    'reason' : 'We could not pay you through the normal means',
    'reward' : 1.00,
    'submitted' : [],
    'nPending' : 0
  };

  $scope.globals = {
    bonusReason : "Final game score.",
    rejectionReason : "You failed to complete the task correctly."
  };
  $scope.listHITs = listHITs;
  $scope.moreAssignmentsForHIT = moreAssignmentsForHIT;
  $scope.pageHITs = pageHITs;
  $scope.selectHIT = selectHIT;
  $scope.updateAssignmentCounts = updateAssignmentCounts;
  $scope.updateAssignmentCompleted = updateAssignmentCompleted;
  $scope.grantBonuses = grantBonuses;
  $scope.approveAssignments = approveAssignments;
  $scope.rejectAssignments = rejectAssignments;
  $scope.approveAll = approveAll;
  $scope.rejectAll = rejectAll;
  $scope.grantAll = grantAll;
  $scope.showAll = showAll;
  $scope.toggleSandbox = toggleSandbox;
  $scope.submitDummyHITs = submitDummyHITs;
  $scope.clearDummyHITs = clearDummyHITs;
  $scope.getAssignmentsCSV = getAssignmentsCSV;
  getAccountBalance();
  listHITs();


  function toggleSandbox() {
    AMTAdminSrv.setSandbox(!AMTAdminSrv.isSandbox());
    $scope.sandbox = AMTAdminSrv.isSandbox();
    $scope.tokens = [null];
    $scope.curToken = 0;
    $scope.selectedHIT = null;
    getAccountBalance();
    listHITs();
  }

  function pageHITs(page) {
    if (page > -1 && page < $scope.tokens.length) {
      $scope.curToken = page;
      listHITs();
    }
  }

  function getAccountBalance() {
    AMTAdminSrv.getAccountBalance().then(function(response) {
      $scope.accountBalance = parseFloat(response.data.availableBalance);
      //console.log('getAccountBalance', response);
    });
  }

  function listHITs() {
    AMTAdminSrv.listHITs($scope.tokens[$scope.curToken]).then(function(response) {
      //console.log('listHITs response', response);
      if ($scope.curToken === $scope.tokens.length - 1 && response.data.hits.length > 0) {
        if (response.data.nextToken !== null) {
          $scope.tokens.push(response.data.nextToken);
        }
      }
      if (response.data.hits.length === 0) {
        // Reached the end of the list, remove the last page
        $scope.curToken--;
        $scope.tokens.pop();
      } else {
        $scope.hits = response.data.hits;
      }
      //console.log('$scope.hits', $scope.hits);
    });
  }

  function updateAssignmentCounts(hit) {
    hit.approveCount = 0;
    hit.rejectCount = 0;
    hit.bonusTotal = 0;
    angular.forEach(hit.assignments, function(assignment) {
      if (assignment.approve) hit.approveCount++;
      if (assignment.reject) hit.rejectCount++;
      if (assignment.grantBonus) hit.bonusTotal += assignment.bonus;
    });
  }

  function showAll(hit) {
    angular.forEach(hit.assignments, function(assignment) {
      assignment.showAnswers = !assignment.showAnswers;
    });
  }

  function approveAll(hit) {
    angular.forEach(hit.assignments, function(assignment) {
      if (assignment.approvalTime === null && assignment.rejectionTime === null) {
        assignment.reject = false;
        assignment.approve = true;
      }
    });
    updateAssignmentCounts(hit);
  }

  function rejectAll(hit) {
    angular.forEach(hit.assignments, function(assignment) {
      if (assignment.rejectionTime === null && assignment.approvalTime === null) {
        assignment.reject = true;
        assignment.approve = false;
      }
    });
    updateAssignmentCounts(hit);
  }

  function grantAll(hit) {
    angular.forEach(hit.assignments, function(assignment) {
      if (assignment.bonusGranted === 0 && assignment.bonus !== null && assignment.bonus > 0) {
        assignment.grantBonus = true;
      }
    });
    updateAssignmentCounts(hit);
  }

  function grantBonuses(hit) {
    angular.forEach(hit.assignments, function(assignment) {
      if (assignment.grantBonus && !assignment.bonusPending) {
        assignment.bonusError = null;
        assignment.bonusPending = true;
        assignment.grantBonus = false;
        AMTAdminSrv.sendBonus(assignment.assignmentId, assignment.workerId, assignment.bonus, $scope.globals.bonusReason).then(function() {
          assignment.bonusPending = null;
          assignment.bonusGranted = assignment.bonus;
          //$scope.accountBalance -= assignment.bonus;
          getAccountBalance();
        }, function(error) {
          assignment.bonusPending = null;
          assignment.bonusError = error.data;
        });
      }
    });
    updateAssignmentCounts(hit);
  }

  function approveAssignments(hit) {
    angular.forEach(hit.assignments, function(assignment) {
      if (assignment.approve && !assignment.approvalPending) {
        assignment.approvalError = null;
        assignment.approvalPending = true;
        assignment.approve = false;
        AMTAdminSrv.approveAssignment(assignment.assignmentId).then(function() {
          assignment.approvalPending = null;
          assignment.approvalTime = Date.now();
          hit.numberOfAssignmentsCompleted++;
          //$scope.accountBalance -= parseFloat(hit.reward);
        }, function(error) {
          assignment.approvalPending = null;
          assignment.approvalError = error.data;
        });
      }
    });
    updateAssignmentCounts(hit);
  }

  function rejectAssignments(hit) {
    angular.forEach(hit.assignments, function(assignment) {
      if (assignment.reject && !assignment.rejectionPending) {
        assignment.rejectionError = null;
        assignment.rejectionPending = true;
        assignment.reject = false;
        AMTAdminSrv.rejectAssignment(assignment.assignmentId, $scope.globals.rejectionReason).then(function() {
          assignment.rejectionPending = null;
          assignment.rejectionTime = Date.now();
          hit.numberOfAssignmentsCompleted++;
        }, function(error) {
          assignment.rejectionPending = null;
          assignment.rejectionError = error.data;
        });
      }
    });
    updateAssignmentCounts(hit);
  }

  function moreAssignmentsForHIT(hit) {
    getAssignmentsForHIT(hit, hit.assignmentsNextToken).then(function(assignments) {
      //console.log('assignments', assignments);
      hit.assignments = hit.assignments.concat(assignments);
      updateBonusPaymentsForHIT(hit, null);
    });
  }

  function updateAssignmentCompleted(assignment) {
    assignment.completedPending = true;
    AMTAdminSrv.updateAssignmentCompleted(assignment.assignmentId, assignment.assignmentCompleted)
      .then(function() {
        assignment.completedPending = false;
        assignment.completedSuccess = true;
        $timeout(function() {
          assignment.completedSuccess = false;
        }, 1500);
      },
      function(error) {
        assignment.completedPending = false;
        assignment.completedSuccess = false;
        assignment.completedError = error.data;
      });
  }

  function updateBonusPaymentsForHIT(hit, nextToken) {
    // Limitation, this will not return more than 100 applied bonuses for a single HIT per request
    AMTAdminSrv.listBonusPaymentsForHIT(hit.hitid, nextToken, 100).then(function(response) {
      angular.forEach(hit.assignments, function(assignment) {
        angular.forEach(response.data.bonusPayments, function(bonusPayment) {
          if (bonusPayment.assignmentId === assignment.assignmentId) {
            assignment.bonusGranted = parseFloat(bonusPayment.bonusAmount);
            assignment.bonusReason = bonusPayment.reason;
            assignment.bonusTime = bonusPayment.grantTime;
          }
        });
      });
      if (response.data.nextToken !== null) {
        // Keep updating bonuses while there are more bonuses to retrieve
        updateBonusPaymentsForHIT(hit, response.data.nextToken);
      } else {
        // We retrieved all bonuses for this HIT, if no bonus was granted, set bonusGranted = 0 at this point
        angular.forEach(hit.assignments, function(assignment) {
          if (assignment.bonusGranted === null) {
            assignment.bonusGranted = 0;
          }
        });
      }
    });
  }

  function getAssignmentsForHIT(hit, nextToken) {
    var deferred = $q.defer();
    AMTAdminSrv.listAssignmentsForHIT(hit.hitid, nextToken, $scope.maxAssignments).then(function(response) {
      //console.log('listAssignmentsForHIT response', response);
      var returnArray = [];
      hit.assignmentsNextToken = response.data.nextToken;
      angular.forEach(response.data.assignments, function(assignment) {
        assignment.answer = parseQuestionFormAnswer(assignment.answer);
        assignment.showAnswers = false;
        assignment.bonus = null;
        assignment.bonusGranted = null;
        assignment.approvalError = null;
        assignment.approvalPending = null;
        assignment.rejectionError = null;
        assignment.rejectionPending = null;
        assignment.bonusPending = null;
        assignment.bonusError = null;
        assignment.completedSuccess = false;
        assignment.completedPending = false;
        assignment.completedError = null;

        if (assignment.answer.hasOwnProperty('bonus')) {
          assignment.bonus = parseFloat(assignment.answer.bonus);
        }
        assignment.approve = false;
        assignment.reject = false;
        returnArray.push(assignment);
      });
      deferred.resolve(returnArray);
      //console.log('assignments', hit.assignments);
    }, function(error) {
      deferred.reject(error);
    });
    return deferred.promise;
  }


  function selectHIT(hit) {
    if (hit === $scope.selectedHIT) {
      $scope.selectedHIT = null;
    } else {
      hit.assignments = null;
      if (hit.approveCount === undefined) hit.approveCount = 0;
      if (hit.rejectCount === undefined) hit.rejectCount = 0;
      if (hit.bonusTotal === undefined) hit.bonusTotal = 0;
      $scope.selectedHIT = hit;

      getAssignmentsForHIT(hit, null).then(function(assignments) {
        hit.assignments = assignments;
        updateBonusPaymentsForHIT(hit, null);
      });
    }
  }

  function parseQuestionFormAnswer(questionFormAnswerString) {
    var returnAnswers = {};
    var domParser = new DOMParser();
    var questionFormAnswerDocument = domParser.parseFromString(questionFormAnswerString, "text/xml");
    var answers = questionFormAnswerDocument.getElementsByTagName("Answer");
    for (var i = 0; i < answers.length; i++) {
      var answer = answers[i];
      var  questionIdentifier = answer.getElementsByTagName("QuestionIdentifier")[0].innerHTML;
      var  freeText = answer.getElementsByTagName("FreeText")[0].innerHTML;
      returnAnswers[questionIdentifier] = freeText;
    }
    return returnAnswers;
  }

  function submitDummyHITs() {
    var workerIDs = $scope.dummyHIT.workerIDs.split('\n');
    for (var i = 0; i < workerIDs.length; i++) {
      var submission = {};
      var workerID = workerIDs[i].trim();
      if (workerID.length > 0) {
        submission.workerId = workerIDs[i].trim();
        submission.status = 'Pending...';
        $scope.dummyHIT.nPending++;
        $scope.dummyHIT.submitted.push(submission);
        (function(workerID, submission) {
          AMTAdminSrv.createDummyHIT(workerID, $scope.dummyHIT.reward, $scope.dummyHIT.reason).then(
            function() {
              submission.status = 'Created';
              $scope.dummyHIT.nPending--;
            },
            function(error) {
              submission.status = 'Error: ' + error.data;
              $scope.dummyHIT.nPending--;
            });
        })(workerID, submission);
      }
    }
  }

  function setStatusByWorkerID(workerID, status) {
    for (var i = 0; i < $scope.dummyHIT.submitted.length; i++) {
      var submission = $scope.dummyHIT.submitted[i];
      if (submission.workerId === workerID) {
        submission.status = status;
      }
    }

  }

  function clearDummyHITs() {
    $scope.dummyHIT.submitted = [];
  }

  function getAssignmentsCSV(hit) {
    var headerRow = '"hitId","hitTitle","creationTime","assignmentId","workerId","approvalTime","rejectionTime","bonusGranted"';
    var rows = [];
    var answerKeys = [];
    angular.forEach(hit.assignments, function (assignment) {
      angular.forEach(assignment.answer, function (value, key) {
        if (answerKeys.indexOf(key) === -1) {
          headerRow += ',"' + key + '"';
          answerKeys.push(key);
        }
      });
    });

    angular.forEach(hit.assignments, function (assignment) {
      var row = '"' + hit.hitid + '","' + hit.title + '","' + ($filter('date')(hit.creationTime, "yyyy-MM-dd")) + '","' + assignment.assignmentId + '","' + assignment.workerId + '","' + ((assignment.approvalTime) ? ($filter('date')(assignment.approvalTime, "yyyy-MM-dd")) : '') + '","' + ((assignment.rejectionTime) ? ($filter('date')(assignment.rejectionTime, "yyyy-MM-dd")) : '') + '","' + assignment.bonusGranted + '"';
      for (var i = 0; i < answerKeys.length; i++) {
        if (assignment.answer.hasOwnProperty(answerKeys[i])) {
          row += ',"' + assignment.answer[answerKeys[i]].replace(/"/g, '""') + '"';
        } else {
          row += ',""'
        }
      }
      rows.push(row);
    });
    var csvString = headerRow + '\n' + rows.join('\n');
    console.log('csvString', csvString);
    var filename = hit.hitid + '.csv';
    var blob = new Blob([csvString], {type: 'text/csv'});

    if (window.navigator && window.navigator.msSaveOrOpenBlob) {
      window.navigator.msSaveOrOpenBlob(blob, filename);
    } else {
      var e = document.createEvent('MouseEvents'),
        a = document.createElement('a');

      a.download = filename;
      a.href = window.URL.createObjectURL(blob);
      a.dataset.downloadurl = ['text/csv', a.download, a.href].join(':');
      e.initEvent('click', true, false, window,
        0, 0, 0, 0, 0, false, false, false, false, 0, null);
      a.dispatchEvent(e);
    }
  }
}

AMTAdminCtrl.$inject = ['$scope', 'AMTAdminSrv', '$q', '$filter', '$timeout'];

export default AMTAdminCtrl;
