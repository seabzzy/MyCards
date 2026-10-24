"""Зеркало StoreFinder.webHits: какие сайты выберет приложение."""
import base64, re, sys, time, html, urllib.parse, urllib.request
MOBILE = "Mozilla/5.0 (Linux; Android 16; vivo X200 Pro mini) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
AGG = ["wikipedia.org","wikidata.org","wikimedia.org","yandex.","ya.ru","google.","bing.com","duckduckgo.com","mail.ru","2gis.","zoon.","flamp.","otzovik.","irecommend.","spr.ru","magazinnoff.","shopandmall.","tcshops.","find-co.","adresatelefony.","firstmagazin.","rusprofile.","list-org.","checko.","sbis.ru","hh.ru","avito.","ozon.","wildberries.","youtube.","rutube.","dzen.ru","pikabu.","orgpage.","yell.ru","ok.ru","t.me","telegram.","instagram.","facebook.","twitter.","tripadvisor.","edadeal.","skidkaonline.","proskidku.","katalog","spravker.","cataloxy.","kartaslov.","infobel.","companies.","zachestnyibiznes.","vc.ru","habr.","rbc.ru","kommersant.","ria.ru","tass.ru","retail.ru","new-retail.","sravni."]
def get(url):
    ua = DESKTOP if "bing.com" in url else MOBILE
    req = urllib.request.Request(url, headers={"User-Agent": ua, "Accept": "text/html", "Accept-Language": "ru,en;q=0.8"})
    with urllib.request.urlopen(req, timeout=20) as r: return r.read(900*1024).decode("utf-8","ignore")
def ddg(page):
    out=[]
    for h in re.findall(r'href="([^"]*uddg=[^"]+)"', page):
        h=html.unescape(h); m=re.search(r'[?&]uddg=([^&]+)', h)
        if m: out.append(urllib.parse.unquote(m.group(1)))
    return list(dict.fromkeys(out))
def bing(page):
    out=[]
    for h in re.findall(r'<li class="b_algo"[^>]*>.*?<a[^>]+href="(https?://[^"]+)"', page, re.S):
        h=html.unescape(h)
        if "bing.com/ck/a" in h:
            m=re.search(r'[?&]u=a1([^&]+)', h)
            if not m: continue
            e=m.group(1); e+= "="*(-len(e)%4)
            try: h=base64.urlsafe_b64decode(e).decode()
            except Exception: continue
        out.append(h)
    return list(dict.fromkeys(out))
def hits(links):
    sites, vk = [], []
    for l in links:
        host=(urllib.parse.urlparse(l).hostname or "").lower().removeprefix("www.")
        if host in ("vk.com","m.vk.com","vk.ru"): vk.append(l.split("?")[0]); continue
        if any(a in host for a in AGG): continue
        if "https://"+host not in sites: sites.append("https://"+host)
    return sites, vk
TR = dict(zip("абвгдеёжзийклмнопрстуфхцчшщъыьэюя", ["a","b","v","g","d","e","e","zh","z","i","y","k","l","m","n","o","p","r","s","t","u","f","h","ts","ch","sh","sch","","y","","e","yu","ya"]))
def translit(s): return "".join(TR.get(c, c if (c.isascii() and c.isalnum()) else "") for c in s.lower())
def norm(s): return "".join(c for c in s.lower().replace("ё","е") if c.isalnum())
def looks(site, q):
    host = urllib.parse.urlparse(site).hostname or ""
    lat = translit(q)
    if len(lat) >= 4 and lat[:6] in host: return "host"
    try: page = get(site)
    except Exception as e: return False
    t = re.search(r"<title[^>]*>(.*?)</title>", page, re.I | re.S); t = t.group(1) if t else ""
    sn = re.search(r"property=[\"']og:site_name[\"'][^>]+content=[\"']([^\"']+)", page); sn = sn.group(1) if sn else ""
    return ("title:" + html.unescape(t).strip()[:60]) if norm(q) in norm(html.unescape(t + " " + sn)) else False
for name in sys.argv[1:]:
    q=urllib.parse.quote(name+" магазин официальный сайт")
    print("==", name)
    for eng, url, parse in [("ddg","https://html.duckduckgo.com/html/?q=%s&kl=ru-ru"%q,ddg),("ddg-lite","https://lite.duckduckgo.com/lite/?q=%s&kl=ru-ru"%q,ddg),("bing","https://www.bing.com/search?q=%s&setlang=ru&cc=RU"%q,bing)]:
        time.sleep(4)
        try:
            links=parse(get(url)); s,v=hits(links)
            print("  [%s] links=%d sites=%s vk=%s" % (eng,len(links),s[:4],v[:2]))
            if eng == "ddg":
                for site in s[:4]:
                    r = looks(site, name); print("      check", site, "->", r)
                    if r: print("      CHOSEN:", site); break
        except Exception as e: print("  [%s] error %s"%(eng,e))
