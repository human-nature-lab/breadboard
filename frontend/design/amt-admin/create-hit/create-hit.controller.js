function CreateHitCtrl($scope, CreateHitSrv) {
  setDefaultValues();
  $scope.status = 0; // 0: Show form, 1: Submitting, 2: Successful, 3: Error
  $scope.error = '';

  $scope.createHIT = createHIT;
  $scope.clearForm = clearForm;

  $scope.$watch('experimentInstance', function(experimentInstance, oldExperimentInstance) {
    if (!experimentInstance) {
      $scope.status = 0;
    } else {
      if (experimentInstance.hits && experimentInstance.hits.length > 0) {
        $scope.status = 2;
      } else {
        if ((!oldExperimentInstance) || experimentInstance.id !== oldExperimentInstance.id) {
          // New experiment instance
          $scope.status = 0;
        }
      }
    }
  }, true);

  function setDefaultValues() {
    $scope.disallowPrevious = (window.localStorage.getItem('createHitDisallowPrevious')) ? window.localStorage.getItem('createHitDisallowPrevious') : 'type';
    $scope.tutorialTime = (window.localStorage.getItem('createHitTutorialTime')) ? Number(window.localStorage.getItem('createHitTutorialTime')) : 300;
    $scope.lifetime = (window.localStorage.getItem('createHitLifetime')) ? Number(window.localStorage.getItem('createHitLifetime')) : 300;
    $scope.assignmentDuration = (window.localStorage.getItem('createHitAssignmentDuration')) ? Number(window.localStorage.getItem('createHitAssignmentDuration')) : 5400;
    $scope.keywords = (window.localStorage.getItem('createHitKeywords')) ? window.localStorage.getItem('createHitKeywords') : '';
    $scope.maxAssignments = (window.localStorage.getItem('createHitMaxAssignments')) ? Number(window.localStorage.getItem('createHitMaxAssignments')) : 20;
    $scope.reward = (window.localStorage.getItem('createHitReward')) ? Number(window.localStorage.getItem('createHitReward')) : 1;
    $scope.description = (window.localStorage.getItem('createHitDescription')) ? window.localStorage.getItem('createHitDescription') : '';
    $scope.title = (window.localStorage.getItem('createHitTitle')) ? window.localStorage.getItem('createHitTitle') : '';
    $scope.autoLaunch = (window.localStorage.getItem('createHitAutoLaunch')) ? (window.localStorage.getItem('createHitAutoLaunch') === 'true') : true;
    $scope.qualificationRequirements = (window.localStorage.getItem('createHitQualificationRequirements')) ? JSON.parse(window.localStorage.getItem('createHitQualificationRequirements')) : [];
  }

  function createHIT() {
    if ($scope.sandbox || confirm("Are you sure you want to post this HIT to AMT?")) {
      $scope.status = 1;
      CreateHitSrv.createHIT(
        $scope.title,
        $scope.description,
        $scope.reward,
        $scope.maxAssignments,
        $scope.lifetime,
        $scope.tutorialTime,
        $scope.assignmentDuration,
        $scope.keywords,
        $scope.disallowPrevious,
        $scope.experiment.id,
        $scope.experimentInstance.id,
        $scope.qualificationRequirements,
        $scope.sandbox)
        .then(function (amtHit) {
            $scope.status = 2;
            if ($scope.autoLaunch) {
              $scope.onCreateHit()($scope.lifetime, $scope.tutorialTime);
            } else {
              $scope.experimentInstance.hits.push(amtHit.data);
            }
            saveFormToLocalStorage();
          },
          function(error) {
            $scope.status = 3;
            $scope.error = error.data;
          });
    }
  }

  function saveFormToLocalStorage() {
    window.localStorage.setItem('createHitTitle', $scope.title);
    window.localStorage.setItem('createHitDescription', $scope.description);
    window.localStorage.setItem('createHitReward', $scope.reward);
    window.localStorage.setItem('createHitMaxAssignments', $scope.maxAssignments);
    window.localStorage.setItem('createHitLifetime', $scope.lifetime);
    window.localStorage.setItem('createHitTutorialTime', $scope.tutorialTime);
    window.localStorage.setItem('createHitAssignmentDuration', $scope.assignmentDuration);
    window.localStorage.setItem('createHitKeywords', $scope.keywords);
    window.localStorage.setItem('createHitDisallowPrevious', $scope.disallowPrevious);
    window.localStorage.setItem('createHitQualificationRequirements', JSON.stringify($scope.qualificationRequirements));
    window.localStorage.setItem('createHitAutoLaunch', $scope.autoLaunch);
  }

  function clearForm() {
    window.localStorage.removeItem('createHitTitle');
    window.localStorage.removeItem('createHitDescription');
    window.localStorage.removeItem('createHitReward');
    window.localStorage.removeItem('createHitMaxAssignments');
    window.localStorage.removeItem('createHitLifetime');
    window.localStorage.removeItem('createHitTutorialTime');
    window.localStorage.removeItem('createHitAssignmentDuration');
    window.localStorage.removeItem('createHitKeywords');
    window.localStorage.removeItem('createHitDisallowPrevious');
    window.localStorage.removeItem('createHitQualificationRequirements');
    window.localStorage.removeItem('createHitAutoLaunch');
    setDefaultValues();
  }
}


CreateHitCtrl.$inject = ['$scope', 'CreateHitSrv'];

export default CreateHitCtrl;
