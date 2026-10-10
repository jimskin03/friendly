import { useState, type ReactNode } from "react";
import { postNative } from "./native";
import { RichResponse, openUiProgram } from "./RichResponse";
import { Markdown, safeLinkTarget } from "./Markdown";
import { formatSeconds, formatTokens } from "./format";
import type { FriendlyAttachment, FriendlyChatState, FriendlyMessage, FriendlyTool } from "./types";
import {
  IconBolt, IconChevronLeft, IconChevronRight, IconClock, IconCopy, IconDown, IconEdit, IconFile, IconFork, IconImage,
  IconMore, IconRegenerate, IconShare, IconSpeaker, IconStar, IconStopCircle, IconTool, IconTrash, IconUp,
} from "./icons";

export function AttachmentChip({ item, onRemove }: { item: FriendlyAttachment; onRemove?: () => void }) {
  return (
    <span className="attachment-chip">
      {item.kind === "image" ? <IconImage size={16} /> : <IconFile size={16} />}
      <span className="attachment-name">{item.name || item.kind}</span>
      {onRemove && <button type="button" className="chip-remove" onClick={onRemove} aria-label={`Remove ${item.name}`}>×</button>}
    </span>
  );
}

function Reasoning({ text, ms, streaming }: { text: string; ms?: number | null; streaming: boolean }) {
  return (
    <details className="reasoning" open={streaming}>
      <summary>{streaming ? "Thinking…" : ms ? `Thought for ${formatSeconds(ms)}` : "Reasoning"}</summary>
      <div className="reasoning-body"><Markdown text={text} /></div>
    </details>
  );
}

const ASK_USER = "ask_user";

function ToolCard({ tool, interactive }: { tool: FriendlyTool; interactive: boolean }) {
  const [answer, setAnswer] = useState("");
  const pending = tool.state === "pending" && !tool.executed;
  const status = tool.executed ? "Done" : pending ? "Needs approval" : tool.state === "denied" ? "Denied" : "Running…";
  return (
    <div className={`tool-card${pending ? " tool-pending" : ""}`}>
      <details open={pending}>
        <summary>
          <IconTool size={16} />
          <span className="tool-name">{tool.name}</span>
          <span className={`tool-status status-${pending ? "pending" : tool.executed ? "done" : tool.state}`}>{status}</span>
        </summary>
        {tool.input && <pre className="tool-io"><code>{tool.input}</code></pre>}
        {tool.output && <pre className="tool-io tool-output"><code>{tool.output}</code></pre>}
      </details>
      {pending && interactive && (tool.name === ASK_USER ? (
        <form className="tool-answer" onSubmit={event => {
          event.preventDefault();
          if (answer.trim()) postNative({ type: "toolAnswer", toolCallId: tool.id, text: answer.trim() });
        }}>
          <input value={answer} onChange={e => setAnswer(e.target.value.slice(0, 4000))} placeholder="Your answer" aria-label="Answer" />
          <button type="submit" className="btn-primary" disabled={!answer.trim()}>Reply</button>
        </form>
      ) : (
        <div className="tool-actions">
          <button type="button" className="btn-secondary" onClick={() => postNative({ type: "toolApproval", toolCallId: tool.id, approved: false })}>Deny</button>
          <button type="button" className="btn-primary" onClick={() => postNative({ type: "toolApproval", toolCallId: tool.id, approved: true })}>Approve</button>
        </div>
      ))}
    </div>
  );
}

function BranchSwitcher({ message, disabled }: { message: FriendlyMessage; disabled: boolean }) {
  const count = message.branchCount ?? 0;
  if (count < 2) return null;
  const index = message.branchIndex ?? 0;
  return (
    <span className="branch-switcher">
      <button type="button" className="icon-btn" disabled={disabled || index <= 0} aria-label="Previous version"
        onClick={() => postNative({ type: "branch", messageId: message.id, delta: -1 })}><IconChevronLeft size={16} /></button>
      <span className="branch-count">{index + 1}/{count}</span>
      <button type="button" className="icon-btn" disabled={disabled || index >= count - 1} aria-label="Next version"
        onClick={() => postNative({ type: "branch", messageId: message.id, delta: 1 })}><IconChevronRight size={16} /></button>
    </span>
  );
}

function MoreMenu({ message, onClose, canDelete }: { message: FriendlyMessage; onClose: () => void; canDelete: boolean }) {
  const item = (label: string, icon: ReactNode, action: () => void, danger = false) => (
    <button type="button" role="menuitem" className={danger ? "danger" : undefined} onClick={() => { action(); onClose(); }}>{icon}<span>{label}</span></button>
  );
  return (
    <>
      <div className="menu-scrim" onClick={onClose} />
      <div className="menu" role="menu">
        {message.canEdit && item("Edit", <IconEdit />, () => postNative({ type: "edit", messageId: message.id }))}
        {item("Share", <IconShare />, () => postNative({ type: "share", messageId: message.id }))}
        {item("Fork into new chat", <IconFork />, () => postNative({ type: "fork", messageId: message.id }))}
        {item(message.favorite ? "Remove favorite" : "Favorite", <IconStar />, () => postNative({ type: "favorite", messageId: message.id }))}
        {canDelete && item("Delete", <IconTrash />, () => postNative({ type: "delete", messageId: message.id }), true)}
      </div>
    </>
  );
}

export function StatsLine({ stats }: { stats: NonNullable<FriendlyMessage["stats"]> }) {
  return (
    <div className="stats-line" aria-label="Generation stats">
      <span title="Input tokens"><IconUp size={13} />{formatTokens(stats.inputTokens)} tokens{stats.cachedTokens ? ` (${formatTokens(stats.cachedTokens)} cached)` : ""}</span>
      <span title="Output tokens"><IconDown size={13} />{formatTokens(stats.outputTokens)} tokens</span>
      {stats.tokensPerSecond != null && <span title="Speed"><IconBolt size={13} />{stats.tokensPerSecond.toFixed(1)} tok/s</span>}
      {stats.durationMs != null && <span title="Duration"><IconClock size={13} />{formatSeconds(stats.durationMs)}</span>}
    </div>
  );
}

export function MessageView({ message, streaming, interactive, chat, busy }: {
  message: FriendlyMessage; streaming: boolean; interactive: boolean; chat: FriendlyChatState; busy: boolean;
}) {
  const [menuOpen, setMenuOpen] = useState(false);
  const isUser = message.role === "user";
  const program = !isUser ? openUiProgram(message.text, message.openui) : null;
  const speaking = chat.ttsSpeakingMessageId === message.id;
  const editing = chat.editingMessageId === message.id;

  return (
    <article className={`message ${isUser ? "message-user" : "message-assistant"}${editing ? " is-editing" : ""}`} data-message-id={message.id}>
      {!isUser && message.reasoning && <Reasoning text={message.reasoning} ms={message.reasoningMs} streaming={streaming && !message.text} />}
      {!isUser && (message.tools ?? []).map(tool => <ToolCard key={tool.id} tool={tool} interactive={interactive} />)}
      {isUser && (message.attachments?.length ?? 0) > 0 && (
        <div className="message-attachments">{message.attachments!.map((a, i) => <AttachmentChip key={i} item={a} />)}</div>
      )}
      {(message.text || program || (streaming && !message.reasoning && !(message.tools?.length))) && (
        <div className="message-body">
          {program ? <RichResponse text={message.text} program={program} loading={streaming} interactive={interactive} />
            : isUser ? <p>{message.text}</p>
            : message.text ? <Markdown text={message.text} />
            : <p className="thinking">Thinking…</p>}
        </div>
      )}
      {!isUser && (message.citations?.length ?? 0) > 0 && (
        <ol className="citations" aria-label="Sources">
          {message.citations!.map((c, i) => {
            const url = safeLinkTarget(c.url);
            return <li key={i}>{url ? <a href={url}>{c.title || new URL(url).hostname}</a> : c.title}</li>;
          })}
        </ol>
      )}
      {editing && <div className="editing-badge">Editing below</div>}
      {!streaming && (
        <div className="message-actions" aria-label="Message actions">
          <button type="button" className="icon-btn" aria-label="Copy" onClick={() => postNative({ type: "copyMessage", messageId: message.id })}><IconCopy /></button>
          {isUser && message.canEdit && (
            <button type="button" className="icon-btn" aria-label="Edit" onClick={() => postNative({ type: "edit", messageId: message.id })}><IconEdit /></button>
          )}
          {!isUser && message.canRegenerate && (
            <button type="button" className="icon-btn" aria-label="Regenerate" onClick={() => postNative({ type: "regenerate", messageId: message.id })}><IconRegenerate /></button>
          )}
          {!isUser && (
            <button type="button" className="icon-btn" aria-label={speaking ? "Stop reading" : "Read aloud"} disabled={!chat.ttsAvailable}
              onClick={() => postNative(speaking ? { type: "stopSpeaking" } : { type: "speak", messageId: message.id })}>
              {speaking ? <IconStopCircle /> : <IconSpeaker />}
            </button>
          )}
          <span className="menu-anchor">
            <button type="button" className="icon-btn" aria-label="More options" aria-haspopup="menu" aria-expanded={menuOpen} onClick={() => setMenuOpen(v => !v)}><IconMore /></button>
            {menuOpen && <MoreMenu message={message} onClose={() => setMenuOpen(false)} canDelete={!busy} />}
          </span>
          <BranchSwitcher message={message} disabled={busy} />
        </div>
      )}
      {!isUser && !streaming && chat.showStats && message.stats && <StatsLine stats={message.stats} />}
    </article>
  );
}
