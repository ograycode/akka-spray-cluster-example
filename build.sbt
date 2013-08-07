name := "My Project"
 
version := "1.0"
 
scalaVersion := "2.10.1"
 
resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "spray repo" at "http://nightlies.spray.io"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-contrib" % "2.2.0",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.0",
  "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
  "io.spray" % "spray-can" % "1.2-20130710")

atmosSettings