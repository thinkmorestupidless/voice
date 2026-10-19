/** What the sharing and contribution pages decide, apart from React (FR-011 to FR-018). */
import type { SuggestionView } from "./model.ts";

export interface ModeOption {
  mode: string;
  label: string;
  explanation: string;
  audience: string;
  chosen: boolean;
}
export interface DetectionView {
  kind: string;
  passage: string;
}
export interface ReviewView {
  outcome: "approved" | "rejected";
  reason?: string;
  at: string;
}
export interface ContributionView {
  id: string;
  concern: string;
  mode: string;
  audience: string;
  state: string;
  text: string;
  attribution?: string;
  review?: ReviewView;
  association: { issue?: string; newIssueAsked: boolean };
  issue?: string;
  suggestedIssues: SuggestionView[];
}
export interface SharingOptions {
  concern: string;
  modes: ModeOption[];
  derivative: string;
  flags: string[];
  reasons: string[];
  detections: DetectionView[];
  suggestedIssues: SuggestionView[];
  contributions: ContributionView[];
}

/** The mode already chosen, which is private unless the service says otherwise (FR-011). */
export function chosenMode(options: SharingOptions): string {
  return options.modes.find((m) => m.chosen)?.mode ?? "private";
}

/** The passages a participant may want to remove, still present in what they are about to approve. */
export function stillPresent(text: string, detections: DetectionView[]): DetectionView[] {
  return detections.filter((d) => d.passage !== "" && text.includes(d.passage));
}

/**
 * The approval body, or why there is none: the exact text as it stands, and a name only for a
 * publicly attributed contribution. A private choice needs no approval at all.
 */
export function approval(mode: string, text: string, name: string): { body?: { mode: string; text: string; name?: string }; problem?: string } {
  const exact = text.trim();
  if (mode === "private") return { problem: "Private keeps the concern in your conversation; there is nothing to approve." };
  if (exact === "") return { problem: "There is no text to share." };
  if (mode === "public-attribution") {
    if (name.trim() === "") return { problem: "Give the name you want shown." };
    return { body: { mode, text: exact, name: name.trim() } };
  }
  return { body: { mode, text: exact } };
}

/** What the participant can do next with a contribution, from its state label. */
export function nextSteps(c: ContributionView): Array<"submit" | "associate" | "withdraw"> {
  switch (c.state) {
    case "approved by you":
      return ["submit", "associate", "withdraw"];
    case "under moderation":
      return ["associate", "withdraw"];
    case "published":
      return ["withdraw"];
    case "not published":
    case "draft":
      return ["withdraw"];
    default:
      return [];
  }
}

/** The outcome in words: a rejection always with its reason (FR-015). */
export function outcomeText(c: ContributionView): string | undefined {
  if (!c.review) return undefined;
  return c.review.outcome === "approved" ? "A reviewer approved this." : `A reviewer did not publish this: ${c.review.reason ?? "no reason was recorded"}.`;
}
