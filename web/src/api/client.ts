/**
 * The interface's one way to Voice: the service's three endpoints under the `/api` mount, and
 * nothing else (R13). Bearer tokens come from memory, never from storage; a refusal's body becomes
 * a typed error carrying the rule a person or a test can act on.
 */

export type Audience = "public" | "me" | "staff";

/** A refusal as the service answers it: `{ rule, status, error, names }` (contracts/refusals.md). */
export class ApiError extends Error {
  readonly rule: string;
  readonly status: number;
  readonly names: Record<string, string>;
  constructor(rule: string, status: number, error: string, names: Record<string, string> = {}) {
    super(error);
    this.rule = rule;
    this.status = status;
    this.names = names;
  }
}

/** Reads a refusal from a failed answer's body; a body that is not one still names its status. */
export function problemOf(status: number, body: string): ApiError {
  try {
    const parsed = JSON.parse(body) as { rule?: string; error?: string; names?: Record<string, string> };
    if (typeof parsed.rule === "string") return new ApiError(parsed.rule, status, parsed.error ?? "", parsed.names ?? {});
  } catch {
    // not JSON: fall through
  }
  return new ApiError(status === 401 ? "session.required" : "unknown", status, body || `answered ${status}`);
}

export interface Tokens {
  /** The citizen's session token, held in memory only. */
  citizen(): string | undefined;
  /** The staff person's access token, held in memory only. */
  staff(): string | undefined;
}

export const noTokens: Tokens = { citizen: () => undefined, staff: () => undefined };

export class VoiceClient {
  private readonly base: string;
  private readonly tokens: Tokens;
  private readonly fetchImpl: typeof fetch;

  constructor(base = "/api", tokens: Tokens = noTokens, fetchImpl: typeof fetch = fetch) {
    this.base = base;
    this.tokens = tokens;
    // A browser's fetch called as this.fetchImpl(...) throws "Illegal invocation": bind it to the global.
    this.fetchImpl = fetchImpl === globalThis.fetch ? fetchImpl.bind(globalThis) : fetchImpl;
  }

  private headers(audience: Audience, body: boolean): Record<string, string> {
    const headers: Record<string, string> = {};
    const token = audience === "me" ? this.tokens.citizen() : audience === "staff" ? this.tokens.staff() : undefined;
    if (token) headers["Authorization"] = `Bearer ${token}`;
    if (body) headers["Content-Type"] = "application/json";
    return headers;
  }

  /** One request; the parsed JSON on success, an `ApiError` otherwise. */
  async send<T>(audience: Audience, method: string, path: string, body?: unknown): Promise<T> {
    const answer = await this.fetchImpl(`${this.base}/${audience}${path}`, {
      method,
      headers: this.headers(audience, body !== undefined),
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await answer.text();
    if (!answer.ok) throw problemOf(answer.status, text);
    if (text === "") return undefined as T;
    const type = answer.headers.get("content-type") ?? "";
    return (type.includes("json") ? JSON.parse(text) : text) as T;
  }

  get<T>(audience: Audience, path: string): Promise<T> {
    return this.send<T>(audience, "GET", path);
  }
  post<T>(audience: Audience, path: string, body: unknown = {}): Promise<T> {
    return this.send<T>(audience, "POST", path, body);
  }
  put<T>(audience: Audience, path: string, body: unknown): Promise<T> {
    return this.send<T>(audience, "PUT", path, body);
  }
  del<T>(audience: Audience, path: string): Promise<T> {
    return this.send<T>(audience, "DELETE", path);
  }

  /**
   * Server-sent events with the bearer header (an `EventSource` cannot send one), reconnecting with
   * `after` set to the last id seen, because the gateway ends every response at its bound. Voice's
   * stream carries no ids (an endpoint can send only unnamed events), so a caller that knows its own
   * position passes `cursor`, which is asked before every reconnect and wins over an id.
   */
  stream(
    audience: Audience,
    path: string,
    onEvent: (event: SseEvent) => void,
    signal: AbortSignal,
    cursor?: () => string | undefined,
    retryMs = 1000,
  ): Promise<void> {
    let after: string | undefined;
    const once = async (): Promise<void> => {
      after = cursor?.() ?? after;
      const query = after ? `${path.includes("?") ? "&" : "?"}after=${encodeURIComponent(after)}` : "";
      const answer = await this.fetchImpl(`${this.base}/${audience}${path}${query}`, {
        headers: this.headers(audience, false),
        signal,
      });
      if (!answer.ok || !answer.body) throw problemOf(answer.status, await answer.text());
      const reader = answer.body.pipeThrough(new TextDecoderStream()).getReader();
      const parser = new SseParser();
      for (;;) {
        const { value, done } = await reader.read();
        if (done) return;
        for (const event of parser.push(value)) {
          if (event.id) after = event.id;
          onEvent(event);
        }
      }
    };
    const loop = async (): Promise<void> => {
      // A response that ends is the server's bound, not a failure: reconnect at once. A failure
      // the server says is the caller's (a 4xx) ends the stream; anything else retries after a pause.
      while (!signal.aborted) {
        try {
          await once();
        } catch (e) {
          if (signal.aborted) return;
          if (e instanceof ApiError && e.status < 500) throw e;
          await new Promise((r) => setTimeout(r, retryMs));
        }
      }
    };
    return loop();
  }
}

export interface SseEvent {
  event: string;
  data: string;
  id?: string;
}

/** Splits a text/event-stream into events, across chunk boundaries. */
export class SseParser {
  private buffer = "";

  push(chunk: string): SseEvent[] {
    this.buffer += chunk.replace(/\r\n/g, "\n");
    const events: SseEvent[] = [];
    let end = this.buffer.indexOf("\n\n");
    while (end >= 0) {
      const block = this.buffer.slice(0, end);
      this.buffer = this.buffer.slice(end + 2);
      const event: SseEvent = { event: "message", data: "" };
      const data: string[] = [];
      for (const line of block.split("\n")) {
        if (line.startsWith(":")) continue;
        const colon = line.indexOf(":");
        const field = colon < 0 ? line : line.slice(0, colon);
        const value = colon < 0 ? "" : line.slice(colon + 1).replace(/^ /, "");
        if (field === "event") event.event = value;
        else if (field === "data") data.push(value);
        else if (field === "id") event.id = value;
      }
      event.data = data.join("\n");
      if (data.length > 0 || event.event !== "message") events.push(event);
      end = this.buffer.indexOf("\n\n");
    }
    return events;
  }
}
