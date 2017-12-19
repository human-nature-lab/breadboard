angular
  .module('breadboard.middleware', [], ['$provide', function($provide){
    $provide.factory('AuthorizationMiddleware', ['$q', '$injector', function($q, $injector){

      function response(res){
        console.log('res interceptor', res);
        return res;
      }

      function responseError(res){
        if(res.status === 401){
          console.log("Need to login");
          var $state = $injector.get('$state');
          return $state.go('login');
        }
        return res;
      }

      return {
        response : response,
        responseError : responseError
      };
    }])
  }]);