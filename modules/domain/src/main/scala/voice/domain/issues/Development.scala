package voice.domain.issues

import voice.domain.*

import java.time.Instant

/** What an authoritative development records (FR-047). */
enum DevelopmentKind(val wire: String):
  case ActionRecorded   extends DevelopmentKind("action-recorded")
  case EvidenceAccepted extends DevelopmentKind("evidence-accepted")
  case DecisionRecorded extends DevelopmentKind("decision-recorded")
  case OutcomeRecorded  extends DevelopmentKind("outcome-recorded")
  case UpdatePublished  extends DevelopmentKind("update-published")

/** Whether a development is worth telling followers about. A correction of wording is not. */
enum Materiality:
  case Material, Minor

/**
 * One authoritative development on an Issue, minted once per authoritative event (FR-047): the
 * record it is about, by reference, and whether it is material. Followers' updates are keyed on it.
 */
final case class Development(
    developmentId: String,
    kind: DevelopmentKind,
    ref: String,
    materiality: Materiality,
    at: Instant
)

/**
 * A representative's narrative update (FR-044), public as written; a correction names what it
 * corrects.
 */
final case class NarrativeUpdate(
    developmentId: String,
    text: String,
    by: String,
    at: Instant,
    corrects: Option[String]
)

object Development:
  def of(
      id: String,
      kind: DevelopmentKind,
      ref: String,
      at: Instant,
      minor: Boolean = false
  ): Development =
    Development(id, kind, ref, if minor then Materiality.Minor else Materiality.Material, at)

  def checkUpdate(
      s: IssueState,
      issue: String,
      text: String,
      corrects: Option[String]
  ): Option[Refusal] =
    if text.trim.isEmpty then
      Some(Rules.UpdateTextRequired.refusal("An update needs its text.", "issue" -> issue))
    else
      corrects.filterNot(c => s.updates.exists(_.developmentId == c)).map { c =>
        Rules.UpdateCorrectionNotHeld.refusal(
          "The issue holds no such update to correct.",
          "issue"       -> issue,
          "development" -> c
        )
      }
