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

    /**
     * Imports an archive OVER an already-populated experiment and asserts the resources are synced:
     * matching resources are replaced, new ones added, and stale ones deleted -- while the
     * experiment's identity (id/uid) is preserved. This is the wipe-then-reimport contract the HTTP
     * "replace experiment" endpoint relies on (it shares Experiment.removeSteps()/removeContent()/
     * removeParameters()/removeImages() with this directory-based importer).
     */
    @Test
    public void importOverPopulatedExperimentSyncsResources() throws Exception {
        Language eng = createLanguage("eng", "English");
        User user = createUser("sync@test.com", "Sync User", "pass", eng);

        // --- Source experiment: the desired end state ---
        Experiment source = createExperiment("SyncSource");
        source.setStyle("body { color: blue; }");
        source.setClientHtml("<html>new</html>");
        source.setClientGraph("// new graph");
        source.save();
        createStep(source, "keepStep", "// new source for keepStep");
        createStep(source, "addedStep", "// only in source");
        createParameter(source, "rounds", "Integer", "3");
        Content sourceContent = createContent(source, "intro");
        Translation st = new Translation();
        st.setHtml("<p>new intro</p>");
        st.language = eng;
        st.content = sourceContent;
        st.save();
        Image si = new Image();
        si.fileName = "new.png";
        si.contentType = "image/png";
        si.file = new byte[]{9, 9, 9};
        source.images.add(si);
        source.save();

        // --- Target experiment: pre-populated with DIFFERENT, stale resources ---
        Experiment target = createExperiment("SyncTarget");
        target.setStyle("body { color: red; }");
        target.save();
        Long targetId = target.id;
        String targetUid = target.uid;
        createStep(target, "keepStep", "// OLD source for keepStep");
        createStep(target, "staleStep", "// should be deleted");
        createParameter(target, "staleParam", "Text", "");
        Content staleContent = createContent(target, "staleContent");
        Translation tt = new Translation();
        tt.setHtml("<p>stale</p>");
        tt.language = eng;
        tt.content = staleContent;
        tt.save();
        Image ti = new Image();
        ti.fileName = "stale.png";
        ti.contentType = "image/png";
        ti.file = new byte[]{1, 1, 1};
        target.images.add(ti);
        target.save();

        File dir = Files.createTempDirectory("bb-sync").toFile();
        try {
            ExperimentController.exportExperimentToDirectory(source.id, dir);
            // Import OVER the populated target.
            ExperimentController.importExperimentFromDirectory(targetId, user, dir);

            Experiment synced = Experiment.findById(targetId);
            assertNotNull("Synced experiment should exist", synced);

            // Identity is preserved (same row, not a new experiment).
            assertEquals("Experiment id should be preserved", targetId, synced.id);
            assertEquals("Experiment uid should be preserved", targetUid, synced.uid);

            // Scalars replaced.
            assertEquals("body { color: blue; }", synced.getStyle());
            assertEquals("<html>new</html>", synced.getClientHtml());
            assertEquals("// new graph", synced.getClientGraph());

            // Steps: keepStep replaced, addedStep added, staleStep deleted.
            Map<String, String> steps = new HashMap<String, String>();
            for (Step s : synced.getSteps()) {
                steps.put(s.name, s.source);
            }
            assertEquals("Only the source's steps should remain", 2, steps.size());
            assertEquals("keepStep should be replaced", "// new source for keepStep", steps.get("keepStep"));
            assertTrue("addedStep should be added", steps.containsKey("addedStep"));
            assertFalse("staleStep should be deleted", steps.containsKey("staleStep"));

            // Parameters: only the source's remain.
            assertEquals(1, synced.parameters.size());
            assertEquals("rounds", synced.parameters.get(0).name);

            // Content: only the source's remains.
            List<Content> contents = synced.getContent();
            assertEquals(1, contents.size());
            assertEquals("intro", contents.get(0).name);

            // Images: only the source's remains.
            assertEquals(1, synced.images.size());
            assertEquals("new.png", synced.images.get(0).fileName);
        } finally {
            FileUtils.deleteQuietly(dir);
        }
    }
}
