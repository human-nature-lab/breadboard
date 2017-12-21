import './graph';
import './controllers';
import './breadboard';
import './directives';
import './filters';
import '../services/services.module';
import './routes';
import uiJqConfig from '../providers/ui-jq-config.value';
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
  'ngCookies'])
.value('uiJqConfig', uiJqConfig);