import models.EventBus;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link EventBus} -- the pub/sub used by the DSL ({@code events.on/emit}).
 * These need neither the script engine nor a database, so they live here rather than in
 * ScriptEngineTest (where an earlier draft kept them).
 */
public class EventBusTest {

    /** A closure that records each invocation's first argument (or a fixed tag). */
    private static groovy.lang.Closure recorder(final List<Object> sink, final Object tag) {
        return new groovy.lang.Closure(null) {
            public void doCall(Object... args) {
                if (tag != null) {
                    sink.add(tag);
                } else {
                    sink.add(args.length > 0 ? args[0] : null);
                }
            }
        };
    }

    @Test
    public void onThenEmitDeliversPayload() {
        EventBus<Object> bus = new EventBus<>();
        List<Object> received = new ArrayList<>();
        bus.on("e", recorder(received, null));

        bus.emit("e", "hello");

        assertEquals(1, received.size());
        assertEquals("hello", received.get(0));
    }

    @Test
    public void emitDeliversAllPayloadArgs() {
        EventBus<Object> bus = new EventBus<>();
        final List<Object[]> calls = new ArrayList<>();
        bus.on("e", new groovy.lang.Closure(null) {
            public void doCall(Object... args) { calls.add(args); }
        });

        bus.emit("e", "a", "b");

        assertEquals("listener should fire once", 1, calls.size());
        assertEquals("listener should receive both payload args", 2, calls.get(0).length);
        assertEquals("a", calls.get(0)[0]);
        assertEquals("b", calls.get(0)[1]);
    }

    @Test
    public void emitWithNoListenersIsANoOp() {
        EventBus<Object> bus = new EventBus<>();
        // Must not throw even though nothing was ever registered for this event.
        bus.emit("never-registered", "data");
    }

    @Test
    public void offByNameRemovesAllListeners() {
        EventBus<Object> bus = new EventBus<>();
        List<Object> received = new ArrayList<>();
        bus.on("e", recorder(received, "fired"));

        boolean removed = bus.off("e");
        bus.emit("e", "data");

        assertTrue("off(name) should report it removed the handler", removed);
        assertEquals("No listeners should fire after off(name)", 0, received.size());
    }

    @Test
    public void offByNameAndClosureRemovesOnlyThatListener() {
        EventBus<Object> bus = new EventBus<>();
        List<Object> received = new ArrayList<>();
        groovy.lang.Closure c1 = recorder(received, "c1");
        groovy.lang.Closure c2 = recorder(received, "c2");
        bus.on("e", c1);
        bus.on("e", c2);

        boolean removed = bus.off("e", c1);
        bus.emit("e", "data");

        assertTrue("off(name, closure) should report removal", removed);
        assertEquals("Only the remaining listener should fire", 1, received.size());
        assertEquals("c2", received.get(0));
    }

    @Test
    public void offByNameAndClosureReturnsFalseWhenAbsent() {
        EventBus<Object> bus = new EventBus<>();
        List<Object> received = new ArrayList<>();
        groovy.lang.Closure registered = recorder(received, "c1");
        groovy.lang.Closure stranger = recorder(received, "stranger");
        bus.on("e", registered);

        assertFalse("Removing a never-added closure should return false",
            bus.off("e", stranger));
    }

    @Test
    public void registerThenUnregister() {
        EventBus<Object> bus = new EventBus<>();
        List<Object> received = new ArrayList<>();
        bus.register("e");
        bus.on("e", recorder(received, "fired"));
        bus.emit("e", "data");
        assertEquals("Listener fires while registered", 1, received.size());

        bus.unregister("e");
        bus.emit("e", "data");
        assertEquals("No delivery after unregister", 1, received.size());
    }

    @Test
    public void multipleListenersAllFire() {
        EventBus<Object> bus = new EventBus<>();
        List<Object> received = new ArrayList<>();
        bus.on("e", recorder(received, "l1"));
        bus.on("e", recorder(received, "l2"));

        bus.emit("e", "data");

        assertEquals(2, received.size());
        assertTrue(received.contains("l1"));
        assertTrue(received.contains("l2"));
    }

    @Test
    public void clearRemovesEverything() {
        EventBus<Object> bus = new EventBus<>();
        List<Object> received = new ArrayList<>();
        bus.on("e1", recorder(received, "e1"));
        bus.on("e2", recorder(received, "e2"));

        bus.clear();
        bus.emit("e1", "data");
        bus.emit("e2", "data");

        assertEquals("No listeners should fire after clear()", 0, received.size());
    }
}
