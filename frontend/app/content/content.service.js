angular.module('breadboard.content.service', [])
  .factory('ContentSrv', ContentSrv);

ContentSrv.$inject = ['$http', '$q'];

function ContentSrv($http, $q) {
  let service = {
    getContent: getContent,
    updateContent: updateContent,
    createContent: createContent,
    deleteContent: deleteContent
  };

  return service;

  function getContent(experimentId) {
    return $http.get('content/' + experimentId)
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

  function updateContent(experimentId, content) {
    let translations = [];

    angular.forEach(content.translations, function(t) {
      translations.push({
        'id': t.id,
        'html': t.clientHtml,
        'language': t.language
      });
    });

    const payload = {
      'experimentId': experimentId,
      'contentId': content.id,
      'translations': translations,
      'name': content.name
    };
    return $http.post('content/' + content.id, payload)
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

  function createContent(experimentId, content) {
    const payload = {
      'name': content.name,
      'translations': content.translations
    };
    return $http.put('content/' + experimentId, payload)
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

  function deleteContent(contentId) {
    return $http.delete('content/' + contentId)
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

