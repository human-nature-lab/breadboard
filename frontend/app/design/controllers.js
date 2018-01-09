import _ from 'underscore';
import 'ng-file-upload';

/* Controllers */
angular.module('breadboard.controllers', ['ngFileUpload']).controller('AppCtrl', ['$scope', 'breadboardFactory', '$timeout', '$http', '$state', 'csvService', 'configService', 'Upload', 'downloadService',
function ($scope, $breadboardFactory, $timeout, $http, $state, csvService, configService, Upload, downloadService) {
  /*
  $scope.$watch('selectedLanguage', function(newValue) {
    console.log('selectedLanguage', newValue);
  });
  */
  $scope.image = {
    file: null,
    path: ''
  };

  configService.get('imageUploadRoute').then(imageUploadRoute => {
    $scope.image.path = imageUploadRoute;
  });

  $scope.uploadImage = function(){
    if(!$scope.image.file) return;
    Upload.upload({
      url: $scope.image.path,
      data: {
        picture: $scope.image.file,
        experimentId: $scope.breadboard.experiment.id
      }
    }).then(function(resp){
      if(resp.data === 'Error uploading'){
        alert("Unable to upload the photo. Please select a new photo.");
      } else {
        $scope.update();
      }
      $scope.image.file = null;
    }, function(err){
      console.error(err);
      $scope.image.file = null;
    }, function(evt){
      console.log("progress", evt);
    });
  };

  $breadboardFactory.onmessage(function (data) {
    try {
      if ($scope.breadboard == undefined) {
        $scope.breadboard = {};
      }

      $scope.breadboard = _.extend($scope.breadboard, data);

      console.log("$scope.breadboard", $scope.breadboard);

      if ($scope.breadboard.experiment != undefined) {
        // If there is style, apply it
        if ($scope.breadboard.experiment.style) {
          applyStyle();
        }

        if ($scope.breadboard.experiment.parameters != undefined) {
          // Set parameters in Launch dialog to default values
          /*
          for (var i = 0; i < $scope.breadboard.experiment.parameters.length; i++) {
            var parameter = $scope.breadboard.experiment.parameters[i];
            if (!$scope.launchParameters[parameter.name]) {
              $scope.launchParameters[parameter.name] = parameter.defaultVal;
            }
          }
          */
        }
        if ($scope.selectedLanguage === undefined) {
          // Setup default language
          $scope.selectedLanguage = $scope.breadboard.experiment.languages[0];
        } else {
          //console.log("$scope.selectedLanguage", $scope.selectedLanguage);
        }
        // Setup watch for selectedTranslation
        $scope.$watch('[selectedLanguage, selectedContent]', $scope.selectTranslation, true);
        /*
        if ($scope.breadboard.experiment.content != undefined && $scope.contentLanguages == undefined) {
          $scope.contentLanguages = [];
          $scope.selectedContentLanguages = {};
          $scope.contentObjects = {};
          $scope.selectedContentName = undefined;
          setupContentLanguages();
          $scope.$watch($scope.breadboard.experiment.content, setupContentLanguages, true);
        }
        */
      }
    }
    catch (e) {
      // TODO: add error object to scope and handle error client-side
      console.error("Parse error: " + e.toString());
    }
  });

  /* Store last experiment ID */
  $scope.lastExperimentId = -1;

  /* Script engine state */
  $scope.ENGINE_STATE = {
    'READY':0,
    'LOADING':1,
    'LOADED':2,
    'STALE':3
  };
  $scope.scriptEngineLastReloaded = 0;
  $scope.scriptEngineLastReloading = 0;
  $scope.scriptEngineState = $scope.ENGINE_STATE.READY;

  $scope.$watch('breadboard.notify', function(notify) {
    console.log('notify', notify);
    if (notify) {
      if (notify.hasOwnProperty('ScriptEngineReloaded') &&
        notify['ScriptEngineReloaded'] !== $scope.scriptEngineLastReloaded) {
        $scope.scriptEngineLastReloaded = notify['ScriptEngineReloaded'];
        $scope.scriptEngineState = $scope.ENGINE_STATE.LOADED;
        $timeout(function() {
          $scope.scriptEngineState = $scope.ENGINE_STATE.READY;
        }, 1500);
      }
      if (notify.hasOwnProperty('ScriptEngineReloading') &&
        notify['ScriptEngineReloading'] !== $scope.scriptEngineLastReloading) {
        $scope.scriptEngineLastReloading = notify['ScriptEngineReloading'];
        $scope.scriptEngineState = $scope.ENGINE_STATE.LOADING;
      }
    }
  });

  /* Graph here */
  $scope.nodes = [];
  $scope.selectedNode = {};
  $scope.breadboardGraph = new Graph(($(window).width() / 2), ($(window).width() / 2), $scope);

  // This links the 'Save' button of the Customize dialog with the customize directive
  $scope.customizeActions = {};
  function saveCustomize() {
    $scope.customizeActions.saveCustomize();
  }

  $scope.stepsActions = {
    'sendStep': sendStep,
    'onDeleteStep': onDeleteStep
  };
  function saveSteps() {
    $scope.stepsActions.saveSteps();
  }
  function onDeleteStep() {
    // If you delete a step, you need to reload the script engine
    $scope.scriptEngineState = $scope.ENGINE_STATE.STALE;
  }

  $breadboardFactory.addNodeChangeListener(function(nodes) {
      $scope.nodes = nodes;
  });

  $scope.selectNode = function(node) {
    for (let i = 0; i < $scope.nodes.length; i++) {
      $scope.nodes[i].selected = "0";
    }
    node.selected = "1";
    $scope.breadboardGraph.selectNode(node);
    $scope.selectedNode = node;
  };

  var applyStyle = function () {
    $('#styleTag').text($scope.breadboard.experiment.style);
  };

  /*
  function setupContentLanguages() {
    var contentLanguageSet = new Set();
    $scope.breadboard.experiment.content.forEach(function (content) {
      contentLanguageSet.add(content.language);
      // Default to the first language found as selected language for that content
      if (! ((content.name) in $scope.selectedContentLanguages)) {
        $scope.selectedContentLanguages[content.name] = content.language;
      }
      if (! ((content.name) in $scope.contentObjects)) {
        $scope.contentObjects[content.name] = {};
      }
      $scope.contentObjects[content.name][content.language] = content;
    });
    $scope.contentLanguages = Array.from(contentLanguageSet);
    $scope.selectedLanguage = $scope.contentLanguages[0];
    console.log("$scope.contentObjects", $scope.contentObjects);
    console.log("$scope.contentLanguages", $scope.contentLanguages);
    console.log("$scope.selectedContentLanguages", $scope.selectedContentLanguages);
  }
  */

  $scope.logout = function(){
    $http.get('/logout').then(function(res){
      // $state.go('login');
      // Remove this redirect once the application cleans up after itself correctly
      setTimeout(function(){
        window.location.reload();
      });
    });
  };

  $scope.paramType = function (type) {
    if (type === 'Boolean') {
      return "checkbox";
    }

    if (type === 'Decimal' || type === 'Integer') {
      return "number";
    }

    return "text";
  };

  $scope.clearParameterFields = function () {
      $scope.parameterMin = '';
      $scope.parameterMax = '';
      $scope.parameterDefaultInteger = '';
      $scope.parameterDefaultDecimal = '';
      $scope.parameterDefaultText = '';
  };

  $scope.newParameter = function () {
    var parameterDefault = '';
    if ($scope.parameterType === 'Integer') parameterDefault = $scope.parameterDefaultInteger + '';
    if ($scope.parameterType === 'Decimal') parameterDefault = $scope.parameterDefaultDecimal + '';
    if ($scope.parameterType === 'Boolean') parameterDefault = $scope.parameterDefaultBoolean;
    if ($scope.parameterType === 'Text') parameterDefault = $scope.parameterDefaultText;
    $breadboardFactory.send(
      {
        "action": "NewParameter",
        "name": $scope.parameterName,
        "type": $scope.parameterType,
        "minVal": $scope.parameterMin + '',
        "maxVal": $scope.parameterMax + '',
        "defaultVal": parameterDefault,
        "description": $scope.parameterDescription
      });
    // Clear values
    $scope.clearParameterFields();
    $scope.parameterName = '';
    $scope.parameterType = '';
    $scope.parameterDescription = '';
    // Set the default value
    //$scope.launchParameters[$scope.parameterName] = $scope.parameterDefault;
  };

  $scope.removeParameter = function (id) {
    $breadboardFactory.send(
      {
        "action": "RemoveParameter",
        "id": id
      });
  };

  $scope.playerProperties = function (n) {
    var ignoreProps = ["weight", "x", "y", "px", "fixed", "equals", "py", "text", "choices"];
    var playerProps = {};
    for (var propertyName in n) {
      if ($.inArray(propertyName, ignoreProps) == -1)
        playerProps[propertyName] = n[propertyName];
    }
    return playerProps;
  };

  $scope.getClientURL = function(experimentId, experimentInstanceId) {
    return window.location.protocol + "//" + window.location.hostname + ":" + window.location.port + "/game/" + experimentId + "/" + experimentInstanceId + "/login"
  };

  $scope.makeChoice = function (i) {
    $breadboardFactory.send(
      {
        "action": "MakeChoice",
        "choiceUID": $scope.selectedNode.choices[i].uid
      });
  };

  $scope.formatContent = function (content) {
    var returnContent = {};
    returnContent.languages = {};
    returnContent.contentObject = {};
    //console.log("content", content);
    for (var i = 0; i < content.length; i++) {
      var c = content[i];
      returnContent.languages[c.language] = true;

      if (!returnContent.contentObject.hasOwnProperty(c.name)) {
        returnContent.contentObject[c.name] = [];
      }

      var co = {};
      co[c.language] = c.html;
      returnContent.contentObject[c.name].push(co);
    }
    return returnContent;
  };

  var saveContent = function () {
    if ($scope.selectedContent != undefined) {
      //console.log("saveContent: ", $scope.selectedContent);
      $breadboardFactory.send(
        {
          "action": "SaveContent",
          "contentId": $scope.selectedContent.id,
          "name": $scope.selectedContent.name,
          "translations": $scope.selectedContent.translations
        });
    }
  };

  $scope.update = function () {
    //console.log("update");
    $breadboardFactory.send(
      {
        "action": "Update"
      });
  };

  $scope.experimentChanged = function () {
    if ($scope.lastExperimentId !== $scope.breadboard.experiment.id) {
      $breadboardFactory.send({
        "action": "SelectExperiment",
        "experimentId": $scope.breadboard.experiment.id
      });

      $scope.lastExperimentId = $scope.breadboard.experiment.id;
    }
  };

  $scope.newExperiment = function () {

    $("#newExperimentDialog input").each(function (index, element) {
      $(element).val("");
    });

    $('#newExperimentDialog').dialog({
      title: 'Create a new experiment',
      modal: true
    });
  };

  $scope.openNewInstanceModal = function(){

    $("#newExperimentInstanceDialog").dialog({
      title: 'Create an experiment instance',
      width: '600px',
      modal: 'true'
    });

  };

  $scope.launchParameters = {};

  $scope.launchGame = function () {
    $('#newExperimentInstanceDialog').dialog('close');
    $('#launchDiv').dialog('open');
    $('#graphDiv').dialog('open');
    $breadboardFactory.send(
      {
        "action": "LaunchGame",
        "name": $scope.experimentInstanceName,
        "parameters": $scope.launchParameters
      });
  };

  $scope.deleteExperiment = function () {
    var selectedExperiment = $scope.breadboard.user.selectedExperiment;
    $('#deleteExperimentDialog span.deleteExperimentName').html(selectedExperiment);
    $('#deleteExperimentDialog').dialog({
      title: 'Delete Experiment',
      buttons: {
        'Yes': function () {
          $breadboardFactory.send({"action": "DeleteExperiment", "selectedExperiment": selectedExperiment});
          $(this).dialog("close");
        },
        'No': function () {
          $(this).dialog("close");
        }
      }
    });
  };

  $scope.exportExperiment = function () {
    downloadService.download("/experiment/export/" + $scope.breadboard.experiment.id, $scope.breadboard.experiment.name + '.zip')
      .then(function(res){
        console.log("Successfully downloaded experiment");
      });
  };

  $scope.openImportDialog = function(){

    $('#importExperimentDialog').dialog({
      title: "Import Experiment"
    });

  };

  $scope.createExperiment = function () {
    $('#newExperimentDialog').dialog('close');
    $breadboardFactory.send(
      {
        "action": "CreateExperiment",
        "name": $scope.newExperimentName,
        "copyExperimentName": $scope.copyExperimentName
      });
  };

  $scope.deleteImage = function (imageId) {
    $breadboardFactory.send({
      "action": "DeleteImage",
      "imageId": imageId
    });
  };

  $scope.showUserSettings = function () {
    var currentPasswordField = $('.user-setting-section input#currentPassword');
    var newPasswordField = $('.user-setting-section input#newPassword');
    var confirmPasswordField = $('.user-setting-section input#confirmNewPassword');
    var changePasswordError = $('.user-setting-section ul#changePasswordError');
    $('#userSettingsDialog').dialog({
      modal: true,
      minWidth: 360,
      beforeClose: function (event, ui) {
        resetFields();
      },
      buttons: [
        {
          id: "saveUserSettings",
          text: "Save",
          click: function () {
            $('#saveUserSettings, #cancelUserSettings').prop('disabled', true).addClass('ui-state-disabled');
            var currentPassword = $.trim(currentPasswordField.val());
            var newPassword = $.trim(newPasswordField.val());
            var confirmPassword = $.trim(confirmPasswordField.val());

//TODO: add more js validation such as regex
            if (currentPassword.length == 0) {
              changePasswordError.append("<li>'Current Password' is required</li>");
              changePasswordError.show();
            }
            if (newPassword.length == 0) {
              changePasswordError.append("<li>'New Password' is required</li>");
              changePasswordError.show();
            }
            if (confirmPassword != newPassword) {
              changePasswordError.append("<li>'Confirm Password' doesn't match the new password</li>");
              changePasswordError.show();
            }

            $.post("/saveUserSettings", {
              "email": $scope.breadboard.user.email, "currentPassword": currentPassword,
              "newPassword": newPassword, "confirmPassword": confirmPassword
            }, function (data) {
              resetFields();
              if (data.success) {
                $('#userSettingsDialog').dialog("close");
              } else if (data.error) {
                changePasswordError.append("<li>" + data.error + "</li>").show();
              }

            });
          }
        },
        {
          id: "cancelUserSettings",
          text: "Cancel",
          click: function () {
            $(this).dialog("close");
          }
        }
      ]
    });

    function resetFields() {
      changePasswordError.html('');
      currentPasswordField.val('');
      newPasswordField.val('');
      confirmPasswordField.val('');
      changePasswordError.hide();
      $('#saveUserSettings, #cancelUserSettings').prop('disabled', false).removeClass('ui-state-disabled');
    }
  };

  $scope.selectInstance = function (id) {
    $breadboardFactory.send(
      {
        "action": "SelectInstance",
        "id": id
      });
  };

  $scope.deleteInstance = function (name, id) {
    $('#deleteInstanceDialogDesc').text(name);
    $('#deleteInstanceDialog').dialog({
      modal: true,
      minWidth: 360,
      buttons: [
        {
          text: "Delete",
          click: function () {
            $breadboardFactory.send({"action": "DeleteInstance", "id": id});
            $(this).dialog("close");
          }
        },
        {
          text: "Cancel",
          click: function () {
            $(this).dialog("close");
          }
        }
      ]
    });
  };

  var clearScript = function () {
    $scope.breadboard.user.currentScript = '';
    $scope.$apply();
  };

  var sendScript = function () {
    //console.log("sendScript sending: " + $scope.breadboard.user.currentScript);
    var script = $scope.breadboard.user.currentScript;
    if (window.getSelection && window.getSelection().toString().length > 0) {
      script = window.getSelection().toString();
    }
    $breadboardFactory.send(
      {
        "action": "SendScript",
        "script": script
      });
  };

  $scope.run = function () {
    $breadboardFactory.send({
      "action": "RunGame"
    });
  };

  $scope.reload = function () {
    $scope.scriptEngineState = $scope.ENGINE_STATE.LOADING;
    $breadboardFactory.send({
      "action": "ReloadEngine"
    });
  };

  function sendStep(step) {
    $breadboardFactory.send({
      'action': 'SendStep',
      'id': step.id,
      'name': step.name,
      'source': step.source
    });
  }

  /*
  var sendStep = function () {
    $breadboardFactory.send(
      {
        "action": "SendStep",
        "id": $scope.selectedStep.id,
        "name": $scope.selectedStep.name,
        "source": $scope.selectedStep.source
      });
  };
  */

  $scope.vim = {vimMode: false};

  $scope.$watch('vim', function () {
    $scope.stepCodemirrorOptions.vimMode = $scope.vim.vimMode;
    //$scope.cssCodemirrorOptions.vimMode = $scope.vim.vimMode;
    $scope.scriptCodemirrorOptions.vimMode = $scope.vim.vimMode;
    //$scope.clientHtmlCodemirrorOptions.vimMode = $scope.vim.vimMode;
    //$scope.clientGraphCodemirrorOptions.vimMode = $scope.vim.vimMode;
  }, true);

  $scope.toggleVim = function () {
    $scope.vim.vimMode = !$scope.vim.vimMode;
  };

  var toggleVim = function () {
    var scope = angular.element($("#mainDiv")).scope();
    scope.$apply(function () {
      $scope.vim.vimMode = !$scope.vim.vimMode;
    });
  };

  $scope.initStep = function (step) {
    //this is for capturing the newly created step and select it
    if ($scope.newStepName !== undefined) {
      if (step.name === $scope.newStepName) {
        $scope.selectedStep = step;
      }
    }
  };

  $scope.deleteStep = function (step) {

    $('#deleteStepDesc').html("Are you sure that you want to permanently delete the step '" + step.name + "'?");

    $('#deleteStepDialog').dialog({
      title: 'Delete Step',
      buttons: {
        'Yes': function () {
          $breadboardFactory.send({"action": "DeleteStep", "id": step.id});
          $(this).dialog("close");
          $scope.selectedStep.source = '';
        },
        'No': function () {
          $(this).dialog("close");
        }
      }
    });
  };

  /*
  $scope.selectedContentLanguages = {};
  $scope.selectedContentName = undefined;
  $scope.selectedContent = undefined;
  */

  $scope.addLanguage = function() {
    // Reset the language dialog
    $("#addLanguageDialog input").each(function (index, element) {
      $(element).val("");
    });

    $('#addLanguageDialog').dialog({
      title: 'Add new language',
      buttons: {
        'Submit': function () {
          var experimentId = $scope.breadboard.experiment.id;
          //console.log("scope.newLanguage", $scope.newLanguageCode);
          //console.log("experimentId", experimentId);

          $breadboardFactory.send({
            "action": "AddLanguage",
            "experimentId": experimentId,
            "code": $scope.newLanguageCode
          });

          $(this).dialog("close");
        }
      }
    });
  };

  /*
  $scope.selectContentLanguage = function (contentName, selectedLanguage, thisSelect) {
    console.log("contentName", contentName);
    console.log("selectedLanguage", selectedLanguage);
    console.log("thisSelect", thisSelect);

    $scope.selectedContentName = contentName;
    $scope.selectedContent = $scope.breadboard.experiment.content[contentName][selectedLanguage];
    $scope.selectedContentLanguages[contentName] = selectedLanguage;
    $timeout(resizeTiny, 10);
  };
  */

  $scope.selectContentName = function (contentName) {
    $scope.selectedContentName = contentName;
    /*
    if ($scope.selectedContentLanguages[contentName] !== undefined) {
      $scope.selectedContent = $scope.breadboard.experiment.content[contentName][$scope.selectedContentLanguages[contentName]];
    }
    */
    $timeout(resizeTiny, 10);
  };

  $scope.selectContent = function (content) {
    $scope.selectedContent = content;
    /*
     if ($scope.selectedContentLanguages[contentName] !== undefined) {
     $scope.selectedContent = $scope.breadboard.experiment.content[contentName][$scope.selectedContentLanguages[contentName]];
     }
     */
    $timeout(resizeTiny, 10);
  };

  $scope.selectTranslation = function() {
    if ($scope.selectedLanguage !== undefined && $scope.selectedContent !== undefined) {
      $scope.selectedTranslation = undefined;
      angular.forEach($scope.selectedContent.translations, function(translation) {
        if (translation.language.id == $scope.selectedLanguage.id) {
          $scope.selectedTranslation = translation;
        }
      });
      if ($scope.selectedTranslation == undefined) {
        // No translation for the selected language
        var length = $scope.selectedContent.translations.push(
          {
            'id' : null,
            'html' : "<p>No translation found.</p>",
            'language' : $scope.selectedLanguage
          }
        );
        $scope.selectedTranslation = $scope.selectedContent.translations[(length - 1)];
      }
    }
  };

  $scope.deleteContent = function (content) {

    $('#deleteStepDesc').html("Are you sure that you want to permanently delete the content '" + content.name + "'?");

    $('#deleteStepDialog').dialog({
      title: 'Delete Content',
      buttons: {
        'Yes': function () {
          $breadboardFactory.send({"action": "DeleteContent", "id": content.id});
          if ($scope.selectedContent.id == content.id) {
            $scope.selectedContent = undefined;
          }
          $(this).dialog("close");
        },
        'No': function () {
          $(this).dialog("close");
        }
      }
    });
  };

  $scope.newStep = function () {
    $("#newStepDialog input").each(function (index, element) {
      $(element).val("");
    });
    $('#newStepDialog').dialog({title: 'Create New Step'});
  };

  $scope.createStep = function () {
    $('#newStepDialog').dialog('close');
    $breadboardFactory.send({"action": "CreateStep", "name": $scope.newStepName});
  };

  $scope.newContent = function () {
    $("#newContentDialog input").each(function (index, element) {
      $(element).val("");
    });

    $('#newContentDialog').dialog({title: 'Create New Content'});
  };

  $scope.createContent = function () {
    $('#newContentDialog').dialog('close');
    $breadboardFactory.send({
      "action": "CreateContent",
      "name": $scope.newContentName,
      "language": $scope.newContentLanguage
    });
  };

  $scope.stopGame = function (id) {
    $breadboardFactory.send(
      {
        "action": "StopGame",
        "id": id
        //$scope.breadboard.experimentInstance.id
      });
  };

  $scope.downloadEventCsv = function (experimentInstance) {
    csvService.getInstanceData(experimentInstance.id)
      .then(function(success) {
          let filename = experimentInstance.name + '.csv';
          let blob = new Blob([success.data], {type: 'text/csv'});

          if (window.navigator && window.navigator.msSaveOrOpenBlob) {
            window.navigator.msSaveOrOpenBlob(blob, filename);
          } else {
            let e = document.createEvent('MouseEvents'),
              a = document.createElement('a');

            a.download = filename;
            a.href = window.URL.createObjectURL(blob);
            a.dataset.downloadurl = ['text/csv', a.download, a.href].join(':');
            e.initEvent('click', true, false, window,
              0, 0, 0, 0, 0, false, false, false, false, 0, null);
            a.dispatchEvent(e);
          }
        },
        function(error) {
          console.error(error.data);
        });
  };

  $scope.downloadExperimentCsv = function(experiment) {
    //console.log('experiment', experiment);
    csvService.getExperimentInstances(experiment.id)
      .then(function(success) {
          let filename = experiment.name + '.csv';
          let blob = new Blob([success.data], {type: 'text/csv'});

          if (window.navigator && window.navigator.msSaveOrOpenBlob) {
            window.navigator.msSaveOrOpenBlob(blob, filename);
          } else {
            let e = document.createEvent('MouseEvents'),
                a = document.createElement('a');

            a.download = filename;
            a.href = window.URL.createObjectURL(blob);
            a.dataset.downloadurl = ['text/csv', a.download, a.href].join(':');
            e.initEvent('click', true, false, window,
              0, 0, 0, 0, 0, false, false, false, false, 0, null);
            a.dispatchEvent(e);
          }
      },
      function(error) {
        console.error(error.data);
      });
  };

  var topDivHeight = 45,
    bottomDivHeight = 55,
    margin = 5,
    windowHeight = ($(window).innerHeight() - topDivHeight - bottomDivHeight),
    windowWidth = ($(window).innerWidth() - (margin * 2));

  $scope.amtAdminDialogOptions = {
    title: 'AMT',
    autoOpen: false,
    width: windowWidth,
    height: windowHeight - 9, // Not sure why the AMT dialog is 9 pixels higher than the others...
    position: [margin, topDivHeight],
    buttons: {}
  };

  $scope.outputDialogOptions = {
    title: 'Output',
    autoOpen: true,
    width: ((windowWidth * .5) - margin),
    height: ((windowHeight * .25) - margin),
    position: [margin, (topDivHeight + margin + (.75 * windowHeight))],
    buttons: {}
  };

  $scope.scriptDialogOptions = {
    title: 'Script',
    autoOpen: true,
    width: ((windowWidth * .5) - margin),
    height: ((windowHeight * .75) - margin),
    position: [margin, topDivHeight],
    buttons: [
      {
        text: 'Execute Script',
        click: function () {
          sendScript();
        }
      }
    ]
  };

  $scope.contentDialogOptions = {
    title: 'Content',
    width: ((windowWidth * .5) - margin),
    height: windowHeight,
    position: [margin, topDivHeight],
    autoOpen: false,
    buttons: {
      'Save': function () {
        saveContent();
      }
    },
    dialogClass: 'contentDialog'
  };

  $scope.stepsDialogOptions = {
    title: 'Steps',
    width: windowWidth,
    height: windowHeight,
    position: [margin, topDivHeight],
    autoOpen: false,
    dialogClass: 'steps-dialog',
    buttons: {
      'Save': function () {
        saveSteps();
      }
    }
  };

  $scope.customizeDialogOptions = {
    title: 'Customize',
    autoOpen: false,
    width: ((windowWidth * .5) - margin),
    height: windowHeight,
    position: [margin, topDivHeight],
    buttons: {
      'Save': function () {
        saveCustomize();
      }
    },
  };

  $scope.graphDialogOptions = {
    title: 'Graph',
    autoOpen: true,
    width: (windowWidth * .5),
    height: windowHeight,
    position: [((windowWidth * .5) + margin), topDivHeight],
    buttons: {}
  };

  $scope.imagesDialogOptions = {
    title: 'Images',
    width: (windowWidth * .5),
    height: windowHeight,
    position: [((windowWidth * .5) + margin), topDivHeight],
    autoOpen: false,
    buttons: {}
  };

  $scope.launchDialogOptions = {
    title: 'Experiment Instances',
    autoOpen: false,
    width: windowWidth,
    height: windowHeight,
    position: [margin, topDivHeight],
    buttons: {}
  };

  $scope.playerDialogOptions = {
    title: 'Players',
    autoOpen: false,
    width: ((windowWidth * .5) - margin),
    height: windowHeight,
    position: [margin, topDivHeight],
    buttons: {}
  };

  $scope.parametersDialogOptions = {
    title: 'Parameters',
    autoOpen: false,
    width: (windowWidth * .5),
    height: windowHeight,
    position: [((windowWidth * .5) + margin), topDivHeight],
    buttons: {}
  };

  $scope.stepCodemirrorOptions = {
    lineNumbers: true,
    matchBrackets: true,
    mode: 'text/x-groovy',
    extraKeys: {
      "Ctrl-Enter": sendStep
    },
    vimMode: false,
    showCursorWhenSelecting: true
  };

  $scope.scriptCodemirrorOptions = {
    lineNumbers: true,
    matchBrackets: true,
    mode: 'text/x-groovy',
    extraKeys: {
      "Ctrl-Enter": sendScript
    },
    vimMode: false,
    showCursorWhenSelecting: true
  };

  $scope.tinymceOptions = {
    theme: 'modern',
    width: '100%',
    height: '100%',
    plugins: "spellchecker image code lists link charmap textcolor",
    toolbar: "styleselect | bullist numlist outdent indent | forecolor backcolor | cut copy paste | undo redo | link unlink | charmap | spellchecker | code | image",
    statusbar: false,
    menubar: false,
    convert_urls: false,
    content_css: '/assets/css/tinymce.css',
    valid_elements: '*[*]',
    resize: true
  };

  $scope.$on('$destroy', function(){
    // Destroy all of the popup windows.
    $("[ui-jq=\"dialog\"]").dialog('destroy');

    // TODO: Disconnect from websockets and other cleanup so that the application can restart without redirect
  });

}]);