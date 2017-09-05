'use strict';

/* Controllers */

function AppCtrl($scope, $breadboardFactory, $timeout) {
  $breadboardFactory.onmessage(function (data) {
    try {
      if ($scope.breadboard == undefined) {
        $scope.breadboard = {};
      }

      console.log("data: ", data);
      //var data = JSON.parse(message.data);

      //if (data.action != undefined)
      if (false) {
        if (data.action == "addNode")
          $scope.breadboardGraph.addNode(data.id);

        if (data.action == "removeNode")
          $scope.breadboardGraph.removeNode(data.id);

        if (data.action == "nodePropertyChanged") {
          // TODO: Do we ever send node property values as JSON?
          var value;
          try {
            value = JSON.parse(data.value);
          } catch (e) {
            //console.log("Error parsing JSON: " + data.value);
            value = data.value;
          }
          $scope.breadboardGraph.nodePropertyChanged(data.id, data.key, value);
        }

        if (data.action == "nodePropertyRemoved")
          $scope.breadboardGraph.nodePropertyChanged(data.id, data.key);

        if (data.action == "addLink")
          $scope.breadboardGraph.addLink(data.id, data.source, data.target, data.value);

        if (data.action == "removeLink")
          $scope.breadboardGraph.removeLink(data.source, data.target);
      }
      else {
        $scope.breadboard = _.extend($scope.breadboard, data);

        console.log($scope.breadboard);

        if ($scope.breadboard.experiment != undefined) {
          if ($scope.breadboard.experiment.style)
            applyStyle();

          if ($scope.breadboard.experiment.parameters != undefined) {
            for (var i = 0; i < $scope.breadboard.experiment.parameters.length; i++) {
              var parameter = $scope.breadboard.experiment.parameters[i];
              if (!$scope.launchParameters[parameter.name]) {
                $scope.launchParameters[parameter.name] = parameter.defaultVal;
              }
            }
          }
        }
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

  $scope.paramType = function (type) {
    if (type == 'Boolean') {
      return "checkbox";
    }

    if (type == 'Decimal' || type == 'Integer') {
      return "number";
    }

    return "text";
  };

  $scope.dropPlayer = function (pid) {
    $breadboardFactory.send(
      {
        "action": "DropPlayer",
        "pid": pid
      });
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
    //console.log("Making choice: " + $scope.breadboard.graph.nodes[$scope.selectedNodeIndex].choices[i].uid);
    $breadboardFactory.send(
      {
        "action": "MakeChoice",
        "choiceUID": $scope.selectedNode.choices[i].uid
      });
  };

  $scope.makeChoiceOld = function (i) {
    //console.log("Making choice: " + $scope.breadboard.graph.nodes[$scope.selectedNodeIndex].choices[i].uid);
    $breadboardFactory.send(
      {
        "action": "MakeChoice",
        "choiceUID": $scope.breadboard.graph.nodes[$scope.selectedNodeIndex].choices[i].uid
      });
  };

  $scope.formatContent = function(content) {
    var returnContent = {};
    returnContent.languages = {};
    returnContent.contentObject = {};
    console.log("content", content);
    for (var i = 0; i < content.length; i++) {
      var c = content[i];
      returnContent.languages[c.language] = true;

      if (! returnContent.contentObject.hasOwnProperty(c.name)) {
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
          "id": $scope.selectedContent.id,
          "name": $scope.selectedContent.name,
          "html": $scope.selectedContent.html
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
    //console.log("createExperiment: " + $scope.newExperimentName);
    $('#newExperimentDialog').dialog('close');
    $breadboardFactory.send(
      {
        "action": "CreateExperiment",
        "name": $scope.newExperimentName,
        "copyExperimentName": $scope.copyExperimentName
      });
  };

  $scope.submitAMTTask = function () {
    if ($scope.breadboard.experiment == undefined || $scope.breadboard.experimentInstance == undefined || $scope.breadboard.experiment.id == undefined || $scope.breadboard.experimentInstance.id == undefined) {
      $('#amtErrorDialog').dialog({
        modal: true
      }).parent().addClass("ui-state-error");
    } else {
      //$('#submitAMTButton').prop("disabled",true).html("Please wait...").attr("class","disabled-button");
      //console.log("Experiment: " + $scope.breadboard.experiment.id);
      //console.log("ExperimentInstance: " + $scope.breadboard.experimentInstance.id);
      //console.log("submitAMTTask:");
      //console.log($scope.amtTaskReward);
      //console.log($scope.amtTaskLifetime);
      //console.log($scope.amtTaskMaxAssignments);
      //console.log($scope.amtTaskDisallowPrevious);
      //console.log($scope.amtTaskSandbox);
      //console.log($scope.breadboard.experiment.id);
      //console.log($scope.breadboard.experimentInstance.id);

      $breadboardFactory.send(
        {
          "action": "SubmitAMTTask",
          "title": $scope.amtTaskTitle,
          "description": $scope.amtTaskDescription,
          "reward": $scope.amtTaskReward,
          "lifetimeInSeconds": $scope.amtTaskLifetime,
          "tutorialTime": $scope.amtTaskTutorialTime,
          "maxAssignments": $scope.amtTaskMaxAssignments,
          "disallowPrevious": $scope.amtTaskDisallowPrevious,
          "sandbox": $scope.amtTaskSandbox,
          "experimentId": $scope.breadboard.experiment.id,
          "experimentInstanceId": $scope.breadboard.experimentInstance.id
        });
    }
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

  $scope.grantBonus = function (workerId, assignmentId, score) {
    $breadboardFactory.send(
      {
        "action": "GrantBonus",
        "workerId": workerId,
        "assignmentId": assignmentId,
        "bonus": score
      });
  };

  $scope.approveAssignment = function (assignmentId) {
    $breadboardFactory.send(
      {
        "action": "ApproveAssignment",
        "assignmentId": assignmentId
      });
  };

  $scope.rejectAssignment = function (assignmentId) {
    $breadboardFactory.send(
      {
        "action": "RejectAssignment",
        "assignmentId": assignmentId
      });
  };

  $scope.blockWorker = function (assignmentId) {
    $breadboardFactory.send(
      {
        "action": "BlockWorker",
        "assignmentId": assignmentId
      });
  };

  $scope.markCompleted = function (assignmentId) {
    $breadboardFactory.send(
      {
        "action": "MarkCompleted",
        "assignmentId": assignmentId
      });
  };

  $scope.curAssignments = function () {
    if ($scope.selectedHIT == undefined) {
      return new Array();
    }
    for (var i = 0; i < $scope.breadboard.experiment.instances.length; i++) {
      for (var j = 0; j < $scope.breadboard.experiment.instances[i].hits.length; j++) {
        if ($scope.breadboard.experiment.instances[i].hits[j].id == $scope.selectedHIT) {
          return $scope.breadboard.experiment.instances[i].hits[j].assignments;
        }
      }
    }
    return new Array();
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

  $scope.testInstance = function (id) {
    $breadboardFactory.send(
      {
        "action": "TestInstance",
        "id": id
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

  $scope.selectedLanguages = {};
  $scope.selectedContentName = undefined;
  $scope.selectedContent = undefined;

  $scope.selectLanguage = function(contentName, selectedLanguage, thisSelect) {
    console.log("contentName", contentName);
    console.log("selectedLanguage", selectedLanguage);
    console.log("thisSelect", thisSelect);

    if (selectedLanguage == "+") {
      $("#addLanguageDialog input").each(function (index, element) {
        $(element).val("");
      });
      $('#addLanguageDialog').dialog({
        title: 'Add new language',
        buttons: {
          'Submit': function () {
            console.log("scope.newLanguage", $scope.newLanguage);
            console.log("contentName", contentName);
            $breadboardFactory.send({"action": "AddLanguage", "contentName": contentName, "newLanguage": $scope.newLanguage});
            thisSelect.selectedLanguage = " ";
            $(this).dialog("close");
          }
        }
      });
    } else {
      $scope.selectedContentName = contentName;
      $scope.selectedContent = $scope.breadboard.experiment.content[contentName][selectedLanguage];
      $scope.selectedLanguages[contentName] = selectedLanguage;
      $timeout(resizeTiny, 10);
    }
  };

  $scope.selectContent = function(contentName) {
    $scope.selectedContentName = contentName;
    if ($scope.selectedLanguages[contentName] !== undefined) {
      $scope.selectedContent = $scope.breadboard.experiment.content[contentName][$scope.selectedLanguages[contentName]];
    }
    $timeout(resizeTiny, 10);
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
    $breadboardFactory.send({"action": "CreateContent", "name": $scope.newContentName, "language": $scope.newContentLanguage});
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
    windowHeight = ($(window).innerHeight() - topDivHeight - bottomDivHeight - dialogMargin),
    windowWidth = ($(window).innerWidth() - 5);

  $scope.outputDialogOptions = {
    title: 'Output',
    autoOpen: true,
    width: ((windowWidth * .5) - dialogMargin),
    height: ((windowHeight * .25) - dialogMargin),
    position: ['left', (topDivHeight + dialogMargin + (.75 * windowHeight))],
    buttons: {}
  };


  $scope.scriptDialogOptions = {
    title: 'Script',
    autoOpen: true,
    width: ((windowWidth * .5) - dialogMargin),
    height: ((windowHeight * .75) - dialogMargin),
    position: ['left', topDivHeight],
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
    position: ['left', topDivHeight],
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
    open: openCSS,
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

  $scope.cssDialogIsOpen = false;

  function openCSS() {
    $scope.cssDialogIsOpen = true;
  }

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
    position: ['left', topDivHeight],
    buttons: {}
  };

  $scope.playerDialogOptions = {
    title: 'Player',
    autoOpen: false,
    width: ((windowWidth * .5) - dialogMargin),
    height: windowHeight,
    position: ['left', topDivHeight],
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

  $scope.amtAssignmentsDialogOptions = {
    title: 'AMT Assignments',
    autoOpen: false,
    width: ((windowWidth * .5) - dialogMargin),
    height: windowHeight,
    position: [((windowWidth * .5) + dialogMargin), topDivHeight],
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
    content_css: routes.tinymceCSS,
    valid_elements: '*[*]',
    resize: true
  }
}

AppCtrl.$inject = ['$scope', 'breadboardFactory', '$timeout'];
