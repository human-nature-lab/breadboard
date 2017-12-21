import angular from 'angular';
import uiJqConfig from './ui-jq-config.value';

export default angular.module('breadboard.providers', [])
  .value('uiJqConfig', uiJqConfig);