import './websocket.factory';
import 'angular';

BreadboardFactory.$inject = ['websocketFactory', '$rootScope', '$cookieStore', '$http', '$q', 'configService'];
export default function BreadboardFactory($websocketFactory, $rootScope, $cookieStore, $http, $q, configService) {

  let websocket;

  function websocketRoute(){
    let uri = '';
    if(window.location.protocol = 'https:'){
      uri = 'wss:';
    } else {
      uri = 'ws:';
    }
    uri += "//" + loc.host + '/connect';
    // uri += window.location.pathname;
    return uri;
  }

  function processMessage(data, callback) {
    if (data.action !== undefined) {
      const g = $rootScope.$$childHead.breadboardGraph;
      if(data.action === "addNode")
        g.addNode(data.id);

      if(data.action === "removeNode")
        g.removeNode(data.id);

      if(data.action === "nodePropertyChanged") {
        // TODO: Do we ever send node property values as JSON?
        try {
          let value = JSON.parse(data.value);
        } catch(e) {
          let value = data.value;
        }
        g.nodePropertyChanged(data.id, data.key, value);
      }

      if(data.action === "nodePropertyRemoved")
        g.nodePropertyChanged(data.id, data.key);

      if(data.action === "addLink")
        g.addLink(data.id, data.source, data.target, data.value);

      if(data.action === "removeLink")
        g.removeLink(data.id, data.source, data.target);

      if (data.action === "linkPropertyChanged") {
        g.linkPropertyChanged(data.id, data.key, data.value);
      }
      if (data.action === "linkPropertyRemoved") {
        g.linkPropertyRemoved(data.id, data.key);
      }
    } else {
      $rootScope.$apply(function () {
        console.log("data", data);
        callback(data);
      });
    }
  }

  let socketUrl;
  let configPromise = configService.all().then(config => {
    websocket = $websocketFactory(config.connectSocket);
    websocket.onopen = function (evt) {
      websocket.send(JSON.stringify( {"action" : "LogIn", "uid" : config.uid }) );
    };
  });


  let service = {
    onmessage: function (callback) {
      configService.hasLoaded()
        .then(() => {
          setTimeout(function(){
            websocket.onmessage = function(){
              let args = arguments;
              let data = JSON.parse(args[0].data);
              if (data.queuedMessages != undefined) {
                for (var i = 0; i < data.queuedMessages.length; i++) {
                  processMessage(data.queuedMessages[i], callback);
                }
              } else {
                processMessage(data, callback);
              }
            }
          })
        })
    },
    send: function (message) {
      message.uid = sessionId;
      websocket.send(JSON.stringify(message));
    }
  };

  return service;
}