package models;

/**
 * Pure, stateless helpers extracted from {@link ScriptBoard} so the logic can be
 * unit-tested without the actor's static state, the script engine, or a database.
 *
 * <p>Keeping these here (rather than duplicating the logic in a test) means the
 * production code path and the tested code path are the same — see
 * {@code ScriptBoard.initParam} and {@code ScriptBoard.makeUniqueClientId}, which
 * delegate here.
 */
public final class ScriptBoardSupport {

    private ScriptBoardSupport() {}

    /**
     * Coerce a parameter's string value to the type declared by its {@code Parameter}.
     * Mirrors the original {@code ScriptBoard.initParam} branching:
     * <ul>
     *   <li>{@code null} type (no Parameter defined) &rarr; the raw string (default binding)</li>
     *   <li>{@code "Integer"} &rarr; {@link Integer}</li>
     *   <li>{@code "Decimal"} &rarr; {@link Double}</li>
     *   <li>{@code "Boolean"} &rarr; {@link Boolean} (via {@link Boolean#parseBoolean})</li>
     *   <li>{@code "Text"} &rarr; the raw string</li>
     * </ul>
     *
     * @return the coerced value, or {@code null} to signal "do not bind" — which happens
     *         when an {@code Integer}/{@code Decimal} value cannot be parsed, or when the
     *         declared type is unrecognised. ({@code initParam} historically bound nothing
     *         in exactly those cases.)
     */
    public static Object coerceParam(String type, String value) {
        if (type == null) {
            return value; // no Parameter -> default string value
        }
        if (type.equals("Integer")) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (type.equals("Decimal")) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (type.equals("Boolean")) {
            return Boolean.parseBoolean(value);
        }
        if (type.equals("Text")) {
            return value;
        }
        return null; // unrecognised type -> leave unbound
    }

    /**
     * Compose the per-instance unique client id ({@code "<experimentId>-<instanceId>-<clientId>"}).
     * Takes {@code Object} ids so the null-safe string-concatenation semantics of the
     * original (a null id renders as {@code "null"}) are preserved exactly.
     */
    public static String makeUniqueClientId(Object experimentId, Object instanceId, String clientId) {
        return experimentId + "-" + instanceId + "-" + clientId;
    }
}
