import KEYS from '../util/keycodes';
ContentCtrl.$inject = ['$scope', 'ContentSrv', 'STATUS', '$timeout', 'orderByFilter', 'languageService', 'alertService'];
export default function ContentCtrl($scope, ContentSrv, STATUS, $timeout, orderBy, languageService, alertService) {
  let vm = this;

  const savedTime = 1000;

  function defaultHtml(languageName) {
    return '<p>Type your content for the ' + languageName + ' language here.</p>';
  }

  vm.addLanguage = addLanguage;
  vm.removeLanguage = removeLanguage;
  vm.experimentHasLanguage = experimentHasLanguage;
  vm.createContent = createContent;
  vm.selectContent = selectContent;
  vm.deleteContent = deleteContent;
  vm.getSelectedTranslation = getSelectedTranslation;

  vm.languages = [];
  vm.experimentLanguages = $scope.experimentLanguages;
  vm.experimentId = $scope.experimentId;
  vm.content = [];
  vm.createStatus = STATUS.UNLOADED;
  vm.error = '';
  vm.tinymceOptions = {
    theme: 'modern',
    width: '100%',
    height: '100%',
    plugins: "spellchecker image code lists link charmap textcolor autoresize",
    toolbar: "styleselect | bullist numlist outdent indent | forecolor backcolor | cut copy paste | undo redo | link unlink | charmap | spellchecker | code | image",
    statusbar: false,
    menubar: false,
    convert_urls: false,
    content_css: '/assets/css/tinymce.css',
    valid_elements: '*[*]',
    resize: true,
    autoresize_max_height: ($('#contentDiv').height() - $('#contentNavDiv').height() - $('#contentErrorDiv').height()),
    setup: function(editor) {
      //Focus the editor on load
      $timeout(function(){
        editor.focus();
        //editor.setMode("readonly");
        if ($scope.readOnly) {
          //editor.setMode('readonly');
          editor.getBody().setAttribute('contenteditable', 'false');
          angular.element('.mce-toolbar-grp').css('visibility', 'hidden');
        } else {
          //editor.setMode('design');
          editor.getBody().setAttribute('contenteditable', 'true');
          angular.element('.mce-toolbar-grp').css('visibility', 'visible');
        }
      }, 1000);
      editor.on("keydown", function(e) {
        if(e.ctrlKey && e.keyCode === KEYS.S){
          $scope.saveContent();
          e.preventDefault();
        }
      });
    }
  };

  vm.selectedContent = undefined;
  vm.selectedTranslation = undefined;
  vm.selectedLanguage = vm.experimentLanguages[0];

  getContent();

  $scope.$watch('experimentId', function(newExperimentId, oldExperimentId) {
    if (newExperimentId !== oldExperimentId) {
      vm.experimentId = $scope.experimentId;
      vm.experimentLanguages = $scope.experimentLanguages;
      vm.selectedLanguage = vm.experimentLanguages[0];
      getContent();
    }
  });

  $scope.$watch('readOnly', function() {
    let tinymceinstance = tinyMCE.get('tinymceTextarea');
    if (!tinymceinstance) return;
    if ($scope.readOnly) {
      tinymceinstance.getBody().setAttribute('contenteditable', 'false');
      angular.element('.mce-toolbar-grp').css('visibility', 'hidden');
    } else {
      tinymceinstance.getBody().setAttribute('contenteditable', 'true');
      angular.element('.mce-toolbar-grp').css('visibility', 'visible');
    }
    //tinymceinstance.getBody().setAttribute('contenteditable', (!$scope.readOnly) + "");
  });

  // In the case that fileMode = true, we can safely watch the Steps and update when they change.
  $scope.$watch('experiment.content', updateFromFile, true);

  function updateFromFile() {
    if ($scope.readOnly) {
      vm.content = orderBy($scope.experiment.content, 'name', false);

      copyTranslationHtml();

      angular.forEach(vm.content, function(c) {
        if (c.name === vm.selectedContent.name) {
          vm.selectedContent = c;
        }
      });

      getSelectedTranslation();

    }
  }

  function copyTranslationHtml() {
    angular.forEach(vm.content, function(c) {
      angular.forEach(c.translations, function(t) {
        t.clientHtml = t.html;
      });
    });
  }

  function getContent() {
    ContentSrv.getContent(vm.experimentId)
      .then(
        function(success){
          vm.content = orderBy(success.data.content, 'name', false);

          copyTranslationHtml();

          vm.selectedContent = vm.content[0];
          getSelectedTranslation();
          $scope.$watch('vm.selectedLanguage', getSelectedTranslation);
          $scope.$watch('vm.selectedContent', getSelectedTranslation);
        },
        function(error){
          vm.error = error.data;
        });
  }

  function getSelectedTranslation() {
    vm.selectedTranslation = undefined;

    if (vm.selectedContent && vm.selectedLanguage) {
      for (let i = 0; i < vm.selectedContent.translations.length; i++) {
        let translation = vm.selectedContent.translations[i];
        if (translation.language.code == vm.selectedLanguage.code) {
          vm.selectedTranslation = vm.selectedContent.translations[i];
        }
      }

      if (vm.selectedTranslation === undefined) {
        let translation = createTranslation(vm.selectedLanguage);
        vm.selectedContent.translations.push(translation);
        vm.selectedTranslation = translation;
      }
    }
  }

  languageService.all().then(function(response) {
    vm.languages = response.languages;
  },
  function(error) {
    vm.error = error.data;
  });

  function addLanguage(experimentId, language) {
    languageService.addLanguage(experimentId, language.id)
      .then(function(response) {
        // Add the language to the list and select it
        vm.experimentLanguages.push(response.data);
        vm.selectedLanguage = response.data;
      },
      function(error) {
        vm.error = error.data;
      });
  }

  function removeLanguage(experimentId, language) {
    alertService.confirm(`This action is permanent. Are you sure you want to remove ${language.name} from translated languages?`)
      .then(confirmed => {
        languageService.removeLanguage(experimentId, language.id)
          .then(function() {
              // Remove the language from the list and select the next language
              let languageIndex = vm.experimentLanguages.indexOf(language);
              vm.experimentLanguages.splice(languageIndex, 1);
            },
            function(error) {
              vm.error = error.data;
            });
      }, cancelled => {

      })
  }

  function experimentHasLanguage(language) {
    for (let i = 0; i < vm.experimentLanguages.length; i++) {
      if (vm.experimentLanguages[i].id === language.id) return true;
    }
    return false;
  }

  function selectContent(content) {
    if (content === vm.selectedContent) return;
    vm.selectedContent = content;
  }

  function updateSelectedContent() {
    updateContent(vm.selectedContent, vm.experimentId);
  }

  function createTranslation(language) {
    let html = defaultHtml(language.name);
    return {
      'id': -1,
      'html': '',
      'clientHtml': html,
      'language': language
    };
  }

  function createContent() {
    let translations = [];
    for (let i = 0; i < vm.experimentLanguages.length; i++) {
      let language =  vm.experimentLanguages[i];
      let translation = createTranslation(language);
      translations.push(translation);
    }
    let c = {
      'id': -1,
      'name': 'NewContent',
      'editName': true,
      'isNew': true,
      'translations': translations
    };
    vm.content.push(c);

    vm.selectedContent = c;
  }


  function deleteContent(c) {
    alertService.confirm(`This action is permanent. Are you sure you'd like to delete ${c.name}?`)
      .then(confirmed => {
        if (c.id !== -1) {
          ContentSrv.deleteContent(c.id)
            .then(function(){
                let contentIndex = vm.content.indexOf(c);
                vm.content.splice(contentIndex, 1);
                if (contentIndex > (vm.content.length - 1)) contentIndex = 0;
                vm.selectedContent = vm.content[contentIndex];
              },
              function(error) {
                c.status = STATUS.ERROR;
                c.error = error.data;
              });
        } else {
          let contentIndex = vm.content.indexOf(c);
          vm.content.splice(contentIndex, 1);
          if (contentIndex > (vm.content.length - 1)) contentIndex = 0;
          vm.selectedContent = vm.content[contentIndex];
        }
      }, cancelled => {

      });
  }

  function updateContent(c, experimentId) {
    c.status = STATUS.SAVING;
    ContentSrv.updateContent(experimentId, c)
      .then(
        function(success) {
          c.id = success.data.id;
          c.translations = success.data.translations;
          c.status = STATUS.SAVED;
          $timeout(function() {
            c.status = STATUS.UNCHANGED;
          }, savedTime);
          c.error = '';
          angular.forEach(c.translations, function(t) {
            t.clientHtml = t.html;
          });
          getSelectedTranslation();
        },
        function(error) {
          c.status = STATUS.ERROR;
          c.error = error.data;
        }
      );
  }

  $scope.saveContent = function() {
    angular.forEach(vm.content, function(c) {
      if (c.status === STATUS.MODIFIED) {
        updateContent(c, vm.experimentId);
      }
    });
  };

}