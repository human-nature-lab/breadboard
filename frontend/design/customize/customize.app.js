import './customize.service';
import './customize.directive';
import '../directives/tab-status/tab-status.directive';
import '../services/constants'
import 'jquery';
import 'bootstrap';
import '../services/services.module';
import './customize.style.css';

angular.module('breadboard.customize',
  [
    'breadboard.services',
    'breadboard.constants',
    'breadboard.customize.service',
    'breadboard.tab-status',
    'ui.codemirror'
  ]);
