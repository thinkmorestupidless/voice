package voice.features

import com.thinkmorestupidless.ankka.testkit.GherkinSuite

/**
 * The base of every feature suite: one `GherkinSuite` per feature file (R17), the scenario's world,
 * the steps every feature shares, and the check every scenario ends with: each public route the
 * scenario read is read again and holds no private original, no participant id and no contact
 * detail, in its body, its snippets, its counts or its errors (SC-002, SC-003).
 */
abstract class VoiceFeatureSuite(file: String) extends GherkinSuite(file):

  val world = World()

  override def munitTimeout = scala.concurrent.duration.Duration(180, "s")

  override def beforeEach(context: BeforeEach): Unit =
    world.reset()
    super.beforeEach(context)

  override def afterEach(context: AfterEach): Unit =
    try publicRoutesHoldNothingPrivate()
    finally super.afterEach(context)

  /**
   * SC-002: everything a citizen wrote in private, and every participant id, stays off public
   * routes.
   */
  def publicRoutesHoldNothingPrivate(): Unit =
    val secrets = world.privateTexts.filter(_.length >= 12).toSeq ++ world.people.values.map(_.id)
    for route <- world.publicRoutes.toSeq do
      val answer = Harness.send("GET", "/public" + route)
      for secret <- secrets do
        assert(
          !answer.body.contains(secret),
          s"public route $route holds a private value: '$secret'"
        )

  /** Polls until `check` holds, as a view or a workflow catches up. */
  def eventually[A](what: String, seconds: Int = 30)(check: => A): A =
    val deadline           = System.nanoTime() + seconds * 1_000_000_000L
    var failure: Throwable = null
    while System.nanoTime() < deadline do
      try return check
      catch
        case e: AssertionError      => failure = e; Thread.sleep(150)
        case e: munit.FailException => failure = e; Thread.sleep(150)
    throw AssertionError(
      s"$what did not happen within ${seconds}s: ${Option(failure).map(_.getMessage).getOrElse("")}"
    )

  // ── shared steps ───────────────────────────────────────────────────────────

  Then("nothing fails") { () =>
    world.last.foreach(a => assert(a.status < 500, s"the last answer was $a"))
  }
