import './tab-status.app';
import '../../services/constants';
import tabStatusTemplate from './tab-status.template.html';
angular.module('breadboard.tab-status')
  .directive('tabStatus', function(){
    return {
      restrict: 'E',
      scope: {
        status: '='
      },
      controller: TabStatusCtrl,
      templateUrl: tabStatusTemplate
    }
});

function TabStatusCtrl($scope, STATUS) {
  $scope.STATUS = STATUS;
}

TabStatusCtrl.$inject = ['$scope', 'STATUS'];
