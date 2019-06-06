function ManageQualificationsCtrl($scope, ManageQualificationsSrv) {
  $scope.curToken = 0;
  $scope.qualifications = [];
  $scope.tokens = [null];
  $scope.selectedQualificationType = undefined;
  $scope.status = 0; // 1: loading, 2: success, 3: failure
  $scope.maxResults = 25;
  $scope.error = '';
  $scope.page = undefined;
  $scope.addQualificationTypeStatus = 0; // 0: not running, 1: uploading, 2: done
  $scope.addQualificationsStatus = 0; // 0: not running, 1: uploading, 2: done
  $scope.addQualificationsIds = '';
  $scope.addQualificationsStatuses = [];
  $scope.addQualificationsError = '';
  $scope.addQualificationsSuccessCount = 0;
  $scope.addQualificationsErrorCount = 0;

  $scope.qualificationTypes = [];

  $scope.pageQualifications = pageQualifications;
  $scope.openAddQualificationsDialog = openAddQualificationsDialog;
  $scope.addQualifications = addQualifications;
  $scope.importWorkerIDsFromFile = importWorkerIDsFromFile;
  $scope.removeQualification = removeQualification;
  $scope.selectQualificationType = selectQualificationType;
  $scope.addQualificationType = addQualificationType;

  function initPagination() {
    $scope.qualifications = [];
    $scope.tokens = [null];
    $scope.curToken = 0;
  }

  function addQualificationType(experimentUid) {
    ManageQualificationsSrv.addQualificationType(experimentUid, $scope.sandbox)
      .then(
        function(response){
          var qualificationType = response.data;
          for (var i = 0; i < $scope.qualificationTypes.length; i++) {
            if ($scope.qualificationTypes[i].experimentUid === qualificationType.experimentUid) {
              $scope.qualificationTypes.splice(i, 1, qualificationType);
              $scope.selectedQualificationType = $scope.qualificationTypes[i];
              break;
            }
          }
        },
        function(error) {
          console.log(error);
        });
  }

  $scope.$watch('maxResults', function() {
    if ($scope.selectedQualificationType !== undefined) {
      initPagination();
      listWorkersWithQualificationType();
    }
  });

  $scope.$watch('selectedQualificationType', function() {
    if ($scope.selectedQualificationType !== undefined && $scope.selectedQualificationType.qualificationTypeId !== null) {
      initPagination();
      listWorkersWithQualificationType();
    }
  });

  $scope.$watch('sandbox', function() {
    $scope.selectedQualificationType = undefined;
    /*
    if ($scope.selectedQualificationType !== undefined) {
      initPagination();
      listWorkersWithQualificationType();
    }
    */
    listQualificationTypes();
  });

  function selectQualificationType(qualificationType) {
    $scope.selectedQualificationType = qualificationType;
  }

  function removeQualification(qualification) {
    ManageQualificationsSrv.disassociateQualificationFromWorker(qualification.qualificationTypeId, qualification.workerId, $scope.sandbox)
      .then(
        function() {
          $scope.qualifications.splice($scope.qualifications.indexOf(qualification), 1);
        },
        function (error) {
          console.error(error);
        });
  }

  function pageQualifications(page) {
    if (page > -1 && page < $scope.tokens.length) {
      $scope.curToken = page;
      listWorkersWithQualificationType();
    }
  }

  function listWorkersWithQualificationType() {
    var token = undefined;
    if ($scope.curToken !== undefined && $scope.curToken < $scope.tokens.length) {
      token = $scope.tokens[$scope.curToken];
    }
    $scope.status = 1;
    ManageQualificationsSrv.listWorkersWithQualificationType($scope.selectedQualificationType.qualificationTypeId, token, $scope.maxResults, $scope.sandbox)
      .then(function(response) {
          console.log('response', response);
          $scope.status = 2;
          if ($scope.curToken === $scope.tokens.length - 1 && response.data.qualifications.length > 0) {
            if (response.data.nextToken !== null) {
              $scope.tokens.push(response.data.nextToken);
            }
          }
          if (response.data.qualifications.length === 0) {
            // Reached the end of the list, remove the last page
            $scope.curToken--;
            $scope.tokens.pop();
          } else {
            $scope.qualifications = response.data.qualifications;
          }
        },
        function(error) {
          $scope.status = 3;
          $scope.error = error.data;
        });
  }

  function openAddQualificationsDialog() {
    $('#addQualificationsDialog').dialog({
      title: 'Add participant qualifications',
      width: '600px'
    });
  }

  function importWorkerIDsFromFile(file) {
    if(!file) return;
    var reader = new FileReader();
    reader.onload = function (e) {
      $scope.addQualificationsIds = e.target.result;
      $scope.$apply();
    };
    reader.readAsText(file);
  }

  function listQualificationTypes() {
    ManageQualificationsSrv.listQualificationTypes($scope.sandbox)
      .then(
        function(results) {
          console.log('listQualificationTypes', results);
          $scope.qualificationTypes = results.data.qualificationTypes;
        },
        function(error) {
          console.error(error);
        });
  }


  function addQualifications() {
    $scope.addQualificationsStatus = 1;
    var workerIdsArray = $scope.addQualificationsIds.trim().split('\n');
    angular.forEach(workerIdsArray, function(wid, i) {
      var workerId = wid.trim();
      $scope.addQualificationsStatuses[i] = { 'workerId': workerId, 'status': 'RUNNING' };
      ManageQualificationsSrv.assignParticipantQualifications($scope.selectedQualificationType.qualificationTypeId, workerId, $scope.sandbox)
        .then(function () {
            $scope.addQualificationsStatuses[i] = { 'workerId': workerId, 'status': 'SUCCESS' };
            $scope.addQualificationsSuccessCount++;
            if (i === (workerIdsArray.length - 1)) {
              $scope.addQualificationsStatus = 2;
            }
          },
          function (error) {
            $scope.addQualificationsErrorCount++;
            $scope.addQualificationsStatuses[i] = { 'workerId': workerId, 'status': error.data };
            $scope.addQualificationsStatus = 3;
          });
    });
    //console.log('experimentUid', $scope.experimentUid);

    /*
    ManageQualificationsSrv.getExperimentQualificationTypeId($scope.experimentId)
      .then(function(response) {
        },
        function(error) {
          console.error(error);
        });
        */
  }
}

ManageQualificationsCtrl.$inject = ['$scope', 'ManageQualificationsSrv'];

export default ManageQualificationsCtrl;
