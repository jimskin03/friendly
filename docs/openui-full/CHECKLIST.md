# Native chat inventory → OpenUI (web) port

Source of truth: `ChatPage.kt`, `ChatList.kt`, `ChatInput`, `components/message/*`, `TopBar`.
Status: [x] ported to web, [~] web entry point that opens a native sheet/system UI, [ ] missing.

## Transcript (ChatList / ChatMessage)
- [ ] User bubble with text + image/file attachments
- [ ] Assistant Markdown, code (highlight + copy), LaTeX, tables
- [ ] OpenUI rich cards
- [ ] Reasoning / chain-of-thought (collapsible, duration)
- [ ] Tool-call cards (name, input, output) incl. built-in/desktop/phone/workspace tools
- [ ] Tool approval (approve / deny) and ask-user answers
- [ ] Citations / search-result links (UrlCitation annotations)
- [ ] Per-message action bar: copy, regenerate, read aloud (TTS), more menu
- [ ] More menu: select & copy, edit, share, fork, favorite, delete
- [ ] Branch/version switcher `< n/m >`
- [ ] Stats line: ↑ input tokens (cached), ↓ output tokens, ⚡ tok/s, ⏱ duration
- [ ] Error cards (dismiss / clear all)
- [ ] Processing status, streaming indicator
- [ ] Suggestions row
- [ ] Scroll-to-bottom / follow output
- [ ] Conversation outline / preview mode with search & jump (TopBar menu)
- [ ] Per-conversation system prompt card
- [ ] Long-press menus (native: long-press bubble → actions sheet)

## Input (ChatInput)
- [ ] Text composer, send, long-press send (= add without answering), stop
- [ ] Edit message mode (inline in web; cancel)
- [ ] Attachments: picker (images, camera, files), pending chips, remove
- [ ] Voice mode: start / stop / interrupt, state indicator
- [ ] Message queue (queued while generating: remove / edit / resume)
- [ ] Model picker, assistant picker, reasoning level, MCP picker, search mode/service
- [ ] Desktop control sheet, phone automation sheet (nightly), desktop-active banner

## Top bar / page
- [ ] Back, title (tap to rename), move to folder, new chat
- [ ] Share / export conversation
- [ ] Renderer failure handling (no native fallback)

## Entry points must open the web chat
- [ ] Home starter / input, folders, message search, history, favorites, notifications, shortcuts, share intent
