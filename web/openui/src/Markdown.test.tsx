import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { Markdown, safeLinkTarget } from "./Markdown";

const html = (text: string) => renderToStaticMarkup(<Markdown text={text} />);

describe("assistant markdown", () => {
  it("renders headings, emphasis, lists, tables, quotes and inline code", () => {
    const out = html("# Title\n\n**bold** *it* `code`\n\n- a\n- b\n\n> quote\n\n| A | B |\n|---|---|\n| 1 | 2 |");
    for (const tag of ["<h1>", "<strong>", "<em>", "<code>", "<ul>", "<blockquote>", "<table>", "<td>"]) {
      expect(out).toContain(tag);
    }
  });

  it("highlights fenced code and offers a copy button", () => {
    const out = html("```kotlin\nval x = 1\n```");
    expect(out).toContain("language-kotlin");
    expect(out).toContain("hljs-keyword");
    expect(out).toContain("Copy code");
  });

  it("renders LaTeX with KaTeX", () => {
    expect(html("$E = mc^2$")).toContain("katex");
  });

  it("never injects raw HTML, script URLs or remote images", () => {
    const out = html('<img src=x onerror=alert(1)><script>alert(1)</script>\n\n[x](javascript:alert(1)) ![p](https://picsum.photos/1)');
    expect(out).not.toContain("<script");
    expect(out).not.toContain("onerror");
    expect(out).not.toContain("<img");
    expect(out).not.toContain("javascript:");
    expect(safeLinkTarget("javascript:alert(1)")).toBeNull();
    expect(safeLinkTarget("data:text/html,hi")).toBeNull();
    expect(safeLinkTarget("https://example.com/a")).toBe("https://example.com/a");
  });
});
