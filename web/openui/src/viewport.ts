/** Size the fixed chat shell to the visual viewport.
 *
 * `100dvh` on Android stays the large viewport, so the composer sits in the
 * area the keyboard covers. Tracking `visualViewport` keeps the shell, and
 * the text being typed, in the visible region. Offset is clamped so a
 * negative WebView report cannot shove the shell off the top.
 */
export function keyboardShellVars(
  viewport: { height: number; offsetTop: number } | null,
  fallbackHeight: number,
): Record<"--vv-height" | "--vv-top", string> {
  const height = viewport?.height ?? fallbackHeight;
  const top = viewport?.offsetTop ?? 0;
  return {
    "--vv-height": `${Math.max(0, height)}px`,
    "--vv-top": `${Math.max(0, top)}px`,
  };
}

export function installKeyboardShellLock(target: HTMLElement = document.documentElement): () => void {
  const apply = () => {
    const vars = keyboardShellVars(window.visualViewport, window.innerHeight);
    for (const [key, value] of Object.entries(vars)) target.style.setProperty(key, value);
    // A focused textarea makes the WebView pan the document. The shell is
    // position:fixed, so that pan hides it under the keyboard.
    if (window.scrollX !== 0 || window.scrollY !== 0) window.scrollTo(0, 0);
  };
  apply();
  const viewport = window.visualViewport;
  viewport?.addEventListener("resize", apply);
  viewport?.addEventListener("scroll", apply);
  window.addEventListener("resize", apply);
  return () => {
    viewport?.removeEventListener("resize", apply);
    viewport?.removeEventListener("scroll", apply);
    window.removeEventListener("resize", apply);
  };
}
