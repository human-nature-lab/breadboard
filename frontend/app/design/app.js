import './graph';
import './controllers';
import './breadboard';
import './directives';
import './filters';
import './services';
import './routes';

angular.module('breadboard', [
  'breadboard.filters',
  'breadboard.services',
  'breadboard.directives',
  'breadboard.controllers',
  'breadboard.login',
  'breadboard.routes',
  'ui.utils',
  'ui.codemirror',
  'ui.tinymce',
  'ngSanitize',
  'ngCookies']);