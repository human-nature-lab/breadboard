import models.*;
import org.junit.Test;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for AMTHit, AMTAssignment, and AMTWorker models.
 */
public class AMTModelTest extends BaseTest {

    private AMTHit createHit(ExperimentInstance instance) {
        AMTHit hit = new AMTHit();
        hit.creationDate = new Date();
        hit.hitId = "HIT-" + System.nanoTime();
        hit.title = "Test HIT";
        hit.description = "A test HIT";
        hit.reward = "0.50";
        hit.maxAssignments = "10";
        hit.sandbox = true;
        hit.experimentInstance = instance;
        hit.save();
        return hit;
    }

    // --- AMTHit ---

    @Test
    public void createAndFindHit() {
        Experiment exp = createExperiment("AMTExp");
        ExperimentInstance instance = createInstance("AMTRun", exp);
        AMTHit hit = createHit(instance);

        assertNotNull("Hit should have an ID", hit.id);

        AMTHit found = AMTHit.findById(hit.id);
        assertNotNull("Should find hit by ID", found);
        assertEquals("Test HIT", found.title);
        assertEquals("0.50", found.reward);
        assertTrue("Should be sandbox", found.sandbox);
    }

    @Test
    public void findHitByHitId() {
        Experiment exp = createExperiment("HitIdExp");
        ExperimentInstance instance = createInstance("HitIdRun", exp);
        AMTHit hit = createHit(instance);

        AMTHit found = AMTHit.findByHitId(hit.hitId);
        assertNotNull("Should find hit by hitId", found);
        assertEquals(hit.id, found.id);
    }

    @Test
    public void hitFindAll() {
        Experiment exp = createExperiment("HitAllExp");
        ExperimentInstance instance = createInstance("HitAllRun", exp);
        createHit(instance);
        createHit(instance);

        List<AMTHit> all = AMTHit.findAll();
        assertTrue("Should find at least 2 hits", all.size() >= 2);
    }

    @Test
    public void hitBelongsToInstance() {
        Experiment exp = createExperiment("HitInstExp");
        ExperimentInstance instance = createInstance("HitInstRun", exp);
        AMTHit hit = createHit(instance);

        ExperimentInstance found = ExperimentInstance.findById(instance.id);
        assertNotNull(found);
        assertEquals("Instance should have 1 hit", 1, found.amtHits.size());
    }

    // --- AMTAssignment ---

    @Test
    public void createAssignment() {
        Experiment exp = createExperiment("AssignExp");
        ExperimentInstance instance = createInstance("AssignRun", exp);
        AMTHit hit = createHit(instance);

        AMTAssignment assignment = new AMTAssignment();
        assignment.assignmentId = "ASSIGN-001";
        assignment.workerId = "WORKER-001";
        assignment.assignmentStatus = "Submitted";
        assignment.assignmentCompleted = false;
        assignment.bonusGranted = false;
        assignment.amtHit = hit;
        assignment.save();

        assertNotNull("Assignment should have an ID", assignment.id);
    }

    @Test
    public void findAssignmentByAssignmentId() {
        Experiment exp = createExperiment("FindAssignExp");
        ExperimentInstance instance = createInstance("FindAssignRun", exp);
        AMTHit hit = createHit(instance);

        AMTAssignment assignment = new AMTAssignment();
        assignment.assignmentId = "ASSIGN-FIND";
        assignment.workerId = "WORKER-FIND";
        assignment.amtHit = hit;
        assignment.save();

        AMTAssignment found = AMTAssignment.findByAssignmentId("ASSIGN-FIND");
        assertNotNull("Should find assignment by assignmentId", found);
        assertEquals("WORKER-FIND", found.workerId);
    }

    @Test
    public void assignmentRowCountByWorkerId() {
        Experiment exp = createExperiment("WorkerCountExp");
        ExperimentInstance instance = createInstance("WorkerCountRun", exp);
        AMTHit hit = createHit(instance);

        // findRowCountByWorkerId filters on assignmentCompleted = true
        AMTAssignment a1 = new AMTAssignment();
        a1.assignmentId = "A1";
        a1.workerId = "REPEAT-WORKER";
        a1.assignmentCompleted = true;
        a1.amtHit = hit;
        a1.save();

        AMTAssignment a2 = new AMTAssignment();
        a2.assignmentId = "A2";
        a2.workerId = "REPEAT-WORKER";
        a2.assignmentCompleted = true;
        a2.amtHit = hit;
        a2.save();

        int count = AMTAssignment.findRowCountByWorkerId("REPEAT-WORKER");
        assertEquals("Should find 2 completed assignments for worker", 2, count);
    }

    @Test
    public void assignmentCascadeFromHit() {
        Experiment exp = createExperiment("CascadeAssignExp");
        ExperimentInstance instance = createInstance("CascadeAssignRun", exp);
        AMTHit hit = createHit(instance);

        AMTAssignment assignment = new AMTAssignment();
        assignment.assignmentId = "CASCADE-A";
        assignment.workerId = "CASCADE-W";
        assignment.amtHit = hit;
        assignment.save();

        AMTHit found = AMTHit.findById(hit.id);
        assertEquals("Hit should have 1 assignment", 1, found.amtAssignments.size());
    }

    // --- AMTWorker ---

    @Test
    public void createWorker() {
        Experiment exp = createExperiment("WorkerExp");
        ExperimentInstance instance = createInstance("WorkerRun", exp);
        AMTHit hit = createHit(instance);

        AMTWorker worker = new AMTWorker();
        worker.workerId = "W-001";
        worker.score = "100";
        worker.completion = "complete";
        worker.amtHit = hit;
        worker.save();

        assertNotNull("Worker should have an ID", worker.id);
    }

    @Test
    public void findWorkerByWorkerId() {
        Experiment exp = createExperiment("FindWorkerExp");
        ExperimentInstance instance = createInstance("FindWorkerRun", exp);
        AMTHit hit = createHit(instance);

        AMTWorker worker = new AMTWorker();
        worker.workerId = "W-FIND";
        worker.amtHit = hit;
        worker.save();

        AMTWorker found = AMTWorker.findByWorkerId("W-FIND");
        assertNotNull("Should find worker by workerId", found);
    }

    @Test
    public void countWorkerByWorkerId() {
        Experiment exp = createExperiment("CountWorkerExp");
        ExperimentInstance instance = createInstance("CountWorkerRun", exp);
        AMTHit hit1 = createHit(instance);
        AMTHit hit2 = createHit(instance);

        AMTWorker w1 = new AMTWorker();
        w1.workerId = "W-COUNT";
        w1.amtHit = hit1;
        w1.save();

        AMTWorker w2 = new AMTWorker();
        w2.workerId = "W-COUNT";
        w2.amtHit = hit2;
        w2.save();

        int count = AMTWorker.countByWorkerId("W-COUNT");
        assertEquals("Should count 2 workers with same workerId", 2, count);
    }

    @Test
    public void workerCascadeFromHit() {
        Experiment exp = createExperiment("CascadeWorkerExp");
        ExperimentInstance instance = createInstance("CascadeWorkerRun", exp);
        AMTHit hit = createHit(instance);

        AMTWorker worker = new AMTWorker();
        worker.workerId = "W-CASCADE";
        worker.amtHit = hit;
        worker.save();

        AMTHit found = AMTHit.findById(hit.id);
        assertEquals("Hit should have 1 worker", 1, found.amtWorkers.size());
    }
}
