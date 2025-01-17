package io.cvbio.neodisambiguate

import com.fulcrumgenomics.bam.Template
import com.fulcrumgenomics.bam.api.{SamOrder, SamSource}
import com.fulcrumgenomics.commons.CommonsDef._
import com.fulcrumgenomics.commons.io.{Io, PathUtil}
import com.fulcrumgenomics.testing.SamBuilder
import htsjdk.samtools.SAMSequenceRecord
import htsjdk.samtools.SAMTag.{AS, NM}
import io.cvbio.neodisambiguate.CommonsDef.BamExtension
import io.cvbio.neodisambiguate.DisambiguationStrategy.Classic
import io.cvbio.neodisambiguate.Disambiguate.NeodisambiguateConf
import io.cvbio.neodisambiguate.testing.{TemplateBuilder, UnitSpec}

class DisambiguateTest extends UnitSpec {

  "DisambiguationStrategy.ClassicDisambiguationStrategy" should "pick None when there is nothing to pick from" in {
    Classic.choose(Seq.empty) shouldBe empty
  }

  it should "pick None when all alignments are unmapped" in {
    val builder = new SamBuilder(sort = Some(SamOrder.Queryname))
    val pair1   = builder.addPair(name = "pair1", unmapped1 = true, unmapped2 = true)
    val pair2   = builder.addPair(name = "pair2", unmapped1 = true, unmapped2 = true)

    val templates = Seq(pair1, pair2).map(pair => Template(pair.iterator))

    Classic.choose(templates) shouldBe None
  }

  it should "pick None if there are multiple of the same highest minimum and maximum alignment scores" in {
    val builder = new SamBuilder(sort = Some(SamOrder.Queryname))
    val pair1   = builder.addPair(name = "pair1", attrs = Map(AS.toString -> 50))
    val pair2   = builder.addPair(name = "pair2", attrs = Map(AS.toString -> 50))
    val pair3   = builder.addPair(name = "pair3", attrs = Map(AS.toString -> 50))

    val templates = Seq(pair1, pair2, pair3).map(pair => Template(pair.iterator))

    Classic.choose(templates) shouldBe None
  }

  it should "pick the template with the highest maximum alignment score" in {
    val builder = new SamBuilder(sort = Some(SamOrder.Queryname))
    val pair1   = builder.addPair(name = "pair1", attrs = Map(AS.toString -> 22))
    val pair2   = builder.addPair(name = "pair2", attrs = Map(AS.toString -> 22))
    val pair3   = builder.addPair(name = "pair3", attrs = Map(AS.toString -> 50))

    val templates = Seq(pair1, pair2, pair3).map(pair => Template(pair.iterator))

    Classic.choose(templates).value.name shouldBe "pair3"
  }

  it should "pick the template with the highest minimum alignment score when multiple maximums exist" in {
    val builder1 = new TemplateBuilder("pair1")
    builder1.addPrimaryPair(Map(AS.toString -> 22), Map(AS.toString -> 4))
    val builder2 = new TemplateBuilder("pair2")
    builder2.addPrimaryPair(Map(AS.toString -> 22), Map(AS.toString -> 5))
    val builder3 = new TemplateBuilder("pair3")
    builder3.addPrimaryPair(Map(AS.toString -> 16), Map(AS.toString -> 19))

    val templates = Seq(builder1, builder2, builder3).map(_.template)

    Classic.choose(templates).value.name shouldBe "pair2"
  }

  it should "pick None if there are multiple of the same lowest minimum and maximum alignment edit distances" in {
    val builder = new SamBuilder(sort = Some(SamOrder.Queryname))
    val pair1   = builder.addPair(name = "pair1", attrs = Map(NM.toString -> 50))
    val pair2   = builder.addPair(name = "pair2", attrs = Map(NM.toString -> 50))
    val pair3   = builder.addPair(name = "pair3", attrs = Map(NM.toString -> 50))

    val templates = Seq(pair1, pair2, pair3).map(pair => Template(pair.iterator))

    Classic.choose(templates) shouldBe None
  }

  it should "pick the template with the lowest minimum alignment edit distance" in {
    val builder = new SamBuilder(sort = Some(SamOrder.Queryname))
    val pair1   = builder.addPair(name = "pair1", attrs = Map(NM.toString -> 7))
    val pair2   = builder.addPair(name = "pair2", attrs = Map(NM.toString -> 6))
    val pair3   = builder.addPair(name = "pair3", attrs = Map(NM.toString -> 8))

    val templates = Seq(pair1, pair2, pair3).map(pair => Template(pair.iterator))

    Classic.choose(templates).value.name shouldBe "pair2"
  }

  it should "pick the template with the lowest maximum alignment edit distance when multiple minimums exist" in {
    val builder = new SamBuilder(sort = Some(SamOrder.Queryname))
    val pair1   = builder.addPair(name = "pair1", attrs = Map(NM.toString -> 6))
    val pair2   = builder.addPair(name = "pair2", attrs = Map(NM.toString -> 6))
    val pair3   = builder.addPair(name = "pair3", attrs = Map(NM.toString -> 6))

    pair1.take(1).foreach(rec => { rec(NM.toString) = 75 })
    pair2.take(1).foreach(rec => { rec(NM.toString) = 50 })
    pair3.take(1).foreach(rec => { rec(NM.toString) = 25 })

    val templates = Seq(pair1, pair2, pair3).map(pair => Template(pair.iterator))

    Classic.choose(templates).value.name shouldBe "pair3"
  }

  "Disambiguate" should "run end-to-end on a single BAM, uselessly" in {
    val assembly: String = "hg38"
    val dir: DirPath     = Io.makeTempDir("disambiguate")
    dir.toFile.deleteOnExit()

    val builder = new SamBuilder(sort = Some(SamOrder.Queryname))
    val pair1   = builder.addPair(name = "pair1", attrs = Map(NM.toString -> 6), start1 = 2, start2 = 101)
    val pair2   = builder.addPair(name = "pair2", attrs = Map(NM.toString -> 6), start1 = 2, start2 = 101)
    val pair3   = builder.addPair(name = "pair3", attrs = Map(NM.toString -> 6), start1 = 2, start2 = 101)
    builder.header.getSequenceDictionary.getSequences.forEach { seq: SAMSequenceRecord  => val _ = seq.setAssembly(assembly) }

    val input  = builder.toTempFile(deleteOnExit = true)
    val prefix = PathUtil.pathTo(dir.toString, more = "insilico")

    val disambiguate = new Disambiguate(input = Seq(input), prefix = prefix, referenceNames = Seq("hg38"))
    disambiguate.execute()

    val source = SamSource(PathUtil.pathTo(Seq(prefix, assembly).mkString(".") + BamExtension))

    val _ = source.map(_.name).toSeq should contain theSameElementsInOrderAs (pair1 ++ pair2 ++ pair3).map(_.name)
  }

  it should "run end-to-end on a single-template two-reference example" in {
    val humanAssembly: String = "hg38"
    val mouseAssembly: String = "mm10"
    val dir: DirPath          = Io.makeTempDir("disambiguate")
    dir.toFile.deleteOnExit()

    val humanBuilder = new SamBuilder(sort = Some(SamOrder.Queryname))
    val mouseBuilder = new SamBuilder(sort = Some(SamOrder.Queryname))

    val _ = humanBuilder.addPair(name = "pair", attrs = Map(NM.toString -> 6, AS.toString -> 32), start1 = 2, start2 = 101)
    val mousePair = mouseBuilder.addPair(name = "pair", attrs = Map(NM.toString -> 2, AS.toString -> 32), start1 = 2, start2 = 101)

    humanBuilder.header.getSequenceDictionary.getSequences.forEach { seq: SAMSequenceRecord  => val _ = seq.setAssembly(humanAssembly) }
    mouseBuilder.header.getSequenceDictionary.getSequences.forEach { seq: SAMSequenceRecord  => val _ = seq.setAssembly(mouseAssembly) }

    val prefix = PathUtil.pathTo(dir.toString, more = "insilico")

    val mouseBam = mouseBuilder.toTempFile(deleteOnExit = true)
    val humanBam = humanBuilder.toTempFile(deleteOnExit = true)

    val disambiguate = new Disambiguate(input = Seq(mouseBam, humanBam), prefix = prefix)
    disambiguate.execute()

    val humanSource = SamSource(PathUtil.pathTo(Seq(prefix, humanAssembly).mkString(".") + BamExtension))
    val mouseSource = SamSource(PathUtil.pathTo(Seq(prefix, mouseAssembly).mkString(".") + BamExtension))

    humanSource.iterator.toSeq shouldBe empty
    mouseSource.map(_.name).toSeq should contain theSameElementsInOrderAs mousePair.map(_.name)

    Io.assertWritableDirectory(PathUtil.pathTo(dir.toString, more = "ambiguous-alignments")) // will raise exception if non-existent.
  }

  it should "write out ambiguous templates to ambiguous-specific BAM files" in {
    val humanAssembly: String = "hg38"
    val mouseAssembly: String = "mm10"
    val dir: DirPath          = Io.makeTempDir("disambiguate")
    //dir.toFile.deleteOnExit()

    val humanBuilder = new SamBuilder(sort = Some(SamOrder.Queryname))
    val mouseBuilder = new SamBuilder(sort = Some(SamOrder.Queryname))

    val humanPair = humanBuilder.addPair(name = "pair", attrs = Map(NM.toString -> 2, AS.toString -> 32), start1 = 2, start2 = 101)
    val mousePair = mouseBuilder.addPair(name = "pair", attrs = Map(NM.toString -> 2, AS.toString -> 32), start1 = 2, start2 = 101)

    humanBuilder.header.getSequenceDictionary.getSequences.forEach { seq: SAMSequenceRecord  => val _ = seq.setAssembly(humanAssembly) }
    mouseBuilder.header.getSequenceDictionary.getSequences.forEach { seq: SAMSequenceRecord  => val _ = seq.setAssembly(mouseAssembly) }

    val prefix = PathUtil.pathTo(dir.toString, more = "insilico")

    val humanBam = humanBuilder.toTempFile(deleteOnExit = false)
    val mouseBam = mouseBuilder.toTempFile(deleteOnExit = false)

    val disambiguate = new Disambiguate(input = Seq(mouseBam, humanBam), prefix = prefix)
    disambiguate.execute()

    val humanSource = SamSource(PathUtil.pathTo(Seq(prefix, humanAssembly).mkString(".") + BamExtension))
    val mouseSource = SamSource(PathUtil.pathTo(Seq(prefix, mouseAssembly).mkString(".") + BamExtension))

    humanSource.iterator.toSeq shouldBe empty
    mouseSource.iterator.toSeq shouldBe empty

    val ambiguousAlignments = PathUtil.pathTo(dir.toString, more = "ambiguous-alignments")

    Io.assertWritableDirectory(ambiguousAlignments) // will raise exception if non-existent.

    val ambiguousHumanSource = SamSource(ambiguousAlignments.resolve(PathUtil.replaceExtension(humanBam.getFileName, ".ambiguous" + BamExtension)))
    val ambiguousMouseSource = SamSource(ambiguousAlignments.resolve(PathUtil.replaceExtension(mouseBam.getFileName, ".ambiguous" + BamExtension)))

    ambiguousHumanSource.map(_.name).toSeq should contain theSameElementsInOrderAs humanPair.map(_.name)
    ambiguousMouseSource.map(_.name).toSeq should contain theSameElementsInOrderAs mousePair.map(_.name)
  }

  "NeoNeodisambiguate" should "run end-to-end via the CLI entrypoint" in {
    val humanAssembly: String = "hg38"
    val mouseAssembly: String = "mm10"
    val dir: DirPath          = Io.makeTempDir("disambiguate")
    dir.toFile.deleteOnExit()

    val humanBuilder = new SamBuilder(sort = Some(SamOrder.Queryname))
    val mouseBuilder = new SamBuilder(sort = Some(SamOrder.Queryname))

    val _ = humanBuilder.addPair(name = "pair", attrs = Map(NM.toString -> 6, AS.toString -> 32), start1 = 2, start2 = 101)
    val mousePair = mouseBuilder.addPair(name = "pair", attrs = Map(NM.toString -> 2, AS.toString -> 32), start1 = 2, start2 = 101)

    humanBuilder.header.getSequenceDictionary.getSequences.forEach { seq: SAMSequenceRecord  => val _ = seq.setAssembly(humanAssembly) }
    mouseBuilder.header.getSequenceDictionary.getSequences.forEach { seq: SAMSequenceRecord  => val _ = seq.setAssembly(mouseAssembly) }

    val prefix = PathUtil.pathTo(dir.toString, more = "insilico")

    val mouseBam = mouseBuilder.toTempFile(deleteOnExit = true)
    val humanBam = humanBuilder.toTempFile(deleteOnExit = true)

    val conf = new NeodisambiguateConf(args = Seq("--input", mouseBam.toString, humanBam.toString, "--output", prefix.toString))
    Disambiguate.main(Array.from(conf.args))

    val humanSource = SamSource(PathUtil.pathTo(Seq(prefix, humanAssembly).mkString(".") + BamExtension))
    val mouseSource = SamSource(PathUtil.pathTo(Seq(prefix, mouseAssembly).mkString(".") + BamExtension))

    humanSource.iterator.toSeq shouldBe empty
    mouseSource.map(_.name).toSeq should contain theSameElementsInOrderAs mousePair.map(_.name)

    Io.assertWritableDirectory(PathUtil.pathTo(dir.toString, more = "ambiguous-alignments")) // will raise exception if non-existent.
  }
}
