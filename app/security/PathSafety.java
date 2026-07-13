package security;

import java.nio.file.Path;

/**
 * Path-traversal containment helpers. Resolving a user-influenced path segment against a trusted
 * base directory and then verifying the result still lives inside that base is the standard guard
 * against "../" / absolute-path escapes. Comparison is purely lexical (after {@code normalize()}),
 * which matches how the rest of the app builds these paths and needs no filesystem access.
 */
public class PathSafety {

  /**
   * Resolve {@code child} against {@code baseDir} and verify the result stays inside {@code baseDir}.
   *
   * @return the normalized, absolute, contained path, or {@code null} if {@code child} would escape
   *         the base directory (via "..", an absolute path, etc.).
   */
  public static Path resolveContained(Path baseDir, String child) {
    Path base = baseDir.normalize();
    if (!base.isAbsolute()) {
      base = base.toAbsolutePath();
    }
    Path resolved = base.resolve(child).normalize();
    return isChildOf(resolved, base) ? resolved : null;
  }

  /** True if {@code child} is {@code parent} itself or nested beneath it (lexical comparison). */
  public static Boolean isChildOf(Path child, Path parent) {
    while (child != null) {
      if (child.equals(parent)) {
        return true;
      }
      child = child.getParent();
    }
    return false;
  }
}
