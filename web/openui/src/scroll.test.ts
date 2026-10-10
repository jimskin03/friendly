import { describe, expect, it, vi } from "vitest";
import { scrollInside } from "./scroll";

function box(top: number, bottom: number) {
  const height = bottom - top;
  return { top, bottom, left: 0, right: 100, width: 100, height, x: 0, y: top, toJSON() { return {}; } };
}

function view(top: number, bottom: number, scrollTop: number) {
  const scrollTo = vi.fn();
  const element = {
    getBoundingClientRect: () => box(top, bottom),
    scrollTop,
    scrollTo,
  } as unknown as HTMLElement;
  return { element, scrollTo };
}

describe("scrollInside", () => {
  it("scrolls the conversation to put a child at the end without touching the window", () => {
    const scroller = view(10, 110, 40);
    const child = { getBoundingClientRect: () => box(80, 200) } as unknown as Element;

    scrollInside(scroller.element, child, "end", "auto");

    expect(scroller.scrollTo).toHaveBeenCalledWith({ top: 130, behavior: "auto" });
  });

  it("aligns a child to the start of the conversation", () => {
    const scroller = view(20, 220, 15);
    const child = { getBoundingClientRect: () => box(70, 90) } as unknown as Element;

    scrollInside(scroller.element, child, "start", "smooth");

    expect(scroller.scrollTo).toHaveBeenCalledWith({ top: 65, behavior: "smooth" });
  });
});
