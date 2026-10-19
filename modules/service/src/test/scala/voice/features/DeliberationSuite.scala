package voice.features

import java.nio.file.{Files, Paths}

/** features/deliberation/opinions-not-evidence.feature (US4). */
class DeliberationSuite
    extends VoiceFeatureSuite("features/deliberation/opinions-not-evidence.feature"):

  private var issue: String          = ""
  private var options: Seq[String]   = Seq.empty
  private var shown: ujson.Value     = ujson.Null
  private var mine: ujson.Value      = ujson.Null
  private var staffView: ujson.Value = ujson.Null

  private val TradeOffText = "A longer walk is fine for us if the path beside the ring road is lit."

  override def beforeEach(context: BeforeEach): Unit =
    issue = ""; options = Seq.empty; shown = ujson.Null; mine = ujson.Null; staffView = ujson.Null
    super.beforeEach(context)

  private def page      = world.ok(world.public(s"/issues/$issue")).json
  private def aggregate = page("aggregate")

  /** The aggregate once the view has caught up with `participants` expressions. */
  private def settled(participants: Int, levels: (String, Int)*): ujson.Value =
    eventually(s"the aggregate counts $participants participants") {
      val a = aggregate
      assertEquals(
        a("participants").obj.get("count").map(_.num.toInt),
        Some(participants),
        a.toString
      )
      for (k, n) <- levels do
        assertEquals(
          a("priorities").arr.find(_("key").str == k).flatMap(_.obj.get("count")).map(_.num.toInt),
          Some(n),
          a.toString
        )
      a
    }

  Given("a public issue with options") { () =>
    val loaded = Evidence.load()
    issue = loaded.issue
    options = Opinion.Options.map(o => loaded.options(o("title").str))
  }

  // ── opinion, never evidence ───────────────────────────────────────────────

  Given("a participant reading the issue") { () =>
    world.join("Alice")
    world.ok(world.public(s"/issues/$issue")): Unit
  }

  When("the participant expresses a priority and a trade-off") { () =>
    world.privateTexts += TradeOffText
    val alice = world.person("Alice")
    world.ok(
      world.me(alice, "POST", s"/issues/$issue/expression/priority", Some(ujson.Obj("level" -> 4)))
    ): Unit
    mine = world
      .ok(
        world.me(
          alice,
          "POST",
          s"/issues/$issue/expression/trade-offs",
          Some(ujson.Obj("option" -> options(1), "stance" -> "accepts", "text" -> TradeOffText))
        )
      )
      .json
  }

  Then("each is held as that participant's statement, labelled as opinion") { () =>
    val held = world.ok(world.me(world.person("Alice"), "GET", s"/issues/$issue/expression")).json
    assertEquals(held("label").str, "opinion")
    assertEquals(held("priority").num.toInt, 4)
    assertEquals(
      held("tradeOffs").arr.map(t => (t("option").str, t("stance").str, t("text").str)).toSeq,
      Seq((options(1), "accepts", TradeOffText))
    )
    assert(held("notice").str.contains("never as evidence"), held.toString)
  }

  Then("neither appears among the issue's claims or evidence") { () =>
    val p = page
    for part <- Seq("claims", "evidence", "questions") do
      assert(!ujson.write(p(part)).contains(TradeOffText), part)
    for c <- p("claims").arr do
      val x = ujson.write(world.ok(world.public(s"/issues/$issue/claims/${c("id").str}")).json)
      assert(!x.contains(TradeOffText) && !x.contains("opinion"), x)
    // SC-005: no evidence route, claim explanation or search reads an expression.
    for file <- Seq(
        "api/Evidence.scala",
        "application/reasoning/Explanations.scala",
        "application/issues/PublicSearchView.scala"
      )
    do
      val source = Files.readString(Paths.get("modules/service/src/main/scala/voice", file))
      for name <- Seq("ExpressionEntity", "IssueAggregates", "Deliberation") do
        assert(!source.contains(name), s"$file reads $name")
  }

  // ── options without endorsement ───────────────────────────────────────────

  When("a participant reads the options") { () =>
    world.join("Alice")
    shown = page("options")
  }

  Then("each option shows its costs, its benefits and its open questions") { () =>
    assertEquals(shown.arr.map(_("id").str).toSet, options.toSet)
    for o <- shown.arr do
      assert(
        o("costs").arr.nonEmpty && o("benefits").arr.nonEmpty && o("openQuestions").arr.nonEmpty,
        o.toString
      )
  }

  Then("no option is endorsed") { () =>
    for o <- shown.arr do
      assertEquals(
        o.obj.keySet.toSet,
        Set("id", "title", "description", "costs", "benefits", "openQuestions")
      )
    val endorsing = Opinion.Options.head.obj.clone()
    endorsing("recommended") = true
    assertEquals(
      world.reviewer("PUT", s"/issues/$issue/options/new", Some(ujson.Obj.from(endorsing))).rule,
      "option.endorsement"
    )
    val ranked = ujson.Obj.from(Opinion.Options.head.obj.toSeq :+ ("rank" -> ujson.Num(1)))
    assertEquals(
      world.reviewer("PUT", s"/issues/$issue/options/${options.head}", Some(ranked)).rule,
      "option.endorsement"
    )
  }

  Then("the participant can record a consideration against any option") { () =>
    val alice = world.person("Alice")
    for o <- options do
      world.ok(
        world.me(
          alice,
          "POST",
          s"/issues/$issue/expression/considerations",
          Some(ujson.Obj("option" -> o, "text" -> "Think about winter mornings."))
        )
      ): Unit
    val held = world.ok(world.me(alice, "GET", s"/issues/$issue/expression")).json
    assertEquals(held("considerations").arr.map(_("option").str).toSet, options.toSet)
    assertEquals(
      world
        .me(
          alice,
          "POST",
          s"/issues/$issue/expression/considerations",
          Some(ujson.Obj("option" -> "pol-00000000000000000000000000", "text" -> "x"))
        )
        .rule,
      "expression.option.not-held"
    )
  }

  // ── an aggregate's sample ─────────────────────────────────────────────────

  Given("priorities from a self-selected set of participants") { () =>
    Opinion.express(world, issue, "sample", 5, 4): Unit
  }

  When("an aggregate of the priorities is shown") { () =>
    shown = settled(5, "4" -> 5)
  }

  Then(
    "the aggregate states the number of participants, the number of contributions, the collection method, the time window, the deduplication basis and a representativeness caveat"
  ) { () =>
    assertEquals(shown("participants")("count").num.toInt, 5)
    assertEquals(shown("contributions")("count").num.toInt, 5)
    assert(shown("method").str.startsWith("Self-selected"), shown.toString)
    assert(
      shown("window").obj.contains("from") && shown("window").obj.contains("to"),
      shown.toString
    )
    assert(shown("deduplication").str.nonEmpty)
    assert(shown("caveat").str.contains("proportion of all constituents"), shown.toString)
  }

  Then("the aggregate makes no claim about the proportion of all constituents") { () =>
    for k <- Opinion.keys(shown) do
      assert(!Opinion.Ranking.matches(k), s"the aggregate has a field $k")
    val text = ujson.write(shown)
    assert(!text.contains("%"), text)
  }

  // ── small cells ───────────────────────────────────────────────────────────

  Given("fewer participants than the suppression threshold in a cell") { () =>
    Opinion.express(world, issue, "many", 5, 4): Unit
    Opinion.express(world, issue, "few", 1, 2): Unit
  }

  When("the aggregate is shown") { () =>
    shown = eventually("the aggregate counts six participants") {
      val a = aggregate
      assertEquals(a("participants")("count").num.toInt, 6, a.toString)
      a
    }
  }

  Then("the cell is suppressed") { () =>
    val two = shown("priorities").arr.find(_("key").str == "2").get
    assert(two("suppressed").bool && !two.obj.contains("count"), two.toString)
  }

  Then("no participant or small group can be inferred from the aggregate") { () =>
    val cells  = shown("priorities").arr
    val hidden = cells.count(_("suppressed").bool)
    // With one hidden cell it would be the total less the others: a second one is hidden beside it.
    assert(hidden == 0 || hidden >= 2, shown.toString)
    val visible = cells.flatMap(_.obj.get("count")).map(_.num.toInt).sum
    assert(visible < 6, s"every suppressed count is recoverable: $shown")
    assert(!ujson.write(shown).contains("prt-"), shown.toString)
  }

  // ── amendment ─────────────────────────────────────────────────────────────

  Given("a participant who has expressed a priority") { () =>
    Opinion.express(world, issue, "others", 5, 5): Unit
    Opinion.express(world, issue, "amender", 1, 3): Unit
    settled(6): Unit
  }

  When("the participant amends the priority") { () =>
    world.ok(
      world.me(
        world.person("amender-1"),
        "POST",
        s"/issues/$issue/expression/priority",
        Some(ujson.Obj("level" -> 5))
      )
    ): Unit
  }

  Then("the aggregate reflects the amended priority") { () =>
    val a = settled(6, "5" -> 6, "3" -> 0)
    assertEquals(a("contributions")("count").num.toInt, 6)
    // And a withdrawal takes it out of the count.
    world.ok(
      world.me(
        world.person("amender-1"),
        "POST",
        s"/issues/$issue/expression/withdraw",
        Some(ujson.Obj())
      )
    ): Unit
    settled(5, "5" -> 5): Unit
  }

  // ── no ranking, no profile ────────────────────────────────────────────────

  When("any public or representative view of the issue is shown") { () =>
    Opinion.express(world, issue, "view", 5, 3): Unit
    settled(5): Unit
    shown = page
    staffView = world.ok(world.representative("GET", s"/issues/$issue")).json
  }

  Then("no ranking of individuals appears") { () =>
    for view <- Seq(shown, staffView) do
      for k <- Opinion.keys(view) do assert(!Opinion.Ranking.matches(k), s"a field $k")
      val text = ujson.write(view)
      for p <- world.people.values do assert(!text.contains(p.id), s"a participant id appears")
  }

  Then("no ideological or demographic profile of any participant appears") { () =>
    for view <- Seq(shown, staffView); k <- Opinion.keys(view) do
      assert(!Opinion.Profile.matches(k), s"a field $k")
  }

  // ── the dashboard ─────────────────────────────────────────────────────────

  Given("an issue with contributions from a self-selected sample") { () =>
    Flows.confirmedConcern(world, "Alice"): Unit
    val text = Sharing.uniqueDerivative()
    Sharing.approve(world, "Alice", text): Unit
    Sharing.submit(world, "Alice"): Unit
    Sharing.awaitingReview(world, "Alice"): Unit
    val id = Sharing.contributionOf(world, "Alice")
    world.ok(
      world.reviewer("POST", s"/reviews/$id/approve", Some(ujson.Obj("issue" -> issue)))
    ): Unit
    Opinion.express(world, issue, "dash", 5, 4): Unit
    settled(5): Unit
  }

  When("the representative opens the concerns dashboard") { () =>
    staffView = eventually("the contribution is on the issue") {
      val v = world.ok(world.representative("GET", s"/issues/$issue")).json
      assert(v("page")("contributions").arr.nonEmpty, v.toString)
      v
    }
  }

  Then("every measure is labelled by participants and time window") { () =>
    val measures = staffView("measures").arr
    assert(measures.nonEmpty)
    for m <- measures do
      assert(m("label").str.nonEmpty, m.toString)
      assert(
        m("basis").str.contains("participants") || m("basis").str.contains("contributions"),
        m.toString
      )
      assert(
        m.obj.contains("window") && m("method").str.nonEmpty && m("caveat").str.nonEmpty,
        m.toString
      )
  }

  Then("no claim about the proportion of all constituents is made") { () =>
    for k <- Opinion.keys(staffView("measures")) do
      assert(!Opinion.Ranking.matches(k), s"a field $k")
    assert(!ujson.write(staffView("measures")).contains("%"))
    for m <- staffView("measures").arr do assert(m("caveat").str.nonEmpty)
  }
