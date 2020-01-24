import './create-first-user.service';
import './create-first-user.directive';
import '../services/services.module';
import 'jquery';
import 'bootstrap';

angular.module('breadboard.create-first-user', ['breadboard.services', 'breadboard.create-first-user.service']);
