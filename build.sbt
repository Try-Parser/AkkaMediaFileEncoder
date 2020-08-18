lazy val Information = new {
	val name = "akka-media-file-encoder"
	val org = "com.callhandling"
	val scala = "2.13.1"

	val scalacOpt = Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint")
	val javacOpt = Seq("-Xlint:unchecked", "-Xling:deprecation")

	val akka = "2.6.8"
	val `akka-http` = "10.2.0"
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
		skip in publish := true
	).settings(settings)

lazy val mediaManageState = (project in file("media-manage-state"))
	.settings(
		name := "media_manage_state",
		settings
	).dependsOn(utils % "compile->compile;test->test")

lazy val mediaManagerApp = (project in file("media-manager-app"))
	.settings(
		name := "media_manager_app",
		settings
	).dependsOn(utils % "compile->compile;test->test")

lazy val mediaManagerService = (project in file("media-manager-service"))
	.settings(
		name := "media_manager_service",
		settings,
		libraryDependencies ++= httpDepend
	).dependsOn(utils % "compile->compile;test->test")

lazy val settings = Seq(
	crossTarget := baseDirectory.value / "target",
	scalacOptions := Information.scalacOpt,
	javacOptions := Information.javacOpt,
	scalaVersion in ThisBuild := Information.scala,
	artifactName := { (sv: ScalaVersion, module: ModuleID, artifact: Artifact) =>
		s"${artifact.name}.${artifact.extension}"
	}
)

lazy val httpDepend = Seq(
	"com.typesafe.akka" %% "akka-http" % Information.`akka-http`,
	"com.typesafe.akka" %% "akka-http-spray-json" % Information.akka
)
