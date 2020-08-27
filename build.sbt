lazy val Information = new {
	val name = "akka-media-file-encoder"
	val org = "com.callhandling"
	val scala = "2.13.1"

	val scalacOpt = Seq(
	  "-deprecation",
	  "-feature",
	  "-unchecked",
	  "-Xlint",
	  "-Ywarn-unused:imports",
	  "-encoding", "UTF-8"
	)
	val javacOpt = Seq(
		"-Xlint:unchecked",
		"-Xlint:deprecation"
	)

	val akka = "2.6.8"
	val `akka-http` = "10.2.0"
	val logback = "1.2.3"
}

lazy val `root` = (project in file("."))
	.settings(
		moduleName := "root",
		name := Information.name,
		organization := Information.org,
	).settings(
		publish := {},
		publishLocal := {},
		publishArtifact := false,
		skip in publish := true
	).settings(settings)
	.aggregate(
		utils,
		mediaManageState,
		mediaManagerService,
		mediaManagerApp
	)

lazy val utils = (project in file("utils"))
	.settings(
		name := "utils",
	).settings(
		publish := {},
		publishLocal := {},
		publishArtifact := false,
		skip in publish := true,
		libraryDependencies ++= actorShardTyped ++ reflect
	).settings(settings)

lazy val mediaManageState = (project in file("media-manage-state"))
	.settings(
		name := "media_manage_state",
		assemblyJarName in assembly := "state.jar",
		mainClass in assembly := Some("media.ServerState"),
		settings
	).dependsOn(utils % "compile->compile;test->test")

lazy val mediaManagerApp = (project in file("media-manager-app"))
	.settings(
		name := "media_manager_app",
		assemblyJarName in assembly := "app.jar",
		mainClass in assembly := Some("media.ServerApp"),
		settings
	).dependsOn(utils % "compile->compile;test->test")

lazy val mediaManagerService = (project in file("media-manager-service"))
	.settings(
		name := "media_manager_service",
		assemblyJarName in assembly := "service.jar",
		mainClass in assembly := Some("media.ServerService"),
		assemblyMergeStrategy in assembly := {
			case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
			case fileName if fileName.toLowerCase.endsWith(".conf") => MergeStrategy.concat
			case _                                                  => MergeStrategy.first
		},
		settings,
		libraryDependencies ++= httpDepend ++ actorShardTyped
	).dependsOn(utils % "compile->compile;test->test")

lazy val settings = Seq(
	crossTarget := baseDirectory.value / "target",
	scalacOptions := Information.scalacOpt,
	javacOptions := Information.javacOpt,
	scalaVersion in ThisBuild := Information.scala,
	// artifactName := { (sv: ScalaVersion, module: ModuleID, artifact: Artifact) =>
	// 	s"${artifact.name}.${artifact.extension}"
	// }
)

lazy val reflect = Seq(
	"org.scala-lang" % "scala-reflect" % Information.scala
)

lazy val actorShardTyped = Seq(
	"com.typesafe.akka" %% "akka-actor-typed" % Information.akka,
	"com.typesafe.akka" %% "akka-cluster-sharding-typed" % Information.akka,
	"ch.qos.logback" % "logback-classic" % Information.logback,
	"com.typesafe.akka" %% "akka-slf4j" % Information.akka,
)

lazy val httpDepend = Seq(
	// "ws.schild" % "jave-all-deps" % "2.7.3",
	"ws.schild" % "jave-core" % "3.0.0",
	"ws.schild" % "jave-nativebin-linux64" % "3.0.0",
	"com.typesafe.akka" %% "akka-stream" % Information.akka,
	"com.typesafe.akka" %% "akka-serialization-jackson" % Information.akka,
	"com.typesafe.akka" %% "akka-distributed-data" % Information.akka,
	"com.typesafe.akka" %% "akka-http" % Information.`akka-http`,
	"com.typesafe.akka" %% "akka-http-spray-json" % Information.`akka-http`,
)