import templateUrl from './amt-admin.template.html';
import './amt-admin.app';
import AmtAdminCtrl from './amt-admin.controller';
import './manage-qualifications/manage-qualifications.directive';

angular.module('breadboard.amt-admin').directive('amtAdmin', function(){
  return {
    restrict: 'E',
    scope: {
      experiment: '=',
      experimentInstance: '=',
      onCreateHit: '&'
    },
    controller: AmtAdminCtrl,
    templateUrl: templateUrl
  }
});