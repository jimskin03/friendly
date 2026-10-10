# Native chat inventory → web chat port

Legend: [x] in the web chat · [~] partly, or a web button that opens a native sheet/system UI · [ ] missing

## Transcript (was ChatList / ChatMessage)
- [x] User bubble with text; attachments as chips (images, files) — [ ] no image thumbnails yet (WebView can't load app files under the CSP)
- [x] Assistant Markdown, highlighted code + copy, LaTeX, tables, OpenUI rich cards
- [x] Reasoning (collapsible, "Thought for Xs")
- [~] Tool-call cards: generic name / input / output card. [ ] Native per-tool previews (search results, screen time, file diffs, charts, desktop/phone screenshots) not ported
- [x] Tool approval (Approve / Deny) and ask_user answers
- [x] Citations (UrlCitation) as source links
- [x] Action bar: copy, regenerate (assistant), edit (user), read aloud / stop (TTS), ⋮ more menu
- [~] More menu: edit, share, fork, favorite, delete. [ ] "Select & copy", "Render with WebView"
- [x] Version switcher `< n/m >`
- [x] Stats line: ↑ input tokens (cached), ↓ output tokens, ⚡ tok/s, ⏱ duration (honours "show token usage")
- [~] Error cards (dismiss / clear all). [ ] Error "solution" buttons
- [x] Processing status, streaming "Thinking…"
- [x] Suggestions row
- [x] Scroll-to-bottom button, follow output
- [ ] Conversation outline / preview mode with search & jump (TopBar button now opens Share/export)
- [~] Per-conversation system prompt: still on the native home (empty chat) only
- [~] Long-press menus: replaced by the ⋮ menu

## Input (was ChatInput)
- [x] Composer, send, stop. [ ] Long-press send (= add without answering)
- [x] Edit message inline in the web composer (banner + Cancel); no "Edit in native"
- [~] Attachments: native picker sheet (images, camera, files); web chips with remove
- [x] Voice: start / end / interrupt, live state + transcript panel
- [~] Message queue: show, remove, send queued. [ ] Edit a queued message
- [~] Model picker (native model sheet from the web model chip)
- [ ] Assistant picker, reasoning level, MCP picker, search mode / search service (were in ChatInput)
- [~] Desktop control / phone automation (web menu → native sheets); [ ] desktop-active banner actions (snap to chat, stop stream)

## Page
- [~] Native TopBar kept: back, title rename, move to folder, new chat; its outline button is now Share/export
- [x] Share / export conversation (ChatExportSheet) and per-message share
- [x] Renderer failure: error toast, no native fallback

## Entry points
- [~] Empty new chat still shows the native home (dashboard, folders, home composer); the first message switches to the web chat
- [x] Folder chats (also empty), conversation list/history, favorites, message search and notifications (scroll to the message), share intent, shortcuts, voice launch → web chat
