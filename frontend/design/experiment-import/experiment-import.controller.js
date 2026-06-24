ExperimentImportCtrl.$inject = ['$scope', 'Upload', '$timeout'];

export default function ExperimentImportCtrl($scope, Upload, $timeout){

  $scope.import = {
    path: '/experiment/import',
    file: null,
    name: '',
    importedName: '',
    error: '',
    success: false,
    selectExperiment: true
  };

  $scope.$watch('import.file.name', function() {
    if ($scope.import.file !== null && $scope.import.name === "") {
      let fileName = $scope.import.file.name;
      let indexOfZip = fileName.indexOf('.zip');
      fileName = fileName.substring(0, indexOfZip);
      $scope.import.name = fileName;
    }
  }, true);

  $scope.importExperiment = function(){
    if(!$scope.import.file) return;
    let name = $scope.import.name.length ? $scope.import.name : $scope.import.file.name;
    name = name.replace('.zip', '');
    Upload.upload({
      url: $scope.import.path + `/${name}`,
      data: {
        file: $scope.import.file
      }
    }).then(function(resp){
      if (resp.status < 400) { //Success
        $scope.import.file = null;
        $scope.import.importedName = $scope.import.name;
        $scope.import.name = '';
        $scope.import.success = true;
        if ($scope.import.selectExperiment) {
          $scope.selectExperiment()(resp.data.id);
        }
        $timeout(function() {
          $scope.import.importedName = '';
          $scope.import.success = false;
          $('#importExperimentDialog').dialog('close');
        }, 1500);
      } else {
        errorOnUpload(resp);
      }
    }, function(err){
      errorOnUpload(err)
    }, function(evt){
      console.log('import upload progress', evt);
    });
  };

  function errorOnUpload(err) {
    console.error(err);
    $scope.import.error = (err.data) ? err.data : err;
    $scope.import.file = null;
    $scope.import.name = '';
  }

  // Replace (import over) the currently selected experiment. The dialog is opened from the toolbar
  // via openReplaceDialog(); it reads the target from the currentExperiment binding.
  $scope.replace = {
    file: null,
    error: '',
    success: false,
    targetName: ''
  };

  $scope.replaceExperiment = function(){
    if (!$scope.replace.file) return;
    let experiment = $scope.currentExperiment;
    if (!experiment || !experiment.id) return;
    $scope.replace.targetName = experiment.name;
    Upload.upload({
      url: $scope.import.path + `/${encodeURIComponent(experiment.name)}?experimentId=${experiment.id}`,
      data: {
        file: $scope.replace.file
      }
    }).then(function(resp){
      if (resp.status < 400) { //Success
        $scope.replace.file = null;
        $scope.replace.success = true;
        // Re-select to reload the freshly synced experiment from the server.
        $scope.selectExperiment()(experiment.id);
        $timeout(function() {
          $scope.replace.success = false;
          $('#replaceExperimentDialog').dialog('close');
        }, 1500);
      } else {
        replaceErrorOnUpload(resp);
      }
    }, function(err){
      replaceErrorOnUpload(err)
    }, function(evt){
      console.log('replace upload progress', evt);
    });
  };

  function replaceErrorOnUpload(err) {
    console.error(err);
    $scope.replace.error = (err.data) ? err.data : err;
    $scope.replace.file = null;
  }

}