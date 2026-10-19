/**
 * What the citizen pages decide, apart from React, so the node test runner can check it: the shapes
 * the service answers, the conversation folded from its stream, and the proposal card's passages.
 */
import type { SseEvent } from "../../api/client.ts";

export interface MessageView {
  seq: number;
  author: "citizen" | "voice";
  text: string;
  at: string;
}
export interface SpanView {
  message: number;
  from: number;
  to: number;
  passage: string;
}
export interface ElementView {
  kind: string;
  text: string;
  spans: SpanView[];
}
export interface SuggestionView {
  issue: string;
  title: string;
  score: number;
}
export interface ProposalView {
  id: string;
  original: string;
  summary: string;
  elements: ElementView[];
  confidence: string;
  suggestedIssues: SuggestionView[];
  edited: boolean;
  state: string;
  concern?: string;
  fromMessages: number[];
}
export interface ConversationView {
  id: string;
  messages: MessageView[];
  proposals: ProposalView[];
  confirmedConcerns: string[];
}
export interface TurnView {
  turn: string;
  status: "Thinking" | "Replied" | "CouldNotInterpret" | string;
  reply?: string;
  proposal?: ProposalView;
}
export interface SessionReply {
  participant: string;
  recoveryCredential: string;
  session: string;
  expiresAt: string;
  notice?: { privacy: string; ai: string };
}

/** An empty conversation, before the first answer from the service. */
export function emptyConversation(id: string): ConversationView {
  return { id, messages: [], proposals: [], confirmedConcerns: [] };
}

/**
 * One stream event folded into the conversation. A message already held is not added twice, which
 * is what lets a reconnect overlap; a proposal replaces the one with its id, because it can change.
 */
export function fold(view: ConversationView, event: SseEvent): ConversationView {
  let parsed: { type?: string; data?: unknown };
  try {
    parsed = JSON.parse(event.data);
  } catch {
    return view;
  }
  if (parsed.type === "message") {
    const message = parsed.data as MessageView;
    if (view.messages.some((m) => m.seq === message.seq)) return view;
    return { ...view, messages: [...view.messages, message].sort((a, b) => a.seq - b.seq) };
  }
  if (parsed.type === "proposal") {
    const proposal = parsed.data as ProposalView;
    const others = view.proposals.filter((p) => p.id !== proposal.id);
    return { ...view, proposals: [...others, proposal] };
  }
  return view;
}

/** Where a reconnect resumes: after the last message held, or from the start. */
export function cursorOf(view: ConversationView): string | undefined {
  const last = view.messages.at(-1);
  return last ? String(last.seq) : undefined;
}

/** A piece of a message, marked when an element of the proposal came from it. */
export interface Segment {
  text: string;
  kinds: string[];
}

/**
 * A citizen message cut into segments at every span boundary, each segment carrying the kinds of
 * the elements whose spans cover it, so the card can highlight exactly the passages each came from.
 * A span outside the text is ignored; the service drops them, and the card does not trust it to.
 */
export function highlight(message: MessageView, elements: ElementView[]): Segment[] {
  const text = message.text;
  const spans = elements.flatMap((e) =>
    e.spans.filter((s) => s.message === message.seq && s.from >= 0 && s.to > s.from && s.to <= text.length).map((s) => ({ ...s, kind: e.kind })),
  );
  const cuts = [...new Set([0, text.length, ...spans.flatMap((s) => [s.from, s.to])])].sort((a, b) => a - b);
  const segments: Segment[] = [];
  for (let i = 0; i < cuts.length - 1; i++) {
    const [from, to] = [cuts[i], cuts[i + 1]];
    const kinds = [...new Set(spans.filter((s) => s.from <= from && s.to >= to).map((s) => s.kind))];
    segments.push({ text: text.slice(from, to), kinds });
  }
  return segments;
}

const confidenceWords: Record<string, string> = {
  High: "Voice is fairly sure it understood",
  Medium: "Voice may have missed something",
  Low: "Voice is unsure it understood; please check closely",
};

/** The confidence in words, never a bare score. */
export function confidenceText(confidence: string): string {
  return confidenceWords[confidence] ?? confidenceWords.Medium;
}

/** Suggestions, best first, at most three: they are suggestions, and the citizen chooses. */
export function suggestions(proposal: ProposalView): SuggestionView[] {
  return [...proposal.suggestedIssues].sort((a, b) => b.score - a.score).slice(0, 3);
}

/** A proposal may be edited and confirmed only while it is open. */
export function isOpen(proposal: ProposalView): boolean {
  return proposal.state === "Open";
}

/** The body of an edit: the summary trimmed, refused when empty. */
export function editBody(summary: string): { summary: string } | undefined {
  const trimmed = summary.trim();
  return trimmed === "" ? undefined : { summary: trimmed };
}

/** A message the citizen may send: one to 4,000 characters (`conversation.message.empty`). */
export function sendable(text: string): boolean {
  const t = text.trim();
  return t.length > 0 && t.length <= 4000;
}
