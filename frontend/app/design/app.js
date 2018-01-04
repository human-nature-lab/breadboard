import './graph';
import './controllers';
import './breadboard';
import './directives';
import './filters';
import '../services/services.module';
import './routes';
import uiJqConfig from '../providers/ui-jq-config.value';
import '../amt-admin/amt-admin.directive';
import '../create-new-experiment/create-new-experiment.directive';
import '../customize/customize.directive';
import '../steps/steps.directive';
import '../directives/instance-parameters/instance-parameters.directive';
import '../create-first-user/create-first-user.directive';

angular.module('breadboard', [
  'breadboard.filters',
  'breadboard.services',
  'breadboard.directives',
  'breadboard.controllers',
  'breadboard.login',
  'breadboard.routes',
  'breadboard.amt-admin',
  'breadboard.customize',
  'breadboard.create-first-user',
  'breadboard.create-new-experiment',
  'breadboard.instance-parameters',
  'breadboard.steps',
  'ui.utils',
  'ui.codemirror',
  'ui.tinymce',
  'ui.bootstrap',
  'ngSanitize',
  'ngCookies'])
.value('uiJqConfig', uiJqConfig);