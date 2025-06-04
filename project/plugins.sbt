// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository - updated to HTTPS
resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

// Add Maven Central repository with HTTPS
resolvers += "Maven Central" at "https://repo1.maven.org/maven2/"

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.0")
