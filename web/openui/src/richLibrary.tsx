import { createLibrary } from "@openuidev/react-lang";
import { openuiChatLibrary } from "@openuidev/react-ui/genui-lib";
import { MarkDownRenderer } from "@openuidev/react-ui";
import remarkGfm from "remark-gfm";

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
              options={{ remarkPlugins: [[remarkGfm, { singleTilde: false }]] }} />
          ),
        }
      : component,
  ),
});
