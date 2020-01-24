function CreateFirstUserCtrl($scope, languageService, CreateFirstUserSrv, $state) {
  $scope.vm = {
    languages:  [],
    defaultLanguage: {},
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
    console.log($scope.vm.defaultLanguage);
    if ($scope.vm.password !== $scope.vm.confirmPassword) {
      $scope.vm.hasError = true;
      $scope.vm.errorMessage = 'Password and confirm password do not match.';
    } else {
      CreateFirstUserSrv.createFirstUser($scope.vm.email, $scope.vm.password, $scope.vm.defaultLanguage.id)
        .then(function(res) {
          if(res.status === 200) {
            $state.go('login');
          } else {
            $scope.vm.hasError = true;
            $scope.vm.errorMessage = res.data;
          }
        },
        function(error) {
          $scope.vm.hasError = true;
          $scope.vm.errorMessage = error.data;
        });
    }
  }
}

CreateFirstUserCtrl.$inject = ['$scope', 'languageService', 'CreateFirstUserSrv', '$state'];

export default CreateFirstUserCtrl;

