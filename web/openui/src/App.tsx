import { useEffect, useRef, useState } from "react";
import { postNative, setNativeSession } from "./native";
import { safeLinkTarget } from "./Markdown";
import { themeStyle } from "./theme";
import { AttachmentChip, MessageView } from "./MessageView";
import { AssetsContext, parseAssets } from "./assets";
import { PickerSheet, reasoningLabel, searchLabel, type PickerKind } from "./Pickers";
import { OutlineSheet } from "./Outline";
import { IconBrain, IconClose, IconDown, IconEdit, IconGlobe, IconMic, IconMonitor, IconPhone, IconPlug, IconPlus, IconSpark, IconStopCircle, IconUp, IconUser } from "./icons";
import type { FriendlyChatState, FriendlySnapshot } from "./types";
import "@openuidev/react-ui/styles/index.css";
import "./styles.css";

const EMPTY_CHAT: FriendlyChatState = {
  pendingAttachments: [], voice: "off", voiceTranscript: "", ttsAvailable: true, showStats: true,
  queue: [], errors: [], desktopAvailable: true, phoneAvailable: false, desktopStreaming: false,
};

const VOICE_LABEL: Record<string, string> = {
  connecting: "Connecting…", listening: "Listening…", transcribing: "Transcribing…", speaking: "Speaking…", error: "Voice error",
};

function VoicePanel({ chat }: { chat: FriendlyChatState }) {
  return (
    <div className={`voice-panel voice-${chat.voice}`} role="status">
      <span className="voice-orb" aria-hidden="true"><span /><span /><span /></span>
      <div className="voice-text">
        <strong>{VOICE_LABEL[chat.voice] ?? "Voice"}</strong>
        {chat.voiceTranscript && <span>{chat.voiceTranscript}</span>}
      </div>
      {chat.voice === "speaking" && <button type="button" className="btn-secondary" onClick={() => postNative({ type: "voiceInterrupt" })}>Interrupt</button>}
      <button type="button" className="btn-primary" onClick={() => postNative({ type: "voiceStop" })}>End</button>
    </div>
  );
}

export default function App() {
  const [snapshot, setSnapshot] = useState<FriendlySnapshot | null>(null);
  const [draft, setDraft] = useState("");
  const [atBottom, setAtBottom] = useState(true);
  const [toolsOpen, setToolsOpen] = useState(false);
  const [assets, setAssets] = useState<Record<string, string>>({});
  const [picker, setPicker] = useState<PickerKind | null>(null);
  const [outlineOpen, setOutlineOpen] = useState(false);
  const [queueEdit, setQueueEdit] = useState<{ id: string; text: string } | null>(null);
  const outlineSeen = useRef<number | null>(null);
  const longPress = useRef<{ timer: number | null; fired: boolean }>({ timer: null, fired: false });
  const scrollRef = useRef<HTMLElement>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const followOutput = useRef(true);
  const currentSession = useRef<string | null>(null);
  const lastEditing = useRef<string | null>(null);
  const focused = useRef<string | null>(null);

  useEffect(() => {
    window.friendlyOpenUI = {
      pushSnapshot(payload: string) {
        const parsed = JSON.parse(payload) as FriendlySnapshot;
        if (parsed.protocolVersion !== 1) return;
        const editing = parsed.chat?.editingMessageId ?? null;
        if (currentSession.current !== parsed.sessionId || editing !== lastEditing.current) {
          // New session, or native entered/left edit mode: the native draft is canonical.
          currentSession.current = parsed.sessionId;
          setDraft(parsed.draft);
        }
        lastEditing.current = editing;
        setNativeSession(parsed.sessionId);
        setSnapshot(current => {
          if (current?.sessionId === parsed.sessionId && parsed.revision < current.revision) return current;
          return parsed;
        });
      },
      pushAssets(payload: string) {
        const incoming = parseAssets(payload);
        setAssets(current => ({ ...current, ...incoming }));
      },
      retire() {
        setNativeSession(null);
        currentSession.current = null;
        setSnapshot(null);
        setDraft("");
        setAssets({});
      },
    };
    postNative({ type: "ready" });
    return () => {
      setNativeSession(null);
      delete window.friendlyOpenUI;
    };
  }, []);

  useEffect(() => {
    const target = snapshot?.chat?.focusMessageId;
    if (!target || focused.current === target) return;
    const element = document.querySelector(`[data-message-id="${CSS.escape(target)}"]`);
    if (!element) return;
    focused.current = target;
    followOutput.current = false;
    element.scrollIntoView({ block: "start" });
    element.classList.add("is-focused");
  }, [snapshot?.revision]);

  useEffect(() => {
    const request = snapshot?.chat?.outlineRequest ?? 0;
    if (outlineSeen.current !== null && request > outlineSeen.current) setOutlineOpen(true);
    outlineSeen.current = request;
  }, [snapshot?.chat?.outlineRequest]);

  useEffect(() => {
    if (!snapshot || snapshot.messages.length === 0 || !followOutput.current) return;
    endRef.current?.scrollIntoView({ behavior: snapshot.loading ? "auto" : "smooth", block: "end" });
  }, [snapshot?.revision, snapshot?.loading, snapshot?.messages.length]);

  if (!snapshot) return <main className="loading-shell" aria-live="polite">Loading Friendly…</main>;

  const chat = snapshot.chat ?? EMPTY_CHAT;
  const busy = snapshot.loading;
  const canSend = Boolean(draft.trim()) && !busy && snapshot.modelAvailable;
  const editing = Boolean(chat.editingMessageId);
  const voiceActive = chat.voice !== "off" && chat.voice !== "error";

  const submit = (answer = true) => {
    const text = draft.trim();
    if (!canSend) return;
    postNative(answer ? { type: "send", text } : { type: "send", text, answer: false });
    setDraft("");
  };
  const pickers = chat.pickers;

  const jumpTo = (id: string) => {
    setOutlineOpen(false);
    const element = document.querySelector(`[data-message-id="${CSS.escape(id)}"]`);
    if (!element) return;
    followOutput.current = false;
    element.scrollIntoView({ block: "start", behavior: "smooth" });
    element.classList.remove("is-focused");
    void (element as HTMLElement).offsetWidth;
    element.classList.add("is-focused");
  };

  const sendPressStart = () => {
    longPress.current.fired = false;
    if (editing || !canSend) return;
    longPress.current.timer = window.setTimeout(() => {
      longPress.current.fired = true;
      submit(false); // long-press: add to the chat without asking for a reply
    }, 550);
  };
  const sendPressEnd = () => {
    if (longPress.current.timer !== null) window.clearTimeout(longPress.current.timer);
    longPress.current.timer = null;
  };

  return (
    <AssetsContext.Provider value={assets}>
    <main className={snapshot.darkMode ? "app dark" : "app"} style={themeStyle(snapshot.theme)}
      onClickCapture={event => {
        const link = (event.target as Element).closest("a");
        if (!link) return;
        // Every link (ours and OpenUI's) leaves through native, which only opens http(s)/mailto.
        event.preventDefault();
        event.stopPropagation();
        const url = safeLinkTarget(link.getAttribute("href") ?? undefined);
        if (url) postNative({ type: "link", url });
      }}>
      <section ref={scrollRef} className="conversation" aria-live="polite" aria-busy={busy}
        onScroll={event => {
          const view = event.currentTarget;
          const bottom = view.scrollHeight - view.scrollTop - view.clientHeight < 100;
          followOutput.current = bottom;
          setAtBottom(bottom);
        }}>
        {snapshot.messages.length === 0 ? (
          <div className="welcome">
            <div className="cat">🐈</div>
            <h2>{chat.folderName ? chat.folderName : "How can I help?"}</h2>
            <p>{chat.folderName ? "Chats here are saved to this folder." : "Friendly can chat, work with your files, and use the tools enabled for this edition."}</p>
          </div>
        ) : snapshot.messages.map((message, index) => (
          <MessageView key={message.id} message={message} chat={chat} busy={busy}
            streaming={busy && index === snapshot.messages.length - 1 && message.role === "assistant"}
            interactive={!busy && snapshot.modelAvailable} />
        ))}
        {snapshot.processingStatus && <div className="processing">{snapshot.processingStatus}</div>}
        {chat.errors.length > 0 && (
          <div className="errors" role="alert">
            {chat.errors.map(error => (
              <div key={error.id} className="error-card">
                <div><strong>{error.title || "Something went wrong"}</strong><span>{error.message}</span>
                  <span className="error-solutions">
                    {error.solution === "fastModelSettings" && <button type="button" className="btn-secondary" onClick={() => postNative({ type: "errorSolution", id: error.id, solution: "fastModelSettings" })}>Check fast model settings</button>}
                    {!busy && <button type="button" className="btn-secondary" onClick={() => { postNative({ type: "dismissError", id: error.id }); postNative({ type: "retry" }); }}>Retry</button>}
                  </span>
                </div>
                <button type="button" className="icon-btn" aria-label="Dismiss error" onClick={() => postNative({ type: "dismissError", id: error.id })}><IconClose size={16} /></button>
              </div>
            ))}
            {chat.errors.length > 1 && <button type="button" className="text-btn" onClick={() => postNative({ type: "clearErrors" })}>Clear all</button>}
          </div>
        )}
        <div ref={endRef} />
      </section>

      {!atBottom && snapshot.messages.length > 0 && (
        <button type="button" className="scroll-bottom" aria-label="Scroll to bottom" onClick={() => {
          followOutput.current = true;
          endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
        }}><IconDown /></button>
      )}

      {snapshot.suggestions.length > 0 && !editing && (
        <nav className="suggestions" aria-label="Suggestions">
          {snapshot.suggestions.map(text => (
            <button key={text} type="button" disabled={busy || !snapshot.modelAvailable} onClick={() => postNative({ type: "suggestion", text })}>{text}</button>
          ))}
        </nav>
      )}

      <footer className="composer-wrap">
        {chat.desktopStreaming && (
          <div className="live-banner" role="status">
            <span className="live-dot" aria-hidden="true" /><span className="live-text">Desktop live</span>
            <button type="button" className="text-btn" onClick={() => postNative({ type: "desktop" })}>Open</button>
            <button type="button" className="btn-secondary" onClick={() => postNative({ type: "desktopSnap" })}>Snap to chat</button>
            <button type="button" className="btn-secondary" onClick={() => postNative({ type: "desktopStop" })}>Stop</button>
          </div>
        )}
        {voiceActive || chat.voice === "error" ? <VoicePanel chat={chat} /> : null}
        {chat.queue.length > 0 && (
          <div className="queue" aria-label="Queued messages">
            {chat.queue.map(q => queueEdit?.id === q.id ? (
              <form key={q.id} className="queue-edit" onSubmit={event => {
                event.preventDefault();
                postNative({ type: "editQueued", id: q.id, text: queueEdit.text });
                setQueueEdit(null);
              }}>
                <textarea value={queueEdit.text} rows={2} aria-label="Edit queued message" autoFocus
                  onChange={e => setQueueEdit({ id: q.id, text: e.target.value.slice(0, 16000) })} />
                <button type="button" className="text-btn" onClick={() => { postNative({ type: "editQueued", id: q.id }); setQueueEdit(null); }}>Cancel</button>
                <button type="submit" className="btn-primary" disabled={!queueEdit.text.trim()}>Save</button>
              </form>
            ) : (
              <span key={q.id} className="queue-item"><span>{q.text || "Queued message"}</span>
                <button type="button" className="chip-remove" aria-label="Edit queued message" onClick={() => {
                  postNative({ type: "beginEditQueued", id: q.id });
                  setQueueEdit({ id: q.id, text: q.text });
                }}><IconEdit size={14} /></button>
                <button type="button" className="chip-remove" aria-label="Remove queued message" onClick={() => postNative({ type: "removeQueued", id: q.id })}>×</button></span>
            ))}
            {!busy && <button type="button" className="text-btn" onClick={() => postNative({ type: "resumeQueue" })}>Send queued</button>}
          </div>
        )}
        <div className={`composer${editing ? " composer-editing" : ""}`}>
          {editing && (
            <div className="edit-banner">
              <span>Editing message</span>
              <button type="button" className="text-btn" onClick={() => { postNative({ type: "cancelEdit" }); setDraft(""); }}>Cancel</button>
            </div>
          )}
          {chat.pendingAttachments.length > 0 && (
            <div className="pending-attachments">
              {chat.pendingAttachments.map((item, index) => (
                <AttachmentChip key={`${item.name}-${index}`} item={item} onRemove={() => postNative({ type: "removeAttachment", index })} />
              ))}
            </div>
          )}
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
            placeholder={!snapshot.modelAvailable ? "Select a model to start" : editing ? "Edit your message…" : "Ask anything…"}
            aria-label="Message Friendly" rows={1}
          />
          <div className="composer-actions">
            <div className="composer-tools">
              <button type="button" className="icon-btn tool" onClick={() => postNative({ type: "attachments" })} aria-label="Add attachments"><IconPlus /></button>
              <button type="button" className="model-chip" onClick={() => pickers ? setPicker("model") : postNative({ type: "modelPicker" })} aria-label="Choose model">
                <IconSpark size={15} /><span>{chat.modelName ?? "Choose model"}</span>
              </button>
              {pickers && pickers.assistants.length > 1 && (
                <button type="button" className="icon-btn tool" onClick={() => setPicker("assistant")} aria-label={`Assistant: ${chat.assistantName ?? ""}`}><IconUser /></button>
              )}
              {pickers?.reasoning != null && (
                <button type="button" className={`icon-btn tool${pickers.reasoning !== "off" ? " is-on" : ""}`} onClick={() => setPicker("reasoning")} aria-label={`Reasoning: ${reasoningLabel(pickers.reasoning)}`}><IconBrain /></button>
              )}
              {pickers && (
                <button type="button" className={`icon-btn tool${pickers.searchMode !== "off" ? " is-on" : ""}`} onClick={() => setPicker("search")} aria-label={`Web search: ${searchLabel(pickers.searchMode)}`}><IconGlobe /></button>
              )}
              {pickers && pickers.mcp.length > 0 && (
                <button type="button" className={`icon-btn tool${pickers.mcp.some(m => m.enabled) ? " is-on" : ""}`} onClick={() => setPicker("mcp")} aria-label="MCP servers"><IconPlug /></button>
              )}
              <span className="menu-anchor">
                <button type="button" className={`icon-btn tool${chat.desktopStreaming ? " is-live" : ""}`} aria-label="Device control" aria-haspopup="menu" aria-expanded={toolsOpen}
                  onClick={() => setToolsOpen(v => !v)}><IconMonitor /></button>
                {toolsOpen && (
                  <>
                    <div className="menu-scrim" onClick={() => setToolsOpen(false)} />
                    <div className="menu menu-up" role="menu">
                      {chat.desktopAvailable && <button type="button" role="menuitem" onClick={() => { setToolsOpen(false); postNative({ type: "desktop" }); }}><IconMonitor /><span>Desktop control{chat.desktopStreaming ? " · live" : ""}</span></button>}
                      {chat.phoneAvailable && <button type="button" role="menuitem" onClick={() => { setToolsOpen(false); postNative({ type: "phone" }); }}><IconPhone /><span>Phone automation</span></button>}
                    </div>
                  </>
                )}
              </span>
            </div>
            {busy ? (
              <button type="button" className="send stop" onClick={() => postNative({ type: "stop" })} aria-label="Stop generation"><IconStopCircle size={22} /></button>
            ) : draft.trim() || editing ? (
              <button type="button" className="send" disabled={!canSend}
                onClick={() => { if (!longPress.current.fired) submit(); longPress.current.fired = false; }}
                onPointerDown={sendPressStart} onPointerUp={sendPressEnd} onPointerLeave={sendPressEnd} onContextMenu={e => e.preventDefault()}
                aria-label={editing ? "Save and resend" : "Send message (hold to add without reply)"} title="Hold to send without reply"><IconUp size={20} /></button>
            ) : (
              <button type="button" className={`send voice${voiceActive ? " is-live" : ""}`} onClick={() => postNative({ type: voiceActive ? "voiceStop" : "voice" })}
                aria-label={voiceActive ? "Stop voice" : "Start voice"}><IconMic size={20} /></button>
            )}
          </div>
        </div>
      </footer>
      {picker && pickers && <PickerSheet kind={picker} pickers={pickers} onClose={() => setPicker(null)} />}
      {outlineOpen && <OutlineSheet messages={snapshot.messages} onJump={jumpTo} onClose={() => setOutlineOpen(false)}
        onExport={() => { setOutlineOpen(false); postNative({ type: "export" }); }} />}
    </main>
    </AssetsContext.Provider>
  );
}
