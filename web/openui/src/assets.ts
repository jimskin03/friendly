import { createContext, useContext } from "react";

/** Thumbnails pushed by native (asset key → data: URI). Only data:image URIs are accepted. */
export const AssetsContext = createContext<Record<string, string>>({});

export function useAsset(key?: string | null): string | null {
  const assets = useContext(AssetsContext);
  if (!key) return null;
  const uri = assets[key];
  return uri && /^data:image\/(jpeg|png|webp);base64,/.test(uri) ? uri : null;
}

export function parseAssets(payload: string): Record<string, string> {
  try {
    const raw = JSON.parse(payload) as unknown;
    if (!raw || typeof raw !== "object") return {};
    const out: Record<string, string> = {};
    for (const [key, value] of Object.entries(raw as Record<string, unknown>)) {
      if (/^[0-9a-f]{8,40}$/.test(key) && typeof value === "string" && /^data:image\/(jpeg|png|webp);base64,/.test(value)) out[key] = value;
    }
    return out;
  } catch {
    return {};
  }
}
