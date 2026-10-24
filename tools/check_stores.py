"""Диагностика автоподбора магазинов (та же логика, что в StoreFinder.autoFind)."""
import json, re, sys, urllib.parse, urllib.request

UA = {"User-Agent": "MyCards/1.0 (Android; personal loyalty-card wallet)"}
BUSINESS = re.compile(
    "сет[ьи]|магазин|ритейл|розничн|супермаркет|гипермаркет|дискаунтер|аптек|ресторан|кафе|кофейн|"
    "заправ|азс|бренд|компани|торгов|банк|retail|chain|store|supermarket|pharmacy|restaurant|company|brand|shop", re.I)

def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=20) as r:
        return json.load(r)

def norm(s): return "".join(ch for ch in s.lower().replace("ё", "е") if ch.isalnum())

def claim(qid, prop):
    d = get("https://www.wikidata.org/w/api.php?action=wbgetclaims&format=json&entity=%s&property=%s" % (qid, prop))
    arr = [c for c in d.get("claims", {}).get(prop, []) if c.get("rank") != "deprecated"]
    arr.sort(key=lambda c: 0 if c.get("rank") == "preferred" else 1)
    for c in arr:
        v = c.get("mainsnak", {}).get("datavalue", {}).get("value")
        if v: return v
    return None

for q in sys.argv[1:]:
    print("==", q)
    d = get("https://www.wikidata.org/w/api.php?" + urllib.parse.urlencode(dict(
        action="wbsearchentities", format="json", type="item", limit=8, language="ru", uselang="ru", search=q)))
    cands = [c for c in d.get("search", []) if "значени" not in c.get("description", "").lower()]
    for c in cands:
        print("   cand", c["id"], c.get("label"), "|", c.get("description", ""))
    ranked = sorted(enumerate(cands), key=lambda t: (-((2 if BUSINESS.search(t[1].get("description", "")) else 0)
                                                        + (1 if norm(t[1].get("label", "")) == norm(q) else 0)), t[0]))
    chosen = None
    for _, c in ranked[:5]:
        if not BUSINESS.search(c.get("description", "")) and norm(c.get("label", "")) != norm(q):
            continue
        logo = claim(c["id"], "P154")
        if not logo:
            continue
        chosen = (c["id"], c.get("label"), c.get("description", ""), logo, claim(c["id"], "P856"))
        break
    print("   CHOSEN:", chosen)
