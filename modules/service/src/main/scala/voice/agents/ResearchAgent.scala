package voice.agents

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, writeToString}
import com.thinkmorestupidless.ankka.agent.*
import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.ComponentClient
import voice.api.Problems
import voice.application.issues.{IssueEntity, SourceCardEntity}
import voice.application.reasoning.{Explanations, ReasoningClient}
import voice.domain.EvidenceState
import voice.domain.issues.{IssueState, SourceCardState}

import scala.io.Source

final case class ResearchInput(issueId: String, question: String, reasoningService: String)

final case class SuggestedSource(name: String, why: String)
final case class SuggestedQuestion(statement: String, why: String)
final case class SuggestedInvestigation(
    question: String,
    reason: String,
    records: Vector[String] = Vector.empty
)

/** What the agent suggests; every item is a suggestion until a representative acts on it (R15). */
final case class ResearchSuggestions(
    sources: Vector[SuggestedSource] = Vector.empty,
    questions: Vector[SuggestedQuestion] = Vector.empty,
    investigations: Vector[SuggestedInvestigation] = Vector.empty
)

given JsonValueCodec[ResearchInput]       = Problems.codec[ResearchInput]
given JsonValueCodec[ResearchSuggestions] = Problems.codec[ResearchSuggestions]
given Serializer[ResearchInput]           = Codecs.serializer[ResearchInput]("research-input")
given Serializer[ResearchSuggestions] =
  Codecs.serializer[ResearchSuggestions]("research-suggestions")

/** The public facts of an Issue as the agent reads them: no sealed copy, no participant. */
final case class IssueFacts(
    id: String,
    title: String,
    summary: String,
    contributions: Vector[String],
    questions: Vector[String],
    claims: Vector[String],
    evidence: Vector[String],
    options: Vector[String]
)
given JsonValueCodec[IssueFacts] = Problems.codec[IssueFacts]

/**
 * The research agent's tools (FR-027, FR-038): reads, and nothing else. Each reads a public record
 * by id, from the Issue, the source cards or the reasoning service's records, and returns it as
 * text. None writes, and none reads a sealed value, a restricted copy or an opinion.
 */
object ResearchTools:

  val Names: Set[String] =
    Set("read_issue", "read_evidence", "read_claim", "read_question", "read_source")

  /** Over the component client, as the agent builds them inside its handler. */
  def all(client: ComponentClient, reasoning: ReasoningClient): Seq[FunctionTool] =
    over(
      id => client.forEventSourcedEntity(EntityId(id)).call(IssueEntity.get).invoke(),
      id =>
        Option(client.forKeyValueEntity(EntityId(id)).call(SourceCardEntity.get).invoke())
          .filter(_.exists),
      reasoning
    )

  /** Over any reads at all: what a test builds them from. */
  def over(
      issue: String => IssueState,
      cards: String => Option[SourceCardState],
      reasoning: ReasoningClient
  ): Seq[FunctionTool] =
    val explanations = Explanations(reasoning, cards)
    import voice.api.EvidenceJson
    def json[A](a: A)(using c: JsonValueCodec[A]): String = writeToString(a)(using c)
    Seq(
      FunctionTool
        .named("read_issue")
        .describedAs(
          "Reads a public issue by id: title, summary, de-identified contributions, and the ids of its questions, claims, accepted evidence and options."
        )
        .param[String]("issue", "The issue id, iss-…")
        .handle { id =>
          val s = issue(id)
          if !s.exists then s"no issue $id"
          else
            json(
              IssueFacts(
                id,
                s.title,
                s.summary,
                s.contributions.map(_.text),
                s.questions.map(_.id),
                s.claims.map(_.claim.id),
                s.evidence.filter(_.state == EvidenceState.Accepted).map(_.linkId),
                s.options
              )
            )
        },
      FunctionTool
        .named("read_evidence")
        .describedAs(
          "Reads one accepted piece of evidence on an issue: source, passage, dates, scope, authority and limitations."
        )
        .param[String]("issue", "The issue id")
        .param[String]("evidence", "The evidence id, evl-…")
        .handle { (id, link) =>
          val s = issue(id)
          s.link(link)
            .filter(l => l.state == EvidenceState.Accepted || l.state == EvidenceState.Superseded)
            .fold(s"no public evidence $link on $id")(l =>
              json(explanations.card(id, l))(using EvidenceJson.given_JsonValueCodec_EvidenceCard)
            )
        },
      FunctionTool
        .named("read_claim")
        .describedAs("Reads why a claim linked to an issue is supported, and what contradicts it.")
        .param[String]("issue", "The issue id")
        .param[String]("claim", "The claim id")
        .handle { (id, claim) =>
          explanations
            .claim(id, issue(id), claim)
            .fold(s"no claim $claim on $id")(x =>
              json(x)(using EvidenceJson.given_JsonValueCodec_ClaimExplanation)
            )
        },
      FunctionTool
        .named("read_question")
        .describedAs(
          "Reads a question linked to an issue, with what the records say on each hypothesis, or that they do not answer it."
        )
        .param[String]("issue", "The issue id")
        .param[String]("question", "The question id")
        .handle { (id, question) =>
          explanations
            .question(id, issue(id), question)
            .fold(s"no question $question on $id")(x =>
              json(x)(using EvidenceJson.given_JsonValueCodec_QuestionAnswer)
            )
        },
      FunctionTool
        .named("read_source")
        .describedAs("Reads a source's name, licence, attribution and link.")
        .param[String]("source", "The source id, voice.src.…")
        .handle { id =>
          json(explanations.source(id))(using EvidenceJson.given_JsonValueCodec_SourceView)
        }
    )

/**
 * Suggests sources, questions and investigations for an Issue (FR-038, R15). Read-only tools; a
 * typed reply; no memory across runs. It records nothing: the research workflow records each
 * suggested investigation, labelled as suggested, and only a representative opens one.
 */
final class ResearchAgent(context: AgentContext) extends Agent:

  def research(input: ResearchInput): Effect[ResearchSuggestions] =
    val client    = componentClient
    val reasoning = ReasoningClient(context.services, input.reasoningService)
    effects
      .systemMessage(ResearchAgent.prompt)
      .userMessage(
        s"The issue is ${input.issueId}. The representative asks:\n<<<\n${input.question}\n>>>"
      )
      .tools(ResearchTools.all(client, reasoning)*)
      .memory(MemoryProvider.none)
      .thenReplyAs[ResearchSuggestions]

object ResearchAgent extends Agent.Companion[ResearchAgent](ComponentId("research-assistant")):

  lazy val prompt: String =
    val stream = getClass.getResourceAsStream("/prompts/research.md")
    try Source.fromInputStream(stream, "UTF-8").mkString
    finally stream.close()

  def create(context: AgentContext) = new ResearchAgent(context)

  val research = command("research")(_.research)
