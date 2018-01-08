function CustomizeCtrl($scope, CustomizeSrv, STATUS, $timeout) {
  let vm = this;

  const TAB = {
    'HTML': 1,
    'GRAPH': 2,
    'STYLE': 3
  };

  const savedTime = 1000;

  // vm.experimentId = $scope.experimentId;
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
        "Ctrl-Enter": updateClientHtml
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
        "Ctrl-Enter": updateClientGraph
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
      lineNumbers: true,
      matchBrackets: true,
      mode: 'text/css',
      extraKeys: {
        "Ctrl-Enter": updateStyle
      },
      vimMode: false,
      showCursorWhenSelecting: true
    }
  };

  $scope.$watch('experimentId', function(){
    selectTab(TAB.STYLE, 'styleTab');
  });
  selectTab(TAB.STYLE, 'styleTab');

  function getClientHtml() {
    vm.clientHtml.status = STATUS.LOADING;
    CustomizeSrv.getClientHtml($scope.experimentId)
      .then(
        function(success){
          vm.clientHtml.status = STATUS.UNCHANGED;
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
          vm.clientHtml.errorMessage = error.data;
        });
  }

  function getClientGraph() {
    vm.clientGraph.status = STATUS.LOADING;
    CustomizeSrv.getClientGraph($scope.experimentId)
      .then(
        function(success){
          vm.clientGraph.status = STATUS.UNCHANGED;
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
          vm.clientGraph.errorMessage = error.data;
        });
  }

  function getStyle() {
    vm.style.status = STATUS.LOADING;
    CustomizeSrv.getStyle($scope.experimentId)
      .then(
        function(success){
          vm.style.status = STATUS.UNCHANGED;
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
          vm.style.errorMessage = error.data;
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
    if (vm.clientHtml.status === STATUS.MODIFIED) {
      updateClientHtml();
    }
    if (vm.clientGraph.status === STATUS.MODIFIED) {
      updateClientGraph();
    }
    if (vm.style.status === STATUS.MODIFIED) {
      updateStyle();
    }
  };

  function updateClientHtml() {
    vm.clientHtml.status = STATUS.SAVING;
    CustomizeSrv.updateClientHtml($scope.experimentId, vm.clientHtml.clientValue)
      .then(
        function() {
          vm.clientHtml.status = STATUS.SAVED;
          $timeout(function() {
            vm.clientHtml.status = STATUS.UNCHANGED;
          }, savedTime);
          vm.clientHtml.serverValue = vm.clientHtml.clientValue;
        },
        function(error) {
          vm.clientHtml.status = STATUS.ERROR;
          vm.clientHtml.errorMessage = error.data;
        }
      );
  }

  function updateClientGraph() {
    vm.clientGraph.status = STATUS.SAVING;
    CustomizeSrv.updateClientGraph($scope.experimentId, vm.clientGraph.clientValue)
      .then(
        function() {
          vm.clientGraph.status = STATUS.SAVED;
          $timeout(function() {
            vm.clientGraph.status = STATUS.UNCHANGED;
          }, savedTime);
          vm.clientGraph.serverValue = vm.clientGraph.clientValue;
        },
        function(error) {
          vm.clientGraph.status = STATUS.ERROR;
          vm.clientGraph.errorMessage = error.data;
        }
      );
  }

  function updateStyle() {
    vm.style.status = STATUS.SAVING;
    CustomizeSrv.updateStyle($scope.experimentId, vm.style.clientValue)
      .then(
        function() {
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
          vm.style.errorMessage = error.data;
        }
      );
  }
}

CustomizeCtrl.$inject = ['$scope', 'CustomizeSrv', 'STATUS', '$timeout'];

export default CustomizeCtrl;
