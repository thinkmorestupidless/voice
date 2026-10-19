package voice.features

/** The citizen's and the reviewer's steps through a disclosure, reused by later stories. */
object Sharing:

  /** The bus concern with the identifying detail taken out: what a participant would share. */
  val CleanDerivative =
    "The school bus to Kingsway Academy was withdrawn in September and the walk along the ring road now takes forty minutes."

  private val counter = java.util.concurrent.atomic.AtomicInteger(1)

  /**
   * The clean derivative made unique, so scenarios sharing one service never see each other's text.
   */
  def uniqueDerivative(): String =
    s"$CleanDerivative It has been like this for ${counter.getAndIncrement() + 2} weeks."

  val DistinctiveDerivative =
    "We are the only family on Holloway Lane whose son uses a wheelchair, and the withdrawn bus was the only way he could reach Kingsway Academy."

  /** The feature's words for each mode, and the wire value the service reads. */
  val Modes: Map[String, String] = Map(
    "private"             -> "private",
    "de-identified"       -> "de-identified",
    "representative only" -> "representative-only",
    "publicly attributed" -> "public-attribution"
  )

  def concernOf(world: World, name: String): String =
    world.notes.getOrElse(s"$name.concern", throw AssertionError(s"$name has no confirmed concern"))

  /** The participant approves `text` for `mode`; the contribution's view. */
  def approve(
      world: World,
      name: String,
      text: String,
      mode: String = "de-identified"
  ): ujson.Value =
    val person = world.person(name)
    val body   = ujson.Obj("mode" -> Modes.getOrElse(mode, mode), "text" -> text)
    if mode == "publicly attributed" then body("name") = "Sam from Northfield"
    val view = world
      .ok(
        world.me(person, "POST", s"/concerns/${concernOf(world, name)}/contributions", Some(body)),
        201
      )
      .json
    world.notes(s"$name.contribution") = view("id").str
    view

  def contributionOf(world: World, name: String): String =
    world.notes.getOrElse(s"$name.contribution", throw AssertionError(s"$name has no contribution"))

  def submit(world: World, name: String): ujson.Value =
    world
      .ok(
        world.me(
          world.person(name),
          "POST",
          s"/contributions/${contributionOf(world, name)}/submit",
          Some(ujson.Obj())
        )
      )
      .json

  /** The reviewer's item once the privacy check has run. */
  def awaitingReview(world: World, name: String): ujson.Value =
    val id       = contributionOf(world, name)
    val deadline = System.currentTimeMillis() + 30_000
    var item     = ujson.Value(ujson.Null)
    while
      val queue = world.ok(world.reviewer("GET", "/reviews")).json.arr
      item = queue.find(_("contribution").str == id).getOrElse(ujson.Null)
      (item == ujson.Null || item("status").str == "Checking") && System
        .currentTimeMillis() < deadline
    do Thread.sleep(150)
    assert(
      item != ujson.Null && item("status").str == "AwaitingReviewer",
      s"$id did not reach the reviewer: $item"
    )
    item

  /** Approved, submitted and published into a new Issue; the Issue's id. */
  def published(
      world: World,
      name: String,
      text: String,
      title: String = "School transport to Kingsway Academy",
      summary: String = "Getting pupils to Kingsway Academy after the bus was withdrawn."
  ): String =
    approve(world, name, text): Unit
    submit(world, name): Unit
    awaitingReview(world, name): Unit
    val id = contributionOf(world, name)
    val item = world
      .ok(
        world.reviewer(
          "POST",
          s"/reviews/$id/approve",
          Some(ujson.Obj("new" -> ujson.Obj("title" -> title, "summary" -> summary)))
        )
      )
      .json
    assertEquals(item("status").str, "Published", item.toString)
    val issue = item("issue").str
    world.issues(title) = issue
    world.notes(s"$name.issue") = issue
    issue

  private def assertEquals(a: Any, b: Any, clue: String): Unit = assert(a == b, s"$a != $b: $clue")
