import { useMemo, useState } from "react";
import type { FriendlyMessage } from "./types";

export function outlineEntries(messages: FriendlyMessage[], query: string) {
  const q = query.trim().toLowerCase();
  return messages
    .map((m, index) => ({ id: m.id, role: m.role, index, text: m.text.replace(/\s+/g, " ").trim() }))
    .filter(e => (e.role === "user" || e.role === "assistant") && (e.text || !q))
    .filter(e => !q || e.text.toLowerCase().includes(q))
    .map(e => {
      if (!q) return { ...e, preview: e.text.slice(0, 140) };
      const at = e.text.toLowerCase().indexOf(q);
      const start = Math.max(0, at - 40);
      return { ...e, preview: (start > 0 ? "…" : "") + e.text.slice(start, at + q.length + 90) };
    });
}

export function OutlineSheet({ messages, onJump, onClose, onExport }: {
  messages: FriendlyMessage[]; onJump: (id: string) => void; onClose: () => void; onExport: () => void;
}) {
  const [query, setQuery] = useState("");
  const entries = useMemo(() => outlineEntries(messages, query), [messages, query]);
  return (
    <>
      <div className="menu-scrim sheet-scrim" onClick={onClose} />
      <div className="sheet sheet-tall" role="dialog" aria-label="Conversation outline">
        <div className="sheet-head"><strong>Outline</strong>
          <button type="button" className="text-btn" onClick={onExport}>Export</button>
          <button type="button" className="text-btn" onClick={onClose}>Done</button></div>
        <input className="sheet-search" value={query} onChange={e => setQuery(e.target.value.slice(0, 200))} placeholder="Search this chat" aria-label="Search this chat" autoFocus />
        <ol className="outline-list">
          {entries.map(e => (
            <li key={e.id}>
              <button type="button" onClick={() => onJump(e.id)} className={`outline-item outline-${e.role}`}>
                <span className="outline-role">{e.role === "user" ? "You" : "Friendly"}</span>
                <span className="outline-text">{e.preview || "(no text)"}</span>
              </button>
            </li>
          ))}
          {entries.length === 0 && <li className="picker-empty">No messages match.</li>}
        </ol>
      </div>
    </>
  );
}
