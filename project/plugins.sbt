// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository - updated to HTTPS
resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

resolvers += "Typesafe Maven" at "https://repo.typesafe.com/typesafe/maven-releases/"

resolvers += "Maven Central" at "https://repo1.maven.org/maven2/"

resolvers += "scala-ivy-releases" at "https://scala.jfrog.io/artifactory/ivy-releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.6")
