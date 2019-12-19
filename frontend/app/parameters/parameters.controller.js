function ParametersCtrl($scope, ParametersSrv, $timeout) {
  const vm = this;

  vm.readOnly = $scope.readOnly;
  vm.createdId = false;
  vm.removedId = false;
  vm.overDelete = false;
  vm.error = false;
  vm.errorMessage = '';
  vm.parameterType = '';
  vm.parameterMin = '';
  vm.parameterMax = '';
  vm.parameterDefaultInteger = '';
  vm.parameterDefaultDecimal = '';
  vm.parameterDefaultText = '';
  vm.parameterDefaultBoolean = true;
  vm.clearParameterFields = clearParameterFields;
  vm.paramType = paramType;
  vm.createParameter = createParameter;
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

  function createParameter() {
    let parameterDefault = '';
    if (vm.parameterType === 'Integer') parameterDefault = vm.parameterDefaultInteger + '';
    if (vm.parameterType === 'Decimal') parameterDefault = vm.parameterDefaultDecimal + '';
    if (vm.parameterType === 'Boolean') parameterDefault = vm.parameterDefaultBoolean;
    if (vm.parameterType === 'Text') parameterDefault = vm.parameterDefaultText;

    vm.error = false;
    vm.errorMessage = '';
    ParametersSrv.createParameter($scope.experiment.id, vm.parameterName, vm.parameterType, vm.parameterMin + '', vm.parameterMax + '', parameterDefault, vm.parameterDescription)
      .then(
        function(success){
          // Add the newly created parameter
          $scope.experiment.parameters.push(success.data.parameter);
          // Clear values
          clearParameterFields();
          vm.parameterName = '';
          vm.parameterType = '';
          vm.parameterDescription = '';
          vm.createdId = success.data.parameter.id;
          $timeout(function() {
            vm.createdId = false;
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
    ParametersSrv.removeParameter($scope.experiment.id, parameterId)
      .then(
        function (success) {
          vm.removedId = parameterId;
          $timeout(function () {
            vm.removedId = false;
            let parameterIndex = -1;
            for (let i = 0; i < $scope.experiment.parameters.length; i++) {
              if ($scope.experiment.parameters[i].id === parameterId) {
                parameterIndex = i;
                break;
              }
            }
            $scope.experiment.parameters.splice(parameterIndex, 1);
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
