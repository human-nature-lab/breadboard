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

    /**
     * Build a never-null, human-readable description of a Throwable for surfacing to the
     * admin/client console (the message field the browser shows). This replaces the old
     * {@code "Caught error: " + e.getMessage()} pattern, which collapsed to the useless
     * {@code "Caught error: null"} for the most common Groovy failures (e.g. a
     * {@link NullPointerException} from touching a null vertex property, whose message is
     * null) — and which actually threw a fresh NPE of its own via {@code String.concat(null)}.
     *
     * <p>The result is:
     * <pre>
     *   &lt;SimpleExceptionType&gt;[: &lt;message&gt;]
     *     at &lt;file&gt;.groovy:&lt;line&gt;
     *     ...
     * </pre>
     * i.e. always at least the exception type (so it can never be just "null"), the message
     * when there is one, and every distinct {@code *.groovy} stack frame from the throwable
     * and its causes so the user sees <em>where</em> in their step it failed.
     *
     * <p>Groovy's opaque {@code Script<N>.groovy} frame names are translated back to real file
     * names first via {@link ScriptLoader#humanizeStackTrace} (a no-op when no scripts are
     * loaded, which keeps this method unit-testable without the engine).
     */
    public static String describeError(Throwable t) {
        if (t == null) {
            return "Unknown error (null)";
        }
        // Translate Groovy's "Script<N>.groovy" frame names back to real file names in place.
        // Idempotent and safe to call even when callers have already humanized the trace.
        ScriptLoader.humanizeStackTrace(t);

        StringBuilder sb = new StringBuilder(headline(t));
        appendGroovyFrames(sb, t);
        return sb.toString();
    }

    /** "&lt;SimpleType&gt;" or "&lt;SimpleType&gt;: &lt;message&gt;" — never null, never empty. */
    private static String headline(Throwable t) {
        String type = t.getClass().getSimpleName();
        if (type == null || type.isEmpty()) {
            type = t.getClass().getName(); // anonymous classes have an empty simple name
        }
        String msg = ScriptLoader.humanize(t.getMessage());
        return (msg == null || msg.isEmpty()) ? type : type + ": " + msg;
    }

    /**
     * Append each distinct {@code *.groovy} stack frame ("file.groovy:line") from {@code t} and
     * its cause chain. Walking the causes matters because the script engine wraps the real Groovy
     * exception: the user's frames live on the cause, not the top-level ScriptException. Frames are
     * de-duplicated (the same frame typically appears on both the wrapper and the cause).
     */
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
