import { useEffect, useState } from "react";
import type { VoiceClient } from "../api/client.ts";
import type { SessionStore } from "../app/session.ts";

/** The privacy and AI notice, read from the service and acknowledged before the first message (FR-003). */
export function Notice({ client, session, onRead }: { client: VoiceClient; session: SessionStore; onRead: () => void }) {
  const [text, setText] = useState<string | undefined>();
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    client.get<string>("me", "/notice").then(setText, () => setFailed(true));
  }, [client]);

  if (failed) return <p role="alert">The notice could not be loaded, so Voice cannot start yet.</p>;
  if (text === undefined) return <p>Loading the notice…</p>;
  return (
    <section aria-labelledby="notice-heading">
      <h2 id="notice-heading">Before you start</h2>
      <pre className="notice">{text}</pre>
      <button
        onClick={() => {
          session.acknowledgeNotice();
          onRead();
        }}
      >
        I have read this
      </button>
    </section>
  );
}
