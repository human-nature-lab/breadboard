import './content.app';
import './content-status.directive';
import './content-name.directive';
import '../directives/tab-status/tab-status.directive';
import templateUrl from './content.template.html';
import ContentCtrl from './content.controller';
angular.module('breadboard.content').directive('content', function(){
  return {
    restrict: 'E',
    scope: {
      experimentId: '=',
      experimentLanguages: '=',
      experiment: '=',
      actions: '=',
      readOnly: '='
    },
    link: function(scope){
      angular.extend(scope.actions, {
        saveContent: function(){
          scope.saveContent();
        }
      });
    },
    controllerAs: 'vm',
    controller: ContentCtrl,
    templateUrl: templateUrl
  }
});
