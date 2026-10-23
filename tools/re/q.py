#!/usr/bin/env python3
"""Query the dexdump index.
  q.py <dir> callers <regex>      methods whose calls match regex
  q.py <dir> strings <regex>      methods whose const-strings match regex
  q.py <dir> fields <regex>       methods whose field refs match regex
  q.py <dir> class <regex>        dump methods (calls/strings) of classes matching regex
  q.py <dir> sub <regex>          classes whose super/interfaces match regex
"""
import json, re, sys
d, mode, pat = sys.argv[1], sys.argv[2], re.compile(sys.argv[3])
excl = re.compile(sys.argv[4]) if len(sys.argv) > 4 else None
cls = json.load(open(d + '/classes.json'))
for c in cls:
    if excl and excl.search(c['name']):
        continue
    if mode == 'sub':
        if (c['super'] and pat.search(c['super'])) or any(pat.search(i) for i in c['interfaces']):
            print(c['name'], '<', c['super'], c['interfaces'])
        continue
    if mode == 'class':
        if not pat.fullmatch(c['name']) and not pat.search(c['name']):
            continue
        print('=== %s  extends %s  implements %s  [%s]' % (c['name'], c['super'], ','.join(c['interfaces']), c['dex']))
        for m in c['methods']:
            print('  %s%s' % (m['name'], m['sig']))
            for s in m['strings']:
                print('      S: %r' % s)
            seen = set()
            for x in m['calls']:
                if x in seen: continue
                seen.add(x)
                print('      C: %s' % x)
            fs = sorted(set(m['fields']))
            if fs and len(fs) < 30:
                print('      F: %s' % ', '.join(fs))
        continue
    for m in c['methods']:
        key = {'callers': 'calls', 'strings': 'strings', 'fields': 'fields'}[mode]
        hits = sorted(set(x for x in m[key] if pat.search(x)))
        if hits:
            print('%s->%s%s' % (c['name'], m['name'], m['sig']))
            for h in hits[:12]:
                print('      ', repr(h) if mode == 'strings' else h)
