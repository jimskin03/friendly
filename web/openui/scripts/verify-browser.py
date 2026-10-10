from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread
import os
from pathlib import Path
from playwright.sync_api import sync_playwright

out = Path(__file__).resolve().parents[3] / 'docs' / 'issue-14'
out.mkdir(parents=True, exist_ok=True)
chart = '''root = Card([header, note, chart, followups])
header = CardHeader("Gold price", "Monthly trend · USD per troy ounce")
note = TextContent("**Illustrative data** for this UI preview. These are not live market prices.")
chart = LineChart(months, [Series("Gold", prices)], "natural", "Month", "USD/oz", 220)
months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun"]
prices = [2600, 2700, 2650, 2900, 3100, 3050]
followups = FollowUpBlock([FollowUpItem("Compare months"), FollowUpItem("Explain the trend")])'''
steps = '''root = Card([header, intro, steps, followups])
header = CardHeader("Create a GitHub token", "A step-by-step guide")
intro = TextContent("Create a **Personal Access Token (classic)** from your GitHub account settings.")
steps = Steps([StepsItem("Open Settings", "Tap your profile photo and choose Settings."), StepsItem("Developer settings", "Open Personal access tokens, then Tokens (classic)."), StepsItem("Configure your token", "Choose a descriptive name, an expiration, and only the scopes you need.")])
followups = FollowUpBlock([FollowUpItem("Explain token scopes"), FollowUpItem("Show a safer alternative")])'''
base = dict(protocolVersion=1, sessionId='preview', revision=1, conversationId='preview', title='Test Chat', loading=False, darkMode=True, draft='', attachmentCount=0, modelAvailable=True, capabilities=dict(edition='nightly',phoneAutomation=True,desktopControl=True,contentReporting=False,openUiActions=True), suggestions=[])
# Native toolbar is drawn by Compose. This matching HTML is only a review mockup.
header = '''<div id="native-preview"><span>‹</span><div><b><i></i>Default Assistant</b><small>Test Chat · openrouter/free</small></div><svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M3 7V5a2 2 0 0 1 2-2h5l3 4h6a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z"/></svg><svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M7 5h14M7 12h14M7 19h14M2 5h1M2 12h1M2 19h1"/></svg><span>＋</span></div>'''
header_css = '''#native-preview {position:fixed;inset:0 0 auto;height:64px;display:flex;align-items:center;gap:14px;padding:0 16px;background:var(--bg);color:var(--text);border-bottom:1px solid var(--line);font:16px system-ui;z-index:10} #native-preview>div{flex:1;min-width:0} #native-preview b{display:block;white-space:nowrap;font-size:15px;font-weight:500} #native-preview small{display:block;font-size:11px;color:var(--muted);margin-top:5px} #native-preview i{display:inline-block;width:7px;height:7px;background:var(--accent);border-radius:50%;margin-right:7px} #native-preview>span{font-size:24px} .app{padding-top:64px}'''
server = ThreadingHTTPServer(('127.0.0.1', 0), partial(SimpleHTTPRequestHandler, directory=str(out.parents[1] / 'app/src/main/assets/openui')))
Thread(target=server.serve_forever, daemon=True).start()
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ.get('PLAYWRIGHT_CHROMIUM_EXECUTABLE'), headless=True, args=['--no-sandbox'])
    page = browser.new_page(viewport={'width':393,'height':852},device_scale_factor=2)
    errors=[]
    page.on('pageerror',lambda e:errors.append(str(e)))
    page.add_init_script('window.actions=[]; window.FriendlyOpenUI={postMessage:p=>window.actions.push(JSON.parse(p))}')
    page.goto(f'http://127.0.0.1:{server.server_port}')
    page.wait_for_function('!!window.friendlyOpenUI')
    def push(program, user='Show a gold price graph', **kwargs):
        snapshot={**base, **kwargs, 'messages':[dict(id='user',role='user',text=user,canEdit=True),dict(id='assistant',role='assistant',text=program,canRegenerate=not kwargs.get('loading',False))]}
        page.evaluate('(s)=>window.friendlyOpenUI.pushSnapshot(JSON.stringify(s))',snapshot)
    push(chart)
    page.get_by_text('Compare months',exact=True).wait_for()
    page.wait_for_timeout(500)
    assert page.locator('.rich-response svg').count()>0, 'Chart SVG missing'
    assert not page.get_by_text('could not be displayed',exact=False).count(), 'Valid chart rejected'
    page.get_by_text('Compare months',exact=True).click()
    assert any(a.get('type')=='send' and a.get('text')=='Compare months' for a in page.evaluate('window.actions'))
    page.get_by_role('textbox').fill('Test message')
    page.get_by_role('button',name='Send message',exact=True).click()
    assert any(a.get('type')=='send' and a.get('text')=='Test message' for a in page.evaluate('window.actions'))
    page.evaluate('(html)=>document.querySelector(".app").insertAdjacentHTML("afterbegin",html)',header)
    page.add_style_tag(content=header_css)
    page.wait_for_timeout(700)
    page.locator('.conversation').evaluate('(e)=>e.scrollTo({top:0,behavior:"instant"})')
    page.wait_for_timeout(100)
    page.screenshot(path=str(out/'chart-dark-mobile.png'))
    push(steps,'Help me create a GitHub token',revision=2)
    page.get_by_text('Configure your token',exact=True).wait_for()
    page.wait_for_timeout(700)
    page.locator('.conversation').evaluate('(e)=>e.scrollTo({top:0,behavior:"instant"})')
    page.wait_for_timeout(100)
    page.screenshot(path=str(out/'steps-dark-mobile.png'))
    push(chart,revision=3,darkMode=False)
    page.wait_for_timeout(700)
    page.locator('.conversation').evaluate('(e)=>e.scrollTo({top:0,behavior:"instant"})')
    page.wait_for_timeout(100)
    page.screenshot(path=str(out/'chart-light-mobile.png'))
    page.set_viewport_size({'width':1280,'height':900})
    page.screenshot(path=str(out/'chart-light-desktop.png'))
    for width in [320,393,768,1280]:
        page.set_viewport_size({'width':width,'height':852})
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), f'Overflow at {width}'
        box=page.locator('.composer').bounding_box()
        assert box['y']+box['height'] <= 853, f'Composer offscreen at {width}'
    form = 'root = Card([Form("preferences", Buttons([Button("Save preferences", Action([@ToAssistant("Save preferences")]), "primary")]), [FormControl("Period", Input("period", "Your period", "text", { required: true }))])])'
    push(form, revision=4)
    page.get_by_placeholder('Your period').fill('One year')
    page.get_by_role('button',name='Save preferences',exact=True).click()
    assert any(a.get('type')=='send' and 'One year' in a.get('text','') for a in page.evaluate('window.actions'))
    push('root = Card([TextContent("[GitHub](https://github.com)")])',revision=5)
    page.get_by_role('link',name='GitHub',exact=True).click()
    assert any(a.get('type')=='link' and a.get('url')=='https://github.com/' for a in page.evaluate('window.actions'))
    push('root = Card([NotAComponent("broken")])', revision=6)
    page.get_by_text('This rich response could not be displayed.',exact=False).wait_for()
    push(chart,revision=7,loading=True)
    page.get_by_role('button',name='Stop generation',exact=True).click()
    assert any(a.get('type')=='stop' for a in page.evaluate('window.actions'))
    assert not errors, errors
    print('PASS: charts, steps, forms, links, follow-up/send/stop bridges, invalid-response fallback, 320–1280px layout; screenshots saved')
    browser.close()

server.shutdown()
