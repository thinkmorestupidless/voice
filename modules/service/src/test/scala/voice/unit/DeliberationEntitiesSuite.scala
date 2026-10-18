package voice.unit

import com.thinkmorestupidless.ankka.testkit.EventSourcedTestKit
import voice.application.deliberation.*
import voice.domain.*
import voice.domain.deliberation.*

import java.time.Instant

/** The expression rules, with no runtime (T071). */
class DeliberationEntitiesSuite extends munit.FunSuite:

  private val at = Instant.parse("2026-11-02T09:00:00Z")
  private def p  = Provenance(Actor.Participant("prt-a"), at, "test", "c1")
  private def ruleOf(e: com.thinkmorestupidless.ankka.core.CommandError) =
    Refusals.fromCommandError(e).map(_.rule)
  private def priority(level: Int) =
    ExpressPriority("iss-1", "prt-a", level, Sealed.Present[Priority]("prt-a", s"level-$level"), p)
  private def trade(option: String) =
    ExpressTradeOff(
      "iss-1",
      "prt-a",
      Set("pol-1"),
      TradeOffEntry(option, TradeOffStance.Accepts, Sealed.Present[String]("prt-a", "AAAA"), at),
      p
    )

  test("a priority is a level from 1 to 5, and a second one amends it") {
    val kit = EventSourcedTestKit.of(ExpressionEntity, "exp-1")
    assertEquals(
      ruleOf(kit.call(ExpressionEntity.priority)(priority(6)).error),
      Some("expression.priority.range")
    )
    assertEquals(
      ruleOf(kit.call(ExpressionEntity.priority)(priority(0)).error),
      Some("expression.priority.range")
    )
    assertEquals(kit.call(ExpressionEntity.priority)(priority(4)).replyValue.level, Some(4))
    val amended = kit.call(ExpressionEntity.priority)(priority(2))
    assert(
      amended.events.exists(_.isInstanceOf[ExpressionEvent.ExpressionAmended]),
      amended.events.toString
    )
    assertEquals(amended.replyValue.level, Some(2))
  }

  test("a trade-off names an option the issue offers, one stance per option") {
    val kit = EventSourcedTestKit.of(ExpressionEntity, "exp-2")
    assertEquals(
      ruleOf(kit.call(ExpressionEntity.tradeOff)(trade("pol-9")).error),
      Some("expression.option.not-held")
    )
    kit.call(ExpressionEntity.tradeOff)(trade("pol-1"))
    val again = kit.call(ExpressionEntity.tradeOff)(
      trade("pol-1").copy(entry = trade("pol-1").entry.copy(stance = TradeOffStance.Rejects))
    )
    assertEquals(again.replyValue.tradeOffs.map(_.stance), Vector(TradeOffStance.Rejects))
  }

  test("a withdrawn expression refuses amendments, and a new priority starts it again") {
    val kit = EventSourcedTestKit.of(ExpressionEntity, "exp-3")
    kit.call(ExpressionEntity.priority)(priority(5))
    kit.call(ExpressionEntity.tradeOff)(trade("pol-1"))
    val gone = kit.call(ExpressionEntity.withdraw)(WithdrawExpression(p)).replyValue
    assert(gone.withdrawn && gone.level.isEmpty && gone.tradeOffs.isEmpty, gone.toString)
    assert(!kit.call(ExpressionEntity.withdraw)(WithdrawExpression(p)).persisted)
    assertEquals(
      ruleOf(kit.call(ExpressionEntity.tradeOff)(trade("pol-1")).error),
      Some("expression.withdrawn")
    )
    val again = kit.call(ExpressionEntity.priority)(priority(3))
    assert(
      again.events.exists(_.isInstanceOf[ExpressionEvent.PriorityExpressed]),
      again.events.toString
    )
    assert(!again.replyValue.withdrawn)
  }

  test("the same participant and issue give the same expression id, and others a different one") {
    val key = Array.fill[Byte](32)(7)
    val a   = Ids.keyed(IdKind.Expression, key, "expression", "prt-a", "iss-1")
    assertEquals(Ids.keyed(IdKind.Expression, key, "expression", "prt-a", "iss-1"), a)
    assertNotEquals(Ids.keyed(IdKind.Expression, key, "expression", "prt-b", "iss-1"), a)
    assert(Ids.is(IdKind.Expression, a), a)
  }
