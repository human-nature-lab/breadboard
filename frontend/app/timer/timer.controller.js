TimerCtrl.$inject = ['$scope', '$interval', '$filter'];
export default function TimerCtrl($scope, $interval, $filter){
  let updateIntervalPromise = null;
  let updateIntervalSpeed = 500;
  $scope.isHidden = false;
  $scope.val = 100;
  $scope.time = 0; // The relevant time in seconds
  // $scope.timer.direction = $scope.timer.direction || 'down';
  // $scope.timer.type = $scope.timer.timerType || 'time';
  // $scope.timer.timerText = $scope.timer.timerText || '';

  if(typeof $scope.timer.currencyAmount === 'string'){
    $scope.timer.currencyAmount = parseFloat($scope.timer.currencyAmount, 10);
  }

  // A single update to the time value. Changes depending on direction
  function update(){
    $scope.timer.elapsed += updateIntervalSpeed;
    if($scope.timer.direction === 'down') {
      $scope.val = (($scope.timer.duration - $scope.timer.elapsed) / $scope.timer.duration) * 100;
      $scope.time = Math.round(($scope.timer.duration - $scope.timer.elapsed)/1000);
    } else {
      $scope.val = ($scope.timer.elapsed / $scope.timer.duration) * 100;
      $scope.time = Math.round($scope.timer.elapsed/1000);
    }
    updateMessage();
    if($scope.timer.elapsed >= $scope.timer.duration){
      stop();
    }
  }

  // Updates the message depending on the timer type. Based on $scope.time
  function updateMessage(){
    $scope.message = $scope.timer.timerText;
    if($scope.timer.type === 'currency') {
      $scope.message += $filter('currency')($scope.timer.currencyAmount * $scope.val / 100 / 100, '$');
    } else if($scope.timer.type === 'percent'){
      $scope.message += Math.round($scope.val) + '%';
    } else {
      $scope.message += ($scope.time * 1000).toString().toHHMMSS();
    }
  }

  update();
  start();

  // Start updating
  function start(){
    if(updateIntervalPromise === null){
      updateIntervalPromise = $interval(update, updateIntervalSpeed);
    }
  }

  // Stop updating
  function stop(){
    if(updateIntervalPromise !== null){
      $interval.cancel(updateIntervalPromise);
      updateIntervalPromise = null;
      $scope.isHidden = true;
    }
  }

  $scope.$on("$destroy", function(){
    stop();
  });

};