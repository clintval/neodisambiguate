package io.cvbio.neodisambiguate

import com.fulcrumgenomics.bam.api.{SamOrder, SamSource, SamWriter}
import com.fulcrumgenomics.commons.CommonsDef._
import com.fulcrumgenomics.commons.io.{Io, PathUtil}
import com.fulcrumgenomics.commons.util.LazyLogging
import com.fulcrumgenomics.commons.util.SystemUtil.IntelCompressionLibrarySupported
import com.fulcrumgenomics.util.ProgressLogger
import com.intel.gkl.compression.{IntelDeflaterFactory, IntelInflaterFactory}
import htsjdk.samtools.ValidationStringency
import htsjdk.samtools.util.{BlockCompressedOutputStream, BlockGunzipper, IOUtil}
import io.cvbio.neodisambiguate.CommonsDef.{BamExtension, Filename}
import io.cvbio.neodisambiguate.Disambiguate.{AmbiguousOutputDirName, firstAssemblyName}
import io.cvbio.neodisambiguate.DisambiguationStrategy.Classic
import io.cvbio.neodisambiguate.bam.Bams.templatesIterator
import org.rogach.scallop.{ScallopOption => Opt, _}

import scala.util.Properties.lineSeparator

/** An executable class for disambiguating a set of BAM files for a single sample across multiple references. */
class Disambiguate(
  val input: Seq[PathToBam],
  val prefix: PathPrefix,
  val strategy: DisambiguationStrategy = Classic,
  val referenceNames: Seq[String] = Seq.empty,
) extends LazyLogging {

  /** Execute [[Disambiguate]] on all inputs. */
  def execute(): Unit = {
    Io.assertReadable(input)
    Seq(prefix.getParent, prefix.resolveSibling(AmbiguousOutputDirName)).foreach(Io.mkdirs)

    val progress           = ProgressLogger(logger, noun = "templates")
    val sources            = input.map(bam => SamSource(bam))
    val ambiguousWriters   = input.map(bam => ambiguousWriter(bam, prefix = prefix))
    val unambiguousWriters = input.zip(finalizedNames).map { case (bam, name) => unambiguousWriter(bam, prefix = prefix, name = name) }

    templatesIterator(sources: _*)
      .tapEach(_ => progress.record())
      .foreach { templates =>
        strategy.choose(templates).map(templates.indexOf) match {
          case Some(index) => unambiguousWriters(index).write(templates(index).allReads)
          case None => templates.zip(ambiguousWriters).foreach { case (template, writer) => writer.write(template.allReads) }
        }
      }

    (ambiguousWriters ++ unambiguousWriters).foreach(_.close())
  }

  /** Return the finalized reference names to use when writing out disambiguated BAMs. */
  private[neodisambiguate] def finalizedNames: Seq[String] = {
    val names = if (referenceNames.nonEmpty) referenceNames else input.flatMap(firstAssemblyName)
    require(names.length == input.length, s"Not all BAM have a reference name defined. Found: ${names.mkString(", ")}")
    require(names.distinct.length == names.length, s"No redundant reference names allowed. Found: ${names.mkString(", ")}")
    names
  }

  /** Return an ambiguous SAM Writer that will write to a path within <prefix> making use of the <input> filename. */
  private[neodisambiguate] def ambiguousWriter(input: PathToBam, prefix: PathPrefix): SamWriter = {
    val source   = SamSource(input)
    val header   = source.header.clone()
    val filename = PathUtil.replaceExtension(input, s".ambiguous$BamExtension").getFileName
    val path     = prefix.resolveSibling(AmbiguousOutputDirName).resolve(filename)
    yieldAndThen(SamWriter(path = path, header = header, sort = SamOrder(header)))(source.safelyClose())
  }

  /** Return an unambiguous SAM Writer to a path starting with <prefix> and containing <name> infix. */
  private[neodisambiguate] def unambiguousWriter(input: PathToBam, prefix: PathPrefix, name: String): SamWriter = {
    val source = SamSource(input)
    val header = source.header.clone()
    val path   = PathUtil.pathTo(prefix.toString + s".$name$BamExtension")
    yieldAndThen(SamWriter(path = path, header = header, sort = SamOrder(header)))(source.safelyClose())
  }
}

/** Companion object to [[Disambiguate]]. */
object Disambiguate {

  /** The directory in the output prefix that will hold ambiguous alignments. */
  val AmbiguousOutputDirName: Filename = "ambiguous-alignments"

  /** Return the first reference sequence assembly name from a SAM file's sequence dictionary. */
  private[neodisambiguate] def firstAssemblyName(input: PathToBam): Option[String] = {
    val source    = SamSource(input)
    val sequences = source.header.getSequenceDictionary.getSequences
    yieldAndThen(sequences.to(LazyList).headOption.flatMap(record => Option(record.getAssembly)))(source.safelyClose())
  }

  /** Command line configuration. */
  private class NeodisambiguateConf(args: Seq[String]) extends ScallopConf(args) {
    private val packageName: String = Option(this.getClass.getPackage.getImplementationTitle).getOrElse("neodisambiguate")
    private val version: String     = Option(this.getClass.getPackage.getImplementationVersion).getOrElse("UNKNOWN")
    version(s"$packageName $version\n")
    banner(
      s"""Disambiguate reads that were mapped to multiple references.
         |
         |Disambiguation of aligned reads is performed per-template and all information
         |across primary, secondary, and supplementary alignments is used as evidence.
         |Alignment disambiguation is commonly required when analyzing sequencing data
         |from transduction, transfection, transgenic, or xenographic (including patient
         |derived xenograft) experiments. This tool works by comparing various alignment
         |scores between a template that has been aligned to many references in order to
         |determine which reference is the most likely source.
         |
         |All templates which are positively assigned to a single source reference are
         |written to a reference-specific output BAM file. Any templates with ambiguous
         |reference assignment are written to an ambiguous input-specific output BAM
         |file. Only BAMs produced from the Burrows-Wheeler Aligner (bwa) or STAR are
         |currently supported.
         |
         |Input BAMs of arbitrary sort order are accepted, however, an internal sort to
         |queryname will be performed unless the BAM is already in queryname sort order.
         |All output BAM files will be written in the same sort order as the input BAM
         |files. Although paired-end reads will give the most discriminatory power for
         |disambiguation of short-read sequencing data, this tool accepts paired,
         |single-end (fragment), and mixed pairing input data.
         |
         |EXAMPLE:
         |
         |To disambiguate a sample aligned to human (A) and mouse (B):
         |
         |```
         |❯ neodisambiguate -i dna00001.A.bam dna00001.B.bam -p out/dna00001 -n hg38 mm10
         |
         |❯ tree out/
         |  out/
         |  ├── ambiguous-alignments/
         |  │  ├── dna00001.A.ambiguous.bai
         |  │  ├── dna00001.A.ambiguous.bam
         |  │  ├── dna00001.B.ambiguous.bai
         |  │  └── dna00001.B.ambiguous.bam
         |  ├── dna00001.hg38.bai
         |  ├── dna00001.hg38.bam
         |  ├── dna00001.mm10.bai
         |  └── dna00001.mm10.bam
         |```
         |
         |ARGUMENTS AND OPTIONS:
     """.stripMargin.stripLineEnd.trim
    )
    private val required = this.group("required")
    val input: Opt[List[PathToBam]] = opt(descr = "The BAMs to disambiguate", required = true, group = required)
    val output: Opt[PathPrefix]     = opt(descr = "The output file prefix (e.g. dir/sample_prefix)", required = true, group = required)

    private val optional = this.group("optional")
    val names: Opt[List[String]] = opt(descr = "The reference assembly names.\nDefaults to the first assembly in the BAM headers", default = Some(List.empty), group = optional)

    private val system = this.group("system")
    val asyncIo: Opt[Boolean] = opt(descr = "Use asynchronous I/O for SAM and BAM files", default = Some(false), group = system)
    val compression: Opt[Int] = opt(descr = "Default GZIP BAM compression level", default = Some(5), group = system)
    val tmpDir: Opt[DirPath]  = opt(descr = "Directory to use for temporary files", default = Some(PathUtil.pathTo(System.getProperty("java.io.tmpdir"))), group = system)

    footer(lineSeparator + "MIT License Copyright 2019, 2024 Clint Valentine")
    verify()
  }

  /** Run the tool neodisambiguate. */
  def main(args: Array[String]): Unit = {
    import com.fulcrumgenomics.util.Io

    val conf = new NeodisambiguateConf(args.toIndexedSeq)

    SamSource.DefaultUseAsyncIo = conf.asyncIo()
    SamWriter.DefaultUseAsyncIo = conf.asyncIo()

    SamWriter.DefaultCompressionLevel = conf.compression()
    BlockCompressedOutputStream.setDefaultCompressionLevel(conf.compression())
    IOUtil.setCompressionLevel(conf.compression())
    Io.compressionLevel = conf.compression()

    if (IntelCompressionLibrarySupported) {
      BlockCompressedOutputStream.setDefaultDeflaterFactory(new IntelDeflaterFactory)
      BlockGunzipper.setDefaultInflaterFactory(new IntelInflaterFactory)
    }

    Io.tmpDir = conf.tmpDir()
    System.setProperty("java.io.tmpdir", conf.tmpDir().toAbsolutePath.toString)

    SamSource.DefaultValidationStringency = ValidationStringency.LENIENT

    htsjdk.samtools.util.Log.setGlobalLogLevel(htsjdk.samtools.util.Log.LogLevel.WARNING)

    new Disambiguate(input = conf.input(), prefix = conf.output(), referenceNames = conf.names()).execute()
  }
}
