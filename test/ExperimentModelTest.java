import models.*;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for Experiment and related models: Step, Content, Translation, Parameter, Language, Image.
 * Covers CRUD, finders, relationships, and cascade behavior.
 */
public class ExperimentModelTest extends BaseTest {

    // --- Experiment ---

    @Test
    public void createAndFindExperiment() {
        Experiment exp = createExperiment("TestExperiment");
        assertNotNull("Experiment should have an ID after save", exp.id);

        Experiment found = Experiment.findById(exp.id);
        assertNotNull("Should find experiment by ID", found);
        assertEquals("TestExperiment", found.name);
        assertNotNull("UID should be set", found.uid);
    }

    @Test
    public void findExperimentByName() {
        createExperiment("ByNameExp");
        Experiment found = Experiment.findByName("ByNameExp");
        assertNotNull("Should find experiment by name", found);
        assertEquals("ByNameExp", found.name);
    }

    @Test
    public void findExperimentByUid() {
        Experiment exp = createExperiment("UidExp");
        String uid = exp.uid;

        Experiment found = Experiment.findByUid(uid);
        assertNotNull("Should find experiment by UID", found);
        assertEquals("UidExp", found.name);
    }

    @Test
    public void findAllExperiments() {
        createExperiment("Exp1");
        createExperiment("Exp2");

        List<Experiment> all = Experiment.findAll();
        assertTrue("Should find at least 2 experiments", all.size() >= 2);
    }

    @Test
    public void experimentDefaultClientHtmlAndGraph() {
        String defaultHtml = Experiment.defaultClientHTML();
        assertNotNull("Default client HTML should not be null", defaultHtml);

        String defaultGraph = Experiment.defaultClientGraph();
        assertNotNull("Default client graph should not be null", defaultGraph);
    }

    @Test
    public void experimentFileModeDefault() {
        Experiment exp = createExperiment("FileModeExp");
        Experiment found = Experiment.findById(exp.id);
        // fileMode can be null or false by default
        assertTrue("fileMode should be null or false",
                found.fileMode == null || !found.fileMode);
    }

    // --- Step ---

    @Test
    public void createStepAndAssociateWithExperiment() {
        Experiment exp = createExperiment("StepExp");
        Step step = createStep(exp, "initStep", "println('hello')");

        assertNotNull("Step should have an ID", step.id);

        Experiment found = Experiment.findById(exp.id);
        assertNotNull(found);
        List<Step> steps = found.getSteps();
        assertEquals("Experiment should have 1 step", 1, steps.size());
        assertEquals("initStep", steps.get(0).name);
        assertEquals("println('hello')", steps.get(0).source);
    }

    @Test
    public void stepFinders() {
        Experiment exp = createExperiment("StepFinderExp");
        Step step = createStep(exp, "myStep", "// script");

        Step byId = Step.findById(step.id);
        assertNotNull("Should find step by ID", byId);
        assertEquals("myStep", byId.name);

        Step byName = Step.findByName("myStep");
        assertNotNull("Should find step by name", byName);
    }

    @Test
    public void experimentCascadesDeleteToSteps() {
        Experiment exp = createExperiment("CascadeExp");
        Step step = createStep(exp, "cascadeStep", "// to be deleted");
        Long stepId = step.id;

        exp.delete();

        Step orphan = Step.findById(stepId);
        assertNull("Step should be deleted when experiment is deleted", orphan);
    }

    // --- Content & Translation ---

    @Test
    public void createContentWithTranslation() {
        Experiment exp = createExperiment("ContentExp");
        Content content = createContent(exp, "greeting");
        Language lang = createLanguage("eng", "English");

        Translation t = new Translation();
        t.setHtml("<p>Hello</p>");
        t.language = lang;
        t.content = content;
        t.save();

        Content found = Content.findById(content.id);
        assertNotNull(found);
        assertEquals("greeting", found.name);
        assertNotNull("Content should have translations", found.translations);
        assertEquals(1, found.translations.size());
        assertEquals("<p>Hello</p>", found.translations.get(0).getHtml());
    }

    @Test
    public void contentFinders() {
        Experiment exp = createExperiment("ContentFinderExp");
        createContent(exp, "instructions");

        Content byName = Content.findByName("instructions");
        assertNotNull("Should find content by name", byName);
    }

    @Test
    public void translationFinders() {
        Language lang = createLanguage("deu", "German");
        Experiment exp = createExperiment("TransExp");
        Content content = createContent(exp, "message");

        Translation t = new Translation();
        t.setHtml("<p>Hallo</p>");
        t.language = lang;
        t.content = content;
        t.save();

        Translation found = Translation.findById(t.id);
        assertNotNull("Should find translation by ID", found);

        List<Translation> all = Translation.findAll();
        assertTrue("Should find at least 1 translation", all.size() >= 1);
    }

    // --- Parameter ---

    @Test
    public void createParameterAndAssociate() {
        Experiment exp = createExperiment("ParamExp");
        Parameter param = createParameter(exp, "numPlayers", "Integer", "4");

        assertNotNull("Parameter should have an ID", param.id);
        assertEquals("numPlayers", param.name);
        assertEquals("Integer", param.type);
        assertEquals("4", param.defaultVal);

        Experiment found = Experiment.findById(exp.id);
        assertEquals("Experiment should have 1 parameter", 1, found.parameters.size());
    }

    @Test
    public void parameterTypes() {
        Experiment exp = createExperiment("ParamTypeExp");
        createParameter(exp, "count", "Integer", "10");
        createParameter(exp, "rate", "Decimal", "0.5");
        createParameter(exp, "label", "Text", "default");
        createParameter(exp, "enabled", "Boolean", "true");

        Experiment found = Experiment.findById(exp.id);
        assertEquals("Should have 4 parameters", 4, found.parameters.size());
    }

    // --- Language ---

    @Test
    public void createAndFindLanguage() {
        Language lang = createLanguage("spa", "Spanish");
        assertNotNull("Language should have an ID", lang.id);

        Language found = Language.findById(lang.id);
        assertNotNull("Should find language by ID", found);
        assertEquals("spa", found.code);
        assertEquals("Spanish", found.name);
    }

    @Test
    public void languageFindAll() {
        createLanguage("eng", "English");
        createLanguage("fra", "French");

        List<Language> all = Language.findAll();
        assertTrue("Should find at least 2 languages", all.size() >= 2);
    }

    @Test
    public void experimentLanguagesManyToMany() {
        Experiment exp = createExperiment("LangExp");
        Language en = createLanguage("eng", "English");
        Language fr = createLanguage("fra", "French");

        exp.languages.add(en);
        exp.languages.add(fr);
        exp.saveManyToManyAssociations("languages");

        Experiment found = Experiment.findById(exp.id);
        assertEquals("Experiment should have 2 languages", 2, found.languages.size());
    }

    // --- Image ---

    @Test
    public void createAndFindImage() {
        Experiment exp = createExperiment("ImgCrudExp");

        Image img = new Image();
        img.fileName = "test.png";
        img.contentType = "image/png";
        img.file = new byte[]{0x01, 0x02, 0x03};
        img.thumbFile = new byte[]{0x04, 0x05};
        img.thumbFileName = "test_thumb.png";
        exp.images.add(img);
        exp.save();

        Experiment found = Experiment.findById(exp.id);
        assertEquals(1, found.images.size());
        Image foundImg = found.images.get(0);
        assertNotNull("Image should have an ID", foundImg.id);
        assertEquals("test.png", foundImg.fileName);
        assertEquals("image/png", foundImg.contentType);
    }

    @Test
    public void imageAssociatedWithExperiment() {
        Experiment exp = createExperiment("ImageExp");

        Image img = new Image();
        img.fileName = "graph.svg";
        img.contentType = "image/svg+xml";
        img.file = "<svg></svg>".getBytes();
        exp.images.add(img);
        exp.save();

        Experiment found = Experiment.findById(exp.id);
        assertEquals("Experiment should have 1 image", 1, found.images.size());
        assertEquals("graph.svg", found.images.get(0).fileName);
    }
}
