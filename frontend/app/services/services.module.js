import angular from 'angular';
import ScriptInjector from './script-injector.service';
import WebsocketFactory from './websocket.factory';
import ClientGraphService from './client-graph.service';
import ClientFactory from './client.factory';
import ConfigService from './config.service';

export default angular.module('breadboard.services', [])
  .service('configService', ConfigService)
  .service('websocketFactory', WebsocketFactory)
  .service('clientGraph', ClientGraphService)
  .service('scriptInjector', ScriptInjector)
  .service('clientFactory', ClientFactory)