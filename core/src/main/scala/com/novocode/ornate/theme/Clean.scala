package com.novocode.ornate.theme

import better.files._
import com.novocode.ornate.FileMatcher
import com.novocode.ornate.Page
import com.novocode.ornate.Site
import com.novocode.ornate.config.Global
import com.novocode.ornate.config.ConfigExtensionMethods.configExtensionMethods

/** A theme that cleans the target directory. */
class Clean(global: Global) extends Theme(global) {
  val ignore = new FileMatcher(global.userConfig.theme.config.getStringListOr("global.ignore"))

  def render(site: Site): Unit = ()

  override def build: Unit = {
    var files, dirs = 0
    ignore.filter(global.userConfig.targetDir).reverse.foreach {
      case f if f.isDirectory =>
        if(f.isEmpty) if(del(f)) dirs += 1
      case f if f.isRegularFile =>
        if(del(f)) files += 1
      case f =>
        logger.debug(s"Ignoring $f - not a file or directory")
    }
    logger.info(s"Deleted $files files and $dirs directories")
  }

  def del(f: File): Boolean = {
    logger.debug(s"Deleting $f")
    try {
      f.delete(swallowIOExceptions = false)
      true
    } catch {
      case ex: Exception =>
        logger.error(s"Error deleting $f - $ex")
        false
    }
  }
}
