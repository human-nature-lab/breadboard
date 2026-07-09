import com.avaje.ebean.Ebean;
import com.avaje.ebean.SqlRow;
import models.*;
import org.junit.After;
import org.junit.BeforeClass;
import org.mindrot.jbcrypt.BCrypt;
import play.test.FakeApplication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static play.test.Helpers.*;

/**
 * Base test class providing a FakeApplication backed by an in-memory H2 database.
 *
 * <h3>One application for the whole suite</h3>
 * Starting a Play {@code FakeApplication} (boot the runtime, init Ebean, apply all
 * 30 evolutions) costs seconds; doing it per {@code @Test} method multiplied that
 * across ~100 tests. Instead we start <b>one</b> application lazily (the first
 * subclass {@code @BeforeClass} wins; the rest short-circuit) and never stop it —
 * the JVM reclaims it at exit.
 *
 * <h3>Isolation by truncation, not rollback</h3>
 * Per-test isolation is achieved by truncating every table in {@code @After}.
 * We deliberately do <i>not</i> use a test-thread transaction + rollback: the
 * controller tests drive real requests through {@code route()}/{@code callAction()},
 * whose actions commit in their own transaction. A test-thread rollback would not
 * undo those committed rows, so they would leak between tests. Truncation removes
 * committed rows regardless of which thread/transaction wrote them.
 *
 * <p>Because the DB is shared, test classes must not run in parallel — this is
 * enforced by {@code parallelExecution in Test := false} in {@code project/Build.scala}.
 */
public class BaseTest {

    protected static FakeApplication app;

    @BeforeClass
    public static void startApp() {
        if (app != null) {
            return; // already started by an earlier test class in this JVM
        }
        Map<String, String> config = new HashMap<String, String>();
        config.put("db.default.driver", "org.h2.Driver");
        // A single, stable in-memory DB shared by the whole suite. DB_CLOSE_DELAY=-1
        // keeps it alive even when no connection is open.
        config.put("db.default.url", "jdbc:h2:mem:breadboard-test;MODE=MYSQL;DB_CLOSE_DELAY=-1");
        config.put("ebean.default", "models.*");
        config.put("application.secret", "test-secret-key-for-testing-only");
        config.put("breadboard.clientUpdateRate", "0");
        config.put("breadboard.rootUrl", "http://localhost:9000");
        config.put("breadboard.wsUrl", "ws://localhost:9000/connect");
        // Auto-apply all evolutions (1.sql through 30.sql) to create the full schema.
        // Evolutions now describe the schema completely (file_mode lives in 30.sql,
        // experiment_instances.version in 5.sql), so no manual schema patches are needed.
        config.put("applyEvolutions.default", "true");
        // Pin the framework-default Global: the app's Global has no startup behaviour to
        // exercise here, since schema management is owned entirely by evolutions.
        config.put("application.global", "play.GlobalSettings");
        app = fakeApplication(config);
        start(app);
    }

    /**
     * Wipe every table after each test so the next test sees a clean database.
     * Foreign keys are disabled for the duration so truncate order doesn't matter.
     * The {@code play_evolutions} bookkeeping table is preserved (the schema must
     * survive; we never re-apply evolutions).
     */
    @After
    public void cleanDatabase() {
        Ebean.createSqlUpdate("SET REFERENTIAL_INTEGRITY FALSE").execute();
        List<SqlRow> tables = Ebean.createSqlQuery(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC'").findList();
        for (SqlRow row : tables) {
            // Read the single selected column positionally so we don't depend on how
            // SqlRow cases the column key ("TABLE_NAME" vs "table_name").
            if (row.values().isEmpty()) {
                continue;
            }
            String table = String.valueOf(row.values().iterator().next());
            if ("play_evolutions".equalsIgnoreCase(table)) {
                continue;
            }
            Ebean.createSqlUpdate("TRUNCATE TABLE " + table).execute();
        }
        Ebean.createSqlUpdate("SET REFERENTIAL_INTEGRITY TRUE").execute();

        // Raw-SQL truncation removes rows but does NOT invalidate Ebean's bean/query caches.
        // Clear them too, otherwise a later test can read a stale cached bean for a row that
        // was just truncated -- making behaviour order-dependent.
        Ebean.getServer("default").getServerCacheManager().clearAll();
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
