import './create-hit.service';
import './create-hit.directive';
import './create-hit.style.css';
import 'jquery';
import 'bootstrap';
import './select-qualifications/select-qualifications.directive'

angular.module('breadboard.amt-admin.create-hit', [
  'breadboard.amt-admin.create-hit.services',
  'breadboard.amt-admin.create-hit.select-qualifications'
]);
