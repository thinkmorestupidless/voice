package voice.application.disclosure

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.agent.forAgent
import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.agents.{PrivacyFlaggingAgent, PrivacyInput}
import voice.api.Problems
import voice.application.Refuse
import voice.application.issues.*
import voice.domain.*
import voice.domain.disclosure.*
import voice.sealing.Sealing

import scala.concurrent.duration.DurationInt

/** Where a review has got to. `Published` waits, paused, for a withdrawal. */
enum ReviewStatus:
  case NotStarted, Checking, AwaitingReviewer, Publishing, Published, Rejecting, Rejected,
    Withdrawing, Withdrawn

final case class NewIssue(title: String, summary: String)

final case class ReviewState(
    contributionId: String,
    participantId: String,
    status: ReviewStatus,
    flags: Vector[PrivacyFlag],
    reasons: Vector[String],
    secondStep: Option[SecondStep],
    reviewer: Option[String],
    reason: Option[String],
    issueId: Option[String],
    newIssue: Option[NewIssue],
    pub: Option[String],
    provenance: Option[Provenance],
    submittedAt: Option[java.time.Instant] = None
)

final case class StartReview(contributionId: String, participantId: String, provenance: Provenance)
final case class EnhancedReview(reviewer: String, reason: String, provenance: Provenance)
final case class ApproveReview(
    reviewer: String,
    issueId: Option[String],
    newIssue: Option[NewIssue],
    provenance: Provenance
)
final case class RejectReview(reviewer: String, reason: String, provenance: Provenance)
final case class WithdrawReview(participantId: String, provenance: Provenance)

/** The purposes a disclosure's values are sealed under; a value opens only under its own. */
object DisclosurePurposes:
  val Derivative  = "contribution.derivative"
  val Attribution = "contribution.attribution"
  val IssueText   = "issue.copy.text"
  val IssueName   = "issue.copy.name"

/**
 * A shared derivative's review (FR-014 to FR-018), one per contribution, id = the contribution id
 * so a repeated submission addresses the running review (R7). `check` runs the deterministic
 * detectors and then the privacy-flagging agent (R6) and pauses for a reviewer. A flagged
 * derivative needs `enhanced` from an earlier request before `approve` (FR-016). `publish` writes
 * the copy to the Issue and the restricted link, then pauses again so a withdrawal can find it;
 * `withdraw` is accepted at any step before the end. The reviewer's rules are checked by the
 * commands, so a refusal reaches the reviewer at once; the contribution checks them again.
 */
final class DisclosureReviewWorkflow(context: WorkflowContext) extends Workflow[ReviewState]:
  import DisclosureReviewWorkflow.*

  given JsonValueCodec[String] = Problems.codec[String]

  def emptyState: ReviewState = ReviewState(
    "",
    "",
    ReviewStatus.NotStarted,
    Vector.empty,
    Vector.empty,
    None,
    None,
    None,
    None,
    None,
    None,
    None
  )

  private def refuse[R](r: Refusal): Effect[R] =
    val (m, c) = Refuse(r)
    effects.error(m, c)

  private def id = context.workflowId

  private def contribution =
    context.componentClient.forEventSourcedEntity(EntityId(currentState.contributionId))

  private def awaiting: Option[Refusal] =
    Option.when(currentState.status != ReviewStatus.AwaitingReviewer)(
      Rules.ContributionNotUnderReview.refusal(
        if currentState.status == ReviewStatus.Checking then "The privacy check is still running."
        else "This contribution is not under moderation.",
        "contribution" -> id
      )
    )

  // ── commands ───────────────────────────────────────────────────────────────

  def start(c: StartReview): Effect[ReviewState] =
    if currentState.status != ReviewStatus.NotStarted then effects.reply(currentState)
    else
      effects
        .updateState(
          emptyState.copy(
            contributionId = c.contributionId,
            participantId = c.participantId,
            status = ReviewStatus.Checking,
            provenance = Some(c.provenance),
            submittedAt = Some(c.provenance.at)
          )
        )
        .transitionTo(check.ref)
        .thenReplyState

  def enhanced(c: EnhancedReview): Effect[ReviewState] =
    awaiting.orElse(Contribution.checkReason(c.reason, id)) match
      case Some(r) => refuse(r)
      case None =>
        val step =
          SecondStep(c.reviewer, c.reason.trim, c.provenance.correlationId, c.provenance.at)
        effects
          .updateState(currentState.copy(secondStep = Some(step), provenance = Some(c.provenance)))
          .transitionTo(recordEnhanced.ref)
          .thenReplyState

  def approve(c: ApproveReview): Effect[ReviewState] =
    val refusal = awaiting
      .orElse(
        Option.when(currentState.flags.nonEmpty && currentState.secondStep.isEmpty)(
          Rules.ContributionSecondStepRequired.refusal(
            "This derivative is flagged for enhanced review; record the second step before approving it.",
            "contribution" -> id,
            "flags"        -> currentState.flags.map(_.toString).mkString(",")
          )
        )
      )
      .orElse(
        Option.when(currentState.secondStep.exists(_.correlationId == c.provenance.correlationId))(
          Rules.ContributionSecondStepSameClick.refusal(
            "The enhanced review and the approval must be separate decisions.",
            "contribution" -> id
          )
        )
      )
      .orElse(
        Option.when(c.issueId.isEmpty && c.newIssue.forall(_.title.trim.isEmpty))(
          Rules.ContributionIssueNotHeld.refusal(
            "Name the issue to publish into, or a new issue's title.",
            "contribution" -> id,
            "issue"        -> ""
          )
        )
      )
    refusal match
      case Some(r) => refuse(r)
      case None =>
        val at    = c.provenance.at
        val issue = c.issueId.getOrElse(Ids.mint(IdKind.Issue, at))
        effects
          .updateState(
            currentState.copy(
              status = ReviewStatus.Publishing,
              reviewer = Some(c.reviewer),
              issueId = Some(issue),
              newIssue = if c.issueId.isDefined then None else c.newIssue,
              pub = Some(Ids.mint(IdKind.PublicCopy, at)),
              provenance = Some(c.provenance)
            )
          )
          .transitionTo(publish.ref)
          .thenReplyState

  def reject(c: RejectReview): Effect[ReviewState] =
    awaiting.orElse(Contribution.checkReason(c.reason, id)) match
      case Some(r) => refuse(r)
      case None =>
        effects
          .updateState(
            currentState.copy(
              status = ReviewStatus.Rejecting,
              reviewer = Some(c.reviewer),
              reason = Some(c.reason.trim),
              provenance = Some(c.provenance)
            )
          )
          .transitionTo(rejectStep.ref)
          .thenReplyState

  /** Accepted while the review runs or the copy is published; otherwise the state, unchanged. */
  def withdraw(c: WithdrawReview): Effect[ReviewState] =
    if c.participantId != currentState.participantId then
      refuse(
        Rules.ContributionNotOwned.refusal("This contribution is not yours.", "contribution" -> id)
      )
    else
      currentState.status match
        case ReviewStatus.Checking | ReviewStatus.AwaitingReviewer | ReviewStatus.Published =>
          effects
            .updateState(
              currentState.copy(status = ReviewStatus.Withdrawing, provenance = Some(c.provenance))
            )
            .transitionTo(withdrawStep.ref)
            .thenReplyState
        case _ => effects.reply(currentState)

  def status: ReadOnlyEffect[ReviewState] = effects.reply(currentState)

  // ── steps ──────────────────────────────────────────────────────────────────

  private def provenance: Provenance =
    currentState.provenance.getOrElse(
      Provenance(Actor.System("disclosure-review"), java.time.Instant.EPOCH, id, id)
    )

  private def system: Provenance =
    provenance.copy(actor = Actor.System("disclosure-review"), causationId = s"workflow:$id")

  /**
   * Patterns first, then the model, which can only add; the flags are recorded on the contribution.
   */
  def checkStep: StepEffect =
    val sealing = Sealing(context.secrets)
    val held    = contribution.call(ContributionEntity.get).invoke()
    val text = held.derivative.map(sealing.openText(_, DisclosurePurposes.Derivative)).getOrElse("")
    val found = PrivacyDetectors.check(text)
    val model = context.componentClient
      .forAgent(SessionId(s"privacy-$id"))
      .call(PrivacyFlaggingAgent.check)
      .invoke(PrivacyInput(text))
    val judged = PrivacyCheck(
      model.flags.flatMap(f => PrivacyFlag.values.find(_.toString.equalsIgnoreCase(f.trim))),
      model.reasons.filter(_.trim.nonEmpty),
      Vector.empty
    )
    flagged(found.merge(judged))

  /** The model could not be asked or understood: enhanced review, never a silent pass (FR-016). */
  def checkUnavailableStep: StepEffect =
    val sealing = Sealing(context.secrets)
    val held    = contribution.call(ContributionEntity.get).invoke()
    val text = held.derivative.map(sealing.openText(_, DisclosurePurposes.Derivative)).getOrElse("")
    flagged(
      PrivacyDetectors
        .check(text)
        .merge(PrivacyCheck(Vector(PrivacyFlag.Distinctive), Vector(Unavailable), Vector.empty))
    )

  private def flagged(check: PrivacyCheck): StepEffect =
    contribution
      .call(ContributionEntity.recordFlags)
      .invoke(RecordFlags(check.flags, check.reasons, system)): Unit
    stepEffects
      .updateState(
        currentState.copy(
          status = ReviewStatus.AwaitingReviewer,
          flags = check.flags,
          reasons = check.reasons
        )
      )
      .thenPause()

  def recordEnhancedStep: StepEffect =
    currentState.secondStep.foreach { s =>
      contribution
        .call(ContributionEntity.recordEnhanced)
        .invoke(RecordEnhanced(s.reviewer, s.reason, provenance)): Unit
    }
    stepEffects.updateState(currentState.copy(status = ReviewStatus.AwaitingReviewer)).thenPause()

  /**
   * Issue first when it is new, then the contribution, the copy and the link; each is idempotent.
   */
  def publishStep: StepEffect =
    val sealing = Sealing(context.secrets)
    val issueId = currentState.issueId.getOrElse(throw IllegalStateException("no issue chosen"))
    val pub     = currentState.pub.getOrElse(throw IllegalStateException("no reference minted"))
    val issue   = context.componentClient.forEventSourcedEntity(EntityId(issueId))
    currentState.newIssue.foreach(n =>
      issue.call(IssueEntity.open).invoke(OpenIssue(n.title, n.summary, provenance)): Unit
    )
    val held = contribution
      .call(ContributionEntity.approve)
      .invoke(Approve(currentState.reviewer.getOrElse(""), issueId, pub, provenance))
    val subject = held.participantId
    val text = held.derivative.map(sealing.openText(_, DisclosurePurposes.Derivative)).getOrElse("")
    val copy = held.mode match
      case DisclosureMode.DeIdentified => Copy.Public(pub, text)
      case DisclosureMode.PublicAttribution =>
        val name =
          held.attribution.map(sealing.openText(_, DisclosurePurposes.Attribution)).getOrElse("")
        Copy.Attributed(
          pub,
          sealing.seal(subject, DisclosurePurposes.IssueText, text),
          sealing.seal(subject, DisclosurePurposes.IssueName, name)
        )
      case DisclosureMode.RepresentativeOnly =>
        Copy.Restricted(pub, sealing.seal(subject, DisclosurePurposes.IssueText, text))
      case DisclosureMode.Private =>
        throw IllegalStateException("a private contribution is not published")
    issue.call(IssueEntity.publish).invoke(PublishCopy(copy, provenance)): Unit
    context.componentClient
      .forKeyValueEntity(EntityId(pub))
      .call(IssueLinkEntity.write)
      .invoke(WriteLink(currentState.contributionId, subject, issueId, provenance)): Unit
    stepEffects
      .updateState(currentState.copy(status = ReviewStatus.Published, newIssue = None))
      .thenPause()

  def rejectStep_ : StepEffect =
    contribution
      .call(ContributionEntity.reject)
      .invoke(
        Reject(currentState.reviewer.getOrElse(""), currentState.reason.getOrElse(""), provenance)
      ): Unit
    stepEffects.updateState(currentState.copy(status = ReviewStatus.Rejected)).thenEnd

  /** The contribution first, then the copy leaves the Issue and its counts (FR-018). */
  def withdrawStep_ : StepEffect =
    contribution
      .call(ContributionEntity.withdraw)
      .invoke(Withdraw(currentState.participantId, provenance)): Unit
    for
      issueId <- currentState.issueId; pub <- currentState.pub
      if currentState.status == ReviewStatus.Withdrawing
    do
      context.componentClient
        .forEventSourcedEntity(EntityId(issueId))
        .call(IssueEntity.withdraw)
        .invoke(WithdrawCopy(pub, provenance)): Unit
    stepEffects.updateState(currentState.copy(status = ReviewStatus.Withdrawn)).thenEnd

  override def settings: WorkflowSettings =
    WorkflowSettings.builder
      .defaultStepTimeout(30.seconds)
      .stepTimeout(DisclosureReviewWorkflow.check, 120.seconds)
      .stepRecovery(
        DisclosureReviewWorkflow.check,
        RecoverStrategy.maxRetries(1).failoverTo(DisclosureReviewWorkflow.checkUnavailable)
      )
      .defaultRecovery(RecoverStrategy.maxRetries(3))
      .build

object DisclosureReviewWorkflow
    extends Workflow.Companion[DisclosureReviewWorkflow, ReviewState](
      componentId = ComponentId("disclosure-review"),
      stateSerializer = Codecs.serializer[ReviewState]("disclosure-review")
    ):
  given Serializer[StartReview]    = Codecs.serializer[StartReview]("disclosure-review-start")
  given Serializer[EnhancedReview] = Codecs.serializer[EnhancedReview]("disclosure-review-enhanced")
  given Serializer[ApproveReview]  = Codecs.serializer[ApproveReview]("disclosure-review-approve")
  given Serializer[RejectReview]   = Codecs.serializer[RejectReview]("disclosure-review-reject")
  given Serializer[WithdrawReview] = Codecs.serializer[WithdrawReview]("disclosure-review-withdraw")

  val Unavailable = "the automated privacy check could not run, so enhanced review is required"

  def create(context: WorkflowContext) = new DisclosureReviewWorkflow(context)

  val check            = step("check")(_.checkStep)
  val checkUnavailable = step("check-unavailable")(_.checkUnavailableStep)
  val recordEnhanced   = step("record-enhanced")(_.recordEnhancedStep)
  val publish          = step("publish")(_.publishStep)
  val rejectStep       = step("reject")(_.rejectStep_)
  val withdrawStep     = step("withdraw")(_.withdrawStep_)

  val start    = command("start")(_.start)
  val enhanced = command("enhanced")(_.enhanced)
  val approve  = command("approve")(_.approve)
  val reject   = command("reject")(_.reject)
  val withdraw = command("withdraw")(_.withdraw)
  val status   = query("status")(_.status)
