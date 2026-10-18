import { labelText } from "./labels.ts";

/** One state, in the server's words only (FR-061). */
export function StateLabel({ state }: { state: string }) {
  return (
    <span className="state-label" data-state={state}>
      {labelText(state)}
    </span>
  );
}
