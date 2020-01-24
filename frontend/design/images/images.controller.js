function ImagesCtrl($scope, ImagesSrv, $timeout) {
  const vm = this;

  vm.readOnly = $scope.readOnly;
  vm.experiment = $scope.experiment;
  vm.uploadImage = null;
  vm.removedId = false;
  vm.createdId = false;
  vm.overDelete = false;
  vm.createImage = createImage;
  vm.deleteImage = deleteImage;


  function createImage() {
    if (!vm.uploadImage) return;
    ImagesSrv.uploadImage($scope.experiment.id, vm.uploadImage)
      .then(
        function(success) {
          $scope.experiment.images.unshift(success.data);
          vm.uploadImage = null;
          vm.createdId = success.data.id;
          $timeout(function() {
            vm.createdId = false;
          }, 1500);
        },
        function(error) {
          alert("Unable to upload the photo. Please select a new photo.");
          vm.uploadImage = null;
        });

  }

  function deleteImage(imageId) {
    vm.removedId = imageId;
    ImagesSrv.removeImage(imageId)
      .then(
        function(success) {
          $timeout(function() {
            vm.removedId = false;
            let imageIndex = -1;
            for (let i = 0; i < $scope.experiment.images.length; i++) {
              if ($scope.experiment.images[i].id === imageId) {
                imageIndex = i;
                break;
              }
            }
            $scope.experiment.images.splice(imageIndex, 1);
          }, 750);
        },
        function(error) {
          alert("Unable to delete the photo.");
          vm.removedId = false;
        });
  }
}

ImagesCtrl.$inject = ['$scope', 'ImagesSrv', '$timeout'];

export default ImagesCtrl;
