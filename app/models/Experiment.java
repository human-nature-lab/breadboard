package models;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonValue;
import play.libs.Json;
import play.Play;

import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.node.ArrayNode;

import java.util.*;
import java.io.File;
import java.io.IOException;
import javax.persistence.*;

import play.db.ebean.*;
import play.data.format.*;
import play.data.validation.*;

import org.apache.commons.io.FileUtils;

@Entity
@Table(name="experiments")
public class Experiment extends Model
{
    @Id
    public Long id;

    @Constraints.Required
    @Formats.NonEmpty
    public String name;

    @OneToMany(cascade=CascadeType.ALL)
    public List<Step> steps = new ArrayList<Step>();

    @OneToMany(cascade=CascadeType.ALL)
    public List<Content> content = new ArrayList<Content>();

    @OneToMany(cascade=CascadeType.ALL)
    public List<Parameter> parameters = new ArrayList<Parameter>();

    @JsonIgnore
    public ContentFetcher contentFetcher = new ContentFetcher(this);

    @OneToMany(cascade=CascadeType.ALL)
    public List<ExperimentInstance> instances = new ArrayList<ExperimentInstance>();

    @OneToMany(cascade=CascadeType.ALL)
    public List<Image> images = new ArrayList<Image>();

    public static final Long TEST_INSTANCE_ID = 0L;
	  public ExperimentInstance TEST_INSTANCE = null;

    // The AMT QualificationTypeId for the Previous Worker qualification specific to this experiment type.
	  public String qualificationTypeId;
	  // QualificationTypeId in the AMT Sandbox
	  public String qualificationTypeIdSandbox;

    public static final String ON_JOIN_STEP_NAME = "OnJoinStep";
    public static final String ON_LEAVE_STEP_NAME = "OnLeaveStep";

    /*
     * The CSS Style for the experiment
     */
    @Column(columnDefinition="text")
    public String style = "";

    /*
     * The HTML + JavaScript for the client.
     */
    @Column(columnDefinition="text")
    public String clientHtml = "";

    /*
     * The client-graph.js for the client.
     */
    @Column(columnDefinition="text")
    public String clientGraph = "";

    @JsonIgnore
    public static Model.Finder<Long, Experiment> find = new Model.Finder(Long.class, Experiment.class);

    public static List<Experiment> findAll()
    {
        return find.all();
    }

    public static Experiment findByName(String name)
    {
        return find.where().eq("name", name).findUnique();
    }

    public static Experiment findById(Long id)
    {
        return find.where().eq("id", id).findUnique();
    }

    public ExperimentInstance getTestInstance()
	{
		if (TEST_INSTANCE == null) {
			TEST_INSTANCE = new ExperimentInstance("TESTING", this); 	
			TEST_INSTANCE.status = ExperimentInstance.Status.TESTING;
			TEST_INSTANCE.id = TEST_INSTANCE_ID;
        }
        return TEST_INSTANCE;
	}

    public Experiment() {
    }

    /**
     * Copy constructor. Everything is copied except the experimentInstances and name.
     * @param experiment the experiment to be copied from
     */
    public Experiment(Experiment experiment) {
        this.style = experiment.style;
        this.clientHtml = experiment.clientHtml;
        this.clientGraph = experiment.clientGraph;

        for (Step step : experiment.steps) {
            this.steps.add(new Step(step));
        }
        for (Content c : experiment.content) {
            this.content.add(new Content(c));
        }
        for (Parameter param : experiment.parameters) {
            this.parameters.add(new Parameter(param));
        }
        for (Image image : experiment.images) {
            this.images.add(new Image(image));
        }
    }

    public void export() throws IOException {
        File experimentDirectory = new File(Play.application().path().toString() + "/experiments/" + this.name);
        FileUtils.writeStringToFile(new File(experimentDirectory, "style.css"), this.style);
        FileUtils.writeStringToFile(new File(experimentDirectory, "client.html"), this.clientHtml);
        FileUtils.writeStringToFile(new File(experimentDirectory, "client-graph.js"), this.clientGraph);

        File stepsDirectory = new File(experimentDirectory, "/Steps");
        for (Step step : this.steps) {
        	FileUtils.writeStringToFile(new File(stepsDirectory, step.name.concat(".groovy")), step.source);
        }

        File contentDirectory = new File(experimentDirectory, "/Content");
        for (Content c : this.content) {
        	FileUtils.writeStringToFile(new File(contentDirectory, c.name.concat(".html")), c.html);
        }

		String ls = System.getProperty("line.separator");
        File parametersFile = new File(experimentDirectory, "parameters.csv");
        FileUtils.writeStringToFile(parametersFile, "Name,Type,Min.,Max.,Default,Short Description" + ls);
        for (Parameter param : this.parameters) {
        	FileUtils.writeStringToFile(parametersFile, param.name + "," + param.type + "," + param.minVal + "," + param.maxVal + "," + param.defaultVal + "," + param.description + ls, true);
        }

        File imagesDirectory = new File(experimentDirectory, "/Images");
        for (Image image : this.images) {
        	FileUtils.writeByteArrayToFile(new File(imagesDirectory, image.fileName), image.file);
        }
    }

    public static String defaultClientHTML() {
        return "    <div id=\"mainDiv\" ng-controller=\"ClientCtrl\">\n" +
            "        <div id=\"statusDiv\" ng-controller=\"TimersCtrl\" ng-show=\"isTimer()\">\n" +
            "            <progressbar ng-repeat=\"timer in timers\" type=\"{{timer.appearance}}\" value=\"timer.timerValue\">{{timer.timerText}}</progressbar>\n" +
            "        </div>\n" +
            "        <div id=\"gameDiv\">\n" +
            "            <div id=\"graph\">\n" +
            "            </div>\n" +
            "\n" +
            "            <div id=\"rightDiv\">\n" +
            "                <div id=\"text\" ng-bind-html=\"client.player.text | to_trusted\"></div>\n" +
            "\n" +
            "                <div id=\"choices\" ng-controller=\"ChoicesCtrl\">\n" +
            "                    <div ng-bind-html=\"custom | to_trusted\" ng-hide=\"client.player.choices === undefined || custom === undefined\"></div>\n" +
            "                    <button ng-repeat=\"choice in childChoices |filter: {class: '!drop'}\" class=\"{{choice.class}}\" ng-click=\"makeChoice(choice.uid)\">\n" +
            "                        {{choice.name}}\n" +
            "                    </button>\n" +
            "                </div>\n" +
            "            </div>\n" +
            "        </div>\n" +
            "    </div>\n";
    }

    public static String defaultClientGraph() {
        return "function Graph(w, h, clientId) {\n" +
            "\n" +
            "  var width = (w == undefined) ? 600 : w;\n" +
            "  var height = (h == undefined) ? 600 : h;\n" +
            "  var egoNodeR = 50;\n" +
            "  var alterNodeR = 30;\n" +
            "  var arrowPadding = 7;\n" +
            "  var graphPadding = 10;\n" +
            "  var linkDistance = (Math.min(width, height) / 2) - alterNodeR - (2 * graphPadding);\n" +
            "\n" +
            "  var ignoreProps = [\"$$hashKey\", \"text\", \"choices\", \"x\", \"y\", \"px\", \"py\"];\n" +
            "\n" +
            "  // set up initial svg object\n" +
            "  var div = d3.select(\"#graph\");\n" +
            "  var vis = div.append(\"svg:svg\")\n" +
            "    .attr(\"width\", width)\n" +
            "    .attr(\"height\", height);\n" +
            "\n" +
            "  // set up arrow markers for graph links\n" +
            "  // Thanks to rkirsling for the example here: http://bl.ocks.org/rkirsling/5001347\n" +
            "  vis.append('svg:defs').append('svg:marker')\n" +
            "    .attr('id', 'end')\n" +
            "    .attr('viewBox', '0 -5 10 10') //'0 -5 10 10'\n" +
            "    .attr('refX', 6)\n" +
            "    .attr('markerWidth', 4)\n" +
            "    .attr('markerHeight', 4)\n" +
            "    .attr('orient', 'auto')\n" +
            "    .append('svg:path')\n" +
            "    .attr('d', 'M0,-5L10,0L0,5')\n" +
            "    .attr('fill', '#333');\n" +
            "\n" +
            "  vis.append('svg:defs').append('svg:marker')\n" +
            "    .attr('id', 'start')\n" +
            "    .attr('viewBox', '0 -5 10 10') //'0 -5 10 10'\n" +
            "    .attr('refX', 4)\n" +
            "    .attr('markerWidth', 4)\n" +
            "    .attr('markerHeight', 4)\n" +
            "    .attr('orient', 'auto')\n" +
            "    .append('svg:path')\n" +
            "    .attr('d', 'M10,-5L0,0L10,5')\n" +
            "    .attr('fill', '#333');\n" +
            "\n" +
            "  vis.append('svg:defs').append('svg:marker')\n" +
            "    .attr('id', 'end-green')\n" +
            "    .attr('viewBox', '0 -5 10 10')//'0 -5 10 10'\n" +
            "    .attr('refX', 6)\n" +
            "    .attr('markerWidth', 4)\n" +
            "    .attr('markerHeight', 4)\n" +
            "    .attr('orient', 'auto')\n" +
            "    .append('svg:path')\n" +
            "    .attr('d', 'M0,-5L10,0L0,5')\n" +
            "    .attr('fill', 'green');\n" +
            "\n" +
            "  vis.append('svg:defs').append('svg:marker')\n" +
            "    .attr('id', 'end-red')\n" +
            "    .attr('viewBox', '0 -5 10 10')//'0 -5 10 10'\n" +
            "    .attr('refX', 6)\n" +
            "    .attr('markerWidth', 4)\n" +
            "    .attr('markerHeight', 4)\n" +
            "    .attr('orient', 'auto')\n" +
            "    .append('svg:path')\n" +
            "    .attr('d', 'M0,-5L10,0L0,5')\n" +
            "    .attr('fill', 'red');\n" +
            "\n" +
            "  vis.append('svg:defs').append('svg:marker')\n" +
            "    .attr('id', 'start-green')\n" +
            "    .attr('viewBox', '0 -5 10 10')//'0 -5 10 10'\n" +
            "    .attr('refX', 4)\n" +
            "    .attr('markerWidth', 4)\n" +
            "    .attr('markerHeight', 4)\n" +
            "    .attr('orient', 'auto')\n" +
            "    .append('svg:path')\n" +
            "    .attr('d', 'M10,-5L0,0L10,5')\n" +
            "    .attr('fill', 'green');\n" +
            "\n" +
            "  vis.append('svg:defs').append('svg:marker')\n" +
            "    .attr('id', 'start-red')\n" +
            "    .attr('viewBox', '0 -5 10 10')//'0 -5 10 10'\n" +
            "    .attr('refX', 4)\n" +
            "    .attr('markerWidth', 4)\n" +
            "    .attr('markerHeight', 4)\n" +
            "    .attr('orient', 'auto')\n" +
            "    .append('svg:path')\n" +
            "    .attr('d', 'M10,-5L0,0L10,5')\n" +
            "    .attr('fill', 'red');\n" +
            "\n" +
            "  var force = d3.layout.force()\n" +
            "    .gravity(.05)\n" +
            "    .friction(0.8)\n" +
            "    .charge(-10000) //-500\n" +
            "    .linkStrength(10) //2\n" +
            "    .linkDistance(linkDistance * 0.9)\n" +
            "    .size([width, height]);\n" +
            "\n" +
            "  var nodes = force.nodes(),\n" +
            "    links = force.links();\n" +
            "\n" +
            "  force.on(\"tick\", function () {\n" +
            "    vis.selectAll(\"line.link\")\n" +
            "      .attr(\"x1\", function (d) {\n" +
            "        var deltaX = d.target.x - d.source.x,\n" +
            "          deltaY = d.target.y - d.source.y,\n" +
            "          dist = Math.sqrt(deltaX * deltaX + deltaY * deltaY),\n" +
            "          normX = deltaX / dist,\n" +
            "          sourcePadding = (d.source.id == clientId) ? egoNodeR : alterNodeR;\n" +
            "        if (d.arrow && d.arrow.length > 0) {\n" +
            "          sourcePadding += arrowPadding;\n" +
            "        }\n" +
            "        return d.source.x + (sourcePadding * normX);\n" +
            "      })\n" +
            "      .attr(\"y1\", function (d) {\n" +
            "        var deltaX = d.target.x - d.source.x,\n" +
            "          deltaY = d.target.y - d.source.y,\n" +
            "          dist = Math.sqrt(deltaX * deltaX + deltaY * deltaY),\n" +
            "          normY = deltaY / dist,\n" +
            "          sourcePadding = (d.source.id == clientId) ? egoNodeR : alterNodeR;\n" +
            "        if (d.arrow && d.arrow.length > 0) {\n" +
            "          sourcePadding += arrowPadding;\n" +
            "        }\n" +
            "        return d.source.y + (sourcePadding * normY);\n" +
            "      })\n" +
            "      .attr(\"x2\", function (d) {\n" +
            "        var deltaX = d.target.x - d.source.x,\n" +
            "          deltaY = d.target.y - d.source.y,\n" +
            "          dist = Math.sqrt(deltaX * deltaX + deltaY * deltaY),\n" +
            "          normX = deltaX / dist,\n" +
            "          targetPadding = (d.target.id == clientId) ? egoNodeR : alterNodeR;\n" +
            "        if (d.arrow && d.arrow.length > 0) {\n" +
            "          targetPadding += arrowPadding;\n" +
            "        }\n" +
            "        return targetX = d.target.x - (targetPadding * normX);\n" +
            "      })\n" +
            "      .attr(\"y2\", function (d) {\n" +
            "        var deltaX = d.target.x - d.source.x,\n" +
            "          deltaY = d.target.y - d.source.y,\n" +
            "          dist = Math.sqrt(deltaX * deltaX + deltaY * deltaY),\n" +
            "          normY = deltaY / dist,\n" +
            "          targetPadding = (d.target.id == clientId) ? egoNodeR : alterNodeR;\n" +
            "        if (d.arrow && d.arrow.length > 0) {\n" +
            "          targetPadding += arrowPadding;\n" +
            "        }\n" +
            "        return targetY = d.target.y - (targetPadding * normY);\n" +
            "      });\n" +
            "\n" +
            "    vis.selectAll(\"g.node\")\n" +
            "      .attr(\"transform\", function (d) {\n" +
            "        return \"translate(\" + d.x + \",\" + d.y + \")\"\n" +
            "      });\n" +
            "  });\n" +
            "\n" +
            "  var removeNode = function (nid) {\n" +
            "    var nodeIndex = findNode(nid);\n" +
            "    if (nodeIndex > -1) {\n" +
            "      nodes.splice(i, 1);\n" +
            "    }\n" +
            "  }\n" +
            "\n" +
            "  var findNode = function (nid) {\n" +
            "    for (var i = 0; i < nodes.length; i++) {\n" +
            "      if (nodes[i].id == nid) {\n" +
            "        return nodes[i];\n" +
            "      }\n" +
            "    }\n" +
            "    return null;\n" +
            "  }\n" +
            "\n" +
            "  var addLink = function (link, sourceId, targetId) {\n" +
            "    link.source = findNode(sourceId);\n" +
            "    link.target = findNode(targetId);\n" +
            "    links.push(link);\n" +
            "  }\n" +
            "\n" +
            "  var updateLink = function (oldLink, newLink, sourceId, targetId) {\n" +
            "    _.extend(oldLink, newLink);\n" +
            "    oldLink.source = findNode(sourceId);\n" +
            "    oldLink.target = findNode(targetId);\n" +
            "  }\n" +
            "\n" +
            "  this.updateGraph = function (newGraph) {\n" +
            "\n" +
            "    if (newGraph == undefined)\n" +
            "      return;\n" +
            "\n" +
            "    if (newGraph.nodes == undefined || newGraph.nodes.length == 0) {\n" +
            "      // Remove all nodes\n" +
            "      nodes.length = 0;\n" +
            "    } else {\n" +
            "      // If there is anything in the old array that isn't in the new, it needs to be removed\n" +
            "      for (var i = nodes.length - 1; i >= 0; i--) {\n" +
            "        if (_.find(newGraph.nodes, function (n) {\n" +
            "            return n.id === nodes[i].id;\n" +
            "          }) === undefined) {\n" +
            "          nodes.splice(i, 1);\n" +
            "        }\n" +
            "      }\n" +
            "\n" +
            "      // Finally, anything in the new array that isn't in the old needs to be added\n" +
            "      for (var i = 0; i < newGraph.nodes.length; i++) {\n" +
            "        var oldNode = _.find(nodes, function (n) {\n" +
            "          return n.id === newGraph.nodes[i].id;\n" +
            "        });\n" +
            "        if (oldNode === null || oldNode === undefined) {\n" +
            "          nodes.push(newGraph.nodes[i]);\n" +
            "        } else {\n" +
            "          // Update the old node\n" +
            "          _.extend(oldNode, newGraph.nodes[i]);\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "\n" +
            "    if (newGraph.links == undefined || newGraph.links.length == 0) {\n" +
            "      // Remove all links\n" +
            "      links.length = 0;\n" +
            "    } else {\n" +
            "      // If there is anything in the old array that isn't in the new, it needs to be removed\n" +
            "      for (var i = links.length - 1; i >= 0; i--) {\n" +
            "        // source or target could have been removed at this point\n" +
            "        var sourceId = (links[i].source == undefined) ? null : links[i].source.id;\n" +
            "        var targetId = (links[i].target == undefined) ? null : links[i].target.id;\n" +
            "\n" +
            "        try {\n" +
            "          if (_.find(newGraph.links, function (l) {\n" +
            "              return ((newGraph.nodes[l.source].id === sourceId) && (newGraph.nodes[l.target].id === targetId));\n" +
            "            }) === undefined) {\n" +
            "            links.splice(i, 1);\n" +
            "          }\n" +
            "        } catch (e) {\n" +
            "          //TODO: Why is there an exception being thrown here?\n" +
            "        }\n" +
            "      }\n" +
            "\n" +
            "      // Finally, anything in the new array that isn't in the old needs to be added\n" +
            "      for (var i = 0; i < newGraph.links.length; i++) {\n" +
            "        var sourceIdx, targetIdx, source, target, sourceId, targetId = undefined;\n" +
            "        var link = newGraph.links[i];\n" +
            "        if (link != undefined) {\n" +
            "          sourceIdx = link.source;\n" +
            "          targetIdx = link.target;\n" +
            "        }\n" +
            "\n" +
            "        if (sourceIdx != undefined && targetIdx != undefined) {\n" +
            "          source = newGraph.nodes[sourceIdx];\n" +
            "          target = newGraph.nodes[targetIdx];\n" +
            "        }\n" +
            "\n" +
            "        if (source != undefined && target != undefined) {\n" +
            "          sourceId = source.id;\n" +
            "          targetId = target.id;\n" +
            "        }\n" +
            "\n" +
            "        if (sourceId != undefined && targetId != undefined) {\n" +
            "          var oldLink = _.find(links, function (l) {\n" +
            "            return ((l.target.id === targetId) && (l.source.id === sourceId));\n" +
            "          });\n" +
            "          if (oldLink === null || oldLink === undefined) {\n" +
            "            addLink(link, sourceId, targetId);\n" +
            "          } else {\n" +
            "            // Update the old link\n" +
            "            updateLink(oldLink, link, sourceId, targetId);\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "\n" +
            "    update();\n" +
            "  };\n" +
            "\n" +
            "  var animateScore = function (amount, start, end, endNodeId) {\n" +
            "    //console.log(\"animateScore(\" + amount + \", \" + start + \", \" + end + \", \" + endNodeId + \")\");\n" +
            "\n" +
            "    var animText = vis.append(\"svg:text\")\n" +
            "      .attr(\"class\", \"anim\")\n" +
            "      .style(\"text-anchor\", \"middle\")\n" +
            "      .style(\"fill\", \"#1EFF1E\")\n" +
            "      .style(\"stroke\", \"#000\")\n" +
            "      .style(\"font-family\", \"Lucida Sans\")\n" +
            "      .style(\"font-weight\", \"bold\")\n" +
            "      .style(\"font-size\", 18)\n" +
            "      .text(((amount < 0) ? \"\" : \"+\") + amount)\n" +
            "      .attr(\"x\", start.x)\n" +
            "      .attr(\"y\", start.y)\n" +
            "      .transition()\n" +
            "      .duration(1500)\n" +
            "      .attr(\"x\", end.x)\n" +
            "      .attr(\"y\", end.y)\n" +
            "      .remove();\n" +
            "\n" +
            "  }\n" +
            "\n" +
            "  var update = function () {\n" +
            "    var link = vis.selectAll(\"line.link\")\n" +
            "      .data(links, function (d) {\n" +
            "        return d.id;\n" +
            "      });\n" +
            "\n" +
            "    link.enter().insert(\"svg:line\", \"g.node\")\n" +
            "      .attr(\"class\", \"link client\");\n" +
            "\n" +
            "    link.exit().remove();\n" +
            "\n" +
            "    var g = vis.selectAll(\"g.node\")\n" +
            "      .data(nodes, function (d) {\n" +
            "        return d.id;\n" +
            "      });\n" +
            "\n" +
            "    var gEnter = g.enter().append(\"svg:g\")\n" +
            "      .attr(\"class\", \"node client\");\n" +
            "    //.attr(\"transform\", function(d) { return \"translate(\" + d.x + \",\" + d.y + \")\"; });\n" +
            "\n" +
            "    gEnter.append(\"svg:text\")\n" +
            "      .attr(\"class\", \"node client\")\n" +
            "      .style(\"text-anchor\", \"middle\")\n" +
            "      .style(\"font-size\", function (d) {\n" +
            "        return (d.id == clientId) ? \"18pt\" : \"14pt\"\n" +
            "      })\n" +
            "      .text(function (d) {\n" +
            "        return (d.score == undefined) ? \"\" : d.score;\n" +
            "      });\n" +
            "\n" +
            "    gEnter.insert(\"svg:circle\", \"text.node\")\n" +
            "      .attr(\"class\", \"node client\")\n" +
            "      .attr(\"r\", function (d) {\n" +
            "        return (d.id == clientId) ? egoNodeR : alterNodeR;\n" +
            "      })\n" +
            "      .each(function (d) {\n" +
            "        if (d.id == clientId) {\n" +
            "          d.fixed = true;\n" +
            "          d.x = width / 2;\n" +
            "          d.y = height / 2;\n" +
            "        }\n" +
            "      });\n" +
            "\n" +
            "    g.exit().remove();\n" +
            "\n" +
            "\n" +
            "    var scoreText = vis.selectAll(\"text.node\");\n" +
            "\n" +
            "    scoreText.text(function (d) {\n" +
            "      return (d.score == undefined) ? \"\" : d.score;\n" +
            "    });\n" +
            "\n" +
            "    force\n" +
            "      .nodes(nodes)\n" +
            "      .links(links)\n" +
            "      .start();\n" +
            "\n" +
            "    var node = g.selectAll(\"circle.node\");\n" +
            "\n" +
            "    d3.selectAll(\"circle.node\").each(function (d, i) {\n" +
            "      for (var propertyName in d) {\n" +
            "        if (_.indexOf(ignoreProps, propertyName) == -1 && isNaN(propertyName)) {\n" +
            "          d3.select(this).attr(propertyName, d[propertyName]);\n" +
            "        }\n" +
            "      }\n" +
            "    });\n" +
            "\n" +
            "    d3.selectAll(\"line.link\").each(function (d, i) {\n" +
            "      d3.select(this).attr(\"marker-start\", null);\n" +
            "      d3.select(this).attr(\"marker-end\", null);\n" +
            "\n" +
            "      for (var propertyName in d) {\n" +
            "        if (propertyName == \"arrow\") {\n" +
            "          var arrowParams = d.arrow.split(\",\");\n" +
            "          if (arrowParams.length < 1) {\n" +
            "            return;\n" +
            "          }\n" +
            "\n" +
            "          if (d.source.id == arrowParams[0] || arrowParams[0] == \"both\") {\n" +
            "            if (arrowParams.length > 1 && arrowParams[1] != \"grey\") {\n" +
            "              d3.select(this).attr(\"marker-start\", \"url(#start-\" + arrowParams[1] + \")\")\n" +
            "            } else {\n" +
            "              d3.select(this).attr(\"marker-start\", \"url(#start)\")\n" +
            "            }\n" +
            "          } else {\n" +
            "            d3.select(this).attr(\"marker-start\", null);\n" +
            "          }\n" +
            "\n" +
            "          if (d.target.id == arrowParams[0] || arrowParams[0] == \"both\") {\n" +
            "            if (arrowParams.length > 1 && arrowParams[1] != \"grey\") {\n" +
            "              d3.select(this).attr(\"marker-end\", \"url(#end-\" + arrowParams[1] + \")\")\n" +
            "            } else {\n" +
            "              d3.select(this).attr(\"marker-end\", \"url(#end)\")\n" +
            "            }\n" +
            "          } else {\n" +
            "            d3.select(this).attr(\"marker-end\", null);\n" +
            "          }\n" +
            "\n" +
            "        } else if (_.indexOf(ignoreProps, propertyName) == -1) {\n" +
            "          d3.select(this).attr(propertyName, d[propertyName]);\n" +
            "        }\n" +
            "      }\n" +
            "    });\n" +
            "\n" +
            "\n" +
            "    var setupAnim = function () {\n" +
            "      link.each(function (d) {\n" +
            "        var animate = d3.select(this).attr(\"animate\");\n" +
            "        if (animate != undefined) {\n" +
            "          var params = animate.split(\",\");\n" +
            "          if (params.length > 3) {\n" +
            "            var round = params[0];\n" +
            "            var amount = params[1];\n" +
            "            var startNodeId = params[2];\n" +
            "            var endNodeId = params[3];\n" +
            "\n" +
            "            var startNode = findNode(startNodeId);\n" +
            "            var endNode = findNode(endNodeId);\n" +
            "\n" +
            "            var start = {\"x\": startNode.x, \"y\": startNode.y};\n" +
            "            var end = {\"x\": endNode.x, \"y\": endNode.y};\n" +
            "\n" +
            "            if ((d.animated != animate) && start != undefined && end != undefined && endNodeId != undefined) {\n" +
            "              d.animated = animate;\n" +
            "              animateScore(amount, start, end, endNodeId);\n" +
            "            }\n" +
            "          }\n" +
            "        }\n" +
            "      });\n" +
            "    };\n" +
            "\n" +
            "    var t = setTimeout(setupAnim, 1000);\n" +
            "\n" +
            "  };\n" +
            "}\n";
    }

    public static Step generateOnJoinStep() {
        Step onJoin = new Step();
        onJoin.name = "OnJoinStep";
        onJoin.source = "onJoinStep = stepFactory.createNoUserActionStep()\n" +
                "\n" +
                "onJoinStep.run = { playerId->\n" +
                "   println \"onJoinStep.run\"\n" +
                "   def player = g.getVertex(playerId)\n" +
                "}" +
                "\n" +
                "onJoinStep.done = {\n" +
                "   println \"onJoinStep.done\"\n" +
                "}";

        return onJoin;
    }

    public static Step generateOnLeaveStep() {
        Step onLeave = new Step();
        onLeave.name = "OnLeaveStep";
        onLeave.source = "onLeaveStep = stepFactory.createNoUserActionStep()\n" +
                "\n" +
                "onLeaveStep.run = {\n" +
                "   println \"onLeaveStep.run\"\n" +
                "}" +
                "\n" +
                "onLeaveStep.done = {\n" +
                "   println \"onLeaveStep.done\"\n" +
                "}";
        return onLeave;
    }

    public static Step generateInitStep() {
        Step init = new Step();
        init.name = "InitStep";
        init.source = "initStep = stepFactory.createStep()\n" +
                "\n" +
                "initStep.run = {\n" +
                "   println \"initStep.run\"\n" +
                "}" +
                "\n" +
                "initStep.done = {\n" +
                "   println \"initStep.done\"\n" +
                "}";
        return init;
    }

    @Override
    public void delete() {
        for (Step s : steps) {
            s.delete();
        }
        for (Content c : content) {
            c.delete();
        }
        for (Parameter p : parameters) {
            p.delete();
        }
        for (ExperimentInstance ei : instances) {
            ei.delete();
        }
        for (Image i : images) {
            i.delete();
        }
        super.delete();
    }

    public void setStyle(String style)
    {
        this.style = style;
    }

    public void setClientHtml(String clientHtml)
    {
        this.clientHtml = clientHtml;
    }

    public void setClientGraph(String clientGraph)
    {
        this.clientGraph = clientGraph;
    }

    public Content getContent(Long id)
    {
        for (Content c : content)
        {
            if (c.id.equals(id))
                return c;
        }
        return null;
    }

    public Content getContentByName(String name)
    {
        for (Content c : content)
        {
            if (c.name.equals(name))
                return c;
        }
        return null;
    }

    public Step getStep(Long id)
    {
        for (Step s : steps)
        {
            if (s.id.equals(id))
                return s;
        }
        return null;
    }

    public Parameter getParameterByName(String name)
    {
        for (Parameter p : parameters) 
        {
            if (p.name.equals(name))
                return p;
        }
        return null;
    }

    public List<Parameter> getParameters()
    {
        return parameters;
    }

    public boolean hasOnJoinStep() {
        return getOnJoinStep() != null;
    }

    public boolean hasOnLeaveStep() {
        return getOnLeaveStep() != null;
    }

    public Step getOnJoinStep() {
        for (Step step : steps) {
            if (ON_JOIN_STEP_NAME.equalsIgnoreCase(step.name)) {
                return step;
            }
        }
        return null;
    }

    public Step getOnLeaveStep() {
        for (Step step : steps) {
            if (ON_LEAVE_STEP_NAME.equalsIgnoreCase(step.name)) {
                return step;
            }
        }
        return null;
    }


    @JsonValue
    public ObjectNode toJson()
    {
        ObjectNode experiment = Json.newObject();

        experiment.put("id", id);
        experiment.put("name", name);

        ArrayNode jsonSteps = experiment.putArray("steps"); 
        for (Step s : steps)
        {
            jsonSteps.add(s.toJson());
        }

        ArrayNode jsonContent = experiment.putArray("content"); 
        for (Content c : content)
        {
            jsonContent.add(c.toJson());
        }

        ArrayNode jsonParameters = experiment.putArray("parameters"); 
        for (Parameter p : parameters)
        {
            jsonParameters.add(p.toJson());
        }

        ArrayNode jsonInstances = experiment.putArray("instances"); 
        for (ExperimentInstance ei : instances)
        {
            // Only return the name and ID of the instances
            // TODO: Perhaps add the Date/Time of the instance here as well
            jsonInstances.add(ei.toJsonStub());
        }

        ArrayNode jsonImages = experiment.putArray("images"); 
        for (Image i : images)
        {
            jsonImages.add(i.toJson());
        }

        experiment.put("style", style);
        experiment.put("clientHtml", clientHtml);
        experiment.put("clientGraph", clientGraph);

        return experiment;
    }

    public String toString()
    {
        return "Experiment(" + id + ")";
    }
}
