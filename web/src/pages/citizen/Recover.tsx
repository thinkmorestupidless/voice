import { useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client, navigate, session } from "../../app/context.ts";
import type { SessionReply } from "./model.ts";

/** Recovering a session with the credential; a new one replaces it and is shown once. */
export function Recover() {
  const [credential, setCredential] = useState("");
  const [fresh, setFresh] = useState<string | undefined>();
  const [problem, setProblem] = useState<string | undefined>();

  async function recover() {
    setProblem(undefined);
    try {
      const answer = await client.post<SessionReply>("me", "/sessions/recover", { recoveryCredential: credential.trim() });
      session.begin({ participant: answer.participant, token: answer.session, expiresAt: answer.expiresAt }, answer.recoveryCredential);
      session.acknowledgeNotice();
      setFresh(session.takeRecoveryCredential());
    } catch (e) {
      const rule = e instanceof ApiError ? e.rule : "";
      setProblem(
        rule === "participant.credential.invalid"
          ? "That credential does not match any participant."
          : rule === "participant.erased"
            ? "The participant this credential belonged to has been erased."
            : "Voice could not recover the session; try again.",
      );
    }
  }

  if (fresh !== undefined)
    return (
      <section aria-labelledby="recovered-heading">
        <h2 id="recovered-heading">Your session is back</h2>
        <p>Your old credential no longer works. Keep this new one; Voice shows it once.</p>
        <pre className="credential">{fresh}</pre>
        <button onClick={() => navigate("/me/conversations")}>Go to my conversations</button>
      </section>
    );

  return (
    <section aria-labelledby="recover-heading">
      <h2 id="recover-heading">Recover your session</h2>
      <label>
        Recovery credential <input value={credential} onChange={(e) => setCredential(e.target.value)} autoComplete="off" />
      </label>
      <button disabled={credential.trim() === ""} onClick={() => void recover()}>
        Recover
      </button>
      {problem && <p role="alert">{problem}</p>}
    </section>
  );
}
