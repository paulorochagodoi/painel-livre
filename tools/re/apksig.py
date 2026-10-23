#!/usr/bin/env python3
"""Extract signer certificates from an APK Signature Scheme v2/v3 block (or v1 META-INF)."""
import struct, sys, zipfile, hashlib, subprocess

def lp(buf, off):
    n = struct.unpack_from('<I', buf, off)[0]
    return buf[off + 4: off + 4 + n], off + 4 + n

def certs_from_block(data):
    eocd = data.rfind(b'PK\x05\x06')
    cd_off = struct.unpack_from('<I', data, eocd + 16)[0]
    if data[cd_off - 16:cd_off] != b'APK Sig Block 42':
        return {}
    size = struct.unpack_from('<Q', data, cd_off - 24)[0]
    start = cd_off - size - 8
    p = start + 8
    end = cd_off - 24
    out = {}
    while p < end:
        plen = struct.unpack_from('<Q', data, p)[0]
        pid = struct.unpack_from('<I', data, p + 8)[0]
        val = data[p + 12: p + 8 + plen]
        p += 8 + plen
        if pid not in (0x7109871a, 0xf05368c0):
            continue
        signers, _ = lp(val, 0)
        q = 0
        while q < len(signers):
            signer, q = lp(signers, q)
            signed_data, _ = lp(signer, 0)
            digests, r = lp(signed_data, 0)
            certs, r = lp(signed_data, r)
            c = 0
            while c < len(certs):
                cert, c = lp(certs, c)
                out.setdefault(hex(pid), []).append(cert)
    return out

apk = sys.argv[1]
data = open(apk, 'rb').read()
res = certs_from_block(data)
z = zipfile.ZipFile(apk)
for n in z.namelist():
    if n.startswith('META-INF/') and n.upper().endswith(('.RSA', '.DSA', '.EC')):
        res.setdefault('v1:' + n, []).append(('pkcs7', z.read(n)))
for k, certs in res.items():
    for cert in certs:
        if isinstance(cert, tuple):
            pem = subprocess.run(['openssl', 'pkcs7', '-inform', 'DER', '-print_certs'], input=cert[1], capture_output=True).stdout
            print(k, pem.decode()[:600]);
            continue
        print(k, 'sha256', hashlib.sha256(cert).hexdigest(), 'sha1', hashlib.sha1(cert).hexdigest())
        txt = subprocess.run(['openssl', 'x509', '-inform', 'DER', '-noout', '-subject', '-issuer', '-dates'], input=cert, capture_output=True).stdout.decode()
        print(txt)
