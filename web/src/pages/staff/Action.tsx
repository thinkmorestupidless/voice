import { useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client } from "../../app/context.ts";
import { ActionStates, actionBody, missingFields, type ActionDraft } from "./accountability.ts";

const empty: ActionDraft = { authority: "", owner: "", date: "", rationale: "", state: "proposed", publicSummary: "" };

/**
 * Record an action with every field it needs, then its decisions and outcomes (FR-041 to FR-043).
 * What is recorded is public at once; nothing here is shown as done before its record says so.
 */
export function Action({ issue, investigation }: { issue: string; investigation?: string }) {
  const [draft, setDraft] = useState<ActionDraft>({ ...empty, investigation });
  const [action, setAction] = useState<{ id: string; state: string } | undefined>();
  const [problem, setProblem] = useState<string | undefined>();
  const [approach, setApproach] = useState("");
  const [authority, setAuthority] = useState("");
  const [alternatives, setAlternatives] = useState("");
  const [justification, setJustification] = useState("");
  const [measured, setMeasured] = useState("");
  const [limitations, setLimitations] = useState("");
  const [done, setDone] = useState<string[]>([]);

  const field = (k: keyof ActionDraft) => ({
    value: draft[k] ?? "",
    onChange: (e: { target: { value: string } }) => setDraft({ ...draft, [k]: e.target.value }),
  });
  const missing = missingFields(draft);

  async function act<T>(what: string, call: () => Promise<T>): Promise<T | undefined> {
    setProblem(undefined);
    try {
      const r = await call();
      setDone([...done, what]);
      return r;
    } catch (e) {
      setProblem(e instanceof ApiError ? e.message : `The ${what} could not be recorded.`);
      return undefined;
    }
  }

  async function record() {
    const body = actionBody(draft);
    if (!body) return;
    const r = await act("action", () => client.post<{ id: string; state: string }>("staff", `/issues/${encodeURIComponent(issue)}/actions`, body));
    if (r) setAction(r);
  }

  return (
    <main>
      <h1>Record an action</h1>
      <label>Authority <input {...field("authority")} /></label>
      <label>Owner <input {...field("owner")} /></label>
      <label>Date <input type="date" {...field("date")} /></label>
      <label>Rationale <textarea {...field("rationale")} /></label>
      <label>
        State
        <select {...field("state")}>
          {ActionStates.map(([w, words]) => (
            <option key={w} value={w}>
              {words}
            </option>
          ))}
        </select>
      </label>
      <label>Public summary <textarea {...field("publicSummary")} /></label>
      {missing.length > 0 && <p>Still needed: {missing.join(", ")}.</p>}
      <button type="button" onClick={record} disabled={missing.length > 0 || action !== undefined}>
        Record the action
      </button>
      {action && (
        <>
          <p>Recorded as {action.id}.</p>
          <fieldset>
            <legend>Change its state</legend>
            {ActionStates.map(([w, words]) => (
              <button key={w} type="button" onClick={() => act("state", () => client.post("staff", `/actions/${action.id}/state`, { state: w }))}>
                {words}
              </button>
            ))}
          </fieldset>
          <fieldset>
            <legend>Record what it achieved</legend>
            <label>What was measured <textarea value={measured} onChange={(e) => setMeasured(e.target.value)} /></label>
            <label>Its limitations <textarea value={limitations} onChange={(e) => setLimitations(e.target.value)} /></label>
            <button type="button" disabled={!measured.trim() || !limitations.trim()} onClick={() => act("outcome", () => client.post("staff", `/actions/${action.id}/outcomes`, { measured, limitations }))}>
              Record the outcome
            </button>
          </fieldset>
        </>
      )}
      <fieldset>
        <legend>Record a decision</legend>
        <label>Approach <textarea value={approach} onChange={(e) => setApproach(e.target.value)} /></label>
        <label>Authority <input value={authority} onChange={(e) => setAuthority(e.target.value)} /></label>
        <label>Alternatives considered, one per line <textarea value={alternatives} onChange={(e) => setAlternatives(e.target.value)} /></label>
        <label>Justification <textarea value={justification} onChange={(e) => setJustification(e.target.value)} /></label>
        <button
          type="button"
          disabled={!approach.trim() || !authority.trim() || !justification.trim()}
          onClick={() =>
            act("decision", () =>
              client.post("staff", `/issues/${encodeURIComponent(issue)}/decisions`, {
                approach,
                authority,
                alternatives: alternatives.split("\n").map((a) => a.trim()).filter((a) => a !== ""),
                justification,
              }),
            )
          }
        >
          Record the decision
        </button>
      </fieldset>
      {problem && <p role="alert">{problem}</p>}
      {done.length > 0 && <p>Recorded: {done.join(", ")}.</p>}
    </main>
  );
}
