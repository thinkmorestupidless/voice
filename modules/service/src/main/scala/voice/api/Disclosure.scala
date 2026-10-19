package voice.api

import voice.application.erasure.{ParticipantRecordsEntity, RecordKind}
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.EntityId
import com.thinkmorestupidless.ankka.http.{Principal, RequestContext}
import voice.api.CitizenJson.SuggestionView
import voice.application.disclosure.*
import voice.application.issues.*
import voice.application.participation.ConcernEntity
import voice.application.participation.OpenContribution
import voice.application.participation.Purposes
import voice.domain.*
import voice.domain.disclosure.*

import java.time.Instant

/**
 * The disclosure bodies, citizen's and reviewer's (contracts/http-api.md, "Disclosure",
 * "Reviewer").
 */
object DisclosureJson:
  final case class ModeOption(
      mode: String,
      label: String,
      explanation: String,
      audience: String,
      chosen: Boolean
  )
  final case class DetectionView(kind: String, passage: String)
  final case class ReviewView(outcome: String, reason: Option[String], at: Instant)
  final case class AssociationView(issue: Option[String], newIssueAsked: Boolean)
  final case class ContributionView(
      id: String,
      concern: String,
      mode: String,
      audience: String,
      state: String,
      text: String,
      attribution: Option[String],
      review: Option[ReviewView],
      association: AssociationView,
      issue: Option[String],
      suggestedIssues: Vector[SuggestionView]
  )
  final case class SharingOptions(
      concern: String,
      modes: Vector[ModeOption],
      derivative: String,
      flags: Vector[String],
      reasons: Vector[String],
      detections: Vector[DetectionView],
      suggestedIssues: Vector[SuggestionView],
      contributions: Vector[ContributionView]
  )
  final case class ContributionRequest(mode: String, text: String, name: Option[String] = None)
  final case class IssueChoice(accept: Option[String] = None, `new`: Option[Boolean] = None)

  final case class SecondStepView(reviewer: String, reason: String, at: Instant)
  final case class ReviewItem(
      contribution: String,
      derivative: String,
      mode: String,
      audience: String,
      status: String,
      flags: Vector[String],
      reasons: Vector[String],
      secondStep: Option[SecondStepView],
      association: AssociationView,
      suggestedIssues: Vector[SuggestionView],
      submittedAt: Option[Instant],
      ageSeconds: Long,
      issue: Option[String],
      review: Option[ReviewView]
  )
  final case class ReasonRequest(reason: String)
  final case class NewIssueRequest(title: String, summary: String = "")
  final case class ApproveRequest(
      issue: Option[String] = None,
      `new`: Option[NewIssueRequest] = None,
      enhanced: Option[String] = None
  )

  given JsonValueCodec[SharingOptions]                  = Problems.codec[SharingOptions]
  given JsonValueCodec[ContributionView]                = Problems.codec[ContributionView]
  given JsonValueCodec[ContributionRequest]             = Problems.codec[ContributionRequest]
  given JsonValueCodec[IssueChoice]                     = Problems.codec[IssueChoice]
  given JsonValueCodec[ReviewItem]                      = Problems.codec[ReviewItem]
  given reviewItems: JsonValueCodec[Vector[ReviewItem]] = Problems.codec[Vector[ReviewItem]]
  given JsonValueCodec[ReasonRequest]                   = Problems.codec[ReasonRequest]
  given JsonValueCodec[ApproveRequest]                  = Problems.codec[ApproveRequest]

import DisclosureJson.*

/** Issues a text may join, by the words it shares with their public title and summary (FR-017). */
object IssueSuggestions:
  private val Stop = Set(
    "about",
    "after",
    "again",
    "also",
    "because",
    "been",
    "being",
    "from",
    "have",
    "into",
    "just",
    "more",
    "most",
    "much",
    "now",
    "only",
    "over",
    "some",
    "such",
    "than",
    "that",
    "their",
    "them",
    "then",
    "there",
    "these",
    "they",
    "this",
    "very",
    "were",
    "what",
    "when",
    "where",
    "which",
    "while",
    "with",
    "would",
    "your"
  )

  def words(text: String): Set[String] =
    text.toLowerCase
      .split("[^\\p{L}\\p{N}]+")
      .iterator
      .filter(w => w.length >= 4 && !Stop.contains(w))
      .toSet

  /** At most three, best first; a suggestion only, never a decision. */
  def apply(reach: Reach, text: String): Vector[SuggestionView] =
    val mine = words(text)
    if mine.isEmpty then Vector.empty
    else
      reach.views
        .forView(IssueRows)
        .all()
        .toVector
        .map(r =>
          r -> (words(r.title) ++ words(r.summary)).intersect(mine).size.toDouble / mine.size
        )
        .filter(_._2 >= 0.15)
        .sortBy(-_._2)
        .take(3)
        .map((r, score) =>
          SuggestionView(
            r.issueId,
            r.title,
            BigDecimal(score).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
          )
        )

/** Waits a short while for a workflow to reach a state, so a route can answer what it caused. */
object Settle:
  def apply[S](read: () => S, done: S => Boolean, within: Long = 8000): S =
    val until = System.currentTimeMillis() + within
    var state = read()
    while !done(state) && System.currentTimeMillis() < until do
      Thread.sleep(100)
      state = read()
    state

/**
 * The citizen's disclosures at the edge (US2). The exact text is sealed here and goes to the
 * contribution as the participant typed it; a submission starts the review, which only a reviewer
 * ends (FR-012 to FR-014). A contribution's original concern is never copied anywhere (FR-013).
 */
final class Disclosure(reach: Reach, participation: Participation):

  given JsonValueCodec[String] = Problems.codec[String]

  private def contribution(id: String) = reach.components.forEventSourcedEntity(EntityId(id))
  private def review(id: String)       = reach.components.forWorkflow(EntityId(id))

  private def owned(participantId: String, contributionId: String): ContributionRecord =
    val held = contribution(contributionId).call(ContributionEntity.get).invoke()
    if !held.exists || held.participantId != participantId then
      throw Rules.ContributionNotOwned.refused(
        "This contribution is not yours.",
        "contribution" -> contributionId
      )
    held

  def sharing(participantId: String, concernId: String): SharingOptions =
    val concern = participation.concernOf(participantId, concernId)
    val text = concern.summary.map(reach.sealing.openText(_, Purposes.ConcernSummary)).getOrElse("")
    val check = PrivacyDetectors.check(text)
    SharingOptions(
      concern = concernId,
      modes = Modes.offered.map(m =>
        ModeOption(
          m.wire,
          Modes.label(m),
          Modes.explanation(m),
          Modes.audience(m),
          m == DisclosureMode.Private
        )
      ),
      derivative = text,
      flags = check.flags.map(_.toString),
      reasons = check.reasons,
      detections = check.detections.map(d => DetectionView(d.kind, text.slice(d.from, d.to))),
      suggestedIssues = IssueSuggestions(reach, text),
      contributions = concern.contributions.map(id =>
        view(id, contribution(id).call(ContributionEntity.get).invoke())
      )
    )

  /** The participant approves this exact text for this mode (FR-012); nothing is shared yet. */
  def authorise(
      request: RequestContext,
      participantId: String,
      concernId: String,
      body: ContributionRequest
  ): ContributionView =
    participation.concernOf(participantId, concernId): Unit
    val mode = DisclosureMode
      .parse(body.mode.trim)
      .getOrElse(
        throw Rules.ContributionModeInvalid
          .refused(s"There is no sharing mode '${body.mode}'.", "contribution" -> "")
      )
    val text = body.text.trim
    if text.isEmpty then
      throw Rules.ContributionTextNotApproved.refused(
        "There is no text to approve.",
        "contribution" -> ""
      )
    val at    = reach.now()
    val id    = Ids.mint(IdKind.Contribution, at)
    val p     = Requests.provenance(request, Actor.Participant(participantId), at)
    val exact = reach.sealing.seal(participantId, DisclosurePurposes.Derivative, text)
    val grant = DisclosureGrant(
      mode,
      Modes.audience(mode),
      Modes.purpose(mode),
      exact,
      Contribution.textHash(exact),
      at
    )
    val name =
      body.name.map(_.trim).filter(_.nonEmpty).filter(_ => mode == DisclosureMode.PublicAttribution)
    if mode == DisclosureMode.PublicAttribution && name.isEmpty then
      throw Rules.ContributionModeInvalid.refused(
        "A publicly attributed contribution needs the name to show.",
        "contribution" -> id
      )
    val sealedName = name.map(reach.sealing.seal(participantId, DisclosurePurposes.Attribution, _))
    ParticipantRecordsEntity.noteFor(reach.components, participantId, RecordKind.Contribution, id)
    val state = contribution(id)
      .call(ContributionEntity.authorise)
      .invoke(Authorise(participantId, concernId, grant, sealedName, p))
    reach.components
      .forEventSourcedEntity(EntityId(concernId))
      .call(ConcernEntity.openContribution)
      .invoke(OpenContribution(id, p)): Unit
    view(id, state)

  /** Into review; the participant then sees "under moderation" (FR-014). */
  def submit(
      request: RequestContext,
      participantId: String,
      contributionId: String
  ): ContributionView =
    owned(participantId, contributionId): Unit
    val p = Requests.provenance(request, Actor.Participant(participantId), reach.now())
    val state =
      contribution(contributionId).call(ContributionEntity.submit).invoke(Submit(participantId, p))
    review(contributionId)
      .call(DisclosureReviewWorkflow.start)
      .invoke(StartReview(contributionId, participantId, p)): Unit
    view(contributionId, state)

  def get(participantId: String, contributionId: String): ContributionView =
    view(contributionId, owned(participantId, contributionId))

  def associate(
      request: RequestContext,
      participantId: String,
      contributionId: String,
      body: IssueChoice
  ): ContributionView =
    owned(participantId, contributionId): Unit
    body.accept.filter(_.nonEmpty).foreach { issue =>
      val held =
        reach.components.forEventSourcedEntity(EntityId(issue)).call(IssueEntity.get).invoke()
      if !held.exists then
        throw Rules.ContributionIssueNotHeld.refused(
          "There is no such issue.",
          "contribution" -> contributionId,
          "issue"        -> issue
        )
    }
    val p = Requests.provenance(request, Actor.Participant(participantId), reach.now())
    val state = contribution(contributionId)
      .call(ContributionEntity.associate)
      .invoke(
        Associate(participantId, body.accept.filter(_.nonEmpty), body.`new`.contains(true), p)
      )
    view(contributionId, state)

  /**
   * Out of its audience and the counts; the chronology keeps a withdrawal with no text (FR-018).
   */
  def withdraw(
      request: RequestContext,
      participantId: String,
      contributionId: String
  ): ContributionView =
    owned(participantId, contributionId): Unit
    val p      = Requests.provenance(request, Actor.Participant(participantId), reach.now())
    val status = () => review(contributionId).call(DisclosureReviewWorkflow.status).invoke()
    val moving = Set(ReviewStatus.Publishing, ReviewStatus.Rejecting, ReviewStatus.Withdrawing)
    val now    = Settle(status, s => !moving.contains(s.status))
    now.status match
      case ReviewStatus.Checking | ReviewStatus.AwaitingReviewer | ReviewStatus.Published =>
        review(contributionId)
          .call(DisclosureReviewWorkflow.withdraw)
          .invoke(WithdrawReview(participantId, p)): Unit
        Settle(status, _.status == ReviewStatus.Withdrawn): Unit
      case _ =>
        contribution(contributionId)
          .call(ContributionEntity.withdraw)
          .invoke(Withdraw(participantId, p)): Unit
    view(contributionId, contribution(contributionId).call(ContributionEntity.get).invoke())

  def view(id: String, s: ContributionRecord): ContributionView =
    val text =
      s.derivative.map(reach.sealing.openText(_, DisclosurePurposes.Derivative)).getOrElse("")
    ContributionView(
      id = id,
      concern = s.concernId,
      mode = s.mode.wire,
      audience = Modes.audience(s.mode),
      state = s.state.label,
      text = text,
      attribution = s.attribution.map(reach.sealing.openText(_, DisclosurePurposes.Attribution)),
      review = s.review.map(r =>
        ReviewView(if r.approved then "approved" else "rejected", r.reason, r.at)
      ),
      association = AssociationView(s.associatedIssue, s.newIssueAsked),
      issue = s.issueId,
      suggestedIssues =
        if s.state == ContributionState.Withdrawn then Vector.empty
        else IssueSuggestions(reach, text)
    )

/**
 * The reviewer's side (FR-014 to FR-016). Every read of a derivative is a read of a restricted
 * record and is logged (FR-005). Decisions go to the review workflow, whose commands refuse at
 * once.
 */
final class Reviews(reach: Reach, gate: StaffGate):

  given JsonValueCodec[String] = Problems.codec[String]

  private def review(id: String) = reach.components.forWorkflow(EntityId(id))

  def queue(request: RequestContext, principal: Principal, state: String): Vector[ReviewItem] =
    val wanted = ContributionState.values
      .find(_.toString.equalsIgnoreCase(state.replace("-", "")))
      .getOrElse(ContributionState.UnderModeration)
    reach.views
      .forView(ContributionRows)
      .ask(ContributionRows.inState, "state" -> wanted.toString)
      .toVector
      .sortBy(_.submittedAt.map(_.toEpochMilli).getOrElse(0L))
      // A row can outlive its contribution while the view catches up with an erasure's purge: the
      // queue passes over it rather than failing for everyone (FR-056).
      .filter(r =>
        reach.components
          .forEventSourcedEntity(EntityId(r.contributionId))
          .call(ContributionEntity.get)
          .invoke()
          .exists
      )
      .map(r => item(request, principal, r.contributionId))

  def item(request: RequestContext, principal: Principal, contributionId: String): ReviewItem =
    val held = reach.components
      .forEventSourcedEntity(EntityId(contributionId))
      .call(ContributionEntity.get)
      .invoke()
    if !held.exists || held.state == ContributionState.Drafting || held.state == ContributionState.AuthorisedByCitizen
    then
      throw Rules.ContributionNotUnderReview.refused(
        "This contribution is not under moderation.",
        "contribution" -> contributionId
      )
    gate.logAccess(request, principal, "GET /staff/reviews", contributionId)
    val flow = review(contributionId).call(DisclosureReviewWorkflow.status).invoke()
    val text =
      held.derivative.map(reach.sealing.openText(_, DisclosurePurposes.Derivative)).getOrElse("")
    val submitted = flow.submittedAt
    ReviewItem(
      contribution = contributionId,
      derivative = text,
      mode = held.mode.wire,
      audience = Modes.audience(held.mode),
      status = flow.status.toString,
      flags = held.flags.map(_.toString),
      reasons = held.flagReasons,
      secondStep = flow.secondStep
        .orElse(held.secondStep)
        .map(s => SecondStepView(s.reviewer, s.reason, s.at)),
      association = AssociationView(held.associatedIssue, held.newIssueAsked),
      suggestedIssues = IssueSuggestions(reach, text),
      submittedAt = submitted,
      ageSeconds = submitted
        .map(t => java.time.Duration.between(t, reach.now()).getSeconds.max(0L))
        .getOrElse(0L),
      issue = held.issueId,
      review = held.review.map(r =>
        ReviewView(if r.approved then "approved" else "rejected", r.reason, r.at)
      )
    )

  private def provenance(request: RequestContext, principal: Principal) =
    Requests.provenance(request, Requests.staff(principal), reach.now())

  private def status(id: String) = () => review(id).call(DisclosureReviewWorkflow.status).invoke()

  def enhanced(
      request: RequestContext,
      principal: Principal,
      contributionId: String,
      body: ReasonRequest
  ): ReviewItem =
    review(contributionId)
      .call(DisclosureReviewWorkflow.enhanced)
      .invoke(EnhancedReview(principal.subject, body.reason, provenance(request, principal))): Unit
    Settle(status(contributionId), _.status == ReviewStatus.AwaitingReviewer): Unit
    item(request, principal, contributionId)

  def approve(
      request: RequestContext,
      principal: Principal,
      contributionId: String,
      body: ApproveRequest
  ): ReviewItem =
    if body.enhanced.exists(_.trim.nonEmpty) then
      throw Rules.ContributionSecondStepSameClick.refused(
        "The enhanced review and the approval must be separate decisions.",
        "contribution" -> contributionId
      )
    body.issue.filter(_.nonEmpty).foreach { issue =>
      val held =
        reach.components.forEventSourcedEntity(EntityId(issue)).call(IssueEntity.get).invoke()
      if !held.exists then
        throw Rules.ContributionIssueNotHeld.refused(
          "There is no such issue.",
          "contribution" -> contributionId,
          "issue"        -> issue
        )
    }
    review(contributionId)
      .call(DisclosureReviewWorkflow.approve)
      .invoke(
        ApproveReview(
          principal.subject,
          body.issue.filter(_.nonEmpty),
          body.`new`.map(n => NewIssue(n.title, n.summary)),
          provenance(request, principal)
        )
      ): Unit
    Settle(status(contributionId), _.status == ReviewStatus.Published): Unit
    item(request, principal, contributionId)

  def reject(
      request: RequestContext,
      principal: Principal,
      contributionId: String,
      body: ReasonRequest
  ): ReviewItem =
    review(contributionId)
      .call(DisclosureReviewWorkflow.reject)
      .invoke(RejectReview(principal.subject, body.reason, provenance(request, principal))): Unit
    Settle(status(contributionId), _.status == ReviewStatus.Rejected): Unit
    item(request, principal, contributionId)
