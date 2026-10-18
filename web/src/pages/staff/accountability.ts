/** What the representative's pages decide, apart from React (FR-038 to FR-043). */

export interface QueueItem {
  kind: string;
  id: string;
  issue?: string;
  title: string;
  state: string;
  since: string;
  ageSeconds: number;
  link: string;
}

export interface Labelled<A> {
  label: string;
  item: A;
}

export interface ResearchRun {
  run: string;
  issue: string;
  state: string;
  sources: Array<Labelled<{ name: string; why: string }>>;
  questions: Array<Labelled<{ statement: string; why: string }>>;
  investigations: Array<Labelled<{ question: string; reason: string; records: string[] }>>;
  recorded: string[];
}

export interface TaskView {
  id: string;
  text: string;
  owner: string;
  due: string;
  state: string;
}

export interface InvestigationView {
  id: string;
  issue: string;
  question: string;
  state: string;
  reason?: string;
  records: string[];
  tasks: TaskView[];
}

export interface ActionDraft {
  authority: string;
  owner: string;
  date: string;
  rationale: string;
  state: string;
  publicSummary: string;
  investigation?: string;
}

/** The wire values the service reads, with the words a person sees. */
export const ActionStates: ReadonlyArray<readonly [string, string]> = [
  ["proposed", "proposed, not started"],
  ["in-progress", "in progress"],
  ["completed", "completed"],
];

export const TaskStates: ReadonlyArray<readonly [string, string]> = [
  ["open", "open"],
  ["awaiting-external", "awaiting someone outside"],
  ["done", "done"],
];

/** How long an item has waited, in the largest whole unit. */
export function ageText(seconds: number): string {
  if (seconds < 60) return "just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} minute${minutes === 1 ? "" : "s"}`;
  const hours = Math.floor(minutes / 60);
  if (hours < 48) return `${hours} hour${hours === 1 ? "" : "s"}`;
  const days = Math.floor(hours / 24);
  return `${days} days`;
}

/** The oldest first: the queue is work, and work that has waited longest comes first. */
export function oldestFirst(items: QueueItem[]): QueueItem[] {
  return [...items].sort((a, b) => b.ageSeconds - a.ageSeconds);
}

/** The fields an action still needs before it can be recorded; every one is required (FR-041). */
export function missingFields(d: ActionDraft): string[] {
  const named: Array<[keyof ActionDraft, string]> = [
    ["authority", "the authority"],
    ["owner", "the owner"],
    ["date", "the date"],
    ["rationale", "the rationale"],
    ["state", "the state"],
    ["publicSummary", "the public summary"],
  ];
  return named.filter(([k]) => (d[k] ?? "").trim() === "").map(([, words]) => words);
}

/** The action body the service reads; only once nothing is missing. */
export function actionBody(d: ActionDraft): ActionDraft | undefined {
  if (missingFields(d).length > 0) return undefined;
  const body: ActionDraft = {
    authority: d.authority.trim(),
    owner: d.owner.trim(),
    date: d.date,
    rationale: d.rationale.trim(),
    state: d.state,
    publicSummary: d.publicSummary.trim(),
  };
  if (d.investigation) body.investigation = d.investigation;
  return body;
}

/** Every item of a run, each with the label the service gave it; nothing here is shown as decided. */
export function suggestions(run: ResearchRun): Array<{ label: string; kind: string; text: string; why: string }> {
  return [
    ...run.sources.map((s) => ({ label: s.label, kind: "source", text: s.item.name, why: s.item.why })),
    ...run.questions.map((q) => ({ label: q.label, kind: "question", text: q.item.statement, why: q.item.why })),
    ...run.investigations.map((i) => ({ label: i.label, kind: "investigation", text: i.item.question, why: i.item.reason })),
  ];
}

export function runDone(run: ResearchRun): boolean {
  return run.state === "done" || run.state === "failed";
}
