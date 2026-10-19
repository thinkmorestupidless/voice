import { useEffect, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client } from "../../app/context.ts";
import { canRequest, className, requestBody, stateText, type DataSummary, type ErasureStatus } from "./rights.ts";

/** What Voice holds for the participant, what erasure removes and keeps and why, and the request. */
export function DataRights() {
  const [summary, setSummary] = useState<DataSummary | undefined>();
  const [status, setStatus] = useState<ErasureStatus | undefined>();
  const [credential, setCredential] = useState("");
  const [problem, setProblem] = useState<string | undefined>();

  useEffect(() => {
    client.get<DataSummary>("me", "/data").then(
      (s) => {
        setSummary(s);
        setStatus(s.erasure);
      },
      (e) => setProblem(e instanceof ApiError ? e.message : "What Voice holds could not be read."),
    );
  }, []);

  async function request() {
    try {
      setStatus(await client.post<ErasureStatus>("me", "/erasure", requestBody(credential)));
    } catch (e) {
      setProblem(e instanceof ApiError ? e.message : "The request could not be sent.");
    }
  }

  if (problem) return <p role="alert">{problem}</p>;
  if (!summary || !status) return <p>Reading what Voice holds…</p>;
  return (
    <main>
      <h1>Your data</h1>
      <h2>What Voice holds</h2>
      <ul>
        {summary.held.map((c) => (
          <li key={c.name}>
            {className(c.name)}: {c.count}
          </li>
        ))}
      </ul>
      <p>{summary.retention}</p>
      <h2>If you ask for erasure</h2>
      <p>Erased:</p>
      <ul>{status.erased.map((t) => <li key={t}>{t}</li>)}</ul>
      <p>Retained:</p>
      <ul>{status.retained.map((t) => <li key={t}>{t}</li>)}</ul>
      <p>{status.why}</p>
      <p>What remains public:</p>
      <ul>{status.remainsPublic.map((t) => <li key={t}>{t}</li>)}</ul>
      <h2>Status</h2>
      <p>{stateText(status.state)}</p>
      {canRequest(status) && (
        <form onSubmit={(e) => { e.preventDefault(); void request(); }}>
          <label>
            Your recovery credential, so the request is verified at once (optional)
            <input value={credential} onChange={(e) => setCredential(e.target.value)} autoComplete="off" />
          </label>
          <button type="submit">Erase my data</button>
        </form>
      )}
    </main>
  );
}
