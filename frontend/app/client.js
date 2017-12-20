import angular from 'angular';
import 'jquery';
import 'jquery-ui';
import 'underscore';
import 'angular-route';
import 'angular-sanitize';
import './client/client-directives';
import './client/client-filters';
import './timer/timer.directive';
import './services/services.module';
import './util/util';

import _ from 'underscore';
window._ = _;

import clientTemplateUrl from './client/client.template.html';

import ClientCtrl from './client/client.controller';
import ChoicesCtrl from './client/choices.controller';

angular.module('breadboard.client', [
    'breadboard.services',
    'ngSanitize',
    'ngRoute',
    'ui.bootstrap',
    'client.filters',
    'client.directives',
    // 'angular-bind-html-compile'
  ])
  .controller('ChoicesCtrl', ChoicesCtrl)
  .directive('app', function(){
    return {
      restrict: 'E',
      replace: true,
      templateUrl: clientTemplateUrl,
      controller: ClientCtrl,
    }
  });