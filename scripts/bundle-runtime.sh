#!/usr/bin/env bash
#
# Bundle a Java 8 runtime into per-platform, self-contained Breadboard zips, and
# assemble a Docker build context.  (TODO #11)
# ------------------------------------------------------------------------------
# The Play 2.2 / Scala 2.10 stack only runs on Java 8 -- the launchers even pass
# -XX:+UseConcMarkSweepGC, a flag REMOVED in JDK 14 -- so today a recipient must
# hunt down and install exactly Java 8. Instead we ship, per target, a
# SELF-CONTAINED zip: the `sbt dist` tree, plus
#   runtime/          a Java 8 JDK (Amazon Corretto 8, a TCK-certified OpenJDK 8)
#   groovy/           the runtime Groovy scripts (loaded from disk, not the jar)
#   run.sh / run.bat  a wrapper that points JAVA_HOME at ./runtime
# and a Docker build context on amazoncorretto:8 (there the base image supplies
# Java, so no bundled runtime is needed).
#
# This script operates on an ALREADY-BUILT dist tree -- it does not build the app
# -- so it runs after `sbt dist` (see create_prod_dist.sh), in CI right after the
# dist step, or by hand to re-bundle without a rebuild.
#
# One build host produces EVERY platform zip: only the JDK differs per target and
# we download that per target; the app jar is platform-independent. Corretto is
# used everywhere (all four zips + the Docker base) so every artifact runs the
# same certified Java 8. Corretto publishes stable "latest" URLs plus a SHA-256
# we verify.
#
# Usage:  scripts/bundle-runtime.sh <dist-root> [version]
#   <dist-root>  unpacked dist dir (contains bin/ lib/ conf/ share/)
#   [version]    e.g. v2.5.0 (default: derived from the <dist-root> basename)
#
# Env knobs:
#   BUNDLE_PLATFORMS  space-separated targets (default: linux-x64 windows-x64 mac-x64 mac-aarch64)
#   GROOVY_SRC        dir of runtime *.groovy scripts (default: ./groovy)
#   DIST_OUT          output dir for the zips (default: ./dist)
#   RUNTIME_CACHE     JDK download cache, reused across runs (default: ./.cache/runtimes)
#   BUILD_DOCKER=1    also run `docker build` if docker is on PATH
#
# NOTE: the linux/mac JDKs contain symlinks; build those bundles on Linux/macOS
# (i.e. in CI). A Windows/Git-Bash host may not preserve them. The windows-x64
# bundle and the Docker context build fine anywhere.
set -euo pipefail

dist_root="${1:?usage: bundle-runtime.sh <dist-root> [version]}"
[ -d "$dist_root/lib" ] || { echo "ERROR: '$dist_root' is not a dist tree (missing lib/)." >&2; exit 1; }
breadboard_version="${2:-$(basename "$dist_root" | sed 's/^breadboard-//')}"

BUNDLE_PLATFORMS="${BUNDLE_PLATFORMS:-linux-x64 windows-x64 mac-x64 mac-aarch64}"
GROOVY_SRC="${GROOVY_SRC:-groovy}"
DIST_OUT="${DIST_OUT:-dist}"
RUNTIME_CACHE="${RUNTIME_CACHE:-.cache/runtimes}"
CORRETTO_LATEST="https://corretto.aws/downloads/latest"
CORRETTO_SHA="https://corretto.aws/downloads/latest_sha256"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

ls "$GROOVY_SRC"/*.groovy >/dev/null 2>&1 || { echo "ERROR: no groovy scripts in ${GROOVY_SRC}/" >&2; exit 1; }
mkdir -p "$DIST_OUT" "$RUNTIME_CACHE"

# Map a bundle target to its Corretto 8 JDK artifact filename.
corretto_artifact() {
  case "$1" in
    linux-x64)   echo "amazon-corretto-8-x64-linux-jdk.tar.gz" ;;
    windows-x64) echo "amazon-corretto-8-x64-windows-jdk.zip" ;;
    mac-x64)     echo "amazon-corretto-8-x64-macos-jdk.tar.gz" ;;
    mac-aarch64) echo "amazon-corretto-8-aarch64-macos-jdk.tar.gz" ;;
    *) echo "ERROR: unknown bundle target: $1" >&2; return 1 ;;
  esac
}

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}';
  else shasum -a 256 "$1" | awk '{print $1}'; fi
}

# Download + SHA-256-verify + extract + normalize a target's JDK into
# $RUNTIME_CACHE/<target>/ such that <that dir>/bin/java(.exe) exists. macOS
# tarballs unpack to `.../Contents/Home`, and every JDK 8 also has a nested
# jre/bin/java -- so we pick the SHALLOWEST bin/java and treat its grandparent
# as JAVA_HOME. Prints the normalized dir on stdout; cached across runs.
fetch_runtime() {
  local target="$1" dest="$RUNTIME_CACHE/$1"
  if [ -x "$dest/bin/java" ] || [ -f "$dest/bin/java.exe" ]; then echo "$dest"; return 0; fi
  local artifact; artifact="$(corretto_artifact "$target")" || return 1
  # Use a RELATIVE work dir under target/: native Windows tools (Git-for-Windows
  # curl) can't write to a POSIX /tmp path, but resolve relative paths fine.
  local work="target/.runtime-dl/$target"
  rm -rf "$work"; mkdir -p "$work/ex"
  echo "==> Fetching Corretto 8 JDK for ${target}: ${artifact}" >&2
  curl -fSL "$CORRETTO_LATEST/$artifact" -o "$work/$artifact"
  local want got; want="$(curl -fsSL "$CORRETTO_SHA/$artifact" | tr -d '[:space:]')"
  got="$(sha256_of "$work/$artifact")"
  if [ -n "$want" ] && [ "$want" != "$got" ]; then
    echo "ERROR: SHA-256 mismatch for ${artifact}: want ${want}, got ${got}" >&2
    rm -rf "$work"; return 1
  fi
  case "$artifact" in
    *.tar.gz) tar -xzf "$work/$artifact" -C "$work/ex" ;;
    *.zip)    unzip -q "$work/$artifact" -d "$work/ex" ;;
  esac
  # Pick the SHALLOWEST bin/java (fewest path components) in one awk pass -- no
  # `sort | head`, which can trip pipefail via SIGPIPE.
  local javabin
  javabin="$(find "$work/ex" -type f \( -path '*/bin/java' -o -path '*/bin/java.exe' \) \
             | awk -F/ 'min==0||NF<min{min=NF;line=$0} END{print line}')"
  [ -n "$javabin" ] || { echo "ERROR: no bin/java found in ${artifact}" >&2; rm -rf "$work"; return 1; }
  local home; home="$(dirname "$(dirname "$javabin")")"
  rm -rf "$dest"; mkdir -p "$(dirname "$dest")"; mv "$home" "$dest"
  rm -rf "$work"
  echo "$dest"
}

# Wrapper scripts (literal heredocs -- nothing is expanded at build time; the
# wrapper resolves its own dir and the bundled runtime at run time).
write_run_sh() {
  cat > "$1/run.sh" <<'RUNSH'
#!/bin/sh
# Breadboard launcher -- uses the bundled Java 8 runtime in ./runtime, so no
# host Java is required. Extra args are forwarded to bin/breadboard, e.g.:
#   ./run.sh -Dhttps.port=9443 -Dhttps.keyStore=conf/prod.keystore
# Provide secrets via the environment (read by conf/application-prod.conf), e.g.:
#   APPLICATION_SECRET=... AMT_ACCESS_KEY=... AMT_SECRET_KEY=... ./run.sh
here="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="$here/runtime"
[ -x "$here/bin/breadboard" ] || chmod u+x "$here/bin/breadboard" 2>/dev/null || true
exec "$here/bin/breadboard" \
  -Dconfig.file="$here/conf/application-prod.conf" \
  -J-XX:+CMSClassUnloadingEnabled \
  -J-XX:+UseConcMarkSweepGC \
  "$@"
RUNSH
  chmod +x "$1/run.sh"
}

# Two fixes to the stock bin/breadboard.bat so the bundle actually launches on
# Windows with the bundled Corretto (OpenJDK). The bash launcher needs neither.
# (TODO #11)
#
#  1) JDK detection: the .bat only accepts a JDK whose `java -version` output has
#     a line beginning with the token "Java" -- true for Oracle JDK but NOT for
#     OpenJDK/Corretto (lines start with "openjdk"/"OpenJDK"). The test runs in a
#     loop over `-version` output, so set the flag unconditionally: it still
#     fails correctly when java is absent (no output -> loop body never runs).
#
#  2) Classpath length: the .bat inlines every jar into one ~8KB `-cp` string,
#     which overflows cmd.exe's 8191-char command-line limit (worse from a deep
#     install path) -> "The input line is too long." Replace it with Java's
#     `lib\*` wildcard (supported since Java 6), which the JVM expands itself.
harden_windows_launcher() {
  local bat="$1/bin/breadboard.bat"
  [ -f "$bat" ] || { echo "ERROR: missing $bat" >&2; return 1; }
  sed -i 's/if %%~j==Java set JAVAINSTALLED=1/set JAVAINSTALLED=1/' "$bat"
  sed -i 's#^set "APP_CLASSPATH=.*"#set "APP_CLASSPATH=%APP_LIB_DIR%*"#' "$bat"
  grep -q '^[[:space:]]*set JAVAINSTALLED=1' "$bat" \
    || { echo "ERROR: failed to relax JDK detection in $bat" >&2; return 1; }
  grep -q '^set "APP_CLASSPATH=%APP_LIB_DIR%\*"' "$bat" \
    || { echo "ERROR: failed to shorten classpath in $bat" >&2; return 1; }
}

write_run_bat() {
  cat > "$1/run.bat" <<'RUNBAT'
@echo off
setlocal
rem Breadboard launcher -- uses the bundled Java 8 runtime in .\runtime, so no
rem host Java is required. Extra args are forwarded to bin\breadboard.bat.
rem Provide secrets via the environment (read by conf\application-prod.conf).
set "HERE=%~dp0"
set "JAVA_HOME=%HERE%runtime"
set "JAVA_OPTS=-XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled"
set "BREADBOARD_OPTS=-Dconfig.file=%HERE%conf\application-prod.conf"
call "%HERE%bin\breadboard.bat" %*
endlocal
RUNBAT
}

# Materialize the config the launcher points at. The dist ships conf files as
# `_application*.conf` reference copies (stock Play 2.2 packaging); create the
# un-prefixed names the launchers expect, without touching anything else.
materialize_conf() {
  local root="$1" c
  for c in application application-dev application-prod; do
    [ -f "$root/conf/_$c.conf" ] && [ ! -f "$root/conf/$c.conf" ] \
      && cp "$root/conf/_$c.conf" "$root/conf/$c.conf"
  done
  return 0
}

# Assemble and zip one self-contained per-platform bundle.
bundle_platform() {
  local target="$1"
  local runtime_dir; runtime_dir="$(fetch_runtime "$target")"
  [ -n "$runtime_dir" ] || { echo "ERROR: no runtime for ${target}" >&2; return 1; }
  local name="breadboard-${breadboard_version}"
  local stage="target/bundle-${target}" root="target/bundle-${target}/${name}"
  rm -rf "$stage"; mkdir -p "$root"
  cp -a "${dist_root}/." "$root/"                 # dist tree: bin/ lib/ conf/ share/ README
  cp -a "$runtime_dir" "$root/runtime"            # bundled JDK 8
  mkdir -p "$root/groovy"; cp "$GROOVY_SRC"/*.groovy "$root/groovy/"
  materialize_conf "$root"
  case "$target" in
    windows-*) write_run_bat "$root"; harden_windows_launcher "$root" ;;
    *)         write_run_sh  "$root" ;;
  esac
  local zip_out="${DIST_OUT}/${name}-${target}.zip"
  rm -f "$zip_out"
  # Zip from INSIDE the stage so the archive's top-level dir is breadboard-<ver>/
  # (same layout as the plain dist). Preserve the JDK's symlinks with -y for the
  # linux/mac bundles (built on Linux/macOS in CI); the Windows JDK has none, and
  # Git-for-Windows' zip lacks -y, so use a plain store there.
  local zipflags="-rqy"
  case "$target" in windows-*) zipflags="-rq" ;; esac
  ( cd "$stage" && zip $zipflags "${name}-tmp.zip" "$name" )
  mv "$stage/${name}-tmp.zip" "$zip_out"
  rm -rf "$stage"
  echo "Built bundle: ${zip_out}"
}

# Assemble the Docker build context (plain dist + groovy + Dockerfile). The
# amazoncorretto:8 base supplies Java, so no runtime/ is bundled here. Set
# BUILD_DOCKER=1 to also run `docker build` if docker is on PATH.
build_docker_context() {
  local ctx="target/docker"
  rm -rf "$ctx"; mkdir -p "$ctx/app" "$ctx/groovy"
  cp -a "${dist_root}/." "$ctx/app/"
  cp "$GROOVY_SRC"/*.groovy "$ctx/groovy/"
  cp "$REPO_ROOT/docker/Dockerfile" "$ctx/Dockerfile"
  [ -f "$REPO_ROOT/docker/.dockerignore" ] && cp "$REPO_ROOT/docker/.dockerignore" "$ctx/.dockerignore"
  echo "Docker build context ready: ${ctx}"
  echo "  docker build -t breadboard:${breadboard_version} ${ctx}"
  if [ "${BUILD_DOCKER:-0}" = "1" ] && command -v docker >/dev/null 2>&1; then
    docker build -t "breadboard:${breadboard_version}" "$ctx"
  fi
}

echo "==> Bundling Java 8 (Corretto) for: ${BUNDLE_PLATFORMS}"
for target in $BUNDLE_PLATFORMS; do
  bundle_platform "$target"
done
build_docker_context
echo "All bundles written to ${DIST_OUT}/"
