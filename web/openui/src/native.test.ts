import { afterEach, describe, expect, it, vi } from "vitest";
import { postNative, setNativeSession } from "./native";

describe("native bridge", () => {
  afterEach(() => {
    setNativeSession(null);
    delete (globalThis as { window?: unknown }).window;
  });

  it("does not throw when running outside the Android WebView", () => {
    (globalThis as { window?: unknown }).window = {};
    expect(() => postNative({ type: "ready" })).not.toThrow();
  });

  it("serializes a send action to the native bridge", () => {
    const postMessage = vi.fn();
    (globalThis as { window?: unknown }).window = { FriendlyOpenUI: { postMessage } };
    setNativeSession("session-test");
    postNative({ type: "send", text: "Hello Friendly" });
    const payload = JSON.parse(postMessage.mock.calls[0][0]);
    expect(payload).toMatchObject({
      type: "send", text: "Hello Friendly", sessionId: "session-test",
    });
    expect(Number.isSafeInteger(payload.actionId)).toBe(true);
  });

  it("does not send privileged actions before a native session is received", () => {
    const postMessage = vi.fn();
    (globalThis as { window?: unknown }).window = { FriendlyOpenUI: { postMessage } };
    postNative({ type: "send", text: "unsolicited" });
    expect(postMessage).not.toHaveBeenCalled();
    postNative({ type: "ready" });
    expect(postMessage).toHaveBeenCalledExactlyOnceWith('{"type":"ready"}');
  });

  it("assigns strictly increasing action identifiers", () => {
    const postMessage = vi.fn();
    (globalThis as { window?: unknown }).window = { FriendlyOpenUI: { postMessage } };
    setNativeSession("session-test");
    postNative({ type: "stop" });
    postNative({ type: "stop" });
    const first = JSON.parse(postMessage.mock.calls[0][0]);
    const second = JSON.parse(postMessage.mock.calls[1][0]);
    expect(second.actionId).toBeGreaterThan(first.actionId);
  });
});
