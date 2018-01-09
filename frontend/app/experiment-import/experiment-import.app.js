import angular from 'angular';
import 'ng-file-upload';
import '../services/services.module';

angular.module('breadboard.experiment-import', [
  'ngFileUpload',
  'breadboard.services',
]);

