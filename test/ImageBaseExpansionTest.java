import models.Experiment;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link Experiment#expandImageBase(String)}, the runtime substitution that keeps image
 * links stable across export/import. Authored markup stores the {{imageBase}} placeholder; the
 * server rewrites it to /images/&lt;currentExperimentId&gt; only when serving to a participant, so a
 * re-imported experiment (which gets a fresh id) still resolves its images.
 *
 * <p>The method is pure string logic keyed on {@code experiment.id}, so these set the id directly on
 * an unsaved bean rather than round-tripping through the database. We still extend {@link BaseTest}
 * so the Play/Ebean runtime is initialized before an Experiment is constructed.
 */
public class ImageBaseExpansionTest extends BaseTest {

    private static Experiment experimentWithId(Long id) {
        Experiment exp = new Experiment();
        exp.id = id;
        return exp;
    }

    @Test
    public void expandsTokenToImagesPrefix() {
        Experiment exp = experimentWithId(412L);
        assertEquals("/images/412/photo.png",
                exp.expandImageBase("{{imageBase}}/photo.png"));
    }

    @Test
    public void expandsEveryOccurrence() {
        Experiment exp = experimentWithId(412L);
        assertEquals("<img src=\"/images/412/a.png\"><img src=\"/images/412/b.png\">",
                exp.expandImageBase("<img src=\"{{imageBase}}/a.png\"><img src=\"{{imageBase}}/b.png\">"));
    }

    @Test
    public void toleratesWhitespaceInsideBraces() {
        Experiment exp = experimentWithId(412L);
        assertEquals("/images/412/bg.png",
                exp.expandImageBase("{{ imageBase }}/bg.png"));
    }

    @Test
    public void worksInCssUrl() {
        Experiment exp = experimentWithId(7L);
        assertEquals("background-image: url(/images/7/bg.png);",
                exp.expandImageBase("background-image: url({{imageBase}}/bg.png);"));
    }

    @Test
    public void leavesOtherPlaceholdersUntouched() {
        // Only the imageBase token is rewritten; unrelated Angular/mustache expressions survive.
        Experiment exp = experimentWithId(412L);
        assertEquals("<img src=\"/images/412/{{image.fileName}}\">",
                exp.expandImageBase("<img src=\"{{imageBase}}/{{image.fileName}}\">"));
    }

    @Test
    public void returnsInputWhenNoToken() {
        Experiment exp = experimentWithId(412L);
        String html = "<p>No images here.</p>";
        assertEquals(html, exp.expandImageBase(html));
    }

    @Test
    public void leavesTokenUnexpandedWhenIdIsNull() {
        // An unsaved experiment has no id yet; better to leave the token than emit "/images/null".
        Experiment exp = experimentWithId(null);
        assertEquals("{{imageBase}}/photo.png",
                exp.expandImageBase("{{imageBase}}/photo.png"));
    }

    @Test
    public void returnsNullForNullInput() {
        Experiment exp = experimentWithId(412L);
        assertNull(exp.expandImageBase(null));
    }
}
