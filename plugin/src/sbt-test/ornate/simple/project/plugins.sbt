addSbtPlugin("com.novocode" % "sbt-ornate" % Option(System.getProperty("plugin.version")).getOrElse(
  throw new RuntimeException("System property 'plugin.version' must be set to Ornate's version")
))
