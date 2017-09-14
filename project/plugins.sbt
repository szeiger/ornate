addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.3.7")

addSbtPlugin("com.eed3si9n"     % "sbt-buildinfo"        % "0.7.0")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

resolvers += Resolver.jcenterRepo 

//addSbtPlugin("me.lessis" % "bintray-sbt" % "0.5.1")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0-M1")
