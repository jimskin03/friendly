import { memo, useState, type ReactNode } from "react";
import ReactMarkdown, { defaultUrlTransform, type Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import rehypeHighlight from "rehype-highlight";
import "katex/dist/katex.min.css";
import { postNative } from "./native";

// Raw HTML in model output is never rendered: react-markdown escapes it unless rehype-raw is
// added (it is not). URLs go through the default transform (drops javascript:, data:, etc.)
// and only http(s)/mailto links are handed to the native side, which opens them outside.
export function safeLinkTarget(href: string | undefined): string | null {
  if (!href) return null;
  const cleaned = defaultUrlTransform(href);
  if (!cleaned) return null;
  try {
    const url = new URL(cleaned);
    return url.protocol === "https:" || url.protocol === "http:" || url.protocol === "mailto:" ? url.toString() : null;
  } catch {
    return null;
  }
}

function textOf(node: ReactNode): string {
  if (node == null || typeof node === "boolean") return "";
  if (typeof node === "string" || typeof node === "number") return String(node);
  if (Array.isArray(node)) return node.map(textOf).join("");
  if (typeof node === "object" && "props" in node) return textOf((node as { props: { children?: ReactNode } }).props.children);
  return "";
}

function CodeBlock({ language, children }: { language: string | null; children: ReactNode }) {
  const [copied, setCopied] = useState(false);
  const copy = () => {
    postNative({ type: "copy", text: textOf(children).replace(/\n$/, "") });
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1500);
  };
  return (
    <div className="code-block">
      <div className="code-header">
        <span>{language ?? "code"}</span>
        <button type="button" onClick={copy} aria-label="Copy code">{copied ? "Copied" : "Copy"}</button>
      </div>
      <pre><code className={language ? `hljs language-${language}` : "hljs"}>{children}</code></pre>
    </div>
  );
}

const components: Components = {
  // Images are blocked by the page CSP (no remote loads), so never emit <img>.
  img: ({ alt }) => (alt ? <span className="md-image-alt">[{alt}]</span> : null),
  a: ({ href, children }) => {
    const target = safeLinkTarget(href);
    if (!target) return <span className="md-link-disabled">{children}</span>;
    return (
      <a
        href={target}
        onClick={event => {
          event.preventDefault();
          postNative({ type: "link", url: target });
        }}
      >
        {children}
      </a>
    );
  },
  pre: ({ children }) => {
    const child = Array.isArray(children) ? children[0] : children;
    const props = (child as { props?: { className?: string; children?: ReactNode } })?.props ?? {};
    const language = /language-([\w+-]+)/.exec(props.className ?? "")?.[1] ?? null;
    return <CodeBlock language={language}>{props.children}</CodeBlock>;
  },
  table: ({ children }) => (
    <div className="md-table-wrap">
      <table>{children}</table>
    </div>
  ),
};

export const Markdown = memo(function Markdown({ text }: { text: string }) {
  return (
    <div className="markdown">
      <ReactMarkdown
        remarkPlugins={[remarkGfm, [remarkMath, { singleDollarTextMath: true }]]}
        rehypePlugins={[[rehypeKatex, { throwOnError: false, strict: "ignore", trust: false }], [rehypeHighlight, { detect: false, ignoreMissing: true }]]}
        components={components}
        skipHtml
      >
        {text}
      </ReactMarkdown>
    </div>
  );
});
