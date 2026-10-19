/** How a field the service could not open is shown: the service's own marker, never an error (FR-056). */
export const Redacted = "redacted or unavailable";

/** The text to show for a protected field: the marker when it is erased or absent. */
export function sealedText(value: string | undefined | null): string {
  if (value === undefined || value === null || value.trim() === "" || value === Redacted) return Redacted;
  return value;
}

export function isRedacted(value: string | undefined | null): boolean {
  return sealedText(value) === Redacted;
}
