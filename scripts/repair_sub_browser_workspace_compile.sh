#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

printf '%s\n' '== Sub Browser: workspace compile repair =='

WEB="app/src/main/java/com/subbrowser/browser/web/BrowserWebView.kt"
UI="app/src/main/java/com/subbrowser/ui/browser/BrowserWorkspace.kt"

test -f "$WEB"
test -f "$UI"

python3 - "$WEB" <<'PY'
from pathlib import Path
import sys

p = Path(sys.argv[1])
s = p.read_text()

s = s.replace(
    "import android.webkit.WebViewClient\n",
    "import android.webkit.WebChromeClient\nimport android.webkit.WebViewClient\n",
)

old = """        override fun onReceivedTitle(view: WebView, title: String) {
            controller.onTitleChanged(title)
        }
"""
s = s.replace(old, "")

needle = """    webView.webViewClient = object : WebViewClient() {
"""
insert = """    webView.webChromeClient = object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView, title: String) {
            controller.onTitleChanged(title)
        }
    }

"""
if "webView.webChromeClient = object : WebChromeClient()" not in s:
    s = s.replace(needle, insert + needle, 1)

p.write_text(s)
PY

python3 - "$UI" <<'PY'
from pathlib import Path
import sys

p = Path(sys.argv[1])
s = p.read_text()
s = s.replace("import androidx.compose.foundation.text.input.clearText\n", "")
s = s.replace(
    "commandState.clearText()",
    'commandState.edit { replace(0, length, "") }',
)
p.write_text(s)
PY

grep -q 'WebChromeClient' "$WEB"
grep -q 'webView.webChromeClient' "$WEB"
grep -q 'commandState.edit { replace(0, length, "") }' "$UI"
grep -q 'onReceivedTitle' "$WEB"

git diff --check

printf '%s\n' 'SUB_BROWSER_WORKSPACE_COMPILE_REPAIR_READY'
