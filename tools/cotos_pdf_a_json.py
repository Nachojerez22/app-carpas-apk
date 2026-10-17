import re, sys, json, math
from urllib.parse import urlparse, parse_qs
html = open(sys.argv[1]).read()
links = json.load(open(sys.argv[2]))
H = 1190.4
pages = html.split('<page ')[1:]
W = re.compile(r'<word xMin="([\d.]+)" yMin="([\d.]+)" xMax="([\d.]+)" yMax="([\d.]+)">(.*?)</word>')
import html as h

def utm_to_ll(E, N, zone=30):
    a=6378137.0; f=1/298.257222101; e2=f*(2-f); k0=0.9996
    x=E-500000.0; y=N
    ep2=e2/(1-e2); M=y/k0
    mu=M/(a*(1-e2/4-3*e2**2/64-5*e2**3/256))
    e1=(1-math.sqrt(1-e2))/(1+math.sqrt(1-e2))
    p1=mu+(3*e1/2-27*e1**3/32)*math.sin(2*mu)+(21*e1**2/16-55*e1**4/32)*math.sin(4*mu)+(151*e1**3/96)*math.sin(6*mu)+(1097*e1**4/512)*math.sin(8*mu)
    N1=a/math.sqrt(1-e2*math.sin(p1)**2); T1=math.tan(p1)**2; C1=ep2*math.cos(p1)**2
    R1=a*(1-e2)/(1-e2*math.sin(p1)**2)**1.5; D=x/(N1*k0)
    lat=p1-(N1*math.tan(p1)/R1)*(D**2/2-(5+3*T1+10*C1-4*C1**2-9*ep2)*D**4/24+(61+90*T1+298*C1+45*T1**2-252*ep2-3*C1**2)*D**6/720)
    lon=(D-(1+2*T1+C1)*D**3/6+(5-2*C1+28*T1-3*C1**2+8*ep2+24*T1**2)*D**5/120)/math.cos(p1)
    lon0=math.radians((zone-1)*6-180+3)
    return math.degrees(lat), math.degrees(lon0+lon)

def lines(words, x0, x1):
    ws = sorted([w for w in words if x0 <= w[0] < x1], key=lambda w: (round(w[1]), w[0]))
    out = {}
    for w in ws:
        key = next((k for k in out if abs(k - w[1]) < 3), w[1])
        out.setdefault(key, []).append(w)
    return sorted((y, ' '.join(t[4] for t in sorted(v))) for y, v in out.items())

def blocks(ls, gap=14):
    res = []
    for y, t in ls:
        if res and y - res[-1][2] <= gap:
            res[-1] = (res[-1][0], res[-1][1] + ' ' + t, y)
        else:
            res.append((y, t, y))
    return res

rows = []
mun = coto = None
skip = {'MUNICIPIO', 'Coto', 'PLAN DEL', 'COTO', 'COTO*', 'PLAN DEL COTO'}
for pi, p in enumerate(pages):
    words = [(float(m.group(1)), float(m.group(2)), float(m.group(3)), float(m.group(4)), h.unescape(m.group(5))) for m in W.finditer(p)]
    plinks = [l for l in links if l[0] == pi + 1 and 'iberpix' in l[2]]
    mun_b = [b for b in blocks(lines(words, 95, 212)) if b[1] not in skip and len(b[1]) > 1]
    coto_b = [b for b in blocks(lines(words, 212, 335)) if b[1] not in skip and 'Coto' != b[1]]
    events = sorted([(b[0], 'm', b[1]) for b in mun_b] + [(b[0], 'c', b[1]) for b in coto_b])
    vers = sorted([w for w in words if w[4] == 'VER' and 545 < w[0] < 570], key=lambda w: w[1])
    ei = 0
    for v in vers:
        r = v[1]
        while ei < len(events) and events[ei][0] <= r + 8:
            if events[ei][1] == 'm': mun = events[ei][2]
            else: coto = events[ei][2]
            ei += 1
        masa = ' '.join(t for y, t in lines([w for w in words if abs(w[1] - r) <= 13], 405, 548))
        def cell(x0, x1):
            return ' '.join(w[4] for w in words if x0 <= w[0] < x1 and abs(w[1] - r) <= 6)
        link = min(plinks, key=lambda l: abs((H - l[1][3]) - r), default=None)
        lat = lon = None
        if link and abs((H - link[1][3]) - r) < 12:
            q = parse_qs(urlparse(link[2]).query)
            E, N = map(float, q['center'][0].split(','))
            zone = 29 if E > 500000 else 30
            lat, lon = utm_to_ll(E, N, zone)
        rows.append(dict(page=pi + 1, municipio=mun, coto=coto, masa=masa, lat=round(lat, 5) if lat else None, lon=round(lon, 5) if lon else None,
                         modelo50=cell(615, 668) == 'SÍ', asociados=cell(668, 728) or None, resto=cell(728, 800) or None))
json.dump(rows, open(sys.argv[3], 'w'), ensure_ascii=False, indent=1)
print(len(rows))
for r in rows:
    if 'Burguillos' in (r['masa'] or '') or r['page'] in (1,): print(r)
