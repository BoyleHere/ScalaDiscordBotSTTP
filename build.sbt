name := "chatops4s-discord"

version := "0.1.0"

scalaVersion := "2.13.14"

libraryDependencies ++= Seq(
  "com.softwaremill.sttp.client4" %% "core" % "4.0.0-M7",
  "com.softwaremill.sttp.client4" %% "circe" % "4.0.0-M7",
  "io.circe" %% "circe-generic" % "0.14.7",
  "io.circe" %% "circe-parser" % "0.14.7",
  "org.http4s" %% "http4s-ember-server" % "0.23.26",
  "org.http4s" %% "http4s-dsl" % "0.23.26",
  "org.http4s" %% "http4s-circe" % "0.23.26",
  "org.typelevel" %% "log4cats-slf4j" % "2.6.0",
  "io.github.cdimascio" % "dotenv-java" % "3.0.0",
  "org.bouncycastle" % "bcprov-jdk18on" % "1.77",
  "com.comcast" %% "ip4s-core" % "3.4.0",
  "org.java-websocket" % "Java-WebSocket" % "1.5.4"
)
