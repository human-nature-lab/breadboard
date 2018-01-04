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
import './timer/timer.directive';
import './client/client.sass';
import '../lib/angular/angular-bind-html-compile';

import _ from 'underscore';
window._ = _;

import ClientCtrl from './client/client.controller';
import ChoicesCtrl from './client/choices.controller';
const clientHtmlCacheLocation = 'holy.cow.client.html';
angular.module('breadboard.client', [
    'breadboard.services',
    'ngSanitize',
    'ngRoute',
    'ui.bootstrap',
    'client.filters',
    'client.directives',
    'breadboard.timer',
    'angular-bind-html-compile'
  ])
  .controller('ChoicesCtrl', ChoicesCtrl)
  .directive('app', ['configService', '$templateCache', function(configService, $templateCache){
    return {
      restrict: 'E',
      replace: true,
      link: function(scope){
        scope.isLoaded = false;
        configService.get('clientHtml').then(function(html){
          scope.isLoaded = true;
          $templateCache.put(clientHtmlCacheLocation, html);
        });
      },
      template: `<div class="main" ng-if="isLoaded">
                    <ng-include src="'${clientHtmlCacheLocation}'">
                        Loading...
                    </ng-include>
                  </div>`,
      controller: ClientCtrl,
    }
  }]);