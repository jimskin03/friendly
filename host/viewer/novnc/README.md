# noVNC viewer assets

Production default: system package at `/usr/share/novnc` (`NOVNC_WEB_ROOT`).

Optional: vendor a noVNC tree here (must include `vnc.html`) if the OS package is unavailable.
websockify is launched as:

```text
websockify --web=$NOVNC_WEB_ROOT --heartbeat=30 127.0.0.1:$NOVNC_PORT 127.0.0.1:$VNC_PORT
```
