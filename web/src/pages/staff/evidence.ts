/** What the reviewer's evidence page decides, apart from React (FR-021, FR-028, FR-029). */
import type { EvidenceCard } from "../public/model.ts";

export interface StaffEvidence {
  card: EvidenceCard;
  bearsOn: string[];
  proposedAt: string;
  decidedBy?: string;
  decidedAt?: string;
  reason?: string;
  prompts: string[];
}

export interface PromptView {
  id: string;
  evidence: string;
  cause: "accepted" | "superseded";
  claims: string[];
  decisions: string[];
  raisedAt: string;
  resolved?: { by: string; at: string; note: string };
}

export interface EvidenceDraft {
  source: string;
  locator: string;
  excerpt: string;
  scope: string;
  authority: string;
  limitations: string;
  publishedAt?: string;
  bearsOn: string[];
}

/** The moves a link's state allows: rejected cannot be accepted, superseded is final. */
export function moves(e: StaffEvidence): Array<"accept" | "reject" | "supersede"> {
  switch (e.card.state) {
    case "candidate":
      return ["accept", "reject"];
    case "accepted":
      return ["supersede"];
    default:
      return [];
  }
}

/** Accepted links another may be superseded by: never the link itself. */
export function replacements(all: StaffEvidence[], e: StaffEvidence): StaffEvidence[] {
  return all.filter((o) => o.card.id !== e.card.id && o.card.state === "accepted");
}

/** A proposal is sent only when every field the card shows is filled in. */
export function draftBody(d: EvidenceDraft): EvidenceDraft | undefined {
  const required = [d.source, d.locator, d.excerpt, d.scope, d.authority, d.limitations];
  if (required.some((v) => v.trim() === "")) return undefined;
  return { ...d, publishedAt: d.publishedAt?.trim() ? d.publishedAt : undefined, bearsOn: d.bearsOn.filter((h) => h.trim() !== "") };
}

/** Open prompts first, oldest first: what a person still has to look at. */
export function openFirst(ps: PromptView[]): PromptView[] {
  return [...ps].sort((a, b) => Number(a.resolved !== undefined) - Number(b.resolved !== undefined) || a.raisedAt.localeCompare(b.raisedAt));
}
