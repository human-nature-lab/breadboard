import './content.app';
import templateUrl from './content-status.template.html';
import ContentStatusCtrl from './content-status.controller';

angular.module('breadboard.content').directive('contentStatus', function(){
  return {
    restrict: 'E',
    replace: true,
    scope: {
      content: '=',
      selectedContent:'=',
      selectContent:'&',
      deleteContent:'&'
    },
    controllerAs: 'vm',
    controller: ContentStatusCtrl,
    templateUrl: templateUrl
  }
});


