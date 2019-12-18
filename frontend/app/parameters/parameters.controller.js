function ParametersCtrl($scope, ParametersSrv, $timeout) {
  let vm = this;

  vm.experiment = $scope.experiment;
  vm.readOnly = $scope.readOnly;
  vm.success = false;
  vm.error = false;
  vm.errorMessage = '';
  vm.experiment = $scope.experiment;
  vm.parameterMin = '';
  vm.parameterMax = '';
  vm.parameterDefaultInteger = '';
  vm.parameterDefaultDecimal = '';
  vm.parameterDefaultText = '';
  vm.parameterDefaultBoolean = true;
  vm.clearParameterFields = clearParameterFields;
  vm.paramType = paramType;
  vm.newParameter = newParameter;
  vm.removeParameter = removeParameter;

  function clearParameterFields() {
    vm.parameterMin = '';
    vm.parameterMax = '';
    vm.parameterDefaultInteger = '';
    vm.parameterDefaultDecimal = '';
    vm.parameterDefaultText = '';
  }

  function paramType(type) {
    if (type === 'Boolean') {
      return "checkbox";
    }
    if (type === 'Decimal' || type === 'Integer') {
      return "number";
    }
    return "text";
  }

  function newParameter() {
    let parameterDefault = '';
    if ($scope.parameterType === 'Integer') parameterDefault = vm.parameterDefaultInteger + '';
    if ($scope.parameterType === 'Decimal') parameterDefault = vm.parameterDefaultDecimal + '';
    if ($scope.parameterType === 'Boolean') parameterDefault = vm.parameterDefaultBoolean;
    if ($scope.parameterType === 'Text') parameterDefault = vm.parameterDefaultText;

    vm.error = false;
    vm.errorMessage = '';
    ParametersSrv.createParameter(vm.experiment.id, vm.parameterName, vm.parameterType, vm.parameterMin + '', vm.parameterMax + '', parameterDefault, vm.parameterDescription)
      .then(
        function(success){
          // Clear values
          clearParameterFields();
          vm.parameterName = '';
          vm.parameterType = '';
          vm.parameterDescription = '';
          vm.success = true;
          $timeout(function() {
            vm.success = false;
          }, 1500);
        },
        function(error){
          vm.error = true;
          vm.errorMessage = error.data;
        });
  }

  function removeParameter(parameterId) {
    vm.error = false;
    vm.errorMessage = '';
    ParametersSrv.removeParameter(parameterId)
      .then(
        function (success) {
          vm.success = true;
          $timeout(function () {
            vm.success = false;
          }, 1500);
        },
        function (error) {
          vm.error = true;
          vm.errorMessage = error.data;
        });
  }
}

ParametersCtrl.$inject = ['$scope', 'ParametersSrv', '$timeout'];

export default ParametersCtrl;
