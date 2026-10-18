package voice.features

import voice.seed.{Loaded, Seed, StaffApi}
import voice.support.TestIssuer

import java.nio.file.Paths

/** The seed pack as the scenarios load it: through the staff routes, as a reviewer (T061). */
object Evidence:

  val Pack = Paths.get("seed/school-transport")

  private val counter = java.util.concurrent.atomic.AtomicInteger(0)

  /** The harness's Voice as the seed's reviewer. */
  val api: StaffApi = new StaffApi:
    def send(method: String, path: String, body: Option[ujson.Value]): (Int, ujson.Value) =
      val a = Harness.send(method, path, body, Some(TestIssuer.token("seed-reviewer", "reviewer")))
      (a.status, a.json)

  /** One copy of the pack under its own Issue, so scenarios keep out of each other's way. */
  def load(): Loaded =
    Seed.load(api, Pack, title = Some(s"Rural school bus withdrawal ${counter.incrementAndGet()}"))

  /** A pack file's entry by key. */
  def entry(file: String, key: String): ujson.Value =
    ujson.read(java.nio.file.Files.readString(Pack.resolve(file))).arr.find(_("key").str == key).get

  /** A path under `/public`, from a link the service gave. */
  def route(link: String): String = link.stripPrefix("/public")
