function CreateNewExperimentCtrl($scope, CreateNewExperimentSrv, $timeout) {
  let vm = this;
  vm.createNewExperiment = createNewExperiment;
  vm.newExperimentName = '';
  vm.copyExperimentId = -1;
  vm.error = '';
  vm.success = false;

  function createNewExperiment() {
    CreateNewExperimentSrv.createNewExperiment(vm.newExperimentName, vm.copyExperimentId)
      .then(function(success) {
        let newExperiment = success.data;
        $scope.userExperiments.push(newExperiment);
        vm.success = true;
        $timeout(function() {
          $('#newExperimentDialog').dialog('close');
        }, 1500);
      }, function(error) {
        vm.error = error.data;
      });
  }
}

CreateNewExperimentCtrl.$inject = ['$scope', 'CreateNewExperimentSrv', '$timeout'];

export default CreateNewExperimentCtrl;
