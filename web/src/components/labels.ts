/**
 * The seven chronology states, as the server names them in its `Voice-State-Labels` header
 * (FR-042, FR-061). The interface renders the server's words and invents none: an unknown state is
 * shown as the server sent it, never upgraded to a stronger one.
 */
export const chronologyStates = [
  "submitted",
  "ai-interpreted",
  "under-moderation",
  "staff-acknowledged",
  "under-investigation",
  "action-recorded",
  "outcome-verified",
] as const;

export function parseLabelsHeader(header: string | null | undefined): string[] {
  return header ? header.split(",").map((s) => s.trim()).filter(Boolean) : [...chronologyStates];
}

/** A label as a person reads it: the server's slug with its hyphens as spaces, except AI's. */
export function labelText(slug: string): string {
  if (slug === "ai-interpreted") return "AI-interpreted";
  if (slug === "staff-acknowledged") return "staff-acknowledged";
  return slug.replace(/-/g, " ");
}

/** Whether a slug is one of the server's chronology states. */
export function isChronologyState(slug: string, known: readonly string[] = chronologyStates): boolean {
  return known.includes(slug);
}
