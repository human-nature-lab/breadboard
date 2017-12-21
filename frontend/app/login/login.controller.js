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

  function defaultErrorHandler(err){
    $scope.hasError = true;
    $scope.errorMessage = err;
  }

  $scope.submit = function(){
    $http.post($scope.path, $scope.vm)
      .then(function(res){
        if(res.status === 200) {
          $scope.onSuccess({res: res});
        } else {
            defaultErrorHandler(res.data.message);
            $scope.onError({err: res.data.message});
        }
      }, function(err){
        console.error(err);
        defaultErrorHandler(err);
        $scope.onError({err});
      });
  }
}]);