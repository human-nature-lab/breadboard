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

    // NOTE: there used to be an @After here that reflectively cleared
    // EventGraphChangedListener.clientListeners because it was a `private static` map
    // shared across every listener / test (a leak -- see MEMORY_LEAKS.md L2). That field
    // is now an instance field, so each test's own listener is isolated and no cross-test
    // cleanup is needed. `clientRegistriesAreIsolatedPerListener` below pins that.

    // === ThrottledWebSocketOut ===
    //
    // NOTE: throttling is currently DISABLED in production -- the queue/timer logic in
    // ThrottledWebSocketOut.write() is commented out and it simply forwards every message
    // to the wrapped Out immediately (ThrottledWebSocketOut.java). These tests therefore
    // pin the *current* behaviour: a transparent pass-through wrapper. The `wait` argument
    // is unused today. If batching/throttling is ever restored, these tests must be
    // rewritten to assert timing/coalescing (and the `wait` value below will become
    // meaningful) -- they are written here so that regression is loud rather than silent.

    @Test
    public void writesPassThroughImmediately() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 100L);

        ObjectNode first = Json.newObject();
        first.put("action", "test");
        throttled.write(first);

        // No coalescing today: a second write arrives as its own frame, not merged.
        for (int i = 0; i < 4; i++) {
            ObjectNode msg = Json.newObject();
            msg.put("index", i);
            throttled.write(msg);
        }

        assertEquals("Throttling is disabled: all 5 frames pass straight through",
            5, testOut.messages.size());
        assertEquals("First frame forwarded verbatim", "test",
            testOut.messages.get(0).get("action").asText());
    }

    @Test
    public void closeIsDelegatedToWrappedOut() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 100L);

        throttled.close();
        assertTrue("close() should be delegated to the wrapped Out", testOut.closed);
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

        // A "private"-prefixed key on the player's OWN vertex is delivered to that player.
        clientP1.vertexPropertyChanged(p1, "private", priv);
        assertEquals("Owner (p1) should receive their own private property change",
            1, testOut.messages.size());
        assertEquals("nodePropertyChanged", testOut.last().get("action").asText());
        assertEquals("Message should target p1", "p1", testOut.last().get("id").asText());

        // The same change must NOT be delivered to a different player's client.
        TestWebSocketOut testOut2 = new TestWebSocketOut();
        ThrottledWebSocketOut throttled2 = new ThrottledWebSocketOut(testOut2, 100L);
        Client clientP2 = new Client("p2", instance, null, throttled2);

        clientP2.vertexPropertyChanged(p1, "private", priv);
        assertEquals("Non-owner (p2) must NOT receive p1's private property",
            0, testOut2.messages.size());
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

    /** Find the node object with the given id inside a D3 "graph.nodes" array. */
    private static JsonNode findNode(JsonNode nodes, String id) {
        for (JsonNode n : nodes) {
            if (n.has("id") && id.equals(n.get("id").asText())) {
                return n;
            }
        }
        return null;
    }

    @Test
    public void clientStripsPrivateKeysFromNeighborVertices() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 0L);

        Experiment exp = createExperiment("NeighborStripExp");
        ExperimentInstance instance = createInstance("NeighborStripRun", exp);
        Client client = new Client("p1", instance, null, throttled);

        TinkerGraph base = new TinkerGraph();
        EventGraph<TinkerGraph> graph = new EventGraph<>(base);
        Vertex p1 = graph.addVertex("p1");
        p1.setProperty("name", "Alice");
        Vertex p2 = graph.addVertex("p2");        // p1's neighbour
        p2.setProperty("name", "Bob");
        p2.setProperty("score", 30);
        p2.setProperty("text", "Bob's private prompt");
        p2.setProperty("choices", "[a, b]");
        Map<String, Object> p2Private = new HashMap<>();
        p2Private.put("secret", 1);
        p2.setProperty("private", p2Private);
        graph.addEdge(null, p1, p2, "connected");

        client.updateGraph(p1);

        JsonNode neighbor = findNode(testOut.last().get("graph").get("nodes"), "p2");
        assertNotNull("Neighbour p2 should appear in p1's subgraph", neighbor);
        assertTrue("Public neighbour props are kept", neighbor.has("name"));
        assertTrue("Public neighbour props are kept", neighbor.has("score"));
        assertFalse("Neighbour 'text' must be stripped", neighbor.has("text"));
        assertFalse("Neighbour 'choices' must be stripped", neighbor.has("choices"));
        assertFalse("Neighbour 'private' map must be stripped", neighbor.has("private"));
        assertFalse("Neighbour private sub-keys must NOT leak", neighbor.has("secret"));
    }

    @Test
    public void clientSeesOutPropsButNotInPropsOnOutgoingEdge() {
        TestWebSocketOut testOut = new TestWebSocketOut();
        ThrottledWebSocketOut throttled = new ThrottledWebSocketOut(testOut, 0L);

        Experiment exp = createExperiment("EdgePropsExp");
        ExperimentInstance instance = createInstance("EdgePropsRun", exp);
        Client client = new Client("p1", instance, null, throttled);

        TinkerGraph base = new TinkerGraph();
        EventGraph<TinkerGraph> graph = new EventGraph<>(base);
        Vertex p1 = graph.addVertex("p1");
        p1.setProperty("name", "Alice");
        Vertex p2 = graph.addVertex("p2");
        p2.setProperty("name", "Bob");
        // Edge p1 -> p2: p1 is the OUT (source) vertex, so it should see outProps, not inProps.
        Edge e = graph.addEdge(null, p1, p2, "connected");
        Map<String, Object> outProps = new HashMap<>();
        outProps.put("trust", 0.6);
        e.setProperty("outProps", outProps);
        Map<String, Object> inProps = new HashMap<>();
        inProps.put("trust", 0.8);
        e.setProperty("inProps", inProps);

        client.updateGraph(p1);

        JsonNode links = testOut.last().get("graph").get("links");
        assertEquals("Subgraph should contain the single edge", 1, links.size());
        JsonNode link = links.get(0);
        assertTrue("Source vertex should see its outProps expanded onto the edge", link.has("trust"));
        assertEquals("outProps.trust (0.6) is the value visible to the source", 0.6,
            link.get("trust").asDouble(), 0.0001);
        assertFalse("Raw 'outProps' map must not be sent", link.has("outProps"));
        assertFalse("Raw 'inProps' map must not be sent", link.has("inProps"));
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

    // An admin ClientListener that records the vertexPropertyChanged events it receives.
    private static ClientListener recordingAdmin(final List<String> calls) {
        return new ClientListener() {
            public void graphChanged(Graph g) {}
            public void vertexAdded(Vertex v) {}
            public void vertexRemoved(Vertex v) {}
            public void vertexPropertyChanged(Vertex v, String key, Object old, Object val) {
                calls.add("vertexPropChanged:" + v.getId() + ":" + key);
            }
            public void vertexPropertyRemoved(Vertex v, String key) {}
            public void edgeAdded(Edge e) {}
            public void edgeRemoved(Edge e) {}
            public void edgePropertyChanged(Edge e, String key, Object val) {}
            public void edgePropertyRemoved(Edge e, String key) {}
        };
    }

    @Test
    public void listenerPrivatePropertyDoesNotUpdateNeighborClients() {
        // p1 -- p2. A change to a PRIVATE key (private/text/choices) on p1 must update
        // only p1's own client, never the neighbour p2's client.
        Experiment exp = createExperiment("PrivDispatchExp");
        ExperimentInstance instance = createInstance("PrivDispatchRun", exp);

        TinkerGraph base = new TinkerGraph();
        EventGraph<TinkerGraph> graph = new EventGraph<>(base);
        Vertex v1 = graph.addVertex("p1");
        v1.setProperty("name", "Alice");
        Vertex v2 = graph.addVertex("p2");
        v2.setProperty("name", "Bob");
        graph.addEdge(null, v1, v2, "connected");

        EventGraphChangedListener listener = new EventGraphChangedListener(base);

        TestWebSocketOut outP1 = new TestWebSocketOut();
        TestWebSocketOut outP2 = new TestWebSocketOut();
        listener.addClientListener(new Client("p1", instance, null, new ThrottledWebSocketOut(outP1, 0L)));
        listener.addClientListener(new Client("p2", instance, null, new ThrottledWebSocketOut(outP2, 0L)));

        final List<String> adminCalls = new ArrayList<>();
        listener.addAdminListener(recordingAdmin(adminCalls));

        listener.vertexPropertyChanged(v1, "private", null, new HashMap<String, Object>());

        assertTrue("Owner p1's client should be updated on a private change", outP1.messages.size() > 0);
        assertEquals("Neighbour p2's client must NOT be updated on a private change",
            0, outP2.messages.size());
        assertTrue("Admin still receives every change",
            adminCalls.contains("vertexPropChanged:p1:private"));
    }

    @Test
    public void listenerPublicPropertyUpdatesNeighborClients() {
        // p1 -- p2. A change to a PUBLIC key (e.g. score) on p1 must update both p1's
        // own client AND the neighbour p2's client.
        Experiment exp = createExperiment("PubDispatchExp");
        ExperimentInstance instance = createInstance("PubDispatchRun", exp);

        TinkerGraph base = new TinkerGraph();
        EventGraph<TinkerGraph> graph = new EventGraph<>(base);
        Vertex v1 = graph.addVertex("p1");
        v1.setProperty("name", "Alice");
        Vertex v2 = graph.addVertex("p2");
        v2.setProperty("name", "Bob");
        graph.addEdge(null, v1, v2, "connected");

        EventGraphChangedListener listener = new EventGraphChangedListener(base);

        TestWebSocketOut outP1 = new TestWebSocketOut();
        TestWebSocketOut outP2 = new TestWebSocketOut();
        listener.addClientListener(new Client("p1", instance, null, new ThrottledWebSocketOut(outP1, 0L)));
        listener.addClientListener(new Client("p2", instance, null, new ThrottledWebSocketOut(outP2, 0L)));

        final List<String> adminCalls = new ArrayList<>();
        listener.addAdminListener(recordingAdmin(adminCalls));

        listener.vertexPropertyChanged(v1, "score", null, 42);

        assertTrue("Owner p1's client should be updated on a public change", outP1.messages.size() > 0);
        assertTrue("Neighbour p2's client SHOULD be updated on a public change", outP2.messages.size() > 0);
        assertTrue("Admin still receives every change",
            adminCalls.contains("vertexPropChanged:p1:score"));
    }

    // === Client Listener Registry: removal + isolation (MEMORY_LEAKS.md L2/L3) ===
    //
    // These pin the leak fix: a registered client is dispatched to exactly as before,
    // but once removed (which ScriptBoard.disconnectClients now does on reload) it must
    // no longer be referenced or dispatched to; and two listeners must not share state.

    @Test
    public void registeredClientReceivesUpdatesThenRemovedClientDoesNot() {
        Experiment exp = createExperiment("RemoveExp");
        ExperimentInstance instance = createInstance("RemoveRun", exp);

        TinkerGraph base = new TinkerGraph();
        EventGraph<TinkerGraph> graph = new EventGraph<>(base);
        Vertex v1 = graph.addVertex("p1");
        v1.setProperty("name", "Alice");

        EventGraphChangedListener listener = new EventGraphChangedListener(base);
        TestWebSocketOut outP1 = new TestWebSocketOut();
        Client c1 = new Client("p1", instance, null, new ThrottledWebSocketOut(outP1, 0L));
        listener.addClientListener(c1);

        // Existing behavior preserved: a registered client receives its own updates.
        listener.vertexPropertyChanged(v1, "score", null, 1);
        int afterFirst = outP1.messages.size();
        assertTrue("Registered client should receive updates (unchanged behavior)", afterFirst > 0);

        // Leak fix: after removal the client is gone from the registry and gets nothing more.
        listener.removeClientListener(c1);
        assertFalse("Registry must no longer reference the removed client",
            listener.getClientListeners().containsKey("p1"));

        listener.vertexPropertyChanged(v1, "score", null, 2);
        assertEquals("Removed client must not receive any further updates",
            afterFirst, outP1.messages.size());
    }

    @Test
    public void clientListenerRegistryShrinksOnRemove() {
        Experiment exp = createExperiment("ShrinkExp");
        ExperimentInstance instance = createInstance("ShrinkRun", exp);
        ThrottledWebSocketOut out = new ThrottledWebSocketOut(new TestWebSocketOut(), 0L);

        EventGraphChangedListener listener = new EventGraphChangedListener(new TinkerGraph());
        Client c1 = new Client("p1", instance, null, out);
        Client c2 = new Client("p2", instance, null, out);
        listener.addClientListener(c1);
        listener.addClientListener(c2);
        assertEquals("Both clients registered", 2, listener.getClientListeners().size());

        listener.removeClientListener(c1);
        assertEquals("Registry shrinks after removal", 1, listener.getClientListeners().size());
        assertFalse(listener.getClientListeners().containsKey("p1"));
        assertTrue(listener.getClientListeners().containsKey("p2"));

        // Removing an unregistered/unknown client is a safe no-op.
        listener.removeClientListener(new Client("ghost", instance, null, out));
        assertEquals("Removing an unknown client changes nothing", 1, listener.getClientListeners().size());
    }

    @Test
    public void clientRegistriesAreIsolatedPerListener() {
        // Before the fix `clientListeners` was static, so a client added to one listener
        // was visible to every other listener. This asserts the per-instance isolation
        // that replaced it -- it would FAIL against the old static field.
        Experiment exp = createExperiment("IsolationExp");
        ExperimentInstance instance = createInstance("IsolationRun", exp);
        ThrottledWebSocketOut out = new ThrottledWebSocketOut(new TestWebSocketOut(), 0L);

        EventGraphChangedListener l1 = new EventGraphChangedListener(new TinkerGraph());
        EventGraphChangedListener l2 = new EventGraphChangedListener(new TinkerGraph());

        l1.addClientListener(new Client("p1", instance, null, out));

        assertEquals("First listener holds its own client", 1, l1.getClientListeners().size());
        assertEquals("Second listener must NOT see the first listener's client (no shared static state)",
            0, l2.getClientListeners().size());
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
