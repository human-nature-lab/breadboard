function CreateFirstUserCtrl($scope, languageService) {
  let languages = [];
  languageService.all().then(function(response) {
    console.log("languageService.all()", response);
  },
  function(error) {
    console.error("Something went wrong", error);
  });
}

CreateFirstUserCtrl.$inject = ['$scope', 'languageService'];

export default CreateFirstUserCtrl;

