package voice.domain.issues

import voice.domain.*

import java.time.Instant

/**
 * Evidence proposed but not yet accepted: what the reviewer typed, held until acceptance writes it
 * to reasoning and then dropped, so an accepted passage is always read from reasoning (R4).
 */
final case class Candidate(
    source: String,
    locator: String,
    excerpt: String,
    publishedAt: Option[Instant] = None,
    author: Option[String] = None
)

/**
 * Voice's civic decision about one piece of evidence on an Issue (FR-021, FR-029): its state, who
 * decided and when, and the scope, authority and limitations reasoning has no field for.
 */
final case class EvidenceLink(
    linkId: String,
    source: String,
    evidence: Option[EvidenceRef],
    candidate: Option[Candidate],
    state: EvidenceState,
    scope: String,
    authority: String,
    limitations: String,
    bearsOn: Vector[HypothesisRef],
    proposedAt: Instant,
    decidedBy: Option[String] = None,
    decidedAt: Option[Instant] = None,
    reason: Option[String] = None,
    supersededBy: Option[String] = None
)

/**
 * A claim linked to an Issue: what it derives from and its stances, so contradiction is found here.
 */
final case class Stance(hypothesis: HypothesisRef, supports: Boolean)
final case class LinkedClaim(
    claim: ClaimRef,
    derivesFrom: Vector[EvidenceRef],
    stances: Vector[Stance],
    revises: Option[ClaimRef] = None
)

/** What a review prompt is answered with; resolving it changes nothing else (FR-028). */
final case class Resolution(by: String, at: Instant, note: String)

/**
 * Raised when evidence is accepted or superseded, naming every claim and decision that derives from
 * it or bears on what it bears on (FR-028). A prompt asks a person to look; it reverses nothing.
 */
final case class ReviewPrompt(
    promptId: String,
    evidence: String,
    cause: String,
    claims: Vector[ClaimRef],
    decisions: Vector[String],
    raisedAt: Instant,
    resolved: Option[Resolution] = None
)
