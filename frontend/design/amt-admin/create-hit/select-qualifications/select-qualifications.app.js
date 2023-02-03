import './select-qualifications.service';
import './select-qualifications.directive';
import './select-qualifications.style.scss';
import 'jquery';
import 'bootstrap';
import '../../manage-qualifications/manage-qualifications.service'

angular.module('breadboard.amt-admin.create-hit.select-qualifications', [
  'breadboard.amt-admin.create-hit.select-qualifications.services',
  'breadboard.amt-admin.manage-qualifications.services'
]);
