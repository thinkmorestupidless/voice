package voice.domain.deliberation

import voice.domain.*

import java.time.Instant

/** How much an Issue matters to one participant, 1 to 5, and when they said so (FR-030). */
final case class Priority(level: Int, at: Instant)

/** Whether a participant would accept an option's trade-off. */
enum TradeOffStance:
  case Accepts, Rejects

  def wire: String = toString.toLowerCase

object TradeOffStance:
  def parse(s: String): Option[TradeOffStance] = values.find(_.wire == s.trim.toLowerCase)

/** A trade-off on one option: the stance is a category, the participant's words are sealed. */
final case class TradeOffEntry(
    optionId: String,
    stance: TradeOffStance,
    text: Sealed[String],
    at: Instant
)

/** A consideration the participant records against an option, in their words (FR-032). */
final case class ConsiderationEntry(optionId: String, text: Sealed[String], at: Instant)

/**
 * One participant's statements on one Issue: opinion, never evidence (FR-030, FR-031). The priority
 * is sealed; its level is carried plain beside it, because a level alone identifies no one and the
 * aggregate suppresses small cells (R8).
 */
final case class ExpressionState(
    issueId: String,
    participantId: String,
    priority: Option[Sealed[Priority]],
    level: Option[Int],
    tradeOffs: Vector[TradeOffEntry],
    considerations: Vector[ConsiderationEntry],
    withdrawn: Boolean,
    firstAt: Option[Instant],
    lastAt: Option[Instant]
):
  def exists: Boolean = participantId.nonEmpty

  /** Statements counted by the aggregate: the priority, each trade-off and each consideration. */
  def statements: Int = level.size + tradeOffs.size + considerations.size

object ExpressionState:
  val empty: ExpressionState =
    ExpressionState("", "", None, None, Vector.empty, Vector.empty, false, None, None)

enum ExpressionEvent:
  case PriorityExpressed(
      issueId: String,
      participantId: String,
      level: Int,
      priority: Sealed[Priority],
      provenance: Provenance
  )
  case ExpressionAmended(level: Int, priority: Sealed[Priority], provenance: Provenance)
  case TradeOffExpressed(
      issueId: String,
      participantId: String,
      entry: TradeOffEntry,
      provenance: Provenance
  )
  case ConsiderationRecorded(
      issueId: String,
      participantId: String,
      entry: ConsiderationEntry,
      provenance: Provenance
  )
  case ExpressionWithdrawn(provenance: Provenance)

object Expression:
  import ExpressionEvent.*

  private def touch(s: ExpressionState, issue: String, participant: String, at: Instant) =
    s.copy(
      issueId = issue,
      participantId = participant,
      withdrawn = false,
      firstAt = s.firstAt.orElse(Some(at)),
      lastAt = Some(at)
    )

  def apply(s: ExpressionState, event: ExpressionEvent): ExpressionState = event match
    case PriorityExpressed(issue, participant, level, priority, p) =>
      touch(s, issue, participant, p.at).copy(priority = Some(priority), level = Some(level))
    case ExpressionAmended(level, priority, p) =>
      s.copy(priority = Some(priority), level = Some(level), lastAt = Some(p.at))
    case TradeOffExpressed(issue, participant, entry, p) =>
      touch(s, issue, participant, p.at)
        .copy(tradeOffs = s.tradeOffs.filterNot(_.optionId == entry.optionId) :+ entry)
    case ConsiderationRecorded(issue, participant, entry, p) =>
      touch(s, issue, participant, p.at).copy(considerations = s.considerations :+ entry)
    case ExpressionWithdrawn(p) =>
      s.copy(
        priority = None,
        level = None,
        tradeOffs = Vector.empty,
        considerations = Vector.empty,
        withdrawn = true,
        lastAt = Some(p.at)
      )

  val Levels: Range = 1 to 5

  def checkLevel(issue: String, level: Int): Option[Refusal] =
    Option.unless(Levels.contains(level))(
      Rules.ExpressionPriorityRange.refusal("A priority is a level from 1 to 5.", "issue" -> issue)
    )

  /**
   * An amendment to a withdrawn expression is refused; re-expressing a priority starts it again.
   */
  def checkLive(s: ExpressionState, issue: String): Option[Refusal] =
    Option.when(s.withdrawn)(
      Rules.ExpressionWithdrawn.refusal(
        "This expression was withdrawn; express a priority to start again.",
        "issue" -> issue
      )
    )

  def checkOption(issue: String, offered: Set[String], option: String): Option[Refusal] =
    Option.unless(offered.contains(option))(
      Rules.ExpressionOptionNotHeld
        .refusal("The issue does not offer this option.", "issue" -> issue, "option" -> option)
    )
