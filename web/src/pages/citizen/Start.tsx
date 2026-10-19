import { useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client, navigate, session } from "../../app/context.ts";
import type { SessionReply } from "./model.ts";

/**
 * Starting with no identity (FR-001 to FR-003): an invite code, then the privacy and AI notice, then
 * the recovery credential shown once. No name, no email, nothing that identifies the person.
 */
export function Start() {
  const [invite, setInvite] = useState("");
  const [reply, setReply] = useState<SessionReply | undefined>();
  const [credential, setCredential] = useState<string | undefined>();
  const [problem, setProblem] = useState<string | undefined>();

  async function begin() {
    setProblem(undefined);
    try {
      const answer = await client.post<SessionReply>("me", "/sessions", { invite: invite.trim() });
      session.begin({ participant: answer.participant, token: answer.session, expiresAt: answer.expiresAt }, answer.recoveryCredential);
      setReply(answer);
    } catch (e) {
      setProblem(e instanceof ApiError && e.rule === "session.invite.invalid" ? "That invite code is not one Voice knows." : "Voice could not start a session; try again.");
    }
  }

  if (!reply)
    return (
      <section aria-labelledby="start-heading">
        <h2 id="start-heading">Talk to Voice</h2>
        <p>You will not be asked for your name or your email.</p>
        <label>
          Invite code <input value={invite} onChange={(e) => setInvite(e.target.value)} autoComplete="off" />
        </label>
        <button disabled={invite.trim() === ""} onClick={() => void begin()}>
          Start
        </button>
        <p>
          <a href="/me/recover">I have a recovery credential</a>
        </p>
        {problem && <p role="alert">{problem}</p>}
      </section>
    );

  if (credential === undefined && !session.mayWrite())
    return (
      <section aria-labelledby="notice-heading">
        <h2 id="notice-heading">Before you start</h2>
        <h3>Your privacy</h3>
        <pre className="notice">{reply.notice?.privacy}</pre>
        <h3>How Voice uses AI</h3>
        <pre className="notice">{reply.notice?.ai}</pre>
        <button
          onClick={() => {
            session.acknowledgeNotice();
            setCredential(session.takeRecoveryCredential() ?? "");
          }}
        >
          I have read this
        </button>
      </section>
    );

  return (
    <section aria-labelledby="credential-heading">
      <h2 id="credential-heading">Keep this recovery credential</h2>
      <p>It is the only way back to your conversations from another session. Voice shows it once and does not keep a copy you can ask for.</p>
      <pre className="credential">{credential}</pre>
      <button
        onClick={async () => {
          const created = await client.post<{ id: string }>("me", "/conversations");
          navigate(`/me/conversations/${created.id}`);
        }}
      >
        I have kept it; start a conversation
      </button>
    </section>
  );
}
