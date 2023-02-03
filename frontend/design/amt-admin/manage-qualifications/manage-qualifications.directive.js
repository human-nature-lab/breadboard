import templateUrl from './manage-qualifications.template.html';
import './manage-qualifications.app';
import ManageQualificationsCtrl from './manage-qualifications.controller';
angular.module('breadboard.amt-admin.manage-qualifications').directive('manageQualifications', function(){
  return {
    restrict: 'E',
    scope: {
      experimentId: '=',
      sandbox: '='
    },
    controller: ManageQualificationsCtrl,
    templateUrl: templateUrl
  }
});
