'use strict';

/* Services */

angular.module('breadboard.services', ['ui.utils', 'ngCookies'], function($provide) {

    $provide.value('uiJqConfig', {
        dialog: {
            closeOnEscape: false,
            width: 500,
            height: 300,
            close: function(event, ui) { dockWindow($(this), $(this).dialog("option", "title")); },
            create: function(event, ui) { if (! $(this).dialog("option", "autoOpen") ) dockWindow($(this), $(this).dialog("option", "title")); }
        }
    });

});


angular.module('breadboard.services')
  .service('websocketFactory', ['$window', function($window) {
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
  }])
  .factory('breadboardFactory', ['websocketFactory', '$rootScope', '$cookieStore', '$http', '$q', function($websocketFactory, $rootScope, $cookieStore, $http, $q) {

    function websocketRoute(){
      var uri = '';
      if(window.location.protocol = 'https:'){
        uri = 'wss:';
      } else {
        uri = 'ws:';
      }
      uri += "//" + loc.host + '/connect';
      // uri += window.location.pathname;
      return uri;
    }

    // websocketRoute will work once the javascript application is serving from the same port

    var websocket;
    var sessionId;
    function makeWebsocket(){
      websocket = $websocketFactory('ws://localhost:9000/connect');
      sessionId = $cookieStore.get('uid');
      websocket.onopen = function (evt) {
        websocket.send(JSON.stringify( {"action" : "LogIn", "uid" : sessionId }) );
      };
    }

    var statePromise = $http.get('/state')
        .then(function (res) {
          $cookieStore.put('juid', res.data.juid);
          $cookieStore.put('email', res.data.email);
          $cookieStore.put('uid', res.data.uid);
          makeWebsocket();
        }, function (err) {
          console.error(err);
        });

    var service = {
      onmessage: function (callback) {

        var processMessage = function(data, callback) {
          if (data.action != undefined) {
            var g = $rootScope.$$childHead.breadboardGraph;
            if(data.action == "addNode")
              g.addNode(data.id);

            if(data.action == "removeNode")
              g.removeNode(data.id);

            if(data.action == "nodePropertyChanged") {
              // TODO: Do we ever send node property values as JSON?
              try {
                var value = JSON.parse(data.value);
              } catch(e) {
                var value = data.value;
              }
              g.nodePropertyChanged(data.id, data.key, value);
            }

            if(data.action == "nodePropertyRemoved")
              g.nodePropertyChanged(data.id, data.key);

            if(data.action == "addLink")
              g.addLink(data.id, data.source, data.target, data.value);

            if(data.action == "removeLink")
              g.removeLink(data.id, data.source, data.target);

            if (data.action == "linkPropertyChanged") {
              g.linkPropertyChanged(data.id, data.key, data.value);
            }
            if (data.action == "linkPropertyRemoved") {
              g.linkPropertyRemoved(data.id, data.key);
            }
          } else {
            $rootScope.$apply(function () {
              console.log("data", data);
              callback(data);
            });
          }
        };

        $q.when(statePromise).then(function(){
          websocket.onmessage = function() {
            //console.log("onmessage");
            var args = arguments;
            var data = JSON.parse(args[0].data);

            if (data.queuedMessages != undefined) {
              for (var i = 0; i < data.queuedMessages.length; i++) {
                processMessage(data.queuedMessages[i], callback);
              }
            } else {
              processMessage(data, callback);
            }

          };
        })
      },
      send: function (message) {
        message.uid = sessionId;
        websocket.send(JSON.stringify(message));
      }
    };

    return service;
  }]);

