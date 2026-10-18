package voice.features

import voice.application.interests.{DeliveryFaults, DeliveryStatus, PrepareNotification}
import voice.domain.*

/** features/feedback/one-truthful-update.feature (US6). */
class FeedbackSuite extends VoiceFeatureSuite("features/feedback/one-truthful-update.feature"):

  private var issue: String       = ""
  private var reader: String      = ""
  private var action: ujson.Value = ujson.Null
  private var development: String = ""
  private var opened: ujson.Value = ujson.Null
  private var before: Int         = 0

  override def beforeEach(context: BeforeEach): Unit =
    issue = ""; reader = ""; action = ujson.Null; development = ""; opened = ujson.Null; before = 0
    DeliveryFaults.clear()
    super.beforeEach(context)

  override def afterEach(context: AfterEach): Unit =
    DeliveryFaults.clear()
    super.afterEach(context)

  private def settle(): Unit = Thread.sleep(1500)

  /** A follower `name` with an update about one recorded action in their inbox. */
  private def delivered(name: String): ujson.Value =
    world.join(name): Unit
    Followers.follow(world, name, issue): Unit
    action = Followers.act(world, issue)
    development = Followers.developmentOf(issue, action("id").str).developmentId
    eventually(s"$name has the update") {
      val box = Followers.inbox(world, name)
      assertEquals(box.size, 1, box.toString)
      box.head
    }

  Given("a public issue {string}") { (title: String) =>
    issue = Followers.issue(world, title)
  }

  // ── following ─────────────────────────────────────────────────────────────

  Given("a participant reading the issue") { () =>
    world.join("Reader"): Unit
    reader = "Reader"
    world.ok(world.public(s"/issues/$issue")): Unit
    world.ok(world.public(s"/search?q=pupils")): Unit
    assertEquals(
      world.ok(world.me(world.person(reader), "GET", "/interests")).json.arr.size,
      0,
      "reading created an interest"
    )
  }

  When("the participant follows the issue") { () =>
    assertEquals(
      world
        .me(
          world.person(reader),
          "PUT",
          s"/interests/$issue",
          Some(ujson.Obj("delivery" -> "email"))
        )
        .rule,
      "interest.delivery.invalid"
    )
    opened = Followers.follow(world, reader, issue)
  }

  Then("an interest is created for the participant") { () =>
    assertEquals(opened("issue").str, issue)
    assert(opened("active").bool)
    eventually("the interest is listed") {
      val listed = world.ok(world.me(world.person(reader), "GET", "/interests")).json.arr
      assertEquals(listed.map(_("issue").str).toSeq, Seq(issue))
    }
  }

  Then("the delivery is the inbox") { () =>
    assertEquals(opened("delivery").str, "inbox-immediate")
  }

  Then("the participant can stop following") { () =>
    Followers.stop(world, reader, issue)
    eventually("the interest is no longer listed") {
      assertEquals(world.ok(world.me(world.person(reader), "GET", "/interests")).json.arr.size, 0)
    }
  }

  // ── only an eligible follower ─────────────────────────────────────────────

  Given("a participant {string} who follows the issue with inbox delivery") { (name: String) =>
    world.join(name): Unit
    Followers.follow(world, name, issue): Unit
  }

  Given("a participant {string} who does not follow the issue") { (name: String) =>
    world.join(name): Unit
  }

  When("a representative records an action {string} on the issue") { (_: String) =>
    action = Followers.act(world, issue)
    development = Followers.developmentOf(issue, action("id").str).developmentId
  }

  When("the development is processed") { () =>
    assertEquals(Followers.processed(development), DeliveryStatus.Done)
  }

  Then("exactly one update about the action is in the inbox of {string}") { (name: String) =>
    val box = eventually("the update reaches the inbox") {
      val b = Followers.inbox(world, name)
      assertEquals(b.size, 1, b.toString)
      b
    }
    assertEquals(box.head("development").str, development)
  }

  Then("no update is in the inbox of {string}") { (name: String) =>
    assertEquals(Followers.inbox(world, name).size, 0)
  }

  Then("the update names the action and its record") { () =>
    val u = Followers.inbox(world, "Alice").head
    assert(u("text").str.contains(Accountable.body()("publicSummary").str), u.toString)
    assert(u("links").arr.exists(_.str.endsWith(s"#${action("id").str}")), u.toString)
  }

  Then("the update does not claim the problem is solved") { () =>
    val text = Followers.inbox(world, "Alice").head("text").str.toLowerCase
    for word <- Seq("solved", "resolved", "honoured", "fulfilled") do
      assert(!text.contains(word), word)
  }

  // ── processed again ───────────────────────────────────────────────────────

  Given("a development already delivered to a follower") { () =>
    world.join("Alice"): Unit
    val alice = world.person("Alice").id
    Followers.follow(world, "Alice", issue): Unit
    // The delivery step is held once the update is prepared, and the service restarted under it:
    // the restarted workflow delivers what was prepared, once (SC-007).
    DeliveryFaults.hold(alice)
    action = Followers.act(world, issue)
    development = Followers.developmentOf(issue, action("id").str).developmentId
    eventually("the update is prepared") {
      assert(Followers.notification(alice, development).exists)
    }
    Harness.restart()
    DeliveryFaults.release(alice)
    eventually("the update is delivered after the restart") {
      assertEquals(Followers.inbox(world, "Alice").size, 1)
    }
  }

  When("the development is processed again") { () =>
    val d = Followers.developmentOf(issue, action("id").str)
    Followers.redeliver(issue, d): Unit
    Followers.redeliver(issue, d): Unit
    // A second preparation addresses the same notification, which answers the first.
    val alice = world.person("Alice").id
    val first = Followers.notification(alice, development)
    val again = Harness.kit.componentClient
      .forEventSourcedEntity(
        com.thinkmorestupidless.ankka.core
          .EntityId(voice.domain.interests.Notification.id(alice, development))
      )
      .call(voice.application.interests.NotificationEntity.prepare)
      .invoke(
        PrepareNotification(
          alice,
          development,
          issue,
          first.text,
          first.why,
          Vector("/elsewhere"),
          Provenance(Actor.System("test"), Harness.clock.now(), "t", "t")
        )
      )
    assertEquals(again.links, first.links)
    settle()
  }

  Then("no second update about the development reaches any follower") { () =>
    val box = Followers.inbox(world, "Alice")
    assertEquals(box.count(_("development").str == development), 1, box.toString)
    assertEquals(Followers.status(development), DeliveryStatus.Done)
  }

  // ── an immaterial development ─────────────────────────────────────────────

  Given("a participant who follows the issue") { () =>
    world.join("Follower"): Unit
    Followers.follow(world, "Follower", issue): Unit
    val published = world
      .ok(
        world.representative(
          "POST",
          s"/issues/$issue/updates",
          Some(ujson.Obj("text" -> "We met the bus operator today."))
        ),
        201
      )
      .json
    development = published("id").str
    Followers.processed(development): Unit
    eventually("the update is delivered")(assertEquals(Followers.inbox(world, "Follower").size, 1))
    before = 1
  }

  When("a typographical correction is recorded on the issue") { () =>
    assertEquals(
      world.representative("POST", s"/issues/$issue/updates", Some(ujson.Obj("text" -> " "))).rule,
      "update.text.required"
    )
    assertEquals(
      world
        .representative(
          "POST",
          s"/issues/$issue/updates",
          Some(ujson.Obj("text" -> "x", "corrects" -> "dev-00000000000000000000000000"))
        )
        .rule,
      "update.correction.not-held"
    )
    val corrected = world
      .ok(
        world.representative(
          "POST",
          s"/issues/$issue/updates",
          Some(
            ujson.Obj(
              "text"     -> "We met the bus operator today (corrected spelling).",
              "corrects" -> development
            )
          )
        ),
        201
      )
      .json
    assertEquals(corrected("materiality").str, "minor")
    development = corrected("id").str
  }

  Then("no update is sent") { () =>
    assertEquals(Followers.processed(development), DeliveryStatus.Immaterial)
    settle()
    assertEquals(Followers.inbox(world, "Follower").size, before)
  }

  // ── an update explains itself ─────────────────────────────────────────────

  Given("an update in a follower's inbox") { () =>
    delivered("Dana"): Unit
  }

  When("the follower opens the update") { () =>
    opened = world
      .ok(world.me(world.person("Dana"), "POST", s"/inbox/$development/read", Some(ujson.Obj())))
      .json
    assertEquals(opened("state").str, "read")
    val other = world.join("Eve")
    assertEquals(
      world.me(other, "POST", s"/inbox/$development/read", Some(ujson.Obj())).rule,
      "notification.not-owned"
    )
  }

  Then("the update says why it was sent to the follower") { () =>
    assert(opened("why").str.startsWith("You follow “"), opened.toString)
    assert(opened("why").str.contains("an action recorded on it"), opened.toString)
  }

  Then("the update links to the records it is about") { () =>
    for link <- opened("links").arr do
      val (path, anchor) = link.str.stripPrefix("/public").span(_ != '#')
      val page           = world.ok(world.public(path)).json
      assert(ujson.write(page).contains(anchor.stripPrefix("#")), s"$link is not on $path")
  }

  Then(
    "the update's wording of human review and action state matches the issue's state when the update was prepared"
  ) { () =>
    val shown = world
      .ok(world.public(s"/issues/$issue"))
      .json("actions")
      .arr
      .find(_("id").str == action("id").str)
      .get
    assert(opened("text").str.contains(s"“${shown("state").str}”"), opened.toString)
    assert(opened("text").str.startsWith("A representative recorded"), opened.toString)
    // A later change of state is a later fact: the update keeps the words it was prepared with.
    world.ok(
      world.representative(
        "POST",
        s"/actions/${action("id").str}/state",
        Some(ujson.Obj("state" -> "completed"))
      )
    ): Unit
    val again = Followers.inbox(world, "Dana").head
    assertEquals(again("text").str, opened("text").str)
  }

  // ── a former follower ─────────────────────────────────────────────────────

  Given("a participant who followed the issue and stopped following") { () =>
    delivered("Frank"): Unit
    Followers.stop(world, "Frank", issue)
  }

  When("a representative records an action on the issue") { () =>
    action = Followers.act(world, issue)
    development = Followers.developmentOf(issue, action("id").str).developmentId
    Followers.processed(development): Unit
  }

  Then("no update reaches the participant") { () =>
    settle()
    assert(!Followers.inbox(world, "Frank").exists(_("development").str == development))
    assert(!Followers.notification(world.person("Frank").id, development).exists)
  }

  Then("the participant's earlier updates remain in the inbox") { () =>
    assertEquals(Followers.inbox(world, "Frank").size, 1)
  }

  // ── a failed delivery ─────────────────────────────────────────────────────

  Given("an update prepared for a follower") { () =>
    world.join("Gail"): Unit
    Followers.follow(world, "Gail", issue): Unit
  }

  When("the delivery fails once and is retried") { () =>
    DeliveryFaults.failNext(world.person("Gail").id, 1)
    action = Followers.act(world, issue)
    development = Followers.developmentOf(issue, action("id").str).developmentId
    assertEquals(Followers.processed(development), DeliveryStatus.Done)
  }

  Then("the failure is recorded") { () =>
    val n = Followers.notification(world.person("Gail").id, development)
    assertEquals(n.attempts, 1)
    assertEquals(n.failure, Some("inbox unavailable"))
  }

  Then("the follower receives the update once and whole") { () =>
    val box = eventually("the update reaches the inbox") {
      val b = Followers.inbox(world, "Gail")
      assertEquals(b.size, 1, b.toString)
      b
    }
    assertEquals(box.head("state").str, "delivered")
    assertEquals(box.head("failedAttempts").num.toInt, 1)
    assert(
      box
        .head("text")
        .str
        .contains(Accountable.body()("publicSummary").str) && box.head("text").str.endsWith("."),
      box.toString
    )
  }
