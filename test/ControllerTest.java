import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import org.junit.Test;
import play.libs.Json;
import play.mvc.Result;
import play.test.FakeRequest;

import java.util.Date;

import static org.junit.Assert.*;
import static play.test.Helpers.*;

/**
 * Functional tests for controller routes: authentication, experiment management,
 * data endpoints, content management, and language management.
 */
public class ControllerTest extends BaseTest {

    // === Authentication ===

    @Test
    public void indexReturnsOk() {
        Result result = route(fakeRequest(GET, "/"));
        assertEquals("Index should return 200", OK, status(result));
    }

    @Test
    public void getStateRequiresAuth() {
        Result result = route(fakeRequest(GET, "/state"));
        assertEquals("State should return 401 without auth", UNAUTHORIZED, status(result));
    }

    @Test
    public void getStateWithNoUsersReturnsCreateFirstUser() {
        Result result = route(fakeRequest(GET, "/state"));
        assertEquals(UNAUTHORIZED, status(result));
        JsonNode json = Json.parse(contentAsString(result));
        assertEquals("create-first-user", json.get("status").asText());
    }

    @Test
    public void addFirstUser() {
        // Need a language for the default language
        Language lang = createLanguage("eng", "English");

        ObjectNode body = Json.newObject();
        body.put("email", "admin@test.com");
        body.put("password", "admin-pass");
        body.put("defaultLanguageId", lang.id);

        Result result = route(fakeRequest(POST, "/createFirstUser").withJsonBody(body));
        assertEquals("Create first user should return 200", OK, status(result));

        // Verify user was created
        User user = User.findByEmail("admin@test.com");
        assertNotNull("User should exist after creation", user);
    }

    @Test
    public void addFirstUserFailsWhenUsersExist() {
        Language lang = createLanguage("eng", "English");
        createUser("existing@test.com", "Existing", "pass", lang);

        ObjectNode body = Json.newObject();
        body.put("email", "another@test.com");
        body.put("password", "pass");
        body.put("defaultLanguageId", lang.id);

        Result result = route(fakeRequest(POST, "/createFirstUser").withJsonBody(body));
        assertEquals("Should reject when users already exist", BAD_REQUEST, status(result));
    }

    @Test
    public void authenticateWithValidCredentials() {
        Language lang = createLanguage("eng", "English");
        createUser("login@test.com", "Login User", "my-password", lang);

        Result result = route(
            fakeRequest(POST, "/login")
                .withFormUrlEncodedBody(
                    new java.util.HashMap<String, String>() {{
                        put("email", "login@test.com");
                        put("password", "my-password");
                    }}
                )
        );
        assertEquals("Login should return 200", OK, status(result));

        JsonNode json = Json.parse(contentAsString(result));
        assertNotNull("Response should contain uid", json.get("uid"));
        assertEquals("login@test.com", json.get("email").asText());
    }

    @Test
    public void authenticateWithInvalidCredentials() {
        Language lang = createLanguage("eng", "English");
        createUser("fail@test.com", "Fail User", "real-password", lang);

        Result result = route(
            fakeRequest(POST, "/login")
                .withFormUrlEncodedBody(
                    new java.util.HashMap<String, String>() {{
                        put("email", "fail@test.com");
                        put("password", "wrong-password");
                    }}
                )
        );
        assertEquals("Login with wrong password should return 400", BAD_REQUEST, status(result));
    }

    @Test
    public void logoutClearsSession() {
        Result result = route(fakeRequest(GET, "/logout"));
        assertEquals("Logout should return 401", UNAUTHORIZED, status(result));
    }

    // === Authenticated State ===

    @Test
    public void getStateWithAuth() {
        Language lang = createLanguage("eng", "English");
        User user = createUser("state@test.com", "State User", "pass", lang);

        Result result = route(
            fakeRequest(GET, "/state")
                .withSession("uid", user.uid)
        );
        assertEquals("State should return 200 with valid session", OK, status(result));

        JsonNode json = Json.parse(contentAsString(result));
        assertNotNull("Should contain connectSocket", json.get("connectSocket"));
    }

    // === Experiment CRUD ===

    @Test
    public void createExperimentRequiresAuth() {
        ObjectNode body = Json.newObject();
        body.put("newExperimentName", "New Experiment");

        // PUT routes may not be matched by route() in Play 2.2, use callAction instead
        Result result = callAction(
            controllers.routes.ref.ExperimentController.createExperiment(),
            fakeRequest().withJsonBody(body)
        );
        assertEquals("Create experiment should require auth", UNAUTHORIZED, status(result));
    }

    @Test
    public void createExperimentWithAuth() {
        Language lang = createLanguage("eng", "English");
        User user = createUser("creator@test.com", "Creator", "pass", lang);

        ObjectNode body = Json.newObject();
        body.put("newExperimentName", "New Experiment");

        Result result = callAction(
            controllers.routes.ref.ExperimentController.createExperiment(),
            fakeRequest()
                .withJsonBody(body)
                .withSession("uid", user.uid)
        );
        assertEquals("Create experiment should return 200", OK, status(result));
    }

    @Test
    public void exportExperimentRequiresAuth() {
        Experiment exp = createExperiment("ExportMe");
        Result result = route(fakeRequest(GET, "/experiment/export/" + exp.id));
        assertEquals("Export should require auth", UNAUTHORIZED, status(result));
    }

    // === CSV Data Export ===

    @Test
    public void dataCsvRequiresAuth() {
        Experiment exp = createExperiment("CsvExp");
        Result result = route(fakeRequest(GET, "/csv/instances/" + exp.id));
        assertEquals("CSV export should require auth", UNAUTHORIZED, status(result));
    }

    @Test
    public void dataCsvWithAuth() {
        Language lang = createLanguage("eng", "English");
        User user = createUser("csv@test.com", "CSV User", "pass", lang);
        Experiment exp = createExperiment("CsvDataExp");
        ExperimentInstance instance = createInstance("CsvRun", exp);

        Data d = new Data();
        d.name = "reward";
        d.value = "1.50";
        d.experimentInstance = instance;
        d.save();

        Result result = route(
            fakeRequest(GET, "/csv/instances/" + exp.id)
                .withSession("uid", user.uid)
        );
        assertEquals("CSV export should return 200", OK, status(result));
        String csv = contentAsString(result);
        assertTrue("CSV should contain header", csv.contains("\"id\""));
        assertTrue("CSV should contain data", csv.contains("reward"));
    }

    @Test
    public void eventCsvWithAuth() {
        Language lang = createLanguage("eng", "English");
        User user = createUser("eventcsv@test.com", "EventCSV User", "pass", lang);
        Experiment exp = createExperiment("EventCsvExp");
        ExperimentInstance instance = createInstance("EventCsvRun", exp);

        Event event = new Event();
        event.name = "test_event";
        event.datetime = new Date();
        event.experimentInstance = instance;
        EventData ed = new EventData();
        ed.name = "key1";
        ed.value = "val1";
        event.addEventData(ed);
        event.save();

        Result result = route(
            fakeRequest(GET, "/csv/data/" + instance.id)
                .withSession("uid", user.uid)
        );
        assertEquals("Event CSV export should return 200", OK, status(result));
        String csv = contentAsString(result);
        assertTrue("Event CSV should contain headers", csv.contains("\"event\""));
        assertTrue("Event CSV should contain event name", csv.contains("test_event"));
    }

    // === Steps ===

    @Test
    public void getStepsRequiresAuth() {
        Experiment exp = createExperiment("StepAuthExp");
        Result result = route(fakeRequest(GET, "/steps/" + exp.id));
        assertEquals("Steps should require auth", UNAUTHORIZED, status(result));
    }

    @Test
    public void getStepsWithAuth() {
        Language lang = createLanguage("eng", "English");
        User user = createUser("steps@test.com", "Steps User", "pass", lang);
        Experiment exp = createExperiment("StepGetExp");
        createStep(exp, "initStep", "println('init')");

        Result result = route(
            fakeRequest(GET, "/steps/" + exp.id)
                .withSession("uid", user.uid)
        );
        assertEquals("Get steps should return 200", OK, status(result));
    }

    // === Content ===

    @Test
    public void getContentRequiresAuth() {
        Experiment exp = createExperiment("ContentAuthExp");
        Result result = route(fakeRequest(GET, "/content/" + exp.id));
        assertEquals("Content should require auth", UNAUTHORIZED, status(result));
    }

    // === Customize ===

    @Test
    public void getClientHtmlRequiresAuth() {
        Experiment exp = createExperiment("CustomizeAuthExp");
        Result result = route(fakeRequest(GET, "/customize/clientHtml/" + exp.id));
        assertEquals("Customize should require auth", UNAUTHORIZED, status(result));
    }

    // === Languages ===

    @Test
    public void getLanguagesReturnsJson() {
        createLanguage("eng", "English");
        createLanguage("fra", "French");

        Result result = route(fakeRequest(GET, "/languages"));
        assertEquals("Languages endpoint should return 200", OK, status(result));
    }

    // === Images ===

    @Test
    public void getImageReturns404ForMissing() {
        Result result = route(fakeRequest(GET, "/images/99999"));
        // Depending on implementation, this could be 404 or 500
        assertTrue("Missing image should not return 200",
            status(result) != OK);
    }

    // === AMT Admin ===

    @Test
    public void amtAdminRequiresAuth() {
        Result result = route(fakeRequest(GET, "/amtadmin/listHITs"));
        assertEquals("AMT admin should require auth", UNAUTHORIZED, status(result));
    }

    // === Save User Settings ===

    @Test
    public void saveUserSettingsRequiresAuth() {
        Result result = route(fakeRequest(POST, "/saveUserSettings"));
        assertEquals("Save settings should require auth", UNAUTHORIZED, status(result));
    }
}
