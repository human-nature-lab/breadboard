function ManageHitsCtrl($scope, $q, $filter, $timeout, AMTAdminSrv, ManageQualificationsSrv) {
  $scope.manageHits = {
    'status' : 1, // 0: no requester key found, 1: loading, 2: loaded, 3: Error,
    'error' : ''
  };

  $scope.globals = {
    bonusReason : "Final game score.",
    rejectionReason : "You failed to complete the task correctly."
  };

  $scope.curToken = 0;
  $scope.hits = [];
  $scope.tokens = [null];
  $scope.selectedHIT = null;
  $scope.maxAssignments = 20;

  $scope.listHITs = listHITs;
  $scope.selectHIT = selectHIT;
  $scope.refreshAssignmentsForHIT = refreshAssignmentsForHIT;
  $scope.approveAll = approveAll;
  $scope.rejectAll = rejectAll;
  $scope.grantAll = grantAll;
  $scope.completeAll = completeAll;
  $scope.qualifyAll = qualifyAll;
  $scope.showAll = showAll;
  $scope.updateAssignmentCounts = updateAssignmentCounts;
  $scope.updateAssignmentCompleted = updateAssignmentCompleted;
  $scope.pageHITs = pageHITs;
  $scope.listHITs = listHITs;
  $scope.moreAssignmentsForHIT = moreAssignmentsForHIT;
  $scope.assignQualification = assignQualification;
  $scope.grantBonuses = grantBonuses;
  $scope.approveAssignments = approveAssignments;
  $scope.rejectAssignments = rejectAssignments;
  $scope.getAssignmentsCSV = getAssignmentsCSV;

  $scope.$watch('sandbox', function(sandbox) {
    $scope.tokens = [null];
    $scope.curToken = 0;
    $scope.selectedHIT = null;
    listHITs();
  });

  listHITs();

  function listHITs() {
    $scope.manageHits.status = 1;
    AMTAdminSrv.listHITs($scope.tokens[$scope.curToken]).then(function(response) {
        //console.log('response', response);
        $scope.manageHits.status = 2;
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
          angular.forEach($scope.hits, function(hit) {
            if (hit.hasOwnProperty('requesterAnnotation') && hit.requesterAnnotation) {
              try {
                let requesterAnnotation = JSON.parse(hit.requesterAnnotation);
                hit.experimentUid = requesterAnnotation.experimentUid;
              } catch(err) {
                //Invalid JSON
              }
            }
          });
        }
        //console.log('$scope.hits', $scope.hits);
      },
      function(err) {
        //console.log('err', err);
        // TODO: Probably shouldn't be relying upon a string comparison here
        if (err.data === 'No AWS keys provided' || err === 'No AWS keys provided') {
          $scope.manageHits.status = 0;
        } else {
          $scope.manageHits.status = 3;
          $scope.manageHits.error = (err.data) ? err.data : err;
        }
      });
  }

  function pageHITs(page) {
    if (page > -1 && page < $scope.tokens.length) {
      $scope.curToken = page;
      listHITs();
    }
  }

  function selectHIT(hit) {
    if (hit === $scope.selectedHIT) {
      $scope.selectedHIT = null;
    } else {
      refreshAssignmentsForHIT(hit);
    }
  }

  function getAMTAssignments() {
    AMTAdminSrv.getAMTAssignments($scope.experiment.id).then(function(response) {
      //console.log('getAMTAssignments', response);
      $scope.manageWorkers.amtAssignments = response.data.assignments;
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
        assignment.qualificationAssigned = false;
        assignment.qualificationPending = true;
        assignment.qualificationError = null;
        assignment.qualificationSuccess = false;

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

  function refreshAssignmentsForHIT(hit) {
    hit.assignments = null;
    if (hit.approveCount === undefined) hit.approveCount = 0;
    if (hit.rejectCount === undefined) hit.rejectCount = 0;
    if (hit.bonusTotal === undefined) hit.bonusTotal = 0;
    $scope.selectedHIT = hit;

    getAssignmentsForHIT(hit, null).then(function(assignments) {
      hit.assignments = assignments;
      updateBonusPaymentsForHIT(hit, null);
      updateAssignQualificationForHIT(hit);
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

  function updateAssignQualificationForHIT(hit) {
    ManageQualificationsSrv.getExperimentQualificationTypeId(hit.experimentUid, $scope.sandbox)
      .then(
        function(response) {
          var qualificationTypeId = response.data.qualificationTypeId;
          angular.forEach(hit.assignments, function(assignment) {
            AMTAdminSrv.getQualificationScore(qualificationTypeId, assignment.workerId)
              .then(
                function(response) {
                  var qualificationGranted = (response.data.status === 'Granted');
                  assignment.qualificationAssigned = qualificationGranted;
                  assignment.qualificationPending = false;
                  //console.log('updateQualificationForHIT', response);
                },
                function(error) {
                  console.error(error);
                }
              );
          });
        },
        function(error) {
          console.error(error);
        }
      );
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

  function completeAll(hit) {
    angular.forEach(hit.assignments, function(assignment) {
      assignment.assignmentCompleted = true;
      updateAssignmentCompleted(assignment);
    });
  }

  function qualifyAll(hit) {
    var experimentUid = hit.experimentUid;
    ManageQualificationsSrv.getExperimentQualificationTypeId(experimentUid, $scope.sandbox)
      .then(
        function(response) {
          var qualificationTypeId = response.data.qualificationTypeId;
          angular.forEach(hit.assignments, function(assignment) {
            assignment.qualificationAssigned = true;
            assignQualification(assignment, experimentUid, qualificationTypeId);
          });
        },
        function(error) {
          console.error(error);
        }
      );
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
      updateAssignQualificationForHIT(hit);
    });
  }

  function assignQualification(assignment, experimentUid, _qualificationTypeId) {
    assignment.qualificationPending = true;

    var qualificationTypeId = _qualificationTypeId;
    if (!qualificationTypeId) {
      ManageQualificationsSrv.getExperimentQualificationTypeId(experimentUid, $scope.sandbox)
        .then(
          function(response) {
            qualificationTypeId = response.data.qualificationTypeId;
            assignQualification(assignment, experimentUid, qualificationTypeId);
          },
          function(error) {
            console.error(error);
          }
        );
    } else {
      if (!assignment.qualificationAssigned) {
        AMTAdminSrv.removeParticipantQualification(qualificationTypeId, assignment.workerId)
          .then(
            function() {
              assignment.qualificationPending = false;
              assignment.qualificationAssigned = false;
              assignment.qualificationSuccess = true;
              $timeout(function() {
                assignment.qualificationSuccess = false;
              }, 1500);
            },
            function(error) {
              console.error(error);
              assignment.qualificationAssigned = true;
              assignment.qualificationError = error.data;
            }
          );

      } else {
        AMTAdminSrv.assignParticipantQualification(qualificationTypeId, assignment.workerId)
          .then(
            function() {
              assignment.qualificationPending = false;
              assignment.qualificationAssigned = true;
              assignment.qualificationSuccess = true;
              $timeout(function() {
                assignment.qualificationSuccess = false;
              }, 1500);
            },
            function(error) {
              console.error(error);
              assignment.qualificationAssigned = false;
              assignment.qualificationError = error.data;
            }
          );
      }
    }
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
    //console.log('csvString', csvString);
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

ManageHitsCtrl.$inject = ['$scope', '$q', '$filter', '$timeout', 'AMTAdminSrv', 'ManageQualificationsSrv'];

export default ManageHitsCtrl;
