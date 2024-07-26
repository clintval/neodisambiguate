package io.cvbio.neodisambiguate

import com.fulcrumgenomics.commons.{CommonsDef => FgBioCommonsDef}
import htsjdk.samtools.util.FileExtensions

object CommonsDef extends FgBioCommonsDef {

  /** The extension of BAM files. */
  val BamExtension: FilenameSuffix = FileExtensions.BAM

  /** Text file extension. */
  val TextExtension: FilenameSuffix = ".txt"

  /** A String that represents a filename. */
  type Filename = String

  /** Represents a SAM tag. */
  type SamTag = String
}
