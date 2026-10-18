import { useEffect, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client } from "../../app/context.ts";
import { cellText, windowText, type Cell, type Window } from "../public/aggregate.ts";
import type { ClaimSummary, IssuePage, QuestionAnswer } from "../public/model.ts";

interface Measure {
  label: string;
  value: Cell;
  basis: string;
  window: Window;
  method: string;
  caveat: string;
}

interface IssueForStaff {
  page: IssuePage;
  restricted: Array<{ pub: string; text: string; publishedAt: string }>;
  measures: Measure[];
  unresolved: QuestionAnswer[];
  contrary: ClaimSummary[];
}

/** The representative's concerns dashboard: every measure labelled, no population claim (FR-034, FR-037). */
export function IssueDashboard({ issue }: { issue: string }) {
  const [view, setView] = useState<IssueForStaff | undefined>();
  const [problem, setProblem] = useState<string | undefined>();
  useEffect(() => {
    client
      .get<IssueForStaff>("staff", `/issues/${encodeURIComponent(issue)}`)
      .then(setView)
      .catch((e) => setProblem(e instanceof ApiError ? e.message : "The dashboard could not be read."));
  }, [issue]);
  if (problem) return <p role="alert">{problem}</p>;
  if (!view) return <p>Reading the dashboard…</p>;
  const threshold = view.page.aggregate?.threshold ?? 5;
  return (
    <main>
      <h1>{view.page.title}</h1>
      <table>
        <thead>
          <tr>
            <th>Measure</th>
            <th>Count</th>
            <th>Counts</th>
            <th>When</th>
            <th>How collected</th>
          </tr>
        </thead>
        <tbody>
          {view.measures.map((m) => (
            <tr key={m.label}>
              <td>{m.label}</td>
              <td>{cellText(m.value, threshold)}</td>
              <td>{m.basis}</td>
              <td>{windowText(m.window)}</td>
              <td>
                {m.method} <small>{m.caveat}</small>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <UpdateForm issue={issue} />
      <section>
        <h2>Restricted contributions</h2>
        {view.restricted.length === 0 ? <p>None.</p> : view.restricted.map((r) => <blockquote key={r.pub}>{r.text}</blockquote>)}
      </section>
      <section>
        <h2>Questions the records do not answer</h2>
        <ul>
          {view.unresolved.map((q) => (
            <li key={q.id}>{q.statement}</li>
          ))}
        </ul>
      </section>
      <section>
        <h2>Contrary perspectives</h2>
        <ul>
          {view.contrary.map((c) => (
            <li key={c.id}>{c.statement}</li>
          ))}
        </ul>
      </section>
    </main>
  );
}

/**
 * A narrative update, written or reviewed by the representative before it is published (FR-044).
 * Publishing tells every follower once; a correction of an earlier update's wording tells no one.
 */
function UpdateForm({ issue }: { issue: string }) {
  const [text, setText] = useState("");
  const [corrects, setCorrects] = useState("");
  const [done, setDone] = useState<string | undefined>();
  const [problem, setProblem] = useState<string | undefined>();

  async function publish() {
    setProblem(undefined);
    try {
      const d = await client.post<{ id: string; materiality: string }>("staff", `/issues/${encodeURIComponent(issue)}/updates`, {
        text,
        ...(corrects.trim() ? { corrects: corrects.trim() } : {}),
      });
      setDone(d.materiality === "minor" ? "The correction is published. Followers are not told of a correction." : "The update is published. Each follower is told once.");
      setText("");
      setCorrects("");
    } catch (e) {
      setProblem(e instanceof ApiError ? e.message : "The update could not be published.");
    }
  }

  return (
    <section>
      <h2>Publish an update</h2>
      <label>
        What you want followers to know, as you have written or reviewed it
        <textarea value={text} onChange={(e) => setText(e.target.value)} />
      </label>
      <label>
        Corrects the wording of update (optional)
        <input value={corrects} onChange={(e) => setCorrects(e.target.value)} />
      </label>
      <button type="button" onClick={publish} disabled={text.trim() === ""}>
        Publish
      </button>
      {done && <p>{done}</p>}
      {problem && <p role="alert">{problem}</p>}
    </section>
  );
}
