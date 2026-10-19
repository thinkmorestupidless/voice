/** What following and the inbox decide, apart from React (FR-045 to FR-049). */
import type { SseEvent } from "../../api/client.ts";

export interface InterestView {
  issue: string;
  title: string;
  delivery: string;
  since: string;
  active: boolean;
}

export interface InboxEntry {
  development: string;
  issue: string;
  text: string;
  links: string[];
  why: string;
  state: string;
  at: string;
  failedAttempts: number;
}

/** The one delivery in this feature; the service refuses any other (FR-046). */
export const InboxDelivery = "inbox-immediate";

export function follows(interests: InterestView[], issue: string): boolean {
  return interests.some((i) => i.issue === issue && i.active);
}

/** Entries by development, each once, oldest first: a stream reconnect may send one again. */
export function merge(held: InboxEntry[], fresh: InboxEntry[]): InboxEntry[] {
  const byId = new Map(held.map((e) => [e.development, e] as const));
  for (const e of fresh) byId.set(e.development, { ...byId.get(e.development), ...e });
  return [...byId.values()].sort((a, b) => a.at.localeCompare(b.at));
}

/** One stream event as an inbox entry, or nothing when it is not one. */
export function entryOf(event: SseEvent): InboxEntry | undefined {
  try {
    const parsed = JSON.parse(event.data) as { type?: string; data?: InboxEntry };
    return parsed.type === "notification" ? parsed.data : undefined;
  } catch {
    return undefined;
  }
}

/** Where the stream resumes: the newest entry's time. */
export function cursor(entries: InboxEntry[]): string | undefined {
  return entries.at(-1)?.at;
}

export function unread(entries: InboxEntry[]): number {
  return entries.filter((e) => e.state === "delivered").length;
}

/** A record link as a page of this interface, with its anchor kept. */
export function recordPage(link: string): string {
  return link.startsWith("/public/") ? link.slice("/public".length) : link;
}
