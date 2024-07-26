import $ivy.`com.lihaoyi::mill-contrib-scoverage:$MILL_VERSION`
import coursier.maven.MavenRepository
import mill._
import mill.api.JarManifest
import mill.contrib.scoverage.ScoverageModule
import mill.define.{Target, Task}
import mill.scalalib._
import mill.scalalib.publish._
import os._

import java.util.jar.Attributes.Name.{IMPLEMENTATION_VERSION => ImplementationVersion}

/** The official package version. */
private val packageVersion = "1.0.0"

/** All the packages we will exclude that come bundled with HTSJDK and Picard. */
private val htsjdkAndPicardExcludes = Seq("org.apache.ant", "gov.nih.nlm.ncbi", "org.testng", "com.google.cloud.genomics")

/** A base trait for all test targets. */
trait ScalaTest extends TestModule {
  override def ivyDeps = Agg(ivy"org.scalatest::scalatest::3.2.19".excludeOrg(organizations = "org.junit"))
  override def testFramework: Target[String] = T { "org.scalatest.tools.Framework" }
}

/** The neodisambiguate Scala package package. */
object neodisambiguate extends ScalaModule with PublishModule with ScoverageModule {
  object test extends ScalaTests with ScalaTest with ScoverageTests

  def scalaVersion     = "2.13.14"
  def scoverageVersion = "2.1.1"
  def publishVersion   = T { packageVersion }

  /** POM publishing settings for this package. */
  def pomSettings: Target[PomSettings] = PomSettings(
    description    = "Disambiguate reads that were mapped to multiple references.",
    organization   = "io.cvbio",
    url            = "https://github.com/clintval/neodisambiguate",
    licenses       = Seq(License.MIT),
    versionControl = VersionControl.github(owner = "clintval", repo = "neodisambiguate", tag = Some(packageVersion)),
    developers     = Seq(Developer(id = "clintval", name = "Clint Valentine", url = "https://github.com/clintval"))
  )

  /** The artifact name, fully resolved within the coordinate. */
  override def artifactName: T[String] = T { "neodisambiguate" }

  /** The JAR manifest. */
  override def manifest: T[JarManifest] = super.manifest().add(ImplementationVersion.toString -> packageVersion)

  /** Create a local JAR for this module. */
  def executable = T {
    val artifact = assembly()
    makeDir.all(pwd / "bin")

    println("Copying artifact to ./bin/neodisambiguate")
    copy(artifact.path, pwd / "bin" / "neodisambiguate", replaceExisting = true)
  }

  override def ivyDeps = Agg(
    ivy"com.fulcrumgenomics::commons::1.4.0",
    ivy"com.fulcrumgenomics::fgbio::2.3.0".excludeOrg(htsjdkAndPicardExcludes: _*),
    ivy"org.rogach::scallop::5.1.0",
    ivy"org.slf4j:slf4j-nop:1.7.6", // For peace and quiet: https://www.slf4j.org/codes.html#StaticLoggerBinder
  )

  /** All the repositories we want to pull from. */
  override def repositoriesTask: Task[Seq[coursier.Repository]] = T.task {
    super.repositoriesTask() ++ Seq(
      MavenRepository("https://oss.sonatype.org/content/repositories/public"),
      MavenRepository("https://oss.sonatype.org/content/repositories/snapshots"),
      MavenRepository("https://jcenter.bintray.com/"),
      MavenRepository("https://broadinstitute.jfrog.io/artifactory/libs-snapshot/"),
    )
  }

  /** Inject a shell script into the header of the assembled JAR file to make it auto-executable.
    *
    * @see https://skife.org/java/unix/2011/06/20/really_executable_jars.html
    */
  override def prependShellScript: T[String] = T {
    finalMainClassOpt().map { cls =>
      s"""#!/usr/bin/env sh
         |set -e
         |
         |PROP_OPTS=()
         |MEM_OPTS=()
         |PASS_ARGS=()
         |DEFAULT_MEM_OPTS=('-Xms512m' '-XX:+AggressiveHeap')
         |
         |for ARG in "$$@"; do
         |  case $$ARG in
         |    '-D'* | '-XX'*) PROP_OPTS+=("$$ARG");;
         |    '-Xm'*) MEM_OPTS+=("$$ARG");;
         |    *) PASS_ARGS+=("$$ARG");;
         |  esac
         |done
         |
         |if [ -z "$${_JAVA_OPTIONS}" ] && [ -z "$${JAVA_OPTS}" ] && [ -z "$${MEM_OPTS}" ]; then
         |  MEM_OPTS=("$${DEFAULT_MEM_OPTS[*]}");
         |fi
         |
         |# If not already set to some value, set MALLOC_ARENA_MAX to constrain the number of memory pools (arenas) used
         |# by glibc to a reasonable number. The default behaviour is to scale with the number of CPUs, which can cause
         |# VIRTUAL memory usage to be ~0.5GB per cpu core in the system, e.g. 32GB of a 64-core machine even when the
         |# heap and resident memory are only 1-4GB! See the following link for more discussion:
         |# https://www.ibm.com/developerworks/community/blogs/kevgrig/entry/linux_glibc_2_10_rhel_6_malloc_may_show_excessive_virtual_memory_usage?lang=en
         |if [ -z "$${MALLOC_ARENA_MAX}" ]; then MALLOC_ARENA_MAX=4; fi
         |
         |exec java $$JAVA_OPTS $${MEM_OPTS[*]} $${PROP_OPTS[*]} -cp "$$0" "$cls" $${PASS_ARGS[*]}
         |exit
       """.trim.stripLineEnd.stripMargin
    }.getOrElse("")
  }

  /** All Scala compiler options for this package. */
  override def scalacOptions: T[Seq[String]] = T {
    Seq(
      "-opt:inline:io.cvbio.**", // Turn on the inliner.
      "-opt-inline-from:io.cvbio.**", // Tells the inliner that it is allowed to inline things from these classes.
      "-Yopt-log-inline", "_", // Optional, logs the inliner activity so you know it is doing something.
      "-Yopt-inline-heuristics:at-inline-annotated", // Tells the inliner to use your `@inliner` tags.
      "-opt-warnings:at-inline-failed", // Tells you if methods marked with `@inline` cannot be inlined, so you can remove the tag.
      // The following are sourced from https://nathankleyn.com/2019/05/13/recommended-scalac-flags-for-2-13/
      "-deprecation", // Emit warning and location for usages of deprecated APIs.
      "-explaintypes", // Explain type errors in more detail.
      "-feature", // Emit warning and location for usages of features that should be imported explicitly.
      "-unchecked", // Enable additional warnings where generated code depends on assumptions.
      "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
      "-Xfatal-warnings", // Fail the compilation if there are any warnings.
      "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
      "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
      "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
      "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
      "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
      "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
      "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
      "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
      "-Xlint:option-implicit", // Option.apply used implicit view.
      "-Xlint:package-object-classes", // Class or object defined in package object.
      "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
      "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
      "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
      "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
      "-Ywarn-dead-code", // Warn when dead code is identified.
      "-Ywarn-extra-implicit", // Warn when more than one implicit parameter section is defined.
      "-Ywarn-numeric-widen", // Warn when numerics are widened.
      "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
      "-Ywarn-unused:imports", // Warn if an import selector is not referenced.
      "-Ywarn-unused:locals", // Warn if a local definition is unused.
      "-Ywarn-unused:params", // Warn if a value parameter is unused.
      "-Ywarn-value-discard", // Warn when non-Unit expression results are unused.
      "-Ywarn-unused:patvars", // Warn if a variable bound in a pattern is unused.
      "-Ywarn-unused:privates", // Warn if a private member is unused.
      "-Ybackend-parallelism", Math.min(Runtime.getRuntime.availableProcessors(), 8).toString, // Enable parallelization — scalac max is 16.
      "-Ycache-plugin-class-loader:last-modified", // Enables caching of classloaders for compiler plugins
      "-Ycache-macro-class-loader:last-modified", // and macro definitions. This can lead to performance improvements.
    )
  }
}
