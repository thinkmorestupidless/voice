package voice.domain

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The catalogue in code and the refusals contract name the same rules with the same statuses. */
class RefusalCatalogueSuite extends munit.FunSuite:

  private val Row = """\| `([a-z0-9.-]+)` \| ([^|]+) \|.*""".r

  private lazy val contract: Map[String, String] =
    Files
      .readAllLines(Path.of("specs/001-voice-mvp/contracts/refusals.md"))
      .asScala
      .collect { case Row(rule, status) => rule -> status.trim }
      .toMap

  test("every rule in the contract is in the catalogue, and nothing else") {
    val inCode = Rules.all.map(_.name).toSet
    assertEquals(inCode -- contract.keySet, Set.empty[String], "in code, not in the contract")
    assertEquals(contract.keySet -- inCode, Set.empty[String], "in the contract, not in code")
  }

  test("every rule answers with the status the contract gives it") {
    for rule <- Rules.all do
      val expected = "^(\\d{3})".r.findFirstIn(contract(rule.name)).map(_.toInt).getOrElse(0)
      assertEquals(rule.status, expected, rule.name)
  }

  test("rule names are unique") {
    assertEquals(Rules.all.map(_.name).distinct.size, Rules.all.size)
  }

  test("a refusal survives the trip through a command error") {
    val refusal = Rules.ProposalNotHeld.refusal(
      "The conversation holds no such proposal.",
      "conversation" -> "cnv-1",
      "proposal"     -> "prp-2"
    )
    val back = Refusals.fromCommandError(Refusals.toCommandError(refusal))
    assertEquals(back, Some(refusal))
  }

  test("a sentence with brackets and colons in it survives too") {
    val refusal = Rules.ActionDateFuture.refusal(
      "Dated 2027-01-01: [later] than today.",
      "date" -> "2027-01-01"
    )
    assertEquals(Refusals.fromCommandError(Refusals.toCommandError(refusal)), Some(refusal))
  }
