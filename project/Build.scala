import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

    val appName         = "breadboard"
    // Version comes from the $BREADBOARD_VERSION env var (CI sets it from the
    // pushed git tag, e.g. v2.5.0); defaults to v2.5.0 for local builds. This is
    // what `sbt dist` names its output after, and create_prod_dist.sh derives
    // the packaging version from that produced zip.
    val appVersion      = sys.env.getOrElse("BREADBOARD_VERSION", "v2.5.0")

    val appDependencies = Seq(
      javaCore,
      javaJdbc,
      javaEbean,
      "org.codehaus.groovy" % "groovy" % "1.8.6",
      "com.tinkerpop.blueprints" % "blueprints" % "2.5.0",
      "com.tinkerpop.blueprints" % "blueprints-core" % "2.5.0",
      "com.tinkerpop.blueprints" % "blueprints-graph-jung" % "2.5.0",
      "com.tinkerpop.gremlin" % "gremlin-groovy" % "2.5.0",
      "commons-io" % "commons-io" % "2.3",
      "org.apache.commons" % "commons-lang3" % "3.1",
      "org.apache.commons" % "commons-csv" % "1.5",
      "net.lingala.zip4j" % "zip4j" % "1.3.2",
      "commons-codec" % "commons-codec" % "1.7",
      "org.imgscalr" % "imgscalr-lib" % "4.2",
      "net.sf.jung" % "jung2" % "2.0.1",
      "org.mindrot" % "jbcrypt" % "0.3m",
      "com.google.code.gson" % "gson" % "2.8.2",
      "com.amazonaws" % "aws-java-sdk" % "1.11.328",
      // The Play 2.2 sbt-plugin already provides junit + junit-interface 0.10 on the test
      // classpath, so `sbt test` discovers the JUnit 4 tests in test/ without this line.
      // We pin it explicitly (at the same 0.10) to make the dependency obvious and guard
      // against future plugin changes -- same version means no extra download/eviction.
      "com.novocode" % "junit-interface" % "0.10" % "test"
    )

    val main = play.Project(appName, appVersion, appDependencies).settings(
      // The whole model/controller/websocket suite shares one FakeApplication +
      // in-memory H2 (see test/BaseTest.java), so test classes must not run in
      // parallel against the shared DB.
      parallelExecution in Test := false
    )

}
