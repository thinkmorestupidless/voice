package voice.features

import com.thinkmorestupidless.ankka.core.EntityId
import voice.application.interests.*
import voice.application.issues.IssueEntity
import voice.domain.interests.Notification
import voice.domain.issues.Development

/** Following, the inbox and the delivery workflow, as the feedback scenarios use them. */
object Followers:

  /** A fresh public issue, opened by a reviewer, so no other scenario's followers are on it. */
  def issue(world: World, title: String): String =
    world
      .ok(
        world.reviewer(
          "POST",
          "/issues",
          Some(
            ujson.Obj("title" -> s"$title (illustrative)", "summary" -> "Getting pupils to school.")
          )
        ),
        201
      )
      .json("id")
      .str

  def follow(world: World, name: String, issue: String): ujson.Value =
    world
      .ok(
        world.me(
          world.person(name),
          "PUT",
          s"/interests/$issue",
          Some(ujson.Obj("delivery" -> "inbox-immediate"))
        )
      )
      .json

  def stop(world: World, name: String, issue: String): Unit =
    world.ok(world.me(world.person(name), "DELETE", s"/interests/$issue"), 204): Unit

  def inbox(world: World, name: String): Vector[ujson.Value] =
    world.ok(world.me(world.person(name), "GET", "/inbox")).json.arr.toVector

  /** The action recorded by the representative; its JSON. */
  def act(world: World, issue: String, state: String = "proposed"): ujson.Value =
    world
      .ok(
        world.representative("POST", s"/issues/$issue/actions", Some(Accountable.body(state))),
        201
      )
      .json

  private def client = Harness.kit.componentClient

  /** The development an authoritative record minted, read from the Issue. */
  def developmentOf(issue: String, ref: String): Development =
    val s = client.forEventSourcedEntity(EntityId(issue)).call(IssueEntity.get).invoke()
    s.developments
      .find(_.ref == ref)
      .getOrElse(throw AssertionError(s"no development for $ref on $issue"))

  def status(development: String): DeliveryStatus =
    client.forWorkflow(EntityId(development)).call(UpdateDeliveryWorkflow.status).invoke().status

  /** The development's workflow has ended, however it ended. */
  def processed(development: String): DeliveryStatus =
    eventuallyStatus(development)(s =>
      s != DeliveryStatus.Running && s != DeliveryStatus.NotStarted
    )

  private def eventuallyStatus(development: String)(
      done: DeliveryStatus => Boolean
  ): DeliveryStatus =
    val deadline = System.currentTimeMillis() + 30_000
    var s        = status(development)
    while !done(s) && System.currentTimeMillis() < deadline do
      Thread.sleep(150)
      s = status(development)
    assert(done(s), s"$development is still $s")
    s

  /** What the consumer does with a redelivered event: start the workflow under the development. */
  def redeliver(issue: String, d: Development): DeliveryStatus =
    client
      .forWorkflow(EntityId(d.developmentId))
      .call(UpdateDeliveryWorkflow.start)
      .invoke(StartDelivery(issue, d))

  def notification(participant: String, development: String) =
    client
      .forEventSourcedEntity(EntityId(Notification.id(participant, development)))
      .call(NotificationEntity.get)
      .invoke()
