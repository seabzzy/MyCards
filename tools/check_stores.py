"""Диагностика автоподбора магазинов (та же логика, что в StoreFinder.autoFind)."""
import json, re, sys, time, urllib.parse, urllib.request

UA = {"User-Agent": "MyCards/1.0 (Android; personal loyalty-card wallet)"}
BROWSER = {"User-Agent": "Mozilla/5.0 (Linux; Android 16; vivo X200 Pro mini) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"}
BUSINESS = re.compile(
    "сет[ьи]|магазин|ритейл|розничн|супермаркет|гипермаркет|дискаунтер|аптек|ресторан|кафе|кофейн|"
    "заправ|азс|бренд|компани|торгов|банк|retail|chain|store|supermarket|pharmacy|restaurant|company|brand|shop", re.I)
FALLBACK = {"Пятёрочка": "5ka.ru", "Магнит": "magnit.ru", "Перекрёсток": "perekrestok.ru", "Лента": "lenta.com"}


def get(url):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=20) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(5 * (attempt + 1))
                continue
            raise
    raise RuntimeError("429 three times")


def norm(s):
    return "".join(ch for ch in s.lower().replace("ё", "е") if ch.isalnum())


def claim(qid, prop):
    d = get("https://www.wikidata.org/w/api.php?action=wbgetclaims&format=json&entity=%s&property=%s" % (qid, prop))
    arr = [c for c in d.get("claims", {}).get(prop, []) if c.get("rank") != "deprecated"]
    arr.sort(key=lambda c: 0 if c.get("rank") == "preferred" else 1)
    for c in arr:
        v = c.get("mainsnak", {}).get("datavalue", {}).get("value")
        if v:
            return v
    return None


def site_info(url):
    if not url.startswith("http"):
        url = "https://" + url
    try:
        req = urllib.request.Request(url, headers={**BROWSER, "Accept": "text/html", "Accept-Language": "ru,en;q=0.8"})
        with urllib.request.urlopen(req, timeout=20) as r:
            html = r.read(600 * 1024).decode("utf-8", "ignore")
            final = r.geturl()
    except Exception as e:
        return "site error: %s" % e
    tags = re.findall(r"<(?:meta|link)\b[^>]*>", html, re.I)
    theme = [t for t in tags if re.search(r"name=[\"']?theme-color", t, re.I)]
    icons = []
    for t in tags:
        if re.search(r"rel=[\"'][^\"']*icon", t, re.I):
            m = re.search(r"href=[\"']([^\"']+)", t)
            if m:
                icons.append(m.group(1))
    return "final=%s theme=%s icons=%s" % (final, theme[:1], icons[:4])


def s2(url):
    host = urllib.parse.urlparse(url if url.startswith("http") else "https://" + url).hostname
    try:
        req = urllib.request.Request("https://www.google.com/s2/favicons?domain=%s&sz=256" % host, headers=BROWSER)
        with urllib.request.urlopen(req, timeout=20) as r:
            b = r.read()
            ctype = r.headers.get("Content-Type")
        size = (int.from_bytes(b[16:20], "big"), int.from_bytes(b[20:24], "big")) if b[:8] == b"\x89PNG\r\n\x1a\n" else "?"
        return "google icon %s %s bytes, size %s" % (ctype, len(b), size)
    except Exception as e:
        return "google icon error: %s" % e


for q in sys.argv[1:]:
    time.sleep(2)
    print("==", q)
    try:
        d = get("https://www.wikidata.org/w/api.php?" + urllib.parse.urlencode(dict(
            action="wbsearchentities", format="json", type="item", limit=8, language="ru", uselang="ru", search=q)))
        cands = [c for c in d.get("search", []) if "значени" not in c.get("description", "").lower()]
        ranked = sorted(enumerate(cands), key=lambda t: (
            -((2 if BUSINESS.search(t[1].get("description", "")) else 0)
              + (1 if norm(t[1].get("label", "")) == norm(q) else 0)), t[0]))
        chosen, business_site = None, None
        for _, c in ranked[:4]:
            is_b = bool(BUSINESS.search(c.get("description", "")))
            if not is_b and norm(c.get("label", "")) != norm(q):
                continue
            time.sleep(0.5)
            logo = claim(c["id"], "P154")
            if logo:
                chosen = (c["id"], c.get("label"), c.get("description", ""), logo, claim(c["id"], "P856"))
                break
            elif is_b and not business_site:
                business_site = claim(c["id"], "P856")
        if chosen:
            print("   LOGO:", chosen)
            if chosen[4]:
                print("   site:", site_info(chosen[4]))
                print("   ", s2(chosen[4]))
        else:
            site = business_site or FALLBACK.get(q)
            print("   no logo in Wikidata -> site:", site)
            if site:
                print("   site:", site_info(site))
                print("   ", s2(site))
    except Exception as e:
        print("   error:", e)
