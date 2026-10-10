import { describe, expect, it } from "vitest";
import { lineDiff, parseJson, toolTitle } from "./ToolPreviews";
import { outlineEntries } from "./Outline";
import { parseAssets } from "./assets";
import { orderedBlocks } from "./MessageView";

describe("tool previews", () => {
  it("diffs lines", () => {
    expect(lineDiff("a\nb\nc", "a\nB\nc")).toEqual([
      { kind: " ", text: "a" }, { kind: "-", text: "b" }, { kind: "+", text: "B" }, { kind: " ", text: "c" },
    ]);
  });
  it("parses only JSON objects", () => {
    expect(parseJson("[1]")).toBeNull();
    expect(parseJson("nope")).toBeNull();
    expect(parseJson('{"a":1}')).toEqual({ a: 1 });
  });
  it("titles tools", () => {
    expect(toolTitle({ id: "1", name: "search_web", input: '{"query":"cats"}', state: "auto", executed: true })).toBe("Searched “cats”");
  });
});

describe("outline", () => {
  const messages = [
    { id: "1", role: "user" as const, text: "Plan a trip to Penang" },
    { id: "2", role: "assistant" as const, text: "Sure, here is a plan with food stops" },
  ];
  it("lists and filters", () => {
    expect(outlineEntries(messages, "").map(e => e.id)).toEqual(["1", "2"]);
    expect(outlineEntries(messages, "food").map(e => e.id)).toEqual(["2"]);
  });
});

describe("assets", () => {
  it("accepts only image data URIs with hex keys", () => {
    const out = parseAssets(JSON.stringify({
      abcdef0123: "data:image/jpeg;base64,AAA=", bad: "data:image/jpeg;base64,AAA=", "0123456789": "javascript:alert(1)",
    }));
    expect(Object.keys(out)).toEqual(["abcdef0123"]);
  });
});

describe("ordered blocks", () => {
  it("keeps native order", () => {
    const blocks = [{ kind: "text" as const, text: "a" }, { kind: "tool" as const, toolId: "t" }, { kind: "text" as const, text: "b" }];
    expect(orderedBlocks({ id: "m", role: "assistant", text: "ab", blocks })).toBe(blocks);
  });
  it("falls back for old snapshots", () => {
    expect(orderedBlocks({ id: "m", role: "assistant", text: "x", reasoning: "r" }).map(b => b.kind)).toEqual(["reasoning", "text"]);
  });
});
