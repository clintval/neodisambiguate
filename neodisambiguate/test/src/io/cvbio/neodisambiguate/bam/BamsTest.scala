package io.cvbio.neodisambiguate.bam

import com.fulcrumgenomics.bam.api.{SamOrder, SamSource}
import com.fulcrumgenomics.commons.io.PathUtil
import com.fulcrumgenomics.testing.SamBuilder
import io.cvbio.neodisambiguate.bam.Bams.ReadOrdinal.{Read1, Read2}
import io.cvbio.neodisambiguate.testing.{TemplateBuilder, UnitSpec}

class BamsTest extends UnitSpec {

  "Bams.TemplateUtil" should "provide easy access to all read tags across a read ordinal" in {
    import io.cvbio.neodisambiguate.bam.Bams.TemplateUtil

    val builder = new TemplateBuilder(name = "test")

    builder.addPrimaryPair(r1Attrs = Map("NM" -> 2), r2Attrs = Map("NM" -> 3))
    builder.addSecondaryPair(r1Attrs = Map("NM" -> 6), r2Attrs = Map("NM" -> 10))
    builder.addSupplementaryPair(r1Attrs = Map("NM" -> null), r2Attrs = Map("NM" -> null))
    builder.addSupplementaryPair(r1Attrs = Map("NM" -> 16))

    builder.template.tagValues[Int](Read1, tag = "NM").flatten should contain theSameElementsInOrderAs Seq(2, 6, 16)
    builder.template.tagValues[Int](Read2, tag = "NM").flatten should contain theSameElementsInOrderAs Seq(3, 10)
  }

  "Bams.templatesIterator" should "accept no SamSource and do nothing" in {
    val iterator = Bams.templatesIterator()
    iterator.hasNext shouldBe false
  }

  it should "accept a single valid SamSource with no alignments" in {
    val builder  = new SamBuilder(sort = Some(SamOrder.Queryname))
    val iterator = Bams.templatesIterator(builder.toSource)
    iterator.hasNext shouldBe false
  }

  it should "raise an exception when empty and next() is called" in {
    val builder  = new SamBuilder(sort = Some(SamOrder.Queryname))
    val iterator = Bams.templatesIterator(builder.toSource)
    an[NoSuchElementException] shouldBe thrownBy { iterator.next() }
  }

  it should "accept a single valid SamSource with more than zero alignments" in {
    val builder  = new SamBuilder(sort = Some(SamOrder.Queryname))
    builder.addPair(name = "pair1", start1 = 1, start2 = 2)
    val iterator = Bams.templatesIterator(builder.toSource)
    iterator.hasNext shouldBe true
    iterator.length shouldBe 1
  }

  it should "not require every SamSource to be queryname sorted" in {
    SamOrder.values.foreach { so =>
      val source1 = new SamBuilder(sort = Some(so)).toSource
      val source2 = new SamBuilder(sort = Some(so)).toSource
      val _ = noException shouldBe thrownBy { Bams.templatesIterator(source1, source2) }
    }
  }

  it should "require that template names are actually synchronized" in {
    // NB: Just because a SAM file says it is in queryname sort does not mean that the sort order of the templates is
    //     actually stable since different tools and locales will produce different orderings. See the following:
    //     https://github.com/samtools/hts-specs/pull/361
    //     https://twitter.com/clint_valentine/status/1138875477974634496
    val tools   = Seq("picard", "samtools")
    val sources = tools.map(tool => SamSource(PathUtil.pathTo(first = s"neodisambiguate/test/resources/$tool.queryname-sort.sam")))
    val caught  = intercept[IllegalArgumentException] { Bams.templatesIterator(sources: _*).toList }
    caught.getMessage should include("Templates with different names found!")
  }

  it should "require that all underlying SamSources have the same number of ordered templates" in {
    val builder1 = new SamBuilder(sort = Some(SamOrder.Queryname))
    val builder2 = new SamBuilder(sort = Some(SamOrder.Queryname))

    builder1.addPair(name = "pair1", start1 = 1, start2 = 2)
    builder1.addPair(name = "pair2", start1 = 1, start2 = 2)
    builder2.addPair(name = "pair1", start1 = 1, start2 = 2)

    val caught = intercept[IllegalStateException] { Bams.templatesIterator(builder1.toSource, builder2.toSource).toList }
    caught.getMessage should include("SAM sources do not have the same number of templates")
  }
}
