package voice.features

import com.thinkmorestupidless.ankka.core.EntityId
import voice.application.erasure.ErasureWorkflow
import voice.application.participation.ConversationEntity
import voice.domain.*
import voice.domain.erasure.*
import voice.sealing.Sealing

/**
 * A participant's whole footprint, an erasure and what it leaves, as the erasure scenarios use
 * them.
 */
object Erasing:

  private val counter = java.util.concurrent.atomic.AtomicInteger(1)

  /**
   * Words only this participant wrote, in private: the dump and every rebuilt view must lose them.
   */
  def privateMarker(name: String): String =
    s"Only $name knows the lollipop warden on Rowan Crescent stopped coming after half term, note ${java.util.UUID.randomUUID()}."

  def client = Harness.kit.componentClient

  /**
   * `name` with a conversation holding a private message of their own, a confirmed concern, a
   * published de-identified derivative on a new issue, a priority on it, and an interest in it.
   */
  def footprint(world: World, name: String): (Person, String) =
    val (person, conv, proposal) = Flows.proposal(world, name)
    val marker                   = privateMarker(name)
    Flows.say(world, person, conv, marker): Unit
    world.notes(s"$name.marker") = marker
    val concern = world.ok(
      world.me(
        person,
        "POST",
        s"/conversations/$conv/proposals/${proposal("id").str}/confirm",
        Some(ujson.Obj())
      )
    )
    world.notes(s"$name.concern") = concern.json("id").str
    world.notes(s"$name.conversation") = conv
    val derivative = Sharing.uniqueDerivative()
    world.notes(s"$name.derivative") = derivative
    val issue = Sharing.published(
      world,
      name,
      derivative,
      s"Erasure walk ${counter.getAndIncrement()} (illustrative)",
      "A walk through erasure, titled so no other scenario's text matches it."
    )
    world.notes(s"$name.pub") = publicCopy(issue, derivative)
    world.ok(
      world.me(person, "POST", s"/issues/$issue/expression/priority", Some(ujson.Obj("level" -> 3)))
    ): Unit
    (person, issue)

  /** `name` contributes to an issue that already exists, and follows it. */
  def contributes(world: World, name: String, issue: String): Person =
    Flows.confirmedConcern(world, name): Unit
    val derivative = Sharing.uniqueDerivative()
    world.notes(s"$name.derivative") = derivative
    Sharing.approve(world, name, derivative): Unit
    Sharing.submit(world, name): Unit
    Sharing.awaitingReview(world, name): Unit
    val item = world
      .ok(
        world.reviewer(
          "POST",
          s"/reviews/${Sharing.contributionOf(world, name)}/approve",
          Some(ujson.Obj("issue" -> issue))
        )
      )
      .json
    assert(item("status").str == "Published", item.toString)
    Followers.follow(world, name, issue): Unit
    world.person(name)

  /** The `pub-…` of the copy holding `text` on the public issue. */
  def publicCopy(issue: String, text: String): String =
    val page = Harness.send("GET", s"/public/issues/$issue").json
    page("contributions").arr
      .find(_("text").str == text)
      .map(_("pub").str)
      .getOrElse(throw AssertionError(s"$text is not on $issue"))

  /** The participant's private messages as sealed, read from the record before it is erased. */
  def sealedMessages(conversation: String): Vector[Sealed[String]] =
    client
      .forEventSourcedEntity(EntityId(conversation))
      .call(ConversationEntity.get)
      .invoke()
      .messages
      .map(_.text)

  def record(participant: String): ErasureRecord =
    client.forWorkflow(EntityId(participant)).call(ErasureWorkflow.status).invoke()

  def completed(participant: String): ErasureRecord =
    val deadline = System.currentTimeMillis() + 60_000
    var r        = record(participant)
    while r.stage != ErasureStage.Completed && System.currentTimeMillis() < deadline do
      Thread.sleep(200)
      r = record(participant)
    assert(
      r.stage == ErasureStage.Completed,
      s"the erasure of $participant is still ${r.stage}: ${r.steps.map(_.step)}"
    )
    r

  def sealing: Sealing = Sealing(Harness.kit.secrets)

  given com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[String] =
    com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker.make[String]

  /** Opens `value` as an edge would: the marker for an erased field, never an exception. */
  def opened(value: Sealed[String]): String =
    sealing.openText(value, voice.application.participation.Purposes.Message)

  /**
   * Empties every view and lets it read its sources again, while the service is down: what raising
   * every view's version does, done to the tables, since a kit keeps the descriptors it started
   * with. Returns what it emptied, so a test can see it touched something.
   */
  def rebuildViews(): Seq[String] =
    val views = voice.VoiceService.own.collect {
      case v: com.thinkmorestupidless.ankka.sdk.ViewDescriptor[?, ?, ?] =>
        v.componentId.toString.replace('-', '_')
    }
    val touched = scala.collection.mutable.ArrayBuffer.empty[String]
    Harness.kit.restartService(whileStopped =
      try
        scala.util.Using.resource(
          java.sql.DriverManager.getConnection(Harness.kit.jdbcUrl, "ankka", "ankka")
        ) { c =>
          val tables = scala.collection.mutable.ArrayBuffer.empty[(String, String)]
          val rs = c
            .createStatement()
            .executeQuery(
              "SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog', 'information_schema') AND table_type = 'BASE TABLE'"
            )
          while rs.next() do tables += rs.getString(1) -> rs.getString(2)
          val offsets = scala.collection.mutable.ArrayBuffer.empty[String]
          val cols = c
            .createStatement()
            .executeQuery(
              "SELECT table_schema, table_name FROM information_schema.columns WHERE column_name = 'projection_name'"
            )
          while cols.next() do offsets += s""""${cols.getString(1)}"."${cols.getString(2)}""""
          for t <- offsets do
            c.createStatement()
              .executeUpdate(s"DELETE FROM $t WHERE projection_name LIKE 'ankka-%view%'"): Unit
            touched += t
          for (schema, table) <- tables if views.exists(v => table.toLowerCase.contains(v)) do
            c.createStatement().executeUpdate(s"""TRUNCATE "$schema"."$table""""): Unit
            touched += table
        }
      catch case e: Throwable => touched += s"failed: $e"
    )
    touched.toSeq
