import './content.service';
import './content.style.css';
import '../directives/tab-status/tab-status.directive';
import '../services/constants'
import 'jquery';
import 'bootstrap';
import '../services/services.module';

angular.module('breadboard.content',
  [
    'breadboard.services',
    'breadboard.constants',
    'breadboard.content.service',
    'breadboard.tab-status',
    'ui.codemirror'
  ]);
