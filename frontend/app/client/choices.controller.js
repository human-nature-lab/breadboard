ChoicesCtrl.$inject = ['$scope', 'clientFactory']
export default function ChoicesCtrl($scope, $clientFactory) {
  $scope.custom = undefined;

  $scope.makeChoice = function (uid) {
    //console.log("makeChoice");
    $scope.params= {};
    if (angular.element('.param')) {
      angular.forEach(angular.element(".param"), function(value, key){
        //console.log("value", value);
        var param = angular.element(value);
        if (param.attr("name")) {
          if (param.attr("type") == "radio") {
            if (param.is(":checked")) {
              $scope.params[param.attr("name")] = param.val();
            }
          } else if (param.attr("type") == "checkbox") {
            if (! $scope.params.hasOwnProperty(param.attr("name"))) {
              $scope.params[param.attr("name")] = new Array();
            }
            if (param.is(":checked")) {
              $scope.params[param.attr("name")].push(param.val());
            }
          } else {
            //console.log(param.attr("name"), param.val());
            $scope.params[param.attr("name")] = param.val();
          }
        } else {
          //console.log("! param.attr(name)");
        }
      });
    }
    var sendData = {
      "action": "MakeChoice",
      "choiceUID": uid,
      "params": ($scope.params == undefined) ? "{}" : JSON.stringify($scope.params)
    };
    //console.log(sendData);
    //$scope.params = {};
    $clientFactory.send(sendData);
  };

  $scope.$watch('client.player.choices', function (newValue) {
    if (newValue && newValue[0] && newValue[0].custom) {
      $scope.custom = newValue[0].custom;
    } else {
      $scope.custom = undefined;
    }
    $scope.childChoices = newValue;
  }, true);
}