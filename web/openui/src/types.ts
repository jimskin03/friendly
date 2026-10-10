export type FriendlyRole = "user" | "assistant" | "system" | "tool";

export interface FriendlyMessage {
  id: string;
  role: FriendlyRole;
  text: string;
  openui?: string | null;
  branchIndex?: number;
  branchCount?: number;
  canRegenerate?: boolean;
  canEdit?: boolean;
  canReport?: boolean;
}

export interface FriendlyCapabilities {
  edition: "nightly" | "play";
  phoneAutomation: boolean;
  desktopControl: boolean;
  contentReporting: boolean;
  openUiActions: boolean;
}

export interface FriendlySnapshot {
  protocolVersion: 1;
  sessionId: string;
  revision: number;
  conversationId: string;
  title: string;
  loading: boolean;
  processingStatus?: string | null;
  darkMode: boolean;
  draft: string;
  attachmentCount: number;
  modelAvailable: boolean;
  capabilities: FriendlyCapabilities;
  messages: FriendlyMessage[];
  suggestions: string[];
}

export type FriendlyAction =
  | { type: "ready" }
  | { type: "send"; text: string }
  | { type: "stop" }
  | { type: "regenerate"; messageId: string }
  | { type: "report"; messageId: string }
  | { type: "suggestion"; text: string }
  | { type: "draft"; text: string }
  | { type: "attachments" }
  | { type: "voice" }
  | { type: "native" }
  | { type: "edit"; messageId: string };

declare global {
  interface Window {
    FriendlyOpenUI?: { postMessage(payload: string): void };
    friendlyOpenUI?: {
      pushSnapshot(payload: string): void;
      retire(): void;
    };
  }
}