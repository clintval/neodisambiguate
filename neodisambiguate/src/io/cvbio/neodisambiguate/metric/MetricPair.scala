package io.cvbio.neodisambiguate.metric

import com.fulcrumgenomics.bam.Template
import io.cvbio.neodisambiguate.CommonsDef.SamTag
import io.cvbio.neodisambiguate.bam.Bams.ReadOrdinal.{Read1, Read2}
import io.cvbio.neodisambiguate.bam.Bams.TemplateUtil

/** A pair of metrics, both optional, that can be collected from a fragment, single-end read, or paired-end read. */
case class MetricPair[T: Ordering](read1: Option[T], read2: Option[T])
  extends Iterable[T]
  with Ordered[MetricPair[T]] {

  /** How to compare metric pairs. */
  override def compare(that: MetricPair[T]): Int = {
    Ordering.Tuple2(Ordering.Option, Ordering.Option).compare(
      (this.maxOption, this.minOption),
      (that.maxOption, that.minOption)
    )
  }

  /** An iterator over the value of the metric pair, if they are defined. */
  override def iterator: Iterator[T] = (read1 ++ read2).iterator
}

/** Companion object for [[MetricPair]]. */
object MetricPair {

  /** Build a [[MetricPair]] from a [[Template]]. A function is required to reduce the tag values to one canonical value. */
  def apply[T](template: Template, tag: SamTag)(fn: (T, T) => T)(implicit cmp: Ordering[T]): MetricPair[T] = {
    new MetricPair(
      read1 = template.tagValues[T](Read1, tag).flatten.reduceOption(fn(_, _)),
      read2 = template.tagValues[T](Read2, tag).flatten.reduceOption(fn(_, _))
    )
  }

  /** Build an empty [[MetricPair]]. */
  def empty[T](implicit cmp: Ordering[T]): MetricPair[T] = new MetricPair[T](read1 = None, read2 = None)
}
