name := """play-jsonpath"""

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies += "com.typesafe.play" % "play-json_2.11" % "2.4.3"

// Change this to another test framework if you prefer
libraryDependencies ++= Seq(
    //"org.scalatest" %% "scalatest" % "2.2.4" % "test"
    "org.scalatest" % "scalatest_2.11" % "2.2.5" % "test",
    "org.scalatestplus" %% "play" % "1.4.0-M4" % "test"
)

// Uncomment to use Akka
//libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.11"

