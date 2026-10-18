package voice.unit

import com.thinkmorestupidless.ankka.testkit.{EventSourcedTestKit, KeyValueEntityTestKit}
import voice.application.interests.*
import voice.domain.*
import voice.domain.accountability.*
import voice.domain.interests.*
import voice.domain.issues.*

import java.time.{Instant, LocalDate}

/** Interests, notifications and the wording of updates, with no runtime (T086 to T088). */
class InterestsSuite extends munit.FunSuite:

  private val at = Instant.parse("2026-11-02T09:00:00Z")
  private def p  = Provenance(Actor.System("update-delivery"), at, "test", "c1")
  private def ruleOf(e: com.thinkmorestupidless.ankka.core.CommandError) =
    Refusals.fromCommandError(e).map(_.rule)

  test("an interest is the participant's own, follows once and stops without being deleted") {
    val kit = KeyValueEntityTestKit.of(InterestEntity, "prt-a:iss-1")
    assertEquals(
      ruleOf(
        kit.call(InterestEntity.follow)(Follow("prt-b", "iss-1", Delivery.InboxImmediate, at)).error
      ),
      Some("interest.not-owned")
    )
    kit.call(InterestEntity.follow)(Follow("prt-a", "iss-1", Delivery.InboxImmediate, at))
    kit.call(InterestEntity.follow)(
      Follow("prt-a", "iss-1", Delivery.InboxImmediate, at.plusSeconds(60))
    )
    assertEquals(kit.currentState.since, at, "following again keeps the first time")
    assert(kit.currentState.selects(at))
    assert(
      !kit.currentState.selects(at.minusSeconds(1)),
      "a development before the interest is not selected"
    )
    kit.call(InterestEntity.stop)(StopFollowing("prt-a", at.plusSeconds(120)))
    assert(kit.currentState.exists && !kit.currentState.active)
    assert(!kit.currentState.selects(at.plusSeconds(200)))
  }

  test("a delivery other than the inbox is refused") {
    assertEquals(
      Interest.checkDelivery("email").left.map(_.rule),
      Left("interest.delivery.invalid")
    )
    assertEquals(Interest.checkDelivery("inbox-immediate"), Right(Delivery.InboxImmediate))
  }

  test("an issue's follower list adds and removes each participant once") {
    val kit = KeyValueEntityTestKit.of(IssueFollowersEntity, "iss-1")
    kit.call(IssueFollowersEntity.add)(AddFollower("prt-a"))
    kit.call(IssueFollowersEntity.add)(AddFollower("prt-a"))
    kit.call(IssueFollowersEntity.add)(AddFollower("prt-b"))
    assertEquals(kit.currentState.participants, Vector("prt-a", "prt-b"))
    kit.call(IssueFollowersEntity.remove)(RemoveFollower("prt-a"))
    assertEquals(kit.currentState.participants, Vector("prt-b"))
  }

  private def prepare = PrepareNotification(
    "prt-a",
    "dev-1",
    "iss-1",
    Sealed.Erased("prt-a"),
    Sealed.Erased("prt-a"),
    Vector("/public/issues/iss-1#act-1"),
    p
  )

  test(
    "a second preparation for one follower and development answers the first (notification.duplicate)"
  ) {
    val kit   = EventSourcedTestKit.of(NotificationEntity, "prt-a:dev-1")
    val first = kit.call(NotificationEntity.prepare)(prepare)
    assert(first.persisted)
    val again = kit.call(NotificationEntity.prepare)(prepare.copy(links = Vector("/elsewhere")))
    assert(!again.persisted)
    assertEquals(again.replyValue.links, Vector("/public/issues/iss-1#act-1"))
  }

  test("a failed delivery is recorded with its attempts, then delivered once") {
    val kit = EventSourcedTestKit.of(NotificationEntity, "prt-a:dev-2")
    kit.call(NotificationEntity.prepare)(prepare.copy(developmentId = "dev-2"))
    assertEquals(
      ruleOf(kit.call(NotificationEntity.read)(ReadNotification("prt-a", p)).error),
      Some("notification.not-owned"),
      "not delivered yet"
    )
    kit.call(NotificationEntity.fail)(FailNotification("inbox unavailable", p))
    assertEquals(
      (kit.currentState.status, kit.currentState.attempts),
      (NotificationState.Failed, 1)
    )
    assert(kit.call(NotificationEntity.deliver)(DeliverNotification(p)).persisted)
    assert(
      !kit.call(NotificationEntity.deliver)(DeliverNotification(p)).persisted,
      "delivered once"
    )
    assert(
      !kit.call(NotificationEntity.fail)(FailNotification("late", p)).persisted,
      "a delivered update is not failed afterwards"
    )
    assertEquals(
      ruleOf(kit.call(NotificationEntity.read)(ReadNotification("prt-b", p)).error),
      Some("notification.not-owned")
    )
    assertEquals(
      kit.call(NotificationEntity.read)(ReadNotification("prt-a", p)).replyValue.status,
      NotificationState.Read
    )
  }

  // ── the wording ───────────────────────────────────────────────────────────

  private val action = ActionRecord(
    "act-1",
    "iss-1",
    None,
    "Cabinet member",
    "Transport team",
    LocalDate.parse("2026-11-02"),
    "Pupils need a route.",
    "Officers will cost a footpath.",
    ActionState.Proposed,
    "representative-1",
    at,
    "dev-1"
  )
  private val link = EvidenceLink(
    "evl-1",
    "src-1",
    Some(EvidenceRef("ev-1")),
    None,
    EvidenceState.Accepted,
    "Route 14 in 2026.",
    "The operator.",
    "One term only.",
    Vector.empty,
    at
  )
  private val decision = DecisionRecord(
    "dec-1",
    "iss-1",
    "Retender the route.",
    "Cabinet",
    Vector("Do nothing"),
    "Pupils need it.",
    "representative-1",
    at,
    "dev-3"
  )
  private val outcome = OutcomeRecord(
    "out-1",
    "iss-1",
    "act-1",
    "A costed design.",
    "A design, not a path.",
    false,
    "representative-1",
    at,
    "dev-4"
  )
  private val update =
    NarrativeUpdate("dev-5", "We met the operator today.", "representative-1", at, None)

  private val all = Seq(
    Facts.Action(action),
    Facts.Evidence(link),
    Facts.Decision(decision),
    Facts.Outcome(outcome, Some(action)),
    Facts.Update(update)
  )

  test("every kind is worded from its record, with its links and why, and never as solved") {
    for f <- all do
      val r = Renderer.render("iss-1", "School transport", f)
      assertEquals(Renderer.render("iss-1", "School transport", f), r, "deterministic")
      assert(r.links.nonEmpty && r.links.forall(_.startsWith("/public/issues/iss-1")), r.toString)
      assert(r.why.contains("You follow “School transport”"), r.why)
      val words = r.text.toLowerCase
      for strong <- Seq("solved", "honoured", "delivered", "fulfilled") do
        assert(
          !words.contains(strong) || words.contains(Renderer.NotSolved.toLowerCase),
          s"$strong in ${r.text}"
        )
  }

  test("an action's state is its label when prepared, and a proposed action is not shown as done") {
    val proposed = Renderer.render("iss-1", "T", Facts.Action(action)).text
    assert(proposed.contains("“proposed, not started”"), proposed)
    assert(!proposed.contains("completed"))
    assert(
      Renderer
        .render("iss-1", "T", Facts.Action(action.copy(stage = ActionState.Completed)))
        .text
        .contains("“completed”")
    )
  }

  test("an outcome says the problem is resolved only when its record says so") {
    assert(
      Renderer.render("iss-1", "T", Facts.Outcome(outcome, None)).text.endsWith(Renderer.NotSolved)
    )
    val resolved =
      Renderer.render("iss-1", "T", Facts.Outcome(outcome.copy(claimsResolved = true), None)).text
    assert(resolved.contains("states that the problem is resolved"), resolved)
  }

  test("a narrative update is quoted as published") {
    assert(
      Renderer
        .render("iss-1", "T", Facts.Update(update))
        .text
        .contains("“We met the operator today.”")
    )
  }

  test("a correction of an update is minor; any other development is material") {
    assertEquals(
      Development
        .of("dev-6", DevelopmentKind.UpdatePublished, "dev-6", at, minor = true)
        .materiality,
      Materiality.Minor
    )
    assertEquals(
      Development.of("dev-7", DevelopmentKind.ActionRecorded, "act-1", at).materiality,
      Materiality.Material
    )
    val s = IssueState.empty.copy(updates = Vector(update))
    assertEquals(
      Development.checkUpdate(s, "iss-1", " ", None).map(_.rule),
      Some("update.text.required")
    )
    assertEquals(
      Development.checkUpdate(s, "iss-1", "typo", Some("dev-9")).map(_.rule),
      Some("update.correction.not-held")
    )
    assertEquals(Development.checkUpdate(s, "iss-1", "typo", Some("dev-5")), None)
  }
