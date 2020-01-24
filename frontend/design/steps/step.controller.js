function StepCtrl($scope, STATUS, $timeout) {
  let vm = this;

  vm.protectedSteps = ["InitStep", "OnJoinStep", "OnLeaveStep"];

  vm.readOnly = $scope.readOnly;
  vm.step = $scope.step;
  vm.selectStep = $scope.selectStep();
  vm.deleteStep = $scope.deleteStep();
  vm.step.clientSource = vm.step.source;
  vm.step.status = STATUS.UNCHANGED;

  $scope.$watch('vm.step.clientSource', function(clientSource) {
    if (clientSource !== vm.step.source) {
      vm.step.status = STATUS.MODIFIED;
    } else {
      if (!(vm.step.status === STATUS.SAVED)) {
        vm.step.status = STATUS.UNCHANGED;
      }
    }
  });

}

StepCtrl.$inject = ['$scope', 'STATUS', '$timeout'];

export default StepCtrl;
