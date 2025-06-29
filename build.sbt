import sbtcrossproject.CrossType
import sbtrelease.ReleaseStateTransformations._

publish / skip := true

def gitHash: String = sys.process.Process("git rev-parse HEAD").lineStream.head

val tagName = Def.setting {
  s"v${if (releaseUseGlobalVersion.value) (ThisBuild / version).value else version.value}"
}
val tagOrHash = Def.setting {
  if (isSnapshot.value) gitHash else tagName.value
}

val unusedWarnings = Seq("-Ywarn-unused:imports")

val Scala212 = "2.12.20"
val Scala213 = "2.13.16"
val Scala3 = "3.3.5"

lazy val commonSettings = Def.settings(
  scalaVersion := Scala212,
  ThisBuild / organization := "com.github.xuwei-k",
  crossScalaVersions := Seq(Scala212, Scala213, Scala3),
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-Xlint",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions"
  ),
  scalacOptions ++= unusedWarnings,
  releaseCrossBuild := true,
  (ThisBuild / publishTo) := sonatypePublishToBundle.value,
  Test / publishArtifact := false,
  releaseTagName := tagName.value,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    ReleaseStep(
      action = { state =>
        val extracted = Project extract state
        extracted.runAggregated(extracted.get(thisProjectRef) / (Global / PgpKeys.publishSigned), state)
      },
      enableCrossBuild = true
    ),
    releaseStepCommandAndRemaining("sonatypeBundleRelease"),
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
)

commonSettings

lazy val buildSettings = commonSettings ++ Seq(
  name := "scodec-msgpack",
  Global / scalaJSStage := FastOptStage,
  libraryDependencies += {
    if (scalaVersion.value.startsWith("3."))
      "org.scodec" %%% "scodec-core" % "2.3.2"
    else
      "org.scodec" %%% "scodec-core" % "1.11.11"
  },
  libraryDependencies ++= Seq(
    "org.scalatest" %%% "scalatest" % "3.2.19" % "test",
    "org.scalatestplus" %%% "scalacheck-1-18" % "3.2.19.0" % "test",
    "org.scalacheck" %%% "scalacheck" % "1.18.1" % "test"
  ),
  buildInfoKeys := Seq[BuildInfoKey](
    organization,
    name,
    version,
    scalaVersion,
    sbtVersion,
    licenses
  ),
  buildInfoPackage := "scodec.msgpack",
  buildInfoObject := "BuildInfoScodecMsgpack",
  credentials ++= PartialFunction
    .condOpt(sys.env.get("SONATYPE_USER") -> sys.env.get("SONATYPE_PASS")) { case (Some(user), Some(pass)) =>
      Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
    }
    .toList,
  homepage := Some(url("https://github.com/xuwei-k/scodec-msgpack")),
  licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),
  pomExtra :=
    <developers>
      <developer>
        <id>xuwei-k</id>
        <name>Kenji Yoshida</name>
        <url>https://github.com/xuwei-k</url>
      </developer>
    </developers>
    <scm>
      <url>git@github.com:xuwei-k/scodec-msgpack.git</url>
      <connection>scm:git:git@github.com:xuwei-k/scodec-msgpack.git</connection>
      <tag>{tagOrHash.value}</tag>
    </scm>,
  description := "yet another msgpack implementation",
  pomPostProcess := { node =>
    import scala.xml._
    import scala.xml.transform._
    def stripIf(f: Node => Boolean) =
      new RewriteRule {
        override def transform(n: Node) =
          if (f(n)) NodeSeq.Empty else n
      }
    val stripTestScope = stripIf { n => n.label == "dependency" && (n \ "scope").text == "test" }
    new RuleTransformer(stripTestScope).transform(node)(0)
  }
) ++ Seq(Compile, Test).flatMap(c => c / console / scalacOptions --= unusedWarnings)

lazy val msgpack = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("."))
  .settings(
    buildSettings: _*
  )
  .enablePlugins(
    BuildInfoPlugin
  )
  .jsSettings(
    scalacOptions += {
      val a = (LocalRootProject / baseDirectory).value.toURI.toString
      val g = "https://raw.githubusercontent.com/xuwei-k/scodec-msgpack/" + tagOrHash.value
      val key = CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) =>
          "-scalajs-mapSourceURI"
        case _ =>
          "-P:scalajs:mapSourceURI"
      }
      s"${key}:$a->$g/"
    }
  )
  .jvmSettings(
    Test / fork := true,
    libraryDependencies += "org.msgpack" % "msgpack-core" % "0.9.9" % "test"
  )

lazy val msgpackJVM = msgpack.jvm
lazy val msgpackJS = msgpack.js
lazy val msgpackNative = msgpack.native
