/* global Breadboard */
export default function ConfigService () {
  /**
   * Should be used to access all configuration properties set by the /state route
   * @param {string} key - name of the parameter to get
   * @returns {Promise}
   */
  this.get = function (key) {
    return Breadboard.loadConfig()
      .then(function (state) {
        return state[key]
      }, function (err) {
        throw err
      })
  }

  /**
   * Returns a copy of the configuration object
   * @returns {Promise}
   */
  this.all = function () {
    //return $q.when(configPromise)
    //return configPromise
    return Breadboard.loadConfig()
      .then(function (state) {
        return state
      }, function (err) {
        throw err
      })
  }
}
