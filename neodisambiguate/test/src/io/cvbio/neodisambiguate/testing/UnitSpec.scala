package io.cvbio.neodisambiguate.testing

import com.fulcrumgenomics.commons.io.Io
import com.fulcrumgenomics.commons.util.CaptureSystemStreams
import io.cvbio.neodisambiguate.CommonsDef.TextExtension
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{OptionValues, TryValues}

import java.nio.file.Path

/** Base class for unit testing. */
trait UnitSpec extends AnyFlatSpec with Matchers with OptionValues with TryValues with CaptureSystemStreams {

  /** Make an arbitrary temporary file with the following permissions. */
  def tempFile(readable: Boolean = true, writable: Boolean = true, executable: Boolean = true): Path = {
    val path = Io.makeTempFile(prefix = this.getClass.getSimpleName, suffix = TextExtension)
    permissions(path, readable, writable, executable)
    path
  }

  /** Set permissions on the file underlying a file path. */
  private def permissions(path: Path, readable: Boolean, writable: Boolean, executable: Boolean):Path = {
    val file = path.toFile
    file.setReadable(readable)
    file.setWritable(writable)
    file.setExecutable(executable)
    path
  }
}
