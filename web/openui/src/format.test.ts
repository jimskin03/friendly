import { describe, expect, it } from "vitest";
import { formatSeconds, formatTokens } from "./format";

describe("stats formatting", () => {
  it("formats tokens like the native stats line", () => {
    expect(formatTokens(17012)).toBe("17.0K");
    expect(formatTokens(1100)).toBe("1.1K");
    expect(formatTokens(950)).toBe("950");
    expect(formatTokens(2_500_000)).toBe("2.5M");
  });
  it("formats durations in seconds", () => {
    expect(formatSeconds(9900)).toBe("9.9s");
  });
});

import { formatBytes, formatMinutes } from "./format";

describe("formatBytes/formatMinutes", () => {
  it("formats sizes", () => {
    expect(formatBytes(512)).toBe("512 B");
    expect(formatBytes(2048)).toBe("2.0 KB");
    expect(formatBytes(3 * 1024 * 1024)).toBe("3.0 MB");
    expect(formatBytes(null)).toBe("");
  });
  it("formats minutes", () => {
    expect(formatMinutes(42)).toBe("42m");
    expect(formatMinutes(135)).toBe("2h 15m");
  });
});
