import 'core-js'
import 'regenerator-runtime'
import { Breadboard } from './breadboard'
window.Breadboard = new Breadboard()
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
var app = angular.module('breadboard.client');

app.config(['$controllerProvider', '$provide', '$compileProvider',
  function( $controllerProvider, $provide, $compileProvider ) {
    // Since the "shorthand" methods for component
    // definitions are no longer valid, we can just
    // override them to use the providers for post-
    // bootstrap loading.
    // Let's keep the older references.
    app._controller = app.controller;
    app._service = app.service;
    app._factory = app.factory;
    app._value = app.value;
    app._directive = app.directive;
    // Provider-based controller.
    app.controller = function( name, constructor ) {
      $controllerProvider.register( name, constructor );
      return( this );
    };
    // Provider-based service.
    app.service = function( name, constructor ) {
      $provide.service( name, constructor );
      return( this );
    };
    // Provider-based factory.
    app.factory = function( name, factory ) {
      $provide.factory( name, factory );
      return( this );
    };
    // Provider-based value.
    app.value = function( name, value ) {
      $provide.value( name, value );
      return( this );
    };
    // Provider-based directive.
    app.directive = function( name, factory ) {
      $compileProvider.directive( name, factory );
      return( this );
    };
    // NOTE: You can do the same thing with the "filter"
    // and the "$filterProvider"; but, I don't really use
    // custom filters.
  }]);
