import loginTemplateUrl from '../templates/login.html';
import createFirstUserTemplateUrl from '../templates/create-first-user.html';
import homeTemplateUrl from '../templates/home.html';
import '../login/login.directive';
import '../create-first-user/create-first-user.directive';
import '../middleware/Authorization.middleware';
import '../services/services.module';

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
          // $cookieStore was removed in angular-cookies 1.6+; $cookies.putObject/getObject
          // is the drop-in replacement (same JSON encoding, so existing cookies still decode).
          $cookies.putObject('email', res.data.email);
          $cookies.putObject('juid', res.data.juid);
          $cookies.putObject('uid', res.data.uid);
          window.Breadboard.disconnect()
          $state.go('home');
        };
      }]
    })
    .state('home', {
      url: '/',
      controller: 'AppCtrl',
      templateUrl: homeTemplateUrl,
      onExit: function(){
        // remove this hack once we're free of jquery ui dialogs
        //window.location.reload();
      }
    })
    .state('create-first-user', {
      url: '/create-first-user',
      templateUrl: createFirstUserTemplateUrl
    });

    $httpProvider.interceptors.push('AuthorizationMiddleware');

}]);
