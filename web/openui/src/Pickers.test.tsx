import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { PickerSheet } from "./Pickers";
import type { FriendlyPickers } from "./types";

const pickers: FriendlyPickers = {
  assistants: [{ id: "default", name: "Default assistant" }],
  assistantId: "default",
  models: [],
  reasoning: "auto",
  reasoningLevels: ["off", "auto"],
  mcp: [{ id: "server", name: "Composio", enabled: true }],
  searchMode: "local",
  searchModes: ["off", "local"],
  searchServices: [{ id: "bing", name: "Bing" }],
  searchServiceId: "bing",
};

describe("chat options picker", () => {
  it("keeps reasoning and search while omitting assistant and MCP controls", () => {
    const html = renderToStaticMarkup(<PickerSheet kind="options" pickers={pickers} onClose={() => {}} />);
    expect(html).toContain("Reasoning");
    expect(html).toContain("Web search");
    expect(html).not.toContain("Assistant");
    expect(html).not.toContain("MCP servers");
    expect(html).not.toContain("Composio");
  });
});
