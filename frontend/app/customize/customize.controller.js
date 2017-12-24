function CustomizeCtrl($scope, CustomizeSrv, STATUS, $timeout) {
  const TAB = {
    'HTML': 1,
    'GRAPH': 2,
    'STYLE': 3
  };

  $scope.TAB = TAB;
  $scope.selectTab = selectTab;
  $scope.vm = {
    selectedTab: 0,
    clientHtml: {
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
    },
    clientGraph: {
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
    },
    style: {
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
    }
  };

  const savedTime = 1000;

  selectTab(TAB.STYLE, 'styleTab');

  function getClientHtml() {
    $scope.vm.clientHtml.status = STATUS.LOADING;
    CustomizeSrv.getClientHtml($scope.experimentId)
      .then(
        function(success){
          $scope.vm.clientHtml.status = STATUS.UNCHANGED;
          $scope.vm.clientHtml.serverValue = success.data.clientHtml;
          $scope.vm.clientHtml.clientValue = success.data.clientHtml;
          $scope.$watch('vm.clientHtml.clientValue', function(newValue) {
            if (newValue !== $scope.vm.clientHtml.serverValue) {
              $scope.vm.clientHtml.status = STATUS.MODIFIED;
            } else {
              if (!($scope.vm.clientHtml.status === STATUS.SAVED)) {
                $scope.vm.clientHtml.status = STATUS.UNCHANGED;
              }
            }
          });
        },
        function(error){
          $scope.vm.clientHtml.status = STATUS.ERROR;
          $scope.vm.clientHtml.errorMessage = error.data;
        });
  }

  function getClientGraph() {
    $scope.vm.clientGraph.status = STATUS.LOADING;
    CustomizeSrv.getClientGraph($scope.experimentId)
      .then(
        function(success){
          $scope.vm.clientGraph.status = STATUS.UNCHANGED;
          $scope.vm.clientGraph.serverValue = success.data.clientGraph;
          $scope.vm.clientGraph.clientValue = success.data.clientGraph;
          $scope.$watch('vm.clientGraph.clientValue', function(newValue) {
            if (newValue !== $scope.vm.clientGraph.serverValue) {
              $scope.vm.clientGraph.status = STATUS.MODIFIED;
            } else {
              if (!($scope.vm.clientGraph.status === STATUS.SAVED)) {
                $scope.vm.clientGraph.status = STATUS.UNCHANGED;
              }
            }
          });
        },
        function(error){
          $scope.vm.clientGraph.status = STATUS.ERROR;
          $scope.vm.clientGraph.errorMessage = error.data;
        });
  }

  function getStyle() {
    $scope.vm.style.status = STATUS.LOADING;
    CustomizeSrv.getStyle($scope.experimentId)
      .then(
        function(success){
          $scope.vm.style.status = STATUS.UNCHANGED;
          $scope.vm.style.serverValue = success.data.style;
          $scope.vm.style.clientValue = success.data.style;
          $scope.$watch('vm.style.clientValue', function(newValue) {
            if (newValue !== $scope.vm.style.serverValue) {
              $scope.vm.style.status = STATUS.MODIFIED;
            } else {
              if (!($scope.vm.style.status === STATUS.SAVED)) {
                $scope.vm.style.status = STATUS.UNCHANGED;
              }
            }
          });
        },
        function(error){
          $scope.vm.style.status = STATUS.ERROR;
          $scope.vm.style.errorMessage = error.data;
        });
  }


  function selectTab(tab, tabId) {
    if (tab === $scope.vm.selectedTab) return;

    $scope.vm.selectedTab = tab;

    // If we haven't already retrieved the content, get it now
    if (tab === TAB.HTML && $scope.vm.clientHtml.status === STATUS.UNLOADED) getClientHtml();
    if (tab === TAB.GRAPH && $scope.vm.clientGraph.status === STATUS.UNLOADED) getClientGraph();
    if (tab === TAB.STYLE && $scope.vm.style.status === STATUS.UNLOADED) getStyle();

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
    if ($scope.vm.clientHtml.status === STATUS.MODIFIED) {
      updateClientHtml();
    }
    if ($scope.vm.clientGraph.status === STATUS.MODIFIED) {
      updateClientGraph();
    }
    if ($scope.vm.style.status === STATUS.MODIFIED) {
      updateStyle();
    }
  };

  function updateClientHtml() {
    $scope.vm.clientHtml.status = STATUS.SAVING;
    CustomizeSrv.updateClientHtml($scope.experimentId, $scope.vm.clientHtml.clientValue)
      .then(
        function() {
          $scope.vm.clientHtml.status = STATUS.SAVED;
          $timeout(function() {
            $scope.vm.clientHtml.status = STATUS.UNCHANGED;
          }, savedTime);
          $scope.vm.clientHtml.serverValue = $scope.vm.clientHtml.clientValue;
        },
        function(error) {
          $scope.vm.clientHtml.status = STATUS.ERROR;
          $scope.vm.clientHtml.errorMessage = error.data;
        }
      );
  }

  function updateClientGraph() {
    $scope.vm.clientGraph.status = STATUS.SAVING;
    CustomizeSrv.updateClientGraph($scope.experimentId, $scope.vm.clientGraph.clientValue)
      .then(
        function() {
          $scope.vm.clientGraph.status = STATUS.SAVED;
          $timeout(function() {
            $scope.vm.clientGraph.status = STATUS.UNCHANGED;
          }, savedTime);
          $scope.vm.clientGraph.serverValue = $scope.vm.clientGraph.clientValue;
        },
        function(error) {
          $scope.vm.clientGraph.status = STATUS.ERROR;
          $scope.vm.clientGraph.errorMessage = error.data;
        }
      );
  }

  function updateStyle() {
    $scope.vm.style.status = STATUS.SAVING;
    CustomizeSrv.updateStyle($scope.experimentId, $scope.vm.style.clientValue)
      .then(
        function() {
          $scope.vm.style.status = STATUS.SAVED;
          $timeout(function() {
            $scope.vm.style.status = STATUS.UNCHANGED;
          }, savedTime);
          $scope.vm.style.serverValue = $scope.vm.style.clientValue;
        },
        function(error) {
          $scope.vm.style.status = STATUS.ERROR;
          $scope.vm.style.errorMessage = error.data;
        }
      );
  }
}

CustomizeCtrl.$inject = ['$scope', 'CustomizeSrv', 'STATUS', '$timeout'];

export default CustomizeCtrl;
