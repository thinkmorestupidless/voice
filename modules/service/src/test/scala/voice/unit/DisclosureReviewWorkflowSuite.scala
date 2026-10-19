package voice.unit

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.agent.TestModelProvider
import com.thinkmorestupidless.ankka.core.{CommandError, EntityId}
import com.thinkmorestupidless.ankka.http.HttpServer
import com.thinkmorestupidless.ankka.testkit.{AnkkaTestKit, TimerProbe}
import voice.VoiceService
import voice.api.Problems
import voice.application.disclosure.*
import voice.application.issues.*
import voice.domain.*
import voice.domain.disclosure.*
import voice.sealing.Sealing
import voice.support.{SettableClock, Timers}

import java.time.Instant
import scala.concurrent.duration.DurationInt

/**
 * The review workflow (T053) against a scripted model: patterns and the model's flags recorded, a
 * flagged derivative refused without a second step, publication writing the copy and the link, a
 * rejection, a withdrawal leaving the Issue, and a model that cannot be understood flagging rather
 * than passing.
 */
class DisclosureReviewWorkflowSuite extends munit.FunSuite:

  override val munitTimeout = 240.seconds

  given JsonValueCodec[String] = Problems.codec[String]

  private val model = TestModelProvider()
  private val at    = Instant.parse("2026-11-02T09:00:00Z")

  private lazy val kit: AnkkaTestKit =
    val agents = VoiceService.agents(model)
    AnkkaTestKit.start(
      VoiceService.components(agents),
      VoiceService.extensions(
        agents,
        Timers.runtime(SettableClock(at), TimerProbe()),
        HttpServer.at("127.0.0.1", 0)()
      ),
      readyTimeout = 120.seconds
    )

  override def afterAll(): Unit = kit.stop()

  private def citizen(id: String) = Provenance(Actor.Participant(id), at, "test", s"citizen-$id")
  private def staff(correlation: String) =
    Provenance(Actor.Staff("reviewer-1"), at, "test", correlation)
  private def flow(id: String)         = kit.componentClient.forWorkflow(EntityId(id))
  private def contribution(id: String) = kit.componentClient.forEventSourcedEntity(EntityId(id))
  private def issue(id: String)        = kit.componentClient.forEventSourcedEntity(EntityId(id))

  private def ruleOf(f: => Any): Option[String] =
    try
      f: Unit
      None
    catch case e: Throwable => CommandError.from(e).flatMap(Refusals.fromCommandError).map(_.rule)

  private def until(id: String, status: ReviewStatus): ReviewState =
    kit.eventually(s"$id reaches $status", 60.seconds) {
      val s = flow(id).call(DisclosureReviewWorkflow.status).invoke()
      Option.when(s.status == status)(s)
    }

  /** A participant's contribution of `text`, submitted; answers its id once the check has run. */
  private def submitted(
      name: String,
      text: String,
      mode: DisclosureMode = DisclosureMode.DeIdentified
  ): String =
    val participant = s"prt-review-$name"
    val id          = s"ctb-review-$name"
    val sealing     = Sealing(kit.secrets)
    sealing.mintKey(participant)
    val value = sealing.seal(participant, DisclosurePurposes.Derivative, text)
    val grant = DisclosureGrant(
      mode,
      Modes.audience(mode),
      Modes.purpose(mode),
      value,
      Contribution.textHash(value),
      at
    )
    val name_ = Option.when(mode == DisclosureMode.PublicAttribution)(
      sealing.seal(participant, DisclosurePurposes.Attribution, "Sam")
    )
    contribution(id)
      .call(ContributionEntity.authorise)
      .invoke(Authorise(participant, "cnc-1", grant, name_, citizen(participant))): Unit
    contribution(id)
      .call(ContributionEntity.submit)
      .invoke(Submit(participant, citizen(participant))): Unit
    flow(id)
      .call(DisclosureReviewWorkflow.start)
      .invoke(StartReview(id, participant, citizen(participant))): Unit
    until(id, ReviewStatus.AwaitingReviewer): Unit
    id

  private val clean = ujson.write(ujson.Obj("flags" -> ujson.Arr(), "reasons" -> ujson.Arr()))

  test("a clean derivative is published into a new Issue with its copy and its link") {
    model.whenUserSays("Kingsway bus")(clean): Unit
    val id = submitted("clean", "The Kingsway bus was withdrawn in September.")
    assertEquals(contribution(id).call(ContributionEntity.get).invoke().flags, Vector.empty)
    flow(id)
      .call(DisclosureReviewWorkflow.approve)
      .invoke(
        ApproveReview(
          "reviewer-1",
          None,
          Some(NewIssue("School transport", "Buses to the academy")),
          staff("a1")
        )
      ): Unit
    val done = until(id, ReviewStatus.Published)
    val held = issue(done.issueId.get).call(IssueEntity.get).invoke()
    assertEquals(held.title, "School transport")
    assertEquals(
      held.contributions.map(c => (c.pub, c.text)),
      Vector(done.pub.get -> "The Kingsway bus was withdrawn in September.")
    )
    val link = kit.componentClient
      .forKeyValueEntity(EntityId(done.pub.get))
      .call(IssueLinkEntity.get)
      .invoke()
    assertEquals((link.contributionId, link.issueId), (id, done.issueId.get))
    assertEquals(
      contribution(id).call(ContributionEntity.get).invoke().state,
      ContributionState.Published
    )
  }

  test("a flagged derivative needs a separate second step, then is published") {
    model.whenUserSays("Orchard")(
      ujson.write(
        ujson.Obj("flags" -> ujson.Arr("SmallGroup"), "reasons" -> ujson.Arr("a very small group"))
      )
    ): Unit
    val id    = submitted("flagged", "My daughter is one of few pupils on the Orchard route.")
    val state = flow(id).call(DisclosureReviewWorkflow.status).invoke()
    assertEquals(state.flags.toSet, Set(PrivacyFlag.Minor, PrivacyFlag.SmallGroup))
    assertEquals(
      ruleOf(
        flow(id)
          .call(DisclosureReviewWorkflow.approve)
          .invoke(ApproveReview("reviewer-1", None, Some(NewIssue("Orchard", "")), staff("b1")))
      ),
      Some("contribution.second-step.required")
    )
    flow(id)
      .call(DisclosureReviewWorkflow.enhanced)
      .invoke(EnhancedReview("reviewer-1", "no child can be identified", staff("b2"))): Unit
    until(id, ReviewStatus.AwaitingReviewer): Unit
    assertEquals(
      ruleOf(
        flow(id)
          .call(DisclosureReviewWorkflow.approve)
          .invoke(ApproveReview("reviewer-1", None, Some(NewIssue("Orchard", "")), staff("b2")))
      ),
      Some("contribution.second-step.same-click")
    )
    flow(id)
      .call(DisclosureReviewWorkflow.approve)
      .invoke(ApproveReview("reviewer-1", None, Some(NewIssue("Orchard", "")), staff("b3"))): Unit
    until(id, ReviewStatus.Published): Unit
    assert(contribution(id).call(ContributionEntity.get).invoke().secondStep.isDefined)
  }

  test("a model answer that cannot be read flags the derivative instead of passing it") {
    model.whenUserSays("Ferndale")("not the reply shape"): Unit
    val id    = submitted("unreadable", "The Ferndale stop has no shelter.")
    val state = flow(id).call(DisclosureReviewWorkflow.status).invoke()
    assertEquals(state.flags, Vector(PrivacyFlag.Distinctive))
    assertEquals(state.reasons, Vector(DisclosureReviewWorkflow.Unavailable))
  }

  test("a rejection is recorded with its reason and publishes nothing") {
    model.whenUserSays("Station Road")(clean): Unit
    val id = submitted("rejected", "The lights at Station Road are broken.")
    assertEquals(
      ruleOf(
        flow(id)
          .call(DisclosureReviewWorkflow.reject)
          .invoke(RejectReview("reviewer-1", " ", staff("c1")))
      ),
      Some("contribution.reason.required")
    )
    flow(id)
      .call(DisclosureReviewWorkflow.reject)
      .invoke(RejectReview("reviewer-1", "it repeats a published contribution", staff("c2"))): Unit
    until(id, ReviewStatus.Rejected): Unit
    val held = contribution(id).call(ContributionEntity.get).invoke()
    assertEquals(
      (held.state, held.review.flatMap(_.reason), held.pub),
      (ContributionState.Rejected, Some("it repeats a published contribution"), None)
    )
  }

  test("a withdrawal after publication takes the copy out of the Issue and its counts") {
    model.whenUserSays("Market Square")(clean): Unit
    val id = submitted(
      "withdrawn",
      "Market Square has no step-free crossing.",
      DisclosureMode.PublicAttribution
    )
    flow(id)
      .call(DisclosureReviewWorkflow.approve)
      .invoke(ApproveReview("reviewer-1", None, Some(NewIssue("Crossings", "")), staff("d1"))): Unit
    val published = until(id, ReviewStatus.Published)
    val before    = issue(published.issueId.get).call(IssueEntity.get).invoke()
    assertEquals(
      (before.attributed.size, before.contributions.size, before.counts.contributions),
      (1, 0, 1)
    )
    flow(id)
      .call(DisclosureReviewWorkflow.withdraw)
      .invoke(WithdrawReview("prt-review-withdrawn", citizen("prt-review-withdrawn"))): Unit
    until(id, ReviewStatus.Withdrawn): Unit
    val after = issue(published.issueId.get).call(IssueEntity.get).invoke()
    assertEquals(
      (after.attributed.size, after.counts.contributions, after.counts.withdrawn),
      (0, 0, 1)
    )
    assertEquals(
      contribution(id).call(ContributionEntity.get).invoke().state,
      ContributionState.Withdrawn
    )
  }
