package voice.domain.disclosure

import voice.domain.*

import java.time.Instant

/**
 * The participant's consent to one disclosure (FR-011, FR-012): the mode, the exact audience in
 * words, why, the exact text sealed, and a hash of its ciphertext so a later submission can prove
 * it is the text approved without opening it. Kept as evidence of consent; erased with its subject.
 */
final case class DisclosureGrant(
    mode: DisclosureMode,
    audience: String,
    purpose: String,
    exactText: Sealed[String],
    textHash: String,
    grantedAt: Instant,
    withdrawnAt: Option[Instant] = None
)

/** The words a participant reads for each mode, and the audience each reaches (FR-011, FR-012). */
object Modes:
  /** The mode as a participant reads it. */
  def label(mode: DisclosureMode): String = mode match
    case DisclosureMode.Private            => "private"
    case DisclosureMode.DeIdentified       => "de-identified"
    case DisclosureMode.RepresentativeOnly => "representative only"
    case DisclosureMode.PublicAttribution  => "publicly attributed"

  def audience(mode: DisclosureMode): String = mode match
    case DisclosureMode.Private => "you alone"
    case DisclosureMode.DeIdentified =>
      "a reviewer first, then anyone reading the issue, without your name"
    case DisclosureMode.RepresentativeOnly =>
      "a reviewer first, then the issue's authorised representatives only"
    case DisclosureMode.PublicAttribution =>
      "a reviewer first, then anyone reading the issue, with the name you give"

  def explanation(mode: DisclosureMode): String = mode match
    case DisclosureMode.Private =>
      "Nothing is shared. The concern stays in your conversation, where only you can see it."
    case DisclosureMode.DeIdentified =>
      "The text you approve is shown on a public issue with no name. A reviewer checks it first for anything that could identify you."
    case DisclosureMode.RepresentativeOnly =>
      "The text you approve is shown only to the representatives working on the issue, after a reviewer checks it. The public does not see it."
    case DisclosureMode.PublicAttribution =>
      "The text you approve is shown on a public issue with the name you choose. A reviewer checks it first."

  def purpose(mode: DisclosureMode): String = mode match
    case DisclosureMode.Private => "kept private"
    case _                      => "to inform a public issue and the people answerable for it"

  /** The offer order: private first, and already chosen (FR-011). */
  val offered: Vector[DisclosureMode] =
    Vector(
      DisclosureMode.Private,
      DisclosureMode.DeIdentified,
      DisclosureMode.RepresentativeOnly,
      DisclosureMode.PublicAttribution
    )

/** A reviewer's decision, with the reason a rejection always has (FR-015). */
final case class Review(reviewer: String, approved: Boolean, reason: Option[String], at: Instant)

/**
 * The second step a flagged derivative needs before public approval (FR-016). Its correlation is
 * the request that recorded it, so an approval in the same request is told apart (same-click).
 */
final case class SecondStep(reviewer: String, reason: String, correlationId: String, at: Instant)

final case class ContributionRecord(
    participantId: String,
    concernId: String,
    mode: DisclosureMode,
    derivative: Option[Sealed[String]],
    attribution: Option[Sealed[String]],
    state: ContributionState,
    flags: Vector[PrivacyFlag],
    flagReasons: Vector[String],
    grant: Option[DisclosureGrant],
    review: Option[Review],
    secondStep: Option[SecondStep],
    associatedIssue: Option[String],
    newIssueAsked: Boolean,
    issueId: Option[String],
    pub: Option[String],
    createdAt: Option[Instant]
):
  def exists: Boolean  = createdAt.isDefined
  def flagged: Boolean = flags.nonEmpty

object ContributionRecord:
  val empty: ContributionRecord =
    ContributionRecord(
      "",
      "",
      DisclosureMode.Private,
      None,
      None,
      ContributionState.Drafting,
      Vector.empty,
      Vector.empty,
      None,
      None,
      None,
      None,
      false,
      None,
      None,
      None
    )

enum ContributionEvent:
  /** The participant approved this exact text for this mode; a private choice stays a draft. */
  case DisclosureAuthorised(
      participantId: String,
      concernId: String,
      grant: DisclosureGrant,
      attribution: Option[Sealed[String]],
      provenance: Provenance
  )
  case SubmittedForReview(provenance: Provenance)
  case FlagsRecorded(flags: Vector[PrivacyFlag], reasons: Vector[String], provenance: Provenance)
  case EnhancedReviewRecorded(step: SecondStep, provenance: Provenance)
  case PublicationApproved(reviewer: String, issueId: String, pub: String, provenance: Provenance)
  case PublicationRejected(reviewer: String, reason: String, provenance: Provenance)
  case AssociatedWithIssue(issueId: Option[String], newIssueAsked: Boolean, provenance: Provenance)
  case DisclosureWithdrawn(provenance: Provenance)

object Contribution:
  import ContributionEvent.*

  def apply(s: ContributionRecord, event: ContributionEvent): ContributionRecord = event match
    case DisclosureAuthorised(participant, concern, grant, attribution, p) =>
      s.copy(
        participantId = participant,
        concernId = concern,
        mode = grant.mode,
        derivative = Some(grant.exactText),
        attribution = attribution,
        state =
          if grant.mode == DisclosureMode.Private then ContributionState.Drafting
          else ContributionState.AuthorisedByCitizen,
        grant = Some(grant),
        review = None,
        secondStep = None,
        flags = Vector.empty,
        flagReasons = Vector.empty,
        createdAt = s.createdAt.orElse(Some(p.at))
      )
    case SubmittedForReview(_)            => s.copy(state = ContributionState.UnderModeration)
    case FlagsRecorded(flags, reasons, _) => s.copy(flags = flags, flagReasons = reasons)
    case EnhancedReviewRecorded(step, _)  => s.copy(secondStep = Some(step))
    case PublicationApproved(reviewer, issue, pub, p) =>
      s.copy(
        state = ContributionState.Published,
        review = Some(Review(reviewer, true, None, p.at)),
        issueId = Some(issue),
        pub = Some(pub)
      )
    case PublicationRejected(reviewer, reason, p) =>
      s.copy(
        state = ContributionState.Rejected,
        review = Some(Review(reviewer, false, Some(reason), p.at))
      )
    case AssociatedWithIssue(issue, asked, _) =>
      s.copy(associatedIssue = issue, newIssueAsked = asked)
    case DisclosureWithdrawn(p) =>
      s.copy(
        state = ContributionState.Withdrawn,
        grant = s.grant.map(_.copy(withdrawnAt = Some(p.at)))
      )

  /** A hash of a sealed value's ciphertext: equal for the same sealing, and it reveals nothing. */
  def textHash(value: Sealed[String]): String = value match
    case Sealed.Present(_, ciphertext) =>
      java.util.HexFormat
        .of()
        .formatHex(
          java.security.MessageDigest.getInstance("SHA-256").digest(ciphertext.getBytes("UTF-8"))
        )
    case Sealed.Erased(_) => ""

  // ── rules (R12), each answering the refusal or nothing ─────────────────────

  def checkOwner(s: ContributionRecord, id: String, participant: String): Option[Refusal] =
    Option.when(s.exists && s.participantId != participant)(
      Rules.ContributionNotOwned.refusal("This contribution is not yours.", "contribution" -> id)
    )

  def checkLive(s: ContributionRecord, id: String): Option[Refusal] =
    Option.when(s.state == ContributionState.Withdrawn)(
      Rules.ContributionWithdrawn.refusal("This contribution was withdrawn.", "contribution" -> id)
    )

  def checkAuthorisable(s: ContributionRecord, id: String): Option[Refusal] =
    checkLive(s, id).orElse(
      Option.when(
        s.state == ContributionState.UnderModeration || s.state == ContributionState.Published
      )(
        Rules.ContributionStateInvalid.refusal(
          s"A contribution that is ${s.state.label} cannot be approved again; withdraw it first.",
          "contribution" -> id,
          "state"        -> s.state.label
        )
      )
    )

  /**
   * Only the exact text the participant approved, in a mode that shares, goes to review (FR-012,
   * FR-014).
   */
  def checkSubmittable(s: ContributionRecord, id: String): Option[Refusal] =
    checkLive(s, id)
      .orElse(
        Option.when(s.mode == DisclosureMode.Private)(
          Rules.ContributionPrivate.refusal(
            "A private contribution is not shared, so it is not reviewed.",
            "contribution" -> id
          )
        )
      )
      .orElse(
        Option.when(
          s.state != ContributionState.AuthorisedByCitizen ||
            !s.grant.exists(g => s.derivative.exists(d => g.textHash == textHash(d)))
        )(
          Rules.ContributionTextNotApproved
            .refusal("Only the exact text you approved can be submitted.", "contribution" -> id)
        )
      )

  def checkUnderReview(s: ContributionRecord, id: String): Option[Refusal] =
    checkLive(s, id).orElse(
      Option.when(s.state != ContributionState.UnderModeration)(
        Rules.ContributionNotUnderReview
          .refusal("This contribution is not under moderation.", "contribution" -> id)
      )
    )

  def checkReason(reason: String, id: String): Option[Refusal] =
    Option.when(reason.trim.isEmpty)(
      Rules.ContributionReasonRequired.refusal("A reason is required.", "contribution" -> id)
    )

  /** A flagged derivative needs a recorded second step, from an earlier request (FR-016). */
  def checkApprovable(s: ContributionRecord, id: String, correlationId: String): Option[Refusal] =
    checkUnderReview(s, id)
      .orElse(
        Option.when(s.mode == DisclosureMode.Private)(
          Rules.ContributionPrivate
            .refusal("A private contribution is not published.", "contribution" -> id)
        )
      )
      .orElse(
        Option.when(s.flagged && s.secondStep.isEmpty)(
          Rules.ContributionSecondStepRequired.refusal(
            "This derivative is flagged for enhanced review; record the second step before approving it.",
            "contribution" -> id,
            "flags"        -> s.flags.map(_.toString).mkString(",")
          )
        )
      )
      .orElse(
        Option.when(s.secondStep.exists(_.correlationId == correlationId))(
          Rules.ContributionSecondStepSameClick.refusal(
            "The enhanced review and the approval must be separate decisions.",
            "contribution" -> id
          )
        )
      )

  def checkAssociable(s: ContributionRecord, id: String): Option[Refusal] =
    checkLive(s, id).orElse(
      Option.when(s.state == ContributionState.Published)(
        Rules.ContributionStateInvalid.refusal(
          "A published contribution's issue is changed by a reviewer.",
          "contribution" -> id,
          "state"        -> s.state.label
        )
      )
    )
