import models.EventBus;
import models.EventTracker;
import models.ExperimentContext;
import models.GameListener;
import models.ScriptLoader;
import org.junit.Test;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Tests for {@link ScriptLoader}: the load-order resolution (core first, then *.groovy
 * alphabetically, skipping *_test.groovy) and the generated-name -> real-name translation
 * used to make {@code Script<N>.groovy} readable in error messages and stack traces.
 */
public class ScriptLoaderTest {

    // === load-order resolution ===

    @Test
    public void resolveLoadOrderPutsCorePresentFirstThenAlphaAndSkipsTests() throws Exception {
        File dir = Files.createTempDirectory("bb-scripts").toFile();
        for (String n : new String[] {
            "util.groovy", "timer.groovy", "actions.groovy",   // core, intentionally out of order
            "zeta.groovy", "alpha.groovy", "beta.groovy",       // non-core (synthetic names, not real core scripts)
            "my_test.groovy", "notes.txt"                        // must be skipped
        }) {
            assertTrue(new File(dir, n).createNewFile());
        }

        List<String> order = ScriptLoader.resolveLoadOrder(dir);

        // Core files first in CORE_ORDER (only those present: util, timer, actions), then the
        // remaining *.groovy alphabetically. Test scripts and non-groovy files are excluded.
        // Synthetic non-core names are used so this test stays decoupled from which real scripts
        // are core (e.g. groups.groovy is core, so it would NOT sort alphabetically here).
        assertEquals(Arrays.asList(
            "util.groovy", "timer.groovy", "actions.groovy",
            "alpha.groovy", "beta.groovy", "zeta.groovy"
        ), order);
    }

    @Test
    public void resolveLoadOrderGatesExperimentalScriptsOnTheFlag() throws Exception {
        File dir = Files.createTempDirectory("bb-scripts-exp").toFile();
        // an experimental script alongside a core and a plain non-core script
        String experimental = ScriptLoader.EXPERIMENTAL_SCRIPTS.iterator().next();
        for (String n : new String[] { "util.groovy", "alpha.groovy", experimental }) {
            assertTrue(new File(dir, n).createNewFile());
        }
        assertTrue(ScriptLoader.isExperimental(experimental));
        assertFalse(ScriptLoader.isExperimental("alpha.groovy"));

        // off: experimental script is excluded; everything else loads as usual
        assertFalse(ScriptLoader.resolveLoadOrder(dir, false).contains(experimental));
        assertEquals(Arrays.asList("util.groovy", "alpha.groovy"),
            ScriptLoader.resolveLoadOrder(dir, false));

        // on (and the no-flag overload, which defaults on): experimental script is included
        assertTrue(ScriptLoader.resolveLoadOrder(dir, true).contains(experimental));
        assertTrue(ScriptLoader.resolveLoadOrder(dir).contains(experimental));
    }

    @Test
    public void resolveLoadOrderGatesCoreExperimentalScriptsButKeepsCoreOrdering() throws Exception {
        File dir = Files.createTempDirectory("bb-scripts-core-exp").toFile();
        // groups.groovy is both core (order-sensitive) and experimental (gated)
        String coreExp = "groups.groovy";
        assertTrue(ScriptLoader.isCore(coreExp));
        assertTrue(ScriptLoader.isExperimental(coreExp));
        for (String n : new String[] { "util.groovy", "events.groovy", coreExp, "zeta.groovy" }) {
            assertTrue(new File(dir, n).createNewFile());
        }

        // off: the core+experimental script is excluded; the rest keep core-then-alpha order
        assertEquals(Arrays.asList("util.groovy", "events.groovy", "zeta.groovy"),
            ScriptLoader.resolveLoadOrder(dir, false));

        // on: it loads in its core position (after events, before the non-core zeta)
        assertEquals(Arrays.asList("util.groovy", "events.groovy", "groups.groovy", "zeta.groovy"),
            ScriptLoader.resolveLoadOrder(dir, true));
    }

    @Test
    public void isLoadableSkipsTestScriptsAndNonGroovy() {
        assertTrue(ScriptLoader.isLoadable("graph.groovy"));
        assertFalse(ScriptLoader.isLoadable("waiting_room_test.groovy"));
        assertFalse(ScriptLoader.isLoadable("notes.txt"));
        assertFalse(ScriptLoader.isLoadable(null));
    }

    // === message humanization ===

    @Test
    public void humanizeReplacesMappedGeneratedNamesAndLeavesOthers() {
        ScriptLoader.resetScriptNames();
        ScriptLoader.registerScriptName("Script7", "graph.groovy");

        assertEquals("error in graph.groovy: 5: boom",
            ScriptLoader.humanize("error in Script7.groovy: 5: boom"));
        // bare class name (no .groovy) is mapped too
        assertEquals("at graph.groovy.foo()", ScriptLoader.humanize("at Script7.foo()"));
        // unmapped generated name is left untouched
        assertEquals("Script99.groovy", ScriptLoader.humanize("Script99.groovy"));
        // identifiers that merely contain "Script<N>" are not touched (word boundary)
        assertEquals("MyScript7Helper", ScriptLoader.humanize("MyScript7Helper"));
        assertNull(ScriptLoader.humanize(null));
    }

    // === stack-trace humanization ===

    @Test
    public void humanizeStackTraceRewritesOnlyMappedFrameFileNames() {
        ScriptLoader.resetScriptNames();
        ScriptLoader.registerScriptName("Script3", "timer.groovy");

        RuntimeException e = new RuntimeException("boom");
        e.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("Script3", "run", "Script3.groovy", 42),
            new StackTraceElement("models.Foo", "bar", "Foo.java", 1),
        });

        assertSame(e, ScriptLoader.humanizeStackTrace(e));
        StackTraceElement[] frames = e.getStackTrace();
        assertEquals("timer.groovy", frames[0].getFileName());
        assertEquals(42, frames[0].getLineNumber());          // line preserved
        assertEquals("Script3", frames[0].getClassName());    // class name preserved
        assertEquals("Foo.java", frames[1].getFileName());    // unrelated frame untouched
    }

    // === end-to-end: real scripts self-register their names through the live engine ===

    @Test
    public void loadAllRegistersRealFileNamesThroughTheEngine() throws Exception {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("gremlin-groovy");
        assertNotNull("gremlin-groovy engine should be on the classpath", engine);

        EventTracker eventTracker = new EventTracker();
        eventTracker.disable();                               // persistence off
        Bindings b = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        b.put("r", new Random());
        b.put("results", new HashMap());
        b.put("eventTracker", eventTracker);
        b.put("gameListener", new GameListener());
        b.put("events", new EventBus());
        // groups.groovy reads experimentContext.dataDir at load time to build its group-id sequence.
        // A null-args ExperimentContext (dataDir == null) yields the in-memory sequence -- same as
        // ScriptTestHarness binds. Without it, loading groups.groovy throws MissingPropertyException.
        b.put("experimentContext", new ExperimentContext(null, null, null));

        // cwd is the project root under sbt, so the scripts live in ./groovy
        ScriptLoader.loadAll(engine, new File("groovy"));

        Map<String, String> names = ScriptLoader.scriptNamesSnapshot();
        // Every loaded script self-registered: the map's values are the real file names. This
        // also proves the epilogue's `models.ScriptLoader.registerScriptName(...)` call resolves
        // from inside the gremlin-groovy engine.
        assertTrue("expected util.groovy among " + names, names.values().contains("util.groovy"));
        assertTrue("expected timer.groovy among " + names, names.values().contains("timer.groovy"));
        assertTrue("expected graph.groovy among " + names, names.values().contains("graph.groovy"));

        // And the mapping actually translates a generated name for one of them.
        String generatedForGraph = null;
        for (Map.Entry<String, String> e : names.entrySet()) {
            if ("graph.groovy".equals(e.getValue())) generatedForGraph = e.getKey();
        }
        assertNotNull(generatedForGraph);
        assertEquals("graph.groovy:12",
            ScriptLoader.humanize(generatedForGraph + ".groovy:12"));
    }
}
