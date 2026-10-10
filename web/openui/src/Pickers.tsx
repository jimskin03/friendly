import { useMemo, useState } from "react";
import { postNative } from "./native";
import type { FriendlyOption, FriendlyPickers } from "./types";

export type PickerKind = "assistant" | "model" | "reasoning" | "mcp" | "search" | "options";

const REASONING_LABEL: Record<string, string> = {
  off: "Off", auto: "Auto", low: "Low", medium: "Medium", high: "High", xhigh: "Extra high", max: "Max",
};
const SEARCH_LABEL: Record<string, string> = { off: "Off", local: "Search service", built_in: "Model's built-in search" };

export const reasoningLabel = (level?: string | null) => (level ? REASONING_LABEL[level] ?? level : "");
export const searchLabel = (mode: string) => SEARCH_LABEL[mode] ?? mode;

function Options({ options, selected, onPick }: { options: FriendlyOption[]; selected?: string | null; onPick: (o: FriendlyOption) => void }) {
  let lastGroup: string | null | undefined;
  return (
    <div className="picker-list" role="listbox">
      {options.map(option => {
        const header = option.group && option.group !== lastGroup ? <div className="picker-group" key={`g-${option.group}`}>{option.group}</div> : null;
        lastGroup = option.group;
        const active = option.id === selected;
        return [header, (
          <button key={option.id} type="button" role="option" aria-selected={active} className={`picker-option${active ? " is-active" : ""}`} onClick={() => onPick(option)}>
            <span>{option.name}</span>{active && <span className="picker-check" aria-hidden="true">✓</span>}
          </button>
        )];
      })}
    </div>
  );
}

export function PickerSheet({ kind, pickers, onClose }: { kind: PickerKind; pickers: FriendlyPickers; onClose: () => void }) {
  const [query, setQuery] = useState("");
  const models = useMemo(() => {
    const q = query.trim().toLowerCase();
    return q ? pickers.models.filter(m => m.name.toLowerCase().includes(q) || (m.group ?? "").toLowerCase().includes(q)) : pickers.models;
  }, [pickers.models, query]);
  const title = { assistant: "Assistant", model: "Model", reasoning: "Reasoning", mcp: "MCP servers", search: "Web search", options: "Chat options" }[kind];
  const all = kind === "options";
  const show = (k: PickerKind) => kind === k || (all && (k !== "assistant" || pickers.assistants.length > 1) && (k !== "reasoning" || pickers.reasoning != null));
  const heading = (text: string) => all ? <div className="picker-section">{text}</div> : null;
  return (
    <>
      <div className="menu-scrim sheet-scrim" onClick={onClose} />
      <div className="sheet" role="dialog" aria-label={title}>
        <div className="sheet-head"><strong>{title}</strong><button type="button" className="text-btn" onClick={onClose}>Done</button></div>
        {show("assistant") && (<>{heading("Assistant")}
          <Options options={pickers.assistants} selected={pickers.assistantId} onPick={o => { postNative({ type: "selectAssistant", id: o.id }); if (!all) onClose(); }} />
        </>)}
        {kind === "model" && (
          <>
            <input className="sheet-search" value={query} onChange={e => setQuery(e.target.value.slice(0, 100))} placeholder="Search models" aria-label="Search models" />
            {models.length ? <Options options={models} selected={pickers.modelId} onPick={o => { postNative({ type: "selectModel", id: o.id }); onClose(); }} />
              : <p className="picker-empty">No models match.</p>}
          </>
        )}
        {show("reasoning") && (<>{heading("Reasoning")}
          <div className="segmented" role="radiogroup" aria-label="Reasoning level">
            {pickers.reasoningLevels.map(level => (
              <button key={level} type="button" role="radio" aria-checked={pickers.reasoning === level}
                className={pickers.reasoning === level ? "is-active" : undefined}
                onClick={() => postNative({ type: "setReasoning", level })}>{reasoningLabel(level)}</button>
            ))}
          </div>
        </>)}
        {show("search") && (<>{heading("Web search")}
            <div className="segmented" role="radiogroup" aria-label="Search mode">
              {pickers.searchModes.map(mode => (
                <button key={mode} type="button" role="radio" aria-checked={pickers.searchMode === mode}
                  className={pickers.searchMode === mode ? "is-active" : undefined}
                  onClick={() => postNative({ type: "setSearchMode", mode })}>{searchLabel(mode)}</button>
              ))}
            </div>
            {pickers.searchMode === "local" && pickers.searchServices.length > 0 && (
              <>
                <div className="picker-group">Search service</div>
                <Options options={pickers.searchServices} selected={pickers.searchServiceId} onPick={o => postNative({ type: "setSearchService", id: o.id })} />
              </>
            )}
          </>)}
        {show("mcp") && (<>{heading("MCP servers")}{pickers.mcp.length ? (
          <div className="picker-list">
            {pickers.mcp.map(server => (
              <label key={server.id} className="picker-option picker-toggle">
                <span>{server.name}</span>
                <input type="checkbox" role="switch" checked={!!server.enabled}
                  onChange={e => postNative({ type: "setMcp", id: server.id, enabled: e.target.checked })} />
              </label>
            ))}
          </div>
        ) : <p className="picker-empty">No MCP servers are enabled. Add one in Settings → MCP.</p>}</>)}
      </div>
    </>
  );
}
