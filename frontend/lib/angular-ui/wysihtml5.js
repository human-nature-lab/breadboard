'use strict';

angular.module('breadboard.directives', []).
    directive('graph', function() {

demoApp.directive('richTextEditor', function( $log, $location) {
        var self = this;
        var directive = {
                
                restrict : "A",
                replace : true,
                transclude : true,
                scope: {
                   
                },             
                template : 

                        "<div>" +

                                "<textarea id=\"richtexteditor-content\" style=\"height:300px;width:70%\" ></textarea>"+

                        "</div>",

                link : function( $scope, $element, $attrs, ngModel ) {
                        $scope.editor = $('#richtexteditor-content').wysihtml5();

                        
                        
                     $scope.$parent.$watch( $attrs.content, function( newValue, oldValue ) {
                                
                                $scope.editor.innerHTML = newValue;
                                $scope.editor.composer.setValue( newValue );
                            alert($scope.editor.innerHTML)
                     });
                        
                        $scope.cancel = function() {
                                $scope.$parent.cancel();
                        }

                        $scope.isClean = function() {
                                $scope.$parent.isClean();
                        }
                }
        }
        return directive;
});
            
