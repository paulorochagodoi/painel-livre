#!/usr/bin/env python3
"""Minimal Android binary XML (AXML) decoder -> text XML."""
import struct, sys, zipfile

def read_string_pool(data, off):
    hdr_type, hdr_size, chunk_size = struct.unpack_from('<HHI', data, off)
    string_count, style_count, flags, strings_start, styles_start = struct.unpack_from('<IIIII', data, off + 8)
    utf8 = bool(flags & (1 << 8))
    offsets = struct.unpack_from('<%dI' % string_count, data, off + hdr_size)
    base = off + strings_start
    out = []
    for o in offsets:
        p = base + o
        if utf8:
            n = data[p]; p += 1
            if n & 0x80: p += 1
            n = data[p]; p += 1
            if n & 0x80:
                n = ((n & 0x7f) << 8) | data[p]; p += 1
            out.append(data[p:p + n].decode('utf-8', 'replace'))
        else:
            n = struct.unpack_from('<H', data, p)[0]; p += 2
            if n & 0x8000:
                n = ((n & 0x7fff) << 16) | struct.unpack_from('<H', data, p)[0]; p += 2
            out.append(data[p:p + n * 2].decode('utf-16-le', 'replace'))
    return out, off + chunk_size

def fmt_value(vtype, vdata, raw, strings):
    if raw != 0xffffffff and raw < len(strings):
        return strings[raw]
    if vtype == 0x03:
        return strings[vdata] if vdata < len(strings) else '?'
    if vtype == 0x01:
        return '@0x%08x' % vdata
    if vtype == 0x02:
        return '?0x%08x' % vdata
    if vtype == 0x12:
        return 'true' if vdata else 'false'
    if vtype in (0x10,):
        return str(struct.unpack('<i', struct.pack('<I', vdata))[0])
    if vtype == 0x11:
        return '0x%x' % vdata
    if vtype == 0x04:
        return str(struct.unpack('<f', struct.pack('<I', vdata))[0])
    return '(t%d)0x%x' % (vtype, vdata)

def decode(data):
    off = 8
    strings = []
    ns = {}
    out = []
    depth = 0
    while off < len(data):
        ctype, hsize, csize = struct.unpack_from('<HHI', data, off)
        if ctype == 0x0001:
            strings, _ = read_string_pool(data, off)
        elif ctype == 0x0100:
            prefix, uri = struct.unpack_from('<II', data, off + 16)
            ns[strings[uri]] = strings[prefix]
        elif ctype == 0x0102:
            ns_i, name_i, attr_start, attr_size, attr_count = struct.unpack_from('<IIHHH', data, off + 16)
            name = strings[name_i]
            attrs = []
            p = off + 16 + attr_start
            for i in range(attr_count):
                a_ns, a_name, a_raw, a_size, _, a_type, a_data = struct.unpack_from('<IIIHBBI', data, p)
                p += attr_size
                an = strings[a_name] if a_name < len(strings) else '?'
                if a_ns != 0xffffffff and strings[a_ns] in ns:
                    an = ns[strings[a_ns]] + ':' + an
                attrs.append('%s="%s"' % (an, fmt_value(a_type, a_data, a_raw, strings)))
            out.append('  ' * depth + '<' + name + ('' if not attrs else ' ' + ' '.join(attrs)) + '>')
            depth += 1
        elif ctype == 0x0103:
            depth -= 1
            name_i = struct.unpack_from('<I', data, off + 20)[0]
            out.append('  ' * depth + '</' + strings[name_i] + '>')
        if csize == 0:
            break
        off += csize
    return '\n'.join(out)

if __name__ == '__main__':
    z = zipfile.ZipFile(sys.argv[1])
    member = sys.argv[2] if len(sys.argv) > 2 else 'AndroidManifest.xml'
    print(decode(z.read(member)))
