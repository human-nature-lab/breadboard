package models;

import org.junit.After;
import org.junit.Test;

import java.util.Timer;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * Behavior tests for the AMT lifecycle timer registry (MEMORY_LEAKS.md L6).
 *
 * <p>Before the fix, {@code ScriptBoard}'s HitCreated handler scheduled two bare
 * {@code new Timer()}s that were never tracked or cancelled — a reload or a second HIT
 * submission orphaned them (each pinning a stale {@code ThrottledWebSocketOut}). The
 * scheduling was extracted into {@link ScriptBoard#scheduleAmtTimers} /
 * {@link ScriptBoard#cancelAmtTimers} so the contract is testable here without the actor
 * or a live script engine. These assert that:
 * <ul>
 *   <li>the timers still fire on their delays (existing behavior preserved), and</li>
 *   <li>they can now be cancelled, and a re-submit supersedes the previous pair (the fix).</li>
 * </ul>
 *
 * No {@code FakeApplication} is needed: only static methods are exercised, never the actor.
 */
public class ScriptBoardAmtTimerTest {

    @After
    public void cancelLeftoverTimers() {
        ScriptBoard.cancelAmtTimers();
    }

    @Test
    public void scheduledAmtTimersStillFireOnTheirDelays() throws Exception {
        final AtomicInteger noNewConnections = new AtomicInteger(0);
        final AtomicInteger startInitStep = new AtomicInteger(0);

        ScriptBoard.scheduleAmtTimers(30, 60,
            new Runnable() { public void run() { noNewConnections.incrementAndGet(); } },
            new Runnable() { public void run() { startInitStep.incrementAndGet(); } });

        assertEquals("Both AMT timers should be tracked", 2, ScriptBoard.amtTimers.size());

        Thread.sleep(400);
        assertEquals("First timer (no-new-connections) should have fired once", 1, noNewConnections.get());
        assertEquals("Second timer (initStep.start) should have fired once", 1, startInitStep.get());
    }

    @Test
    public void cancelAmtTimersPreventsFiringAndClearsRegistry() throws Exception {
        final AtomicInteger fired = new AtomicInteger(0);

        ScriptBoard.scheduleAmtTimers(500, 700,
            new Runnable() { public void run() { fired.incrementAndGet(); } },
            new Runnable() { public void run() { fired.incrementAndGet(); } });

        ScriptBoard.cancelAmtTimers();
        assertEquals("Registry should be empty after cancel", 0, ScriptBoard.amtTimers.size());

        Thread.sleep(900); // well past both original delays
        assertEquals("Cancelled timers must not fire (this is what used to leak)", 0, fired.get());
    }

    @Test
    public void reSubmittingSupersedesThePreviousTimers() throws Exception {
        final AtomicInteger superseded = new AtomicInteger(0);
        // First submission with long delays.
        ScriptBoard.scheduleAmtTimers(400, 500,
            new Runnable() { public void run() { superseded.incrementAndGet(); } },
            new Runnable() { public void run() { superseded.incrementAndGet(); } });

        // Second submission must cancel the first pair before scheduling its own.
        final AtomicInteger fresh = new AtomicInteger(0);
        ScriptBoard.scheduleAmtTimers(30, 60,
            new Runnable() { public void run() { fresh.incrementAndGet(); } },
            new Runnable() { public void run() { fresh.incrementAndGet(); } });

        assertEquals("Only the new pair should be tracked, not four timers", 2, ScriptBoard.amtTimers.size());

        Thread.sleep(400);
        assertEquals("The superseded timers must not fire", 0, superseded.get());
        assertEquals("The new timers should fire", 2, fresh.get());
    }
}
