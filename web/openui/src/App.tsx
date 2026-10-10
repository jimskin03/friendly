import { Renderer } from "@openuidev/react-lang";
import { openuiChatLibrary } from "@openuidev/react-ui/genui-lib";
import { useEffect, useMemo, useRef, useState } from "react";
import { postNative, setNativeSession } from "./native";
import { Markdown } from "./Markdown";
import { themeStyle } from "./theme";
import type { FriendlyMessage, FriendlySnapshot } from "./types";
import "@openuidev/react-ui/styles/index.css";
import "./styles.css";

function Message({ message, loading }: { message: FriendlyMessage; loading: boolean }) {
  const isUser = message.role === "user";
  const hasOpenUI = !isUser && Boolean(message.openui?.trim());

  return (
    <article className={`message ${isUser ? "message-user" : "message-assistant"}`} data-message-id={message.id}>
      <div className="message-label">{isUser ? "You" : message.role === "assistant" ? "Friendly" : message.role}</div>
      <div className="message-body">
        {hasOpenUI ? (
          <Renderer
            response={message.openui ?? null}
            library={openuiChatLibrary}
            isStreaming={loading}
            toolProvider={null}
            publishObservability={false}
          />
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
              Edit in native
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
    endRef.current?.scrollIntoView({ behavior: snapshot?.loading ? "auto" : "smooth", block: "end" });
  }, [snapshot?.revision, snapshot?.loading]);

  const submit = () => {
    const text = draft.trim();
    if (!text || snapshot?.loading || !snapshot?.modelAvailable) return;
    postNative({ type: "send", text });
    setDraft("");
  };

  const editionLabel = useMemo(() => snapshot?.capabilities.edition === "play" ? "Play" : "Nightly", [snapshot]);

  if (!snapshot) {
    return <main className="loading-shell" aria-live="polite">Loading Friendly…</main>;
  }

  return (
    <main className={snapshot.darkMode ? "app dark" : "app"} style={themeStyle(snapshot.theme)}>
      <header className="chat-header">
        <div>
          <h1>{snapshot.title || "New chat"}</h1>
          <span>{editionLabel} · OpenUI</span>
        </div>
        <div className="header-actions">
          <button type="button" className="native-switch" onClick={() => postNative({ type: "native" })} aria-label="Use native chat">
            Native
          </button>
          {snapshot.loading && <button type="button" className="stop" onClick={() => postNative({ type: "stop" })}>Stop</button>}
        </div>
      </header>

      <section className="conversation" aria-live="polite" aria-busy={snapshot.loading}>
        {snapshot.messages.length === 0 ? (
          <div className="welcome">
            <div className="cat">🐈</div>
            <h2>How can I help?</h2>
            <p>Friendly can chat, work with your files, and use the tools enabled for this edition.</p>
          </div>
        ) : snapshot.messages.map((message, index) => (
          <Message key={message.id} message={message} loading={snapshot.loading && index === snapshot.messages.length - 1} />
        ))}
        {snapshot.processingStatus && <div className="processing">{snapshot.processingStatus}</div>}
        <div ref={endRef} />
      </section>

      {snapshot.suggestions.length > 0 && (
        <nav className="suggestions" aria-label="Suggestions">
          {snapshot.suggestions.map(text => (
            <button key={text} type="button" onClick={() => postNative({ type: "suggestion", text })}>{text}</button>
          ))}
        </nav>
      )}

      <footer className="composer">
        <button type="button" className="composer-tool" onClick={() => postNative({ type: "attachments" })} aria-label="Open native attachment picker">
          +{snapshot.attachmentCount > 0 ? snapshot.attachmentCount : ""}
        </button>
        <button type="button" className="composer-tool" onClick={() => postNative({ type: "voice" })} aria-label="Start native voice session">
          🎤
        </button>
        <textarea
          value={draft}
          onChange={event => {
            const next = event.target.value.slice(0, 16000);
            setDraft(next);
            postNative({ type: "draft", text: next });
          }}
          onKeyDown={event => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              submit();
            }
          }}
          placeholder={snapshot.modelAvailable ? "Message Friendly" : "Select a model in native settings"}
          aria-label="Message Friendly"
          rows={1}
        />
        <button type="button" className="send" disabled={!draft.trim() || snapshot.loading || !snapshot.modelAvailable} onClick={submit} aria-label="Send message">↑</button>
      </footer>
    </main>
  );
}