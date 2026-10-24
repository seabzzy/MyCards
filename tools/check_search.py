"""Какие поисковики отвечают программе и что в ответе (диагностика)."""
import re, sys, time, html, urllib.parse, urllib.request

UA = "Mozilla/5.0 (Linux; Android 16; vivo X200 Pro mini) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"

def fetch(url, data=None):
    req = urllib.request.Request(url, data=data, headers={"User-Agent": UA, "Accept": "text/html", "Accept-Language": "ru-RU,ru;q=0.9"})
    with urllib.request.urlopen(req, timeout=20) as r:
        return r.geturl(), r.read(800 * 1024).decode("utf-8", "ignore")

def links_ddg(page):
    out = []
    for m in re.finditer(r'class="result__a"[^>]*href="([^"]+)"', page):
        h = html.unescape(m.group(1))
        q = urllib.parse.parse_qs(urllib.parse.urlparse(h).query).get("uddg")
        out.append(q[0] if q else h)
    return out

def links_google(page):
    out = re.findall(r'href="/url\?q=([^&"]+)', page)
    out = [urllib.parse.unquote(u) for u in out]
    out += re.findall(r'<a[^>]+href="(https?://(?!(?:www\.)?google\.)[^"]+)"', page)
    return out

def links_bing(page):
    return [html.unescape(u) for u in re.findall(r'<h2[^>]*><a[^>]+href="(https?://[^"]+)"', page)]

def links_yandex(page):
    return [html.unescape(u) for u in re.findall(r'<a[^>]+class="[^"]*(?:OrganicTitle|Link_theme_outer|organic__url)[^"]*"[^>]+href="(https?://[^"]+)"', page)] or \
           [u for u in re.findall(r'href="(https?://[^"]+)"', page) if "yandex" not in u][:10]

ENGINES = {
    "duckduckgo": (lambda q: "https://html.duckduckgo.com/html/?" + urllib.parse.urlencode({"q": q, "kl": "ru-ru"}), links_ddg),
    "google": (lambda q: "https://www.google.com/search?" + urllib.parse.urlencode({"q": q, "hl": "ru", "gl": "ru", "num": 10}), links_google),
    "bing": (lambda q: "https://www.bing.com/search?" + urllib.parse.urlencode({"q": q, "setlang": "ru", "cc": "RU", "mkt": "ru-RU"}), links_bing),
    "yandex": (lambda q: "https://yandex.ru/search/?" + urllib.parse.urlencode({"text": q, "lr": 225}), links_yandex),
}

for q in sys.argv[1:]:
    print("=====", q)
    for name, (mk, parse) in ENGINES.items():
        time.sleep(1.5)
        try:
            final, page = fetch(mk(q))
            links = parse(page)
            flag = ""
            if "captcha" in final.lower() or "showcaptcha" in page.lower(): flag += " CAPTCHA"
            if "enablejs" in page.lower() or "включите javascript" in page.lower() or "please click here if you are not redirected" in page.lower(): flag += " NEEDS_JS"
            print("  [%s] %d bytes, %d links%s" % (name, len(page), len(links), flag))
            for u in links[:6]: print("      ", u[:140])
        except Exception as e:
            print("  [%s] error: %s" % (name, e))
