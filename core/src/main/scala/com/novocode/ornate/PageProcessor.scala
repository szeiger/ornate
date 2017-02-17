package com.novocode.ornate

abstract class PageProcessor extends (Page => Unit) {
  def runAt: Phase
}

abstract class Phase(val idx: Int)

object Phase {
  /** Replace nodes by attributed versions or different custom nodes without relying on external information. */
  case object Attribute extends Phase(1000)

  /** Include external content. Should not replace nodes because this would require reattributing. */
  case object Include extends Phase(2000)

  /** Expand variables, links, etc. */
  case object Expand extends Phase(3000)

  /** Perform visual post-processing of content. */
  case object Visual extends Phase(4000)

  /** Perform theme-specific work before highlighting, e.g. setting `noHighlight` flags. This phase should only
    * be used internally by themes and not by extensions. */
  case object PreHighlight extends Phase(4900)

  /** Perform code highlighting. No further modification to code nodes should be done at or after this phase. */
  case object Highlight extends Phase(5000)

  /** A phase for special use. Must not be used by Extension phases. */
  case object Special extends Phase(-1)
}
