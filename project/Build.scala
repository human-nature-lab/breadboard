import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

    val appName         = "breadboard"
    val appVersion      = "v2.3"

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
      "commons-codec" % "commons-codec" % "1.7",
      "org.imgscalr" % "imgscalr-lib" % "4.2",
      "net.sf.jung" % "jung2" % "2.0.1",
      "org.mindrot" % "jbcrypt" % "0.3m",
      "com.google.code.gson" % "gson" % "2.8.2"
    )

    val main = play.Project(appName, appVersion, appDependencies).settings(
    )

}
