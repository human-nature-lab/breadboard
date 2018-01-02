import './customize.app';
import '../directives/tab-status/tab-status.directive';
import templateUrl from './customize.template.html';
import CustomizeCtrl from './customize.controller';
angular.module('breadboard.customize').directive('customize', function(){
  return {
    restrict: 'E',
    scope: {
      experimentId: '=',
      actions: '='
    },
    link: function(scope){
      angular.extend(scope.actions, {
        saveCustomize: function(){
          scope.saveCustomize();
        }
      });
    },
    controllerAs: 'vm',
    controller: CustomizeCtrl,
    templateUrl: templateUrl
  }
});
