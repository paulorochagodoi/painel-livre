#!/usr/bin/env python3
"""Minimal DEX table dumper: strings, classes, method refs, field refs, and
per-method string/method references (by scanning bytecode for const-string and
invoke-* instructions).  Writes a JSON index for grepping.

usage: dexdump.py <apk> <outdir>
"""
import json, os, struct, sys, zipfile

def uleb(data, p):
    r = 0; s = 0
    while True:
        b = data[p]; p += 1
        r |= (b & 0x7f) << s
        if not b & 0x80:
            return r, p
        s += 7

def mutf8(data, p):
    _, p = uleb(data, p)
    e = data.index(b'\x00', p)
    return data[p:e].decode('utf-8', 'replace')

# instruction formats -> size in 16-bit code units, indexed by opcode
FMT_SIZE = {}
def _init():
    sizes = [1]*256
    two = list(range(0x02,0x03))+[0x05,0x08,0x13,0x15,0x16,0x19,0x1a,0x1c,0x1f,0x20,0x22,0x23]+list(range(0x2d,0x32))+[0x32,0x33,0x34,0x35,0x36,0x37,0x38,0x39,0x3a,0x3b,0x3c,0x3d]+list(range(0x44,0x6e))+list(range(0x90,0xb0))+list(range(0xd0,0xe3))+[0x29]
    for o in two: sizes[o] = 2
    three = [0x03,0x06,0x09,0x14,0x17,0x1b,0x24,0x25,0x26,0x2a,0x2b,0x2c]+list(range(0x6e,0x73))+list(range(0x74,0x79))+[0xfc,0xfd]
    for o in three: sizes[o] = 3
    sizes[0x18] = 5; sizes[0xfe] = 2; sizes[0xff] = 2
    sizes[0xfa] = 4; sizes[0xfb] = 4
    sizes[0x2a] = 3; sizes[0x29] = 2; sizes[0x28] = 1
    return sizes
SIZES = _init()

def parse(data):
    (magic,) = struct.unpack_from('8s', data, 0)
    string_ids_size, string_ids_off, type_ids_size, type_ids_off, proto_ids_size, proto_ids_off, field_ids_size, field_ids_off, method_ids_size, method_ids_off, class_defs_size, class_defs_off = struct.unpack_from('<12I', data, 0x38)
    strings = [mutf8(data, struct.unpack_from('<I', data, string_ids_off + 4*i)[0]) for i in range(string_ids_size)]
    types = [strings[struct.unpack_from('<I', data, type_ids_off + 4*i)[0]] for i in range(type_ids_size)]
    protos = []
    for i in range(proto_ids_size):
        shorty, ret, params_off = struct.unpack_from('<III', data, proto_ids_off + 12*i)
        params = []
        if params_off:
            n = struct.unpack_from('<I', data, params_off)[0]
            params = [types[struct.unpack_from('<H', data, params_off + 4 + 2*j)[0]] for j in range(n)]
        protos.append((types[ret], params))
    fields = []
    for i in range(field_ids_size):
        c, t, n = struct.unpack_from('<HHI', data, field_ids_off + 8*i)
        fields.append((types[c], types[t], strings[n]))
    methods = []
    for i in range(method_ids_size):
        c, p, n = struct.unpack_from('<HHI', data, method_ids_off + 8*i)
        methods.append((types[c], strings[n], protos[p]))
    classes = []
    for i in range(class_defs_size):
        cidx, acc, sup, ifoff, srcf, annoff, cdoff, svoff = struct.unpack_from('<8I', data, class_defs_off + 32*i)
        cls = {'name': types[cidx], 'super': types[sup] if sup != 0xffffffff else None, 'interfaces': [], 'methods': []}
        if ifoff:
            n = struct.unpack_from('<I', data, ifoff)[0]
            cls['interfaces'] = [types[struct.unpack_from('<H', data, ifoff + 4 + 2*j)[0]] for j in range(n)]
        if cdoff:
            p = cdoff
            sf, p = uleb(data, p); inf, p = uleb(data, p); dm, p = uleb(data, p); vm, p = uleb(data, p)
            for _ in range(sf + inf):
                _, p = uleb(data, p); _, p = uleb(data, p)
            midx = 0
            for k in range(dm + vm):
                if k == dm: midx = 0
                d, p = uleb(data, p); midx += d
                macc, p = uleb(data, p); code_off, p = uleb(data, p)
                m = methods[midx]
                entry = {'name': m[1], 'sig': '(' + ''.join(m[2][1]) + ')' + m[2][0], 'strings': [], 'calls': [], 'fields': []}
                if code_off:
                    insns_size = struct.unpack_from('<I', data, code_off + 12)[0]
                    base = code_off + 16
                    pc = 0
                    while pc < insns_size:
                        op = data[base + 2*pc]
                        if op == 0x00:
                            ident = data[base + 2*pc + 1]
                            if ident == 0x01:
                                sz = struct.unpack_from('<H', data, base + 2*pc + 2)[0]; pc += sz*2 + 4; continue
                            if ident == 0x02:
                                sz = struct.unpack_from('<H', data, base + 2*pc + 2)[0]; pc += sz*4 + 2; continue
                            if ident == 0x03:
                                w = struct.unpack_from('<H', data, base + 2*pc + 2)[0]; sz = struct.unpack_from('<I', data, base + 2*pc + 4)[0]
                                pc += (w*sz + 1)//2 + 4; continue
                            pc += 1; continue
                        if op == 0x1a:
                            si = struct.unpack_from('<H', data, base + 2*pc + 2)[0]
                            entry['strings'].append(strings[si])
                        elif op == 0x1b:
                            si = struct.unpack_from('<I', data, base + 2*pc + 2)[0]
                            entry['strings'].append(strings[si])
                        elif 0x6e <= op <= 0x72 or 0x74 <= op <= 0x78:
                            mi = struct.unpack_from('<H', data, base + 2*pc + 2)[0]
                            mm = methods[mi]
                            entry['calls'].append(mm[0] + '->' + mm[1])
                        elif 0x52 <= op <= 0x6d:
                            fi = struct.unpack_from('<H', data, base + 2*pc + 2)[0]
                            ff = fields[fi]
                            entry['fields'].append(ff[0] + '->' + ff[2])
                        pc += SIZES[op]
                cls['methods'].append(entry)
        classes.append(cls)
    return strings, classes

if __name__ == '__main__':
    apk, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    z = zipfile.ZipFile(apk)
    allc = []
    with open(os.path.join(out, 'strings.txt'), 'w') as sf:
        for n in sorted(x for x in z.namelist() if x.endswith('.dex') and '/' not in x):
            s, c = parse(z.read(n))
            for x in s:
                sf.write(n + '\t' + x.replace('\n', '\\n') + '\n')
            for cc in c: cc['dex'] = n
            allc += c
    json.dump(allc, open(os.path.join(out, 'classes.json'), 'w'))
    with open(os.path.join(out, 'classes.txt'), 'w') as f:
        for c in allc:
            f.write(c['name'] + '\t' + str(c['super']) + '\t' + ','.join(c['interfaces']) + '\n')
    print(len(allc), 'classes')
