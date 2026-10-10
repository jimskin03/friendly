import type { CSSProperties } from "react";
import type { FriendlyTheme } from "./types";

const HEX = /^#([0-9a-f]{6}|[0-9a-f]{8})$/i;

/** Accepts #RRGGBB or Android-style #AARRGGBB and returns a CSS color, or null if malformed. */
export function cssColor(value: unknown): string | null {
  if (typeof value !== "string" || !HEX.test(value)) return null;
  if (value.length === 7) return value.toLowerCase();
  const a = parseInt(value.slice(1, 3), 16) / 255;
  const r = parseInt(value.slice(3, 5), 16);
  const g = parseInt(value.slice(5, 7), 16);
  const b = parseInt(value.slice(7, 9), 16);
  return a >= 1 ? `#${value.slice(3).toLowerCase()}` : `rgba(${r}, ${g}, ${b}, ${a.toFixed(3)})`;
}

const MAPPING: Array<[keyof FriendlyTheme, string]> = [
  ["primary", "--accent"],
  ["onPrimary", "--on-accent"],
  ["secondaryContainer", "--bubble"],
  ["onSecondaryContainer", "--on-bubble"],
  ["background", "--bg"],
  ["surface", "--surface"],
  ["surfaceContainer", "--surface-container"],
  ["surfaceContainerHigh", "--surface-high"],
  ["onSurface", "--text"],
  ["onSurfaceVariant", "--muted"],
  ["outlineVariant", "--line"],
];

/** CSS custom properties for the active native theme; unknown/malformed entries fall back to CSS defaults. */
export function themeStyle(theme: FriendlyTheme | null | undefined): CSSProperties {
  const style: Record<string, string> = {};
  if (!theme) return style;
  for (const [key, variable] of MAPPING) {
    const color = cssColor(theme[key]);
    if (color) style[variable] = color;
  }
  return style as CSSProperties;
}
