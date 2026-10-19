import { test } from "node:test";
import assert from "node:assert/strict";
import { ApiError, problemOf, SseParser, VoiceClient } from "../src/api/client.ts";
import { SessionStore } from "../src/app/session.ts";
import { match } from "../src/app/paths.ts";
import { labelText, parseLabelsHeader, chronologyStates } from "../src/components/labels.ts";

test("a refusal body becomes an error carrying its rule", () => {
  const e = problemOf(409, JSON.stringify({ rule: "proposal.stale", status: 409, error: "stale", names: { proposal: "prp-1" } }));
  assert.ok(e instanceof ApiError);
  assert.equal(e.rule, "proposal.stale");
  assert.equal(e.names.proposal, "prp-1");
  assert.equal(problemOf(401, "").rule, "session.required");
});

test("the citizen's token is sent as a bearer header to /me only", async () => {
  const seen: Array<{ url: string; auth?: string }> = [];
  const fake = (async (url: string, init?: RequestInit) => {
    seen.push({ url, auth: (init?.headers as Record<string, string>)?.["Authorization"] });
    return new Response("{}", { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;
  const client = new VoiceClient("/api", { citizen: () => "tok", staff: () => undefined }, fake);
  await client.get("me", "/concerns");
  await client.get("public", "/issues");
  assert.deepEqual(seen, [
    { url: "/api/me/concerns", auth: "Bearer tok" },
    { url: "/api/public/issues", auth: undefined },
  ]);
});

test("server-sent events are parsed across chunks", () => {
  const parser = new SseParser();
  assert.deepEqual(parser.push("event: reply\nid: 3\ndata: {\"a\""), []);
  assert.deepEqual(parser.push(":1}\n\n: heartbeat\n\n"), [{ event: "reply", id: "3", data: '{"a":1}' }]);
});

test("the recovery credential is shown once and the notice comes before writing", () => {
  const store = new SessionStore();
  store.begin({ participant: "prt-1", token: "t", expiresAt: "x" }, "secret");
  assert.equal(store.mayWrite(), false);
  store.acknowledgeNotice();
  assert.equal(store.mayWrite(), true);
  assert.equal(store.takeRecoveryCredential(), "secret");
  assert.equal(store.takeRecoveryCredential(), undefined);
});

test("routes match their parameters", () => {
  assert.deepEqual(match("/issues/:issue", "/issues/iss-1?x=1"), { issue: "iss-1" });
  assert.equal(match("/issues/:issue", "/issues"), undefined);
});

test("state labels are the server's seven words", () => {
  assert.deepEqual(parseLabelsHeader(undefined), [...chronologyStates]);
  assert.equal(labelText("ai-interpreted"), "AI-interpreted");
  assert.equal(labelText("action-recorded"), "action recorded");
});

test("the default fetch is called bound to the global, as a browser's fetch requires", async () => {
  const real = globalThis.fetch;
  // A browser's fetch throws "Illegal invocation" when called with any other receiver.
  globalThis.fetch = function (this: unknown) {
    if (this !== globalThis && this !== undefined) throw new TypeError("Illegal invocation");
    return Promise.resolve(new Response("[]", { status: 200, headers: { "content-type": "application/json" } }));
  } as typeof fetch;
  try {
    assert.deepEqual(await new VoiceClient("http://voice.test").get("public", "/issues"), []);
  } finally {
    globalThis.fetch = real;
  }
});
