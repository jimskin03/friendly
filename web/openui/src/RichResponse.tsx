import { Component, useCallback, useState, type ReactNode } from "react";
import { Renderer, type ActionEvent } from "@openuidev/react-lang";
import { openuiChatLibrary } from "@openuidev/react-ui/genui-lib";
import { Markdown, safeLinkTarget } from "./Markdown";
import { postNative } from "./native";

/** Normal replies are text, not metadata. Accept both raw and fenced programs. */
export function openUiProgram(text: string, metadata?: string | null): string | null {
  if (metadata?.trim()) return metadata.trim();
  const candidate = text.trim().replace(/^```(?:openui(?:-lang)?|\s)\s*\n/i, "").replace(/\n```\s*$/, "");
  return /^root\s*=\s*[A-Z]\w*\s*\(/.test(candidate) ? candidate : null;
}

export function dispatchRichAction(event: ActionEvent, enabled: boolean) {
  if (!enabled) return;
  if (event.type === "open_url") {
    const target = safeLinkTarget(typeof event.params.url === "string" ? event.params.url : undefined);
    if (target) postNative({ type: "link", url: target });
  } else if (event.type === "continue_conversation") {
    const label = event.humanFriendlyMessage || (typeof event.params.context === "string" ? event.params.context : "");
    const hasFormValues = event.formState && Object.keys(event.formState).length > 0;
    const text = [label, hasFormValues ? JSON.stringify(event.formState) : ""].filter(Boolean).join("\n\n").trim();
    if (text && text.length <= 16000) postNative({ type: "send", text });
  }
}

class RenderBoundary extends Component<{ children: ReactNode; fallback: ReactNode; program: string }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidUpdate(previous: Readonly<{ program: string }>) {
    if (this.state.failed && previous.program !== this.props.program) this.setState({ failed: false });
  }
  render() { return this.state.failed ? this.props.fallback : this.props.children; }
}

export function RichResponse({ text, program, loading, interactive }: { text: string; program: string; loading: boolean; interactive: boolean }) {
  const [invalid, setInvalid] = useState(false);
  const onError = useCallback((errors: unknown[]) => setInvalid(errors.length > 0), []);
  const fallback = (
    <div className="rich-fallback">
      <p>This rich response could not be displayed. You can regenerate it or view its source.</p>
      <details><summary>View response</summary><Markdown text={text === program ? `\`\`\`openui\n${program}\n\`\`\`` : text} /></details>
    </div>
  );
  return (
    <RenderBoundary program={program} fallback={fallback}>
      <div className="rich-response" hidden={invalid && !loading}>
        <Renderer response={program} library={openuiChatLibrary} isStreaming={loading}
          toolProvider={null} publishObservability={false} onError={onError}
          onAction={event => dispatchRichAction(event, interactive && !loading)} />
      </div>
      {invalid && !loading && fallback}
    </RenderBoundary>
  );
}
