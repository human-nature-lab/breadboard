function CustomizeCtrl($scope, CustomizeSrv, STATUS, $timeout) {
  let vm = this;

  const TAB = {
    'HTML': 1,
    'GRAPH': 2,
    'STYLE': 3
  };

  const savedTime = 1000;

  vm.experiment = $scope.experiment;
  vm.experimentId = undefined;
  vm.TAB = TAB;
  vm.selectTab = selectTab;
  vm.selectedTab = 0;

  vm.clientHtml = {
    status: STATUS.UNLOADED,
    errorMessage: '',
    serverValue: '',
    clientValue: '',
    codeMirrorOptions: {
      lineNumbers: true,
      matchBrackets: true,
      mode: 'text/html',
      extraKeys: {
        "Ctrl-Enter": updateClientHtml,
        "Ctrl-S" : function() {
          $scope.saveCustomize()
        }
      },
      vimMode: false,
      showCursorWhenSelecting: true
    }
  };

  vm.clientGraph = {
    status: STATUS.UNLOADED,
    errorMessage: '',
    serverValue: '',
    clientValue: '',
    codeMirrorOptions: {
      lineNumbers: true,
      matchBrackets: true,
      mode: 'text/javascript',
      extraKeys: {
        "Ctrl-Enter": updateClientGraph,
        "Ctrl-S" : function() {
          $scope.saveCustomize()
        }
      },
      vimMode: false,
      showCursorWhenSelecting: true
    }
  };

  vm.style = {
    status: STATUS.UNLOADED,
    errorMessage: '',
    serverValue: '',
    clientValue: '',
    codeMirrorOptions: {
      readOnly: ($scope.readOnly) ? 'nocursor' : false,
      lineNumbers: true,
      matchBrackets: true,
      mode: 'text/css',
      extraKeys: {
        "Ctrl-Enter": updateStyle,
        "Ctrl-S" : function() {
          $scope.saveCustomize()
        }
      },
      vimMode: false,
      showCursorWhenSelecting: true
    }
  };

  $scope.$watch('readOnly', function(newValue) {
    let codeMirrorInstances = document.getElementById('customize-div').getElementsByClassName('CodeMirror');
    for (let i = 0; i < codeMirrorInstances.length; i++) {
      let cm = codeMirrorInstances[i].CodeMirror;
      cm.setOption('readOnly', ((newValue) ? 'nocursor' : false));
    }
  });

  $scope.$watch('experimentId', function(experimentId){
    if (experimentId !== vm.experimentId) {
      vm.selectedTab = undefined;
      vm.experimentId = experimentId;

      vm.clientHtml.status = STATUS.UNLOADED;
      vm.clientGraph.status = STATUS.UNLOADED;
      vm.style.status = STATUS.UNLOADED;

      selectTab(TAB.STYLE, 'styleTab');
    }
  });

  $scope.$watch('experiment.style', function(style){
    if (vm.experiment.fileMode) {
      vm.style.serverValue = style;
      vm.style.clientValue = style;
    }
  });

  $scope.$watch('experiment.clientGraphHash', function(cgh){
    if (vm.experiment.fileMode) {
      getClientGraph();
    }
  });

  $scope.$watch('experiment.clientHtmlHash', function(chh){
    if (vm.experiment.fileMode) {
      getClientHtml();
    }
  });

  function getClientHtml() {
    vm.clientHtml.status = STATUS.LOADING;
    CustomizeSrv.getClientHtml(vm.experimentId)
      .then(
        function(success){
          vm.clientHtml.status = STATUS.UNCHANGED;
          vm.clientHtml.errorMessage = '';
          vm.clientHtml.serverValue = success.data.clientHtml;
          vm.clientHtml.clientValue = success.data.clientHtml;
          $scope.$watch('vm.clientHtml.clientValue', function(newValue) {
            if (newValue !== $scope.vm.clientHtml.serverValue) {
              vm.clientHtml.status = STATUS.MODIFIED;
            } else {
              if (!(vm.clientHtml.status === STATUS.SAVED)) {
                vm.clientHtml.status = STATUS.UNCHANGED;
              }
            }
          });
        },
        function(error){
          vm.clientHtml.status = STATUS.ERROR;
          vm.clientHtml.errorMessage = (error.data) ? error.data : error;
        });
  }

  function getClientGraph() {
    vm.clientGraph.status = STATUS.LOADING;
    CustomizeSrv.getClientGraph(vm.experimentId)
      .then(
        function(success){
          vm.clientGraph.status = STATUS.UNCHANGED;
          vm.clientGraph.errorMessage = '';
          vm.clientGraph.serverValue = success.data.clientGraph;
          vm.clientGraph.clientValue = success.data.clientGraph;
          $scope.$watch('vm.clientGraph.clientValue', function(newValue) {
            if (newValue !== vm.clientGraph.serverValue) {
              vm.clientGraph.status = STATUS.MODIFIED;
            } else {
              if (!(vm.clientGraph.status === STATUS.SAVED)) {
                vm.clientGraph.status = STATUS.UNCHANGED;
              }
            }
          });
        },
        function(error){
          vm.clientGraph.status = STATUS.ERROR;
          vm.clientGraph.errorMessage = (error.data) ? error.data : error;
        });
  }

  function getStyle() {
    vm.style.status = STATUS.LOADING;
    CustomizeSrv.getStyle(vm.experimentId)
      .then(
        function(success){
          vm.style.status = STATUS.UNCHANGED;
          vm.style.errorMessage = '';
          vm.style.serverValue = success.data.style;
          vm.style.clientValue = success.data.style;
          $scope.$watch('vm.style.clientValue', function(newValue) {
            if (newValue !== vm.style.serverValue) {
              vm.style.status = STATUS.MODIFIED;
            } else {
              if (!(vm.style.status === STATUS.SAVED)) {
                vm.style.status = STATUS.UNCHANGED;
              }
            }
          });
        },
        function(error){
          vm.style.status = STATUS.ERROR;
          vm.style.errorMessage = (error.data) ? error.data : error;
        });
  }

  function selectTab(tab, tabId) {
    if (tab === vm.selectedTab) return;

    vm.selectedTab = tab;

    // If we haven't already retrieved the content, get it now
    if (tab === TAB.HTML && vm.clientHtml.status === STATUS.UNLOADED) getClientHtml();
    if (tab === TAB.GRAPH && vm.clientGraph.status === STATUS.UNLOADED) getClientGraph();
    if (tab === TAB.STYLE && vm.style.status === STATUS.UNLOADED) getStyle();

    // Refresh CodeMirror instance when clicking tab
    $timeout(function() {
      let codeMirrorInstances = document.getElementById(tabId).getElementsByClassName("CodeMirror");
      for (let i = 0; i < codeMirrorInstances.length; i++) {
        let cm = codeMirrorInstances[i].CodeMirror;
        if (cm) {
          cm.refresh();
        }
      }
    }, 100);
  }

  $scope.saveCustomize = function() {
    if (vm.clientHtml.status === STATUS.MODIFIED || vm.clientHtml.status === STATUS.ERROR) {
      updateClientHtml();
    }
    if (vm.clientGraph.status === STATUS.MODIFIED || vm.clientGraph.status === STATUS.ERROR) {
      updateClientGraph();
    }
    if (vm.style.status === STATUS.MODIFIED || vm.style.status === STATUS.ERROR) {
      updateStyle();
    }
  };

  function updateClientHtml() {
    vm.clientHtml.status = STATUS.SAVING;
    CustomizeSrv.updateClientHtml(vm.experimentId, vm.clientHtml.clientValue)
      .then(
        function() {
          vm.clientHtml.errorMessage = '';
          vm.clientHtml.status = STATUS.SAVED;
          $timeout(function() {
            vm.clientHtml.status = STATUS.UNCHANGED;
          }, savedTime);
          vm.clientHtml.serverValue = vm.clientHtml.clientValue;
        },
        function(error) {
          vm.clientHtml.status = STATUS.ERROR;
          vm.clientHtml.errorMessage = (error.data) ? error.data : error;
        }
      );
  }

  function updateClientGraph() {
    vm.clientGraph.status = STATUS.SAVING;
    CustomizeSrv.updateClientGraph(vm.experimentId, vm.clientGraph.clientValue)
      .then(
        function() {
          vm.clientGraph.errorMessage = '';
          vm.clientGraph.status = STATUS.SAVED;
          $timeout(function() {
            vm.clientGraph.status = STATUS.UNCHANGED;
          }, savedTime);
          vm.clientGraph.serverValue = vm.clientGraph.clientValue;
        },
        function(error) {
          vm.clientGraph.status = STATUS.ERROR;
          vm.clientGraph.errorMessage = (error.data) ? error.data : error;
        }
      );
  }

  function updateStyle() {
    vm.style.status = STATUS.SAVING;
    CustomizeSrv.updateStyle(vm.experimentId, vm.style.clientValue)
      .then(
        function() {
          vm.style.errorMessage = '';
          vm.style.status = STATUS.SAVED;
          $timeout(function() {
            vm.style.status = STATUS.UNCHANGED;
          }, savedTime);
          vm.style.serverValue = vm.style.clientValue;

          // Update Style
          $('#styleTag').text(vm.style.clientValue);
        },
        function(error) {
          vm.style.status = STATUS.ERROR;
          vm.style.errorMessage = (error.data) ? error.data : error;
        }
      );
  }
}

CustomizeCtrl.$inject = ['$scope', 'CustomizeSrv', 'STATUS', '$timeout'];

export default CustomizeCtrl;
