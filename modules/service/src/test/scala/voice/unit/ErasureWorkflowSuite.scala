package voice.unit

import com.thinkmorestupidless.ankka.agent.TestModelProvider
import com.thinkmorestupidless.ankka.core.EntityId
import com.thinkmorestupidless.ankka.core.effect.ViewEffect
import com.thinkmorestupidless.ankka.http.HttpServer
import com.thinkmorestupidless.ankka.testkit.{AnkkaTestKit, TimerProbe}
import voice.VoiceService
import voice.application.erasure.*
import voice.application.participation.*
import voice.domain.*
import voice.domain.disclosure.ContributionRecord
import voice.domain.erasure.*
import voice.sealing.Sealing
import voice.support.*

import java.time.Instant
import scala.concurrent.duration.DurationInt

/**
 * The erasure workflow's stages and refusals, the residue rule and the receipts view (T094, T095).
 */
class ErasureWorkflowSuite extends munit.FunSuite:

  override val munitTimeout = 240.seconds

  private val at = Instant.parse("2026-11-02T09:00:00Z")

  private lazy val kit: AnkkaTestKit =
    val agents = VoiceService.agents(TestModelProvider())
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

  private def p(id: String) = Provenance(Actor.Participant(id), at, "test", s"c-$id")

  private def participant(id: String): Unit =
    Sealing(kit.secrets).mintKey(id)
    kit.componentClient
      .forEventSourcedEntity(EntityId(id))
      .call(ParticipantEntity.create)
      .invoke(CreateParticipant("hash", p(id))): Unit

  private def flow(id: String) = kit.componentClient.forWorkflow(EntityId(id))

  private def until(what: String)(done: ErasureRecord => Boolean)(id: String): ErasureRecord =
    val deadline = System.currentTimeMillis() + 60_000
    var r        = flow(id).call(ErasureWorkflow.status).invoke()
    while !done(r) && System.currentTimeMillis() < deadline do
      Thread.sleep(150)
      r = flow(id).call(ErasureWorkflow.status).invoke()
    assert(done(r), s"$id: $what did not happen; it is ${r.stage} after ${r.steps.map(_.step)}")
    r

  private def stage(id: String, wanted: ErasureStage): ErasureRecord =
    until(s"reaching $wanted")(_.stage == wanted)(id)

  test(
    "an unverified request waits for an operator, answers a repeat unchanged, then erases once verified"
  ) {
    val id = "prt-01M6EUNIT0000000000000001"
    participant(id)
    val started = flow(id)
      .call(ErasureWorkflow.start)
      .invoke(
        StartErasure(
          ErasureCause.Requested,
          Verification.HeldForOperator(VerificationGap.NoRecoveryCredential),
          p(id)
        )
      )
    assertEquals(started.stage, ErasureStage.HeldForOperator)
    val held = until("the verify step")(_.done(ErasureStep.Verify))(id)
    assertEquals(
      held.steps.map(_.step),
      Vector(ErasureStep.Verify),
      "nothing past verify runs while held"
    )
    assert(Sealing(kit.secrets).hasKey(id))
    val again = flow(id)
      .call(ErasureWorkflow.start)
      .invoke(StartErasure(ErasureCause.Dormancy, Verification.NotRequired, p(id)))
    assertEquals(
      (again.stage, again.cause),
      (ErasureStage.HeldForOperator, Some(ErasureCause.Requested)),
      "erasure.already-requested"
    )
    flow(id).call(ErasureWorkflow.operatorVerify).invoke(VerifyErasure("operator-1", at)): Unit
    val done = stage(id, ErasureStage.Completed)
    assertEquals(done.steps.map(_.step), ErasureStep.values.toVector)
    assert(!Sealing(kit.secrets).hasKey(id))
    assertEquals(done.receipt.map(_.authorisedBy), Some(Authority.Operator("operator-1")))
    assert(done.receipt.exists(_.completionVerified))
    val summary = kit.componentClient
      .forEventSourcedEntity(EntityId(id))
      .call(ParticipantEntity.summary)
      .invoke()
    assertEquals(summary.erasure, "completed")
  }

  test("a dormancy erasure needs no verification, and a verify after completion changes nothing") {
    val id = "prt-01M6EUNIT0000000000000002"
    participant(id)
    flow(id)
      .call(ErasureWorkflow.start)
      .invoke(StartErasure(ErasureCause.Dormancy, Verification.NotRequired, p(id))): Unit
    val done = stage(id, ErasureStage.Completed)
    assertEquals(
      done.receipt.map(r => (r.cause, r.authorisedBy)),
      Some((ErasureCause.Dormancy, Authority.DormancyRule))
    )
    val after =
      flow(id).call(ErasureWorkflow.operatorVerify).invoke(VerifyErasure("operator-1", at))
    assertEquals(after.verification, Verification.NotRequired)
  }

  test("a copy flagged as possibly identifying is suppressed; an unflagged one is retained") {
    assertEquals(Residue.decide(ContributionRecord.empty), ResidueResult.Retained)
    for flag <- PrivacyFlag.values do
      assertEquals(
        Residue.decide(ContributionRecord.empty.copy(flags = Vector(flag))),
        ResidueResult.Suppressed(ResidueReason.DistinctiveCombination)
      )
  }

  test("the receipts view rows an erasure by stage and cause, and nothing before it starts") {
    val view = ErasureReceiptsView()
    assertEquals(view.onChange(ErasureRecord.empty), ViewEffect.Ignore)
    val held = ErasureRecord.empty.copy(
      participantId = "prt-x",
      cause = Some(ErasureCause.Requested),
      stage = ErasureStage.HeldForOperator,
      requestedAt = Some(at)
    )
    view.onChange(held) match
      case ViewEffect.UpdateRow(row: ErasureRow) =>
        assertEquals(
          (row.stage, row.held, row.cause, row.receipt),
          ("held-for-operator", true, Some(ErasureCause.Requested), None)
        )
      case other => fail(s"unexpected $other")
  }
