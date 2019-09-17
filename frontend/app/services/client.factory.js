ClientFactory.$inject = ['websocketFactory', '$rootScope', '$http', '$q', 'configService'];
export default function ClientFactory($websocketFactory, $rootScope, $http, $q, configService){

  window.Breadboard.connect().then(websocket => {
    websocket.onopen = async function () {
      const clientVars = await configService.all()
      clientVars.action = 'LogIn';
      websocket.send(JSON.stringify(clientVars));
    }
  })

  return {
    async onmessage (callback) {
      const websocket = await window.Breadboard.connect()
      websocket.onmessage = function() {
        let args = arguments;
        $rootScope.$apply(function () {
          callback.apply(websocket, args);
        });
      };
    },
    async send (message) {
      const websocket = await window.Breadboard.connect()
      websocket.send(JSON.stringify(message));
    }
  }
}
