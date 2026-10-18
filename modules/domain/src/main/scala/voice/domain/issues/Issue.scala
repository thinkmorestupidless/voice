package voice.domain.issues

import voice.domain.*

import java.time.Instant

/**
 * A de-identified contribution's public copy: the one citizen text an Issue holds in plaintext,
 * public by the citizen's and the reviewer's decision, and what remains after erasure (R2). It
 * carries a `pub-…` reference and nothing about its author but whether the author was erased.
 */
final case class PublicContribution(
    pub: String,
    text: String,
    publishedAt: Instant,
    authorErased: Option[ErasureCause] = None
)

/** A publicly attributed copy, sealed under the author's key and opened at the edge (R2). */
final case class AttributedContribution(
    pub: String,
    text: Sealed[String],
    name: Sealed[String],
    publishedAt: Instant
)

/** A copy for representatives only, sealed under the author's key. */
final case class RestrictedContribution(pub: String, text: Sealed[String], publishedAt: Instant)

/**
 * One chronology entry (FR-042): exactly one of the seven states, what happened in fixed words, the
 * kind of actor, when, and the record it refers to. An entry never carries a contribution's text.
 */
final case class Entry(
    state: ChronologyState,
    note: String,
    actorKind: String,
    at: Instant,
    ref: String
)

/** The Issue's counts (FR-018, FR-064): what later weighting reads, and the caveat with them. */
final case class IssueCounts(
    contributions: Int = 0,
    withdrawn: Int = 0,
    erasedRequested: Int = 0,
    erasedDormancy: Int = 0
)

object IssueCounts:
  val Caveat: String =
    "Counts are of contributions, not people: one person may contribute more than once, and a count says nothing about how many people agree."

final case class IssueState(
    title: String,
    summary: String,
    openedAt: Option[Instant],
    contributions: Vector[PublicContribution],
    attributed: Vector[AttributedContribution],
    restricted: Vector[RestrictedContribution],
    withdrawnPubs: Vector[String],
    chronology: Vector[Entry],
    counts: IssueCounts,
    lastDevelopmentAt: Option[Instant],
    questions: Vector[QuestionRef] = Vector.empty,
    claims: Vector[LinkedClaim] = Vector.empty,
    evidence: Vector[EvidenceLink] = Vector.empty,
    reviewPrompts: Vector[ReviewPrompt] = Vector.empty,
    options: Vector[String] = Vector.empty,
    investigations: Vector[String] = Vector.empty,
    actions: Vector[String] = Vector.empty,
    decisions: Vector[String] = Vector.empty,
    outcomes: Vector[String] = Vector.empty,
    developments: Vector[Development] = Vector.empty,
    updates: Vector[NarrativeUpdate] = Vector.empty
):
  def exists: Boolean = openedAt.isDefined
  def holds(pub: String): Boolean =
    contributions.exists(_.pub == pub) || attributed.exists(_.pub == pub) || restricted.exists(
      _.pub == pub
    )
  def known(pub: String): Boolean                    = holds(pub) || withdrawnPubs.contains(pub)
  def link(id: String): Option[EvidenceLink]         = evidence.find(_.linkId == id)
  def linkOf(ref: EvidenceRef): Option[EvidenceLink] = evidence.find(_.evidence.contains(ref))
  def prompt(id: String): Option[ReviewPrompt]       = reviewPrompts.find(_.promptId == id)
  def development(id: String): Option[Development]   = developments.find(_.developmentId == id)

  /** The hypotheses of the Issue's questions, by reference, for a claim's or evidence's bearing. */
  def hypothesesOf(question: QuestionRef): Vector[HypothesisRef] =
    claims.flatMap(_.stances.map(_.hypothesis)).filter(_.question == question.id).distinct

  /**
   * The claims an evidence bears on (FR-028): those deriving from it, and those with a stance on a
   * hypothesis it was proposed as bearing on, or on any of the Issue's when it named none.
   */
  def claimsBearingOn(link: EvidenceLink): Vector[ClaimRef] =
    val deriving = claims.filter(c => link.evidence.exists(c.derivesFrom.contains)).map(_.claim)
    val bearing =
      if link.bearsOn.isEmpty then claims.map(_.claim)
      else claims.filter(_.stances.exists(s => link.bearsOn.contains(s.hypothesis))).map(_.claim)
    (deriving ++ bearing).distinct

  /** Claims deriving from an evidence, and only those: what a supersession puts in question. */
  def claimsDerivingFrom(link: EvidenceLink): Vector[ClaimRef] =
    claims.filter(c => link.evidence.exists(c.derivesFrom.contains)).map(_.claim)

object IssueState:
  val empty: IssueState = IssueState(
    "",
    "",
    None,
    Vector.empty,
    Vector.empty,
    Vector.empty,
    Vector.empty,
    Vector.empty,
    IssueCounts(),
    None
  )

enum IssueEvent:
  case IssueOpened(title: String, summary: String, provenance: Provenance)
  case ContributionPublished(pub: String, text: String, provenance: Provenance)
  case ContributionAttributed(
      pub: String,
      text: Sealed[String],
      name: Sealed[String],
      provenance: Provenance
  )
  case ContributionRestricted(pub: String, text: Sealed[String], provenance: Provenance)
  case ContributionWithdrawn(pub: String, provenance: Provenance)
  case QuestionLinked(question: QuestionRef, provenance: Provenance)
  case ClaimLinked(claim: LinkedClaim, provenance: Provenance)
  case EvidenceProposed(link: EvidenceLink, provenance: Provenance)
  case EvidenceAccepted(
      linkId: String,
      evidence: EvidenceRef,
      reviewer: String,
      provenance: Provenance
  )
  case EvidenceRejected(linkId: String, reviewer: String, reason: String, provenance: Provenance)
  case EvidenceSuperseded(
      linkId: String,
      by: String,
      reviewer: String,
      reason: String,
      provenance: Provenance
  )
  case ReviewPromptRaised(prompt: ReviewPrompt, provenance: Provenance)
  case ReviewPromptResolved(promptId: String, by: String, note: String, provenance: Provenance)
  case OptionAdded(optionId: String, title: String, provenance: Provenance)
  case InvestigationOpened(investigationId: String, question: String, provenance: Provenance)
  case ActionRecorded(
      actionId: String,
      summary: String,
      developmentId: String,
      provenance: Provenance
  )
  case DecisionRecorded(
      decisionId: String,
      approach: String,
      developmentId: String,
      provenance: Provenance
  )
  case OutcomeRecorded(
      outcomeId: String,
      actionId: String,
      developmentId: String,
      provenance: Provenance
  )
  case UpdatePublished(
      developmentId: String,
      text: String,
      by: String,
      corrects: Option[String],
      provenance: Provenance
  )
  case DevelopmentPublished(development: Development, provenance: Provenance)
  case AuthorErased(pub: String, cause: ErasureCause, provenance: Provenance)

object Issue:
  import IssueEvent.*

  private def kind(p: Provenance): String = p.actor match
    case Actor.Participant(_) => "participant"
    case Actor.Citizen        => "participant"
    case Actor.Staff(_)       => "staff"
    case Actor.System(_)      => "system"

  private def entry(
      s: IssueState,
      state: ChronologyState,
      note: String,
      p: Provenance,
      ref: String
  ): IssueState =
    s.copy(
      chronology = s.chronology :+ Entry(state, note, kind(p), p.at, ref),
      lastDevelopmentAt = Some(p.at)
    )

  private def published(s: IssueState, pub: String, p: Provenance): IssueState =
    entry(
      s.copy(counts = s.counts.copy(contributions = s.counts.contributions + 1)),
      ChronologyState.Submitted,
      "a contribution was published after review",
      p,
      pub
    )

  def apply(s: IssueState, event: IssueEvent): IssueState = event match
    case IssueOpened(title, summary, p) =>
      entry(
        s.copy(title = title, summary = summary, openedAt = Some(p.at)),
        ChronologyState.StaffAcknowledged,
        "the issue was opened",
        p,
        ""
      )
    case ContributionPublished(pub, text, p) =>
      published(
        s.copy(contributions = s.contributions :+ PublicContribution(pub, text, p.at)),
        pub,
        p
      )
    case ContributionAttributed(pub, text, name, p) =>
      published(
        s.copy(attributed = s.attributed :+ AttributedContribution(pub, text, name, p.at)),
        pub,
        p
      )
    case ContributionRestricted(pub, text, p) =>
      published(
        s.copy(restricted = s.restricted :+ RestrictedContribution(pub, text, p.at)),
        pub,
        p
      )
    case ContributionWithdrawn(pub, p) =>
      val dropped = s.copy(
        contributions = s.contributions.filterNot(_.pub == pub),
        attributed = s.attributed.filterNot(_.pub == pub),
        restricted = s.restricted.filterNot(_.pub == pub),
        withdrawnPubs = s.withdrawnPubs :+ pub,
        counts = s.counts
          .copy(contributions = s.counts.contributions - 1, withdrawn = s.counts.withdrawn + 1)
      )
      entry(
        dropped,
        ChronologyState.Submitted,
        "a contribution was withdrawn by its author",
        p,
        pub
      )
    case QuestionLinked(q, p) =>
      entry(
        s.copy(questions = s.questions :+ q),
        ChronologyState.StaffAcknowledged,
        "a question was linked to the issue",
        p,
        q.id
      )
    case ClaimLinked(c, p) =>
      entry(
        s.copy(claims = s.claims :+ c),
        ChronologyState.StaffAcknowledged,
        "a claim from accepted evidence was linked to the issue",
        p,
        c.claim.id
      )
    case EvidenceProposed(link, _) => s.copy(evidence = s.evidence :+ link)
    case EvidenceAccepted(id, ref, reviewer, p) =>
      val moved = s.copy(evidence =
        s.evidence.map(l =>
          if l.linkId == id then
            l.copy(
              evidence = Some(ref),
              candidate = None,
              state = EvidenceState.Accepted,
              decidedBy = Some(reviewer),
              decidedAt = Some(p.at)
            )
          else l
        )
      )
      entry(moved, ChronologyState.StaffAcknowledged, "evidence was accepted by a reviewer", p, id)
    case EvidenceRejected(id, reviewer, reason, p) =>
      s.copy(evidence =
        s.evidence.map(l =>
          if l.linkId == id then
            l.copy(
              state = EvidenceState.Rejected,
              decidedBy = Some(reviewer),
              decidedAt = Some(p.at),
              reason = Some(reason)
            )
          else l
        )
      )
    case EvidenceSuperseded(id, by, reviewer, reason, p) =>
      val moved = s.copy(evidence =
        s.evidence.map(l =>
          if l.linkId == id then
            l.copy(
              state = EvidenceState.Superseded,
              decidedBy = Some(reviewer),
              decidedAt = Some(p.at),
              reason = Some(reason),
              supersededBy = Some(by)
            )
          else l
        )
      )
      entry(
        moved,
        ChronologyState.StaffAcknowledged,
        "evidence was superseded by a correction",
        p,
        id
      )
    case ReviewPromptRaised(prompt, _) => s.copy(reviewPrompts = s.reviewPrompts :+ prompt)
    case ReviewPromptResolved(id, by, note, p) =>
      s.copy(reviewPrompts =
        s.reviewPrompts.map(r =>
          if r.promptId == id then r.copy(resolved = Some(Resolution(by, p.at, note))) else r
        )
      )
    case OptionAdded(option, _, p) =>
      entry(
        s.copy(options = s.options :+ option),
        ChronologyState.StaffAcknowledged,
        "an option was described for the issue",
        p,
        option
      )
    case InvestigationOpened(id, _, p) =>
      entry(
        s.copy(investigations = s.investigations :+ id),
        ChronologyState.UnderInvestigation,
        "a representative opened an investigation",
        p,
        id
      )
    case ActionRecorded(id, _, _, p) =>
      entry(
        s.copy(actions = s.actions :+ id),
        ChronologyState.ActionRecorded,
        "a representative recorded an action",
        p,
        id
      )
    case DecisionRecorded(id, _, _, p) =>
      entry(
        s.copy(decisions = s.decisions :+ id),
        ChronologyState.StaffAcknowledged,
        "a representative recorded a decision",
        p,
        id
      )
    case OutcomeRecorded(id, _, _, p) =>
      entry(
        s.copy(outcomes = s.outcomes :+ id),
        ChronologyState.OutcomeVerified,
        "a representative recorded what an action achieved",
        p,
        id
      )
    case UpdatePublished(id, text, by, corrects, p) =>
      val note =
        if corrects.isDefined then "a representative corrected the wording of an update"
        else "a representative published an update"
      entry(
        s.copy(updates = s.updates :+ NarrativeUpdate(id, text, by, p.at, corrects)),
        ChronologyState.StaffAcknowledged,
        note,
        p,
        id
      )
    case DevelopmentPublished(d, _) => s.copy(developments = s.developments :+ d)
    case AuthorErased(pub, cause, _) =>
      val counts = cause match
        case ErasureCause.Requested => s.counts.copy(erasedRequested = s.counts.erasedRequested + 1)
        case ErasureCause.Dormancy  => s.counts.copy(erasedDormancy = s.counts.erasedDormancy + 1)
      s.copy(
        contributions =
          s.contributions.map(c => if c.pub == pub then c.copy(authorErased = Some(cause)) else c),
        counts = counts
      )

  def checkHeld(s: IssueState, id: String): Option[Refusal] =
    Option.when(!s.exists)(Rules.IssueNotHeld.refusal("There is no such issue.", "issue" -> id))

  /** A move the evidence states allow (`evidence.state.invalid`). */
  def checkMove(
      s: IssueState,
      issue: String,
      linkId: String,
      to: EvidenceState
  ): Either[Refusal, EvidenceLink] =
    s.link(linkId) match
      case None =>
        Left(
          Rules.EvidenceReferenceUnresolved.refusal(
            "There is no such evidence on this issue.",
            "kind" -> "evidence",
            "id"   -> linkId
          )
        )
      case Some(l) if !EvidenceState.canMove(l.state, to) =>
        Left(
          Rules.EvidenceStateInvalid.refusal(
            s"Evidence that is ${l.state.toString.toLowerCase} cannot become ${to.toString.toLowerCase}.",
            "issue"    -> issue,
            "evidence" -> linkId,
            "from"     -> l.state.toString,
            "to"       -> to.toString
          )
        )
      case Some(l) => Right(l)
