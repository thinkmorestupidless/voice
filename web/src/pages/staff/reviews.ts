/** What the reviewer's queue decides, apart from React (FR-014 to FR-016). */
import type { SuggestionView } from "../citizen/model.ts";

export interface ReviewItem {
  contribution: string;
  derivative: string;
  mode: string;
  audience: string;
  status: string;
  flags: string[];
  reasons: string[];
  secondStep?: { reviewer: string; reason: string; at: string };
  association: { issue?: string; newIssueAsked: boolean };
  suggestedIssues: SuggestionView[];
  submittedAt?: string;
  ageSeconds: number;
  issue?: string;
}

/** A flagged derivative needs its second step recorded first, in a separate decision (FR-016). */
export function needsSecondStep(item: ReviewItem): boolean {
  return item.flags.length > 0 && item.secondStep === undefined;
}

export function decidable(item: ReviewItem): boolean {
  return item.status === "AwaitingReviewer";
}

/** The Issue the reviewer approves into: the participant's choice first, else the best suggestion. */
export function defaultIssue(item: ReviewItem): string | undefined {
  return item.association.issue ?? (item.association.newIssueAsked ? undefined : item.suggestedIssues[0]?.issue);
}

/** The approval body; a new Issue needs a title. Never carries the second step (same-click). */
export function approveBody(issue: string | undefined, title: string, summary: string): { issue: string } | { new: { title: string; summary: string } } | undefined {
  if (issue) return { issue };
  if (title.trim() === "") return undefined;
  return { new: { title: title.trim(), summary: summary.trim() } };
}

/** Oldest first, so nothing waits behind newer work. */
export function ordered(items: ReviewItem[]): ReviewItem[] {
  return [...items].sort((a, b) => b.ageSeconds - a.ageSeconds);
}

export function age(seconds: number): string {
  if (seconds < 3600) return `${Math.max(1, Math.round(seconds / 60))} min`;
  if (seconds < 86400) return `${Math.round(seconds / 3600)} h`;
  return `${Math.round(seconds / 86400)} d`;
}
