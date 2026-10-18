package voice.features

import com.thinkmorestupidless.ankka.core.EntityId
import voice.application.erasure.{ParticipantRecordsEntity, Residue}
import voice.application.interests.{IssueFollowersEntity, InterestEntity}
import voice.application.participation.{DormancyTimedAction, ParticipantEntity}
import voice.domain.*
import voice.domain.disclosure.ContributionRecord
import voice.domain.erasure.*
import voice.domain.interests.Interest
import voice.support.DatabaseDump

import java.time.Duration

/** features/erasure/field-erasure.feature (US7). */
class ErasureSuite extends VoiceFeatureSuite("features/erasure/field-erasure.feature"):

  private var issue: String                    = ""
  private var captured: Vector[Sealed[String]] = Vector.empty
  private var answer: Answer                   = Answer(0, "")
  private var period: Duration                 = Duration.ofMinutes(10)

  override def beforeEach(context: BeforeEach): Unit =
    issue = ""; captured = Vector.empty; answer = Answer(0, ""); Harness.dormancy = None
    super.beforeEach(context)

  override def afterEach(context: AfterEach): Unit =
    Harness.dormancy = None
    super.afterEach(context)

  private def alice = world.person("Alice")
  private def bob   = world.person("Bob")

  /**
   * Alice's private words: what no record, view, backup or receipt may hold readable afterwards.
   */
  private def alicePrivate: Seq[String] = Seq(world.notes("Alice.marker"))

  private def erase(name: String): ErasureRecord =
    val p = world.person(name)
    captured = Erasing.sealedMessages(world.notes(s"$name.conversation"))
    world.ok(
      world.me(p, "POST", "/erasure", Some(ujson.Obj("recoveryCredential" -> p.recovery))),
      202
    ): Unit
    Erasing.completed(p.id)

  // ── background ────────────────────────────────────────────────────────────

  Given(
    "a participant {string} who has a conversation, a published de-identified derivative, a priority, an interest and an update in the inbox"
  ) { (name: String) =>
    val (_, i) = Erasing.footprint(world, name)
    issue = i
    Followers.follow(world, name, issue): Unit
    world.notes("action") = Followers.act(world, issue)("id").str
    eventually(s"$name has the update") {
      assertEquals(Followers.inbox(world, name).size, 1)
    }
  }

  Given("a participant {string} who contributed to the same issue") { (name: String) =>
    Erasing.contributes(world, name, issue): Unit
    val more = Followers.act(world, issue)("id").str
    world.notes("second-action") = more
    eventually(s"$name has the second update") {
      assertEquals(Followers.inbox(world, name).size, 1)
    }
  }

  // ── a request ─────────────────────────────────────────────────────────────

  When("{string} requests erasure") { (name: String) =>
    answer = world.me(world.person(name), "POST", "/erasure", Some(ujson.Obj()))
  }

  Then("the request is held") { () =>
    assertEquals(answer.status, 202, answer.toString)
    assertEquals(answer.json("rule").str, "erasure.unverified")
    assertEquals(answer.json("state").str, "HeldForOperator")
    assertEquals(world.ok(world.me(alice, "GET", "/erasure")).json("state").str, "HeldForOperator")
    // A second request answers unchanged.
    val again = world.me(alice, "POST", "/erasure", Some(ujson.Obj()))
    assertEquals((again.status, again.json("rule").str), (200, "erasure.already-requested"))
    // Only an operator sees the held requests.
    assertEquals(world.representative("GET", "/erasures").rule, "erasure.not-operator")
    eventually("the operator sees the held request") {
      val held = world.ok(world.operator("GET", "/erasures?state=held-for-operator")).json.arr
      assert(held.exists(_("participant").str == alice.id), held.toString)
    }
    // Nothing is erased while it is held.
    assert(Erasing.sealing.hasKey(alice.id))
    assertEquals(Erasing.record(alice.id).steps.map(_.step), Vector(ErasureStep.Verify))
  }

  Then("{string} is told what will be erased, what is retained and why, and what remains public") {
    (name: String) =>
      val j = answer.json
      assert(
        j("erased").arr.nonEmpty && j("retained").arr.nonEmpty && j("why").str.nonEmpty && j(
          "remainsPublic"
        ).arr.nonEmpty,
        j.toString
      )
      val data = world.ok(world.me(world.person(name), "GET", "/data")).json
      val held = data("held").arr.map(c => c("name").str -> c("count").num.toInt).toMap
      assertEquals(held("conversations"), 1)
      assertEquals(held("contributions"), 1)
      assertEquals(held("expressions"), 1)
      assertEquals(held("interests"), 1)
      assertEquals(held("notifications"), 2)
      assertEquals(data("erasure")("state").str, "HeldForOperator")
  }

  // ── verified and completed ────────────────────────────────────────────────

  Given("the erasure request of {string} is verified") { (name: String) =>
    val p = world.person(name)
    captured = Erasing.sealedMessages(world.notes(s"$name.conversation"))
    world.ok(world.me(p, "POST", "/erasure", Some(ujson.Obj())), 202): Unit
    eventually("the request is held") {
      assertEquals(Erasing.record(p.id).stage, ErasureStage.HeldForOperator)
    }
    val verified = world
      .ok(
        world.operator(
          "POST",
          s"/erasures/${p.id}/verify",
          Some(ujson.Obj("evidence" -> "asked in person (illustrative)"))
        )
      )
      .json
    assertEquals(verified("state").str, "InProgress")
  }

  When("the erasure completes") { () =>
    val r = Erasing.completed(alice.id)
    assertEquals(
      r.verification match {
        case Verification.OperatorVerified(p, _) => p; case other => other.toString
      },
      "operator-1"
    )
  }

  private def unreadable(): Unit =
    assert(!Erasing.sealing.hasKey(alice.id), "the key is still held")
    assert(captured.nonEmpty)
    for v <- captured do assertEquals(Erasing.opened(v), Opened.Redacted)
    assertEquals(world.me(alice, "GET", "/conversations").rule, "participant.erased")
    assertEquals(world.me(alice, "GET", "/inbox").status, 410)
    val held = Erasing.client
      .forEventSourcedEntity(EntityId(world.notes("Alice.conversation")))
      .call(voice.application.participation.ConversationEntity.get)
      .invoke()
    assert(held.messages.isEmpty, s"the conversation is still held: ${held.messages.size} messages")
    val recovered = Harness.send(
      "POST",
      "/me/sessions/recover",
      Some(ujson.Obj("recoveryCredential" -> alice.recovery))
    )
    assert(recovered.status >= 400, s"an erased participant recovered a session: $recovered")

  Then("every protected field of {string} is unreadable")((_: String) => unreadable())

  Then("every protected field of {string} is unreadable after the service restarts") {
    (_: String) =>
      Harness.restart()
      unreadable()
  }

  /**
   * A restore brings back the database, not the key store: the records replay, and the key is still
   * gone.
   */
  Then("every protected field of {string} is unreadable after a restore from a snapshot") {
    (_: String) =>
      Harness.restart()
      unreadable()
      assertEquals(
        Erasing.client
          .forEventSourcedEntity(EntityId(alice.id))
          .call(ParticipantEntity.summary)
          .invoke()
          .erasure,
        "completed"
      )
  }

  Given("the erasure of {string} has completed") { (name: String) =>
    erase(name): Unit
  }

  // ── rebuilt views ─────────────────────────────────────────────────────────

  When("the views and the search index are rebuilt from the records") { () =>
    val touched = Erasing.rebuildViews()
    assert(
      touched.exists(_.contains("issue_rows")) && !touched.exists(_.startsWith("failed")),
      touched.toString
    )
    eventually("the issue is listed again") {
      val listed = world.ok(world.public("/issues?limit=100")).json.arr
      assert(listed.exists(_("id").str == issue))
    }
  }

  Then("nothing of the erased content of {string} reappears") { (_: String) =>
    val dump = DatabaseDump.dump(Harness.kit.jdbcUrl)
    for secret <- alicePrivate do
      assert(!dump.contains(secret), s"the database still holds '$secret'")
    val found = world.ok(world.public(s"/search?q=lollipop")).json.toString
    for secret <- alicePrivate do
      assert(!found.contains(secret), "the search index holds Alice's words")
  }

  // ── the public issue ──────────────────────────────────────────────────────

  When("a reader reads the issue") { () =>
    answer = world.ok(world.public(s"/issues/$issue"))
  }

  Then("the issue's evidence, actions, decisions and anonymous chronology are intact") { () =>
    val page = answer.json
    assertEquals(page("actions").arr.size, 2)
    assert(page("chronology").arr.nonEmpty)
    val copy = page("contributions").arr
      .find(_("pub").str == world.notes("Alice.pub"))
      .getOrElse(fail("Alice's copy is gone"))
    assertEquals(copy("text").str, world.notes("Alice.derivative"))
    assertEquals(copy("authorErased").str, "requested")
    assert(page("contributions").arr.exists(_("text").str == world.notes("Bob.derivative")))
  }

  Then(
    "no reasonably identifying residue of {string} remains, in counts or in distinctive combinations"
  ) { (_: String) =>
    val page   = answer.json
    val counts = page("counts")
    assertEquals(counts("erasedOnRequest").num.toInt, 1)
    assertEquals(counts("erasedForDormancy").num.toInt, 0)
    assert(!answer.body.contains(alice.id))
    // The retained copy has no link back: the issue link is gone.
    val link = Erasing.client
      .forKeyValueEntity(EntityId(world.notes("Alice.pub")))
      .call(voice.application.issues.IssueLinkEntity.get)
      .invoke()
    assert(!link.exists, s"the link is still held: $link")
    // A copy flagged as possibly identifying would have been withdrawn instead (FR-057).
    val flagged = ContributionRecord.empty.copy(flags = Vector(PrivacyFlag.Distinctive))
    assertEquals(
      Residue.decide(flagged),
      ResidueResult.Suppressed(ResidueReason.DistinctiveCombination)
    )
    assertEquals(Erasing.record(alice.id).retained.map(_.result), Vector(ResidueResult.Retained))
  }

  // ── another participant ───────────────────────────────────────────────────

  When("{string} reads his contributions, interests and inbox") { (name: String) =>
    val p = world.person(name)
    world.notes("bob.contribution") = world
      .ok(world.me(p, "GET", s"/contributions/${Sharing.contributionOf(world, name)}"))
      .json
      .toString
    world.notes("bob.interests") = world.ok(world.me(p, "GET", "/interests")).json.toString
    world.notes("bob.inbox") = world.ok(world.me(p, "GET", "/inbox")).json.toString
  }

  Then("the contributions, interests and updates of {string} are intact") { (_: String) =>
    val contribution = ujson.read(world.notes("bob.contribution"))
    assertEquals(contribution("state").str, "published")
    assert(ujson.read(world.notes("bob.interests")).arr.exists(_("issue").str == issue))
    val inbox = ujson.read(world.notes("bob.inbox")).arr
    assertEquals(inbox.size, 1)
    assert(!inbox.head.toString.contains(Opened.Redacted), inbox.head.toString)
    assert(Erasing.sealing.hasKey(bob.id))
  }

  // ── the receipt ───────────────────────────────────────────────────────────

  When("the operator reads the receipt") { () =>
    answer = eventually("the receipt is listed") {
      val a = world.ok(world.operator("GET", "/erasures?state=completed"))
      val row =
        a.json.arr.find(_("participant").str == alice.id).getOrElse(fail("no receipt for Alice"))
      assert(row.obj.contains("receipt"), row.toString)
      Answer(200, ujson.write(row("receipt")))
    }
  }

  Then(
    "the receipt records the classes of data erased, when, who authorised it and that completion was verified"
  ) { () =>
    val r       = answer.json
    val classes = r("classes").arr.map(c => c("name").str -> c("count").num.toInt).toMap
    assertEquals(classes("conversations"), 1)
    assertEquals(classes("contributions"), 1)
    assertEquals(classes("public-copies-retained"), 1)
    assertEquals(classes("key"), 1)
    assertEquals(r("cause").str, "requested")
    assertEquals(r("authorisedBy").str, "participant")
    assertEquals(r("verifiedBy").str, "session")
    assert(r("at").str.nonEmpty)
    assert(r("completionVerified").bool)
  }

  Then("the receipt contains none of the erased content") { () =>
    for secret <- alicePrivate :+ Flows.BusMessage :+ world.notes("Alice.derivative") do
      assert(!answer.body.contains(secret), s"the receipt holds '$secret'")
  }

  // ── nothing further ───────────────────────────────────────────────────────

  When("a representative records an action on the issue") { () =>
    val action = Followers.act(world, issue)("id").str
    val d      = Followers.developmentOf(issue, action).developmentId
    world.notes("late") = d
    Followers.processed(d): Unit
  }

  Then("no update is prepared for {string}") { (_: String) =>
    val d = world.notes("late")
    assert(
      !Followers.notification(alice.id, d).exists,
      "an update was prepared for an erased participant"
    )
    eventually("Bob has the late update")(assertEquals(Followers.inbox(world, "Bob").size, 2))
  }

  Then("the inbox and interests of {string} are gone") { (_: String) =>
    val interest = Erasing.client
      .forKeyValueEntity(EntityId(Interest.id(alice.id, issue)))
      .call(InterestEntity.get)
      .invoke()
    assert(!interest.active, interest.toString)
    val followers =
      Erasing.client.forKeyValueEntity(EntityId(issue)).call(IssueFollowersEntity.get).invoke()
    assert(!followers.participants.contains(alice.id))
    val index = Erasing.client
      .forKeyValueEntity(EntityId(alice.id))
      .call(ParticipantRecordsEntity.get)
      .invoke()
    assertEquals(index, ErasureScope())
    assertEquals(world.me(alice, "GET", "/inbox").rule, "participant.erased")
  }

  // ── rendering ─────────────────────────────────────────────────────────────

  When("a record of {string} with a protected field is shown") { (_: String) =>
    answer = world.ok(world.public(s"/issues/$issue"))
  }

  Then("the field is shown as {string}") { (marker: String) =>
    assertEquals(Opened.Redacted, marker)
    for v <- captured do assertEquals(Erasing.opened(v), marker)
  }

  // ── dormancy ──────────────────────────────────────────────────────────────

  Given(
    "a participant {string} with a published de-identified derivative whose last session ended longer ago than the dormancy period"
  ) { (name: String) =>
    Harness.dormancy = Some(period)
    val (p, i) = Erasing.footprint(world, name)
    Harness.dormancy = None
    world.notes(s"$name.issue") = i
    assert(
      Harness.probe.scheduled(DormancyTimedAction.timerName(p.id)).isDefined,
      "no dormancy timer was scheduled"
    )
  }

  When("the dormancy period is checked") { () =>
    Harness.clock.advance(period.plusMinutes(1))
  }

  Then("{string} is erased with the cause {string}") { (name: String, cause: String) =>
    val r = Erasing.completed(world.person(name).id)
    assertEquals(r.cause.map(DataRightsWire.wire), Some(cause))
    assertEquals(r.receipt.map(_.authorisedBy), Some(Authority.DormancyRule))
    assertEquals(r.verification, Verification.NotRequired)
  }

  Then("the retained derivative on the issue carries the cause {string}") { (cause: String) =>
    val page = world.ok(world.public(s"/issues/${world.notes("Carol.issue")}")).json
    val copy = page("contributions").arr
      .find(_("pub").str == world.notes("Carol.pub"))
      .getOrElse(fail("Carol's copy is gone"))
    assertEquals(copy("authorErased").str, cause)
  }

  Then("the issue's participant count shows how many authors were erased and why") { () =>
    val counts = world.ok(world.public(s"/issues/${world.notes("Carol.issue")}")).json("counts")
    assertEquals(counts("erasedForDormancy").num.toInt, 1)
    assertEquals(counts("authorsErased").num.toInt, 1)
    assert(counts("caveat").str.nonEmpty)
  }

  Given("a participant {string} whose last session ended one day before the dormancy period ends") {
    (name: String) =>
      Harness.dormancy = Some(period)
      world.join(name): Unit
      Harness.clock.advance(period.minusMinutes(1))
  }

  When("{string} recovers the session") { (name: String) =>
    val p = world.person(name)
    val reply = world
      .ok(
        Harness
          .send("POST", "/me/sessions/recover", Some(ujson.Obj("recoveryCredential" -> p.recovery)))
      )
      .json
    world.people(name) =
      p.copy(session = reply("session").str, recovery = reply("recoveryCredential").str)
    world.notes("recovered-at") = Harness.clock.now().toString
    Harness.dormancy = None
    Harness.clock.advance(Duration.ofMinutes(2))
    Thread.sleep(1500)
  }

  Then("nothing of {string} is erased") { (name: String) =>
    val p = world.person(name)
    assertEquals(Erasing.record(p.id).stage, ErasureStage.NotRequested)
    assert(Erasing.sealing.hasKey(p.id))
    world.ok(world.me(p, "GET", "/conversations")): Unit
  }

  Then("the dormancy period for {string} starts again") { (name: String) =>
    val p = world.person(name)
    val timer = Harness.probe
      .scheduled(DormancyTimedAction.timerName(p.id))
      .getOrElse(fail("the dormancy timer is gone"))
    val from = java.time.Instant.parse(world.notes("recovered-at"))
    assertEquals(timer.dueTime, from.plus(period))
    Harness.timers.timerScheduler.delete(DormancyTimedAction.timerName(p.id))
  }

/** The cause as the API writes it. */
object DataRightsWire:
  def wire(c: ErasureCause): String = voice.api.DataRightsJson.wire(c)
