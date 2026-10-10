import type { ReactNode } from "react";
import { useAsset } from "./assets";
import { safeLinkTarget } from "./Markdown";
import { formatMinutes } from "./format";
import type { FriendlyTool } from "./types";

type Json = Record<string, unknown>;

export function parseJson(text?: string | null): Json | null {
  if (!text) return null;
  try {
    const value = JSON.parse(text) as unknown;
    return value && typeof value === "object" && !Array.isArray(value) ? (value as Json) : null;
  } catch {
    return null;
  }
}

const str = (o: Json | null | undefined, k: string): string | null => {
  const v = o?.[k];
  return typeof v === "string" ? v : typeof v === "number" || typeof v === "boolean" ? String(v) : null;
};
const num = (o: Json | null | undefined, k: string): number | null => {
  const v = o?.[k];
  return typeof v === "number" && Number.isFinite(v) ? v : typeof v === "string" && v.trim() && Number.isFinite(Number(v)) ? Number(v) : null;
};
const arr = (o: Json | null | undefined, k: string): Json[] =>
  Array.isArray(o?.[k]) ? (o![k] as unknown[]).filter((x): x is Json => !!x && typeof x === "object") : [];

const host = (url: string) => { try { return new URL(url).hostname.replace(/^www\./, ""); } catch { return url; } };

/** Short human title for the tool header, like the native tool UIs. */
export function toolTitle(tool: FriendlyTool): string {
  const input = parseJson(tool.input);
  const out = parseJson(tool.output);
  switch (tool.name) {
    case "search_web": return `Searched “${str(input, "query") ?? ""}”`;
    case "scrape_web": return `Read ${host(str(input, "url") ?? "page")}`;
    case "get_screen_time": return "Screen time";
    case "chart_display": return str(input, "title") ?? "Chart";
    case "workspace_edit_file": return `Edited ${str(input, "path") ?? "file"}`;
    case "workspace_read_file": return `Read ${str(input, "path") ?? "file"}`;
    case "workspace_write_file": return `Wrote ${str(input, "path") ?? "file"}`;
    case "workspace_shell": return `$ ${(str(input, "command") ?? "shell").slice(0, 80)}`;
    case "conversation_search": return `Searched chats for “${str(input, "query") ?? ""}”`;
    case "recent_chats": return "Recent chats";
    case "memory_tool": return `Memory · ${str(input, "action") ?? "update"}`;
    case "calendar_query": return "Calendar";
    case "calendar_create": return `Added “${str(input, "title") ?? "event"}”`;
    case "text_to_speech": return "Read aloud";
    case "get_time_info": return "Checked the time";
    case "use_skill": return `Skill · ${str(input, "name") ?? ""}`;
    case "desktop_screenshot": return "Captured desktop";
    case "phone_screenshot": return "Captured phone screen";
    case "desktop_click": case "phone_click": return `Tapped ${str(out, "x") ?? "?"}, ${str(out, "y") ?? "?"}`;
    case "desktop_type": return "Typed on desktop";
    case "phone_type_text": return "Typed on phone";
    case "desktop_browser_open": return `Opened ${host(str(out, "url") ?? str(input, "url") ?? "browser")}`;
    case "phone_launch_app": return `Opened ${str(out, "app_name") ?? str(input, "app_name") ?? "app"}`;
    default: return tool.name.replace(/_/g, " ");
  }
}

function SearchResults({ out }: { out: Json }) {
  const items = arr(out, "items");
  const answer = str(out, "answer");
  return (
    <div className="tp-search">
      {answer && <p className="tp-answer">{answer}</p>}
      <ol>
        {items.slice(0, 8).map((item, i) => {
          const url = safeLinkTarget(str(item, "url") ?? undefined);
          return (
            <li key={i}>
              {url ? <a href={url}>{str(item, "title") ?? url}</a> : <span>{str(item, "title")}</span>}
              {url && <span className="tp-host">{host(url)}</span>}
              {str(item, "text") && <span className="tp-snippet">{str(item, "text")!.slice(0, 180)}</span>}
            </li>
          );
        })}
      </ol>
    </div>
  );
}

function ScreenTime({ out }: { out: Json }) {
  if (str(out, "error") === "NO_PERMISSION") return <p className="tp-note">Usage access is off. Allow it in Settings to see screen time.</p>;
  const apps = arr(out, "apps").map(a => ({
    name: str(a, "app_name") ?? str(a, "package") ?? "App",
    minutes: num(a, "total_minutes") ?? (num(a, "total_ms") ?? 0) / 60000,
  })).sort((a, b) => b.minutes - a.minutes);
  const total = num(out, "total_minutes") ?? apps.reduce((s, a) => s + a.minutes, 0);
  const max = Math.max(1, ...apps.map(a => a.minutes));
  return (
    <div className="tp-screentime">
      <div className="tp-total"><strong>{formatMinutes(total)}</strong><span>{[str(out, "start"), str(out, "end")].filter(Boolean).join(" – ")}</span></div>
      {apps.slice(0, 8).map(app => (
        <div key={app.name} className="tp-bar-row">
          <span className="tp-bar-label">{app.name}</span>
          <span className="tp-bar"><span style={{ width: `${(app.minutes / max) * 100}%` }} /></span>
          <span className="tp-bar-value">{formatMinutes(app.minutes)}</span>
        </div>
      ))}
    </div>
  );
}

export function lineDiff(oldText: string, newText: string): { kind: " " | "-" | "+"; text: string }[] {
  const a = oldText.split("\n"), b = newText.split("\n");
  if (a.length * b.length > 250_000) return [...a.map(t => ({ kind: "-" as const, text: t })), ...b.map(t => ({ kind: "+" as const, text: t }))];
  const lcs = Array.from({ length: a.length + 1 }, () => new Array<number>(b.length + 1).fill(0));
  for (let i = a.length - 1; i >= 0; i--) for (let j = b.length - 1; j >= 0; j--)
    lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
  const out: { kind: " " | "-" | "+"; text: string }[] = [];
  let i = 0, j = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) { out.push({ kind: " ", text: a[i] }); i++; j++; }
    else if (lcs[i + 1][j] >= lcs[i][j + 1]) out.push({ kind: "-", text: a[i++] });
    else out.push({ kind: "+", text: b[j++] });
  }
  while (i < a.length) out.push({ kind: "-", text: a[i++] });
  while (j < b.length) out.push({ kind: "+", text: b[j++] });
  return out;
}

function Diff({ path, oldText, newText }: { path?: string | null; oldText: string; newText: string }) {
  const lines = lineDiff(oldText, newText);
  const added = lines.filter(l => l.kind === "+").length, removed = lines.filter(l => l.kind === "-").length;
  return (
    <div className="tp-diff">
      <div className="tp-file"><span>{path}</span><span className="tp-add">+{added}</span><span className="tp-del">−{removed}</span></div>
      <pre>{lines.slice(0, 200).map((l, i) => <div key={i} className={`dl dl-${l.kind === "+" ? "add" : l.kind === "-" ? "del" : "ctx"}`}><span aria-hidden="true">{l.kind}</span>{l.text || " "}</div>)}</pre>
    </div>
  );
}

function FileContent({ path, text }: { path?: string | null; text: string }) {
  return <div className="tp-diff"><div className="tp-file"><span>{path}</span></div><pre className="tp-code">{text.slice(0, 4000)}</pre></div>;
}

function Shell({ input, out }: { input: Json | null; out: Json | null }) {
  const code = num(out, "exit_code") ?? num(out, "exitCode");
  const text = [str(out, "stdout"), str(out, "stderr")].filter(Boolean).join("\n").trim();
  return (
    <div className="tp-diff">
      <div className="tp-file"><span>$ {str(input, "command")}</span>{code != null && <span className={code === 0 ? "tp-add" : "tp-del"}>exit {code}</span>}</div>
      {text && <pre className="tp-code">{text.slice(0, 4000)}</pre>}
    </div>
  );
}

const CHART_COLORS = ["var(--accent)", "#2f8f83", "#7a5cc2", "#c2410c", "#4b7bd1"];

export function Chart({ spec }: { spec: Json }) {
  const style = str(spec, "style");
  const series = arr(spec, "series");
  const xAxis = (spec.xAxis ?? {}) as Json;
  const labels = Array.isArray(xAxis.data) ? (xAxis.data as unknown[]).map(String) : [];
  const W = 320, H = 170, P = { l: 34, r: 8, t: 10, b: 24 };
  type Pt = { x: number; y: number };
  const seriesPts: Pt[][] = series.map(s => style === "scatter"
    ? arr(s, "points").map(p => ({ x: num(p, "x") ?? 0, y: num(p, "y") ?? 0 }))
    : (Array.isArray(s.values) ? (s.values as unknown[]) : []).map((v, i) => ({ x: i, y: Number(v) || 0 })));
  const all = seriesPts.flat();
  if (!all.length || !style) return null;
  const minY = Math.min(0, ...all.map(p => p.y)), maxY = Math.max(...all.map(p => p.y), minY + 1);
  const minX = Math.min(...all.map(p => p.x)), maxX = Math.max(...all.map(p => p.x), minX + 1);
  const count = Math.max(labels.length, ...seriesPts.map(s => s.length));
  const sx = (x: number) => style === "scatter" ? P.l + ((x - minX) / (maxX - minX)) * (W - P.l - P.r) : P.l + ((x + 0.5) / count) * (W - P.l - P.r);
  const sy = (y: number) => H - P.b - ((y - minY) / (maxY - minY)) * (H - P.t - P.b);
  const bw = (W - P.l - P.r) / count / (series.length + 1);
  const ticks = [minY, (minY + maxY) / 2, maxY];
  return (
    <figure className="tp-chart">
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={str(spec, "title") ?? "Chart"}>
        {ticks.map(t => <g key={t}><line x1={P.l} x2={W - P.r} y1={sy(t)} y2={sy(t)} className="grid" /><text x={P.l - 4} y={sy(t) + 3} textAnchor="end">{+t.toFixed(1)}</text></g>)}
        {seriesPts.map((pts, si) => {
          const color = CHART_COLORS[si % CHART_COLORS.length];
          if (style === "bar") return pts.map(p => <rect key={`${si}-${p.x}`} x={sx(p.x) - (bw * series.length) / 2 + si * bw} y={Math.min(sy(p.y), sy(0))} width={bw * 0.9} height={Math.abs(sy(0) - sy(p.y))} rx="2" fill={color} />);
          if (style === "scatter") return pts.map((p, i) => <circle key={`${si}-${i}`} cx={sx(p.x)} cy={sy(p.y)} r="3.5" fill={color} />);
          return <polyline key={si} fill="none" stroke={color} strokeWidth="2" points={pts.map(p => `${sx(p.x)},${sy(p.y)}`).join(" ")} />;
        })}
        {style !== "scatter" && labels.slice(0, count).map((l, i) => (count <= 8 || i % Math.ceil(count / 8) === 0) &&
          <text key={i} x={sx(i)} y={H - 8} textAnchor="middle">{l.slice(0, 8)}</text>)}
      </svg>
      {series.length > 1 && <figcaption>{series.map((s, i) => <span key={i}><i style={{ background: CHART_COLORS[i % CHART_COLORS.length] }} />{str(s, "name") ?? `Series ${i + 1}`}</span>)}</figcaption>}
    </figure>
  );
}

function ToolImage({ assetKey }: { assetKey: string }) {
  const src = useAsset(assetKey);
  return src ? <img className="tp-shot" src={src} alt="Screenshot" /> : <div className="tp-shot tp-shot-missing">Screenshot</div>;
}

function ListPreview({ items }: { items: string[] }) {
  return <ul className="tp-list">{items.slice(0, 8).map((t, i) => <li key={i}>{t}</li>)}</ul>;
}

/** Rich preview for a finished tool call, or null to fall back to raw input/output. */
export function ToolPreview({ tool }: { tool: FriendlyTool }): ReactNode {
  const input = parseJson(tool.input);
  const out = parseJson(tool.output);
  const images = (tool.images ?? []).map(key => <ToolImage key={key} assetKey={key} />);
  const body = (() => {
    switch (tool.name) {
      case "search_web": return out && (arr(out, "items").length || str(out, "answer")) ? <SearchResults out={out} /> : null;
      case "scrape_web": return out ? <ListPreview items={arr(out, "urls").map(u => `${host(str(u, "url") ?? "")} — ${(str(u, "content") ?? "").slice(0, 140)}`)} /> : null;
      case "get_screen_time": return out ? <ScreenTime out={out} /> : null;
      case "chart_display": return input ? <Chart spec={input} /> : null;
      case "workspace_edit_file": {
        const o = str(input, "old_text"), n = str(input, "new_text");
        return o != null && n != null ? <Diff path={str(input, "path")} oldText={o} newText={n} /> : null;
      }
      case "workspace_read_file": return str(out, "text") != null ? <FileContent path={str(input, "path")} text={str(out, "text")!} /> : null;
      case "workspace_write_file": return str(input, "text") != null ? <FileContent path={str(input, "path")} text={str(input, "text")!} /> : null;
      case "workspace_shell": return <Shell input={input} out={out} />;
      case "recent_chats": case "conversation_search": {
        const chats = [...arr(out, "chats"), ...arr(out, "results"), ...arr(out, "items")].map(c => str(c, "title")).filter((t): t is string => !!t);
        return chats.length ? <ListPreview items={chats} /> : null;
      }
      case "calendar_query": {
        const events = arr(out, "events").map(e => [str(e, "title"), str(e, "start") ?? str(e, "begin")].filter(Boolean).join(" · "));
        return events.length ? <ListPreview items={events} /> : null;
      }
      case "memory_tool": return str(out, "content") ? <p className="tp-note">{str(out, "content")!.slice(0, 600)}</p> : null;
      case "text_to_speech": return str(input, "text") ? <p className="tp-note">{str(input, "text")!.slice(0, 600)}</p> : null;
      default: return null;
    }
  })();
  if (!body && !images.length) return null;
  return <div className="tool-preview">{body}{images.length > 0 && <div className="tp-shots">{images}</div>}</div>;
}
