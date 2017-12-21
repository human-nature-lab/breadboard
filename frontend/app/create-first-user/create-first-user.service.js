angular.module('breadboard.create-first-user.service', [])
  .factory('CreateFirstUserSrv', CreateFirstUserSrv);

CreateFirstUserSrv.$inject = ['$http', '$q'];

function CreateFirstUserSrv($http, $q) {
  let service = {
    createFirstUser: createFirstUser
  };

  return service;

  function createFirstUser(email, password, defaultLanguage) {
    const payload = {
      'email': email,
      'password': password,
      'defaultLanguage': defaultLanguage
    };
    return $http.post('createFirstUser', payload)
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

