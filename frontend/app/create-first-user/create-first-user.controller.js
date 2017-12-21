function CreateFirstUserCtrl($scope, languageService, CreateFirstUserSrv) {
  $scope.vm = {
    languages:  [],
    defaultLanguage: '',
    email: '',
    password: '',
    confirmPassword: '',
    hasError: false,
    errorMessage: ''
  };

  languageService.all().then(function(response) {
    $scope.vm.languages = response.languages;
  },
  function(error) {
    $scope.vm.hasError = true;
    $scope.vm.errorMessage = error.data;
  });

  $scope.submit = function() {
    if ($scope.vm.password !== $scope.vm.confirmPassword) {
      $scope.vm.hasError = true;
      $scope.vm.errorMessage = 'Password and confirm password do not match.';
    } else {
      CreateFirstUserSrv.createFirstUser($scope.vm.email, $scope.vm.password, $scope.vm.defaultLanguage.iso3)
        .then(function(res) {
          console.log('res', res);
          if(res.status === 200) {
            if ($scope.onSuccess) {
              $scope.onSuccess({res: res});
            }
          } else {
            $scope.vm.hasError = true;
            $scope.vm.errorMessage = res.data;
            if ($scope.onError) {
              $scope.onError({err: res.data});
            }
          }
        },
        function(error) {
          console.log('error', error);
          $scope.vm.hasError = true;
          $scope.vm.errorMessage = error.data;
          if ($scope.onError) {
            $scope.onError({err: error.data});
          }
        });
    }
  }
}

CreateFirstUserCtrl.$inject = ['$scope', 'languageService', 'CreateFirstUserSrv'];

export default CreateFirstUserCtrl;

