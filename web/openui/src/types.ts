export type FriendlyRole = "user" | "assistant" | "system" | "tool";

export interface FriendlyStats {
  inputTokens: number;
  outputTokens: number;
  cachedTokens?: number;
  durationMs?: number | null;
  tokensPerSecond?: number | null;
}

export interface FriendlyAttachment { kind: "image" | "video" | "audio" | "file"; name: string; size?: number | null; thumb?: string | null }

export interface FriendlyBlock { kind: "text" | "reasoning" | "tool"; text?: string | null; toolId?: string | null; ms?: number | null }

export interface FriendlyOption { id: string; name: string; group?: string | null; enabled?: boolean }

export interface FriendlyPickers {
  assistants: FriendlyOption[];
  assistantId?: string | null;
  models: FriendlyOption[];
  modelId?: string | null;
  reasoning?: string | null;
  reasoningLevels: string[];
  mcp: FriendlyOption[];
  searchMode: "off" | "local" | "built_in";
  searchModes: string[];
  searchServices: FriendlyOption[];
  searchServiceId?: string | null;
}

export interface FriendlyTool {
  id: string;
  name: string;
  input: string;
  output?: string | null;
  state: "auto" | "pending" | "approved" | "denied" | "answered";
  executed: boolean;
  images?: string[];
}

export interface FriendlyCitation { title: string; url: string }

export interface FriendlyMessage {
  id: string;
  role: FriendlyRole;
  text: string;
  openui?: string | null;
  reasoning?: string | null;
  reasoningMs?: number | null;
  blocks?: FriendlyBlock[];
  tools?: FriendlyTool[];
  attachments?: FriendlyAttachment[];
  citations?: FriendlyCitation[];
  stats?: FriendlyStats | null;
  branchIndex?: number | null;
  branchCount?: number | null;
  favorite?: boolean;
  canRegenerate?: boolean;
  canEdit?: boolean;
  canReport?: boolean;
}

export type VoicePhase = "off" | "connecting" | "listening" | "transcribing" | "speaking" | "error";

export interface FriendlyChatState {
  editingMessageId?: string | null;
  pendingAttachments: FriendlyAttachment[];
  voice: VoicePhase;
  voiceTranscript: string;
  ttsSpeakingMessageId?: string | null;
  ttsAvailable: boolean;
  modelName?: string | null;
  assistantName?: string | null;
  showStats: boolean;
  queue: { id: string; text: string; editing?: boolean }[];
  errors: { id: string; title: string; message: string; solution?: string | null }[];
  desktopAvailable: boolean;
  phoneAvailable: boolean;
  desktopStreaming: boolean;
  folderName?: string | null;
  focusMessageId?: string | null;
  pickers?: FriendlyPickers;
  outlineRequest?: number;
}

export interface FriendlyCapabilities {
  edition: "nightly" | "play";
  phoneAutomation: boolean;
  desktopControl: boolean;
  contentReporting: boolean;
  openUiActions: boolean;
}

/** Colors from the active native Material theme (#RRGGBB or #AARRGGBB). */
export interface FriendlyTheme {
  primary: string;
  onPrimary: string;
  secondaryContainer: string;
  onSecondaryContainer: string;
  background: string;
  surface: string;
  surfaceContainer: string;
  surfaceContainerHigh: string;
  onSurface: string;
  onSurfaceVariant: string;
  outlineVariant: string;
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
  chat?: FriendlyChatState;
  capabilities: FriendlyCapabilities;
  messages: FriendlyMessage[];
  suggestions: string[];
  theme?: FriendlyTheme | null;
}

type WithMessage<T extends string> = { type: T; messageId: string };

export type FriendlyAction =
  | { type: "ready" }
  | { type: "send"; text: string; answer?: boolean }
  | { type: "stop" }
  | { type: "suggestion"; text: string }
  | { type: "draft"; text: string }
  | WithMessage<"regenerate"> | WithMessage<"edit"> | WithMessage<"copyMessage"> | WithMessage<"speak">
  | WithMessage<"delete"> | WithMessage<"fork"> | WithMessage<"share"> | WithMessage<"favorite">
  | { type: "branch"; messageId: string; delta: number }
  | { type: "cancelEdit" }
  | { type: "stopSpeaking" }
  | { type: "toolApproval"; toolCallId: string; approved: boolean; reason?: string }
  | { type: "toolAnswer"; toolCallId: string; text: string }
  | { type: "attachments" }
  | { type: "removeAttachment"; index: number }
  | { type: "voice" } | { type: "voiceStop" } | { type: "voiceInterrupt" }
  | { type: "modelPicker" } | { type: "desktop" } | { type: "phone" } | { type: "export" }
  | { type: "dismissError"; id: string } | { type: "clearErrors" }
  | { type: "removeQueued"; id: string } | { type: "resumeQueue" }
  | { type: "link"; url: string }
  | { type: "copy"; text: string }
  | { type: "selectAssistant"; id: string } | { type: "selectModel"; id: string }
  | { type: "setReasoning"; level: string } | { type: "setMcp"; id: string; enabled: boolean }
  | { type: "setSearchMode"; mode: string } | { type: "setSearchService"; id: string }
  | { type: "beginEditQueued"; id: string } | { type: "editQueued"; id: string; text?: string }
  | { type: "errorSolution"; id: string; solution: string } | { type: "retry" }
  | { type: "desktopSnap" } | { type: "desktopStop" };

declare global {
  interface Window {
    FriendlyOpenUI?: { postMessage(payload: string): void };
    friendlyOpenUI?: {
      pushSnapshot(payload: string): void;
      pushAssets?(payload: string): void;
      retire(): void;
    };
  }
}