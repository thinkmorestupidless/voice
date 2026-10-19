package voice.features

/** What the action scenarios share: a full action body and the seven labels. */
object Accountable:

  val Labels: Set[String] =
    Set(
      "submitted",
      "AI-interpreted",
      "under moderation",
      "staff-acknowledged",
      "under investigation",
      "action recorded",
      "outcome verified"
    )

  def body(state: String = "proposed", date: String = "2026-11-02"): ujson.Obj = ujson.Obj(
    "authority"     -> "Cabinet member for transport (illustrative)",
    "owner"         -> "Transport team",
    "date"          -> date,
    "rationale"     -> "Pupils on the withdrawn route need a safe way to school.",
    "state"         -> state,
    "publicSummary" -> "Officers were asked to cost a lit footpath beside the ring road."
  )

  /** A contribution published into `issue` by a new participant `name`; its text. */
  def publish(world: World, name: String, issue: String, mode: String = "de-identified"): String =
    Flows.confirmedConcern(world, name): Unit
    val text = Sharing.uniqueDerivative()
    Sharing.approve(world, name, text, mode): Unit
    Sharing.submit(world, name): Unit
    Sharing.awaitingReview(world, name): Unit
    val id = Sharing.contributionOf(world, name)
    world.ok(
      world.reviewer("POST", s"/reviews/$id/approve", Some(ujson.Obj("issue" -> issue)))
    ): Unit
    text
