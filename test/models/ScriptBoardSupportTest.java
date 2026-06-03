package models;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ScriptBoardSupport} -- the pure logic extracted from
 * ScriptBoard.initParam / makeUniqueClientId. No engine, no DB, no Play app.
 */
public class ScriptBoardSupportTest {

    @Test
    public void coercesIntegerParam() {
        assertEquals(Integer.valueOf(7), ScriptBoardSupport.coerceParam("Integer", "7"));
    }

    @Test
    public void badIntegerYieldsNull() {
        // null signals "leave unbound" -- the original logged and bound nothing.
        assertNull(ScriptBoardSupport.coerceParam("Integer", "notAnInt"));
    }

    @Test
    public void coercesDecimalParam() {
        assertEquals(Double.valueOf(0.5), ScriptBoardSupport.coerceParam("Decimal", "0.5"));
    }

    @Test
    public void badDecimalYieldsNull() {
        assertNull(ScriptBoardSupport.coerceParam("Decimal", "x.y"));
    }

    @Test
    public void coercesBooleanParam() {
        assertEquals(Boolean.TRUE, ScriptBoardSupport.coerceParam("Boolean", "true"));
        assertEquals(Boolean.TRUE, ScriptBoardSupport.coerceParam("Boolean", "TRUE"));
        // Boolean.parseBoolean: anything that isn't "true" (case-insensitive) is false.
        assertEquals(Boolean.FALSE, ScriptBoardSupport.coerceParam("Boolean", "nope"));
    }

    @Test
    public void textIsTheRawString() {
        assertEquals("hello world", ScriptBoardSupport.coerceParam("Text", "hello world"));
    }

    @Test
    public void nullTypeFallsBackToRawString() {
        // No Parameter defined for the key -> bound as the default string value.
        assertEquals("raw", ScriptBoardSupport.coerceParam(null, "raw"));
    }

    @Test
    public void unrecognisedTypeYieldsNull() {
        assertNull(ScriptBoardSupport.coerceParam("Mystery", "value"));
    }

    @Test
    public void makeUniqueClientIdComposesIds() {
        assertEquals("3-7-abc", ScriptBoardSupport.makeUniqueClientId(3L, 7L, "abc"));
    }

    @Test
    public void makeUniqueClientIdIsNullSafe() {
        // Mirrors the original's string concatenation, where a null id renders as "null".
        assertEquals("null-null-abc", ScriptBoardSupport.makeUniqueClientId(null, null, "abc"));
    }
}
