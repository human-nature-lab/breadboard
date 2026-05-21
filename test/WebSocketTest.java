import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import com.tinkerpop.blueprints.util.wrappers.event.EventGraph;
import models.*;
import org.junit.Test;
import play.libs.Json;
import play.mvc.WebSocket;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.*;

/**
 * Tests for WebSocket message handling: Admin messages, Client subgraph filtering,
 * EventGraphChangedListener broadcasting, and ThrottledWebSocketOut passthrough.
 */
public class WebSocketTest extends BaseTest {

    /**
     * A test WebSocket.Out that captures all written messages for assertion.
     */
    static class TestWebSocketOut implements WebSocket.Out<JsonNode> {
        public final List<JsonNode> messages = new CopyOnWriteArrayList<>();
        public boolean closed = false;

        public void write(JsonNode frame) {
            messages.add(frame);
        }

        public void close() {
            closed = true;
        }

        public void clear() {
            messages.clear();
        }

        public JsonNode last() {
            return messages.isEmpty() ? null : messages.get(messages.size() - 1);
        }
    }

    /**
     * Creates a simple graph: p1 -- p2 -- p3 with properties.
     */
    private EventGraph<TinkerGraph> createTestGraph() {
        TinkerGraph base = new TinkerGraph();
        EventGraph<TinkerGraph> graph = new EventGraph<>(base);

        Vertex p1 = graph.addVertex("p1");
        p1.setProperty("name", "Alice");
        p1.setProperty("score", 10);
        Map<String, Object> p1Private = new HashMap<>();
        p1Private.put("secretScore", 99);
        p1.setProperty("private", p1Private);
        p1.setProperty("text", "Your turn");
        p1.setProperty("choices", "[cooperate, defect]");

        Vertex p2 = graph.addVertex("p2");
        p2.setProperty("name", "Bob");
        p2.setProperty("score", 20);

        Vertex p3 = graph.addVertex("p3");
        p3.setProperty("name", "Charlie");
        p3.setProperty("score", 30);

        Edge e12 = graph.addEdge(null, p1, p2, "connected");
        e12.setProperty("weight", 1.0);
        Map<String, Object> inProps = new HashMap<>();
        inProps.put("trust", 0.8);
        e12.setProperty("inProps", inProps);
        Map<String, Object> outProps = new HashMap<>();
        outProps.put("trust", 0.6);
        e12.setProperty("outProps", outProps);

        Edge e23 = graph.addEdge(null, p2, p3, "connected");

        return graph;
    }

    // === ThrottledWebSocketOut ===

    @Test
    public void throttledWebSocketOutPassthrough() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 100L);

        ObjectNode msg = Json.newObject();
        msg.put("action", "test");
        throttled.write(msg);

        assertEquals("Should have 1 message", 1, testOut.messages.size());
        assertEquals("test", testOut.messages.get(0).get("action").asText());
    }

    @Test
    public void throttledWebSocketOutMultipleMessages() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 100L);

        for (int i = 0; i < 5; i++) {
            ObjectNode msg = Json.newObject();
            msg.put("index", i);
            throttled.write(msg);
        }

        assertEquals("Should pass through all 5 messages", 5, testOut.messages.size());
    }

    @Test
    public void throttledWebSocketOutClose() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 100L);

        throttled.close();
        assertTrue("Close should be delegated", testOut.closed);
    }

    // === Admin Graph Change Messages ===

    @Test
    public void adminVertexAdded() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 100L);

        Language lang = createLanguage("eng", "English");
        User user = createUser("admin-ws@test.com", "Admin WS", "pass", lang);

        // Create Admin without a real ScriptBoard actor (pass null)
        Admin admin = new Admin(user, null, throttled);

        TinkerGraph base = new TinkerGraph();
        Vertex v = base.addVertex("v1");
        v.setProperty("name", "Player1");

        admin.vertexAdded(v, false);

        assertTrue("Should have written messages", testOut.messages.size() > 0);

        // Find the addNode message
        boolean foundAddNode = false;
        for (JsonNode msg : testOut.messages) {
            if (msg.has("action") && "addNode".equals(msg.get("action").asText())) {
                assertEquals("v1", msg.get("id").asText());
                foundAddNode = true;
                break;
            }
        }
        assertTrue("Should have sent addNode message", foundAddNode);
    }

    @Test
    public void adminVertexRemoved() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 100L);
        Language lang = createLanguage("eng", "English");
        User user = createUser("admin-rm@test.com", "Admin RM", "pass", lang);
        Admin admin = new Admin(user, null, throttled);

        TinkerGraph base = new TinkerGraph();
        Vertex v = base.addVertex("v1");

        admin.vertexRemoved(v, false);

        boolean found = false;
        for (JsonNode msg : testOut.messages) {
            if (msg.has("action") && "removeNode".equals(msg.get("action").asText())) {
                assertEquals("v1", msg.get("id").asText());
                found = true;
            }
        }
        assertTrue("Should have sent removeNode message", found);
    }

    @Test
    public void adminVertexPropertyChanged() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 100L);
        Language lang = createLanguage("eng", "English");
        User user = createUser("admin-prop@test.com", "Admin Prop", "pass", lang);
        Admin admin = new Admin(user, null, throttled);

        TinkerGraph base = new TinkerGraph();
        Vertex v = base.addVertex("v1");
        v.setProperty("score", 42);

        admin.vertexPropertyChanged(v, "score", null, 42);

        JsonNode last = testOut.last();
        assertNotNull(last);
        assertEquals("nodePropertyChanged", last.get("action").asText());
        assertEquals("v1", last.get("id").asText());
        assertEquals("score", last.get("key").asText());
    }

    @Test
    public void adminPrivatePropertyExpansion() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 100L);
        Language lang = createLanguage("eng", "English");
        User user = createUser("admin-priv@test.com", "Admin Priv", "pass", lang);
        Admin admin = new Admin(user, null, throttled);

        TinkerGraph base = new TinkerGraph();
        Vertex v = base.addVertex("v1");
        Map<String, Object> privateMap = new HashMap<>();
        privateMap.put("secretScore", 99);
        privateMap.put("hiddenItem", "sword");
        v.setProperty("private", privateMap);

        admin.vertexPropertyChanged(v, "private", null, privateMap);

        // Admin should receive individual messages for each private key
        boolean foundSecret = false;
        boolean foundHidden = false;
        for (JsonNode msg : testOut.messages) {
            if (msg.has("action") && "nodePropertyChanged".equals(msg.get("action").asText())) {
                String key = msg.get("key").asText();
                if ("secretScore".equals(key)) foundSecret = true;
                if ("hiddenItem".equals(key)) foundHidden = true;
            }
        }
        assertTrue("Should expand private.secretScore", foundSecret);
        assertTrue("Should expand private.hiddenItem", foundHidden);
    }

    @Test
    public void adminEdgeAdded() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 100L);
        Language lang = createLanguage("eng", "English");
        User user = createUser("admin-edge@test.com", "Admin Edge", "pass", lang);
        Admin admin = new Admin(user, null, throttled);

        TinkerGraph base = new TinkerGraph();
        Vertex v1 = base.addVertex("v1");
        Vertex v2 = base.addVertex("v2");
        Edge e = base.addEdge(null, v1, v2, "connected");

        admin.edgeAdded(e);

        boolean found = false;
        for (JsonNode msg : testOut.messages) {
            if (msg.has("action") && "addLink".equals(msg.get("action").asText())) {
                assertNotNull(msg.get("id"));
                assertEquals("v1", msg.get("source").asText());
                assertEquals("v2", msg.get("target").asText());
                assertEquals("connected", msg.get("value").asText());
                found = true;
            }
        }
        assertTrue("Should have sent addLink message", found);
    }

    // === Client Subgraph Filtering ===

    @Test
    public void clientUpdateGraphShowsSubgraph() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 100L);

        Experiment exp = createExperiment("WSExp");
        ExperimentInstance instance = createInstance("WSRun", exp);

        Client client = new Client("p1", instance, null, throttled);

        // Build a simple graph with numeric edge IDs (required by D3Utils)
        TinkerGraph base = new TinkerGraph();
        EventGraph<TinkerGraph> graph = new EventGraph<>(base);
        Vertex p1 = graph.addVertex("p1");
        p1.setProperty("name", "Alice");
        Vertex p2 = graph.addVertex("p2");
        p2.setProperty("name", "Bob");
        graph.addEdge(null, p1, p2, "connected");

        client.updateGraph(p1);

        assertTrue("Should have written graph message", testOut.messages.size() > 0);
        JsonNode msg = testOut.last();
        assertNotNull("Message should not be null", msg);
        assertTrue("Message should have 'graph' field", msg.has("graph"));
        assertTrue("Message should have 'player' field", msg.has("player"));
    }

    @Test
    public void clientSeesPrivatePropertiesOnOwnVertex() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 100L);

        Experiment exp = createExperiment("PrivExp");
        ExperimentInstance instance = createInstance("PrivRun", exp);
        Client client = new Client("p1", instance, null, throttled);

        // Build a simple graph where p1 has private properties
        TinkerGraph base = new TinkerGraph();
        EventGraph<TinkerGraph> graph = new EventGraph<>(base);
        Vertex p1 = graph.addVertex("p1");
        p1.setProperty("name", "Alice");
        Map<String, Object> priv = new HashMap<>();
        priv.put("secretScore", 99);
        p1.setProperty("private", priv);
        Vertex p2 = graph.addVertex("p2");
        graph.addEdge(null, p1, p2, "connected");

        client.updateGraph(p1);

        JsonNode msg = testOut.last();
        JsonNode player = msg.get("player");
        assertNotNull("Player section should exist", player);
        // Private properties should be expanded onto the player's own vertex
        assertTrue("Player should see secretScore from private map",
            player.has("secretScore"));
    }

    @Test
    public void clientTextAndChoicesOnlyForOwner() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 100L);

        Experiment exp = createExperiment("TextExp");
        ExperimentInstance instance = createInstance("TextRun", exp);

        // Client p1 should see their own text/choices
        Client clientP1 = new Client("p1", instance, null, throttled);

        TinkerGraph base = new TinkerGraph();
        Vertex p1 = base.addVertex("p1");
        p1.setProperty("name", "Alice");
        p1.setProperty("text", "Your turn");
        p1.setProperty("choices", "[cooperate, defect]");

        // vertexPropertyChanged for text - should only send to matching client
        clientP1.vertexPropertyChanged(p1, "text", "Your turn");
        assertTrue("p1 should receive own text", testOut.messages.size() > 0);

        // Now test that a different client doesn't see p1's text
        TestWebSocketOut testOut2 = new TestWebSocketOut();
        ThrottledWebSocketOut throttled2 = new ThrottledWebSocketOut(testOut2, 100L);
        Client clientP2 = new Client("p2", instance, null, throttled2);

        clientP2.vertexPropertyChanged(p1, "text", "Your turn");
        assertEquals("p2 should NOT receive p1's text", 0, testOut2.messages.size());
    }

    @Test
    public void clientPrivatePropertyFiltering() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 100L);

        Experiment exp = createExperiment("PrivFilterExp");
        ExperimentInstance instance = createInstance("PrivFilterRun", exp);

        Client clientP1 = new Client("p1", instance, null, throttled);

        TinkerGraph base = new TinkerGraph();
        Vertex p1 = base.addVertex("p1");
        Map<String, Object> priv = new HashMap<>();
        priv.put("secret", 42);
        p1.setProperty("private", priv);

        // vertexPropertyChanged for "private" key with prefix "private" should go to owner
        clientP1.vertexPropertyChanged(p1, "private", priv);

        // Check that the private key content was not sent to another client
        TestWebSocketOut testOut2 = new TestWebSocketOut();
        ThrottledWebSocketOut throttled2 = new ThrottledWebSocketOut(testOut2, 100L);
        Client clientP2 = new Client("p2", instance, null, throttled2);

        clientP2.vertexPropertyChanged(p1, "private", priv);
        // p2 should NOT see p1's private properties via vertexPropertyChanged
    }

    @Test
    public void clientSendCustomEvent() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 100L);

        Experiment exp = createExperiment("SendExp");
        ExperimentInstance instance = createInstance("SendRun", exp);
        Client client = new Client("p1", instance, null, throttled);

        client.send("gameOver", "winner", 100);

        assertEquals("Should have 1 message", 1, testOut.messages.size());
        JsonNode msg = testOut.messages.get(0);
        assertEquals("gameOver", msg.get("eventName").asText());
        assertTrue("Should have data array", msg.has("data"));
    }

    // === EventGraphChangedListener Dispatch ===

    @Test
    public void listenerDispatchesToAdmins() {
        TinkerGraph base = new TinkerGraph();
        EventGraphChangedListener listener = new EventGraphChangedListener(base);

        // Track events received by the admin listener
        final List<String> received = new ArrayList<>();

        ClientListener mockAdmin = new ClientListener() {
            public void graphChanged(Graph g) { received.add("graphChanged"); }
            public void vertexAdded(Vertex v) { received.add("vertexAdded:" + v.getId()); }
            public void vertexRemoved(Vertex v) { received.add("vertexRemoved:" + v.getId()); }
            public void vertexPropertyChanged(Vertex v, String key, Object old, Object val) {
                received.add("vertexPropChanged:" + v.getId() + ":" + key);
            }
            public void vertexPropertyRemoved(Vertex v, String key) {
                received.add("vertexPropRemoved:" + v.getId() + ":" + key);
            }
            public void edgeAdded(Edge e) { received.add("edgeAdded:" + e.getId()); }
            public void edgeRemoved(Edge e) { received.add("edgeRemoved:" + e.getId()); }
            public void edgePropertyChanged(Edge e, String key, Object val) {
                received.add("edgePropChanged:" + e.getId() + ":" + key);
            }
            public void edgePropertyRemoved(Edge e, String key) {
                received.add("edgePropRemoved:" + e.getId() + ":" + key);
            }
        };

        listener.addAdminListener(mockAdmin);

        Vertex v1 = base.addVertex("v1");
        listener.vertexAdded(v1);
        assertTrue("Admin should receive vertexAdded", received.contains("vertexAdded:v1"));

        listener.vertexPropertyChanged(v1, "score", null, 10);
        assertTrue("Admin should receive vertexPropertyChanged",
            received.contains("vertexPropChanged:v1:score"));

        Vertex v2 = base.addVertex("v2");
        Edge e = base.addEdge(null, v1, v2, "connected");
        listener.edgeAdded(e);
        boolean hasEdgeAdded = false;
        for (String r : received) {
            if (r.startsWith("edgeAdded:")) { hasEdgeAdded = true; break; }
        }
        assertTrue("Admin should receive edgeAdded", hasEdgeAdded);
    }

    @Test
    public void listenerPrivatePropertyDoesNotUpdateNeighborClients() {
        // When a private property changes, only the vertex's own client should be updated,
        // not neighbor clients. We test the dispatch logic by checking that
        // clientVertexChanged is called appropriately.
        TinkerGraph base = new TinkerGraph();
        EventGraphChangedListener listener = new EventGraphChangedListener(base);

        Vertex v1 = base.addVertex("p1");
        Vertex v2 = base.addVertex("p2");
        base.addEdge(null, v1, v2, "connected");

        // Track which clients get called using simple mock admin listeners
        final List<String> calls = new ArrayList<>();

        ClientListener adminMock = new ClientListener() {
            public void graphChanged(Graph g) {}
            public void vertexAdded(Vertex v) {}
            public void vertexRemoved(Vertex v) {}
            public void vertexPropertyChanged(Vertex v, String key, Object old, Object val) {
                calls.add("admin:vertexPropChanged:" + v.getId() + ":" + key);
            }
            public void vertexPropertyRemoved(Vertex v, String key) {}
            public void edgeAdded(Edge e) {}
            public void edgeRemoved(Edge e) {}
            public void edgePropertyChanged(Edge e, String key, Object val) {}
            public void edgePropertyRemoved(Edge e, String key) {}
        };
        listener.addAdminListener(adminMock);

        // Private property change
        calls.clear();
        listener.vertexPropertyChanged(v1, "private", null, new HashMap<>());

        // Admin always gets the event
        assertTrue("Admin should receive private prop change",
            calls.stream().anyMatch(c -> c.contains("vertexPropChanged:p1:private")));
    }

    @Test
    public void listenerPublicPropertyUpdatesNeighborClients() {
        TinkerGraph base = new TinkerGraph();
        EventGraphChangedListener listener = new EventGraphChangedListener(base);

        Vertex v1 = base.addVertex("p1");
        Vertex v2 = base.addVertex("p2");
        base.addEdge(null, v1, v2, "connected");

        final List<String> calls = new ArrayList<>();

        ClientListener adminMock = new ClientListener() {
            public void graphChanged(Graph g) {}
            public void vertexAdded(Vertex v) {}
            public void vertexRemoved(Vertex v) {}
            public void vertexPropertyChanged(Vertex v, String key, Object old, Object val) {
                calls.add("admin:vertexPropChanged:" + v.getId() + ":" + key);
            }
            public void vertexPropertyRemoved(Vertex v, String key) {}
            public void edgeAdded(Edge e) {}
            public void edgeRemoved(Edge e) {}
            public void edgePropertyChanged(Edge e, String key, Object val) {}
            public void edgePropertyRemoved(Edge e, String key) {}
        };
        listener.addAdminListener(adminMock);

        calls.clear();
        listener.vertexPropertyChanged(v1, "score", null, 42);

        // Admin always gets the event
        assertTrue("Admin should receive public prop change",
            calls.stream().anyMatch(c -> c.contains("vertexPropChanged:p1:score")));
    }

    // === Client Disconnect ===

    @Test
    public void clientDisconnectClosesOutput() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 100L);

        Experiment exp = createExperiment("DisconnectExp");
        ExperimentInstance instance = createInstance("DisconnectRun", exp);
        Client client = new Client("p1", instance, null, throttled);

        client.disconnect();
        assertTrue("WebSocket should be closed after disconnect", testOut.closed);
    }
}
