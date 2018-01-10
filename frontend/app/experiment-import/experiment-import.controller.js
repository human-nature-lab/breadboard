ExperimentImportCtrl.$inject = ['$scope', 'Upload'];

export default function ExperimentImportCtrl($scope, Upload){

  $scope.import = {
    path: "/experiment/import",
    file: null,
    name: ""
  };

  $scope.importExperiment = function(){
    if(!$scope.import.file) return;
    Upload.upload({
      url: $scope.import.path,
      data: {
        file: $scope.import.file,
        name: $scope.import.name.length ? $scope.import.name : $scope.import.file.name
      }
    }).then(function(resp){
      if(resp.data === 'Error uploading'){
        alert("Unable to import the experiment. Please select a new experiment zip file");
      }
      $scope.import.file = null;
    }, function(err){
      console.error(err);
      $scope.import.file = null;
      $scope.import.name = "";
    }, function(evt){
      console.log("import upload progress", evt);
    });
  };

}