import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { Markdown } from "./Markdown";
import { RichResponse } from "./RichResponse";
import { hexColorOf } from "./colorSwatch";

const table = "| Name | Color |\n|---|---|\n| Coral | #FF5733 |\n| Short | #0AF |\n| Alpha | #11223344 |\n| Red | Red |\n| Bad | #12345 |";

function checkSwatches(html: string) {
  for (const c of ["#FF5733", "#0AF", "#11223344"]) {
    expect(html).toContain(`data-color="${c}"`);
    expect(html).toContain(`background-color:${c}`);
  }
  expect(html.match(/class="color-swatch"/g)).toHaveLength(3);
  expect(html).toContain(">Red<");
  expect(html).toContain("#12345");
}

describe("hex colour swatches in table cells (#35)", () => {
  it("only accepts whole-cell 3, 6 or 8 digit hex colours", () => {
    expect(hexColorOf(" #ff5733 ")).toBe("#ff5733");
    expect(hexColorOf("#0AF")).toBe("#0AF");
    for (const bad of ["Red", "#12345", "#GGGGGG", "color: #fff", "#fff;background:url(x)", ""]) {
      expect(hexColorOf(bad)).toBeNull();
    }
  });

  it("renders swatches in plain Markdown tables", () => {
    checkSwatches(renderToStaticMarkup(<Markdown text={table} />));
  });

  it("renders swatches in OpenUI MarkDownRenderer tables", () => {
    const program = `root = Card([MarkDownRenderer(${JSON.stringify(table)})])`;
    checkSwatches(renderToStaticMarkup(<RichResponse text={program} program={program} loading={false} interactive />));
  });
});
