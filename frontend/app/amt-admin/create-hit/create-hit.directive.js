import templateUrl from './create-hit.template.html';
import './create-hit.app';
import CreateHitCtrl from './create-hit.controller';
import './select-qualifications/select-qualifications.directive';

angular.module('breadboard.amt-admin.create-hit').directive('createHit', function(){
  return {
    restrict: 'E',
    scope: {
      experiment: '=',
      experimentInstance: '=',
      sandbox: '=',
      onCreateHit: '&'
    },
    controller: CreateHitCtrl,
    templateUrl: templateUrl
  }
});
