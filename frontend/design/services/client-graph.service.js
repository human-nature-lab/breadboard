import DefaultGraph from '../client/client-graph'
export default function ClientGraphService(){
  /**
   * Call this method and wait to load the graph
   * @returns {Promise<Graph>} A promise that contains the graph constructor
   */
  this.load = function () {
    if (window.Graph) {
      return window.Graph
    } else {
      return DefaultGraph
    }
  }
}
