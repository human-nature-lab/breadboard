ConfigService.$inject = ['$http', '$q'];
export default function ConfigService($http, $q){
  let config = null;

  // Makes the initial request for the configuration parameters
  const configPromise = $http.get('state')
    .then(function(res){
      config = res.data;
      return config;
    }, function(err){
      console.error("Unable to load client configuration", err);
    });

  this.hasLoaded = function(){
    return $q.when(configPromise);
  };

  /**
   * Should be used to access all configuration properties set by the /state route
   * @param {string} key - name of the parameter to get
   * @returns {Promise}
   */
  this.get = function(key){
    return $q.when(configPromise)
      .then(function(){
        return config[key];
      }, function(err){
        throw err;
      });
  };

  /**
   * Returns a copy of the configuration object
   * @returns {Promise}
   */
  this.all = function(){
    return $q.when(configPromise)
      .then(function(){
        return Object.assign({}, config);
      }, function(err){
        throw err;
      })
  };

  /**
   * Set a configuration parameter. This is only set in memory and is not persisted
   * @param {string} key - name of the configuration key
   * @param val - value to set it to
   * @returns {Promise}
   */
  this.set = function(key, val){
    return $q.when(configPromise)
      .then(function(){
        config[key] = val;
        return val;
      }, function(err){
        throw err;
      });
  }
}