import './amt-admin.service';
import './amt-admin.directive';
import 'jquery';
import 'bootstrap';
import './amt-admin.style.scss';
import './manage-qualifications/manage-qualifications.directive';
import './create-hit/create-hit.directive';

angular.module('breadboard.amt-admin', [
  'breadboard.amt-admin.services',
  'ui.bootstrap.pagination',
  'ngFileUpload',
  'breadboard.amt-admin.manage-qualifications',
  'breadboard.amt-admin.create-hit'
]);
