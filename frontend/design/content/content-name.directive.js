import './content.app';
import templateUrl from './content-name.template.html';
import ContentNameCtrl from './content-name.controller';

angular.module('breadboard.content')
  .directive('contentName', ['$timeout', function($timeout){
    return {
      restrict: 'E',
      replace: true,
      scope: {
        content: '='
      },
      link: function(scope, element) {
        $timeout(function() {
          element[0].focus();
          element[0].select();
        });

        element.bind("keydown keypress", function(event) {
          if(event.which === 13) {
            element[0].blur();
            event.preventDefault();
          }
        });
      },
      controllerAs: 'vm',
      controller: ContentNameCtrl,
      templateUrl: templateUrl
    }
  }]);


