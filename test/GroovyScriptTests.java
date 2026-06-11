import org.junit.runner.RunWith;

/**
 * Entry point for the Groovy DSL tests. This class has no methods of its own: the
 * {@link GroovyScriptTestRunner} discovers every {@code groovy/*_test.groovy} file, and each
 * {@code test(...)} those files register becomes a JUnit case under this class.
 *
 * Run the whole set with {@code sbt "testOnly GroovyScriptTests"}.
 *
 * To add tests, drop a {@code *_test.groovy} file in {@code groovy/} (production's ScriptLoader
 * skips that suffix, so it never ships) and write {@code test("...") { ... }} — no Java required.
 */
@RunWith(GroovyScriptTestRunner.class)
public class GroovyScriptTests {
}
