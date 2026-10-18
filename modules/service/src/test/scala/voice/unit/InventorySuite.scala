package voice.unit

import voice.VoiceService

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/**
 * Registration is explicit, so the inventory is checkable: every component data-model.md names is
 * registered under that id, and nothing else is.
 */
class InventorySuite extends munit.FunSuite:

  private val Named =
    """(?:event sourced entity|key value entity|workflow|View|view|keyed view|timed action|agent|consumer) `([a-z0-9-]+)`""".r

  private lazy val documented: Set[String] =
    Files
      .readAllLines(Path.of("specs/001-voice-mvp/data-model.md"))
      .asScala
      .filter(l => l.startsWith("#") || l.startsWith("- **Component") || l.startsWith("| "))
      .flatMap(l => Named.findAllMatchIn(l).map(_.group(1)))
      .toSet

  private lazy val registered: Set[String] = VoiceService.own.map(_.componentId: String).toSet

  test("every registered component is in the data model") {
    assertEquals(registered -- documented, Set.empty[String])
  }

  test("every component in the data model is registered") {
    assertEquals(documented -- registered, Set.empty[String])
  }

  test("component ids are unique") {
    assertEquals(VoiceService.own.map(_.componentId: String).distinct.size, VoiceService.own.size)
  }
