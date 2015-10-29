'use strict';

/* Services */

angular.module('breadboard.services', ['ui.utils'], function($provide) {
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

    $provide.factory('breadboardFactory', ['websocketFactory', '$rootScope', function($websocketFactory, $rootScope) {

        var websocket = $websocketFactory(routes.connectSocket);
        websocket.onopen = function (evt) {
            websocket.send(JSON.stringify( {"action" : "LogIn", "uid" : sessionId }) );
        };

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

        return {
            onmessage: function (callback) {
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
            },
            send: function (message) {
                message.uid = sessionId;
                websocket.send(JSON.stringify(message));
            }
        }
    }]);

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

