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
	  "-encoding", "UTF-8",
	  "-Xlog-reflective-calls"
	)
	val javacOpt = Seq(
		"-Xlint:unchecked",
		"-Xlint:deprecation"
	)

	val akka = "2.6.9"
	val `akka-http` = "10.2.0"
	val `akka-persistence` = "1.0.1"
	val `akka-projection` = "0.3"
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
		FDK,
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
		libraryDependencies ++= httpDepend ++ actorShardTyped ++ reflect ++ json
	).settings(settings)

lazy val FDK = (project in file("media-fdk"))
	.settings(
		name := "fdk"
	).settings(
		publish := {},
		publishLocal := {},
		publishArtifact := false,
		skip in publish := true,
		libraryDependencies ++= httpDepend ++ actorShardTyped ++ reflect ++ json
	).dependsOn(utils % "compile->compile;test->test")


lazy val mediaManageState = (project in file("media-manage-state"))
	.settings(
		name := "media_manage_state",
		assemblyJarName in assembly := "state.jar",
		mainClass in assembly := Some("media.ServerState"),
		assemblyMergeStrategy in assembly := {
			case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
			case fileName if fileName.toLowerCase.endsWith(".conf") => MergeStrategy.concat
			case _                                                  => MergeStrategy.first
		},
		settings,
		libraryDependencies ++= httpDepend ++ actorShardTyped ++ persistence
	).dependsOn(
		utils % "compile->compile;test->test",
		FDK % "compile->compile;test->test"
	)

lazy val mediaManagerApp = (project in file("media-manager-app"))
	.settings(
		name := "media_manager_app",
		assemblyJarName in assembly := "app.jar",
		mainClass in assembly := Some("media.ServerApp"),
		settings
	).dependsOn()

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
	).dependsOn(
		utils % "compile->compile;test->test",
		FDK % "compile->compile;test->test",
		mediaManageState % "compile->compile;test->test"
	)

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

lazy val json = Seq(
	"com.typesafe.akka" %% "akka-http-spray-json" % Information.`akka-http`
)

lazy val httpDepend = Seq(
	// "ws.schild" % "jave-all-deps" % "2.7.3",
	"ws.schild" % "jave-core" % "3.0.0",
	"ws.schild" % "jave-nativebin-linux64" % "3.0.0",
	"com.typesafe.akka" %% "akka-stream" % Information.akka,
	"com.typesafe.akka" %% "akka-serialization-jackson" % Information.akka,
	"com.typesafe.akka" %% "akka-distributed-data" % Information.akka,
	"com.typesafe.akka" %% "akka-http" % Information.`akka-http`,
) ++ json

lazy val persistence = Seq(
	"com.typesafe.akka" %% "akka-persistence-typed" % Information.akka,
	"com.typesafe.akka" %% "akka-persistence-query" % Information.akka,
	"com.typesafe.akka" %% "akka-persistence-cassandra" % Information.`akka-persistence`,
	"com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % Information.`akka-persistence`,
	"com.lightbend.akka" %% "akka-projection-eventsourced" % Information.`akka-projection`,
	"com.lightbend.akka" %% "akka-projection-cassandra" % Information.`akka-projection`
)