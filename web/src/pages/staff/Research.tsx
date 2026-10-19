import { useEffect, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client, navigate } from "../../app/context.ts";
import { runDone, suggestions, type ResearchRun } from "./accountability.ts";

/**
 * Ask the research agent, and read what it suggests. Every item carries the label the service gave
 * it; opening an investigation from one is the representative's act, not the agent's (R15).
 */
export function Research({ issue }: { issue: string }) {
  const [question, setQuestion] = useState("");
  const [run, setRun] = useState<ResearchRun | undefined>();
  const [problem, setProblem] = useState<string | undefined>();

  useEffect(() => {
    if (!run || runDone(run)) return;
    const timer = setTimeout(() => {
      client.get<ResearchRun>("staff", `/research/${encodeURIComponent(run.run)}`).then(setRun, () => undefined);
    }, 1000);
    return () => clearTimeout(timer);
  }, [run]);

  async function ask() {
    setProblem(undefined);
    try {
      const { run: id } = await client.post<{ run: string }>("staff", `/issues/${encodeURIComponent(issue)}/research`, { question });
      setRun(await client.get<ResearchRun>("staff", `/research/${encodeURIComponent(id)}`));
    } catch (e) {
      setProblem(e instanceof ApiError ? e.message : "The research could not be asked for.");
    }
  }

  async function open(suggestion: string) {
    try {
      const inv = await client.post<{ id: string }>("staff", `/issues/${encodeURIComponent(issue)}/investigations`, { fromSuggestion: suggestion });
      navigate(`/staff/investigations/${inv.id}`);
    } catch (e) {
      setProblem(e instanceof ApiError ? e.message : "The investigation could not be opened.");
    }
  }

  return (
    <main>
      <h1>Research</h1>
      <label>
        What should the agent look into?
        <textarea value={question} onChange={(e) => setQuestion(e.target.value)} />
      </label>
      <button type="button" onClick={ask} disabled={question.trim() === ""}>
        Ask for suggestions
      </button>
      {problem && <p role="alert">{problem}</p>}
      {run && !runDone(run) && <p>The agent is reading the issue's records…</p>}
      {run?.state === "failed" && <p role="alert">The agent could not make suggestions. Nothing was recorded.</p>}
      {run?.state === "done" && (
        <ul>
          {suggestions(run).map((s, i) => (
            <li key={i}>
              <strong>{s.label}</strong> {s.kind}: {s.text}. <small>{s.why}</small>
            </li>
          ))}
        </ul>
      )}
      {run?.state === "done" && run.recorded.length > 0 && (
        <section>
          <h2>Open one as an investigation</h2>
          {run.recorded.map((id) => (
            <button key={id} type="button" onClick={() => open(id)}>
              Open {id}
            </button>
          ))}
        </section>
      )}
    </main>
  );
}
