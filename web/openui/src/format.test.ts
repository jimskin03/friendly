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
