package voice.features

/** What the deliberation scenarios share: options, participants who express, and a JSON walk. */
object Opinion:

  /** The seed pack's options, in its order: the Background's Issue has these. */
  val Options: Seq[ujson.Value] =
    ujson.read(java.nio.file.Files.readString(Evidence.Pack.resolve("options.json"))).arr.toSeq

  /** `n` new participants, each expressing `level` on `issue`. */
  def express(world: World, issue: String, prefix: String, n: Int, level: Int): Seq[Person] =
    (1 to n).map { i =>
      val person = world.join(s"$prefix-$i")
      world.ok(
        world.me(
          person,
          "POST",
          s"/issues/$issue/expression/priority",
          Some(ujson.Obj("level" -> level))
        )
      )
      person
    }

  /** Every key of every object in `json`, at any depth. */
  def keys(json: ujson.Value): Seq[String] = json match
    case ujson.Obj(m) => m.keys.toSeq ++ m.values.flatMap(keys)
    case ujson.Arr(a) => a.toSeq.flatMap(keys)
    case _            => Seq.empty

  val Profile =
    "(?i).*(ideolog|partisan|party|politic|religio|ethnic|race|gender|sexual|demograph|ageband|voting|income).*".r
  val Ranking = "(?i).*(rank|score|leader|top|percent|proportion|share).*".r
