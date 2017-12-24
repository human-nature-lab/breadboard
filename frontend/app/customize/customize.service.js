angular.module('breadboard.customize.service', [])
  .factory('CustomizeSrv', CustomizeSrv);

CustomizeSrv.$inject = ['$http', '$q'];

function CustomizeSrv($http, $q) {
  let service = {
    getClientHtml: getClientHtml,
    getClientGraph: getClientGraph,
    getStyle: getStyle,
    updateClientHtml: updateClientHtml,
    updateClientGraph: updateClientGraph,
    updateStyle: updateStyle
  };

  return service;

  function getClientHtml(experimentId) {
    return $http.get('customize/clientHtml/' + experimentId)
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

  function getClientGraph(experimentId) {
    return $http.get('customize/clientGraph/' + experimentId)
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

  function getStyle(experimentId) {
    return $http.get('customize/style/' + experimentId)
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

  function updateClientHtml(experimentId, clientHtml) {
    const payload = {
      'clientHtml': clientHtml
    };
    return $http.post('customize/clientHtml/' + experimentId, payload)
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

  function updateClientGraph(experimentId, clientGraph) {
    const payload = {
      'clientGraph': clientGraph
    };
    return $http.post('customize/clientGraph/' + experimentId, payload)
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

  function updateStyle(experimentId, style) {
    const payload = {
      'style': style
    };
    return $http.post('customize/style/' + experimentId, payload)
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

