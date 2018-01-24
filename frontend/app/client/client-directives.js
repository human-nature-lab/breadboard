'use strict';

/* Directives */

angular.module('client.directives', [])
  .directive('compile',['$compile', '$timeout', function($compile, $timeout){
    return{
      restrict:'A',
      link: function(scope,elem,attrs){
        $timeout(function(){
          $compile(elem.contents())(scope);
					//console.log("inside compile directive");
        });
      }
    };
}])
  .directive('clientText', ['$sce', '$location', function($sce, $location){
    return{
      restrict:'E',
      replace:true,
      templateUrl: '/assets/templates/client-text.html',
      link: function (scope, element, attrs) {
        //var language = $routeParams.language;
        //console.log("language", $location.search().language);
        //scope.selectedLanguage = $location.search().language;
        //console.log("routeParams", $routeParams.language);
        scope.translatedText = "";
        scope.textObject = scope.$eval(attrs.textObject);
        scope.$watchCollection('[selectedLanguage,textObject]', function(value){
          //console.log('value[1]', value[1]);
          if (value[1] !== undefined) {
            try {
              var textObject = JSON.parse(value[1]);
              //console.log("scope.textObject", textObject);
              for (var i = 0; i < textObject.contentArray.length; i++) {
                var content = textObject.contentArray[i];
                // Add the language to the languages array if it doesn't already exist
                if (scope.languages.indexOf(content.language) < 0) {
                  scope.languages.push(content.language);
                }

                if (scope.selectedLanguage == content.language) {
                  scope.translatedText = content.text;
                  scope.trustedTranslatedText = $sce.trustAsHtml(scope.translatedText);
                }
              }
            } catch (e) {
              scope.translatedText = value[1];
              scope.trustedTranslatedText = $sce.trustAsHtml(scope.translatedText);
            }
          }
        });
      }
    }
  }])
  .directive('clientButton', ['$sce', '$location', function($sce, $location) {
    return {
      restrict: 'E',
      replace: true,
      controller:ClientButtonController,
      templateUrl: '/assets/templates/client-button.html',
      link: function (scope, element, attrs) {
        scope.$watch(attrs.buttonTextObject, function(value){
          scope.buttonTextObject = JSON.parse(value);
          //console.log("scope.buttonTextObject", scope.buttonTextObject);
          for (var i = 0; i < scope.textObject.contentArray.length; i++) {
            if (scope.selectedLanguage == scope.buttonTextObject.contentArray[i].language) {
              scope.translatedButtonText = scope.buttonTextObject.contentArray[i].text;
              scope.trustedTranslatedButtonText = $sce.trustAsHtml(scope.translatedButtonText);
            }
          }

        });

      }
    }
  }]);

ClientButtonController.$inject = ['$scope', '$location', '$sce'];

function ClientButtonController($scope, $location, $sce) {
  $scope.changeLanguage = changeLanguage;
  function changeLanguage(language) {
    $scope.selectedLanguage = language;
    $location.search('language', language);
    for (var i = 0; i < $scope.textObject.contentArray.length; i++) {
      if ($scope.selectedLanguage == $scope.textObject.contentArray[i].language) {
        $scope.translatedText = $scope.textObject.contentArray[i].text;
        $scope.trustedTranslatedText = $sce.trustAsHtml($scope.translatedText);
      }
    }
  }
}

ClientTextController.$inject = ['$scope', '$location', '$sce'];

function ClientTextController($scope, $location, $sce) {
  $scope.changeLanguage = changeLanguage;
  function changeLanguage(language) {
    $scope.selectedLanguage = language;
    $location.search('language', language);
    for (var i = 0; i < $scope.textObject.contentArray.length; i++) {
      if ($scope.selectedLanguage == $scope.textObject.contentArray[i].language) {
        $scope.translatedText = $scope.textObject.contentArray[i].text;
        $scope.trustedTranslatedText = $sce.trustAsHtml($scope.translatedText);
      }
    }
  }
}

