import templateUrl from './select-qualifications.template.html';
import './select-qualifications.app';
import SelectQualifictionsCtrl from './select-qualifications.controller';
angular.module('breadboard.amt-admin.create-hit.select-qualifications').directive('selectQualifications', function(){
  return {
    restrict: 'E',
    scope: {
      sandbox: '=',
      qualificationRequirements: '='
    },
    controller: SelectQualifictionsCtrl,
    templateUrl: templateUrl
  }
});
