package io.cvbio.neodisambiguate.bam

import com.fulcrumgenomics.FgBioDef.FgBioEnum
import com.fulcrumgenomics.bam.Bams.templateSortedIterator
import com.fulcrumgenomics.bam.Template
import com.fulcrumgenomics.bam.api.SamSource
import com.fulcrumgenomics.commons.CommonsDef.SafelyClosable
import com.fulcrumgenomics.commons.collection.SelfClosingIterator
import enumeratum.EnumEntry
import io.cvbio.neodisambiguate.CommonsDef.SamTag
import io.cvbio.neodisambiguate.bam.Bams.ReadOrdinal.{Read1, Read2}

/** Common methods for working with SAM/BAM files. */
object Bams {

  /** Implicit class that makes working with a [[Template]] easier. */
  implicit class TemplateUtil(private val template: Template) {

    /** Return the SAM tags across a specific read ordinal. */
    def tagValues[T](ordinal: ReadOrdinal, tag: SamTag): Seq[Option[T]] = {
      ordinal match {
        case Read1 => (template.r1 ++: template.r1Secondaries ++: template.r1Supplementals).map(_.get[T](tag))
        case Read2 => (template.r2 ++: template.r2Secondaries ++: template.r2Supplementals).map(_.get[T](tag))
      }
    }
  }

  /** Trait that all enumeration values of type [[ReadOrdinal]] should extend. */
  sealed trait ReadOrdinal extends EnumEntry with Product with Serializable

  /** Contains enumerations of read ordinals. */
  object ReadOrdinal extends FgBioEnum[ReadOrdinal] {

    /** Return all read ordinals. */
    def values: scala.collection.immutable.IndexedSeq[ReadOrdinal] = findValues

    /** The read ordinal for read one. */
    case object Read1 extends ReadOrdinal

    /** The read ordinal for read two. */
    case object Read2 extends ReadOrdinal
  }

  /** Collectively iterate through [[SamSource]] iterators and emit templates of the same query.
    *
    * Reads will be grouped and sorted into queryname order if they are not already. All [[SamSource]] must contain
    * the same templates by query name.
    *
    * @param sources the SAM sources to iterate over
    * @return a self-closing iterator over all templates in all SAM sources sorted by name
    */
  def templatesIterator(sources: SamSource*): SelfClosingIterator[Seq[Template]] = {
    val iterator: Iterator[Seq[Template]] = new Iterator[Seq[Template]] {

      /** The underlying template iterators. */
      private val iterators = sources.map(templateSortedIterator(_))

      /** Test all template iterators to see if they have another template to emit. */
      override def hasNext: Boolean = {
        val conditions = iterators.map(_.hasNext).distinct
        if (conditions.length > 1) throw new IllegalStateException("SAM sources do not have the same number of templates")
        conditions.nonEmpty && conditions.forall(_ == true)
      }

      /** Advance to the next sequence of templates. */
      override def next(): Seq[Template] = {
        require(hasNext, "next() called on empty iterator")
        val templates = iterators.map(_.next())
        require(
          templates.map(_.name).distinct.length <= 1,
          "Templates with different names found! This can only occur if your SAM sources are queryname sorted using"
            + " different implementations, such as with Picard tools versus Samtools. If you have encountered this"
            + " exception, then please alert the maintainer!"
        )
        templates
      }
    }

    new SelfClosingIterator(iterator, () => sources.foreach(_.safelyClose()))
  }
}
