"""Check focused input during snapshot updates and keyboard-sized viewport changes.

Requires Playwright. This exercises browser layout, not Android IME insets.
"""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread
import os

from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[3]
server = ThreadingHTTPServer(('127.0.0.1', 0), partial(
    SimpleHTTPRequestHandler, directory=str(root / 'app/src/main/assets/openui')))
Thread(target=server.serve_forever, daemon=True).start()
snapshot = dict(protocolVersion=1, sessionId='composer-check', revision=1,
                conversationId='composer-check', title='Keyboard check', loading=False,
                darkMode=True, draft='', modelAvailable=True,
                suggestions=['Yes', 'Use demo', 'Demo data', 'Go ahead'],
                messages=[dict(id='answer', role='assistant', text='A long reply.\n\n' * 80)])
try:
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=os.environ.get('PLAYWRIGHT_CHROMIUM_EXECUTABLE'),
                                    args=['--no-sandbox'])
        page = browser.new_page(viewport={'width': 393, 'height': 780})
        page.add_init_script('''window.actions=[];
            window.FriendlyOpenUI={postMessage:p=>window.actions.push(JSON.parse(p))};
            window.ancestorScrolls=0;
            Element.prototype.scrollIntoView=function(){window.ancestorScrolls++};''')
        page.goto(f'http://127.0.0.1:{server.server_port}')
        page.wait_for_function('!!window.friendlyOpenUI')
        page.evaluate('(s)=>window.friendlyOpenUI.pushSnapshot(JSON.stringify(s))', snapshot)
        textbox = page.get_by_role('textbox', name='Message Friendly')
        textbox.focus()
        for width, height in [(393, 420), (320, 300), (780, 260), (393, 780)]:
            page.set_viewport_size({'width': width, 'height': height})
            textbox.fill('I can see the text I am typing.\nAnd a second line.')
            snapshot['revision'] += 1
            # Native state can lag behind keystrokes; the local input must survive.
            page.evaluate('(s)=>window.friendlyOpenUI.pushSnapshot(JSON.stringify(s))', snapshot)
            page.wait_for_timeout(300)
            assert textbox.input_value().endswith('And a second line.')
            assert textbox.evaluate('(e)=>document.activeElement===e')
            for selector in ['.composer', '.composer textarea', '.suggestions']:
                box = page.locator(selector).bounding_box()
                assert box and box['y'] >= 0 and box['y'] + box['height'] <= height + 1, (selector, box)
            assert page.evaluate('window.scrollY===0 && window.ancestorScrolls===0')
        page.set_viewport_size({'width': 393, 'height': 420})
        out = root / 'docs/issue-19'
        out.mkdir(parents=True, exist_ok=True)
        page.screenshot(path=str(out / 'composer-keyboard-viewport.png'))
        page.get_by_role('button', name='Send message (hold to add without reply)', exact=True).click()
        assert any(a.get('type') == 'send' and 'second line' in a.get('text', '')
                   for a in page.evaluate('window.actions'))
        assert textbox.input_value() == ''
        browser.close()
        print('PASS: focused multiline draft, stale native drafts, keyboard-sized viewports, suggestions, scoped scrolling and send')
finally:
    server.shutdown()
