import { useEffect, useState } from "react";
import { route } from "./app/routes.tsx";

/** The interface: the page for the current path, re-rendered when the path changes. */
export function App() {
  const [path, setPath] = useState(location.pathname);
  useEffect(() => {
    const changed = () => setPath(location.pathname);
    addEventListener("popstate", changed);
    return () => removeEventListener("popstate", changed);
  }, []);
  return (
    <main>
      <h1>Voice</h1>
      {route(path === "/" ? "/me" : path)}
    </main>
  );
}
