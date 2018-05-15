function SelectQualificationsCtrl($scope, ManageQualificationSrv) {
  $scope.addQualificationRequirementStatus = 0;
  $scope.addQualificationRequirementError = '';
  $scope.qualificationTypes = [];
  $scope.selectedQualificationType = undefined;
  $scope.otherExperiments = '';
  $scope.comparator = '';
  $scope.integerValues = '';
  $scope.locales = [];
  $scope.actionsGuarded = '';
  $scope.openAddQualificationRequirementDialog = openAddQualificationRequirementDialog;
  $scope.addQualificationRequirement = addQualificationRequirement;
  $scope.addLocale = addLocale;
  $scope.removeLocale = removeLocale;
  $scope.removeQualificationRequirement = removeQualificationRequirement;
  $scope.localesToString = localesToString;

  initController();

  function initController() {
    ManageQualificationSrv.listQualificationTypes($scope.sandbox)
      .then(
        function(response) {
          $scope.qualificationTypes = [
            {
              label: "Masters (5% fee)",
              qualificationTypeId: ($scope.sandbox) ? "2ARFPLSP75KLA8M8DH1HTEQVJT3SY6" : "2F1QJWKUDD8XADTFD2Q0G6UTO95ALH"
            },
            {
              label: "Number of HITs Approved",
              qualificationTypeId: "00000000000000000040"
            },
            {
              label: "Locale",
              qualificationTypeId: "00000000000000000071"
            },
            {
              label: "Adult",
              qualificationTypeId: "00000000000000000060"
            },
            {
              label: "Percent assignments approved",
              qualificationTypeId: "000000000000000000L0"
            }
          ];
          angular.forEach(response.data.qualificationTypes, function(qualificationType) {
            if (qualificationType.hasOwnProperty('qualificationTypeId') && qualificationType.qualificationTypeId !== null) {
              $scope.qualificationTypes.push(qualificationType);
            }
          });
          $scope.qualificationTypes.push({
            label: 'Other experiment',
            qualificationTypeId: 'OTHER_EXPERIMENT'
          });
        },
        function(error) {
          console.error(error);
      });
  }

  $scope.$watch('sandbox', function() {
    initController();
  });

  function initDialog() {
    $scope.selectedQualificationType = undefined;
    $scope.otherExperiments = '';
    $scope.comparator = '';
    $scope.integerValues = '';
    $scope.locales = [];
    $scope.actionsGuarded = '';
  }

  function addLocale() {
    $scope.locales.push({
      country: '',
      subdivision: ''
    });
  }

  function removeLocale(index) {
    $scope.locales.splice(index, 1);
  }

  function addQualificationRequirement() {
    var qualificationRequirement = {
      selectedQualificationType: $scope.selectedQualificationType,
      otherExperiment: (' ' + $scope.otherExperiments).slice(1),
      comparator: $scope.comparator,
      integerValues: (' ' + $scope.integerValues).slice(1),
      locales: $scope.locales.slice(),
      actionsGuarded: $scope.actionsGuarded
    };

    $scope.qualificationRequirements.push(qualificationRequirement);
    $('#addQualificationRequirementDialog').dialog('close');
    console.log('addQualificationRequirement', $scope.qualificationRequirements);
  }

  function removeQualificationRequirement($index) {
    $scope.qualificationRequirements.splice($index, 1);
  }

  function localesToString(locales) {
    var returnString = '';
    for (var i = 0; i < locales.length; i++) {
      var locale = locales[i];
      returnString += locale.country + ((locale.subdivision.trim().length > 0) ? '-' + locale.subdivision.trim() + ' ' : ' ');
    }
    return returnString;
  }

  function openAddQualificationRequirementDialog() {
    initDialog();
    $('#addQualificationRequirementDialog').dialog({
      title: 'Add qualification requirement',
      width: '800px',
      position: { my: "top", at: "top", of: window }
    });
  }
}

SelectQualificationsCtrl.$inject = ['$scope', 'ManageQualificationsSrv'];

export default SelectQualificationsCtrl;
