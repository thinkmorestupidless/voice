import { useEffect, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client } from "../../app/context.ts";
import { causeText, className, ordered, type ErasureRow } from "../citizen/rights.ts";

/** The operator's erasures: held requests to verify, and receipts with their cause and no personal value. */
export function Erasures() {
  const [rows, setRows] = useState<ErasureRow[]>([]);
  const [problem, setProblem] = useState<string | undefined>();
  const [evidence, setEvidence] = useState<Record<string, string>>({});

  function load() {
    client.get<ErasureRow[]>("staff", "/erasures").then(
      (r) => setRows(ordered(r)),
      (e) => setProblem(e instanceof ApiError ? e.message : "The erasures could not be read."),
    );
  }
  useEffect(load, []);

  async function verify(participant: string) {
    const how = (evidence[participant] ?? "").trim();
    if (how === "") {
      setProblem("Say how the request was confirmed as theirs before verifying it.");
      return;
    }
    try {
      await client.post("staff", `/erasures/${encodeURIComponent(participant)}/verify`, { evidence: how });
      load();
    } catch (e) {
      setProblem(e instanceof ApiError ? e.message : "The request could not be verified.");
    }
  }

  return (
    <main>
      <h1>Erasures</h1>
      {problem && <p role="alert">{problem}</p>}
      {rows.length === 0 && <p>No erasures.</p>}
      {rows.map((r) => (
        <article key={r.participant} data-state={r.state}>
          <p>
            {r.state} · {causeText(r.cause)} · {r.requestedAt}
          </p>
          {r.state === "held-for-operator" && (
            <form onSubmit={(e) => { e.preventDefault(); void verify(r.participant); }}>
              <label>
                How the request was confirmed as theirs (not kept)
                <input
                  value={evidence[r.participant] ?? ""}
                  onChange={(e) => setEvidence({ ...evidence, [r.participant]: e.target.value })}
                />
              </label>
              <button type="submit">Verify</button>
            </form>
          )}
          {r.receipt && (
            <dl>
              <dt>Authorised by</dt>
              <dd>{r.receipt.authorisedBy}</dd>
              <dt>Verified by</dt>
              <dd>{r.receipt.verifiedBy}</dd>
              <dt>Completed</dt>
              <dd>
                {r.receipt.at} {r.receipt.completionVerified ? "(key destroyed, checked)" : "(not checked)"}
              </dd>
              <dt>Erased</dt>
              <dd>{r.receipt.classes.filter((c) => c.count > 0).map((c) => `${className(c.name)}: ${c.count}`).join(", ")}</dd>
            </dl>
          )}
        </article>
      ))}
    </main>
  );
}
