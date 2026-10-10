import { describe, expect, it } from "vitest";
import { cssColor, themeStyle } from "./theme";

describe("native theme colors", () => {
  it("accepts #RRGGBB and Android #AARRGGBB", () => {
    expect(cssColor("#FFB870")).toBe("#ffb870");
    expect(cssColor("#FF1C1612")).toBe("#1c1612");
    expect(cssColor("#80000000")).toBe("rgba(0, 0, 0, 0.502)");
  });

  it("rejects anything that is not a plain hex color (no CSS injection)", () => {
    for (const bad of ["red", "#fff", "url(https://x)", "#123456;background:url(x)", 42, null]) {
      expect(cssColor(bad)).toBeNull();
    }
    const style = themeStyle({ primary: "expression(alert(1))", onPrimary: "#FFFFFF" } as never) as Record<string, string>;
    expect(style["--accent"]).toBeUndefined();
    expect(style["--on-accent"]).toBe("#ffffff");
  });
});
