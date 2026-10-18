import { useEffect, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client, navigate } from "../../app/context.ts";
import { TaskStates, type InvestigationView } from "./accountability.ts";

/** One investigation: its question, where it came from, and its tasks with owners and due dates. */
export function Investigation({ id }: { id: string }) {
  const [inv, setInv] = useState<InvestigationView | undefined>();
  const [problem, setProblem] = useState<string | undefined>();
  const [text, setText] = useState("");
  const [owner, setOwner] = useState("");
  const [due, setDue] = useState("");
  const [state, setState] = useState("open");

  const path = `/investigations/${encodeURIComponent(id)}`;
  useEffect(() => {
    client.get<InvestigationView>("staff", path).then(setInv, (e) => setProblem(e instanceof ApiError ? e.message : "The investigation could not be read."));
  }, [path]);

  async function addTask() {
    try {
      setInv(await client.post<InvestigationView>("staff", `${path}/tasks`, { text, owner, due, state }));
      setText("");
      setOwner("");
      setDue("");
    } catch (e) {
      setProblem(e instanceof ApiError ? e.message : "The task could not be added.");
    }
  }

  if (!inv) return <p>{problem ?? "Reading the investigation…"}</p>;
  return (
    <main>
      <h1>{inv.question}</h1>
      <p>{inv.state}</p>
      {inv.reason && <p>Why: {inv.reason}</p>}
      {inv.records.length > 0 && (
        <p>Records it rests on: {inv.records.join(", ")}</p>
      )}
      <h2>Tasks</h2>
      <ul>
        {inv.tasks.map((t) => (
          <li key={t.id}>
            {t.text}: {t.owner}, due {t.due}, {TaskStates.find(([w]) => w === t.state)?.[1] ?? t.state}
          </li>
        ))}
      </ul>
      <fieldset>
        <legend>Add a task</legend>
        <input aria-label="Task" value={text} onChange={(e) => setText(e.target.value)} />
        <input aria-label="Owner" value={owner} onChange={(e) => setOwner(e.target.value)} />
        <input aria-label="Due" type="date" value={due} onChange={(e) => setDue(e.target.value)} />
        <select aria-label="State" value={state} onChange={(e) => setState(e.target.value)}>
          {TaskStates.map(([w, words]) => (
            <option key={w} value={w}>
              {words}
            </option>
          ))}
        </select>
        <button type="button" onClick={addTask} disabled={!text.trim() || !owner.trim() || !due}>
          Add
        </button>
      </fieldset>
      {problem && <p role="alert">{problem}</p>}
      <a href={`/staff/issues/${inv.issue}/actions?investigation=${inv.id}`} onClick={(e) => { e.preventDefault(); navigate(`/staff/issues/${inv.issue}/actions?investigation=${inv.id}`); }}>
        Record an action from this investigation
      </a>
    </main>
  );
}
