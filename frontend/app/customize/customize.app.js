import './customize.service';
import './customize.directive';
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
    'ui.codemirror'
  ]);
