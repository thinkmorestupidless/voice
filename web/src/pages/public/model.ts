/** The public bodies of the evidence routes, and what the pages decide from them, apart from React. */
import type { Aggregate, OptionView } from "./aggregate.ts";

export interface SourceView {
  id: string;
  name: string;
  licence: string;
  attribution: string;
  link: string;
  record: string;
}

export interface EvidenceCard {
  id: string;
  evidence?: string;
  state: string;
  source: SourceView;
  locator?: string;
  passage?: string;
  publishedAt?: string;
  recordedAt?: string;
  scope: string;
  authority: string;
  limitations: string;
  withdrawn?: string;
  supersededBy?: string;
  link: string;
}

export interface StanceView {
  hypothesis: string;
  statement: string;
  stance: "supports" | "contradicts";
}

export interface ClaimCard {
  id: string;
  statement: string;
  stances: StanceView[];
  evidence: EvidenceCard[];
  revises?: string;
  revisedBy: string[];
  link: string;
}

export interface ClaimExplanation {
  claim?: ClaimCard;
  contradicting: ClaimCard[];
  abstained: boolean;
  reason?: string;
}

export interface ClaimSummary {
  id: string;
  statement: string;
  link: string;
}

export interface HypothesisAnswer {
  id: string;
  statement: string;
  supporting: ClaimSummary[];
  contradicting: ClaimSummary[];
}

export interface QuestionAnswer {
  id: string;
  statement: string;
  hypotheses: HypothesisAnswer[];
  abstained: boolean;
  reason?: string;
}

export interface Counts {
  contributions: number;
  withdrawn: number;
  authorsErased: number;
  caveat: string;
}

export interface ActionView {
  id: string;
  state: string;
  authority: string;
  owner: string;
  date: string;
  summary: string;
  rationale: string;
  recordedBy: string;
  recordedAt: string;
  completed: boolean;
  investigation?: string;
}

export interface InvestigationSummary {
  id: string;
  question: string;
  state: string;
  openedAt?: string;
}

export interface DecisionView {
  id: string;
  approach: string;
  authority: string;
  alternatives: string[];
  justification: string;
  recordedBy: string;
  recordedAt: string;
}

export interface OutcomeView {
  id: string;
  action: string;
  actionLink: string;
  measured: string;
  limitations: string;
  claimsResolved: boolean;
  resolution: string;
  recordedBy: string;
  recordedAt: string;
}

export interface UpdateView {
  id: string;
  text: string;
  publishedBy: string;
  publishedAt: string;
  corrects?: string;
}

export interface EntryView {
  state: string;
  note: string;
  actor: string;
  at: string;
  ref?: string;
  unanswered?: boolean;
}

export interface IssuePage {
  id: string;
  title: string;
  summary: string;
  contributions: Array<{ pub: string; text: string; attribution?: string; publishedAt: string; authorErased?: string }>;
  counts: Counts;
  chronology: EntryView[];
  limitations: string;
  questions: QuestionAnswer[];
  evidence: EvidenceCard[];
  claims: ClaimSummary[];
  actions: ActionView[];
  investigations: InvestigationSummary[];
  decisions: DecisionView[];
  outcomes: OutcomeView[];
  updates: UpdateView[];
  options: OptionView[];
  aggregate?: Aggregate;
}

export interface Hit {
  kind: "issue" | "source" | "option";
  id: string;
  version?: string;
  date?: string;
  title: string;
  snippet: string;
  link: string;
}

/** A link the service gave, as a page of this interface: the service's `/public` is the site's root. */
export function page(link: string): string {
  return link.startsWith("/public/") ? link.slice("/public".length) : link;
}

/** What a card says of its passage: the words, the withdrawal note, or that it is not available. */
export function passageText(card: EvidenceCard): string {
  if (card.withdrawn !== undefined) return `Withdrawn from the record: ${card.withdrawn}`;
  return card.passage ?? "The passage is not available.";
}

/** The date a reader is shown: the source's publication date, else when it was recorded. */
export function shownDate(card: EvidenceCard): string {
  const at = card.publishedAt ?? card.recordedAt;
  return at ? at.slice(0, 10) : "undated";
}

/** The evidence's state as a reader reads it; superseded evidence says what replaced it. */
export function stateText(card: EvidenceCard): string {
  if (card.state === "superseded") return card.supersededBy ? `superseded by ${card.supersededBy}` : "superseded";
  return card.state;
}

/** Support and contradiction side by side: the claim's cards, and each contradicting claim's. */
export function sides(x: ClaimExplanation): { support: EvidenceCard[]; against: Array<{ claim: ClaimCard; cards: EvidenceCard[] }> } {
  return {
    support: x.claim?.evidence ?? [],
    against: x.contradicting.map((c) => ({ claim: c, cards: c.evidence })),
  };
}

export const Abstention = "The records Voice holds do not support an answer to this.";

/** What a reader is told when Voice abstains: the service's reason, and nothing cited. */
export function abstentionText(x: { abstained: boolean; reason?: string }): string | undefined {
  return x.abstained ? (x.reason ?? Abstention) : undefined;
}

/** A search hit's line under its title: what it is and its version or date. */
export function hitLine(h: Hit): string {
  const when = h.date ? h.date.slice(0, 10) : h.version ?? "";
  return [h.kind, h.id, when].filter((s) => s !== "").join(" · ");
}
