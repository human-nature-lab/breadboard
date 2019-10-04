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
      controller: ['$scope', '$state', '$cookieStore', function($scope, $state, $cookieStore){
        $scope.path = '/login';
        $scope.onSuccess = function(res){
          $cookieStore.put('email', res.data.email);
          $cookieStore.put('juid', res.data.juid);
          $cookieStore.put('uid', res.data.uid);
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
