import './graph';
import './controllers';
import './breadboard';
import './directives';
import './filters';
import './services';
import './routes';
import '../amt-admin/amt-admin.directive';
import '../create-first-user/create-first-user.directive';

angular.module('breadboard', [
  'breadboard.filters',
  'breadboard.services',
  'breadboard.directives',
  'breadboard.controllers',
  'breadboard.login',
  'breadboard.routes',
  'breadboard.amt-admin',
  'breadboard.create-first-user',
  'ui.utils',
  'ui.codemirror',
  'ui.tinymce',
  'ngSanitize',
  'ngCookies']);