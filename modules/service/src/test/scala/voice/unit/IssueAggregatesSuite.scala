package voice.unit

import voice.domain.deliberation.*

import java.time.Instant

/**
 * The aggregate over five synthetic participants: labelled, suppressed below the threshold (T072).
 */
class IssueAggregatesSuite extends munit.FunSuite:

  private val at = Instant.parse("2026-11-02T09:00:00Z")
  private def row(n: Int, level: Int, stance: Option[TradeOffStance] = None) =
    AggregateRow(
      s"exp-$n",
      "iss-1",
      Some(level),
      stance.map(OptionStance("pol-1", _)).toVector,
      Vector.empty,
      at.plusSeconds(n * 3600L),
      at.plusSeconds(n * 3600L)
    )

  private val five = Vector(
    row(1, 5, Some(TradeOffStance.Accepts)),
    row(2, 5, Some(TradeOffStance.Accepts)),
    row(3, 5, Some(TradeOffStance.Accepts)),
    row(4, 5, Some(TradeOffStance.Accepts)),
    row(5, 5, Some(TradeOffStance.Accepts))
  )

  test("every aggregate names its sample, method, window, deduplication and caveat") {
    val a = Aggregates.of(five, 5, Seq("pol-1"))
    assertEquals(a.participants, Cell("participants", Some(5), false))
    assertEquals(a.contributions.count, Some(10))
    assert(a.method.startsWith("Self-selected"))
    assert(a.caveat.contains("proportion of all constituents"))
    assert(a.deduplication.contains("One expression per participant"))
    assertEquals(
      a.window,
      Window(
        Some(java.time.LocalDate.parse("2026-11-02")),
        Some(java.time.LocalDate.parse("2026-11-02"))
      )
    )
    assertEquals(a.priorities.find(_.key == "5").get.count, Some(5))
  }

  test("a cell under the threshold is suppressed, and so is the next smallest beside it") {
    val mixed = five.take(4) :+ row(6, 2) :+ row(7, 4) :+ row(8, 4) :+ row(9, 4) :+ row(10, 4)
    val a     = Aggregates.of(mixed, 5, Seq("pol-1"))
    val byKey = a.priorities.map(c => c.key -> c).toMap
    assert(byKey("2").suppressed, a.toString)
    // One suppressed cell would be total minus the rest: the smallest other cell goes too.
    assert(byKey("5").suppressed || byKey("4").suppressed, a.toString)
    assertEquals(byKey("1"), Cell("1", Some(0), false))
  }

  test("fewer participants than the threshold suppress every count and the window") {
    val a = Aggregates.of(five.take(4), 5, Seq("pol-1"))
    assert(a.participants.suppressed && a.contributions.suppressed)
    assert(a.priorities.forall(c => c.suppressed || c.count.contains(0)))
    assert(a.options.flatMap(_.cells).forall(_.suppressed))
    assertEquals(a.window, Window(None, None))
  }

  test("a withdrawn expression is not counted") {
    val withdrawn = AggregateRow("exp-9", "iss-1", None, Vector.empty, Vector.empty, at, at)
    assertEquals(Aggregates.of(five :+ withdrawn, 5, Seq("pol-1")).participants.count, Some(5))
  }
