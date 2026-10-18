package voice.unit

import com.thinkmorestupidless.ankka.runtime.Observability
import voice.agents.{ConcernExtractionAgent, PrivacyFlaggingAgent, ResearchAgent}
import voice.features.*
import voice.support.CapturedLogs

/**
 * FR-060: one participant's whole walk, from a private message to an update in their inbox, through
 * the same service the features use; then no log line and no name a trace holds carries the
 * message, the proposal, a prompt, a contact detail or a special-category word.
 */
class LogHygieneSuite extends munit.FunSuite:

  override def munitTimeout = scala.concurrent.duration.Duration(240, "s")

  private val world = World()

  /** Contact details as a log might hold them. */
  private val Contact =
    """(?i)([a-z0-9._%+-]+@[a-z0-9-]+\.[a-z]{2,}\b|(\+44|\b0)\d{2,4}[ -]\d{3,4}[ -]?\d{3,4}\b)""".r

  /** Special-category words in the seed and the fixtures: health, disability, a child's school. */
  private val Special = Seq("wheelchair", "my daughter", "my son", "my child")

  test("a whole walk writes no personal text, prompt or contact detail to a log or a trace") {
    val before                   = CapturedLogs.size
    val (person, conv, proposal) = Flows.proposal(world, "Hygiene")
    val confirmed = world.ok(
      world.me(
        person,
        "POST",
        s"/conversations/$conv/proposals/${proposal("id").str}/confirm",
        Some(ujson.Obj())
      )
    )
    world.notes("Hygiene.concern") = confirmed.json("id").str
    val derivative = Sharing.uniqueDerivative()
    val issue = Sharing.published(
      world,
      "Hygiene",
      derivative,
      "Log hygiene walk (illustrative)",
      "A walk for the log check, titled so no other scenario's text matches it."
    )
    Followers.follow(world, "Hygiene", issue): Unit
    Followers.act(world, issue): Unit
    val deadline = System.currentTimeMillis() + 30_000
    while Followers.inbox(world, "Hygiene").isEmpty && System.currentTimeMillis() < deadline do
      Thread.sleep(200)
    assert(Followers.inbox(world, "Hygiene").nonEmpty, "the update did not arrive")

    val logs = CapturedLogs.all
    assert(CapturedLogs.size > before, "nothing was logged; the capture is not wired")

    val o      = Observability(Harness.kit.service.system)
    val traced = (0 until o.names.size).flatMap(o.names.nameOf)

    val privateTexts = Seq(Flows.BusMessage, proposal("summary").str, proposal("original").str)
    val prompts =
      Seq(ConcernExtractionAgent.prompt, PrivacyFlaggingAgent.prompt, ResearchAgent.prompt).map(
        _.trim.take(80)
      )
    for (where, lines) <- Seq("logs" -> logs, "traces" -> traced) do
      for line <- lines do
        for t <- privateTexts if t.length >= 12 do
          assert(!line.contains(t.take(60)), s"$where hold private text: $line")
        for p <- prompts do assert(!line.contains(p), s"$where hold a prompt: ${line.take(200)}")
        for w <- Special do
          assert(!line.toLowerCase.contains(w), s"$where hold '$w': ${line.take(200)}")
        assert(
          Contact.findFirstIn(line).isEmpty,
          s"$where hold what looks like a contact detail: ${line.take(200)}"
        )
  }
