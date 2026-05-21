import com.avaje.ebean.Ebean;
import models.*;
import org.mindrot.jbcrypt.BCrypt;
import play.test.FakeApplication;
import play.test.WithApplication;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static play.test.Helpers.*;

/**
 * Base test class that provides a FakeApplication backed by an in-memory H2 database.
 * All test classes should extend this to get a clean database per test run.
 */
public class BaseTest extends WithApplication {

    @org.junit.Before
    public void startPlay() {
        Map<String, String> config = new HashMap<String, String>();
        config.put("db.default.driver", "org.h2.Driver");
        config.put("db.default.url", "jdbc:h2:mem:test-" + UUID.randomUUID().toString().substring(0, 8) + ";MODE=MYSQL");
        config.put("ebean.default", "models.*");
        config.put("application.secret", "test-secret-key-for-testing-only");
        config.put("breadboard.clientUpdateRate", "0");
        config.put("breadboard.rootUrl", "http://localhost:9000");
        config.put("breadboard.wsUrl", "ws://localhost:9000/connect");
        // Auto-apply all evolutions (1.sql through 29.sql) to create the base schema.
        // Disable the custom Global class since the evolutions create the
        // breadboard_version table empty, causing Global to skip the v2.4 upgrade.
        config.put("applyEvolutions.default", "true");
        config.put("application.global", "play.GlobalSettings");
        app = fakeApplication(config);
        start(app);

        // Apply schema patches that Global.version2Point4Upgrade() would add
        // but which are not covered by evolution files:
        Ebean.createSqlUpdate("alter table experiments add column if not exists file_mode bit default 0;").execute();
        // Add @Version column to experiment_instances if not present
        Ebean.createSqlUpdate("alter table experiment_instances add column if not exists version integer default 0;").execute();
    }

    /**
     * Creates and saves a Language entity for use in tests.
     */
    protected Language createLanguage(String code, String name) {
        Language lang = new Language();
        lang.code = code;
        lang.name = name;
        lang.save();
        return lang;
    }

    /**
     * Creates and saves a User entity with a hashed password.
     */
    protected User createUser(String email, String name, String rawPassword, Language defaultLanguage) {
        User user = new User();
        user.email = email;
        user.name = name;
        user.password = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
        user.uid = UUID.randomUUID().toString();
        user.role = "admin";
        user.currentScript = "";
        user.defaultLanguage = defaultLanguage;
        user.experimentInstanceId = -1L;
        user.save();
        return user;
    }

    /**
     * Creates and saves an Experiment entity.
     */
    protected Experiment createExperiment(String name) {
        Experiment experiment = new Experiment();
        experiment.name = name;
        experiment.uid = UUID.randomUUID().toString();
        experiment.fileMode = false;
        experiment.save();
        return experiment;
    }

    /**
     * Creates and saves an ExperimentInstance associated with an Experiment.
     */
    protected ExperimentInstance createInstance(String name, Experiment experiment) {
        ExperimentInstance instance = new ExperimentInstance(name, experiment);
        instance.save();
        return instance;
    }

    /**
     * Creates a Step and saves it via the parent Experiment (cascade).
     * Ebean requires unidirectional @OneToMany children to be saved through the parent.
     */
    protected Step createStep(Experiment experiment, String name, String source) {
        Step step = new Step();
        step.name = name;
        step.source = source;
        experiment.getSteps().add(step);
        experiment.save();
        return step;
    }

    /**
     * Creates a Content and saves it via the parent Experiment (cascade).
     */
    protected Content createContent(Experiment experiment, String name) {
        Content content = new Content();
        content.name = name;
        experiment.content.add(content);
        experiment.save();
        return content;
    }

    /**
     * Creates a Parameter and saves it via the parent Experiment (cascade).
     */
    protected Parameter createParameter(Experiment experiment, String name, String type, String defaultVal) {
        Parameter param = new Parameter();
        param.name = name;
        param.type = type;
        param.defaultVal = defaultVal;
        experiment.parameters.add(param);
        experiment.save();
        return param;
    }
}
