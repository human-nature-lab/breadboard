package models;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

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

    /** A never-null, human-readable description of a Throwable for the admin/client console. */
    public static String describeError(Throwable t) {
        if (t == null) {
            return "Unknown error (null)";
        }
        ScriptLoader.humanizeStackTrace(t);
        StringBuilder sb = new StringBuilder(headline(t));
        appendGroovyFrames(sb, t);
        return sb.toString();
    }

    private static String headline(Throwable t) {
        String type = t.getClass().getSimpleName();
        if (type == null || type.isEmpty()) {
            type = t.getClass().getName();
        }
        String msg = ScriptLoader.humanize(t.getMessage());
        return (msg == null || msg.isEmpty()) ? type : type + ": " + msg;
    }

    private static void appendGroovyFrames(StringBuilder sb, Throwable t) {
        Set<Throwable> seenCauses = new HashSet<Throwable>();
        Set<String> frames = new LinkedHashSet<String>();
        for (Throwable cur = t; cur != null && seenCauses.add(cur); cur = cur.getCause()) {
            StackTraceElement[] elements = cur.getStackTrace();
            if (elements == null) continue;
            for (StackTraceElement f : elements) {
                String file = f.getFileName();
                if (file != null && file.endsWith(".groovy")) {
                    frames.add(file + ":" + f.getLineNumber());
                }
            }
        }
        for (String frame : frames) {
            sb.append("\n  at ").append(frame);
        }
    }

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
