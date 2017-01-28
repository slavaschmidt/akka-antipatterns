name := "akka-antipatterns"

version := "1.0"

scalaVersion := "2.12.1"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.16",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.16",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.16" % "test",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "com.storm-enroute" %% "scalameter" % "0.8.2" % "test"
)

testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework")

logBuffered := false

parallelExecution in Test := false
