import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { dispatchRichAction, openUiProgram, RichResponse } from "./RichResponse";
import { setNativeSession } from "./native";

const program = 'root = Card([CardHeader("Gold price", "Illustrative data"), LineChart(["Jan", "Feb", "Mar"], [Series("USD/oz", [2600, 2700, 2650])], "linear"), FollowUpBlock([FollowUpItem("Compare months")])])';

describe("rich response generation contract", () => {
  it("accepts actual assistant text, fenced text, streamed text and legacy metadata", () => {
    expect(openUiProgram(program)).toBe(program);
    expect(openUiProgram(`\`\`\`openui\n${program}\n\`\`\``)).toBe(program);
    expect(openUiProgram('root = Card([header])\nheader = CardHeader("Go')).not.toBeNull();
    expect(openUiProgram("Fallback", program)).toBe(program);
    expect(openUiProgram("# A normal Markdown answer")).toBeNull();
    expect(openUiProgram('Example: root = Card([])')).toBeNull();
  });

  it("renders real library components without requiring metadata", () => {
    const html = renderToStaticMarkup(<RichResponse text={program} program={program} loading={false} interactive />);
    expect(html).toContain("Gold price");
    expect(html).toContain("Illustrative data");
    expect(html).toContain("Compare months");
    expect(html).not.toContain("could not be displayed");
  });
});

describe("rich response native actions", () => {
  const postMessage = vi.fn();
  beforeEach(() => {
    postMessage.mockClear();
    (globalThis as { window?: unknown }).window = { FriendlyOpenUI: { postMessage } };
    setNativeSession("test-session");
  });
  it("sends follow-ups and form values through the existing chat bridge", () => {
    dispatchRichAction({ type: "continue_conversation", params: {}, humanFriendlyMessage: "Compare months", formState: { period: "year" } }, true);
    expect(JSON.parse(postMessage.mock.calls[0][0])).toMatchObject({ type: "send", text: 'Compare months\n\n{"period":"year"}', sessionId: "test-session" });
  });
  it("does not append an empty form state to a follow-up", () => {
    dispatchRichAction({ type: "continue_conversation", params: {}, humanFriendlyMessage: "Compare months", formState: {} }, true);
    expect(JSON.parse(postMessage.mock.calls[0][0]).text).toBe("Compare months");
  });
  it("blocks streaming actions and unsafe links", () => {
    dispatchRichAction({ type: "continue_conversation", params: {}, humanFriendlyMessage: "Go" }, false);
    dispatchRichAction({ type: "open_url", params: { url: "javascript:alert(1)" }, humanFriendlyMessage: "Go" }, true);
    expect(postMessage).not.toHaveBeenCalled();
    dispatchRichAction({ type: "open_url", params: { url: "https://example.com/" }, humanFriendlyMessage: "Go" }, true);
    expect(JSON.parse(postMessage.mock.calls[0][0])).toMatchObject({ type: "link", url: "https://example.com/" });
  });
});
