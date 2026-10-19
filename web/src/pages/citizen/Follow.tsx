import { useEffect, useState } from "react";
import { ApiError } from "../../api/client.ts";
import { client, navigate, session } from "../../app/context.ts";
import { InboxDelivery, follows, type InterestView } from "./interests.ts";

/**
 * Follow or stop following one Issue: the participant's own choice, never made by reading,
 * searching or contributing (FR-045). Delivery is the inbox, said beside the button (FR-046).
 */
export function Follow({ issue }: { issue: string }) {
  const [following, setFollowing] = useState<boolean | undefined>();
  const [problem, setProblem] = useState<string | undefined>();
  const signedIn = session.tokens().citizen() !== undefined;

  useEffect(() => {
    if (!signedIn) return;
    client.get<InterestView[]>("me", "/interests").then((i) => setFollowing(follows(i, issue)), () => setFollowing(false));
  }, [issue, signedIn]);

  async function change(follow: boolean) {
    setProblem(undefined);
    try {
      if (follow) await client.put("me", `/interests/${encodeURIComponent(issue)}`, { delivery: InboxDelivery });
      else await client.del("me", `/interests/${encodeURIComponent(issue)}`);
      setFollowing(follow);
    } catch (e) {
      setProblem(e instanceof ApiError ? e.message : "That did not work.");
    }
  }

  if (!signedIn)
    return (
      <p>
        <a href="/me" onClick={(e) => { e.preventDefault(); navigate("/me"); }}>
          Start a private session
        </a>{" "}
        to follow this issue.
      </p>
    );
  if (following === undefined) return null;
  return (
    <p>
      {following ? (
        <button type="button" onClick={() => change(false)}>
          Stop following
        </button>
      ) : (
        <button type="button" onClick={() => change(true)}>
          Follow this issue
        </button>
      )}{" "}
      <small>Updates go to your Voice inbox, once per development.</small>
      {problem && <span role="alert"> {problem}</span>}
    </p>
  );
}
