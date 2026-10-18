package voice.unit

import com.thinkmorestupidless.ankka.testkit.EventSourcedTestKit
import voice.agents.ResearchTools
import voice.application.accountability.*
import voice.application.reasoning.ReasoningClient
import voice.domain.*
import voice.domain.accountability.*
import voice.domain.issues.IssueState

import java.time.{Instant, LocalDate}

/**
 * Every refusal of "Investigation and action" in contracts/refusals.md, with no runtime (T078,
 * T079).
 */
class AccountabilityEntitiesSuite extends munit.FunSuite:

  private val at    = Instant.parse("2026-11-02T09:00:00Z")
  private val today = LocalDate.parse("2026-11-02")
  private def p     = Provenance(Actor.Staff("representative-1"), at, "test", "c1")
  private def ruleOf(e: com.thinkmorestupidless.ankka.core.CommandError) =
    Refusals.fromCommandError(e).map(_.rule)

  private def action(date: LocalDate = today, authority: String = "Cabinet member for transport") =
    ActionRecord(
      "",
      "iss-1",
      None,
      authority,
      "Transport team",
      date,
      "Pupils need a safe route.",
      "We asked officers to cost a lit footpath.",
      ActionState.Proposed,
      "representative-1",
      at,
      "dev-1"
    )

  test("an action needs every field, a date not in the future and not before the issue") {
    val kit    = EventSourcedTestKit.of(ActionEntity, "act-1")
    val opened = LocalDate.parse("2026-10-01")
    assertEquals(
      ruleOf(
        kit.call(ActionEntity.record)(RecordAction(action(authority = " "), today, opened, p)).error
      ),
      Some("action.fields.required")
    )
    assertEquals(
      ruleOf(
        kit
          .call(ActionEntity.record)(
            RecordAction(action(date = today.plusDays(1)), today, opened, p)
          )
          .error
      ),
      Some("action.date.future")
    )
    assertEquals(
      ruleOf(
        kit
          .call(ActionEntity.record)(
            RecordAction(action(date = opened.minusDays(1)), today, opened, p)
          )
          .error
      ),
      Some("action.date.before-issue")
    )
    val held = kit.call(ActionEntity.record)(RecordAction(action(), today, opened, p)).replyValue
    assertEquals((held.actionId, held.stage), ("act-1", ActionState.Proposed))
  }

  test("an action's state never goes back") {
    val kit = EventSourcedTestKit.of(ActionEntity, "act-2")
    kit.call(ActionEntity.record)(RecordAction(action(), today, today, p))
    assertEquals(
      kit.call(ActionEntity.changeStage)(ChangeStage(ActionState.Completed, p)).replyValue.stage,
      ActionState.Completed
    )
    assertEquals(
      ruleOf(kit.call(ActionEntity.changeStage)(ChangeStage(ActionState.Proposed, p)).error),
      Some("action.state.backwards")
    )
  }

  test("an outcome names an action on its issue, and resolves nothing unless it says so") {
    val kit = EventSourcedTestKit.of(OutcomeEntity, "out-1")
    val o = OutcomeRecord(
      "",
      "iss-1",
      "act-9",
      "Footpath use counted for a month",
      "One month, one path",
      false,
      "representative-1",
      at,
      "dev-2"
    )
    assertEquals(
      ruleOf(kit.call(OutcomeEntity.record)(RecordOutcome(o, Vector("act-1"), p)).error),
      Some("outcome.action.not-held")
    )
    val held = kit
      .call(OutcomeEntity.record)(RecordOutcome(o.copy(actionId = "act-1"), Vector("act-1"), p))
      .replyValue
    assert(!held.claimsResolved)
  }

  test("a suggested investigation takes no task until a representative opens it") {
    val kit = EventSourcedTestKit.of(InvestigationEntity, "inv-1")
    kit.call(InvestigationEntity.suggest)(
      SuggestInvestigation(
        "iss-1",
        "Was the route review complete?",
        Suggestion("inv-run", "two counts disagree", Vector("evl-1")),
        p
      )
    )
    val task = Task(
      "task-1",
      "Ask officers for the afternoon counts",
      "Transport team",
      today,
      TaskState.Open,
      at
    )
    assertEquals(
      ruleOf(kit.call(InvestigationEntity.addTask)(AddTask(task, p)).error),
      Some("investigation.suggested.not-approved")
    )
    assertEquals(
      kit
        .call(InvestigationEntity.open)(OpenInvestigation("iss-1", "", "representative-1", p))
        .replyValue
        .status,
      InvestigationState.Open
    )
    assertEquals(kit.call(InvestigationEntity.addTask)(AddTask(task, p)).replyValue.tasks.size, 1)
  }

  test("the research agent's tools only read, and are exactly the public read routes") {
    val tools = ResearchTools.over(
      _ => IssueState.empty,
      _ => None,
      new ReasoningClient(sys.error("not called"))
    )
    assertEquals(tools.map(_.name).toSet, ResearchTools.Names)
    assertEquals(
      ResearchTools.Names,
      Set("read_issue", "read_evidence", "read_claim", "read_question", "read_source")
    )
    assert(tools.forall(_.name.startsWith("read_")), tools.map(_.name).toString)
  }
