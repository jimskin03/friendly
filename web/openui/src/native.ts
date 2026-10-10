import type { FriendlyAction } from "./types";

let activeSession: string | null = null;
let lastActionId = 0;

export function setNativeSession(sessionId: string | null): void {
  activeSession = sessionId;
  // IDs must continue increasing across web reloads within a native session.
  lastActionId = Math.max(lastActionId, Date.now() * 1000);
}

export function postNative(action: FriendlyAction): void {
  if (action.type === "ready") {
    window.FriendlyOpenUI?.postMessage(JSON.stringify(action));
    return;
  }
  if (!activeSession) return;
  lastActionId = Math.max(lastActionId + 1, Date.now() * 1000);
  window.FriendlyOpenUI?.postMessage(
    JSON.stringify({ ...action, sessionId: activeSession, actionId: lastActionId }),
  );
}