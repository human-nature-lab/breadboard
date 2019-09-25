function CreateHitCtrl($scope, CreateHitSrv) {
  $scope.disallowPrevious = 'type';
  $scope.tutorialTime = 300;
  $scope.lifetime = 300;
  $scope.assignmentDuration = 5400;
  $scope.keywords = '';
  $scope.maxAssignments = 20;
  $scope.reward = 1;
  $scope.description = '';
  $scope.title = '';
  $scope.autoLaunch = true;
  $scope.status = 0; // 0: Show form, 1: Submitting, 2: Successful, 3: Error
  $scope.error = '';
  $scope.qualificationRequirements = [];

  $scope.createHIT = createHIT;

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

  function createHIT() {
    $scope.status = 1;
    console.log('qualificationRequirements', $scope.qualificationRequirements);
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
        },
        function(error) {
          $scope.status = 3;
          $scope.error = error.data;
        });
  }
}

CreateHitCtrl.$inject = ['$scope', 'CreateHitSrv'];

export default CreateHitCtrl;
