'use strict';

/* Directives */

angular.module('client.directives', []).
  directive('compile',function($compile, $timeout){
    return{
      restrict:'A',
      link: function(scope,elem,attrs){
        $timeout(function(){
          $compile(elem.contents())(scope);
					console.log("inside compile directive");
        });
      }
    };
});

