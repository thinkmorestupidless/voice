package voice.features

import com.thinkmorestupidless.ankka.testkit.GherkinSuite.StepBody
import voice.support.TestIssuer

/** features/action/human-authority.feature (US5). */
class ActionSuite extends VoiceFeatureSuite("features/action/human-authority.feature"):

  private var issue: String           = ""
  private var shown: ujson.Value      = ujson.Null
  private var action: ujson.Value     = ujson.Null
  private var outcome: ujson.Value    = ujson.Null
  private var role: String            = ""
  private var refusal: Option[Answer] = None
  private var restrictedText          = ""
  private var privateText             = ""

  override def beforeEach(context: BeforeEach): Unit =
    issue = ""; shown = ujson.Null; action = ujson.Null; outcome = ujson.Null; role = "";
    refusal = None
    restrictedText = ""; privateText = ""
    super.beforeEach(context)

  /** The test kit's step bodies stop at six values; the chronology step names seven labels. */
  private given seven: StepBody[(String, String, String, String, String, String, String) => Unit]
  with
    def arity = 7
    def run(
        body: (String, String, String, String, String, String, String) => Unit,
        v: Seq[Any]
    ): Unit =
      body(
        v(0).toString,
        v(1).toString,
        v(2).toString,
        v(3).toString,
        v(4).toString,
        v(5).toString,
        v(6).toString
      )

  private def page = world.ok(world.public(s"/issues/$issue")).json

  Given("a public issue with published contributions") { () =>
    issue = Evidence.load().issue
    val text = Accountable.publish(world, "Alice", issue)
    eventually("the contribution is on the issue") {
      assert(page("contributions").arr.exists(_("text").str == text), page.toString)
    }
  }

  Given("a representative") { () =>
    world.ok(
      world.operator(
        "PUT",
        "/accounts/representative-1",
        Some(ujson.Obj("displayName" -> "Councillor A. Example (illustrative)"))
      )
    ): Unit
  }

  // ── the work queue ────────────────────────────────────────────────────────

  When("the representative opens the work queue") { () =>
    // Something of each kind: a fresh Issue, a contribution waiting, an investigation with a task
    // waiting on someone outside.
    Evidence.load(): Unit
    Flows.confirmedConcern(world, "Bob"): Unit
    Sharing.approve(world, "Bob", Sharing.uniqueDerivative()): Unit
    Sharing.submit(world, "Bob"): Unit
    Sharing.awaitingReview(world, "Bob"): Unit
    val inv = world
      .ok(
        world.representative(
          "POST",
          s"/issues/$issue/investigations",
          Some(ujson.Obj("question" -> "Who decided not to retender?"))
        )
      )
      .json("id")
      .str
    world.ok(
      world.representative(
        "POST",
        s"/investigations/$inv/tasks",
        Some(
          ujson.Obj(
            "text"  -> "Ask the operator for its records",
            "owner" -> "Route 14 operator",
            "due"   -> "2026-11-20",
            "state" -> "awaiting-external"
          )
        )
      )
    ): Unit
    shown = eventually("the queue shows each kind") {
      val q     = world.ok(world.representative("GET", "/queue")).json
      val kinds = q.arr.map(_("kind").str).toSet
      assert(
        Set("new issue", "pending review", "investigation", "external follow-up").subsetOf(kinds),
        kinds.toString
      )
      q
    }
  }

  Then(
    "the work queue shows new issues, items pending review, open investigations and external follow-ups"
  ) { () =>
    val kinds = shown.arr.map(_("kind").str).toSet
    assert(
      Set("new issue", "pending review", "investigation", "external follow-up").subsetOf(kinds),
      kinds.toString
    )
  }

  Then("each shows its state and its age") { () =>
    for item <- shown.arr do
      assert(item("state").str.nonEmpty, item.toString)
      assert(item("ageSeconds").num >= 0 && item.obj.contains("since"), item.toString)
  }

  // ── what the representative sees ──────────────────────────────────────────

  Given("a contribution shared as {string} and a concern kept {string}") {
    (shared: String, kept: String) =>
      restrictedText = Accountable.publish(world, "Bob", issue, shared)
      world.privateTexts += restrictedText
      Flows.confirmedConcern(world, "Carol"): Unit
      privateText = Sharing.uniqueDerivative() + " Only for me."
      Sharing.approve(world, "Carol", privateText, kept): Unit
      world.privateTexts += privateText
      world.privateTexts += Flows.BusMessage
  }

  When("the representative opens the issue") { () =>
    shown = eventually("the restricted copy is on the issue") {
      val v = world.ok(world.representative("GET", s"/issues/$issue")).json
      assert(v("restricted").arr.nonEmpty, v.toString)
      v
    }
  }

  Then("the representative sees the {string} contribution") { (_: String) =>
    assert(shown("restricted").arr.exists(_("text").str == restrictedText), shown.toString)
  }

  Then("the representative does not see the {string} concern or any original") { (_: String) =>
    val body = ujson.write(shown)
    assert(!body.contains(privateText), "the private concern is shown")
    assert(!body.contains(Flows.BusMessage), "an original message is shown")
  }

  Then("the measures shown are labelled as participant-derived") { () =>
    for m <- shown("measures").arr do
      assert(
        m("basis").str.contains("participants") || m("basis").str.contains("contributions"),
        m.toString
      )
      assert(m("method").str.startsWith("Self-selected"), m.toString)
      assert(m("caveat").str.nonEmpty, m.toString)
  }

  // ── a suggestion stays a suggestion ───────────────────────────────────────

  Given("the research agent suggests an investigation") { () =>
    val run = world
      .ok(
        world.representative(
          "POST",
          s"/issues/$issue/research",
          Some(ujson.Obj("question" -> "What should we look into first?"))
        ),
        202
      )
      .json("run")
      .str
    shown = eventually("the research run is done") {
      val r = world.ok(world.representative("GET", s"/research/$run")).json
      assertEquals(r("state").str, "done", r.toString)
      r
    }
  }

  When("no representative has approved the suggestion")(() => ())

  Then("the investigation is labelled {string}") { (label: String) =>
    assert(shown("investigations").arr.nonEmpty, shown.toString)
    for s <- shown("investigations").arr ++ shown("sources").arr ++ shown("questions").arr do
      assertEquals(s("label").str, label)
    for id <- shown("recorded").arr do
      val inv = world.ok(world.representative("GET", s"/investigations/${id.str}")).json
      assert(inv("state").str.contains(label), inv.toString)
      assert(inv("records").arr.nonEmpty && inv("reason").str.nonEmpty, inv.toString)
  }

  Then("no investigation is open") { () =>
    assertEquals(page("investigations").arr.size, 0)
    assert(!page("chronology").arr.exists(_("state").str == "under investigation"))
  }

  Then("no action is shown as performed") { () =>
    assertEquals(page("actions").arr.size, 0)
    assert(!page("chronology").arr.exists(_("state").str == "action recorded"))
  }

  // ── an action record ──────────────────────────────────────────────────────

  When(
    "the representative records an action with an authority, an owner, a date, a rationale, a state and a public summary"
  ) { () =>
    action = world
      .ok(
        world
          .representative("POST", s"/issues/$issue/actions", Some(Accountable.body("in-progress"))),
        201
      )
      .json
  }

  Then("the action is held with each of them") { () =>
    val held =
      page("actions").arr.find(_("id").str == action("id").str).getOrElse(fail(page.toString))
    val sent = Accountable.body("in-progress")
    assertEquals(held("authority").str, sent("authority").str)
    assertEquals(held("owner").str, sent("owner").str)
    assertEquals(held("date").str, sent("date").str)
    assertEquals(held("rationale").str, sent("rationale").str)
    assertEquals(held("state").str, "in progress")
    assertEquals(held("summary").str, sent("publicSummary").str)
    // Each field is refused when missing.
    for field <- Seq("authority", "owner", "rationale", "publicSummary", "state") do
      val missing = Accountable.body()
      missing.value.remove(field)
      assertEquals(
        world.representative("POST", s"/issues/$issue/actions", Some(missing)).rule,
        "action.fields.required",
        field
      )
    assertEquals(
      world
        .representative(
          "POST",
          s"/issues/$issue/actions",
          Some(Accountable.body(date = "2026-11-03"))
        )
        .rule,
      "action.date.future"
    )
    assertEquals(
      world
        .representative(
          "POST",
          s"/issues/$issue/actions",
          Some(Accountable.body(date = "2026-01-01"))
        )
        .rule,
      "action.date.before-issue"
    )
  }

  Then("the action shows who recorded it and when") { () =>
    val held = page("actions").arr.find(_("id").str == action("id").str).get
    assertEquals(held("recordedBy").str, "Councillor A. Example (illustrative)")
    assert(held("recordedAt").str.startsWith("2026-11-02"), held.toString)
    assert(!ujson.write(held).contains("representative-1"), "the token's subject is shown")
  }

  // ── only a representative ─────────────────────────────────────────────────

  Given("a person with the role {string}") { (r: String) => role = r }

  When("the person records an action on the issue") { () =>
    val subject = s"$role-person"
    refusal = Some(
      Harness.send(
        "POST",
        s"/staff/issues/$issue/actions",
        Some(Accountable.body()),
        Some(TestIssuer.token(subject, role))
      )
    )
  }

  Then("the person is refused") { () =>
    val a = refusal.get
    assertEquals(a.status, 403, a.toString)
    assertEquals(a.rule, "investigation.not-representative")
    assertEquals(page("actions").arr.size, 0)
    if role == "citizen" then
      // A citizen's own session is not a staff credential at all.
      val visitor = world.join("Visitor")
      assertEquals(
        Harness
          .send(
            "POST",
            s"/staff/issues/$issue/actions",
            Some(Accountable.body()),
            Some(visitor.session)
          )
          .status,
        401
      )
  }

  Then("the refusal is logged") { () =>
    eventually("the refusal reaches the access log") {
      val log = world.ok(world.operator("GET", "/access-log")).json.arr
      assert(
        log.exists(e =>
          e("principal").str == s"$role-person" && e("route").str.startsWith("refused POST")
        ),
        log.toString
      )
    }
  }

  // ── a proposed action ─────────────────────────────────────────────────────

  Given("an action in the state {string}") { (state: String) =>
    action = world
      .ok(
        world.representative("POST", s"/issues/$issue/actions", Some(Accountable.body(state))),
        201
      )
      .json
  }

  When("the action is shown anywhere") { () =>
    shown = ujson.Obj(
      "page"       -> page,
      "chronology" -> world.ok(world.public(s"/issues/$issue/chronology")).json
    )
  }

  Then("the action is not shown as completed") { () =>
    val held = shown("page")("actions").arr.find(_("id").str == action("id").str).get
    assert(!held("completed").bool, held.toString)
    assert(!held("state").str.contains("completed"), held.toString)
    assertEquals(held("state").str, "proposed, not started")
  }

  Then("the action is not shown as a commitment honoured") { () =>
    val text = ujson.write(shown).toLowerCase
    for word <- Seq("honoured", "honored", "delivered", "done", "fulfilled") do
      assert(!text.contains(word), word)
  }

  // ── the chronology ────────────────────────────────────────────────────────

  When("the chronology of the issue is shown") { () =>
    shown = world.ok(world.public(s"/issues/$issue/chronology")).json
  }

  Then(
    "every entry carries exactly one of the state labels {string}, {string}, {string}, {string}, {string}, {string} and {string}"
  ) { (a: String, b: String, c: String, d: String, e: String, f: String, g: String) =>
    val labels = Set(a, b, c, d, e, f, g)
    assertEquals(labels, Accountable.Labels)
    for entry <- shown.arr do assert(labels.contains(entry("state").str), entry.toString)
  }

  Then("an open item with no response from the representative remains shown as unanswered") { () =>
    val submitted = shown.arr.filter(_("state").str == "submitted")
    assert(submitted.nonEmpty, shown.toString)
    assert(submitted.forall(_("unanswered").bool), shown.toString)
    // A representative's response answers it.
    world.ok(
      world.representative(
        "POST",
        s"/issues/$issue/investigations",
        Some(ujson.Obj("question" -> "Why was the route withdrawn?"))
      )
    ): Unit
    val after = world.ok(world.public(s"/issues/$issue/chronology")).json.arr
    assert(
      after.filter(_("state").str == "submitted").forall(!_("unanswered").bool),
      after.toString
    )
  }

  // ── an outcome ────────────────────────────────────────────────────────────

  Given("an action and an outcome recorded for it") { () =>
    action = world
      .ok(
        world
          .representative("POST", s"/issues/$issue/actions", Some(Accountable.body("completed"))),
        201
      )
      .json
    outcome = world
      .ok(
        world.representative(
          "POST",
          s"/actions/${action("id").str}/outcomes",
          Some(
            ujson.Obj(
              "measured"    -> "Officers produced a costed footpath design.",
              "limitations" -> "A design, not a built path; no use has been measured."
            )
          )
        ),
        201
      )
      .json
    assertEquals(
      world
        .representative(
          "POST",
          s"/actions/act-00000000000000000000000000/outcomes",
          Some(ujson.Obj("measured" -> "x", "limitations" -> "y"))
        )
        .rule,
      "outcome.action.not-held"
    )
  }

  When("the outcome is shown") { () =>
    shown =
      page("outcomes").arr.find(_("id").str == outcome("id").str).getOrElse(fail(page.toString))
  }

  Then("the outcome links to the action it evaluates") { () =>
    assertEquals(shown("action").str, action("id").str)
    assert(shown("actionLink").str.contains(action("id").str), shown.toString)
  }

  Then("the outcome states what was measured and its limitations") { () =>
    assertEquals(shown("measured").str, "Officers produced a costed footpath design.")
    assert(shown("limitations").str.startsWith("A design, not a built path"), shown.toString)
  }

  Then("the outcome does not claim the problem is solved unless the outcome record says so") { () =>
    assert(!shown("claimsResolved").bool)
    assertEquals(shown("resolution").str, "The record does not say the problem is solved.")
  }
