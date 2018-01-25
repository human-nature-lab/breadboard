function Graph(clientId, parentElement) {

  var width = parentElement ? parentElement.clientWidth : 600;
  var height = parentElement ? parentElement.clientHeight : 600;
  var egoNodeR = 50;
  var alterNodeR = 30;
  var arrowPadding = 7;
  var graphPadding = 10;
  var linkDistance = (Math.min(width, height) / 2) - alterNodeR - (2 * graphPadding);
  console.log('parent', parentElement, width, height);

  var ignoreProps = ["$$hashKey", "text", "choices", "x", "y", "px", "py"];

  var div = parentElement ? d3.select(parentElement) : d3.select("#graph");
  var vis = div.append("svg:svg")
    .attr("viewBox", "0 0 600 600")

  // set up arrow markers for graph links
  // Thanks to rkirsling for the example here: http://bl.ocks.org/rkirsling/5001347
  vis.append('svg:defs').append('svg:marker')
    .attr('id', 'end')
    .attr('viewBox', '0 -5 10 10') //'0 -5 10 10'
    .attr('refX', 6)
    .attr('markerWidth', 4)
    .attr('markerHeight', 4)
    .attr('orient', 'auto')
    .append('svg:path')
    .attr('d', 'M0,-5L10,0L0,5')
    .attr('fill', '#333');

  vis.append('svg:defs').append('svg:marker')
    .attr('id', 'start')
    .attr('viewBox', '0 -5 10 10') //'0 -5 10 10'
    .attr('refX', 4)
    .attr('markerWidth', 4)
    .attr('markerHeight', 4)
    .attr('orient', 'auto')
    .append('svg:path')
    .attr('d', 'M10,-5L0,0L10,5')
    .attr('fill', '#333');

  vis.append('svg:defs').append('svg:marker')
    .attr('id', 'end-green')
    .attr('viewBox', '0 -5 10 10')//'0 -5 10 10'
    .attr('refX', 6)
    .attr('markerWidth', 4)
    .attr('markerHeight', 4)
    .attr('orient', 'auto')
    .append('svg:path')
    .attr('d', 'M0,-5L10,0L0,5')
    .attr('fill', 'green');

  vis.append('svg:defs').append('svg:marker')
    .attr('id', 'end-red')
    .attr('viewBox', '0 -5 10 10')//'0 -5 10 10'
    .attr('refX', 6)
    .attr('markerWidth', 4)
    .attr('markerHeight', 4)
    .attr('orient', 'auto')
    .append('svg:path')
    .attr('d', 'M0,-5L10,0L0,5')
    .attr('fill', 'red');

  vis.append('svg:defs').append('svg:marker')
    .attr('id', 'start-green')
    .attr('viewBox', '0 -5 10 10')//'0 -5 10 10'
    .attr('refX', 4)
    .attr('markerWidth', 4)
    .attr('markerHeight', 4)
    .attr('orient', 'auto')
    .append('svg:path')
    .attr('d', 'M10,-5L0,0L10,5')
    .attr('fill', 'green');

  vis.append('svg:defs').append('svg:marker')
    .attr('id', 'start-red')
    .attr('viewBox', '0 -5 10 10')//'0 -5 10 10'
    .attr('refX', 4)
    .attr('markerWidth', 4)
    .attr('markerHeight', 4)
    .attr('orient', 'auto')
    .append('svg:path')
    .attr('d', 'M10,-5L0,0L10,5')
    .attr('fill', 'red');

  var force = d3.layout.force()
    .gravity(.05)
    .friction(0.8)
    .charge(-10000) //-500
    .linkStrength(10) //2
    .linkDistance(linkDistance * 0.9)
    .size([width, height]);

  var nodes = force.nodes(),
    links = force.links();

  force.on("tick", function () {
    vis.selectAll("line.link")
      .attr("x1", function (d) {
        var deltaX = d.target.x - d.source.x,
          deltaY = d.target.y - d.source.y,
          dist = Math.sqrt(deltaX * deltaX + deltaY * deltaY),
          normX = deltaX / dist,
          sourcePadding = (d.source.id == clientId) ? egoNodeR : alterNodeR;
        if (d.arrow && d.arrow.length > 0) {
          sourcePadding += arrowPadding;
        }
        return d.source.x + (sourcePadding * normX);
      })
      .attr("y1", function (d) {
        var deltaX = d.target.x - d.source.x,
          deltaY = d.target.y - d.source.y,
          dist = Math.sqrt(deltaX * deltaX + deltaY * deltaY),
          normY = deltaY / dist,
          sourcePadding = (d.source.id == clientId) ? egoNodeR : alterNodeR;
        if (d.arrow && d.arrow.length > 0) {
          sourcePadding += arrowPadding;
        }
        return d.source.y + (sourcePadding * normY);
      })
      .attr("x2", function (d) {
        var deltaX = d.target.x - d.source.x,
          deltaY = d.target.y - d.source.y,
          dist = Math.sqrt(deltaX * deltaX + deltaY * deltaY),
          normX = deltaX / dist,
          targetPadding = (d.target.id == clientId) ? egoNodeR : alterNodeR;
        if (d.arrow && d.arrow.length > 0) {
          targetPadding += arrowPadding;
        }
        return targetX = d.target.x - (targetPadding * normX);
      })
      .attr("y2", function (d) {
        var deltaX = d.target.x - d.source.x,
          deltaY = d.target.y - d.source.y,
          dist = Math.sqrt(deltaX * deltaX + deltaY * deltaY),
          normY = deltaY / dist,
          targetPadding = (d.target.id == clientId) ? egoNodeR : alterNodeR;
        if (d.arrow && d.arrow.length > 0) {
          targetPadding += arrowPadding;
        }
        return targetY = d.target.y - (targetPadding * normY);
      });

    vis.selectAll("g.node")
      .attr("transform", function (d) {
        return "translate(" + d.x + "," + d.y + ")"
      });
  });

  var removeNode = function (nid) {
    var nodeIndex = findNode(nid);
    if (nodeIndex > -1) {
      nodes.splice(i, 1);
    }
  }

  var findNode = function (nid) {
    for (var i = 0; i < nodes.length; i++) {
      if (nodes[i].id == nid) {
        return nodes[i];
      }
    }
    return null;
  }

  var addLink = function (link, sourceId, targetId) {
    link.source = findNode(sourceId);
    link.target = findNode(targetId);
    links.push(link);
  }

  var updateLink = function (oldLink, newLink, sourceId, targetId) {
    _.extend(oldLink, newLink);
    oldLink.source = findNode(sourceId);
    oldLink.target = findNode(targetId);
  }

  this.updateGraph = function (newGraph) {

    if (newGraph == undefined)
      return;

    if (newGraph.nodes == undefined || newGraph.nodes.length == 0) {
      // Remove all nodes
      nodes.length = 0;
    } else {
      // If there is anything in the old array that isn't in the new, it needs to be removed
      for (var i = nodes.length - 1; i >= 0; i--) {
        if (_.find(newGraph.nodes, function (n) {
            return n.id === nodes[i].id;
          }) === undefined) {
          nodes.splice(i, 1);
        }
      }

      // Finally, anything in the new array that isn't in the old needs to be added
      for (var i = 0; i < newGraph.nodes.length; i++) {
        var oldNode = _.find(nodes, function (n) {
          return n.id === newGraph.nodes[i].id;
        });
        if (oldNode === null || oldNode === undefined) {
          nodes.push(newGraph.nodes[i]);
        } else {
          // Update the old node
          _.extend(oldNode, newGraph.nodes[i]);
        }
      }
    }

    if (newGraph.links == undefined || newGraph.links.length == 0) {
      // Remove all links
      links.length = 0;
    } else {
      // If there is anything in the old array that isn't in the new, it needs to be removed
      for (var i = links.length - 1; i >= 0; i--) {
        // source or target could have been removed at this point
        var sourceId = (links[i].source == undefined) ? null : links[i].source.id;
        var targetId = (links[i].target == undefined) ? null : links[i].target.id;

        try {
          if (_.find(newGraph.links, function (l) {
              return ((newGraph.nodes[l.source].id === sourceId) && (newGraph.nodes[l.target].id === targetId));
            }) === undefined) {
            links.splice(i, 1);
          }
        } catch (e) {
          //TODO: Why is there an exception being thrown here?
        }
      }

      // Finally, anything in the new array that isn't in the old needs to be added
      for (var i = 0; i < newGraph.links.length; i++) {
        var sourceIdx, targetIdx, source, target, sourceId, targetId = undefined;
        var link = newGraph.links[i];
        if (link != undefined) {
          sourceIdx = link.source;
          targetIdx = link.target;
        }

        if (sourceIdx != undefined && targetIdx != undefined) {
          source = newGraph.nodes[sourceIdx];
          target = newGraph.nodes[targetIdx];
        }

        if (source != undefined && target != undefined) {
          sourceId = source.id;
          targetId = target.id;
        }

        if (sourceId != undefined && targetId != undefined) {
          var oldLink = _.find(links, function (l) {
            return ((l.target.id === targetId) && (l.source.id === sourceId));
          });
          if (oldLink === null || oldLink === undefined) {
            addLink(link, sourceId, targetId);
          } else {
            // Update the old link
            updateLink(oldLink, link, sourceId, targetId);
          }
        }
      }
    }

    update();
  };

  var animateScore = function (amount, start, end, endNodeId) {
    //console.log("animateScore(" + amount + ", " + start + ", " + end + ", " + endNodeId + ")");

    var animText = vis.append("svg:text")
      .attr("class", "anim")
      .style("text-anchor", "middle")
      .style("fill", "#1EFF1E")
      .style("stroke", "#000")
      .style("font-family", "Lucida Sans")
      .style("font-weight", "bold")
      .style("font-size", 18)
      .text(((amount < 0) ? "" : "+") + amount)
      .attr("x", start.x)
      .attr("y", start.y)
      .transition()
      .duration(1500)
      .attr("x", end.x)
      .attr("y", end.y)
      .remove();

  }

  var update = function () {
    var link = vis.selectAll("line.link")
      .data(links, function (d) {
        return d.id;
      });

    link.enter().insert("svg:line", "g.node")
      .attr("class", "link client");

    link.exit().remove();

    var g = vis.selectAll("g.node")
      .data(nodes, function (d) {
        return d.id;
      });

    var gEnter = g.enter().append("svg:g")
      .attr("class", "node client");
    //.attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });

    gEnter.append("svg:text")
      .attr("class", "node client")
      .attr("r", 50)
      .style("text-anchor", "middle")
      .style("font-size", function (d) {
        return (d.id == clientId) ? "18pt" : "14pt"
      })
      .text(function (d) {
        return (d.score == undefined) ? "" : d.score;
      });

    gEnter.insert("svg:circle", "text.node")
      .attr("class", "node client")
      .attr("r", function (d) {
        return (d.id == clientId) ? egoNodeR : alterNodeR;
      })
      .each(function (d) {
        if (d.id == clientId) {
          d.fixed = true;
          d.x = width / 2;
          d.y = height / 2;
        }
      });

    g.exit().remove();


    var scoreText = vis.selectAll("text.node");

    scoreText.text(function (d) {
      return (d.score == undefined) ? "" : d.score;
    });

    force
      .nodes(nodes)
      .links(links)
      .start();

    var node = g.selectAll("circle.node");

    d3.selectAll("circle.node").each(function (d, i) {
      for (var propertyName in d) {
        if (_.indexOf(ignoreProps, propertyName) == -1 && isNaN(propertyName)) {
          d3.select(this).attr(propertyName, d[propertyName]);
        }
      }
    });

    d3.selectAll("line.link").each(function (d, i) {
      d3.select(this).attr("marker-start", null);
      d3.select(this).attr("marker-end", null);

      for (var propertyName in d) {
        if (propertyName == "arrow") {
          var arrowParams = d.arrow.split(",");
          if (arrowParams.length < 1) {
            return;
          }

          if (d.source.id == arrowParams[0] || arrowParams[0] == "both") {
            if (arrowParams.length > 1 && arrowParams[1] != "grey") {
              d3.select(this).attr("marker-start", "url(#start-" + arrowParams[1] + ")")
            } else {
              d3.select(this).attr("marker-start", "url(#start)")
            }
          } else {
            d3.select(this).attr("marker-start", null);
          }

          if (d.target.id == arrowParams[0] || arrowParams[0] == "both") {
            if (arrowParams.length > 1 && arrowParams[1] != "grey") {
              d3.select(this).attr("marker-end", "url(#end-" + arrowParams[1] + ")")
            } else {
              d3.select(this).attr("marker-end", "url(#end)")
            }
          } else {
            d3.select(this).attr("marker-end", null);
          }

        } else if (_.indexOf(ignoreProps, propertyName) == -1) {
          d3.select(this).attr(propertyName, d[propertyName]);
        }
      }
    });


    var setupAnim = function () {
      link.each(function (d) {
        var animate = d3.select(this).attr("animate");
        if (animate != undefined) {
          var params = animate.split(",");
          if (params.length > 3) {
            var round = params[0];
            var amount = params[1];
            var startNodeId = params[2];
            var endNodeId = params[3];

            var startNode = findNode(startNodeId);
            var endNode = findNode(endNodeId);

            var start = {"x": startNode.x, "y": startNode.y};
            var end = {"x": endNode.x, "y": endNode.y};

            if ((d.animated != animate) && start != undefined && end != undefined && endNodeId != undefined) {
              d.animated = animate;
              animateScore(amount, start, end, endNodeId);
            }
          }
        }
      });
    };

    var t = setTimeout(setupAnim, 1000);

  };
}