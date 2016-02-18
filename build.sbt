organization := "org.jiris"

name := """play-jsonpath"""

version := "1.1-snapshot"

scalaVersion := "2.11.7"

libraryDependencies += "com.typesafe.play" % "play-json_2.11" % "2.4.3"

libraryDependencies ++= Seq(
    "org.scalatest" % "scalatest_2.11" % "2.2.5" % "test",
    "org.scalatestplus" %% "play" % "1.4.0-M4" % "test"
)

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

homepage := Some(url("http://github.com/youriris/play-jsonpath"))

publishMavenStyle := true
//publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository")))

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}
