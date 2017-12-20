import DefaultGraph from '../client/client-graph';
ClientGraphService.$inject = ['$q', 'scriptInjector', 'configService'];
export default function ClientGraphService($q, scriptInjector, configService){
  /**
   * Call this method and wait to load the graph
   * @returns {Promise<Graph>} A promise that contains the graph constructor
   */
  this.load = function(){
    return configService.get('clientGraph')
      .then(contents => {
        console.log("Evaluating in global scope", contents);
        return scriptInjector.injectScript(contents);
      })
      .then(() => {
        return window.Graph;
      }, err => {
        return DefaultGraph;
      });
  }
}