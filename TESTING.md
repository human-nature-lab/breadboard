# Testing Plan for Breadboard

A practical plan for introducing unit tests to Breadboard's **Java** (Play
Framework) and **Groovy** (DSL script) sides. This document is a roadmap and
reference. Code blocks are examples to copy when you implement.

> ### ⚠️ Prerequisite: build/run on **Java 8**
> Play **2.2.0** + sbt **0.13** predate modern JVMs and **cannot run on Java 9+**.
> On a Java 17 JDK the sbt launcher fails before it even resolves dependencies
> (it can't parse the `17.0` version string). Install a **JDK 8** (e.g. Temurin 8)
> and point `JAVA_HOME` at it before running `sbt test`. The tests themselves are
> ordinary JUnit 4 and are JDK-version-agnostic — it is the Play/sbt toolchain
> that pins Java 8.

---

## 1. Current state

- **No tests exist.** `test/` contains only `.zip` import fixtures; `tests/timers.groovy`
  and `groovy/test.groovy` are empty stubs.
- `sbt test` runs, but finds nothing.

## 2. The stack (and why it constrains tooling)

| Concern | Reality | Consequence for testing |
|---|---|---|
| Build | Play **2.2.0**, sbt **0.13.0**, old `play.Project` / `project/Build.scala` | Test deps go in `Build.scala`'s `appDependencies` with `% "test"` scope, not a modern `build.sbt`. |
| Java | Java 7, 59 files in `app/`, Ebean ORM on H2, Akka actors | Use **JUnit 4** (bundled with Play) + **Mockito 1.x**. JUnit 5 / Mockito 5 require newer Java. |
| Groovy | **1.8.6**, scripts in `groovy/*.groovy` | Scripts are **read as text and `eval`'d at runtime** (see `ScriptBoard.resetEngine`, `app/models/ScriptBoard.java:126-159`) — they are *not* compiled into the app. Test them by **driving the script engine**, not by compiling them. |
| sbt | 0.13 compiles Java/Scala only | sbt will **not** compile `.groovy` test sources. This is the single most important constraint on the Groovy side — it pushes us toward Java-driven script tests (Section 4). |

> **Rule of thumb:** match the era of the pinned dependencies. Don't introduce a
> tool that needs a newer JVM or a newer Groovy than what's already on the
> classpath.

---

## 3. Java side

Test layout follows the Play convention: sources live in **`test/`**, mirroring
the package of the class under test, and run with **`sbt test`**. Tackle in three
tiers, easiest first.

### Tier 1 — Pure logic, no Play, no DB *(start here)*

These need nothing but JUnit. Fast, deterministic, zero ceremony.

**Best first test — `security/Signature.java`** (static, pure HMAC):

```java
// test/security/SignatureTest.java
package security;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SignatureTest {
  @Test
  public void getSignatureIsStableForKnownInput() throws Exception {
    // Pin against a value you compute once and trust; guards against
    // accidental algorithm/encoding changes.
    String sig = Signature.getSignature(
        "AWSECommerceService", "GetItems", "2009-01-01T12:00:00Z", "1234567890");
    assertEquals("<paste-the-computed-base64-here>", sig);
  }

  @Test(expected = java.security.SignatureException.class)
  public void wrapsFailuresAsSignatureException() throws Exception {
    Signature.calculateRFC2104HMAC("data", null); // null key -> failure path
  }
}
```

**Other Tier-1 candidates:**
- `exceptions/BreadboardException.java` — message/constructor behavior.
- `controllers/D3Utils.java` — any static graph→JSON helpers.
- `Step.toJson()` / `Data.toJson()` — `play.libs.Json.newObject()` works without a
  running app in Play 2.2, so the JSON shape is unit-testable directly.

### Tier 2 — Ebean models, with an in-memory DB

`Experiment`, `Step`, `Data`, `User`, etc. `extend play.db.ebean.Model`, so their
finders and persistence need a real Ebean + H2 instance. Play 2.2 provides
`FakeApplication`:

```java
// test/models/ExperimentTest.java
package models;

import org.junit.Test;
import static play.test.Helpers.*;
import static org.junit.Assert.*;

public class ExperimentTest {
  @Test
  public void findByNameReturnsSavedExperiment() {
    running(fakeApplication(inMemoryDatabase()), () -> {
      Experiment e = new Experiment();
      e.name = "Coloring";
      e.save();

      assertNotNull(Experiment.findByName("Coloring"));
      assertNull(Experiment.findByName("Missing"));
    });
  }
}
```

**Highest-value Tier-2 target: import/export round-tripping in `Experiment.java`.**
The `test/data/*.zip` fixtures (e.g. `v2.3.0 without images.zip`,
`v2.3.0-multi-language.zip`) are ready-made inputs. A round-trip test
(import a zip → export → re-import → assert the model is equivalent) exercises a
lot of real logic and would catch format regressions:

```java
@Test
public void importThenExportPreservesExperiment() {
  running(fakeApplication(inMemoryDatabase()), () -> {
    // 1. import test/data/"v2.3.0 without images.zip"
    // 2. export it to a temp zip
    // 3. re-import the exported zip
    // 4. assert steps/parameters/content/languages match the original
  });
}
```

> Use a fresh `inMemoryDatabase()` per test so Ebean state never leaks between
> tests. Avoid touching the on-disk `db/breadboard` H2 file.

### Tier 3 — Big coupled classes (`ScriptBoard`, etc.): refactor, then test

`ScriptBoard` is hard to test *whole*: it holds ~15 `private static` fields plus a
static `engine`, extends `UntypedActor`, and touches the filesystem and DB. The
static state means tests would leak into each other, and Akka adds lifecycle
overhead. **Don't test the actor as a unit.** Instead, extract the pure logic into
a stateless helper and test that:

Good extraction candidates (all currently buried in `ScriptBoard.java`):

| Logic | Location | Why it's worth testing |
|---|---|---|
| `initParam` type coercion (String → Integer / Decimal / Boolean / Text) | `ScriptBoard.java:681-715` | Real bug surface — `NumberFormatException` handling, default-string fallback. Pure if you pass in the parameter type + value. |
| `makeUniqueClientId` | `ScriptBoard.java:215-217` | Trivial, pure string composition. |
| `processScript` output formatting (the `GremlinGroovyPipeline` join, `"\n\n==>"` framing) | `ScriptBoard.java:753-763` | Output-shape contract worth pinning. |

Sketch:

```java
// Proposed: app/models/ScriptBoardSupport.java  (no static state)
public final class ScriptBoardSupport {
  public static Object coerceParam(String type, String value) { /* moved from initParam */ }
  public static String makeUniqueClientId(long expId, long instId, String clientId) { ... }
}
```

```java
// test/models/ScriptBoardSupportTest.java
@Test public void coercesIntegerParam() {
  assertEquals(7, ScriptBoardSupport.coerceParam("Integer", "7"));
}
@Test public void badIntegerFallsBackGracefully() {
  // assert it doesn't throw and yields the documented fallback
}
```

### Build wiring (Java) — `project/Build.scala` ✅ done

The Play 2.2 sbt plugin **already** puts JUnit 4 + `junit-interface` 0.10 on the
test classpath, so `sbt test` discovers the classes in `test/` out of the box. We
pin `junit-interface` explicitly (at the same 0.10) so the dependency is obvious,
and add the sequential-execution setting the shared-DB design needs:

```scala
val appDependencies = Seq(
  // ...existing entries unchanged...
  "com.novocode" % "junit-interface" % "0.10" % "test"
)
// shared FakeApplication/H2 -> tests must run sequentially
val main = play.Project(...).settings(parallelExecution in Test := false)
```

> **Mockito is intentionally *not* a dependency.** The script harness uses real
> objects with persistence disabled (`EventTracker.disable()`, a
> `RecordingGameListener` with no `ExperimentInstance`) rather than mocks, so no
> mocking framework is needed. Add it only if a future test truly requires it.

Run with `sbt test` (or `sbt "test-only security.SignatureTest"` for one class) —
**on a Java 8 JDK** (see the prerequisite callout at the top).

---

## 4. Groovy side

### The core challenge

The `groovy/*.groovy` files are **not classes the app compiles** — `ScriptBoard`
reads each file as a string, appends `;null;`, and `eval`s it into a JSR-223
`gremlin-groovy` engine, **in a fixed order**, after injecting a set of bindings.
See `ScriptBoard.java:126-159`. So a test must reproduce that environment.

**Load order** (`ScriptBoard.java:138-149`):

```
util → timer → graph → actions → step → test → events → chat → form → ready
```

**Bindings the scripts expect** (`ScriptBoard.java:127-136`, plus objects the
scripts themselves define):

| Binding | Type | Source |
|---|---|---|
| `r` | `java.util.Random` | injected |
| `results` | `Map` | injected |
| `eventTracker` | `EventTracker` | injected |
| `gameListener` | `GameListener` | injected |
| `events` | `EventBus` | injected |
| `c` | `ContentFetcher` | injected (per experiment) |
| `a` | `PlayerActions` | defined by `actions.groovy`, retrieved via `engine.get("a")` |
| `d` | `ObservableMap` | defined by `util.groovy` |
| `g` | graph | the gremlin-groovy graph |
| `stepFactory` | `StepFactory` | defined by `step.groovy` |

### Chosen approach: drive the script engine from JUnit (Java)

Because **sbt 0.13 won't compile `.groovy` test sources**, the lowest-friction
path is a **Java/JUnit test that loads and `eval`s the scripts** exactly the way
`ScriptBoard` does, but with mocks/stubs in place of the live runtime. This
compiles and runs under the *existing* build — no new toolchain.

```java
// test/groovy/GroovyScriptHarness.java  — shared fixture
package groovy;

import javax.script.*;
import java.io.File;
import java.util.*;
import org.apache.commons.io.FileUtils;
import org.mockito.Mockito;
import models.*;

public class GroovyScriptHarness {
  public final ScriptEngine engine;

  public GroovyScriptHarness(String... files) throws Exception {
    engine = new ScriptEngineManager().getEngineByName("gremlin-groovy");

    // Inject the same bindings ScriptBoard does, but test doubles where possible.
    engine.put("r", new Random(42));                 // SEED -> deterministic
    engine.put("results", new HashMap<>());
    engine.put("eventTracker", Mockito.mock(EventTracker.class));
    engine.put("gameListener", Mockito.mock(GameListener.class));
    engine.put("events", new EventBus());
    // engine.put("c", mock ContentFetcher) when testing content/form scripts

    for (String f : files) {
      String src = FileUtils.readFileToString(new File("groovy/" + f), "UTF-8") + ";null;";
      engine.eval(src);
    }
  }

  public Object get(String name) { return engine.get(name); }
  public Object eval(String script) throws ScriptException { return engine.eval(script); }
}
```

```java
// test/groovy/UtilScriptTest.java  — pure helpers in util.groovy
package groovy;

import org.junit.Test;
import static org.junit.Assert.*;

public class UtilScriptTest {
  @Test
  public void randomSubsetWithSeededRandomIsDeterministic() throws Exception {
    GroovyScriptHarness h = new GroovyScriptHarness("util.groovy");
    // randomSubset(List, int, Random) takes an injected Random -> testable
    Object out = h.eval("randomSubset([1,2,3,4,5], 3, new Random(1))");
    assertEquals(3, ((java.util.List) out).size());
  }

  @Test
  public void randomStringHasRequestedLength() throws Exception {
    GroovyScriptHarness h = new GroovyScriptHarness("util.groovy");
    Object s = h.eval("randomString(8)");
    assertEquals(8, s.toString().length());
  }

  @Test
  public void currencyFormatsRoundingUp() throws Exception {
    GroovyScriptHarness h = new GroovyScriptHarness("util.groovy");
    assertEquals("$0.01", h.eval("currency.format(0.001)").toString()); // RoundingMode.UP
  }
}
```

```java
// test/groovy/StepScriptTest.java  — Step / StepFactory from step.groovy
@Test
public void stepFactoryCreatesNamedSteps() throws Exception {
  GroovyScriptHarness h = new GroovyScriptHarness("util.groovy", "actions.groovy", "step.groovy");
  Object step = h.eval("stepFactory.createStep('intro')");
  assertNotNull(step);
  assertEquals("intro", h.eval("stepFactory.createStep('intro').name"));
}
```

### Good Groovy targets (high value, low coupling)

- **`util.groovy`** — `randomSubset(list, n, Random)` (already injectable),
  `randomString`, `currency` `DecimalFormat`, `BreadboardBase.fetchContent`.
- **`form.groovy`** — `FormBase.mapValueLabel` (normalizes value/label shapes),
  `getRandom`, `Block` construction.
- **`step.groovy`** — `Step.start()` branching on user actions, `StepFactory`.
- **`graph.groovy`** — build a real `TinkerGraph` wrapped in `BreadboardGraph`,
  add vertices/edges, assert the `neighbors` step and timer helpers. This is
  closer to an integration test of the DSL but is highly valuable.

### Two cautions

1. **Determinism.** `randomString` and the single-arg `randomSubset(list, n)` call
   `new Random()` internally with no seed — not directly testable. Prefer the
   three-arg `randomSubset(list, n, random)` (it already accepts an injected
   `Random`), and consider routing the others through an injectable `Random` too.
2. **Load-order drift.** Extract the script file list (`ScriptBoard.java:138-149`)
   into a shared constant (e.g. `ScriptBoard.SCRIPT_FILES`) and have the harness
   load from it, so tests can never silently diverge from production load order.

### Alternative (not chosen): Spock in a separate Gradle module

If native `given/when/then` Groovy specs become worthwhile, stand up a standalone
`groovy-tests/` Gradle project depending on the same jars (`groovy:1.8.6`,
`blueprints:2.5.0`) plus `org.spockframework:spock-core:0.7-groovy-1.8` (the
Groovy-1.8-compatible Spock line). This gives the best authoring experience but
adds a second build system. Deferred for now — the Java-driven harness above
covers the same scripts using the existing build.

---

## 5. Suggested sequence

1. **Day 1:** `SignatureTest` (Tier 1) + the `Build.scala` test deps. Proves the
   `sbt test` pipeline end-to-end.
2. **Week 1:** `Experiment` import/export round-trip using the `test/data/*.zip`
   fixtures; extract `initParam`/`makeUniqueClientId` into `ScriptBoardSupport`
   and test them.
3. **Week 2:** `GroovyScriptHarness` + `util`/`step`/`form` helper tests.
4. **Later, if needed:** graph-level DSL tests; revisit Spock/Gradle only if
   Groovy test volume grows.

## 6. Cross-cutting refactors that make the code testable

These aren't tests, but they remove the biggest obstacles to testing:

- **Drain static state out of `ScriptBoard`.** The `private static` fields make it
  a global singleton; pure logic extracted into `ScriptBoardSupport` sidesteps it.
- **Inject `Random`** wherever code calls `new Random()` internally, so behavior
  can be pinned with a seed.
- **Publish the script file list** as a shared constant (Section 4, caution 2).
- **Isolate filesystem/DB access** behind small methods so tests can stub them.

---

## 7. Quick reference

| Task | Command |
|---|---|
| **Toolchain JDK** | **Java 8** (Play 2.2/sbt 0.13 cannot run on Java 9+) |
| Run all tests | `sbt test` |
| Run one class | `sbt "test-only security.SignatureTest"` |
| Test sources live in | `test/` (mirrors package layout) |
| Java framework | JUnit 4 (no Mockito — harness uses real objects) |
| DB isolation | one shared FakeApplication + H2; tables truncated per test |
| Groovy strategy | JUnit test drives the `gremlin-groovy` script engine |
