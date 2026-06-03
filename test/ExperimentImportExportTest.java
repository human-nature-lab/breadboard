import controllers.ExperimentController;
import models.*;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Round-trips a fully-populated experiment through the directory exporter/importer
 * (ExperimentController.exportExperimentToDirectory -> importExperimentFromDirectory)
 * and asserts the model survives intact. This exercises a lot of real format logic and
 * would catch export/import regressions.
 *
 * It uses the directory (not zip/multipart) path so no HTTP request plumbing is needed,
 * and imports OVER a pre-existing empty target experiment (the simpler of the importer's
 * two branches).
 */
public class ExperimentImportExportTest extends BaseTest {

    @Test
    public void exportThenImportPreservesExperiment() throws Exception {
        Language eng = createLanguage("eng", "English");
        User user = createUser("io@test.com", "IO User", "pass", eng);

        // --- Build a fully-populated source experiment ---
        Experiment source = createExperiment("Source");
        source.setStyle("body { color: red; }");
        source.setClientHtml("<html><body>hi</body></html>");
        source.setClientGraph("// client graph js");
        source.save();

        createStep(source, "initStep", "println('init')");
        createStep(source, "onJoin", "// join logic");

        Parameter p = new Parameter();
        p.name = "numPlayers";
        p.type = "Integer";
        p.minVal = "2";
        p.maxVal = "10";
        p.defaultVal = "4";
        p.description = "player count";
        source.parameters.add(p);
        source.save();

        Content content = createContent(source, "greeting");
        Translation t = new Translation();
        t.setHtml("<p>Hello</p>");
        t.language = eng;
        t.content = content;
        t.save();

        Image img = new Image();
        img.fileName = "pic.png";
        img.contentType = "image/png";
        img.file = new byte[]{1, 2, 3, 4, 5};
        source.images.add(img);
        source.save();

        File dir = Files.createTempDirectory("bb-export").toFile();
        try {
            // --- Export, then import into a fresh empty target experiment ---
            ExperimentController.exportExperimentToDirectory(source.id, dir);

            Experiment target = createExperiment("Target");
            ExperimentController.importExperimentFromDirectory(target.id, user, dir);

            // --- Assert the re-imported experiment matches the source ---
            Experiment imported = Experiment.findById(target.id);
            assertNotNull("Imported experiment should exist", imported);

            assertEquals("body { color: red; }", imported.getStyle());
            assertEquals("<html><body>hi</body></html>", imported.getClientHtml());
            assertEquals("// client graph js", imported.getClientGraph());

            // Steps (name -> source), order-independent
            Map<String, String> steps = new HashMap<String, String>();
            for (Step s : imported.getSteps()) {
                steps.put(s.name, s.source);
            }
            assertEquals("Both steps should round-trip", 2, steps.size());
            assertEquals("println('init')", steps.get("initStep"));
            assertEquals("// join logic", steps.get("onJoin"));

            // Parameter (all CSV columns)
            assertEquals(1, imported.parameters.size());
            Parameter ip = imported.parameters.get(0);
            assertEquals("numPlayers", ip.name);
            assertEquals("Integer", ip.type);
            assertEquals("2", ip.minVal);
            assertEquals("10", ip.maxVal);
            assertEquals("4", ip.defaultVal);
            assertEquals("player count", ip.description);

            // Content + translation (in the eng language sub-folder)
            List<Content> contents = imported.getContent();
            assertEquals(1, contents.size());
            Content ic = contents.get(0);
            assertEquals("greeting", ic.name);
            assertEquals(1, ic.translations.size());
            Translation it = ic.translations.get(0);
            assertEquals("<p>Hello</p>", it.getHtml());
            // The translation references a language; load it fully to read its code (direct
            // field access on a lazy Ebean reference returns null -- only the id is populated).
            assertNotNull("Translation should reference a language", it.language);
            Language importedLang = Language.findById(it.language.id);
            assertNotNull(importedLang);
            assertEquals("Language should round-trip as eng", "eng", importedLang.code);

            // Image (filename + bytes preserved)
            assertEquals(1, imported.images.size());
            Image ii = imported.images.get(0);
            assertEquals("pic.png", ii.fileName);
            assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, ii.file);
        } finally {
            // Best-effort temp cleanup -- never let it fail the test after the assertions pass.
            FileUtils.deleteQuietly(dir);
        }
    }
}
