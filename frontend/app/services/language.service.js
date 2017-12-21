LanguageService.$inject = ['$http', '$q'];
export default function LanguageService($http, $q){
  let languages = null;

  // Makes the initial request for the configuration parameters
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
}