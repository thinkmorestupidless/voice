package voice.domain

/**
 * The seven states an Issue's chronology uses, and the only words any human-facing statement about
 * progress may use (FR-042, FR-061). A label never implies a step that is not recorded.
 */
enum ChronologyState(val label: String):
  case Submitted          extends ChronologyState("submitted")
  case AiInterpreted      extends ChronologyState("AI-interpreted")
  case UnderModeration    extends ChronologyState("under moderation")
  case StaffAcknowledged  extends ChronologyState("staff-acknowledged")
  case UnderInvestigation extends ChronologyState("under investigation")
  case ActionRecorded     extends ChronologyState("action recorded")
  case OutcomeVerified    extends ChronologyState("outcome verified")

/** How a contribution may be shared (FR-011). */
enum DisclosureMode(val wire: String):
  case Private            extends DisclosureMode("private")
  case DeIdentified       extends DisclosureMode("de-identified")
  case RepresentativeOnly extends DisclosureMode("representative-only")
  case PublicAttribution  extends DisclosureMode("public-attribution")

object DisclosureMode:
  def parse(wire: String): Option[DisclosureMode] = values.find(_.wire == wire)

/** A contribution's life. `Withdrawn` is terminal. */
enum ContributionState:
  case Drafting, AuthorisedByCitizen, UnderModeration, Published, Rejected, Withdrawn

  /** The chronology label a citizen sees for it; a rejection or a draft is not on the Issue. */
  def label: String = this match
    case Drafting            => "draft"
    case AuthorisedByCitizen => "approved by you"
    case UnderModeration     => ChronologyState.UnderModeration.label
    case Published           => "published"
    case Rejected            => "not published"
    case Withdrawn           => "withdrawn"

object ContributionState:
  private val allowed: Map[ContributionState, Set[ContributionState]] = Map(
    Drafting            -> Set(Drafting, AuthorisedByCitizen, Withdrawn),
    AuthorisedByCitizen -> Set(Drafting, AuthorisedByCitizen, UnderModeration, Withdrawn),
    UnderModeration     -> Set(Published, Rejected, Withdrawn),
    Published           -> Set(Withdrawn),
    Rejected            -> Set(Drafting, Withdrawn),
    Withdrawn           -> Set.empty
  )

  def canMove(from: ContributionState, to: ContributionState): Boolean =
    allowed.getOrElse(from, Set.empty).contains(to)

/** A reviewer's privacy flags on a derivative (FR-016). Any flag means enhanced review. */
enum PrivacyFlag:
  case Distinctive, Health, Minor, SmallGroup

/** The civic state of a piece of evidence linked to an Issue (FR-026, FR-027). */
enum EvidenceState:
  case Candidate, Accepted, Rejected, Superseded

object EvidenceState:
  def canMove(from: EvidenceState, to: EvidenceState): Boolean = (from, to) match
    case (Superseded, _)                   => false
    case (Rejected, Accepted)              => false
    case (a, b) if a == b                  => true
    case (Candidate, _)                    => true
    case (Accepted, Superseded | Rejected) => true
    case (Rejected, Superseded)            => true
    case _                                 => false

/**
 * An investigation is suggested by the research agent or opened by a representative (FR-038,
 * FR-039).
 */
enum InvestigationState(val label: String):
  case Suggested extends InvestigationState("suggested by AI, not opened")
  case Open      extends InvestigationState(ChronologyState.UnderInvestigation.label)
  case Closed    extends InvestigationState("investigation closed")

/** An action's state. `Proposed` is never rendered as done (FR-041). */
enum ActionState(val wire: String, val label: String):
  case Proposed   extends ActionState("proposed", "proposed, not started")
  case InProgress extends ActionState("in-progress", "in progress")
  case Completed  extends ActionState("completed", "completed")

object ActionState:
  def parse(wire: String): Option[ActionState] = values.find(_.wire == wire)

  /** Forward only: a completed action does not move back (`action.state.backwards`). */
  def canMove(from: ActionState, to: ActionState): Boolean = to.ordinal >= from.ordinal

/** A notification's delivery to the inbox (FR-047, FR-048). */
enum NotificationState:
  case Prepared, Delivered, Failed, Read

object NotificationState:
  def canMove(from: NotificationState, to: NotificationState): Boolean = (from, to) match
    case (Prepared, Delivered | Failed) => true
    case (Failed, Delivered | Failed)   => true
    case (Delivered, Read)              => true
    case (a, b)                         => a == b

/** Why an author's retained public copy has no author any more (FR-064). */
enum ErasureCause(val wire: String):
  case Requested extends ErasureCause("requested")
  case Dormancy  extends ErasureCause("dormancy")

object ErasureCause:
  def parse(wire: String): Option[ErasureCause] = values.find(_.wire == wire)
