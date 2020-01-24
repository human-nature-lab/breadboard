function ContentNameCtrl($scope, STATUS) {
  let vm = this;
  vm.STATUS = STATUS;
  vm.content = $scope.content;
}

ContentNameCtrl.$inject = ['$scope', 'STATUS'];

export default ContentNameCtrl;
