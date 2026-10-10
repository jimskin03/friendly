/** Scroll `child` inside `view` only.
 *
 * `Element.scrollIntoView` also pans the layout viewport. On the Android
 * WebView that slides the fixed chat shell under the keyboard, so the
 * composer (and the text being typed) leaves the screen. Keystrokes push a
 * new snapshot, which used to call `scrollIntoView` on every character.
 */
export function scrollInside(
  view: HTMLElement,
  child: Element,
  align: "start" | "end",
  behavior: ScrollBehavior = "auto",
) {
  const viewRect = view.getBoundingClientRect();
  const childRect = child.getBoundingClientRect();
  const delta = align === "end" ? childRect.bottom - viewRect.bottom : childRect.top - viewRect.top;
  view.scrollTo({ top: view.scrollTop + delta, behavior });
}
