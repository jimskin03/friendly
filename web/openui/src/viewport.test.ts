import { describe, expect, it } from "vitest";
import { keyboardShellVars } from "./viewport";

describe("keyboardShellVars", () => {
  it("uses the visual viewport when the keyboard shrinks it", () => {
    expect(keyboardShellVars({ height: 420, offsetTop: 12 }, 800)).toEqual({
      "--vv-height": "420px",
      "--vv-top": "12px",
    });
  });

  it("falls back to the window height and clamps a negative offset", () => {
    expect(keyboardShellVars(null, 640)).toEqual({
      "--vv-height": "640px",
      "--vv-top": "0px",
    });
    expect(keyboardShellVars({ height: -5, offsetTop: -8 }, 640)["--vv-height"]).toBe("0px");
    expect(keyboardShellVars({ height: -5, offsetTop: -8 }, 640)["--vv-top"]).toBe("0px");
  });
});
