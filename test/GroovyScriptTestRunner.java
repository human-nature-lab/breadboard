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
 *       {@link ScriptTestHarness#groovyDir()} is loaded once into a throwaway engine to read back
 *       the names of the cases it registers (see {@link GroovyTestRegistry}). One {@link TestRef}
 *       is emitted per case. A file that fails to load surfaces as a single failing case rather
 *       than vanishing silently.</li>
 *   <li><b>Execution</b> ({@link #runChild}): each case runs in its <b>own fresh engine</b> — the
 *       file is re-evaluated and only the matching case (by index, so duplicate names are
 *       unambiguous) is run. This gives the same per-test isolation the hand-written
 *       {@code ScriptHarnessTest} gets from a fresh harness per {@code @Test}.</li>
 * </ul>
 *
 * Re-evaluating the (small) test file per case costs a Groovy compile each time; the core scripts'
 * text is already cached by the harness, so this is cheap relative to the isolation it buys.
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

    public GroovyScriptTestRunner(Class<?> markerClass) throws InitializationError {
        super(markerClass);
    }

    @Override
    protected List<TestRef> getChildren() {
        List<TestRef> refs = new ArrayList<TestRef>();
        File dir = ScriptTestHarness.groovyDir();
        String[] names = dir.list();
        if (names == null) return refs;
        Arrays.sort(names); // stable, alphabetical discovery order

        for (String file : names) {
            if (!file.toLowerCase().endsWith(ScriptLoader.TEST_SUFFIX)) continue;
            ScriptTestHarness harness = new ScriptTestHarness();
            try {
                List<GroovyTestRegistry.Case> cases = harness.loadTestFile(file).cases();
                for (int i = 0; i < cases.size(); i++) {
                    refs.add(new TestRef(file, i, cases.get(i).name, cases.get(i).skip));
                }
            } catch (Throwable t) {
                // Couldn't even load the file -- represent it as one failing case so it's loud.
                refs.add(new TestRef(file, -1, "failed to load: " + t.getMessage(), false));
            } finally {
                harness.close();
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
        ScriptTestHarness harness = new ScriptTestHarness(); // fresh engine per case
        try {
            GroovyTestRegistry.Case c = harness.loadTestFile(ref.file).cases().get(ref.index);
            harness.runCase(c);
        } catch (Throwable t) {
            notifier.fireTestFailure(new Failure(desc, t));
        } finally {
            harness.close();
            notifier.fireTestFinished(desc);
        }
    }
}
