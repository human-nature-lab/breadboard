package models;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.apache.commons.io.FileUtils;
import play.Logger;

/**
 * Resolves and loads the Groovy DSL scripts into a JSR-223 ScriptEngine.
 *
 * <p>Loading is dynamic: every {@code *.groovy} file in the groovy directory is loaded,
 * except test scripts (file names ending in {@value #TEST_SUFFIX}). A small set of core
 * scripts is loaded first, in a fixed dependency order; everything else is loaded afterwards
 * in alphabetical order.
 *
 * <p>The order is deliberately <b>not</b> purely alphabetical, because the core scripts have
 * eval-time dependencies on one another. For example {@code timer.groovy} declares
 * {@code class SharedTimer extends BreadboardBase} (a class defined in {@code util.groovy}),
 * and {@code actions.groovy}'s top-level {@code a = new PlayerActions(...)} constructs a
 * {@code BBTimer} (defined in {@code timer.groovy}). A plain alphabetical sort would load
 * {@code actions}/{@code timer} before their dependencies and fail at load time.
 *
 * <p>Each script is loaded under its file name so that compile/runtime errors identify the
 * offending file (see {@link #evalNamed}). This is what lets test scripts and new drop-in
 * scripts be loaded and pointed at without hand-editing a hard-coded list.
 *
 * <p>This class is the single source of truth for load order, shared by production
 * ({@link ScriptBoard#resetEngine}) and the test harness, so the two cannot drift.
 */
public final class ScriptLoader {

  /**
   * Core scripts, loaded first in dependency order (this list is intentionally NOT
   * alphabetical):
   * <ol>
   *   <li>{@code util}    &mdash; defines {@code BreadboardBase}, {@code currency}, {@code d}, ...</li>
   *   <li>{@code timer}   &mdash; {@code class SharedTimer extends BreadboardBase}; defines {@code BBTimer}</li>
   *   <li>{@code graph}   &mdash; {@code g = new BreadboardGraph(...)}</li>
   *   <li>{@code actions} &mdash; {@code a = new PlayerActions(...)} (constructs a {@code BBTimer})</li>
   *   <li>{@code step, test, events, chat, form, ready} &mdash; remaining core DSL</li>
   * </ol>
   * Any groovy file not listed here is loaded afterwards, in alphabetical order.
   */
  public static final List<String> CORE_ORDER = Collections.unmodifiableList(
    Arrays.asList(
      "util.groovy",
      "timer.groovy",
      "graph.groovy",
      "actions.groovy",
      "step.groovy",
      // "test.groovy",
      "events.groovy",
      "chat.groovy",
      "form.groovy",
      "ready.groovy"
    )
  );

  /** Files whose names end with this suffix are test scripts and are skipped by the loader. */
  public static final String TEST_SUFFIX = "_test.groovy";

  private ScriptLoader() {}

  /**
   * The script file names to load from {@code groovyDir}, in load order: the core scripts
   * first (those actually present, in {@link #CORE_ORDER}), then every other {@code *.groovy}
   * file alphabetically. Files ending in {@link #TEST_SUFFIX} are excluded. Never returns null.
   */
  public static List<String> resolveLoadOrder(File groovyDir) {
    LinkedHashSet<String> remaining = new LinkedHashSet<String>();
    String[] names = (groovyDir == null) ? null : groovyDir.list();
    if (names != null) {
      List<String> sorted = new ArrayList<String>(Arrays.asList(names));
      Collections.sort(sorted); // alphabetical
      for (String name : sorted) {
        if (isLoadable(name)) {
          remaining.add(name);
        }
      }
    }

    List<String> ordered = new ArrayList<String>();
    // 1) core scripts first, in their declared dependency order (only those present)
    for (String core : CORE_ORDER) {
      if (remaining.remove(core)) {
        ordered.add(core);
      }
    }
    // 2) everything else, already alphabetical from the sort above
    ordered.addAll(remaining);
    return ordered;
  }

  /** Whether {@code name} is a groovy script the loader should load (i.e. not a test script). */
  public static boolean isLoadable(String name) {
    if (name == null) return false;
    String lower = name.toLowerCase();
    return lower.endsWith(".groovy") && !lower.endsWith(TEST_SUFFIX);
  }

  /** Whether {@code name} is one of the order-sensitive core scripts. */
  public static boolean isCore(String name) {
    return CORE_ORDER.contains(name);
  }

  /**
   * Read, name, and eval every resolved script into {@code engine}, in {@link #resolveLoadOrder}
   * order. A failure in a core script aborts the load (the platform can't run without them); a
   * failure in any other (auto-discovered) script is logged &mdash; naming the file &mdash; and
   * skipped, so one broken drop-in script can't take down the whole engine boot.
   */
  public static void loadAll(ScriptEngine engine, File groovyDir)
    throws IOException, ScriptException {
    // Fresh name map for this (re)load. The engine's script counter is static and never resets,
    // so the Script<N> numbers differ on every reload -- the map must be rebuilt each time.
    resetScriptNames();
    for (String name : resolveLoadOrder(groovyDir)) {
      File file = new File(groovyDir, name);
      String body = FileUtils.readFileToString(file, "UTF-8");
      // Append (a) a self-registration epilogue that records this eval's generated class name
      // (this.getClass().getName() == "Script<N>") against the real file name, so humanize() and
      // humanizeStackTrace() can translate the opaque Groovy names back; and (b) a null terminator
      // so the engine can always reload even after an error. The epilogue runs only after the body
      // (preserving the body's line numbers) and is wrapped in try/catch so a registration hiccup
      // can never fail an otherwise-good load.
      String source =
        body +
        "\n" +
        "try { models.ScriptLoader.registerScriptName(this.getClass().getName(), " +
        groovyStringLiteral(name) +
        ") } catch (Throwable __bbIgnore) {}\n" +
        "null;\n";
      Logger.debug("loading " + name);
      try {
        evalNamed(engine, name, source);
        Logger.debug(name + " load done");
      } catch (ScriptException se) {
        if (isCore(name)) throw se; // core scripts are required -- fail loudly
        Logger.error("Skipping groovy script that failed to load: " + name, se);
      } catch (RuntimeException re) {
        if (isCore(name)) throw re;
        Logger.error("Skipping groovy script that failed to load: " + name, re);
      }
    }
  }

  /**
   * Eval {@code source}, tagging any failure with {@code scriptName}. Groovy's JSR-223 engine
   * names parsed scripts {@code "Script<N>.groovy"}, which is opaque; on failure the thrown
   * {@link ScriptException} is rewrapped so its message and {@code getFileName()} identify the
   * actual script file. The original exception is preserved as the cause.
   */
  public static void evalNamed(
    ScriptEngine engine,
    String scriptName,
    String source
  ) throws ScriptException {
    try {
      engine.eval(source);
    } catch (ScriptException se) {
      ScriptException named = new ScriptException(
        "[" + scriptName + "] " + humanize(se.getMessage()),
        scriptName,
        se.getLineNumber(),
        se.getColumnNumber()
      );
      named.initCause(se);
      throw named;
    }
  }

  // --- generated-name -> real-name mapping ------------------------------------------------
  //
  // Groovy's JSR-223 engine names every eval'd script "Script<N>.groovy" via a private static
  // counter; that opaque name is what shows up in exception messages and stack-trace frames.
  // Each loaded script self-registers its generated class name against its real file name (see
  // the epilogue appended in loadAll), letting us translate the names back in errors and logs.

  /** generated class name (e.g. "Script7") -> real script file name (e.g. "graph.groovy"). */
  private static volatile Map<String, String> scriptNames =
    new ConcurrentHashMap<String, String>();

  /** Matches a Groovy-generated script name, optionally with the .groovy suffix, as a whole word. */
  private static final Pattern GENERATED_NAME = Pattern.compile(
    "\\bScript\\d+(?:\\.groovy)?"
  );

  /** Drop all name mappings. Called at the start of each {@link #loadAll} so reloads start clean. */
  public static void resetScriptNames() {
    scriptNames = new ConcurrentHashMap<String, String>();
  }

  /**
   * Record that the Groovy-generated class {@code generatedClassName} (e.g. {@code "Script7"})
   * came from {@code realFileName} (e.g. {@code "graph.groovy"}). Called from loaded scripts via
   * the epilogue {@link #loadAll} appends; safe to call from script (Groovy) code.
   */
  public static void registerScriptName(
    String generatedClassName,
    String realFileName
  ) {
    if (generatedClassName != null && realFileName != null) {
      scriptNames.put(generatedClassName, realFileName);
    }
  }

  /** An immutable copy of the current generated-name -> real-name mappings (for tests/diagnostics). */
  public static Map<String, String> scriptNamesSnapshot() {
    return Collections.unmodifiableMap(
      new java.util.HashMap<String, String>(scriptNames)
    );
  }

  /**
   * Replace every Groovy-generated script name in {@code text} (e.g. {@code "Script7"} or
   * {@code "Script7.groovy"}) with the real file name it was loaded from. Unmapped names (e.g. a
   * user-entered script, which has no file) are left untouched. Returns {@code text} unchanged
   * when there is nothing to map.
   */
  public static String humanize(String text) {
    Map<String, String> names = scriptNames;
    if (text == null || names.isEmpty()) {
      return text;
    }
    Matcher m = GENERATED_NAME.matcher(text);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String real = names.get(stripGroovySuffix(m.group()));
      m.appendReplacement(
        sb,
        Matcher.quoteReplacement(real != null ? real : m.group())
      );
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /**
   * Rewrite, in place, the file name of every stack-trace frame in {@code t} (and its causes) that
   * points at a Groovy-generated script, so logged traces read e.g. {@code graph.groovy:42} instead
   * of {@code Script7.groovy:42}. The frame's declaring class/method/line are left intact. Returns
   * {@code t} for chaining.
   */
  public static Throwable humanizeStackTrace(Throwable t) {
    Map<String, String> names = scriptNames;
    if (t == null || names.isEmpty()) {
      return t;
    }
    Set<Throwable> seen = new HashSet<Throwable>();
    for (
      Throwable cur = t;
      cur != null && seen.add(cur);
      cur = cur.getCause()
    ) {
      StackTraceElement[] frames = cur.getStackTrace();
      boolean changed = false;
      for (int i = 0; i < frames.length; i++) {
        StackTraceElement f = frames[i];
        String real = (f.getFileName() == null)
          ? null
          : names.get(stripGroovySuffix(f.getFileName()));
        if (real != null) {
          frames[i] = new StackTraceElement(
            f.getClassName(),
            f.getMethodName(),
            real,
            f.getLineNumber()
          );
          changed = true;
        }
      }
      if (changed) {
        cur.setStackTrace(frames);
      }
    }
    return t;
  }

  /** Strip a trailing ".groovy" so a token / file name reduces to the registered class key. */
  private static String stripGroovySuffix(String token) {
    return token.endsWith(".groovy")
      ? token.substring(0, token.length() - ".groovy".length())
      : token;
  }

  /** Render {@code s} as a single-quoted Groovy string literal, escaping backslashes and quotes. */
  private static String groovyStringLiteral(String s) {
    return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
  }
}
