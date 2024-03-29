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
import '../parameters/parameters.directive';
import '../images/images.directive';
import '../steps/steps.directive';
import '../testing/testing.directive';
import '../content/content.directive';
import '../directives/instance-parameters/instance-parameters.directive';
import '../create-first-user/create-first-user.directive';
import './design.sass';
import '../experiment-import/experiment-import.app';
import '../experiment-import/experiment-import.directive';

angular.module('breadboard', [
  'breadboard.filters',
  'breadboard.services',
  'breadboard.directives',
  'breadboard.controllers',
  'breadboard.login',
  'breadboard.routes',
  'breadboard.amt-admin',
  'breadboard.customize',
  'breadboard.parameters',
  'breadboard.images',
  'breadboard.create-first-user',
  'breadboard.create-new-experiment',
  'breadboard.instance-parameters',
  'breadboard.steps',
  'breadboard.testing',
  'breadboard.content',
  'breadboard.experiment-import',
  'ui.utils',
  'ui.codemirror',
  'ui.tinymce',
  'ui.bootstrap',
  'ngSanitize',
  'ngCookies'])
.value('uiJqConfig', uiJqConfig);