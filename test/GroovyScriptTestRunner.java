import models.ScriptLoader;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A JUnit 4 runner that turns every test registered by a {@code groovy/*_test.groovy} file into a
 * first-class JUnit case. Put it on an empty marker class:
 *
 * <pre>{@code
 * @RunWith(GroovyScriptTestRunner.class)
 * public class GroovyScriptTests {}
 * }</pre>
 *
 * <h3>How it works</h3>
 * <ul>
 *   <li><b>Discovery</b> ({@link #getChildren}): every {@code *_test.groovy} file in
 *       {@link ScriptTestHarness#groovyDir()} is loaded to read back the names of the cases it
 *       registers (see {@link GroovyTestRegistry}). One {@link TestRef} is emitted per case. A file
 *       that fails to load surfaces as a single failing case rather than vanishing silently.</li>
 *   <li><b>Execution</b> ({@link #runChild}): the file is re-evaluated and only the matching case
 *       (by index, so duplicate names are unambiguous) is run.</li>
 * </ul>
 *
 * <h3>One engine, reset per case</h3>
 * Both phases share a <b>single</b> {@link ScriptTestHarness} (and thus one gremlin-groovy engine).
 * Building the engine and compiling the ~7.6k lines of core Groovy costs ~8s; doing that per case
 * dominated the run (~100 cases ≈ ~14 min of pure compilation). Instead we build it once and call
 * {@link ScriptTestHarness#reset()} before each case — Groovy caches each engine's compiled script
 * classes by source text, so the re-evaluation that restores fresh per-case bindings is ~free. This
 * preserves the per-case isolation a fresh engine gave (fresh {@code g}/{@code a}/bindings, cleared
 * statics) because cases run sequentially ({@code parallelExecution in Test := false}).
 */
public class GroovyScriptTestRunner extends ParentRunner<GroovyScriptTestRunner.TestRef> {

    /** A pointer to one registered case: which file, its position, its name, and whether it's skipped. */
    static final class TestRef {
        final String file;
        final int index;        // position in the file's registry; -1 means "the file failed to load"
        final String name;
        final boolean skip;

        TestRef(String file, int index, String name, boolean skip) {
            this.file = file;
            this.index = index;
            this.name = name;
            this.skip = skip;
        }
    }

    /**
     * One engine for the whole run, reused across every case. Building it (and compiling the ~7.6k
     * lines of core Groovy) costs ~8s; {@link ScriptTestHarness#reset()} returns it to a clean
     * per-case state via cached re-evals (~0ms). Reusing it instead of building one per case is what
     * turns a ~20-min run into a ~1-min one. Safe because cases run sequentially.
     */
    private ScriptTestHarness harness;

    public GroovyScriptTestRunner(Class<?> markerClass) throws InitializationError {
        super(markerClass);
    }

    /** Lazily build the single shared harness (first call pays the one-time core-script compile). */
    private ScriptTestHarness harness() {
        if (harness == null) {
            harness = new ScriptTestHarness();
        }
        return harness;
    }

    @Override
    public void run(RunNotifier notifier) {
        try {
            super.run(notifier);
        } finally {
            if (harness != null) {
                harness.close();
                harness = null;
            }
        }
    }

    @Override
    protected List<TestRef> getChildren() {
        List<TestRef> refs = new ArrayList<TestRef>();
        File dir = ScriptTestHarness.groovyDir();
        String[] names = dir.list();
        if (names == null) return refs;
        Arrays.sort(names); // stable, alphabetical discovery order

        ScriptTestHarness h = harness(); // build/compile once; reset() per file is a free re-eval
        for (String file : names) {
            if (!file.toLowerCase().endsWith(ScriptLoader.TEST_SUFFIX)) continue;
            try {
                h.reset();
                List<GroovyTestRegistry.Case> cases = h.loadTestFile(file).cases();
                for (int i = 0; i < cases.size(); i++) {
                    refs.add(new TestRef(file, i, cases.get(i).name, cases.get(i).skip));
                }
            } catch (Throwable t) {
                // Couldn't even load the file -- represent it as one failing case so it's loud.
                refs.add(new TestRef(file, -1, "failed to load: " + t.getMessage(), false));
            }
        }
        return refs;
    }

    @Override
    protected Description describeChild(TestRef ref) {
        // className=file, methodName=case name -> reads e.g. "groups_test.groovy: done fires ..."
        return Description.createTestDescription(ref.file, ref.name);
    }

    @Override
    protected void runChild(TestRef ref, RunNotifier notifier) {
        Description desc = describeChild(ref);

        if (ref.index < 0) { // file-load failure captured during discovery
            notifier.fireTestStarted(desc);
            notifier.fireTestFailure(new Failure(desc, new IllegalStateException(ref.name)));
            notifier.fireTestFinished(desc);
            return;
        }
        if (ref.skip) {
            notifier.fireTestIgnored(desc);
            return;
        }

        notifier.fireTestStarted(desc);
        ScriptTestHarness h = harness();
        try {
            h.reset(); // pristine per-case state on the shared engine (cached re-eval, ~0ms)
            GroovyTestRegistry.Case c = h.loadTestFile(ref.file).cases().get(ref.index);
            h.runCase(c);
        } catch (Throwable t) {
            notifier.fireTestFailure(new Failure(desc, t));
        } finally {
            h.cancelTimers(); // don't close: the harness is reused by the next case (run() closes it)
            notifier.fireTestFinished(desc);
        }
    }
}
