# Running

Ornate is available in [JCenter](https://bintray.com/bintray/jcenter) and [Maven Central](http://search.maven.org/). The sbt plugin is published to the standard [sbt plugin repository](https://bintray.com/sbt/sbt-plugin-releases).

## SBT Plugin

First you need to add Ornate's sbt plugin to `project/plugins.sbt`:

```scala expandVars=true
addSbtPlugin("com.novocode" % "sbt-ornate" % "{{version}}")
```

A minimum project definition in `build.sbt` which enables the plugin looks like this:

```scala src=../../plugin/src/sbt-test/ornate/simple/build.sbt
```

You can run Ornate from sbt with the `ornate` task. By default it looks for a config file `src/ornate.conf`, source and resources under `src/site`, and it builds the site to `target/site`. These settings can be changed directly through the sbt configuration:

```scala src=../../plugin/src/main/scala/com/novocode/ornate/sbtplugin/OrnatePlugin.scala#--doc-plugin
```

Only the location of the config file is required. The base / source / resource / target directories are set by default, in which case they override any settings made in the config file.

## Command Line

You need to get the Ornate core package and its dependencies in order to run Ornate from the command line:

```scala expandVars=true tab=sbt
libraryDependencies += "com.novocode" %% "ornate" % "{{version}}"
```

```xml expandVars=true tab=Maven
<dependency>
  <groupId>com.novocode</groupId>
  <artifactId>ornate_2.11</artifactId>
  <version>{{version}}</version>
</dependency>
```

> {.note}
> Note: Ornate requires Scala 2.11

The main class for the command line launcher is `com.novocode.ornate.Main`. The following arguments are supported:

- `[--base-dir=<path>]`: Source, resource and target dirs in the config are relative to the base directory. If not specified explicitly, this is the directory that contains the config file.
- `[-D<option>=<value>...]`: Configuration options can be overridden with `-D` arguments or through system properties.
- `<config-file>`: The path to the site config file, typically named `ornate.conf`.

All further [configuration](configuration.md) is done through the config file.

## Cleaning

Cleaning the target directory is performed with a special theme, `com.novocode.ornate.theme.Clean`. On the command line you can invoke it with `-Dglobal.theme=clean`. Excluded resources can be configured:

```yaml src=../../core/src/main/resources/ornate-reference.conf#--doc-clean-theme
```

When using the sbt plugin, the generated site is placed under `target/site` by default, so it can be deleted along with all other generated resources with sbt's standard `clean` task.
