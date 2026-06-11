import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the tests a {@code *_test.groovy} file registers, so the Java side can discover and
 * run them as first-class JUnit cases. An instance is bound into the script engine under the
 * name {@code test}.
 *
 * <p>It <b>extends {@link Closure}</b> on purpose: in the gremlin-groovy engine a bare call like
 * {@code test("name") { ... }} only resolves to a bound variable when that variable is a Closure
 * (this is the same mechanism {@code events.groovy} relies on for {@code log = { ... }; log(...)}).
 * A plain Java object bound as {@code test} would throw {@code MissingMethodException}. So the
 * synchronous form is the closure's own call ({@link #doCall}), and the async/skip variants are
 * methods reached as {@code test.async(...)} / {@code test.skip(...)}:
 *
 * <pre>{@code
 * // groovy/groups_test.groovy
 * test("done fires when the queue empties") {        // -> doCall(name, body): synchronous
 *   def p = g.addPlayer('p1'); assert p != null
 * }
 *
 * test.async("resolves on a timer", 4000) { done ->  // -> async(String, long, Closure)
 *   timers.newTimer().runAfter(50) { done() }
 * }
 *
 * test.skip("not ready yet") { ... }                 // -> skip(String, Closure): reported, not run
 * }</pre>
 *
 * Synchronous vs async is chosen by which form is called, NOT by closure arity: a bare
 * {@code { ... }} closure carries an implicit {@code it} parameter, so arity-sniffing would
 * misclassify it. {@link ScriptTestHarness#runCase} dispatches on {@link Case#async}.
 *
 * <p>A fresh registry is created per engine ({@link ScriptTestHarness#loadTestFile}), so it
 * starts empty and never leaks cases between files or between runs of the same case.
 */
public class GroovyTestRegistry extends Closure<Object> {

    /** A single registered test: its name, the closure body, and how to run it. */
    public static final class Case {
        public final String name;
        public final Closure<?> body;
        public final long timeoutMs;   // async only; 0 => harness default
        public final boolean async;
        public final boolean skip;

        Case(String name, Closure<?> body, long timeoutMs, boolean async, boolean skip) {
            this.name = name;
            this.body = body;
            this.timeoutMs = timeoutMs;
            this.async = async;
            this.skip = skip;
        }
    }

    private final List<Case> cases = new ArrayList<Case>();

    public GroovyTestRegistry() {
        super(null); // no owner/delegate needed; we only use it as a callable
    }

    /** {@code test("name") { ... }} — a synchronous test. The closure's implicit {@code it} is unused. */
    public Object doCall(Object... args) {
        String name = (String) args[0];
        Closure<?> body = (Closure<?>) args[1];
        cases.add(new Case(name, body, 0L, false, false));
        return null;
    }

    /** {@code test.async("name") { done -> ... }} — async with the harness default timeout. */
    public void async(String name, Closure<?> body) {
        cases.add(new Case(name, body, 0L, true, false));
    }

    /** {@code test.async("name", 5000) { done -> ... }} — async with an explicit timeout (ms). */
    public void async(String name, long timeoutMs, Closure<?> body) {
        cases.add(new Case(name, body, timeoutMs, true, false));
    }

    /** {@code test.skip("name") { ... }} — registered but reported as ignored, never executed. */
    public void skip(String name, Closure<?> body) {
        cases.add(new Case(name, body, 0L, false, true));
    }

    /** The registered cases, in file order. */
    public List<Case> cases() {
        return cases;
    }
}
