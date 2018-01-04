function StepsCtrl($scope, StepsSrv, STATUS, $timeout, orderBy) {
  let vm = this;

  const savedTime = 1000;

  vm.createStep = createStep;
  vm.selectStep = selectStep;
  vm.deleteStep = deleteStep;
  vm.sendStep = $scope.actions.sendStep;

  vm.selectedStep = {};
  vm.experimentId = $scope.experimentId;
  vm.steps = [];
  vm.createStatus = STATUS.UNLOADED;
  vm.error = '';
  vm.codeMirrorOptions = {
    lineNumbers: true,
    matchBrackets: true,
    mode: 'text/x-groovy',
    extraKeys: {
      "Ctrl-Enter": updateSelectedStep
    },
    vimMode: false,
    showCursorWhenSelecting: true
  };

  StepsSrv.getSteps(vm.experimentId)
    .then(
      function(success){
        vm.steps = orderBy(success.data.steps, 'name', false);
        vm.selectedStep = vm.steps[0];
      },
      function(error){
        vm.error = error.data;
      });

  function selectStep(step) {
    if (step === vm.selectedStep) return;

    vm.selectedStep = step;

    // Refresh CodeMirror instance when clicking tab
    $timeout(function() {
      let codeMirrorInstances = document.getElementById('stepTab').getElementsByClassName('CodeMirror');
      for (let i = 0; i < codeMirrorInstances.length; i++) {
        let cm = codeMirrorInstances[i].CodeMirror;
        if (cm) {
          cm.refresh();
        }
      }
    }, 100);
  }

  function updateSelectedStep() {
    updateStep(vm.selectedStep, vm.experimentId);
  }

  function createStep(type) {
    let source = getSource('NewStep', type);
    let step = {
      id: -1,
      inputName: 'NewStep',
      name: 'NewStep',
      editName: true,
      isNew: true,
      clientSource: source,
      source: null
    };
    vm.steps.push(step);

    vm.selectedStep = step;

    $scope.$watch('vm.selectedStep.inputName', function(name) {
      if (vm.selectedStep.editName) {
        let source = getSource(name, type);
        vm.selectedStep.clientSource = source;
      }
    });
  }

  function getSource(name, type) {
    if (type === 2) return '';
    let fName = formatName(name);
    return fName + ' = stepFactory.' + ((type === 0) ? 'createStep' : 'createNoUserActionStep') + '("' + name + '")\n' +
      '\n' +
      fName + '.run = {\n' +
      '  println "' + fName + '.run"\n' +
      '}\n\n' +
      fName + '.done = {\n' +
      '  println "' + fName + '.done"\n' +
      '}';
  }

  function formatName(name) {
    return name
      .replace( /[-_]+/g, ' ') // replace _ or - with a space
      .replace( /[^\w\s]/g, '') // remove other non alphanumeric characters
      .replace(/(?:^\w|[A-Z]|\b\w)/g, function(letter, index) {
        return (index === 0 ? letter.toLowerCase() : letter.toUpperCase()); // camelcase
      })
      .replace( / /g, '' ); // remove spaces
  }

  function deleteStep(step) {
    console.log('deleteStep', step);
    StepsSrv.deleteStep(step.id)
      .then(function(){
        let stepIndex = vm.steps.indexOf(step);
        vm.steps.splice(stepIndex, 1);
        if (stepIndex > (vm.steps.length - 1)) stepIndex = 0;
        vm.selectedStep = vm.steps[stepIndex];
      },
      function(error) {
        step.status = STATUS.ERROR;
        step.error = error.data;
      });
  }

  function updateStep(step, experimentId) {
    step.status = STATUS.SAVING;
    StepsSrv.updateStep(experimentId, step)
      .then(
        function(success) {
          step.id = success.data.id;
          step.status = STATUS.SAVED;
          $timeout(function() {
            step.status = STATUS.UNCHANGED;
          }, savedTime);
          step.error = '';
          step.source = step.clientSource;
          // Update the script engine
          vm.sendStep(step);
        },
        function(error) {
          step.status = STATUS.ERROR;
          step.error = error.data;
        }
      );
  }

  $scope.saveSteps = function() {
    angular.forEach(vm.steps, function(step) {
      if (step.status === STATUS.MODIFIED) {
        updateStep(step, vm.experimentId);
      }
    });
  };

}

StepsCtrl.$inject = ['$scope', 'StepsSrv', 'STATUS', '$timeout', 'orderByFilter'];

export default StepsCtrl;
