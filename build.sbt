lazy val akkaHttpVersion = "10.1.3"
lazy val akkaVersion    = "2.5.14"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "org.nirmalya.experiments",
      scalaVersion    := "2.11.8"
    )),
    name := "akka-stream-blog",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.5.14",
      "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-xml"        % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream"          % akkaVersion,

      "net.debasishg" %% "redisclient" % "3.4",
      "org.json4s" % "json4s-native_2.11" % "3.5.2",
      "org.json4s" % "json4s-ext_2.11" % "3.5.2",
       "de.heikoseeberger" %% "akka-http-json4s" % "1.16.0",
       "com.fasterxml.jackson.core" % "jackson-core" % "2.8.9",
       "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.9",
       "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.8.9",


      "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-testkit"         % akkaVersion     % Test,
      "com.typesafe.akka" %% "akka-stream-testkit"  % akkaVersion     % Test,
      "org.scalatest"     %% "scalatest"            % "3.0.1"         % Test
    )
  )
