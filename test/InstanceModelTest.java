import models.*;
import org.junit.Test;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for ExperimentInstance, Event, EventData, and Data models.
 */
public class InstanceModelTest extends BaseTest {

    // --- ExperimentInstance ---

    @Test
    public void createInstance() {
        Experiment exp = createExperiment("InstanceExp");
        ExperimentInstance instance = createInstance("Run 1", exp);

        assertNotNull("Instance should have an ID", instance.id);
        assertEquals("Run 1", instance.name);
        assertEquals(ExperimentInstance.Status.STOPPED, instance.status);
        assertFalse("hasStarted should be false initially", instance.hasStarted);
        assertNotNull("creationDate should be set", instance.creationDate);
    }

    @Test
    public void findInstanceById() {
        Experiment exp = createExperiment("FindInstanceExp");
        ExperimentInstance instance = createInstance("Run 2", exp);

        ExperimentInstance found = ExperimentInstance.findById(instance.id);
        assertNotNull("Should find instance by ID", found);
        assertEquals("Run 2", found.name);
    }

    @Test
    public void instanceLifecycle() {
        Experiment exp = createExperiment("LifecycleExp");
        ExperimentInstance instance = createInstance("Lifecycle", exp);

        assertEquals(ExperimentInstance.Status.STOPPED, instance.status);

        instance.start();
        assertEquals(ExperimentInstance.Status.RUNNING, instance.status);

        instance.stop();
        assertEquals(ExperimentInstance.Status.STOPPED, instance.status);

        instance.start();
        instance.finish();
        assertEquals(ExperimentInstance.Status.FINISHED, instance.status);
    }

    @Test
    public void instanceTestingStatus() {
        Experiment exp = createExperiment("TestStatusExp");
        ExperimentInstance instance = createInstance("Testing", exp);

        instance.test();
        assertEquals(ExperimentInstance.Status.TESTING, instance.status);
        assertTrue("TESTING should be runnable", instance.status.isRunnable());
    }

    @Test(expected = IllegalStateException.class)
    public void cannotStartFinishedInstance() {
        Experiment exp = createExperiment("FinishedExp");
        ExperimentInstance instance = createInstance("Finished", exp);
        instance.start();
        instance.finish();
        instance.start();
    }

    @Test
    public void findInstancesByStatus() {
        Experiment exp = createExperiment("StatusExp");
        ExperimentInstance running = createInstance("Running", exp);
        running.start();
        running.save();

        ExperimentInstance stopped = createInstance("Stopped", exp);
        // stopped is already STOPPED by default

        List<ExperimentInstance> runningInstances = ExperimentInstance.findByStatus(ExperimentInstance.Status.RUNNING);
        assertEquals("Should find 1 running instance", 1, runningInstances.size());

        List<ExperimentInstance> stoppedInstances = ExperimentInstance.findByStatus(ExperimentInstance.Status.STOPPED);
        assertEquals("Should find 1 stopped instance", 1, stoppedInstances.size());
    }

    @Test
    public void instanceBelongsToExperiment() {
        Experiment exp = createExperiment("BelongsExp");
        ExperimentInstance instance = createInstance("Child", exp);

        Experiment found = Experiment.findById(exp.id);
        assertNotNull(found);
        assertEquals("Experiment should have 1 instance", 1, found.instances.size());
        assertEquals("Child", found.instances.get(0).name);
    }

    // --- Event & EventData ---

    @Test
    public void createEventWithData() {
        Experiment exp = createExperiment("EventExp");
        ExperimentInstance instance = createInstance("EventRun", exp);

        Event event = new Event();
        event.name = "player_choice";
        event.datetime = new Date();
        event.experimentInstance = instance;

        EventData ed1 = new EventData();
        ed1.name = "player_id";
        ed1.value = "p1";
        event.addEventData(ed1);

        EventData ed2 = new EventData();
        ed2.name = "choice";
        ed2.value = "cooperate";
        event.addEventData(ed2);

        event.save();

        assertNotNull("Event should have an ID", event.id);
        assertNotNull("EventData should have IDs", ed1.id);
        assertNotNull("EventData should have IDs", ed2.id);
    }

    @Test
    public void eventCascadesFromInstance() {
        Experiment exp = createExperiment("CascadeEventExp");
        ExperimentInstance instance = createInstance("CascadeRun", exp);

        Event event = new Event();
        event.name = "test_event";
        event.datetime = new Date();
        event.experimentInstance = instance;
        event.save();

        ExperimentInstance found = ExperimentInstance.findById(instance.id);
        assertNotNull(found);
        assertEquals("Instance should have 1 event", 1, found.events.size());
    }

    @Test
    public void eventDataFindAll() {
        Experiment exp = createExperiment("EDFindExp");
        ExperimentInstance instance = createInstance("EDRun", exp);

        Event event = new Event();
        event.name = "ed_event";
        event.datetime = new Date();
        event.experimentInstance = instance;

        EventData ed = new EventData();
        ed.name = "key";
        ed.value = "val";
        event.addEventData(ed);
        event.save();

        List<EventData> all = EventData.findAll();
        assertTrue("Should find at least 1 EventData", all.size() >= 1);
    }

    // --- Data ---

    @Test
    public void createDataForInstance() {
        Experiment exp = createExperiment("DataExp");
        ExperimentInstance instance = createInstance("DataRun", exp);

        Data data = new Data();
        data.name = "numPlayers";
        data.value = "6";
        data.experimentInstance = instance;
        data.save();

        ExperimentInstance found = ExperimentInstance.findById(instance.id);
        assertNotNull(found);
        assertEquals("Instance should have 1 data entry", 1, found.data.size());
        assertEquals("numPlayers", found.data.get(0).name);
        assertEquals("6", found.data.get(0).value);
    }

    @Test
    public void dataFindAll() {
        Experiment exp = createExperiment("DataFindExp");
        ExperimentInstance instance = createInstance("DataFindRun", exp);

        Data d1 = new Data();
        d1.name = "param1";
        d1.value = "val1";
        d1.experimentInstance = instance;
        d1.save();

        Data d2 = new Data();
        d2.name = "param2";
        d2.value = "val2";
        d2.experimentInstance = instance;
        d2.save();

        List<Data> all = Data.findAll();
        assertTrue("Should find at least 2 Data entries", all.size() >= 2);
    }

    @Test
    public void instanceDeleteCascadesToData() {
        Experiment exp = createExperiment("DelDataExp");
        ExperimentInstance instance = createInstance("DelRun", exp);

        Data data = new Data();
        data.name = "toDelete";
        data.value = "gone";
        data.experimentInstance = instance;
        data.save();
        Long dataId = data.id;

        instance.delete();

        Data orphan = Data.find.byId(dataId);
        assertNull("Data should be deleted when instance is deleted", orphan);
    }
}
