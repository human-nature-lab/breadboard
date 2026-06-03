import models.*;
import org.junit.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for the User model: CRUD, finders, authentication, and relationships.
 */
public class UserModelTest extends BaseTest {

    @Test
    public void createAndFindUser() {
        Language lang = createLanguage("eng", "English");
        User user = createUser("alice@test.com", "Alice", "secret123", lang);

        User found = User.findByEmail("alice@test.com");
        assertNotNull("User should be found by email", found);
        assertEquals("alice@test.com", found.email);
        assertEquals("Alice", found.name);
        assertEquals("admin", found.role);
    }

    @Test
    public void findByUID() {
        Language lang = createLanguage("eng", "English");
        User user = createUser("bob@test.com", "Bob", "pass", lang);
        String uid = user.uid;

        User found = User.findByUID(uid);
        assertNotNull("User should be found by UID", found);
        assertEquals("bob@test.com", found.email);
    }

    @Test
    public void findByEmailReturnsNullForMissing() {
        User found = User.findByEmail("nobody@test.com");
        assertNull("Should return null for nonexistent email", found);
    }

    @Test
    public void findByUIDReturnsNullForMissing() {
        User found = User.findByUID("nonexistent-uid");
        assertNull("Should return null for nonexistent UID", found);
    }

    @Test
    public void authenticateWithCorrectPassword() {
        Language lang = createLanguage("eng", "English");
        createUser("auth@test.com", "Auth User", "correct-password", lang);

        User authenticated = User.authenticate("auth@test.com", "correct-password");
        assertNotNull("Should authenticate with correct password", authenticated);
        assertEquals("auth@test.com", authenticated.email);
    }

    @Test
    public void authenticateWithWrongPassword() {
        Language lang = createLanguage("eng", "English");
        createUser("auth2@test.com", "Auth User 2", "correct-password", lang);

        User authenticated = User.authenticate("auth2@test.com", "wrong-password");
        assertNull("Should return null for wrong password", authenticated);
    }

    @Test
    public void authenticateWithNonexistentUser() {
        User authenticated = User.authenticate("ghost@test.com", "any-password");
        assertNull("Should return null for nonexistent user", authenticated);
    }

    @Test
    public void findRowCount() {
        assertEquals("Should start with zero users (or seeded count)", 0, User.findRowCount());

        Language lang = createLanguage("eng", "English");
        createUser("count1@test.com", "User 1", "pass", lang);
        createUser("count2@test.com", "User 2", "pass", lang);

        assertEquals("Should have 2 users", 2, User.findRowCount());
    }

    @Test
    public void findAll() {
        Language lang = createLanguage("eng", "English");
        createUser("all1@test.com", "User 1", "pass", lang);
        createUser("all2@test.com", "User 2", "pass", lang);

        List<User> users = User.findAll();
        assertEquals("Should find 2 users", 2, users.size());
    }

    @Test
    public void userOwnedExperimentsManyToMany() {
        Language lang = createLanguage("eng", "English");
        User user = createUser("owner@test.com", "Owner", "pass", lang);
        Experiment exp1 = createExperiment("Experiment 1");
        Experiment exp2 = createExperiment("Experiment 2");

        user.ownedExperiments.add(exp1);
        user.ownedExperiments.add(exp2);
        user.saveManyToManyAssociations("ownedExperiments");

        User found = User.findByEmail("owner@test.com");
        assertNotNull(found);
        assertEquals("User should own 2 experiments", 2, found.ownedExperiments.size());
    }

    @Test
    public void userSelectedExperiment() {
        Language lang = createLanguage("eng", "English");
        User user = createUser("selector@test.com", "Selector", "pass", lang);
        Experiment exp = createExperiment("Selected Experiment");

        user.setSelectedExperiment(exp);
        user.update();

        // Re-fetch the user and verify the relationship
        User found = User.findByEmail("selector@test.com");
        assertNotNull(found);
        // Access through the getter which handles lazy loading
        Experiment selectedExp = found.getExperiment();
        assertNotNull("Selected experiment should be set", selectedExp);
        assertEquals("Selected Experiment", selectedExp.name);
    }

    @Test
    public void userDefaultLanguage() {
        Language lang = createLanguage("fra", "French");
        User user = createUser("french@test.com", "French User", "pass", lang);

        // Re-fetch from database to ensure relationship is loaded
        User found = User.find.fetch("defaultLanguage").where().eq("email", "french@test.com").findUnique();
        assertNotNull(found);
        assertNotNull("Default language should be set", found.defaultLanguage);
        assertEquals("fra", found.defaultLanguage.code);
    }

    @Test
    public void getExperimentByName() {
        Language lang = createLanguage("eng", "English");
        User user = createUser("getter@test.com", "Getter", "pass", lang);
        Experiment exp = createExperiment("FindMe");

        user.ownedExperiments.add(exp);
        user.saveManyToManyAssociations("ownedExperiments");

        User found = User.findByEmail("getter@test.com");
        Experiment byName = found.getExperimentByName("FindMe");
        assertNotNull("Should find experiment by name", byName);
        assertEquals("FindMe", byName.name);

        Experiment missing = found.getExperimentByName("DoesNotExist");
        assertNull("Should return null for missing experiment name", missing);
    }

    @Test
    public void passwordIsHashedNotPlaintext() {
        Language lang = createLanguage("eng", "English");
        User user = createUser("hashed@test.com", "Hashed", "my-secret", lang);

        User found = User.findByEmail("hashed@test.com");
        assertNotEquals("Password should not be stored as plaintext", "my-secret", found.password);
        assertTrue("Password should verify with BCrypt", BCrypt.checkpw("my-secret", found.password));
    }
}
