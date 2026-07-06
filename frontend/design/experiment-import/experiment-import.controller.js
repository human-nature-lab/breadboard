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
    doReplaceUpload(false);
  };

  // Actually POST the replace. `force` becomes the query param that tells the server to overwrite even
  // when the target has drifted since the archive was exported (see the 409 handling below).
  function doReplaceUpload(force){
    if (!$scope.replace.file) return;
    let experiment = $scope.currentExperiment;
    if (!experiment || !experiment.id) return;
    $scope.replace.targetName = experiment.name;
    // Keep a local handle on the file: the success path clears $scope.replace.file, but a force-retry
    // after a conflict still needs it.
    let file = $scope.replace.file;
    let url = $scope.import.path + `/${encodeURIComponent(experiment.name)}?experimentId=${experiment.id}`;
    if (force) {
      url += '&force=true';
    }
    Upload.upload({
      url: url,
      data: {
        file: file
      }
    }).then(function(resp){
      if (resp.status < 400) { //Success
        $scope.replace.file = null;
        $scope.replace.error = '';
        $scope.replace.success = true;
        // Re-select to reload the freshly synced experiment from the server.
        $scope.selectExperiment()(experiment.id);
        $timeout(function() {
          $scope.replace.success = false;
          $('#replaceExperimentDialog').dialog('close');
        }, 1500);
      } else {
        handleReplaceError(resp, file);
      }
    }, function(err){
      handleReplaceError(err, file);
    }, function(evt){
      console.log('replace upload progress', evt);
    });
  }

  // A 409 means the target experiment was modified since the file being imported was exported.
  // Confirm the overwrite with the user and, if they agree, retry with force=true. Any other error
  // falls through to the normal error display. (Depending on the Angular/ng-file-upload version a
  // non-2xx response can arrive here via either the success callback's else branch or the error
  // callback, so both route through this handler.)
  function handleReplaceError(resp, file){
    if (resp && resp.status === 409 && resp.data && resp.data.conflict) {
      let changed = resp.data.changed || [];
      let detail = changed.length ? '\n\nChanged files:\n' + changed.join('\n') : '';
      let message = (resp.data.message || 'This experiment has been modified since the file was exported.')
        + '\n\nOverwrite anyway?' + detail;
      if (window.confirm(message)) {
        $scope.replace.file = file; // ensure the file survives for the forced retry
        doReplaceUpload(true);
      }
      return;
    }
    replaceErrorOnUpload(resp);
  }

  function replaceErrorOnUpload(err) {
    console.error(err);
    $scope.replace.error = (err.data) ? err.data : err;
    $scope.replace.file = null;
  }

}