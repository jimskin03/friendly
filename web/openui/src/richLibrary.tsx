import { createLibrary } from "@openuidev/react-lang";
import { openuiChatLibrary } from "@openuidev/react-ui/genui-lib";
import { MarkDownRenderer, TableCell } from "@openuidev/react-ui";
import type { ComponentProps } from "react";
import { withColorSwatch } from "./colorSwatch";
import remarkGfm from "remark-gfm";

const alignments = new Set(["left", "center", "right"]);
// Same cell as upstream's Markdown table, plus the hex colour swatch (#35).
function SwatchCell({ node: _node, align, children, ...props }: ComponentProps<"td"> & { node?: unknown }) {
  const a = align && alignments.has(align) ? (align as "left" | "center" | "right") : undefined;
  return <TableCell {...props} align={a}>{withColorSwatch(children)}</TableCell>;
}

// The upstream MarkDownRenderer definition omits GFM, although TextContent enables it.
// Keep its schema and container variants so existing generated programs still work.
export const richLibrary = createLibrary({
  root: openuiChatLibrary.root,
  componentGroups: openuiChatLibrary.componentGroups,
  components: Object.values(openuiChatLibrary.components).map(component =>
    component.name === "MarkDownRenderer"
      ? {
          ...component,
          component: ({ props }) => (
            <MarkDownRenderer textMarkdown={props.textMarkdown} variant={props.variant}
              options={{ remarkPlugins: [[remarkGfm, { singleTilde: false }]], components: { td: SwatchCell } }} />
          ),
        }
      : component,
  ),
});
