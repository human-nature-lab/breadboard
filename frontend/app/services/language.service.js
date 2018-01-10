LanguageService.$inject = ['$http', '$q'];
export default function LanguageService($http, $q){
  let languages = null;

  // Makes the initial request for the languages
  const languagePromise = $http.get('languages')
    .then(function(res){
      languages = res.data;
      return languages;
    }, function(err){
      console.error("Unable to retrieve available languages", err);
    });

  /**
   * Returns a copy of the languages object
   * @returns {Promise}
   */
  this.all = function(){
    return $q.when(languagePromise)
      .then(function(){
        return Object.assign({}, languages);
      }, function(err){
        throw err;
      })
  };

  /**
   * Adds a language to the experiments_languages table
   * @returns {Promise}
   */
  this.addLanguage = function(experimentId, languageId) {
    console.log('language.service', languageId);
    const payload = {
      'experimentId': experimentId,
      'languageId': languageId
    };
    return $http.post('languages', payload)
      .then(function (response) {
          if (response.status < 400) {
            return $q.when(response);
          }
          return $q.reject(response);
        },
        function (response) {
          return $q.reject(response);
        });
  };

  /**
   * Removes a language from the experiments_languages table
   * @returns {Promise}
   */
  this.removeLanguage = function(experimentId, languageId) {
    const payload = {
      'experimentId': experimentId,
      'languageId': languageId
    };
    return $http.post('languages/remove', payload)
      .then(function (response) {
          if (response.status < 400) {
            return $q.when(response);
          }
          return $q.reject(response);
        },
        function (response) {
          return $q.reject(response);
        });
  };
}