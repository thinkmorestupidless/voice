import { VoiceClient } from "../api/client.ts";
import { SessionStore } from "./session.ts";

/** One client and one in-memory session for the tab (R9). */
export const session = new SessionStore();
export const client = new VoiceClient("/api", session.tokens());

/** Moves to `path` without a reload; the app re-renders on the event. */
export function navigate(path: string): void {
  history.pushState(null, "", path);
  dispatchEvent(new PopStateEvent("popstate"));
}
