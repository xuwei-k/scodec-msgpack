import sbtcrossproject.{crossProject, CrossType}
import sbtrelease.ReleaseStateTransformations._

skip in publish := true

def gitHash: String = sys.process.Process("git rev-parse HEAD").lineStream.head

val tagName = Def.setting {
  s"v${if (releaseUseGlobalVersion.value) (version in ThisBuild).value else version.value}"
}
val tagOrHash = Def.setting {
  if (isSnapshot.value) gitHash else tagName.value
}

val unusedWarnings = Seq("-Ywarn-unused:imports")

val Scala212 = "2.12.12"

lazy val commonSettings = Def.settings(
  scalaVersion := Scala212,
  organization in ThisBuild := "com.github.xuwei-k",
  crossScalaVersions := Seq(Scala212, "2.13.4"),
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
  publishTo in ThisBuild := sonatypePublishToBundle.value,
  publishArtifact in Test := false,
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
        extracted.runAggregated(PgpKeys.publishSigned in Global in extracted.get(thisProjectRef), state)
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
  scalaJSStage in Global := FastOptStage,
  libraryDependencies ++= Seq(
    "org.scodec" %%% "scodec-core" % "1.11.7",
    "org.scalatest" %%% "scalatest" % "3.2.3" % "test",
    "org.scalatestplus" %%% "scalacheck-1-15" % "3.2.3.0" % "test",
    "org.scalacheck" %%% "scalacheck" % "1.15.1" % "test"
  ),
  buildInfoKeys := Seq[BuildInfoKey](
    organization,
    name,
    version,
    scalaVersion,
    sbtVersion,
    scalacOptions,
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
) ++ Seq(Compile, Test).flatMap(c => scalacOptions in (c, console) --= unusedWarnings)

lazy val msgpack = crossProject(JSPlatform, JVMPlatform)
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
      val a = (baseDirectory in LocalRootProject).value.toURI.toString
      val g = "https://raw.githubusercontent.com/xuwei-k/scodec-msgpack/" + tagOrHash.value
      s"-P:scalajs:mapSourceURI:$a->$g/"
    }
  )
  .jvmSettings(
    fork in Test := true,
    libraryDependencies += "org.msgpack" % "msgpack-core" % "0.8.21" % "test"
  )

lazy val msgpackJVM = msgpack.jvm
lazy val msgpackJS = msgpack.js
