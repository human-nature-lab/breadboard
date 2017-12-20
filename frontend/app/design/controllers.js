import _ from 'underscore';

/* Controllers */
angular.module('breadboard.controllers', []).controller('AppCtrl', ['$scope', 'breadboardFactory', '$timeout', '$http', '$state',
function ($scope, $breadboardFactory, $timeout, $http, $state) {
  $scope.$watch('selectedLanguage', function(newValue) {
    console.log('selectedLanguage', newValue);
  });

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
          for (var i = 0; i < $scope.breadboard.experiment.parameters.length; i++) {
            var parameter = $scope.breadboard.experiment.parameters[i];
            if (!$scope.launchParameters[parameter.name]) {
              $scope.launchParameters[parameter.name] = parameter.defaultVal;
            }
          }
        }
        if ($scope.selectedLanguage === undefined) {
          // Setup default language
          $scope.selectedLanguage = $scope.breadboard.experiment.languages[0];
        } else {
          console.log("$scope.selectedLanguage", $scope.selectedLanguage);
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
      console.log("Parse error: " + e.toString());
    }
  });

  /* Graph here */
  $scope.selectedNode = {};
  $scope.breadboardGraph = new Graph(($(window).width() / 2), ($(window).width() / 2), $scope);

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
      $state.go('login');
    });
  };

  $scope.paramType = function (type) {
    if (type == 'Boolean') {
      return "checkbox";
    }

    if (type == 'Decimal' || type == 'Integer') {
      return "number";
    }

    return "text";
  };

  $scope.newParameter = function () {
    $breadboardFactory.send(
      {
        "action": "NewParameter",
        "name": $scope.parameterName,
        "type": $scope.parameterType,
        "minVal": $scope.parameterMin,
        "maxVal": $scope.parameterMax,
        "defaultVal": $scope.parameterDefault,
        "description": $scope.parameterDescription
      });
    // Set the default value
    $scope.launchParameters[$scope.parameterName] = $scope.parameterDefault;
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
    var playerProps = "";
    for (var propertyName in n) {
      if ($.inArray(propertyName, ignoreProps) == -1)
        playerProps += propertyName + ": " + n[propertyName] + "\n";
    }
    return playerProps;
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
    console.log("content", content);
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
      console.log("saveContent: ", $scope.selectedContent);
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

  var applyStyle = function () {
    $('#styleTag').text($scope.breadboard.experiment.style);
  };

  var saveStyle = function () {
    applyStyle();
    $breadboardFactory.send(
      {
        "action": "SaveStyle",
        "style": $scope.breadboard.experiment.style
      });
  };

  var saveClientHtml = function () {
    $breadboardFactory.send(
      {
        "action": "SaveClientHtml",
        "clientHtml": $scope.breadboard.experiment.clientHtml
      });
  };

  var saveClientGraph = function () {
    $breadboardFactory.send(
      {
        "action": "SaveClientGraph",
        "clientGraph": $scope.breadboard.experiment.clientGraph
      });
  };

  $scope.experimentChanged = function () {
    $breadboardFactory.send(
      {
        "action": "SelectExperiment",
        "experiment": $scope.breadboard.user.selectedExperiment
      });
  };

  $scope.newExperiment = function () {

    $("#newExperimentDialog input").each(function (index, element) {
      $(element).val("");
    });

    $('#newExperimentDialog').dialog({title: 'Create New Experiment'});
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
    var selectedExperiment = $scope.breadboard.user.selectedExperiment;
    $breadboardFactory.send(
      {
        "action": "ExportExperiment",
        "selectedExperiment": selectedExperiment
      });
  };

  $scope.openImportDialog = function () {
    $("#importExperimentDialog input").each(function (index, element) {
      $(element).val("");
    });

    $('#importExperimentDialog').dialog({title: 'Import Experiment'});
  };

  $scope.importExperiment = function () {
    $('#importExperimentDialog').dialog('close');
    $breadboardFactory.send(
      {
        "action": "ImportExperiment",
        "importFrom": $scope.importFrom,
        "importTo": $scope.importTo
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

  $scope.submitAMTTask = function (lifetimeInSeconds, tutorialTime) {
    console.log('submitAMTTask', lifetimeInSeconds, tutorialTime);
    $breadboardFactory.send({
        "action": "SubmitAMTTask",
        "lifetimeInSeconds": lifetimeInSeconds,
        "tutorialTime": tutorialTime
      });
  };

  $scope.getAssignments = function (id) {
    $scope.selectedHIT = id;
    $breadboardFactory.send(
      {
        "action": "GetAssignmentsForHIT",
        "amtHitId": id
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
    //console.log("run()");
    $breadboardFactory.send(
      {
        "action": "RunGame"
      });
  };

  $scope.reload = function () {
    //console.log("run()");
    $breadboardFactory.send(
      {
        "action": "ReloadEngine"
      });
  };

  var sendStep = function () {
    //console.log("sendScript sending: " + $scope.breadboard.user.currentScript);
    $breadboardFactory.send(
      {
        "action": "SendStep",
        "id": $scope.selectedStep.id,
        "name": $scope.selectedStep.name,
        "source": $scope.selectedStep.source
      });
  };

  $scope.vim = {vimMode: false};

  $scope.$watch('selectedNode', function(old, newVal){

  });

  $scope.$watch('vim', function () {
    $scope.stepCodemirrorOptions.vimMode = $scope.vim.vimMode;
    $scope.cssCodemirrorOptions.vimMode = $scope.vim.vimMode;
    $scope.scriptCodemirrorOptions.vimMode = $scope.vim.vimMode;
    $scope.clientHtmlCodemirrorOptions.vimMode = $scope.vim.vimMode;
    $scope.clientGraphCodemirrorOptions.vimMode = $scope.vim.vimMode;
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
          console.log("scope.newLanguage", $scope.newLanguageCode);
          console.log("experimentId", experimentId);

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

  $scope.launchParameters = {};

  $scope.launchGame = function () {
    //console.log($scope.launchParameters);

    $breadboardFactory.send(
      {
        "action": "LaunchGame",
        "name": $scope.experimentInstanceName,
        "parameters": $scope.launchParameters
      });
  };

  $scope.testGame = function () {
    //console.log($scope.launchParameters);

    $breadboardFactory.send(
      {
        "action": "TestGame",
        "name": $scope.experimentInstanceName,
        "parameters": $scope.launchParameters
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

  $scope.showEvent = function (experimentInstance) {
    $scope.showEvent.experimentInstance = experimentInstance;
    $breadboardFactory.send(
      {
        "action": "ShowEvent",
        "id": experimentInstance.id
      });
    $('#eventDataDialog').dialog({title: 'Event Data', width: 800, height: 660, modal: true});
  };

  $scope.downloadEventCsv = function (experimentInstance) {
    location.href = '/csv/event/' + experimentInstance.id;
  };

  var dialogMargin = 10,
    topDivHeight = 50,
    bottomDivHeight = 50,
    margin = 5,
    windowHeight = ($(window).innerHeight() - topDivHeight - bottomDivHeight - dialogMargin),
    windowWidth = ($(window).innerWidth() - (margin * 2));

  $scope.outputDialogOptions = {
    title: 'Output',
    autoOpen: true,
    width: ((windowWidth * .5) - dialogMargin),
    height: ((windowHeight * .25) - dialogMargin),
    position: [margin, (topDivHeight + dialogMargin + (.75 * windowHeight))],
    buttons: {}
  };


  $scope.scriptDialogOptions = {
    title: 'Script',
    autoOpen: true,
    width: ((windowWidth * .5) - dialogMargin),
    height: ((windowHeight * .75) - dialogMargin),
    position: [margin, topDivHeight],
    buttons: [
      {
        text: 'Run',
        click: function () {
          sendScript();
        }
      }
    ]
  };

  $scope.dataDialogOptions = {
    title: 'Data',
    autoOpen: false,
    width: ((windowWidth * .5) - dialogMargin),
    height: windowHeight,
    position: [((windowWidth * .5) + dialogMargin), topDivHeight],
    buttons: [
      {
        text: 'Download CSV',
        click: function () {
          location.href = '/csv/data/' + $scope.breadboard.experiment.id;
        }
      }
    ]
  };

  $scope.contentDialogOptions = {
    title: 'Content',
    width: ((windowWidth * .5) - dialogMargin),
    height: ((windowHeight * .75) - dialogMargin),
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
    width: ((windowWidth * .5) - dialogMargin),
    height: windowHeight,
    position: [((windowWidth * .5) + dialogMargin), topDivHeight],
    autoOpen: false,
    dialogClass: 'steps-dialog',
    buttons: {
      'Save': function () {
        sendStep();
      }
    }
  };

  $scope.cssDialogOptions = {
    title: 'Style',
    autoOpen: true,
    buttons: {
      'Save': function () {
        saveStyle();
      }
    },
    width: ((windowWidth * .5) - dialogMargin),
    height: ((windowHeight * .25) - dialogMargin),
    position: [((windowWidth * .5) + dialogMargin), (topDivHeight + dialogMargin + (.75 * windowHeight))]
  };

  $scope.clientHtmlDialogOptions = {
    title: 'Client HTML',
    autoOpen: true,
    buttons: {
      'Save': function () {
        saveClientHtml();
      }
    },
    width: ((windowWidth * .5) - dialogMargin),
    height: ((windowHeight * .25) - dialogMargin),
    position: [((windowWidth * .5) + dialogMargin), (topDivHeight + dialogMargin + (.75 * windowHeight))]
  };

  $scope.clientGraphDialogOptions = {
    title: 'Client Graph',
    autoOpen: true,
    buttons: {
      'Save': function () {
        saveClientGraph();
      }
    },
    width: ((windowWidth * .5) - dialogMargin),
    height: ((windowHeight * .25) - dialogMargin),
    position: [((windowWidth * .5) + dialogMargin), (topDivHeight + dialogMargin + (.75 * windowHeight))]
  };

  $scope.graphDialogOptions = {
    title: 'Graph',
    autoOpen: true,
    width: ((windowWidth * .5) - dialogMargin),
    height: ((windowHeight * .75) - dialogMargin),
    position: [((windowWidth * .5) + dialogMargin), topDivHeight],
    buttons: {}
  };

  $scope.imagesDialogOptions = {
    title: 'Images',
    width: ((windowWidth * .5) - dialogMargin),
    height: windowHeight,
    position: [((windowWidth * .5) + dialogMargin), topDivHeight],
    autoOpen: false,
    buttons: {}
  };

  $scope.launchDialogOptions = {
    title: 'Launch',
    autoOpen: false,
    width: ((windowWidth * .5) - dialogMargin),
    height: windowHeight,
    position: [margin, topDivHeight],
    buttons: {}
  };

  $scope.playerDialogOptions = {
    title: 'Player',
    autoOpen: false,
    width: ((windowWidth * .5) - dialogMargin),
    height: windowHeight,
    position: [margin, topDivHeight],
    buttons: {}
  };

  $scope.amtDialogOptions = {
    title: 'AMT',
    autoOpen: false,
    width: ((windowWidth * .5) - dialogMargin),
    height: windowHeight,
    position: [((windowWidth * .5) + dialogMargin), topDivHeight],
    buttons: {}
  };

  $scope.amtAdminDialogOptions = {
    title: 'AMT Admin',
    autoOpen: false,
    width: windowWidth,
    height: windowHeight,
    position: [margin, topDivHeight],
    buttons: {}
  };

  $scope.parametersDialogOptions = {
    title: 'Parameters',
    autoOpen: false,
    width: ((windowWidth * .5) - dialogMargin),
    height: windowHeight,
    position: [((windowWidth * .5) + dialogMargin), topDivHeight],
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

  $scope.cssCodemirrorOptions = {
    lineNumbers: true,
    matchBrackets: true,
    mode: 'text/css',
    extraKeys: {
      "Ctrl-Enter": saveStyle
    },
    vimMode: false,
    showCursorWhenSelecting: true
  };

  $scope.clientHtmlCodemirrorOptions = {
    lineNumbers: true,
    matchBrackets: true,
    mode: 'text/html',
    extraKeys: {
      "Ctrl-Enter": saveClientHtml
    },
    vimMode: false,
    showCursorWhenSelecting: true
  };

  $scope.clientGraphCodemirrorOptions = {
    lineNumbers: true,
    matchBrackets: true,
    mode: 'text/javascript',
    extraKeys: {
      "Ctrl-Enter": saveClientGraph
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
    // TODO: Destroy all of the popup windows
    console.log("TODO: Destroy the popup windows or display the login as a modal");
  });

}]);