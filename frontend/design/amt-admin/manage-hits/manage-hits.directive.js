import templateUrl from './manage-hits.template.html';
import './manage-hits.app';
import ManageHitsCtrl from './manage-hits.controller';
angular.module('breadboard.amt-admin.manage-hits').directive('manageHits', function(){
  return {
    restrict: 'E',
    scope: {
      experimentId: '=',
      sandbox: '='
    },
    controller: ManageHitsCtrl,
    templateUrl: templateUrl
  }
});
