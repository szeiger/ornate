addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.1.1")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.1")

libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
