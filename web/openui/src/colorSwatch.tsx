import { Children, type ReactNode } from "react";

// Issue #35: table cells that hold only a hex colour get a small swatch in front of the code.
// The strict pattern is the only gate on what reaches the inline style.
const HEX_COLOR = /^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i;

export function hexColorOf(children: ReactNode): string | null {
  const parts = Children.toArray(children);
  if (!parts.length || !parts.every(p => typeof p === "string" || typeof p === "number")) return null;
  const text = parts.join("").trim();
  return HEX_COLOR.test(text) ? text : null;
}

export function withColorSwatch(children: ReactNode): ReactNode {
  const color = hexColorOf(children);
  if (!color) return children;
  return (
    <span className="color-swatch-cell">
      <span className="color-swatch" aria-hidden="true" data-color={color} style={{ backgroundColor: color }} />
      {children}
    </span>
  );
}
