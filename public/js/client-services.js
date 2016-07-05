'use strict';

/* Services */

angular.module('client.services', [], function($provide) {
    $provide.factory('websocketFactory', function($window) {
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
    });

    $provide.factory('clientFactory', ['websocketFactory', '$rootScope', function($websocketFactory, $rootScope) {

        var websocket = $websocketFactory(clientVars.connectSocket);
        websocket.onopen = function (evt) {
            websocket.send(JSON.stringify( 
            	{"action" : "LogIn", 
            	"clientId" : clientVars.clientId, 
            	"referer" : clientVars.referer, 
            	"connection" : clientVars.connection, 
            	"accept" : clientVars.accept, 
            	"cacheControl" : clientVars.cacheControl, 
            	"acceptCharset" : clientVars.acceptCharset, 
            	"cookie" : clientVars.cookie, 
            	"acceptLanguage" : clientVars.acceptLanguage, 
            	"acceptEncoding" : clientVars.acceptEncoding, 
            	"userAgent" : clientVars.userAgent, 
            	"host" : clientVars.host,
                "ipAddress": clientVars.ipAddress,
                "requestURI": clientVars.requestURI
            	})
            );
        };

        /*
        websocket.onerror = function(evt) {
            console.log("error:", evt);
        };

        websocket.onclose = function(evt) {
            console.log("close:", evt);
        };
        */

        return {
            onmessage: function (callback) {
                websocket.onmessage = function() {
                    var args = arguments;

                    $rootScope.$apply(function () {
                        callback.apply(websocket, args);
                    });

                };
            },
            send: function (message) {
                var jsonMessage = JSON.stringify(message);
                websocket.send(jsonMessage);
            }
        }
    }]);

});

