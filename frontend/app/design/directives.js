'use strict';

/* Directives */

angular.module('breadboard.directives', []).
	directive('jqButton', function() {
		return {
			link: function (scope, elem, attr) {
				elem.button();

				scope.$watch(attr.jqButtonDisabled, function (value) {
					elem.attr('disabled', value);
					if (value) {
						elem.button('disable');
					} else {
						elem.button('enable');
					}
				});
			}
		}
    }).
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


/** TODO **
 *
 angular.module('breadboard.directives', []).
 directive("parameterType", function() {
		return function(scope, element, attrs) {
			return "Hello me.";
		}
	});
	directive('parameterValidate', function() {
		return {
			require: 'ngModel',
			link: function(scope, elm, attrs, ctrl) {
				ctrl.$parsers.unshift(function(viewValue) {

					scope.parameterValid = (true ? 'valid' : undefined);

					if(scope.parameterValid) {
						ctrl.$setValidity('parameterValidate', true);
						return viewValue;
					} else {
						ctrl.$setValidity('parameterValidate', false);                    
						return undefined;
					}
				});
			}
		};
	}).
*/
