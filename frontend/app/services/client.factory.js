ClientFactory.$inject = ['websocketFactory', '$rootScope', '$http', '$q', 'configService'];
export default function ClientFactory($websocketFactory, $rootScope, $http, $q, configService){

  let websocket;
  configService.get('connectSocket').then(connectSocket => {
    websocket = $websocketFactory(connectSocket);
    websocket.onopen = function (evt) {
      configService.all().then(function(clientVars){
        clientVars.action = "LogIn";
        console.log("Sending on open", clientVars);
        websocket.send(JSON.stringify(clientVars));
      })
    };
  });


  return {
    onmessage: function (callback) {
      configService.all().then(() => {
        websocket.onmessage = function() {
          let args = arguments;
          $rootScope.$apply(function () {
            callback.apply(websocket, args);
          });
        };
      });
    },
    send: function (message) {
      let jsonMessage = JSON.stringify(message);
      websocket.send(jsonMessage);
    }
  }
}