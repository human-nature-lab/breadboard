function ContentStatusCtrl($scope, STATUS) {
  let vm = this;

  vm.readOnly = $scope.readOnly;
  vm.content = $scope.content;
  vm.selectContent = $scope.selectContent();
  vm.deleteContent = $scope.deleteContent();
  vm.content.status = (vm.content.html === vm.content.clientHtml) ? STATUS.UNCHANGED : STATUS.MODIFIED;

  $scope.$watch('vm.content.translations', function(translations) {
    let changed = false;
    angular.forEach(translations, function(translation) {
      if (translation.html !== translation.clientHtml) {
        changed = true;
      }
    });

    if (changed && !vm.readOnly) {
      vm.content.status = STATUS.MODIFIED;
    } else {
      if (!(vm.content.status === STATUS.SAVED)) {
        vm.content.status = STATUS.UNCHANGED;
      }
    }
  }, true);

}

ContentStatusCtrl.$inject = ['$scope', 'STATUS'];

export default ContentStatusCtrl;
