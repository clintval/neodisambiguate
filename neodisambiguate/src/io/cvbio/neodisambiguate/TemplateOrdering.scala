package io.cvbio.neodisambiguate

import com.fulcrumgenomics.FgBioDef.FgBioEnum
import com.fulcrumgenomics.bam.Template
import enumeratum.EnumEntry
import htsjdk.samtools.SAMTag.{AS, NM}
import io.cvbio.neodisambiguate.metric.MetricPair

/** Trait that all enumeration values of type [[TemplateOrdering]] should extend. */
sealed trait TemplateOrdering extends EnumEntry with Ordering[Template]

/** Contains enumerations of template orderings. */
object TemplateOrdering extends FgBioEnum[TemplateOrdering] {

  /** Return all template orderings. */
  def values: scala.collection.immutable.IndexedSeq[TemplateOrdering] = findValues

  /** The value when [[TemplateOrdering]] is the original published ordering.
    *
    * A [[Template]] should be ordered before another [[Template]] if it has:
    *
    * 1. The highest single max alignment score across read one and read two, if defined.
    * 2. The highest single min alignment score across read one and read two, if defined.
    * 3. The lowest single min alignment edit distance across read one and read two, if defined.
    * 3. The lowest single max alignment edit distance across read one and read two, if defined.
    *
    * If neither template is clearly better, then the templates are equivalent.
    */
  case object ClassicOrdering extends TemplateOrdering {
    private def bestAlignmentScore(template: Template): MetricPair[Int]  = MetricPair[Int](template, AS)(_ max _)
    private def worstAlignmentScore(template: Template): MetricPair[Int] = MetricPair[Int](template, AS)(_ min _)
    private def bestNumMismatches(template: Template): MetricPair[Int]   = MetricPair[Int](template, NM)(_ min _)
    private def worstNumMismatches(template: Template): MetricPair[Int]  = MetricPair[Int](template, NM)(_ max _)

    /** Compare two templates using the original published algorithm. */
    override def compare(x: Template, y: Template): Int = {
      var compare = bestAlignmentScore(x).compare(bestAlignmentScore(y))
      if (compare == 0) worstAlignmentScore(x).compare(worstAlignmentScore(y))
      if (compare == 0) compare = -bestNumMismatches(x).compare(bestNumMismatches(y))   // Negate because less is better.
      if (compare == 0) compare = -worstNumMismatches(x).compare(worstNumMismatches(y)) // Negate because less is better.
      compare
    }
  }
}
