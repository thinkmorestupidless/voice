/** The labelled aggregate of opinions, and how each figure is said, apart from React (FR-033, FR-034). */

export interface Cell {
  key: string;
  count?: number;
  suppressed: boolean;
}

export interface Window {
  from?: string;
  to?: string;
}

export interface Aggregate {
  participants: Cell;
  contributions: Cell;
  method: string;
  window: Window;
  deduplication: string;
  caveat: string;
  threshold: number;
  priorities: Cell[];
  options: Array<{ optionId: string; cells: Cell[] }>;
}

export interface OptionView {
  id: string;
  title: string;
  description: string;
  costs: string[];
  benefits: string[];
  openQuestions: string[];
}

/** A count, or what suppression means: never a number under the threshold. */
export function cellText(c: Cell, threshold: number): string {
  if (c.suppressed || c.count === undefined) return `fewer than ${threshold}, not shown`;
  return String(c.count);
}

export function windowText(w: Window): string {
  if (!w.from || !w.to) return "not shown while the sample is small";
  return w.from === w.to ? `on ${w.from}` : `from ${w.from} to ${w.to}`;
}

/** The sentence every figure is shown with: who it counts and when. */
export function basisText(a: Aggregate): string {
  return `${cellText(a.participants, a.threshold)} participants who chose to answer, ${windowText(a.window)}.`;
}

/** The priority cells highest first, as a reader reads a scale. */
export function scale(a: Aggregate): Cell[] {
  return [...a.priorities].sort((x, y) => Number(y.key) - Number(x.key));
}
