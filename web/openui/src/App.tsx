import { useEffect, useRef, useState } from "react";
import { postNative, setNativeSession } from "./native";
import { RichResponse, openUiProgram } from "./RichResponse";
import { Markdown, safeLinkTarget } from "./Markdown";
import { themeStyle } from "./theme";
import type { FriendlyMessage, FriendlySnapshot } from "./types";
import "@openuidev/react-ui/styles/index.css";
import "./styles.css";

export function Message({ message, loading, interactive = true }: { message: FriendlyMessage; loading: boolean; interactive?: boolean }) {
  const isUser = message.role === "user";
  const program = !isUser ? openUiProgram(message.text, message.openui) : null;

  return (
    <article className={`message ${isUser ? "message-user" : "message-assistant"}`} data-message-id={message.id}>
      <div className="message-label">{isUser ? "You" : message.role === "assistant" ? "Friendly" : message.role}</div>
      <div className="message-body">
        {program ? (
          <RichResponse text={message.text} program={program} loading={loading} interactive={interactive} />
        ) : isUser ? (
          <p>{message.text}</p>
        ) : message.text ? (
          <Markdown text={message.text} />
        ) : (
          <p className="thinking">{loading ? "Thinking…" : ""}</p>
        )}
      </div>
      {(message.canEdit || !isUser) && (
        <div className="message-actions" aria-label="Message actions">
          {message.canRegenerate && (
            <button type="button" onClick={() => postNative({ type: "regenerate", messageId: message.id })}>
              Regenerate
            </button>
          )}
          {message.canEdit && (
            <button type="button" onClick={() => postNative({ type: "edit", messageId: message.id })}>
              Edit
            </button>
          )}
          {message.canReport && (
            <button type="button" onClick={() => postNative({ type: "report", messageId: message.id })}>
              Report
            </button>
          )}
          {message.branchCount && message.branchCount > 1 ? (
            <span className="branch-count">{(message.branchIndex ?? 0) + 1}/{message.branchCount}</span>
          ) : null}
        </div>
      )}
    </article>
  );
}

export default function App() {
  const [snapshot, setSnapshot] = useState<FriendlySnapshot | null>(null);
  const [draft, setDraft] = useState("");
  const endRef = useRef<HTMLDivElement>(null);
  const followOutput = useRef(true);
  const currentSession = useRef<string | null>(null);

  useEffect(() => {
    window.friendlyOpenUI = {
      pushSnapshot(payload: string) {
        const parsed = JSON.parse(payload) as FriendlySnapshot;
        if (parsed.protocolVersion !== 1) return;
        if (currentSession.current !== parsed.sessionId) {
          currentSession.current = parsed.sessionId;
          setDraft(parsed.draft);
        }
        setNativeSession(parsed.sessionId);
        setSnapshot(current => {
          if (current?.sessionId === parsed.sessionId && parsed.revision < current.revision) return current;
          return parsed;
        });
      },
      retire() {
        setNativeSession(null);
        currentSession.current = null;
        setSnapshot(null);
        setDraft("");
      }
    };
    postNative({ type: "ready" });
    return () => {
      setNativeSession(null);
      delete window.friendlyOpenUI;
    };
  }, []);

  useEffect(() => {
    if (!snapshot || snapshot.messages.length === 0 || !followOutput.current) return;
    endRef.current?.scrollIntoView({ behavior: snapshot.loading ? "auto" : "smooth", block: "end" });
  }, [snapshot?.revision, snapshot?.loading, snapshot?.messages.length]);

  const submit = () => {
    const text = draft.trim();
    if (!text || snapshot?.loading || !snapshot?.modelAvailable) return;
    postNative({ type: "send", text });
    setDraft("");
  };

  if (!snapshot) {
    return <main className="loading-shell" aria-live="polite">Loading Friendly…</main>;
  }

  return (
    <main className={snapshot.darkMode ? "app dark" : "app"} style={themeStyle(snapshot.theme)}
      onClickCapture={event => {
        const link = (event.target as Element).closest("a");
        if (!link) return;
        // OpenUI's own Markdown and source links must also leave through native.
        event.preventDefault();
        event.stopPropagation();
        const url = safeLinkTarget(link.getAttribute("href") ?? undefined);
        if (url) postNative({ type: "link", url });
      }}>
      <section className="conversation" aria-live="polite" aria-busy={snapshot.loading}
        onScroll={event => {
          const view = event.currentTarget;
          followOutput.current = view.scrollHeight - view.scrollTop - view.clientHeight < 100;
        }}>
        {snapshot.messages.length === 0 ? (
          <div className="welcome">
            <div className="cat">🐈</div>
            <h2>How can I help?</h2>
            <p>Friendly can chat, work with your files, and use the tools enabled for this edition.</p>
          </div>
        ) : snapshot.messages.map((message, index) => (
          <Message key={message.id} message={message} loading={snapshot.loading && index === snapshot.messages.length - 1} interactive={!snapshot.loading && snapshot.modelAvailable} />
        ))}
        {snapshot.processingStatus && <div className="processing">{snapshot.processingStatus}</div>}
        <div ref={endRef} />
      </section>

      {snapshot.suggestions.length > 0 && (
        <nav className="suggestions" aria-label="Suggestions">
          {snapshot.suggestions.map(text => (
            <button key={text} type="button" disabled={snapshot.loading || !snapshot.modelAvailable} onClick={() => postNative({ type: "suggestion", text })}>{text}</button>
          ))}
        </nav>
      )}

      <footer className="composer">
        <textarea
          value={draft}
          onChange={event => {
            const next = event.target.value.slice(0, 16000);
            setDraft(next);
            postNative({ type: "draft", text: next });
          }}
          onKeyDown={event => {
            if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
              event.preventDefault();
              submit();
            }
          }}
          placeholder={snapshot.modelAvailable ? "Ask anything…" : "Select a model in native settings"}
          aria-label="Message Friendly" rows={1}
        />
        <div className="composer-actions">
        <div className="composer-tools">
        <button type="button" className="composer-tool" onClick={() => postNative({ type: "attachments" })} aria-label="Open native attachment picker">
          +{snapshot.attachmentCount > 0 ? snapshot.attachmentCount : ""}
        </button>
        <button type="button" className="composer-tool" onClick={() => postNative({ type: "voice" })} aria-label="Start native voice session">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3M8 22h8"/></svg>
        </button>
        <button type="button" className="native-switch" onClick={() => postNative({ type: "native" })} aria-label="Use native chat">Native chat</button>
        </div>
        {snapshot.loading ? <button type="button" className="send stop" onClick={() => postNative({ type: "stop" })} aria-label="Stop generation">■</button> : (
        <button type="button" className="send" disabled={!draft.trim() || snapshot.loading || !snapshot.modelAvailable} onClick={submit} aria-label="Send message">↑</button>)}
        </div>
      </footer>
    </main>
  );
}
