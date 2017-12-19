import loginTemplateUrl from '../templates/login.html';
import homeTemplateUrl from '../templates/home.html';
import '../login/login.directive';
import './middleware';

angular.module('breadboard.routes', ['ui.router', 'breadboard.middleware', 'ngCookies'])
  .config(['$stateProvider', '$urlRouterProvider', '$httpProvider', function($stateProvider, $urlRouterProvider, $httpProvider){
  $urlRouterProvider.otherwise('/');
  $stateProvider
    .state('login', {
      url: '/login',
      templateUrl: loginTemplateUrl,
      controller: ['$scope', '$state', '$cookies', function($scope, $state, $cookies){
        $scope.path = '/login';
        $scope.onSuccess = function(res){
          $cookies.JAVASCRIPT_SESSION = 'email=' + res.data.email + ';juid=' + res.data.juid + ';uid=' + res.data.uid;
          $state.go('home');
        };
      }]
    })
    .state('home', {
      url: '/',
      controller: 'AppCtrl',
      templateUrl: homeTemplateUrl
    });

    $httpProvider.interceptors.push('AuthorizationMiddleware');

}]);