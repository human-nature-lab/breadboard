import './steps.service';
import './steps.style.css';
import '../directives/tab-status/tab-status.directive';
import '../services/constants'
import 'jquery';
import 'bootstrap';
import '../services/services.module';


angular.module('breadboard.steps',
  [
    'breadboard.services',
    'breadboard.constants',
    'breadboard.steps.service',
    'breadboard.tab-status',
    'ui.codemirror'
  ]);
