package voice.features

/** features/participation/private-conversation.feature (US1). */
class ParticipationSuite
    extends VoiceFeatureSuite("features/participation/private-conversation.feature"):

  private var started: Option[Answer] = None
  private var conv: String            = ""
  private var turn: ujson.Value       = ujson.Null
  private var proposal: ujson.Value   = ujson.Null
  private var concern: ujson.Value    = ujson.Null
  private var earlier: Option[Person] = None

  override def beforeEach(context: BeforeEach): Unit =
    started = None; conv = ""; turn = ujson.Null; proposal = ujson.Null; concern = ujson.Null;
    earlier = None
    super.beforeEach(context)

  private def alice = world.person("Alice")

  // ── a citizen starts with no identity ──────────────────────────────────────

  Given("a visitor with an invite code and no account")(() => ())

  When("the visitor starts a session") { () =>
    val answer =
      world.ok(Harness.send("POST", "/me/sessions", Some(ujson.Obj("invite" -> "pilot"))))
    started = Some(answer)
    val j = answer.json
    world.people("Visitor") =
      Person("Visitor", j("participant").str, j("session").str, j("recoveryCredential").str)
  }

  Then("the visitor is a participant with no name, no email and no verified identity") { () =>
    val j = started.get.json.obj
    assert(j("participant").str.startsWith("prt-"))
    assertEquals(
      j.keySet.toSet -- Set("participant", "recoveryCredential", "session", "expiresAt", "notice"),
      Set.empty[String]
    )
    assert(!started.get.body.toLowerCase.contains("email"))
  }

  Then(
    "the participant is shown the privacy notice and the AI notice before the first message is accepted"
  ) { () =>
    val notice = started.get.json("notice")
    assert(notice("privacy").str.contains("private"), notice.toString)
    assert(notice("ai").str.contains("AI"), notice.toString)
    // The notice is the session's first answer: no conversation exists until the participant starts one.
    val visitor = world.person("Visitor")
    assertEquals(world.ok(world.me(visitor, "GET", "/conversations")).json.arr.size, 0)
  }

  Then("the participant is given a recovery credential once") { () =>
    val visitor = world.person("Visitor")
    assert(visitor.recovery.startsWith(visitor.id + "~"))
    val renewed = world.ok(world.me(visitor, "POST", "/sessions/renew", Some(ujson.Obj())))
    assert(!renewed.json.obj.contains("recoveryCredential"), renewed.toString)
  }

  // ── a clarifying question ──────────────────────────────────────────────────

  Given("a participant describing a concern in a conversation") { () =>
    world.join("Alice")
    conv = Flows.conversation(world, alice)
  }

  When("Voice asks a clarifying question") { () =>
    turn = Flows.say(world, alice, conv, Flows.VagueMessage)
  }

  Then("the question proposes no position, no cause and no culprit") { () =>
    assertEquals(turn("status").str, "Replied")
    assert(!turn.obj.contains("proposal"), turn.toString)
    val reply = turn("reply").str.toLowerCase
    assert(reply.contains("?"), reply)
    for word <- Seq("because", "fault", "blame", "should", "caused by", "responsible") do
      assert(!reply.contains(word), s"the question proposes something: $reply")
  }

  Then("the participant may decline the question and continue") { () =>
    val next = Flows.say(world, alice, conv, Flows.DeclineMessage)
    assertEquals(next("status").str, "Replied")
    assert(next("reply").str.nonEmpty)
  }

  // ── a proposal ─────────────────────────────────────────────────────────────

  Given("a conversation holding a concern in the participant's own words") { () =>
    world.join("Alice")
    conv = Flows.conversation(world, alice)
  }

  When("Voice proposes a structured concern") { () =>
    turn = Flows.say(world, alice, conv, Flows.BusMessage)
    proposal = turn("proposal")
  }

  Then(
    "the proposal shows the original statement, a summary, the passage each element came from, a confidence and the related issues it suggests"
  ) { () =>
    assertEquals(proposal("original").str, Flows.BusMessage)
    assert(proposal("summary").str.nonEmpty)
    assert(proposal("elements").arr.nonEmpty)
    for element <- proposal("elements").arr; span <- element("spans").arr do
      assert(Flows.BusMessage.contains(span("passage").str), span.toString)
      assert(span("passage").str.nonEmpty)
    assert(Set("Low", "Medium", "High").contains(proposal("confidence").str))
    assert(proposal.obj.contains("suggestedIssues"))
  }

  Then("no contribution exists") { () =>
    val person   = world.people.values.head
    val concerns = world.ok(world.me(person, "GET", "/concerns")).json.arr
    assert(concerns.forall(_("contributions").arr.isEmpty), concerns.toString)
  }

  // ── confirmation ───────────────────────────────────────────────────────────

  Given("a proposal in a conversation") { () =>
    val (_, c, p) = Flows.proposal(world, "Alice")
    conv = c; proposal = p
  }

  When("the participant edits the summary and confirms the proposal") { () =>
    val id = proposal("id").str
    world.privateTexts += Flows.EditedSummary
    val edited = world.ok(
      world.me(
        alice,
        "POST",
        s"/conversations/$conv/proposals/$id/edit",
        Some(ujson.Obj("summary" -> Flows.EditedSummary))
      )
    )
    assert(edited.json("edited").bool)
    concern = world
      .ok(world.me(alice, "POST", s"/conversations/$conv/proposals/$id/confirm", Some(ujson.Obj())))
      .json
  }

  Then("the concern holds the edited summary") { () =>
    assertEquals(concern("summary").str, Flows.EditedSummary)
    eventually("the concern is listed") {
      val listed = world.ok(world.me(alice, "GET", "/concerns")).json.arr
      assert(listed.exists(_("summary").str == Flows.EditedSummary), listed.toString)
    }
  }

  Then("the concern is confirmed by the participant and not by Voice") { () =>
    assertEquals(concern("confirmedBy").str, "participant")
  }

  Given("a confirmed concern") { () =>
    val (_, c, k) = Flows.confirmedConcern(world, "Alice")
    conv = c; concern = k
  }

  When("the participant does nothing further")(() => ())

  Then("public search finds neither the conversation nor the concern") { () =>
    for q <- Seq("Kingsway", "Northfield", "daughter") do
      val answer = world.public(s"/search?q=$q")
      assert(Set(200, 404).contains(answer.status), answer.toString)
      assert(
        !answer.body.contains(conv) && !answer.body.contains(concern("id").str),
        answer.toString
      )
      assert(!answer.body.contains("daughter"), answer.toString)
  }

  // ── recovery ───────────────────────────────────────────────────────────────

  Given("a participant with a conversation and a confirmed concern who has closed the session") {
    () =>
      val (_, c, k) = Flows.confirmedConcern(world, "Alice")
      conv = c; concern = k
      world.people("Alice") = alice.copy(session = "")
  }

  When("the participant recovers the session with the recovery credential") { () =>
    Harness.restart()
    val old = alice.recovery
    val answer = world.ok(
      Harness.send("POST", "/me/sessions/recover", Some(ujson.Obj("recoveryCredential" -> old)))
    )
    val j = answer.json
    world.people("Alice") =
      alice.copy(session = j("session").str, recovery = j("recoveryCredential").str)
    assertNotEquals(j("recoveryCredential").str, old)
    // The old credential is rotated: it no longer recovers anything.
    val again =
      Harness.send("POST", "/me/sessions/recover", Some(ujson.Obj("recoveryCredential" -> old)))
    assertEquals(again.rule, "participant.credential.invalid")
  }

  Then("the participant finds the conversation and the confirmed concern") { () =>
    eventually("the conversation and the concern are listed") {
      val conversations = world.ok(world.me(alice, "GET", "/conversations")).json.arr
      assert(conversations.exists(_("id").str == conv), conversations.toString)
      val concerns = world.ok(world.me(alice, "GET", "/concerns")).json.arr
      assert(
        concerns.exists(c =>
          c("id").str == concern("id").str && c("summary").str == concern("summary").str
        ),
        concerns.toString
      )
    }
  }

  Given("a participant with a conversation who has closed the session") { () =>
    val person = world.join("Alice")
    conv = Flows.conversation(world, person)
    earlier = Some(person)
  }

  When("a visitor starts a session without the recovery credential") { () =>
    world.join("Visitor"): Unit
  }

  Then("the visitor is a new participant") { () =>
    assertNotEquals(world.person("Visitor").id, earlier.get.id)
  }

  Then("the earlier conversation is not reachable") { () =>
    val visitor = world.person("Visitor")
    val answer  = world.me(visitor, "GET", s"/conversations/$conv")
    assertEquals(answer.status, 403)
    assertEquals(answer.rule, "conversation.not-owned")
    assertEquals(world.ok(world.me(visitor, "GET", "/conversations")).json.arr.size, 0)
  }

  // ── instructions are content ───────────────────────────────────────────────

  Given("a conversation") { () =>
    world.join("Alice")
    conv = Flows.conversation(world, alice)
  }

  When("the participant sends a message containing instructions aimed at Voice") { () =>
    turn = Flows.say(world, alice, conv, Flows.InjectedMessage)
  }

  Then("the message is held as the participant's words") { () =>
    val view = world.ok(world.me(alice, "GET", s"/conversations/$conv")).json
    assert(
      view("messages").arr.exists(m =>
        m("author").str == "citizen" && m("text").str == Flows.InjectedMessage
      ),
      view.toString
    )
  }

  Then("nothing changes but the conversation") { () =>
    val view = world.ok(world.me(alice, "GET", s"/conversations/$conv")).json
    assertEquals(view("proposals").arr.size, 0)
    assertEquals(world.ok(world.me(alice, "GET", "/concerns")).json.arr.size, 0)
  }

  Then("Voice does not say it obeyed the instructions") { () =>
    val reply = turn("reply").str.toLowerCase
    for word <- Seq("published", "ignoring", "ignored", "as instructed", "website") do
      assert(!reply.contains(word), reply)
  }
