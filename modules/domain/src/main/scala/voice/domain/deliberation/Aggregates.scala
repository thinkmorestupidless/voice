package voice.domain.deliberation

import java.time.{Instant, LocalDate, ZoneOffset}

/**
 * One expression as the aggregate view holds it: no participant, no words, only what is counted.
 */
final case class OptionStance(optionId: String, stance: TradeOffStance)
final case class AggregateRow(
    expressionId: String,
    issueId: String,
    level: Option[Int],
    stances: Vector[OptionStance],
    considerations: Vector[String],
    firstAt: Instant,
    lastAt: Instant
):
  def statements: Int = level.size + stances.size + considerations.size

/**
 * A count, or `suppressed` when it is under the threshold (`aggregate.suppressed` is an answer).
 */
final case class Cell(key: String, count: Option[Int], suppressed: Boolean)
final case class OptionCells(optionId: String, cells: Vector[Cell])
final case class Window(from: Option[LocalDate], to: Option[LocalDate])

/**
 * What every figure is shown with (FR-033, FR-034): who it counts, how they came, when, how repeats
 * are counted, and that it claims nothing about everyone.
 */
final case class Aggregate(
    participants: Cell,
    contributions: Cell,
    method: String,
    window: Window,
    deduplication: String,
    caveat: String,
    threshold: Int,
    priorities: Vector[Cell],
    options: Vector[OptionCells]
)

object Aggregates:

  val Method: String =
    "Self-selected: participants on Voice who chose to say how much this issue matters to them or which trade-offs they would accept."
  val Deduplication: String =
    "One expression per participant per issue. An amended priority replaces the earlier one; a withdrawn expression is not counted."
  val Caveat: String =
    "These are statements by the participants who chose to answer. They are opinions, not evidence, and say nothing about the proportion of all constituents who hold them."

  private def day(at: Instant): LocalDate = at.atZone(ZoneOffset.UTC).toLocalDate

  /**
   * Counts under `threshold` are suppressed (FR-034). When one cell of a dimension is suppressed
   * the next smallest non-zero cell is too, so no suppressed count can be recovered from a total.
   */
  def suppress(cells: Vector[(String, Int)], threshold: Int): Vector[Cell] =
    val small = cells.filter((_, n) => n > 0 && n < threshold).map(_._1).toSet
    val also =
      if small.size == 1 then
        cells.filter((k, n) => n > 0 && !small(k)).sortBy(_._2).headOption.map(_._1).toSet
      else Set.empty[String]
    cells.map((k, n) =>
      if small(k) || also(k) then Cell(k, None, true) else Cell(k, Some(n), false)
    )

  def of(rows: Seq[AggregateRow], threshold: Int, options: Seq[String]): Aggregate =
    val live   = rows.filter(_.statements > 0).toVector
    val people = live.size
    val total  = live.map(_.statements).sum
    val tooFew = people < threshold
    def whole(key: String, n: Int) =
      if tooFew && n > 0 then Cell(key, None, true) else Cell(key, Some(n), false)
    val levels = Expression.Levels.toVector.map(l => l.toString -> live.count(_.level.contains(l)))
    val perOption = options.toVector.map { o =>
      OptionCells(
        o,
        if tooFew then TradeOffStance.values.toVector.map(s => Cell(s.wire, None, true))
        else
          suppress(
            TradeOffStance.values.toVector.map(s =>
              s.wire -> live.count(_.stances.contains(OptionStance(o, s)))
            ),
            threshold
          )
      )
    }
    Aggregate(
      participants = whole("participants", people),
      contributions = whole("contributions", total),
      method = Method,
      window =
        if tooFew then Window(None, None)
        else Window(live.map(_.firstAt).minOption.map(day), live.map(_.lastAt).maxOption.map(day)),
      deduplication = Deduplication,
      caveat = Caveat,
      threshold = threshold,
      priorities =
        if tooFew then levels.map((k, _) => Cell(k, None, true)) else suppress(levels, threshold),
      options = perOption
    )
