import type { ReactNode } from "react";
import { resolve, withQuery } from "./paths.ts";
import { Start } from "../pages/citizen/Start.tsx";
import { Recover } from "../pages/citizen/Recover.tsx";
import { Conversation } from "../pages/citizen/Conversation.tsx";
import { Conversations } from "../pages/citizen/Conversations.tsx";
import { Share } from "../pages/citizen/Share.tsx";
import { Contribution } from "../pages/citizen/Contribution.tsx";
import { Reviews } from "../pages/staff/Reviews.tsx";
import { IssueDashboard } from "../pages/staff/IssueDashboard.tsx";
import { Inbox } from "../pages/citizen/Inbox.tsx";
import { DataRights } from "../pages/citizen/DataRights.tsx";
import { Erasures } from "../pages/staff/Erasures.tsx";
import { Express } from "../pages/citizen/Express.tsx";
import { SignIn } from "../pages/staff/SignIn.tsx";
import { Evidence as StaffEvidence } from "../pages/staff/Evidence.tsx";
import { Queue } from "../pages/staff/Queue.tsx";
import { Research } from "../pages/staff/Research.tsx";
import { Investigation } from "../pages/staff/Investigation.tsx";
import { Action } from "../pages/staff/Action.tsx";
import { Issue } from "../pages/public/Issue.tsx";
import { Evidence } from "../pages/public/Evidence.tsx";
import { Claim } from "../pages/public/Claim.tsx";
import { Search } from "../pages/public/Search.tsx";
import { Source } from "../pages/public/Source.tsx";

/** The three route trees, one per audience, each served by its own endpoint (R13). */
export type Page = (params: Record<string, string>) => ReactNode;

export const citizenRoutes: Array<readonly [string, Page]> = [
  ["/me", () => <Start />],
  ["/me/recover", () => <Recover />],
  ["/me/conversations", () => <Conversations />],
  ["/me/conversations/:conversation", (p) => <Conversation id={p.conversation} />],
  ["/me/concerns/:concern/share", (p) => <Share concern={p.concern} />],
  ["/me/contributions/:contribution", (p) => <Contribution id={p.contribution} />],
  ["/me/issues/:issue/express", (p) => <Express issue={p.issue} />],
  ["/me/inbox", () => <Inbox />],
  ["/me/data", () => <DataRights />],
];
export const publicRoutes: Array<readonly [string, Page]> = [
  ["/", () => <Search />],
  ["/search", () => <Search />],
  ["/issues/:issue", (p) => <Issue id={p.issue} />],
  ["/issues/:issue/evidence/:evidence", (p) => <Evidence issue={p.issue} id={p.evidence} />],
  ["/issues/:issue/claims/:claim", (p) => <Claim issue={p.issue} id={p.claim} />],
  ["/sources/:source", (p) => <Source id={p.source} />],
];
export const staffRoutes: Array<readonly [string, Page]> = [
  ["/staff", () => <SignIn />],
  ["/staff/reviews", () => <Reviews />],
  ["/staff/issues/:issue", (p) => <IssueDashboard issue={p.issue} />],
  ["/staff/issues/:issue/evidence", (p) => <StaffEvidence issue={p.issue} />],
  ["/staff/queue", () => <Queue />],
  ["/staff/erasures", () => <Erasures />],
  ["/staff/issues/:issue/research", (p) => <Research issue={p.issue} />],
  ["/staff/investigations/:investigation", (p) => <Investigation id={p.investigation} />],
  ["/staff/issues/:issue/actions", (p) => <Action issue={p.issue} investigation={p.investigation} />],
];

export function route(path: string): ReactNode {
  const tree = path.startsWith("/staff") ? staffRoutes : path.startsWith("/me") ? citizenRoutes : publicRoutes;
  const found = resolve(tree, path);
  return found ? found.route(withQuery(path, found.params)) : <p>There is no such page.</p>;
}
