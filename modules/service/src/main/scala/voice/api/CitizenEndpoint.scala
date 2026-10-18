package voice.api

import com.thinkmorestupidless.ankka.http.*
import org.apache.pekko.stream.scaladsl.Source
import voice.api.CitizenJson.{*, given}
import voice.api.DisclosureJson.{*, given}
import voice.identity.CitizenAcl

import scala.concurrent.duration.DurationInt
import scala.io.Source as IoSource

/**
 * A pseudonymous participant with a session token (R9). Every route is scoped to that participant's
 * own records: the participant is the principal, never a value in the request.
 */
final class CitizenEndpoint(reach: Reach) extends HttpEndpoint("/me"):
  val acl: Acl = CitizenAcl(reach.edge.tokens, reach.edge.clock)

  private val participation = Participation(reach)
  private val disclosure    = Disclosure(reach, participation)
  private val rights        = DataRights(reach)

  /** The session's participant, admitted: it exists and is not erased. */
  private def me(): String =
    val id = principal.subject
    participation.admit(id)
    id

  withAcl(Acl.AllowAll) {
    // The privacy and AI notice, readable before a session exists (FR-003).
    get("/notice")(() =>
      Bytes("text/markdown; charset=utf-8", CitizenEndpoint.notice.getBytes("UTF-8"))
    )

    postBody("/sessions") { (body: StartRequest) =>
      Problems.handle(Problems.created(participation.start(request, body)))
    }

    postBody("/sessions/recover") { (body: RecoverRequest) =>
      Problems.handle(Problems.ok(participation.recover(request, body)))
    }
  }

  postBody("/sessions/renew") { (_: Empty) =>
    Problems.handle(Problems.ok(participation.renew(request, me())))
  }

  // ── conversations and concerns (US1) ───────────────────────────────────────

  postBody("/conversations") { (_: Empty) =>
    Problems.handle(Problems.created(participation.startConversation(request, me())))
  }

  get("/conversations") { () =>
    Problems.handle(Problems.ok(participation.conversations(me())))
  }

  get("/conversations/{conversation}") { (conversation: String) =>
    Problems.handle(Problems.ok(participation.conversationView(me(), conversation)))
  }

  postBody("/conversations/{conversation}/messages") { (conversation: String, body: MessageRequest) =>
    Problems.handle(Problems.accepted(participation.send(request, me(), conversation, body)))
  }

  get("/conversations/{conversation}/turns/{turn}") { (conversation: String, turn: String) =>
    Problems.handle(Problems.ok(participation.turn(me(), conversation, turn)))
  }

  postBody("/conversations/{conversation}/proposals/{proposal}/edit") {
    (conversation: String, proposal: String, body: EditRequest) =>
      Problems.handle(Problems.ok(participation.edit(request, me(), conversation, proposal, body)))
  }

  postBody("/conversations/{conversation}/proposals/{proposal}/confirm") {
    (conversation: String, proposal: String, _: Empty) =>
      Problems.handle(
        Problems.created(participation.confirm(request, me(), conversation, proposal))
      )
  }

  postBody("/conversations/{conversation}/proposals/{proposal}/discard") {
    (conversation: String, proposal: String, _: Empty) =>
      Problems.handle(Problems.ok(participation.discard(request, me(), conversation, proposal)))
  }

  get("/concerns") { () =>
    Problems.handle(Problems.ok(participation.concerns(me())))
  }

  // ── disclosure (US2) ───────────────────────────────────────────────────────

  get("/concerns/{concern}/sharing") { (concern: String) =>
    Problems.handle(Problems.ok(disclosure.sharing(me(), concern)))
  }

  postBody("/concerns/{concern}/contributions") { (concern: String, body: ContributionRequest) =>
    Problems.handle(Problems.created(disclosure.authorise(request, me(), concern, body)))
  }

  postBody("/contributions/{contribution}/submit") { (contribution: String, _: Empty) =>
    Problems.handle(Problems.ok(disclosure.submit(request, me(), contribution)))
  }

  get("/contributions/{contribution}") { (contribution: String) =>
    Problems.handle(Problems.ok(disclosure.get(me(), contribution)))
  }

  postBody("/contributions/{contribution}/issue") { (contribution: String, body: IssueChoice) =>
    Problems.handle(Problems.ok(disclosure.associate(request, me(), contribution, body)))
  }

  postBody("/contributions/{contribution}/withdraw") { (contribution: String, _: Empty) =>
    Problems.handle(Problems.ok(disclosure.withdraw(request, me(), contribution)))
  }

  /**
   * The conversation as it is recorded: every message after `after`, and every proposal, each an
   * event whose JSON says its `type`. Voice ends the stream before the gateway's fifteen seconds,
   * and the interface reconnects with the last sequence it saw (R5). Only unnamed events can be
   * sent, so the type travels in the data.
   */
  sse("/conversations/{conversation}/stream") { (conversation: String) =>
    val participant = me()
    participation.conversationView(participant, conversation): Unit
    val after = request.query.optional[Int]("after").getOrElse(0)
    Source
      .tick(0.millis, 300.millis, ())
      .take(40)
      .statefulMap(() => (after, Set.empty[String]))(
        { case ((seen, proposals), _) =>
          val view     = participation.conversationView(participant, conversation)
          val messages = view.messages.filter(_.seq > seen)
          val fresh = view.proposals.filterNot(p => proposals.contains(p.id + p.state + p.edited))
          val events =
            messages.map(m =>
              CitizenEndpoint
                .event("message", com.github.plokhotnyuk.jsoniter_scala.core.writeToString(m))
            ) ++
              fresh.map(p =>
                CitizenEndpoint
                  .event("proposal", com.github.plokhotnyuk.jsoniter_scala.core.writeToString(p))
              )
          val next = (
            messages.lastOption.map(_.seq).getOrElse(seen),
            proposals ++ fresh.map(p => p.id + p.state + p.edited)
          )
          (next, events)
        },
        _ => None
      )
      .mapConcat(identity)
  }

  // ── deliberation (US4) ─────────────────────────────────────────────────────

  private val deliberation = Deliberation(reach)

  import DeliberationJson.{*, given}

  get("/issues/{issue}/expression") { (issue: String) =>
    Problems.handle(Problems.ok(deliberation.view(me(), issue)))
  }

  postBody("/issues/{issue}/expression/priority") { (issue: String, body: PriorityBody) =>
    Problems.handle(Problems.ok(deliberation.priority(request, me(), issue, body)))
  }

  postBody("/issues/{issue}/expression/trade-offs") { (issue: String, body: TradeOffBody) =>
    Problems.handle(Problems.ok(deliberation.tradeOff(request, me(), issue, body)))
  }

  postBody("/issues/{issue}/expression/considerations") { (issue: String, body: ConsiderationBody) =>
    Problems.handle(Problems.ok(deliberation.consider(request, me(), issue, body)))
  }

  postBody("/issues/{issue}/expression/withdraw") { (issue: String, _: Empty) =>
    Problems.handle(Problems.ok(deliberation.withdraw(request, me(), issue)))
  }

  // ── following and the inbox (US6) ─────────────────────────────────────────

  private val interests = Interests(reach)

  import InterestsJson.{*, given}

  get("/interests") { () =>
    Problems.handle(Problems.ok(interests.list(me())))
  }

  putBody("/interests/{issue}") { (issue: String, body: FollowBody) =>
    Problems.handle(Problems.ok(interests.follow(me(), issue, body)))
  }

  delete("/interests/{issue}") { (issue: String) =>
    Problems.handle {
      interests.stop(me(), issue)
      Problems.noContent
    }
  }

  get("/inbox") { () =>
    val after = request.query.optional[String]("after").map(java.time.Instant.parse)
    Problems.handle(Problems.ok(interests.inbox(me(), after)))
  }

  postBody("/inbox/{development}/read") { (development: String, _: Empty) =>
    Problems.handle(Problems.ok(interests.read(request, me(), development)))
  }

  // ── data rights (US7) ─────────────────────────────────────────────────────

  import DataRightsJson.{*, given}

  get("/data") { () =>
    Problems.handle(Problems.ok(rights.summary(me())))
  }

  postBody("/erasure") { (body: ErasureBody) =>
    Problems.handle(rights.request(request, me(), body))
  }

  get("/erasure") { () =>
    Problems.handle(Problems.ok(DataRightsJson.status(rights.record(me()))))
  }

  /**
   * New inbox entries as they are delivered, each a `notification` event; ended before the
   * gateway's fifteen seconds, and resumed with `after` (the last entry's time) on reconnect.
   */
  sse("/inbox/stream") { () =>
    val participant = me()
    val after       = request.query.optional[String]("after").map(java.time.Instant.parse)
    Source
      .tick(0.millis, 500.millis, ())
      .take(24)
      .statefulMap(() => after)(
        { (seen, _) =>
          val fresh = interests.inbox(participant, seen)
          val next  = fresh.lastOption.map(_.at).orElse(seen)
          (
            next,
            fresh.map(e =>
              CitizenEndpoint
                .event("notification", com.github.plokhotnyuk.jsoniter_scala.core.writeToString(e))
            )
          )
        },
        _ => None
      )
      .mapConcat(identity)
  }

object CitizenEndpoint:
  lazy val notice: String =
    val stream = getClass.getResourceAsStream("/notice.md")
    try IoSource.fromInputStream(stream, "UTF-8").mkString
    finally stream.close()

  def event(kind: String, json: String): String = s"""{"type":"$kind","data":$json}"""
