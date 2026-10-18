package voice.features

/** features/disclosure/exact-approval.feature (US2). */
class DisclosureSuite extends VoiceFeatureSuite("features/disclosure/exact-approval.feature"):

  private var sharing: ujson.Value       = ujson.Null
  private var contribution: ujson.Value  = ujson.Null
  private var issue: String              = ""
  private var suggested: String          = ""
  private var text: String               = ""
  private var conversationBefore: String = ""

  override def beforeEach(context: BeforeEach): Unit =
    sharing = ujson.Null; contribution = ujson.Null; issue = ""; suggested = "";
    text = Sharing.uniqueDerivative(); conversationBefore = ""
    super.beforeEach(context)

  private def alice = world.person("Alice")
  private def mine(): ujson.Value =
    world
      .ok(world.me(alice, "GET", s"/contributions/${Sharing.contributionOf(world, "Alice")}"))
      .json

  Given("a participant with a confirmed concern") { () =>
    val (_, _, concern) = Flows.confirmedConcern(world, "Alice")
    // The concern's own words are as private as the conversation they came from.
    world.privateTexts += concern("summary").str
  }

  // ── the four modes ─────────────────────────────────────────────────────────

  When("the participant opens sharing for the concern") { () =>
    sharing = world
      .ok(world.me(alice, "GET", s"/concerns/${Sharing.concernOf(world, "Alice")}/sharing"))
      .json
  }

  Then("the sharing modes offered are {string}, {string}, {string} and {string}") {
    (a: String, b: String, c: String, d: String) =>
      assertEquals(sharing("modes").arr.map(_("label").str).toSeq, Seq(a, b, c, d))
  }

  Then("each sharing mode is explained in plain words") { () =>
    for m <- sharing("modes").arr do
      val words = m("explanation").str
      assert(words.split(' ').length >= 8, words)
      assert(m("audience").str.nonEmpty)
      for jargon <- Seq("derivative", "workflow", "pseudonym", "de-identification", "ACL", "entity")
      do assert(!words.toLowerCase.contains(jargon.toLowerCase), s"'$jargon' in: $words")
  }

  Then("{string} is the sharing mode already chosen") { (label: String) =>
    assertEquals(
      sharing("modes").arr.filter(_("chosen").bool).map(_("label").str).toSeq,
      Seq(label)
    )
  }

  // ── an edited derivative ───────────────────────────────────────────────────

  Given("Voice proposes a derivative containing an identifying detail") { () =>
    sharing = world
      .ok(world.me(alice, "GET", s"/concerns/${Sharing.concernOf(world, "Alice")}/sharing"))
      .json
    assert(sharing("detections").arr.nonEmpty, sharing.toString)
    assert(sharing("derivative").str.contains("daughter"), sharing.toString)
  }

  When("the participant removes the detail and approves the derivative as {string}") {
    (mode: String) =>
      contribution = Sharing.approve(world, "Alice", text, mode)
  }

  Then("the original is unchanged and private") { () =>
    val conv =
      world.ok(world.me(alice, "GET", s"/conversations/${world.notes("Alice.conversation")}")).json
    assertEquals(conv("messages").arr.head("text").str, Flows.BusMessage)
    assert(!contribution.toString.contains(Flows.BusMessage))
  }

  Then("the derivative is the edited text and nothing else") { () =>
    assertEquals(contribution("text").str, text)
    assertEquals(contribution("state").str, "approved by you")
    assert(!contribution.obj.contains("original"), contribution.toString)
  }

  Then("only the derivative is eligible for a shared issue") { () =>
    val offered = world
      .ok(world.me(alice, "GET", s"/concerns/${Sharing.concernOf(world, "Alice")}/sharing"))
      .json
    assertEquals(offered("contributions").arr.map(_("text").str).toSeq, Seq(text))
    Sharing.submit(world, "Alice"): Unit
    val item = Sharing.awaitingReview(world, "Alice")
    assertEquals(item("derivative").str, text)
  }

  // ── awaiting review ────────────────────────────────────────────────────────

  Given("a derivative approved by the participant as {string}") { (mode: String) =>
    contribution = Sharing.approve(world, "Alice", text, mode)
    // Not yet public: until a reviewer approves it, it is as private as the conversation.
    world.privateTexts += text
  }

  When("the participant submits the derivative") { () =>
    contribution = Sharing.submit(world, "Alice")
  }

  Then("the reviewer sees the derivative") { () =>
    assertEquals(Sharing.awaitingReview(world, "Alice")("derivative").str, text)
  }

  Then("nobody else sees the derivative") { () =>
    val id = Sharing.contributionOf(world, "Alice")
    assertEquals(world.representative("GET", "/reviews").rule, "review.not-reviewer")
    val bob   = world.join("Bob")
    val other = world.me(bob, "GET", s"/contributions/$id")
    assertEquals((other.status, other.rule), (403, "contribution.not-owned"))
    assert(!world.public("/issues").body.contains(text))
  }

  Then("the participant sees the contribution as {string}") { (label: String) =>
    assertEquals(mine()("state").str, label)
  }

  Then("no public record of the derivative exists") { () =>
    val listed = world.ok(world.public("/issues")).json.arr
    for i <- listed do assert(!world.public(s"/issues/${i("id").str}").body.contains(text))
  }

  // ── approval and rejection ─────────────────────────────────────────────────

  Given("a derivative under moderation") { () =>
    Sharing.approve(world, "Alice", text): Unit
    Sharing.submit(world, "Alice"): Unit
    Sharing.awaitingReview(world, "Alice"): Unit
  }

  When("the reviewer approves the derivative into an issue") { () =>
    val id = Sharing.contributionOf(world, "Alice")
    val item = world
      .ok(
        world.reviewer(
          "POST",
          s"/reviews/$id/approve",
          Some(ujson.Obj("new" -> ujson.Obj("title" -> "School transport to Kingsway Academy")))
        )
      )
      .json
    assertEquals(item("status").str, "Published")
    issue = item("issue").str
  }

  Then("the derivative is shown on the issue to its audience") { () =>
    val page = world.ok(world.public(s"/issues/$issue")).json
    assert(page("contributions").arr.exists(_("text").str == text), page.toString)
    assert(
      page("contributions").arr.forall(!_.obj.contains("attribution")),
      "a de-identified copy carries no name"
    )
  }

  Then("the sharing mode is recorded with the contribution") { () =>
    val view = mine()
    assertEquals(
      (view("mode").str, view("state").str, view("issue").str),
      ("de-identified", "published", issue)
    )
  }

  When("the reviewer rejects the derivative with a reason") { () =>
    world.privateTexts += text
    val id = Sharing.contributionOf(world, "Alice")
    val item = world
      .ok(
        world.reviewer(
          "POST",
          s"/reviews/$id/reject",
          Some(ujson.Obj("reason" -> "It repeats a contribution already on the issue."))
        )
      )
      .json
    assertEquals(item("status").str, "Rejected")
  }

  Then("the participant sees the rejection and its reason") { () =>
    val view = mine()
    assertEquals(view("state").str, "not published")
    assertEquals(
      (view("review")("outcome").str, view("review")("reason").str),
      ("rejected", "It repeats a contribution already on the issue.")
    )
  }

  Then("nothing is published") { () =>
    assert(!mine().obj.contains("issue"))
    for i <- world.ok(world.public("/issues")).json.arr do
      assert(!world.public(s"/issues/${i("id").str}").body.contains(text))
  }

  // ── association ────────────────────────────────────────────────────────────

  Given("a derivative approved by the participant and a suggested issue it matches") { () =>
    Flows.confirmedConcern(world, "Bob"): Unit
    suggested = Sharing.published(
      world,
      "Bob",
      "The Kingsway Academy school bus was withdrawn in September.",
      "Kingsway Academy school bus withdrawn"
    )
    contribution = eventually("the issue is suggested") {
      val view = Sharing.approve(world, "Alice", text)
      assert(view("suggestedIssues").arr.exists(_("issue").str == suggested), view.toString)
      view
    }
  }

  When("the participant accepts the suggested issue") { () =>
    conversationBefore =
      world.ok(world.me(alice, "GET", s"/conversations/${world.notes("Alice.conversation")}")).body
    contribution = world
      .ok(
        world.me(
          alice,
          "POST",
          s"/contributions/${Sharing.contributionOf(world, "Alice")}/issue",
          Some(ujson.Obj("accept" -> suggested))
        )
      )
      .json
  }

  Then("the association of the contribution with the issue is recorded apart from the original") {
    () =>
      assertEquals(contribution("association")("issue").str, suggested)
      val conv = world
        .ok(world.me(alice, "GET", s"/conversations/${world.notes("Alice.conversation")}"))
        .body
      assertEquals(
        conv,
        conversationBefore,
        "the conversation does not change when the association is recorded"
      )
      val concerns = world.ok(world.me(alice, "GET", "/concerns")).body
      assert(!concerns.contains(suggested), "the concern does not hold the association")
  }

  Then("the association can be reviewed and changed later") { () =>
    val id = Sharing.contributionOf(world, "Alice")
    val changed = world
      .ok(world.me(alice, "POST", s"/contributions/$id/issue", Some(ujson.Obj("new" -> true))))
      .json
    assertEquals(
      (changed("association").obj.contains("issue"), changed("association")("newIssueAsked").bool),
      (false, true)
    )
    world.ok(
      world.me(alice, "POST", s"/contributions/$id/issue", Some(ujson.Obj("accept" -> suggested)))
    ): Unit
    Sharing.submit(world, "Alice"): Unit
    assertEquals(Sharing.awaitingReview(world, "Alice")("association")("issue").str, suggested)
  }

  // ── withdrawal ─────────────────────────────────────────────────────────────

  Given("a derivative published on an issue") { () =>
    issue = Sharing.published(world, "Alice", text)
    assertEquals(
      world.ok(world.public(s"/issues/$issue")).json("counts")("contributions").num.toInt,
      1
    )
  }

  When("the participant withdraws the disclosure") { () =>
    contribution = world
      .ok(
        world.me(
          alice,
          "POST",
          s"/contributions/${Sharing.contributionOf(world, "Alice")}/withdraw",
          Some(ujson.Obj())
        )
      )
      .json
    assertEquals(contribution("state").str, "withdrawn")
  }

  Then("the derivative is no longer shown to its audience") { () =>
    assert(!world.ok(world.public(s"/issues/$issue")).body.contains(text))
  }

  Then("the issue's participant count no longer includes the contribution") { () =>
    val counts = world.ok(world.public(s"/issues/$issue")).json("counts")
    assertEquals((counts("contributions").num.toInt, counts("withdrawn").num.toInt), (0, 1))
    eventually("the listing's counts move") {
      val row = world
        .ok(world.public("/issues"))
        .json
        .arr
        .find(_("id").str == issue)
        .getOrElse(fail(s"$issue is not listed"))
      assertEquals(row("counts")("contributions").num.toInt, 0)
    }
  }

  Then("the chronology of the issue records a withdrawal without the withdrawn text") { () =>
    val chronology = world.ok(world.public(s"/issues/$issue/chronology"))
    assert(chronology.json.arr.exists(_("note").str.contains("withdrawn")), chronology.toString)
    assert(!chronology.body.contains(text))
    world.privateTexts += text
  }

  // ── public reading ─────────────────────────────────────────────────────────

  Given("a conversation, a confirmed concern and a published de-identified derivative") { () =>
    issue = Sharing.published(world, "Alice", text)
  }

  When("a reader uses public search and reads the issue") { () =>
    world.public("/search?q=Kingsway"): Unit
    world.public("/issues?q=Kingsway"): Unit
    world.ok(world.public(s"/issues/$issue")): Unit
    world.public(s"/issues/$issue/chronology"): Unit
    world.public("/issues/iss-not-a-real-issue"): Unit
  }

  Then("no original, no private field and no participant identifier is returned") { () =>
    publicRoutesHoldNothingPrivate()
    val page = world.public(s"/issues/$issue").body
    for forbidden <- Seq(
        alice.id,
        Sharing.concernOf(world, "Alice"),
        Sharing.contributionOf(world, "Alice"),
        world.notes("Alice.conversation")
      )
    do assert(!page.contains(forbidden), s"the issue page holds $forbidden")
  }

  Then("no snippet, count or error message contains them") { () =>
    val missing = world.public("/issues/iss-not-a-real-issue")
    assertEquals((missing.status, missing.rule), (404, "public.not-found"))
    val listed = world.public("/issues?q=Kingsway").body
    for secret <- world.privateTexts.toSeq :+ alice.id do
      assert(!listed.contains(secret) && !missing.body.contains(secret))
  }

  // ── enhanced review ────────────────────────────────────────────────────────

  Given(
    "a derivative approved by the participant that describes a distinctive circumstance, a health detail, a minor or a very small group"
  ) { () =>
    world.privateTexts += Sharing.DistinctiveDerivative
    Sharing.approve(world, "Alice", Sharing.DistinctiveDerivative): Unit
  }

  Then("the derivative is flagged for enhanced review") { () =>
    val item = Sharing.awaitingReview(world, "Alice")
    assert(
      item("flags").arr
        .map(_.str)
        .toSet
        .intersect(Set("Distinctive", "Health", "Minor", "SmallGroup"))
        .nonEmpty,
      item.toString
    )
  }

  Then("the reviewer cannot approve the derivative for the public in one step") { () =>
    val id   = Sharing.contributionOf(world, "Alice")
    val body = ujson.Obj("new" -> ujson.Obj("title" -> "Access to Kingsway Academy"))
    assertEquals(
      world.reviewer("POST", s"/reviews/$id/approve", Some(body)).rule,
      "contribution.second-step.required"
    )
    body("enhanced") = "checked, nothing identifying"
    assertEquals(
      world.reviewer("POST", s"/reviews/$id/approve", Some(body)).rule,
      "contribution.second-step.same-click"
    )
    assertEquals(Sharing.awaitingReview(world, "Alice")("status").str, "AwaitingReviewer")
  }
