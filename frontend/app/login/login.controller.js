export default angular.module('breadboard.login', []).controller("LoginCtrl", ['$scope', '$http', function($scope, $http){
  if(!$scope.path){
    throw Error("Must include the path to submit to");
  }
  if(!$scope.onSuccess){
    throw Error("Must include the onSuccess callback");
  }
  $scope.hasError = false;
  $scope.vm = {
    username: '',
    password: ''
  };

  $scope.submit = function(){
    $http.post($scope.path, $scope.vm)
      .then(function(res){
        $scope.onSuccess({res: res});
      }, function(err){
        console.error(err);
        if($scope.onError) {
          $scope.onError({err: err});
        } else {
          $scope.hasError = true;
          $scope.errorMessage = "Unable to login at this time";
        }
      });
  }
}]);