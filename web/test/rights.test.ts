import { test } from "node:test";
import assert from "node:assert/strict";
import { canRequest, causeText, className, ordered, requestBody, stateText, type ErasureStatus } from "../src/pages/citizen/rights.ts";
import { Redacted, isRedacted, sealedText } from "../src/components/sealed.ts";

const status = (state: string): ErasureStatus => ({ state, erased: [], retained: [], why: "", remainsPublic: [] });

test("a participant asks once, with the credential when they give one", () => {
  assert.equal(canRequest(status("None")), true);
  assert.equal(canRequest(status("HeldForOperator")), false);
  assert.deepEqual(requestBody("  "), {});
  assert.deepEqual(requestBody(" prt-1~secret "), { recoveryCredential: "prt-1~secret" });
  assert.match(stateText("HeldForOperator"), /operator/);
});

test("an erased field shows the service's marker and never throws", () => {
  assert.equal(sealedText(undefined), Redacted);
  assert.equal(sealedText(""), Redacted);
  assert.equal(sealedText(Redacted), Redacted);
  assert.equal(sealedText("The bus stopped."), "The bus stopped.");
  assert.equal(isRedacted(null), true);
});

test("the operator sees held requests first, and causes and classes in words", () => {
  const rows = ordered([
    { participant: "prt-b", state: "completed", requestedAt: "2026-11-02T09:00:00Z" },
    { participant: "prt-a", state: "held-for-operator", requestedAt: "2026-11-03T09:00:00Z" },
  ]);
  assert.deepEqual(rows.map((r) => r.participant), ["prt-a", "prt-b"]);
  assert.equal(causeText("dormancy"), "no session for the dormancy period");
  assert.equal(className("public-copies-retained"), "de-identified copies retained");
});
