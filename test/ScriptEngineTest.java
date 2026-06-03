import models.*;
import org.junit.Test;

import javax.script.*;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Integration tests for the Gremlin-Groovy ScriptEngine.
 * Tests engine initialization, script loading, Java-Groovy interface bridging,
 * binding injection, and the EventBus pub/sub system.
 *
 * These tests validate the most fragile subsystem in the application --
 * the one most likely to break during a Play Framework upgrade due to
 * classpath and classloader changes.
 *
 * No FakeApplication/DB is needed: the engine is built standalone via
 * {@link ScriptTestHarness}, which is the single source of truth for the script
 * load order and binding setup (mirroring ScriptBoard.resetEngine).
 */
public class ScriptEngineTest {

    private ScriptEngine createEngine() throws Exception {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("gremlin-groovy");
        return engine;
    }

    private ScriptEngine createAndInitializeEngine() throws Exception {
        // Delegate to the shared harness so the script list and binding setup live in
        // exactly one place. Persistence is disabled and no running Play app is required.
        return new ScriptTestHarness().engine;
    }

    // === Engine Discovery ===

    @Test
    public void gremlinGroovyEngineIsAvailable() throws Exception {
        ScriptEngine engine = createEngine();
        assertNotNull("ScriptEngineManager should find 'gremlin-groovy' engine", engine);
        assertTrue("Engine should be Invocable", engine instanceof Invocable);
    }

    @Test
    public void engineCanEvalSimpleGroovy() throws Exception {
        ScriptEngine engine = createEngine();
        Object result = engine.eval("1 + 1");
        assertEquals("Should evaluate simple expression", 2, result);
    }

    // === Script Loading ===

    @Test
    public void allGroovyScriptsLoad() throws Exception {
        // This is the critical test: all 10 scripts load without error
        ScriptEngine engine = createAndInitializeEngine();
        assertNotNull("Engine should be initialized after loading scripts", engine);
    }

    @Test
    public void globalsExistAfterScriptLoading() throws Exception {
        ScriptEngine engine = createAndInitializeEngine();

        // These globals are created by the Groovy scripts
        assertNotNull("'a' (PlayerActions) should exist", engine.get("a"));
        assertNotNull("'d' (ObservableMap/userData) should exist", engine.get("d"));
        assertNotNull("'g' (BreadboardGraph) should exist", engine.get("g"));
        assertNotNull("'stepFactory' should exist", engine.get("stepFactory"));
        assertNotNull("'timers' should exist", engine.get("timers"));
    }

    // === Java-Groovy Interface Bridge ===

    @Test
    public void playerActionsInterfaceBridge() throws Exception {
        ScriptEngine engine = createAndInitializeEngine();
        Invocable inv = (Invocable) engine;

        Object a = engine.get("a");
        assertNotNull("PlayerActions groovy object should exist", a);

        PlayerActionsInterface playerActions = inv.getInterface(a, PlayerActionsInterface.class);
        assertNotNull("Should create PlayerActionsInterface proxy", playerActions);
    }

    @Test
    public void breadboardGraphInterfaceBridge() throws Exception {
        ScriptEngine engine = createAndInitializeEngine();
        Invocable inv = (Invocable) engine;

        Object g = engine.get("g");
        assertNotNull("BreadboardGraph groovy object should exist", g);

        BreadboardGraphInterface graphInterface = inv.getInterface(g, BreadboardGraphInterface.class);
        assertNotNull("Should create BreadboardGraphInterface proxy", graphInterface);
    }

    @Test
    public void userDataGroovyObjectExists() throws Exception {
        ScriptEngine engine = createAndInitializeEngine();

        Object d = engine.get("d");
        assertNotNull("UserData groovy object ('d') should exist", d);

        // Verify it's an ObservableMap by checking its type via Groovy
        Object isMap = engine.eval("d instanceof groovy.util.ObservableMap");
        assertEquals("'d' should be an ObservableMap", true, isMap);
    }

    @Test
    public void graphInterfaceAddAndRemovePlayer() throws Exception {
        ScriptEngine engine = createAndInitializeEngine();
        Invocable inv = (Invocable) engine;

        Object g = engine.get("g");
        BreadboardGraphInterface graphInterface = inv.getInterface(g, BreadboardGraphInterface.class);

        // Add a player
        graphInterface.addPlayer("player1");

        // Verify vertex exists via script
        Object vertex = engine.eval("g.getVertex('player1')");
        assertNotNull("Player vertex should exist after addPlayer", vertex);

        // Remove the player
        graphInterface.removePlayer("player1");
        Object removed = engine.eval("g.getVertex('player1')");
        assertNull("Player vertex should be null after removePlayer", removed);
    }

    @Test
    public void userDataPutAndGetViaScript() throws Exception {
        ScriptEngine engine = createAndInitializeEngine();

        // Test the 'd' ObservableMap via script evaluation
        engine.eval("d.put('testKey', 'testValue')");
        Object value = engine.eval("d.get('testKey')");
        assertEquals("Should retrieve stored value", "testValue", value);

        Object size = engine.eval("d.size()");
        assertEquals("Size should be 1", 1, size);

        engine.eval("d.clear()");
        Object isEmpty = engine.eval("d.isEmpty()");
        assertEquals("Should be empty after clear", true, isEmpty);
    }

    // === Binding Injection ===

    @Test
    public void coreBindingsAreAccessible() throws Exception {
        ScriptEngine engine = createAndInitializeEngine();

        // Verify bindings injected in createAndInitializeEngine
        assertNotNull("'r' (Random) should be accessible", engine.eval("r"));
        assertNotNull("'results' should be accessible", engine.eval("results"));
        assertNotNull("'eventTracker' should be accessible", engine.eval("eventTracker"));
        assertNotNull("'gameListener' should be accessible", engine.eval("gameListener"));
        assertNotNull("'events' (EventBus) should be accessible", engine.eval("events"));
    }

    @Test
    public void scriptCanUseRandomBinding() throws Exception {
        ScriptEngine engine = createAndInitializeEngine();
        Object result = engine.eval("r.nextInt(100)");
        assertNotNull("Should generate random number", result);
        assertTrue("Result should be an integer", result instanceof Integer);
        int val = (Integer) result;
        assertTrue("Random value should be in range", val >= 0 && val < 100);
    }

    // === Step Execution ===

    @Test
    public void createStepViaFactory() throws Exception {
        ScriptEngine engine = createAndInitializeEngine();

        Object step = engine.eval(
            "testStep = stepFactory.createStep('TestStep');\n" +
            "testStep.run = { println('running test step') };\n" +
            "testStep.done = { println('test step done') };\n" +
            "testStep"
        );
        assertNotNull("Step should be created via factory", step);
    }

    @Test
    public void stepFactoryCreateNoUserActionStep() throws Exception {
        ScriptEngine engine = createAndInitializeEngine();

        Object step = engine.eval(
            "noActionStep = stepFactory.createNoUserActionStep('NoAction');\n" +
            "noActionStep"
        );
        assertNotNull("No-user-action step should be created", step);
    }

    // === Script Error Handling ===

    @Test
    public void compilationErrorDoesNotCrashEngine() throws Exception {
        ScriptEngine engine = createAndInitializeEngine();

        try {
            engine.eval("this is not valid groovy{{{");
            fail("Should throw ScriptException for invalid syntax");
        } catch (ScriptException e) {
            // Expected
        }

        // Engine should still work after error
        Object result = engine.eval("1 + 2");
        assertEquals("Engine should recover after compilation error", 3, result);
    }

    @Test
    public void runtimeErrorDoesNotCrashEngine() throws Exception {
        ScriptEngine engine = createAndInitializeEngine();

        try {
            engine.eval("def x = null; x.someMethod()");
            fail("Should throw exception for null method call");
        } catch (ScriptException e) {
            // Expected
        }

        // Engine should still work
        Object result = engine.eval("2 + 3");
        assertEquals("Engine should recover after runtime error", 5, result);
    }

    // NOTE: EventBus pub/sub is covered by EventBusTest (it needs neither the engine
    // nor a DB, so it does not belong here).

    // === Graph Operations via Script ===

    @Test
    public void graphAddVerticesAndEdges() throws Exception {
        ScriptEngine engine = createAndInitializeEngine();

        engine.eval("g.addPlayer('p1')");
        engine.eval("g.addPlayer('p2')");
        engine.eval("g.addEdge(g.getVertex('p1'), g.getVertex('p2'))");

        Object vertexCount = engine.eval("g.V.count()");
        assertNotNull(vertexCount);
        // Count should be 2
        assertEquals("Should have 2 vertices", 2L, ((Number) vertexCount).longValue());

        Object edgeCount = engine.eval("g.E.count()");
        assertNotNull(edgeCount);
        assertEquals("Should have 1 edge", 1L, ((Number) edgeCount).longValue());
    }

    @Test
    public void graphVertexProperties() throws Exception {
        ScriptEngine engine = createAndInitializeEngine();

        engine.eval("g.addPlayer('p1')");
        engine.eval("g.getVertex('p1').score = 42");

        Object score = engine.eval("g.getVertex('p1').score");
        assertEquals("Vertex property should be retrievable", 42, score);
    }

    @Test
    public void graphNeighborsTraversal() throws Exception {
        ScriptEngine engine = createAndInitializeEngine();

        engine.eval("g.addPlayer('center')");
        engine.eval("g.addPlayer('n1')");
        engine.eval("g.addPlayer('n2')");
        engine.eval("g.addEdge(g.getVertex('center'), g.getVertex('n1'))");
        engine.eval("g.addEdge(g.getVertex('center'), g.getVertex('n2'))");

        Object neighborCount = engine.eval("g.getVertex('center').neighbors.count()");
        assertEquals("Center should have 2 neighbors", 2L, ((Number) neighborCount).longValue());
    }

    @Test
    public void graphEmptyClearsAll() throws Exception {
        ScriptEngine engine = createAndInitializeEngine();

        engine.eval("g.addPlayer('p1')");
        engine.eval("g.addPlayer('p2')");

        engine.eval("g.empty()");

        Object count = engine.eval("g.V.count()");
        assertEquals("Graph should be empty after g.empty()", 0L, ((Number) count).longValue());
    }
}
