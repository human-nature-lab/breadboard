WebsocketFactory.$inject = ['$window'];
export default function WebsocketFactory($window){
  var wsClass;

  if ('WebSocket' in $window)
  {
    wsClass = WebSocket;
  }
  else if ('MozWebSocket' in $window)
  {
    wsClass = MozWebSocket;
  }

  return wsClass
    ? function(url) { return new wsClass(url); }
    : undefined;
}