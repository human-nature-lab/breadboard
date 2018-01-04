function StepNameCtrl($scope) {
  let vm = this;
  vm.step = $scope.step;
}

StepNameCtrl.$inject = ['$scope'];

export default StepNameCtrl;
