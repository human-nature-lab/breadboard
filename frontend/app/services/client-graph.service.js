import DefaultGraph from '../client/client-graph';
ClientGraphService.$inject = ['$q', 'scriptInjector', 'configService'];
export default function ClientGraphService($q, scriptInjector, configService){
  /**
   * Call this method and wait to load the graph
   * @returns {Promise<Graph>} A promise that contains the graph constructor
   */
  this.load = function(){
    let url;
    return configService.get('graphLocation')
      .then(uri => {
        url = uri; // hoisted for error reporting
        return scriptInjector.injectScript(uri);
      })
      .then(() => {
        return window.Graph;
      }, err => {
        console.error("Unable to inject script from", url, "because:", err);
        return DefaultGraph;
      });
  }
}