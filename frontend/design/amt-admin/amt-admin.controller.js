import _ from 'underscore';

function AMTAdminCtrl($scope, AMTAdminSrv, Upload) {
  $scope.accountBalance = null;
  $scope.selectedTab = 'manage';
  $scope.showCreateHIT = false;
  $scope.creatingHIT = false;
  $scope.showManageHITs = false;
  $scope.showCreateDummyHITs = false;
  $scope.sandbox = AMTAdminSrv.isSandbox();

  $scope.dummyHIT = {
    'workerIDs' : '',
    'reason' : '',
    'reward' : 1,
    'submitted' : [],
    'nPending' : 0
  };

  $scope.manageWorkers = {
    'status' : 0, // 0: no experiment selected, 1: loading, 2: loaded, 3: Error
    'error' : '',
    'experimentId' : undefined,
    'experimentUid' : undefined,
    'amtWorkers' : [],
    'sandbox': undefined,
    'selectedWorker': undefined,
    'limit': 25,
    'page': 1,
    'total': 0,
    'search': '',
    'lastSearch': ''
  };

  $scope.toggleSandbox = toggleSandbox;
  $scope.submitDummyHITs = submitDummyHITs;
  $scope.clearDummyHITs = clearDummyHITs;
  getAccountBalance();

  let debounceGetAMTWorkers = _.debounce(getAMTWorkers, 750);
  $scope.$watch('manageWorkers.search', function(search) {
    if (search !== $scope.manageWorkers.lastSearch) {
      $scope.manageWorkers.lastSearch = search;
      // TODO: Latest version of angular has a built-in debounce model update
      // which is superior
      debounceGetAMTWorkers();
    }

  });

  /*
  $scope.$watch('manageWorkers.file', function() {
    if(!$scope.manageWorkers.file || !$scope.experiment) return;
    $scope.manageWorkers.importStatus = 1; //Uploading
    Upload.upload({
      url: '/amtadmin/importAMTWorkers/' +  $scope.experiment.id + '?sandbox=' + $scope.sandbox,
      data: {
        file: $scope.manageWorkers.file
      }
    }).then(function(resp){
      if (resp.status < 400) { //Success
        $scope.manageWorkers.file = undefined;
        $scope.manageWorkers.importStatus = 2; //Uploaded
        $timeout(function() {
          $scope.manageWorkers.importStatus = 0;
        }, 1500);
      } else {
        $scope.manageWorkers.importStatus = 3; //Error
        $scope.manageWorkers.importError = (resp.data) ? resp.data : resp; //Error
      }
    }, function(err){
      $scope.manageWorkers.importError = (err.data) ? err.data : err; //Error
    }, function(evt){
      console.log('import upload progress', evt);
    });
  });
  */

  $scope.$watch('manageWorkers.limit', function(newLimit, oldLimit) {
    // TODO: Change the page to match the current offset
    $scope.manageWorkers.page = 1;
    getAMTWorkers();
  });

  $scope.$watch('manageWorkers.page', function() {
    getAMTWorkers();
  });

  $scope.$watch('experiment', function(experiment) {
    if (experiment && experiment.hasOwnProperty('id') && experiment.id !== $scope.manageWorkers.experimentId) {
      $scope.manageWorkers.experimentId = experiment.id;
      $scope.manageWorkers.experimentUid = experiment.uid;
      getAMTWorkers();
    }
  });

  $scope.$watch('sandbox', function(sandbox) {
    if ($scope.manageWorkers.experimentId !== undefined && sandbox !== $scope.manageWorkers.sandbox) {
      $scope.manageWorkers.sandbox = sandbox;
      getAMTWorkers();
    }
  });

  function toggleSandbox() {
    AMTAdminSrv.setSandbox(!AMTAdminSrv.isSandbox());
    $scope.sandbox = AMTAdminSrv.isSandbox();
    getAccountBalance();
  }

  function getAccountBalance() {
    AMTAdminSrv.getAccountBalance().then(function(response) {
      $scope.accountBalance = parseFloat(response.data.availableBalance);
      //console.log('getAccountBalance', response);
    });
  }

  function getAMTWorkers() {
    $scope.manageWorkers.status = 1;
    if (! $scope.experiment) return;
    AMTAdminSrv.getAMTWorkers($scope.experiment.id, $scope.manageWorkers.limit, ($scope.manageWorkers.limit * ($scope.manageWorkers.page - 1)), $scope.manageWorkers.search.toUpperCase()).then(function(response) {
      console.log("getAMTWorkers", response);
      $scope.manageWorkers.status = 2;
      $scope.manageWorkers.total = response.data.total;
      $scope.manageWorkers.amtWorkers = response.data.amtWorkers;
      angular.forEach($scope.manageWorkers.amtWorkers, function(worker) {
        angular.forEach(worker.assignments, function(assignment) {
          assignment.completedSuccess = false;
          assignment.completedPending = false;
          assignment.completedError = null;
        });
      });
    },
    function(err) {
      $scope.manageWorkers.status = 3;
      $scope.manageWorkers.error = (err.data) ? err.data : err;
    });
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
}

AMTAdminCtrl.$inject = ['$scope', 'AMTAdminSrv', 'Upload'];

export default AMTAdminCtrl;
