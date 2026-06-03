import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Exercises the pure DSL helper LOGIC in the groovy scripts (not just that they load):
 * util.groovy's {@code currency}, {@code randomString}, {@code randomSubset}, and
 * form.groovy's {@code mapValueLabel} (via ChoiceQuestion) and {@code fetchContent}.
 *
 * These run on the standalone {@link ScriptTestHarness} -- no DB, no Play app. Groovy-side
 * assertions are run inside {@code harness.runSync(...)} so a failed {@code assert}
 * surfaces as an AssertionError on the test thread.
 */
public class GroovyHelperTest {

    private ScriptTestHarness harness;

    @Before
    public void setUp() {
        harness = new ScriptTestHarness();
    }

    @After
    public void tearDown() {
        if (harness != null) harness.close();
    }

    // === currency (DecimalFormat '$0.00' with RoundingMode.UP) ===

    @Test
    public void currencyRoundsUpToTheNearestCent() throws Exception {
        // 0.001 must round UP to a full cent, not down to $0.00.
        assertEquals("$0.01", harness.eval("currency.format(0.001)").toString());
    }

    @Test
    public void currencyFormatsWholeAndFractionalValues() throws Exception {
        assertEquals("$1.00", harness.eval("currency.format(1)").toString());
        assertEquals("$2.50", harness.eval("currency.format(2.5)").toString());
        assertEquals("$0.00", harness.eval("currency.format(0)").toString());
    }

    // === randomString(len) ===

    @Test
    public void randomStringHasRequestedLengthAndAlphabet() throws Exception {
        Object s = harness.eval("randomString(8)");
        assertNotNull(s);
        assertEquals("randomString(8) should be 8 chars", 8, s.toString().length());
        assertTrue("randomString should be upper-case alphanumeric",
            s.toString().matches("[A-Z0-9]+"));
    }

    // === randomSubset(list, n, Random) -- the seed-injectable, deterministic variant ===

    @Test
    public void randomSubsetIsDeterministicForAGivenSeed() {
        harness.runSync(
            "{ ->\n" +
            "  def a = randomSubset([1,2,3,4,5], 3, new Random(1))\n" +
            "  def b = randomSubset([1,2,3,4,5], 3, new Random(1))\n" +
            "  assert a.size() == 3\n" +
            "  assert a == b                              // same seed -> same result\n" +
            "  assert a.every { [1,2,3,4,5].contains(it) } // only original elements\n" +
            "  assert a.unique().size() == 3              // no duplicates\n" +
            "}");
    }

    // === FormBase.mapValueLabel, exercised through ChoiceQuestion construction ===

    @Test
    public void mapValueLabelNormalizesChoiceShapes() {
        harness.runSync(
            "{ ->\n" +
            "  def q = new ChoiceQuestion([name: 'q1',\n" +
            "      choices: ['Dog', [value: '2', content: 'Two'], [value: '3']]])\n" +
            "  assert q.choices.size() == 3\n" +
            // bare value -> {value, content} both set to the value
            "  assert q.choices[0].value == 'Dog' && q.choices[0].content == 'Dog'\n" +
            // map with explicit content -> passed through unchanged
            "  assert q.choices[1].value == '2' && q.choices[1].content == 'Two'\n" +
            // map without content -> content defaults to value
            "  assert q.choices[2].value == '3' && q.choices[2].content == '3'\n" +
            "}");
    }

    // === BreadboardBase.fetchContent ===

    @Test
    public void fetchContentReturnsProvidedContent() throws Exception {
        // When 'content' is supplied directly, no ContentFetcher ('c') is needed.
        assertEquals("hello", harness.eval("new BreadboardBase().fetchContent([content: 'hello'])"));
    }

    @Test
    public void fetchContentThrowsWhenNeitherContentNorKeyProvided() {
        harness.runSync(
            "{ ->\n" +
            "  def threw = false\n" +
            "  try { new BreadboardBase().fetchContent([:]) } catch (Exception e) { threw = true }\n" +
            "  assert threw : 'fetchContent must reject opts with no content/contentKey'\n" +
            "}");
    }
}
