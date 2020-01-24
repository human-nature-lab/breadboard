import './images.app';
import templateUrl from './images.template.html';
import ImagesCtrl from './images.controller';
angular.module('breadboard.images').directive('images', function() {
  return {
    restrict: 'E',
    scope: {
      experiment: '=',
      readOnly: '='
    },
    controllerAs: 'vm',
    controller: ImagesCtrl,
    templateUrl: templateUrl
  }
});