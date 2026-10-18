import { useEffect, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client, navigate } from "../../app/context.ts";
import { approval, chosenMode, stillPresent, type ContributionView, type SharingOptions } from "./sharing.ts";

/**
 * Sharing a confirmed concern (FR-011, FR-012): the four modes in plain words, private chosen, the
 * proposed text with the details Voice found, editable, and the exact text and audience shown before
 * the participant approves.
 */
export function Share({ concern }: { concern: string }) {
  const [options, setOptions] = useState<SharingOptions | undefined>();
  const [mode, setMode] = useState("private");
  const [text, setText] = useState("");
  const [name, setName] = useState("");
  const [problem, setProblem] = useState<string | undefined>();

  useEffect(() => {
    client.get<SharingOptions>("me", `/concerns/${concern}/sharing`).then((o) => {
      setOptions(o);
      setMode(chosenMode(o));
      setText(o.derivative);
    }, () => setProblem("Sharing could not be loaded."));
  }, [concern]);

  if (!options) return <p>{problem ?? "Loading…"}</p>;
  const chosen = options.modes.find((m) => m.mode === mode);
  const present = stillPresent(text, options.detections);
  const ready = approval(mode, text, name);

  async function approve() {
    if (!ready.body) return;
    try {
      const c = await client.post<ContributionView>("me", `/concerns/${concern}/contributions`, ready.body);
      navigate(`/me/contributions/${c.id}`);
    } catch (e) {
      setProblem(e instanceof ApiError ? e.message : "Voice could not record your approval.");
    }
  }

  return (
    <section aria-labelledby="share-heading">
      <h2 id="share-heading">Choose what to share, and with whom</h2>
      <fieldset>
        <legend>Sharing</legend>
        {options.modes.map((m) => (
          <label key={m.mode}>
            <input type="radio" name="mode" checked={mode === m.mode} onChange={() => setMode(m.mode)} /> <strong>{m.label}</strong>: {m.explanation}
          </label>
        ))}
      </fieldset>
      {mode !== "private" && (
        <>
          <label>
            The exact text that would be shared <textarea value={text} onChange={(e) => setText(e.target.value)} rows={5} />
          </label>
          {present.length > 0 && (
            <div role="note">
              <p>These details could identify someone. You may want to take them out:</p>
              <ul>
                {present.map((d, i) => (
                  <li key={i}>
                    <mark>{d.passage}</mark> ({d.kind})
                  </li>
                ))}
              </ul>
            </div>
          )}
          {mode === "public-attribution" && (
            <label>
              The name to show <input value={name} onChange={(e) => setName(e.target.value)} />
            </label>
          )}
          <p>
            It would be seen by: <strong>{chosen?.audience}</strong>.
          </p>
        </>
      )}
      {ready.problem ? <p>{ready.problem}</p> : <button onClick={() => void approve()}>Approve exactly this text</button>}
      {problem && <p role="alert">{problem}</p>}
    </section>
  );
}
