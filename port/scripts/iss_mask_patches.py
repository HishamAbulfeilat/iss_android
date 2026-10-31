"""Strict CUSA00004 01.00 signature-to-image offset compiler (never edits eboot)."""
from pathlib import Path
import hashlib
import mmap
import struct
import xml.etree.ElementTree as ET

def executable(elf_path):
    elf = Path(elf_path).read_bytes()
    phoff, = struct.unpack_from('<Q', elf, 0x20)
    step, count = struct.unpack_from('<HH', elf, 0x36)
    segments = []
    for i in range(count):
        kind, flags, _, va, _, fs, ms, _ = struct.unpack_from('<IIQQQQQQ', elf, phoff+i*step)
        if kind == 1 and flags & 1 and 0 < fs <= ms:
            segments.append((va, va+fs))
    if not segments:
        raise ValueError('No executable eboot segments')
    return segments

def image_range(blob):
    if len(blob) < 112:
        raise ValueError('Incomplete G3PROBE5')
    magic, size, _, ns, nr, ni, _ = struct.unpack_from('<8s6Q', blob, 0)
    if magic != b'G3PROBE5' or not 0 < size <= 512*1024*1024 or ns > 100 or nr > 1000000 or ni > 100000:
        raise ValueError('Invalid G3PROBE5 counts')
    off = 56+8+32
    nm, = struct.unpack_from('<Q', blob, off)
    if nm > 100:
        raise ValueError('Invalid module count')
    off += 8+56*nm
    nb, = struct.unpack_from('<Q', blob, off)
    if nb > 100000:
        raise ValueError('Invalid binding count')
    off += 8+24*nb+24*ns+128*ni+32*nr
    if off+size != len(blob):
        raise ValueError('Invalid G3PROBE5 image length')
    return off, size

def mask_tokens(value):
    a = [None if x in ('??','?') else int(x,16) for x in value.split()]
    if not a or all(x is None for x in a):
        raise ValueError('Empty/unconstrained signature')
    return a

def find_mask(blob, base, size, regions, tokens):
    runs=[]
    start=None
    for i, token in enumerate(tokens+[None]):
        if token is not None and start is None: start=i
        if token is None and start is not None:
            runs.append((i-start,start))
            start=None
    length, within=max(runs)
    needle=bytes(tokens[within:within+length])
    hits=set()
    for a,b in regions:
        if a < 0 or a >= b or b > size:
            raise ValueError('Invalid executable segment range')
        first,last=base+a,base+b
        cursor=first+within
        while cursor < last:
            hit=blob.find(needle,cursor,last)
            if hit < 0: break
            cursor=hit+1
            match=hit-within
            if match < first or match+len(tokens) > last: continue
            if all(x is None or blob[match+i] == x for i,x in enumerate(tokens)):
                hits.add(match-base)
                if len(hits)>1: return hits
    return hits

def compile_masks(xml, names, version, linked_image, eboot_elf, source_eboot=None):
    if not names: return []
    root=ET.parse(xml).getroot()
    if 'CUSA00004' not in {x.text for x in root.findall('./TitleID/ID')}:
        raise ValueError('Wrong game patch catalog')
    found={m.get('Name'):m for m in root.iter('Metadata') if m.get('AppVer') == version and m.get('AppElf','eboot.bin') == 'eboot.bin'}
    for name in names:
        if name not in found: raise ValueError(f'{name}: missing v{version} patch')
    # Each experimental patch is restricted to explicitly verified binaries. The
    # regional SELF sha is checked against the original game file, not the
    # converted eboot.elf; signature uniqueness is still required below.
    verified={found[name].get('ElfSHA256') for name in names if found[name].get('ElfSHA256')}
    if verified:
        elf_digest=hashlib.sha256(Path(eboot_elf).read_bytes()).hexdigest()
        if verified!={elf_digest}:
            allowed={found[name].get('AllowedEbootSHA256') for name in names
                     if found[name].get('ElfSHA256')}
            if (not source_eboot or len(allowed)!=1 or None in allowed or
                    hashlib.sha256(Path(source_eboot).read_bytes()).hexdigest() not in allowed):
                raise ValueError('Selected render patch requires the verified inFAMOUS executable; ELF hash mismatch')
    with Path(linked_image).open('rb') as fh, mmap.mmap(fh.fileno(),0,access=mmap.ACCESS_READ) as blob:
        base,size=image_range(blob)
        executable_regions=executable(eboot_elf)
        writes=[]
        for name in names:
            pending=[]
            for line in found[name].iter('Line'):
                if line.get('Type')!='mask': raise ValueError(f'{name}: unexpected patch type')
                hits=find_mask(blob,base,size,executable_regions,mask_tokens(line.get('Address','')))
                if len(hits)!=1:
                    raise ValueError(f'{name}: signature has {len(hits)} matches, expected 1')
                target=hits.pop()+int(line.get('Offset','0'),0)
                payload=bytes.fromhex(line.get('Value','').replace(' ',''))
                if not payload or not any(a<=target and target+len(payload)<=b for a,b in executable_regions):
                    raise ValueError(f'{name}: patch outside executable region')
                pending.append((target,payload))
            if not pending: raise ValueError(f'{name}: empty patch')
            writes+=pending
        for i,(addr,value) in enumerate(writes):
            for earlier,data in writes[:i]:
                if max(addr,earlier)<min(addr+len(value),earlier+len(data)) and (addr!=earlier or value!=data):
                    raise ValueError('Overlapping mask patches')
        return list(dict(writes).items())
