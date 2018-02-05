TimerCtrl.$inject = ['$scope', '$interval', '$filter'];
export default function TimerCtrl($scope, $interval, $filter){
  $scope.isHidden = false;
  $scope.val = 100;
  $scope.time = 0; // The relevant time in seconds

  if(typeof $scope.timer.currencyAmount === 'string'){
    $scope.timer.currencyAmount = parseFloat($scope.timer.currencyAmount, 10);
  }

  $scope.$watch('timer', update);
  // A single update to the time value. Changes depending on direction
  function update(timer){
    //$scope.timer.elapsed += updateIntervalSpeed;
    if(timer.direction === 'down') {
      $scope.val = ((timer.duration - timer.elapsed) / timer.duration) * 100;
      $scope.time = Math.round((timer.duration - timer.elapsed)/1000);
    } else {
      $scope.val = (timer.elapsed / timer.duration) * 100;
      $scope.time = Math.round(timer.elapsed/1000);
    }
    updateMessage();
    if(timer.elapsed >= timer.duration){
      stop();
    }
  }

  // Updates the message depending on the timer type. Based on $scope.time
  function updateMessage(){
    $scope.message = $scope.timer.timerText + " ";
    if($scope.timer.type === 'currency') {
      $scope.message += $filter('currency')($scope.timer.currencyAmount * $scope.val / 100 / 100, '$');
    } else if($scope.timer.type === 'percent'){
      $scope.message += Math.round($scope.val) + '%';
    } else {
      $scope.message += ($scope.time * 1000).toString().toHHMMSS();
    }
  }

  // Stop updating
  function stop(){
    $scope.isHidden = true;
  }

  $scope.$on("$destroy", function(){
    stop();
  });

};