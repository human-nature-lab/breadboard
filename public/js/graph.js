function Graph(_width, _height, parentScope) {

	var width = (_width == undefined) ? 600 : _width;
	var height = (_height == undefined) ? 600 : _width;
	var nodeR = width / 90;
	var linkW = width / 300;
	var linkDistance = width / 12;
	var color = d3.scale.category20();
	var parentScope = parentScope;
	// Properties not written to the DOM
	var ignoreProps = ["text"];
	var curScale = 1;

	// Hashtables of link and node keys
	var nodeHash = {};
	var linkHash = {};

	// set up initial svg object
	var div = d3.select("#graphDiv");
	var svg = div.append("svg:svg");
	svg.attr("width", "100%");
	svg.attr("height", "100%");
	var defs = svg.append("svg:defs");
	var radialGradient = defs.append("svg:radialGradient");
	radialGradient.attr("id", "circleGradient");
  var stop1 = radialGradient.append("svg:stop");
  stop1.attr("offset", "0%"); 
  stop1.attr("stop-color", "currentColor"); 
  var stop2 = radialGradient.append("svg:stop");
  stop2.attr("offset", "100%"); 
  stop2.attr("stop-color", "#000"); 
	var vis = svg.append("svg:g");
	vis.attr("width", "100%");
	vis.attr("height", "100%");

	div.call(d3.behavior.zoom()
		.scaleExtent([0.1, 20])
		.on("zoom", function() {
			vis.attr("transform", "translate(" + d3.event.translate + ")scale(" + d3.event.scale + ")");
			if (d3.event.scale > 1) {
        vis.selectAll("circle.node").attr("r", nodeR/d3.event.scale);
      }
			curScale = d3.event.scale;
			// This makes the zoom change the linkDistance more than it changes the size of the nodes, the factor of 0.5 may need to be adjusted
			// The bouncing around is annoying me, consider re-enabling this if necessary:  
			// force.linkDistance(linkDistance * d3.event.scale * 0.5).start();
		}));

	/*
	vis.append("svg:rect")
		.attr("width", width)
		.attr("height", height)
		.attr("fill", "#F0F0F0");
	*/

	var force = d3.layout.force()
		.gravity(.05)
		.charge(-1000)
		.linkDistance(linkDistance)
		.size([width, height]);

	var nodes = force.nodes(),
		links = force.links();

	force.on("tick", function() 
	{
		vis.selectAll("line.link")
			.attr("x1", function(d) { return d.source.x; })
			.attr("y1", function(d) { return d.source.y; })
			.attr("x2", function(d) { return d.target.x; })
			.attr("y2", function(d) { return d.target.y; });
	
		vis.selectAll("circle.node")
			.attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")" });
	});

	this.addNode = function(id, properties) {
		console.log("got here");
		try {
			var node = {"id":id};
			if (properties != undefined) {
			    for (p in properties) {
			        _.extend(node, p); 
			    }
			}
			nodeHash[id] = node;
			nodes.push(nodeHash[id]);
			update();
		} catch (e) {
            console.log("Error in addNode: " + e.toString());
        }
	}

	this.removeNode = function(id) {
		try {
			/*
			var i = 0;
			var n = findNode(id);
			while (i < links.length) {
				if ((links[i]['source'] == n) || (links[i]['target'] == n)) {
					links.splice(i, 1);	
				}
				else i++;
			}
			nodes.splice(findNodeIndex(id), 1);
			*/
			nodes.splice(nodes.indexOf(findNode(id)), 1);
			delete nodeHash[id];
			update();
		} catch (e) {
            console.log("Error in removeNode: " + e.toString());
        }
	}

	this.nodePropertyChanged = function(id, key, value) {
		var node = findNode(id);
		node[key] = value;
		if (_.indexOf(ignoreProps, key) == -1) {
			vis.selectAll("circle.node").attr(key, function(d, i) {
				//console.log("setting node attribute " + key + " + to " + d[key]);
				return d[key];
			});
		}
		// If the node properties change for the selected node, update AngularJS
		if (parentScope != undefined && parentScope.selectedNode != undefined) {
			if (parentScope.selectedNode.id == id) {
				parentScope.$apply();
			}
		}
	}

	this.nodePropertyRemoved = function(id, key) {
		var node = findNode(id);
		node[key] = null;
		vis.selectAll("circle.node").attr(key, null); 
		// If the node properties change for the selected node, update AngularJS
		if (parentScope != undefined && parentScope.selectedNode != undefined) {
			if (parentScope.selectedNode.id == id) {
				parentScope.$apply();
			}
		}
	}

	this.addLink = function (id, source, target, value) {
		try {
			var sourceNode = findNode(source);
			var targetNode = findNode(target);
			var link = {"id":id, "source":sourceNode, "target":targetNode, "value":value, "selected":(sourceNode["selected"] == "1" || targetNode["selected"] == "1") ? "1" : "0"};

			// If the target or source node is currently selected, set the link to be selected as well.
			//if (sourceNode.attr("selected") == "1" || targetNode.attr("selected") == "1") 
				//link["selected"] = "1";
				//d3.select(link).attr("selected", "1");

			//links.push({"source":findNode(source), "target":findNode(target), "value":value});
			linkHash[id] = link;
			//linkHash[id].sourceNode = sourceNode;
			//linkHash[id].targetNode = sourceNode;
			links.push(linkHash[id]);
			update();
		} catch (e) {
            console.log("Error in addLink: " + e.toString());
        }
	}

	this.removeLink = function(id, source, target) {
		try {
			/*
			for (var i=0; i < links.length; i++) {
				if (links[i].source.id == source && links[i].target.id == target) {
					links.splice(i, 1);
					break;
				}
			}
			*/
			var link = linkHash[id];
			links.splice(links.indexOf(link), 1);
			delete linkHash[id];
			update();
		} catch (e) {
            console.log("Error in removeLink: " + e.toString());
        }
	}

	this.linkPropertyChanged = function(id, key, value) {
        var link = findLink(id);
        link[key] = value;
        if (_.indexOf(ignoreProps, key) == -1) {
            // Performance bottleneck here:
            //vis.selectAll("line.link").attr(key, function(d, i) {
            vis.select("#link_" + id).attr(key, function(d, i) {
                //console.log("setting node attribute " + key + " + to " + d[key]);
                return d[key];
            });
        }
	}

	this.linkPropertyRemoved = function(id, key) {
        var link = findLink(id);
        link[key] = null;
        vis.selectAll("line.link").attr(key, null);
	}

	var findNode = function(id) {
		/*
		try {
			for (var i in nodes) {
				if (nodes[i]["id"] == id) return nodes[i];
			}
		} catch (e) {
            console.log("Error in findNode: " + e.toString());
        }
        */
        return nodeHash[id];
	}

	var findNodeIndex = function(id) {
		try {
			for (var i=0; i < nodes.length; i++) {
				if (nodes[i].id==id) {
					return i;
				}	
			}
		} catch (e) {
            console.log("Error in findNodeIndex: " + e.toString());
        }
	}

    var findLink = function(id) {
        return linkHash[id];
    }

    var findLinkIndex = function(id) {
        try {
            for (var i=0; i < links.length; i++) {
                if (links[i].id==id) {
                    return i;
                }
            }
        } catch (e) {
            console.log("Error in findLinkIndex: " + e.toString());
        }
    }

	var update = function () {

		try {

			console.log("update");

			var link = vis.selectAll("line.link")
				.data(links, function(d) { return d.id; });

			link.enter().insert("svg:line", "circle.node")
			  .attr("id", function(d) { return "link_" + d.id; })
				.attr("class", "link")
				.attr("vector-effect", "non-scaling-stroke")
				.attr("selected", function(d) { return d.selected; });

			link.exit().remove();

			var node = vis.selectAll("circle.node")
				.data(nodes, function(d) { return d.id; });

			var nodeEnter = node.enter().append("svg:circle")
				.attr("class", "node")
				.attr("vector-effect", "non-scaling-stroke")
				.attr("r", ((curScale > 1) ? nodeR/curScale : nodeR))
				.call(force.drag);

			node.exit().remove();

			if (parentScope != undefined) {
				node.on('click', function(d) 
				{
					var selectedNode = d3.select(this);
					// if the node is currently selected, deselect it and close the player dialog
					if (selectedNode.attr("selected") == "1") {
						d["selected"] = "0";
						selectedNode.attr("selected", "0");
						vis.selectAll("line.link").attr("selected", "0");
						$('#playerDiv').dialog('close');
						// TODO:  When a node is deselected, the Player dialog should be emptied
						//parentScope.selectedNodeIndex = -1;
					} else {
						vis.selectAll("circle.node").attr("selected", function(d) { d.selected = "0"; return "0"; });
						
						selectedNode.attr("selected", "1");
						d["selected"] = "1";

						var nodeId = d.id;
						vis.selectAll("line.link").attr("selected", function(d, i) {
							if (nodeId == d.source.id || nodeId == d.target.id) {
								return "1";
							} else {
								return "0";
							}
						});

						parentScope.selectedNode = d; 
						parentScope.$apply();

						$('#playerDiv').dialog('open');
						if ($('#closed-dialog-Player').length > 0)
							$('#closed-dialog-Player').remove();
					}
				});
			}

			force
				.nodes(nodes)
				.links(links)
				.start();
		} catch (e) {
            console.log("Error in update: " + e.toString());
        }
		
	}

}
