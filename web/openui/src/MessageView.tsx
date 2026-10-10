import { useEffect, useId, useState, type ReactNode } from "react";
import { postNative } from "./native";
import { RichResponse, openUiProgram } from "./RichResponse";
import { Markdown, safeLinkTarget } from "./Markdown";
import { formatBytes, formatSeconds, formatTokens } from "./format";
import { useAsset } from "./assets";
import { parseJson, ToolPreview, toolTitle } from "./ToolPreviews";
import type { FriendlyAttachment, FriendlyBlock, FriendlyChatState, FriendlyMessage, FriendlyTool } from "./types";
import {
  IconBolt, IconBrain, IconChevronDown, IconChevronLeft, IconChevronRight, IconClock, IconCopy, IconDown, IconEdit, IconFile, IconFork, IconImage,
  IconMore, IconRegenerate, IconShare, IconSpeaker, IconStar, IconStopCircle, IconTool, IconTrash, IconUp,
} from "./icons";

export function AttachmentChip({ item, onRemove }: { item: FriendlyAttachment; onRemove?: () => void }) {
  const thumb = useAsset(item.thumb);
  const size = formatBytes(item.size);
  if (item.kind === "image" && thumb) {
    return (
      <span className="attachment-thumb">
        <img src={thumb} alt={item.name || "Image"} />
        {onRemove && <button type="button" className="chip-remove" onClick={onRemove} aria-label={`Remove ${item.name}`}>×</button>}
      </span>
    );
  }
  const ext = item.name.includes(".") ? item.name.split(".").pop()!.slice(0, 4).toUpperCase() : item.kind.toUpperCase();
  return (
    <span className="attachment-chip">
      <span className={`file-icon file-${item.kind}`} aria-hidden="true">{item.kind === "image" ? <IconImage size={16} /> : <><IconFile size={16} /><small>{ext}</small></>}</span>
      <span className="attachment-meta"><span className="attachment-name">{item.name || item.kind}</span>{size && <span className="attachment-size">{size}</span>}</span>
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
  const preview = ToolPreview({ tool });
  const status = tool.state === "failed" ? "Failed" : tool.state === "denied" ? "Denied" : tool.executed ? "Done" : pending ? "Needs approval" : "Running…";
  return (
    <div className={`tool-card${pending ? " tool-pending" : ""}`}>
      <details open={pending}>
        <summary>
          <IconTool size={16} />
          <span className="tool-name">{toolTitle(tool)}</span>
          <span className={`tool-status status-${pending ? "pending" : tool.state === "failed" || tool.state === "denied" ? tool.state : tool.executed ? "done" : tool.state}`}>{status}</span>
        </summary>
        {preview ?? <>
          {tool.input && <pre className="tool-io"><code>{tool.input}</code></pre>}
          {tool.output && <pre className="tool-io tool-output"><code>{tool.output}</code></pre>}
        </>}
        {preview && (tool.input || tool.output) && (
          <details className="tool-raw"><summary>Raw data</summary>
            {tool.input && <pre className="tool-io"><code>{tool.input}</code></pre>}
            {tool.output && <pre className="tool-io tool-output"><code>{tool.output}</code></pre>}
          </details>
        )}
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

function MoreMenu({ message, onClose, canDelete, onSelect }: { message: FriendlyMessage; onClose: () => void; canDelete: boolean; onSelect: () => void }) {
  const item = (label: string, icon: ReactNode, action: () => void, danger = false) => (
    <button type="button" role="menuitem" className={danger ? "danger" : undefined} onClick={() => { action(); onClose(); }}>{icon}<span>{label}</span></button>
  );
  return (
    <>
      <div className="menu-scrim" onClick={onClose} />
      <div className="menu" role="menu">
        {item("Select & copy", <IconCopy />, onSelect)}
        {message.canEdit && item("Edit", <IconEdit />, () => postNative({ type: "edit", messageId: message.id }))}
        {item("Share", <IconShare />, () => postNative({ type: "share", messageId: message.id }))}
        {item("Fork into new chat", <IconFork />, () => postNative({ type: "fork", messageId: message.id }))}
        {item(message.favorite ? "Remove favorite" : "Favorite", <IconStar />, () => postNative({ type: "favorite", messageId: message.id }))}
        {canDelete && item("Delete", <IconTrash />, () => postNative({ type: "delete", messageId: message.id }), true)}
      </div>
    </>
  );
}

function SelectSheet({ text, onClose }: { text: string; onClose: () => void }) {
  return (
    <>
      <div className="menu-scrim sheet-scrim" onClick={onClose} />
      <div className="sheet" role="dialog" aria-label="Select text">
        <div className="sheet-head"><strong>Select text</strong>
          <button type="button" className="text-btn" onClick={() => postNative({ type: "copy", text })}>Copy all</button>
          <button type="button" className="text-btn" onClick={onClose}>Done</button></div>
        <div className="select-text" tabIndex={0}>{text}</div>
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

/** Blocks in real order; falls back to reasoning → tools → text for older snapshots. */
export function orderedBlocks(message: FriendlyMessage): FriendlyBlock[] {
  if (message.blocks && message.blocks.length) return message.blocks;
  const out: FriendlyBlock[] = [];
  if (message.reasoning) out.push({ kind: "reasoning", text: message.reasoning, ms: message.reasoningMs });
  for (const tool of message.tools ?? []) out.push({ kind: "tool", toolId: tool.id });
  if (message.text) out.push({ kind: "text", text: message.text });
  return out;
}

function MessageActivity({ blocks, tools, streaming, interactive, stats }: {
  blocks: FriendlyBlock[]; tools: FriendlyTool[]; streaming: boolean; interactive: boolean;
  stats?: FriendlyMessage["stats"];
}) {
  const [expanded, setExpanded] = useState(streaming);
  const contentId = useId();
  // A completed or stopped response always folds its history. Later manual expansion
  // survives unrelated snapshots; pending decisions remain outside the folded history.
  useEffect(() => { setExpanded(streaming); }, [streaming]);

  const steps = blocks.filter(block => block.kind !== "text");
  const toolIds = new Set(steps.filter(block => block.kind === "tool").map(block => block.toolId));
  const activityTools = tools.filter(tool => toolIds.has(tool.id));
  const pending = activityTools.filter(tool => tool.state === "pending" && !tool.executed);
  const reasoning = steps.filter(block => block.kind === "reasoning");
  const reasoningMs = reasoning.reduce((sum, block) => sum + Math.max(0, block.ms ?? 0), 0);
  const summary = [
    reasoning.length ? reasoningMs > 0 ? `${formatSeconds(reasoningMs)} thinking` : "Thinking" : null,
    activityTools.length ? `${activityTools.length} ${activityTools.length === 1 ? "action" : "actions"}` : null,
  ].filter(Boolean).join(" · ");
  if (!steps.length && !stats) return null;

  return (
    <section className={`message-activity${streaming ? " activity-live" : ""}`} aria-label="Response activity">
      <button type="button" className="activity-toggle" aria-expanded={expanded} aria-controls={contentId}
        onClick={() => setExpanded(open => !open)}>
        <IconBrain size={17} />
        <span className="activity-label">{pending.length ? "Needs your input" : streaming ? "Working…" : steps.length ? "Activity" : "Response details"}</span>
        {summary && <span className="activity-summary">{summary}</span>}
        {streaming && <span className="activity-dot" aria-hidden="true" />}
        <span className={`activity-chevron${expanded ? " is-open" : ""}`}><IconChevronDown size={16} /></span>
      </button>
      <div id={contentId} className="activity-body" hidden={!expanded}>
        {expanded && <>
          {steps.map((block, index) => {
            if (block.kind === "reasoning") {
              return <Reasoning key={`reasoning-${index}`} text={block.text ?? ""} ms={block.ms}
                streaming={streaming && block === blocks[blocks.length - 1]} />;
            }
            const tool = activityTools.find(item => item.id === block.toolId);
            // Approval and ask_user forms are rendered once, always visible below.
            if (!tool || pending.some(item => item.id === tool.id)) return null;
            return <ToolCard key={tool.id} tool={tool} interactive={interactive} />;
          })}
          {!streaming && stats && <StatsLine stats={stats} />}
        </>}
      </div>
      {pending.length > 0 && <div className="activity-decisions">
        {pending.map(tool => <ToolCard key={tool.id} tool={tool} interactive={interactive} />)}
      </div>}
    </section>
  );
}

export function MessageView({ message, streaming, interactive, chat, busy }: {
  message: FriendlyMessage; streaming: boolean; interactive: boolean; chat: FriendlyChatState; busy: boolean;
}) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [selecting, setSelecting] = useState(false);
  const isUser = message.role === "user";
  const program = !isUser ? openUiProgram(message.text, message.openui) : null;
  const speaking = chat.ttsSpeakingMessageId === message.id;
  const editing = chat.editingMessageId === message.id;

  return (
    <article className={`message ${isUser ? "message-user" : "message-assistant"}${editing ? " is-editing" : ""}`} data-message-id={message.id}>
      {(message.attachments?.length ?? 0) > 0 && (
        <div className="message-attachments">{message.attachments!.map((a, i) => <AttachmentChip key={i} item={a} />)}</div>
      )}
      {isUser ? (message.text && <div className="message-body"><p>{message.text}</p></div>) : (() => {
        const blocks = orderedBlocks(message);
        const lastText = blocks.map(b => b.kind).lastIndexOf("text");
        let programShown = false;
        const nodes = blocks.map((block, i) => {
          if (block.kind === "reasoning") return null;
          if (block.kind === "tool") {
            const tool = message.tools?.find(t => t.id === block.toolId);
            // A generated chart is answer content, while its call and raw data belong
            // to Activity. Keep the chart visible when the activity history folds.
            if (tool?.name === "chart_display" && tool.executed && parseJson(tool.output)?.success === true) {
              return <section key={i} className="message-artifact" aria-label="Generated chart">
                <h3>{toolTitle(tool)}</h3><ToolPreview tool={tool} />
              </section>;
            }
            return null;
          }
          if (program) {
            // An OpenUI program renders once, where the reply text ends.
            if (programShown || i !== lastText) return null;
            programShown = true;
            return <div key={i} className="message-body"><RichResponse text={message.text} program={program} loading={streaming} interactive={interactive} /></div>;
          }
          return <div key={i} className="message-body"><Markdown text={block.text ?? ""} /></div>;
        });
        if (program && !programShown) nodes.push(<div key="program" className="message-body"><RichResponse text={message.text} program={program} loading={streaming} interactive={interactive} /></div>);
        if (streaming && blocks.length === 0) nodes.push(<div key="thinking" className="message-body"><p className="thinking">Thinking…</p></div>);
        return <>
          <MessageActivity blocks={blocks} tools={message.tools ?? []} streaming={streaming} interactive={interactive}
            stats={chat.showStats ? message.stats : null} />
          {nodes}
        </>;
      })()}
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
            {menuOpen && <MoreMenu message={message} onClose={() => setMenuOpen(false)} canDelete={!busy} onSelect={() => setSelecting(true)} />}
          </span>
          <BranchSwitcher message={message} disabled={busy} />
        </div>
      )}
      {selecting && <SelectSheet text={message.text} onClose={() => setSelecting(false)} />}
    </article>
  );
}
