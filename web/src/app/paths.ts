/** Matches `/issues/:issue` against `/issues/iss-1`, answering the parameters or nothing. */
export function match(pattern: string, path: string): Record<string, string> | undefined {
  const want = pattern.split("/").filter(Boolean);
  const have = path.split("?")[0].split("/").filter(Boolean);
  if (want.length !== have.length) return undefined;
  const params: Record<string, string> = {};
  for (let i = 0; i < want.length; i++) {
    if (want[i].startsWith(":")) params[want[i].slice(1)] = decodeURIComponent(have[i]);
    else if (want[i] !== have[i]) return undefined;
  }
  return params;
}

/** The first route of `table` whose pattern matches. */
export function resolve<T>(table: ReadonlyArray<readonly [string, T]>, path: string): { route: T; params: Record<string, string> } | undefined {
  for (const [pattern, route] of table) {
    const params = match(pattern, path);
    if (params) return { route, params };
  }
  return undefined;
}

/** A path's query as values a page may read; a path parameter of the same name wins over them. */
export function withQuery(path: string, params: Record<string, string>): Record<string, string> {
  const query = path.includes("?") ? path.slice(path.indexOf("?") + 1) : "";
  return { ...Object.fromEntries(new URLSearchParams(query)), ...params };
}
