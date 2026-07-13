import groovy.lang.Closure;
import models.EventBus;
import models.EventTracker;
import models.GameListener;
import models.ScriptLoader;
import org.apache.commons.io.FileUtils;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.InvokerInvocationException;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A test harness for driving the Gremlin-Groovy ScriptEngine the same way
 * {@code ScriptBoard.resetEngine} does, but WITHOUT a database or running Play
 * application:
 *
 * <ul>
 *   <li><b>Persistence is disabled</b> -- the {@link EventTracker} is disabled and the
 *       {@link GameListener} has no {@code ExperimentInstance}, so {@code track()} /
 *       {@code finish()} / {@code start()} etc. become no-ops instead of DB writes.</li>
 *   <li><b>No FakeApplication</b> -- the groovy scripts are Play-free, so the engine is
 *       built standalone. Scripts are resolved relative to the project working directory.</li>
 * </ul>
 *
 * <h3>Async testing</h3>
 * The DSL is event-driven: steps complete via listeners, timers fire later on their own
 * threads. To test that, write the test body as a Groovy closure and signal completion
 * with the injected {@code done} closure. Supported signatures:
 *
 * <pre>{@code
 * // 1. Synchronous
 * harness.runSync("{ -> g.addPlayer('p1'); assert g.V.count() == 1 }");
 *
 * // 2. Async -- closure receives `done`; call it when the async work resolves
 * harness.runAsync("{ done ->\n" +
 *     "  timers.newTimer().runAfter(50) { done() }\n" +
 *     "}", 2000);
 *
 * // 3. Async with assertions on the callback thread (failures surface as AssertionError,
 * //    instead of being swallowed by the timer/listener thread):
 * harness.runAsync("{ done ->\n" +
 *     "  def s = stepFactory.createStep('s1')\n" +
 *     "  s.run  = { g.addPlayer('p1') }\n" +
 *     "  s.done = { done { assert g.getVertex('p1') != null } }\n" +
 *     "  s.start()\n" +
 *     "}", 2000);
 * }</pre>
 *
 * {@code done} accepts three forms, callable from anywhere in the engine (it is also a
 * global binding, so nested timer/listener closures can call it directly):
 * <ul>
 *   <li>{@code done()} -- success</li>
 *   <li>{@code done { assert ... }} -- run assertions safely, then complete</li>
 *   <li>{@code done(throwable)} -- fail with the given throwable</li>
 * </ul>
 */
public class ScriptTestHarness {

    /**
     * The eval-ready source of each script (raw file contents + ";null;"), keyed by file name and
     * read from disk once per JVM. Both {@link #prepare()} calls (the constructor's, and every
     * {@link #reset()}) re-read the core scripts; this cache makes the re-reads free. Script bodies
     * don't change during a run, so the text is stable — and feeding the same text back to {@code
     * engine.eval} is what lets Groovy reuse the already-compiled class on {@code reset()}.
     */
    private static final ConcurrentHashMap<String, String> SOURCE_CACHE =
        new ConcurrentHashMap<String, String>();

    /**
     * Shared across all harnesses, cleared in each constructor. events.groovy installs
     * {@code Vertex.metaClass.on/once/off/send} -- JVM-global metaclass mutations whose closures
     * permanently capture the {@code events} binding of the FIRST harness to evaluate events.groovy
     * (re-assigning a global metaclass method from a later engine does NOT replace the closure). With a
     * per-harness {@code new EventBus()}, every harness after the first desynced: {@code player.on(...)}
     * routed through the stale metaclass closure registered listeners on the first harness's bus, while
     * that harness's own CustomEvent handler emitted on its own bus -- so player-scoped events never
     * delivered and group choices never resolved (in ANY harness after the first, on any thread). A
     * single shared bus keeps the metaclass closure and every harness pointed at the same instance;
     * clearing it per harness keeps listeners from leaking between cases. This matches production, which
     * uses one static EventBus for the life of the engine. Safe because tests run sequentially
     * (parallelExecution in Test := false).
     */
    private static final EventBus SHARED_EVENTS = new EventBus();

    public final ScriptEngine engine;
    // Not final: reset() rebuilds these per case so a reused engine starts each case with a fresh
    // tracker/listener (e.g. RecordingGameListener.finishCount must not carry over between cases).
    public EventTracker eventTracker;
    public RecordingGameListener gameListener;

    public ScriptTestHarness() {
        try {
            this.engine = new ScriptEngineManager().getEngineByName("gremlin-groovy");
            if (this.engine == null) {
                throw new IllegalStateException("gremlin-groovy ScriptEngine not found on the classpath");
            }
            prepare();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize ScriptTestHarness", e);
        }
    }

    /**
     * Return this engine to a pristine per-case state <b>without recreating it</b>.
     *
     * <p>Building a gremlin-groovy engine and compiling the ~7.6k lines of core scripts costs ~8s.
     * RE-evaluating a script that the same engine has already compiled is ~0ms — Groovy caches the
     * compiled class per engine, keyed by source text. So a runner that drives ~100 cases can build
     * <i>one</i> engine and call {@code reset()} before each case instead of paying the 8s compile
     * every time (measured: ~14 min of compilation across a run collapses to a single compile).
     *
     * <p>Re-evaluating the core scripts re-runs their top-level initializers — {@code g = new
     * BreadboardGraph(...)}, {@code a = new PlayerActions(...)}, {@code timers = new BBTimers()},
     * {@code GroupContext.bind(g, a, events)}, {@code GroupContext.bindRecruitment(recruitment)} —
     * so every case still gets fresh bindings. What re-eval does <i>not</i> reset are script-defined
     * <b>statics</b> (their initializers run once per class load, and each engine keeps its classes);
     * a brand-new engine would start those empty, so {@link #resetGroovyStatics()} reproduces that.
     * Clearing the bindings first also drops any global a previous case set ad hoc (e.g. {@code
     * player}). Tests run sequentially ({@code parallelExecution in Test := false}), so reuse is safe.
     */
    public void reset() {
        try {
            cancelTimers();   // stop any timer threads the previous case left running before we wipe state
            engine.getBindings(ScriptContext.ENGINE_SCOPE).clear();
            prepare();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reset ScriptTestHarness", e);
        }
    }

    /**
     * Bind the baseline globals and (re)evaluate every core script into {@link #engine}. Shared by
     * the constructor (first, paid-once compile) and {@link #reset()} (subsequent, cached re-evals).
     */
    private void prepare() throws Exception {
        this.eventTracker = new EventTracker();
        this.eventTracker.disable();                 // persistence off: track() becomes a no-op
        this.gameListener = new RecordingGameListener(); // no ExperimentInstance: lifecycle is a no-op

        Bindings b = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        b.put("r", new Random());
        b.put("results", new HashMap());
        b.put("eventTracker", eventTracker);
        b.put("gameListener", gameListener);
        SHARED_EVENTS.clear();                        // see SHARED_EVENTS: per-harness bus desyncs from the global Vertex.metaClass closures
        b.put("events", SHARED_EVENTS);
        // Experiment/instance context for the groovy DSL (see models.ExperimentContext). A null
        // dataDir means the groovy group-id sequence runs in memory and restarts at 1 each case --
        // a fresh context per prepare() is what the waiting-room tests' "1"/"2" ids rely on.
        b.put("experimentContext", new models.ExperimentContext(null, null, null));

        // Load in the same order production uses (ScriptLoader is the single source of
        // truth, so the harness and ScriptBoard can't drift). A core script failing to
        // load is fatal; a non-core drop-in script failing is skipped, matching production
        // and keeping the rest of the suite runnable.
        File dir = groovyDir();
        for (String name : ScriptLoader.resolveLoadOrder(dir)) {
            try {
                ScriptLoader.evalNamed(engine, name, loadSource(dir, name));
            } catch (ScriptException | RuntimeException ex) {
                if (ScriptLoader.isCore(name)) throw ex;
                System.err.println("ScriptTestHarness: skipping non-core script " + name + " -> " + ex.getMessage());
            }
        }
        resetGroovyStatics();
    }

    /**
     * Clear the two stateful statics that re-eval does not reset, so a reused engine matches a fresh
     * one: the {@code Games} registry ({@code byId}) and the default game builder. Best-effort —
     * {@code Games} is absent unless groups.groovy loaded (it does under the harness's experimental
     * load order), so both the Groovy body and this call swallow failures.
     */
    private void resetGroovyStatics() {
        try {
            engine.eval(
                "try { Games.all().toList().each { Games.remove(it.id) }; Games.define(null) }" +
                " catch (Throwable __ignore) {}");
        } catch (Exception ignored) {
            // groups.groovy not loaded, or Games unavailable -- nothing to reset.
        }
    }

    /** Bind an extra value into the engine (e.g. a stub ContentFetcher as {@code c}). */
    public void put(String name, Object value) {
        engine.getBindings(ScriptContext.ENGINE_SCOPE).put(name, value);
    }

    /** Evaluate a raw script and return its result. */
    public Object eval(String script) throws Exception {
        return engine.eval(script);
    }

    public Object get(String name) {
        return engine.get(name);
    }

    /**
     * Run a synchronous test body. The source must evaluate to a closure; it is invoked
     * with no arguments. Any thrown exception (including AssertionError) propagates.
     */
    public void runSync(String closureSource) {
        Object callable;
        try {
            callable = engine.eval(closureSource);
        } catch (Throwable t) {
            cancelTimers();
            rethrow(t);
            return;
        }
        if (!(callable instanceof Closure)) {
            cancelTimers();
            throw new IllegalArgumentException("runSync body must evaluate to a closure");
        }
        runSyncClosure((Closure) callable);
    }

    /**
     * Run a synchronous test body that is already a {@link Closure} (e.g. one registered by a
     * {@code *_test.groovy} file). Any thrown exception (including AssertionError) propagates.
     */
    public void runSyncClosure(Closure<?> body) {
        try {
            body.call();
        } catch (Throwable t) {
            rethrow(t);
        } finally {
            cancelTimers();
        }
    }

    /**
     * Run an async test body and block until {@code done} is signalled or the timeout
     * elapses. The source must evaluate to a closure. If it declares one parameter it is
     * passed {@code done}; if it declares none, it is run and treated as already complete.
     *
     * @throws AssertionError if {@code done} is never called within the timeout, or if an
     *                        assertion routed through {@code done}/{@code check} failed.
     */
    public void runAsync(String closureSource, long timeoutMs) {
        Object callable;
        try {
            callable = engine.eval(closureSource);
        } catch (Throwable t) {
            // Synchronous failure before any closure could be invoked.
            cancelTimers();
            rethrow(t);
            return;
        }
        runAsyncClosure(callable, timeoutMs);
    }

    /**
     * Run an async test body that is already a {@link Closure} (or callable), blocking until
     * {@code done} is signalled or {@code timeoutMs} elapses. Same contract as
     * {@link #runAsync(String, long)}, but for a closure registered by a {@code *_test.groovy}
     * file rather than an eval'd string.
     */
    public void runAsyncClosure(Object callable, long timeoutMs) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicBoolean completed = new AtomicBoolean(false);

        // done() | done { assertions } | done(throwable)
        Closure done = new Closure(null) {
            public Object doCall(Object... args) {
                if (!completed.compareAndSet(false, true)) {
                    return null; // idempotent: first signal wins
                }
                try {
                    if (args != null && args.length > 0 && args[0] != null) {
                        Object first = args[0];
                        if (first instanceof Closure) {
                            ((Closure) first).call();          // run assertions on this thread, captured below
                        } else if (first instanceof Throwable) {
                            error.set((Throwable) first);
                        }
                    }
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    latch.countDown();
                }
                return null;
            }
        };

        // check { assert ... } -- assert without completing; a failure fails the test immediately.
        Closure check = new Closure(null) {
            public Object doCall(Object... args) {
                try {
                    if (args != null && args.length > 0 && args[0] instanceof Closure) {
                        ((Closure) args[0]).call();
                    }
                } catch (Throwable t) {
                    if (completed.compareAndSet(false, true)) {
                        error.set(t);
                        latch.countDown();
                    }
                }
                return null;
            }
        };

        Bindings b = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        b.put("done", done);
        b.put("check", check);

        try {
            invoke(callable, done);
        } catch (Throwable t) {
            // Synchronous failure before any async callback registered.
            cancelTimers();
            rethrow(t);
            return;
        }

        boolean signalled;
        try {
            signalled = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            cancelTimers();
            throw new RuntimeException("Interrupted while awaiting done()", ie);
        }
        cancelTimers();

        if (!signalled) {
            throw new AssertionError("Async test did not call done() within " + timeoutMs + "ms");
        }
        Throwable e = error.get();
        if (e != null) {
            rethrow(e);
        }
    }

    /** Cancel any timers the test scheduled, so their threads don't outlive the test. */
    public void cancelTimers() {
        try {
            engine.eval("timers.cancel()");
        } catch (Exception ignored) {
            // engine may already be unusable; nothing to clean up
        }
    }

    /** Release resources. Safe to call from an @After method. */
    public void close() {
        cancelTimers();
    }

    // --- *_test.groovy registration support ---

    /**
     * Bind a fresh {@link GroovyTestRegistry} as {@code test} and evaluate the given
     * {@code *_test.groovy} file (resolved from {@link #groovyDir()}) into this engine, so its
     * {@code test(...)} / {@code test.async(...)} calls register their cases. Returns the
     * registry holding what the file registered. The file is named for error attribution, the
     * same way production scripts are.
     */
    public GroovyTestRegistry loadTestFile(String fileName) throws Exception {
        GroovyTestRegistry registry = new GroovyTestRegistry();
        put("test", registry);
        String source = FileUtils.readFileToString(new File(groovyDir(), fileName), "UTF-8") + ";null;";
        ScriptLoader.evalNamed(engine, fileName, source);
        return registry;
    }

    /** Run one registered case: synchronous bodies via {@link #runSyncClosure}, async via {@link #runAsyncClosure}. */
    public void runCase(GroovyTestRegistry.Case c) {
        if (c.async) {
            runAsyncClosure(c.body, c.timeoutMs > 0 ? c.timeoutMs : DEFAULT_ASYNC_TIMEOUT_MS);
        } else {
            runSyncClosure(c.body);
        }
    }

    /** Default await for {@code test.async("name") { done -> ... }} when no explicit timeout is given. */
    public static final long DEFAULT_ASYNC_TIMEOUT_MS = 2000L;

    // --- internals ---

    private void invoke(Object callable, Closure done) {
        if (callable instanceof Closure) {
            Closure c = (Closure) callable;
            if (c.getMaximumNumberOfParameters() == 0) {
                c.call();
                done.call();            // explicit 0-arg closure: synchronous, auto-complete
            } else {
                c.call(done);
            }
        } else if (callable != null) {
            InvokerHelper.invokeMethod(callable, "call", new Object[]{ done });
        } else {
            throw new IllegalArgumentException("runAsync body must evaluate to a closure or callable");
        }
    }

    private static void rethrow(Throwable t) {
        Throwable u = unwrap(t);
        if (u instanceof Error) throw (Error) u;          // AssertionError / PowerAssertionError land here
        if (u instanceof RuntimeException) throw (RuntimeException) u;
        throw new RuntimeException("Async test failed", u);
    }

    /** Groovy wraps closure-body exceptions in InvokerInvocationException; peel that off. */
    private static Throwable unwrap(Throwable t) {
        Throwable cur = t;
        while (cur instanceof InvokerInvocationException && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur;
    }

    /** Eval-ready source for a script file (raw contents + ";null;"), cached across harnesses. */
    private String loadSource(File groovyDir, String name) throws Exception {
        String cached = SOURCE_CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        String source = FileUtils.readFileToString(new File(groovyDir, name), "UTF-8") + ";null;";
        SOURCE_CACHE.put(name, source);
        return source;
    }

    /** Locate the groovy script directory: relative to the test cwd (project root), else via Play. */
    public static File groovyDir() {
        File local = new File("groovy");                   // sbt runs tests with cwd = project root
        if (local.isDirectory()) return local;
        try {
            if (play.Play.application() != null) {
                File viaPlay = new File(play.Play.application().path().toString(), "groovy");
                if (viaPlay.isDirectory()) return viaPlay;
            }
        } catch (Throwable ignored) {
            // Play not running -- fall through to the error below
        }
        throw new IllegalStateException(
            "Cannot find groovy script directory (cwd=" + new File(".").getAbsolutePath() + ")");
    }

    /** A GameListener that records finish() calls. With no ExperimentInstance, nothing persists. */
    public static class RecordingGameListener extends GameListener {
        public final AtomicInteger finishCount = new AtomicInteger(0);

        @Override
        public void finish() {
            finishCount.incrementAndGet();
            super.finish(); // safe: experimentInstance == null and user == null
        }

        public boolean finished() {
            return finishCount.get() > 0;
        }
    }
}
