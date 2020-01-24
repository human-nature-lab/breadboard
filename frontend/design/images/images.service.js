import 'ng-file-upload';

angular.module('breadboard.images.service', ['ngFileUpload'])
  .factory('ImagesSrv', ImagesSrv);

ImagesSrv.$inject = ['$http', '$q', 'Upload'];

function ImagesSrv($http, $q, Upload) {
  return {
    uploadImage: uploadImage,
    removeImage: removeImage
  };

  function uploadImage(experimentId, uploadFile) {
    return Upload.upload({
      url: 'images/upload',
      data: {
        picture: uploadFile,
        experimentId: experimentId
      }
    }).then(function (resp) {
      if (resp.data === 'Error uploading') {
        return $q.reject(resp);
      }
      return $q.when(resp);
    }, function (err) {
      return $q.reject(err);
    }, function (evt) {
      console.log("progress", evt);
    });
  }

  function removeImage(imageId) {
    return $http.delete('images/' + imageId)
      .then(function (response) {
          if (response.status < 400) {
            return $q.when(response);
          }
          return $q.reject(response);
        },
        function (response) {
          return $q.reject(response);
        });
  }
}

