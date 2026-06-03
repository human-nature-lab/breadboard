import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Demonstrates {@link ScriptTestHarness}: driving the DSL with no database and signalling
 * async completion via the injected {@code done} closure.
 *
 * Note: these tests do NOT extend BaseTest -- no FakeApplication, no H2. The engine runs
 * standalone with persistence disabled, so they are fast and isolated.
 */
public class ScriptHarnessTest {

    private ScriptTestHarness harness;

    @Before
    public void setUp() {
        harness = new ScriptTestHarness();
    }

    @After
    public void tearDown() {
        if (harness != null) harness.close();
    }

    // === Synchronous ===

    @Test
    public void syncGraphMutation() {
        harness.runSync(
            "{ ->\n" +
            "  g.addPlayer('p1')\n" +
            "  g.addPlayer('p2')\n" +
            "  g.addEdge(g.getVertex('p1'), g.getVertex('p2'))\n" +
            "  assert g.V.count() == 2\n" +
            "  assert g.E.count() == 1\n" +
            "}");
    }

    // === Async via a real timer ===

    @Test
    public void asyncTimerCallsDone() {
        harness.runAsync(
            "{ done ->\n" +
            "  g.addPlayer('p1')\n" +
            "  timers.newTimer().runAfter(50) {\n" +
            "    done { assert g.getVertex('p1') != null }\n" +
            "  }\n" +
            "}", 2000);
    }

    // === Async via the Step lifecycle (no-user-action step completes through done) ===

    @Test
    public void asyncStepLifecycle() {
        harness.runAsync(
            "{ done ->\n" +
            "  def s = stepFactory.createStep('s1')\n" +
            "  s.run  = { g.addPlayer('p1') }\n" +
            "  s.done = { done { assert g.getVertex('p1') != null } }\n" +
            "  s.start()\n" +
            "}", 2000);
    }

    // === done() can be called from a free binding inside any nested closure ===

    @Test
    public void doneIsCallableAsGlobalBinding() {
        harness.runAsync(
            "{ done ->\n" +
            "  timers.newTimer().runAfter(20) { done() }\n" +   // bare done(), success
            "}", 2000);
    }

    // === Failure propagation: an assertion routed through done surfaces as AssertionError ===

    @Test
    public void asyncAssertionFailureSurfaces() {
        try {
            harness.runAsync(
                "{ done ->\n" +
                "  timers.newTimer().runAfter(20) { done { assert 1 == 2 } }\n" +
                "}", 2000);
            fail("Expected the async assertion failure to propagate");
        } catch (AssertionError expected) {
            // good: failure from the timer thread was captured and rethrown on the test thread
        }
    }

    // === done(throwable) explicitly fails ===

    @Test
    public void asyncExplicitThrowableFails() {
        try {
            harness.runAsync(
                "{ done -> done(new IllegalStateException('boom')) }", 2000);
            fail("Expected explicit failure to propagate");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage() == null || expected.getCause() != null
                       || expected.getMessage().contains("boom"));
        }
    }

    // === Timeout when done() is never called ===

    @Test
    public void timeoutWhenDoneNeverCalled() {
        try {
            harness.runAsync("{ done -> /* never signals */ }", 300);
            fail("Expected a timeout");
        } catch (AssertionError e) {
            assertTrue("Message should mention done()", e.getMessage().contains("did not call done()"));
        }
    }

    // === Persistence really is off: tracking an event with no instance does not throw ===

    @Test
    public void eventTrackingIsDisabled() {
        assertFalse("harness should not have recorded a finish yet", harness.gameListener.finished());
        // eventTracker.track on a disabled tracker is a no-op (no DB, no exception)
        harness.runSync(
            "{ ->\n" +
            "  eventTracker.track('SomethingHappened', ['k': 'v'])\n" +
            "  assert true\n" +
            "}");
    }
}
