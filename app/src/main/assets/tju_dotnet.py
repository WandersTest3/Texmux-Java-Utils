import os
import re
import struct
import sys
import zipfile

XABA = b"XABA"
XALZ = b"XALZ"


def lz4_block(src, size):
    out = bytearray()
    i = 0
    n = len(src)
    while len(out) < size:
        token = src[i]
        i += 1
        length = token >> 4
        if length == 15:
            while True:
                b = src[i]
                i += 1
                length += b
                if b != 255:
                    break
        out += src[i:i + length]
        i += length
        if len(out) >= size or i >= n:
            break
        offset = src[i] | (src[i + 1] << 8)
        i += 2
        match = token & 15
        if match == 15:
            while True:
                b = src[i]
                i += 1
                match += b
                if b != 255:
                    break
        match += 4
        if offset == 0 or offset > len(out):
            raise ValueError("lz4")
        start = len(out) - offset
        if offset >= match:
            out += out[start:start + match]
        else:
            for k in range(match):
                out.append(out[start + k])
    return bytes(out[:size])


def unpack_assembly(blob):
    if blob[:4] == XALZ and len(blob) > 12:
        size = struct.unpack_from("<I", blob, 8)[0]
        if size > 0 and size < 400 * 1024 * 1024:
            return lz4_block(blob[12:], size)
        return None
    return blob


def rva_to_offset(data, sections, rva):
    for va, vsize, raw, rsize in sections:
        if va <= rva < va + max(vsize, rsize):
            return rva - va + raw
    return None


CODED = {
    "ResolutionScope": ([0, 0x1A, 0x23, 1], 2),
    "TypeDefOrRef": ([2, 1, 0x1B], 2),
    "HasConstant": ([4, 8, 0x17], 2),
    "HasCustomAttribute": ([6, 4, 1, 2, 8, 9, 0xA, 0, 0xE, 0x17, 0x14, 0x11, 0x1A, 0x1B, 0x20, 0x23, 0x26, 0x27, 0x28, 0x2A, 0x2C, 0x2B], 5),
    "CustomAttributeType": ([6, 0xA], 3),
    "HasFieldMarshal": ([4, 8], 1),
    "HasDeclSecurity": ([2, 6, 0x20], 2),
    "MemberRefParent": ([2, 1, 0x1A, 6, 0x1B], 3),
    "HasSemantics": ([0x14, 0x17], 1),
    "MethodDefOrRef": ([6, 0xA], 1),
    "MemberForwarded": ([4, 6], 1),
}


def assembly_name(data):
    try:
        if data[:2] != b"MZ":
            return None
        pe = struct.unpack_from("<I", data, 0x3C)[0]
        if data[pe:pe + 4] != b"PE\0\0":
            return None
        nsec = struct.unpack_from("<H", data, pe + 6)[0]
        optsize = struct.unpack_from("<H", data, pe + 20)[0]
        opt = pe + 24
        magic = struct.unpack_from("<H", data, opt)[0]
        dd = opt + (0x60 if magic == 0x10B else 0x70)
        clr_rva = struct.unpack_from("<I", data, dd + 14 * 8)[0]
        if clr_rva == 0:
            return None
        sections = []
        sp = opt + optsize
        for k in range(nsec):
            o = sp + 40 * k
            vsize, va, rsize, raw = struct.unpack_from("<IIII", data, o + 8)
            sections.append((va, vsize, raw, rsize))
        clr = rva_to_offset(data, sections, clr_rva)
        md_rva = struct.unpack_from("<I", data, clr + 8)[0]
        md = rva_to_offset(data, sections, md_rva)
        if struct.unpack_from("<I", data, md)[0] != 0x424A5342:
            return None
        vlen = struct.unpack_from("<I", data, md + 12)[0]
        p = md + 16 + vlen
        nstreams = struct.unpack_from("<H", data, p + 2)[0]
        p += 4
        streams = {}
        for _ in range(nstreams):
            off, size = struct.unpack_from("<II", data, p)
            q = p + 8
            end = data.index(b"\0", q)
            name = data[q:end].decode("ascii")
            streams[name] = (md + off, size)
            p = q + ((end - q + 1 + 3) & ~3)
        tables = streams.get("#~") or streams.get("#-")
        strings = streams.get("#Strings")
        if not tables or not strings:
            return None
        t = tables[0]
        heap = data[t + 6]
        valid = struct.unpack_from("<Q", data, t + 8)[0]
        p = t + 24
        rows = {}
        for bit in range(64):
            if valid >> bit & 1:
                rows[bit] = struct.unpack_from("<I", data, p)[0]
                p += 4
        S = 4 if heap & 1 else 2
        G = 4 if heap & 2 else 2
        B = 4 if heap & 4 else 2

        def idx(table):
            return 4 if rows.get(table, 0) >= 65536 else 2

        def coded(name):
            tabs, bits = CODED[name]
            top = max(rows.get(x, 0) for x in tabs)
            return 4 if top >= (1 << (16 - bits)) else 2

        sizes = {
            0x00: 2 + S + G * 3,
            0x01: coded("ResolutionScope") + S + S,
            0x02: 4 + S + S + coded("TypeDefOrRef") + idx(4) + idx(6),
            0x03: idx(4),
            0x04: 2 + S + B,
            0x05: idx(6),
            0x06: 4 + 2 + 2 + S + B + idx(8),
            0x07: idx(8),
            0x08: 2 + 2 + S,
            0x09: idx(2) + coded("TypeDefOrRef"),
            0x0A: coded("MemberRefParent") + S + B,
            0x0B: 1 + 1 + coded("HasConstant") + B,
            0x0C: coded("HasCustomAttribute") + coded("CustomAttributeType") + B,
            0x0D: coded("HasFieldMarshal") + B,
            0x0E: 2 + coded("HasDeclSecurity") + B,
            0x0F: 2 + 4 + idx(2),
            0x10: 4 + idx(4),
            0x11: B,
            0x12: idx(2) + idx(0x14),
            0x13: idx(0x14),
            0x14: 2 + S + coded("TypeDefOrRef"),
            0x15: idx(2) + idx(0x17),
            0x16: idx(0x17),
            0x17: 2 + S + B,
            0x18: 2 + idx(6) + coded("HasSemantics"),
            0x19: idx(2) + coded("MethodDefOrRef") * 2,
            0x1A: S,
            0x1B: B,
            0x1C: 2 + coded("MemberForwarded") + S + idx(0x1A),
            0x1D: 4 + idx(4),
            0x1E: 8,
            0x1F: 4,
        }
        pos = p
        for table in range(0x20):
            pos += rows.get(table, 0) * sizes[table]
        if rows.get(0x20, 0) == 0:
            return None
        name_off = pos + 4 + 8 + 4 + B
        if S == 4:
            sidx = struct.unpack_from("<I", data, name_off)[0]
        else:
            sidx = struct.unpack_from("<H", data, name_off)[0]
        start = strings[0] + sidx
        end = data.index(b"\0", start)
        name = data[start:end].decode("utf-8", "replace")
        return name or None
    except Exception:
        return None


def parse_store(blob):
    entries = []
    if blob[:4] != XABA or len(blob) < 24:
        return entries
    version, local, glob, extra = struct.unpack_from("<IIII", blob, 4)
    major = version & 0xFFFF
    if major == 1:
        start, stride, shift = 20, 24, 0
    else:
        start, stride, shift = 20 + extra, 28, 1
    for k in range(local):
        o = start + stride * k
        if o + stride > len(blob):
            break
        vals = struct.unpack_from("<%dI" % (stride // 4), blob, o)
        off, size = vals[shift], vals[shift + 1]
        if size == 0 or off + size > len(blob):
            continue
        entries.append(blob[off:off + size])
    return entries


def scan_store(blob):
    out = []
    for m in re.finditer(XALZ, blob):
        p = m.start()
        if p + 12 > len(blob):
            continue
        size = struct.unpack_from("<I", blob, p + 8)[0]
        if size == 0 or size > 400 * 1024 * 1024:
            continue
        try:
            out.append(lz4_block(blob[p + 12:], size))
        except Exception:
            continue
    return out


def store_assemblies(raw):
    pos = raw.find(XABA)
    if pos < 0:
        return []
    blob = raw[pos:]
    found = []
    for entry in parse_store(blob):
        try:
            data = unpack_assembly(entry)
        except Exception:
            data = None
        if data and data[:2] == b"MZ":
            found.append(data)
    if found:
        return found
    return [d for d in scan_store(blob) if d[:2] == b"MZ"]


def collect(apk, report):
    result = []
    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
        lower = [n.lower() for n in names]
        if any(n.endswith("libil2cpp.so") for n in lower):
            report.append("IL2CPP")
        for name in names:
            low = name.lower()
            base = low.rsplit("/", 1)[-1]
            if low.startswith("assemblies/") and low.endswith(".dll"):
                try:
                    data = unpack_assembly(z.read(name))
                except Exception:
                    data = None
                if data and data[:2] == b"MZ":
                    result.append((name.rsplit("/", 1)[-1][:-4], data))
            elif "assembl" in low and (low.endswith(".blob") or (low.startswith("lib/") and base.endswith(".so"))):
                raw = z.read(name)
                if XABA in raw[:65536] or raw.find(XABA) >= 0:
                    for data in store_assemblies(raw):
                        result.append((None, data))
            elif "/managed/" in low and low.endswith(".dll"):
                result.append((name.rsplit("/", 1)[-1][:-4], z.read(name)))
    return result


def main():
    if len(sys.argv) < 3:
        print("uso: tju_dotnet.py <pasta-saida> <apk>...")
        return 2
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    used = {}
    total = 0
    report = []
    for apk in sys.argv[2:]:
        try:
            items = collect(apk, report)
        except Exception as e:
            print("Falha ao ler %s: %s" % (os.path.basename(apk), e))
            continue
        for hint, data in items:
            name = assembly_name(data) or hint or "assembly_%03d" % total
            name = re.sub(r"[^A-Za-z0-9._-]", "_", name)
            if name in used:
                used[name] += 1
                name = "%s_%d" % (name, used[name])
            else:
                used[name] = 0
            with open(os.path.join(out, name + ".dll"), "wb") as f:
                f.write(data)
            total += 1
    if "IL2CPP" in report:
        print("IL2CPP: o código C# original não está no APK; só dá para extrair a estrutura com Il2CppDumper.")
    print("Assemblies extraídos: %d" % total)
    return 0 if total > 0 else 3


if __name__ == "__main__":
    sys.exit(main())
