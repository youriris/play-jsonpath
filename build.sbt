organization := "com.youriris"

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

pomExtra := (
<licenses>
  <license>
    <name>Apache 2</name>
    <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
    <distribution>repo</distribution>
  </license>
</licenses>
<developers>
  <developer>
    <name>Sean Ahn</name>
    <email>seanahn@youriris.com</email>
    <organization>Iris</organization>
    <organizationUrl>http://www.youriris.com</organizationUrl>
  </developer>
</developers>
<scm>
  <connection>scm:git:git@github.com:seanahn/play-jsonpath.git</connection>
  <developerConnection>scm:git:git@github.com:seanahn/play-jsonpath.git</developerConnection>
  <url>git@github.com:seanahn/play-jsonpath.git</url>
</scm>
)

publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository")))
