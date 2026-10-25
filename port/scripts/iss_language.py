"""Resolve PS4 language IDs using inFAMOUS' own lang_<language>_text/audio.xpps archives (also accepting .xpp).

Only directory metadata and a bounded PlayGo metadata file are read. No archive
is modified and no missing language is synthesized. English is the conservative
fallback for incomplete dumps.
"""
import os
import re
import struct
from pathlib import Path

# Names/ordering are embedded in Second Son's executable beside lang_%s_%s%s.xpp.
LANGUAGES = {
    'ja': (0, 'japanese'), 'en': (1, 'english'), 'fr': (2, 'french'),
    'es': (3, 'spanish'), 'de': (4, 'german'), 'it': (5, 'italian'),
    'pt_PT': (7, 'portuguese'), 'ru': (8, 'russian'), 'ko': (9, 'korean'),
    'zh_TW': (10, 'chinese'), 'zh': (11, 'chinese_s'),
    'pl': (16, 'polish'), 'tr': (19, 'turkish'),
    'pt_BR': (17, 'brazilian'), 'es_LA': (20, 'latino'), 'fr_CA': (22, 'quebecois'),
}
PACKAGE = re.compile(r'^lang_([a-z_]+?)_(text|audio)(_p)?\.xpps?$', re.I)


def installed_packages(game):
    """Bounded traversal of art/, no symlink traversal or archive reads."""
    packages = {'text': set(), 'audio': set()}
    pending = [(Path(game) / 'art', 0)]
    visited = 0
    while pending:
        folder, depth = pending.pop()
        try:
            with os.scandir(folder) as entries:
                for entry in entries:
                    visited += 1
                    if visited > 100000:
                        return packages
                    if entry.is_symlink():
                        continue
                    if entry.is_dir(follow_symlinks=False) and depth < 8:
                        pending.append((Path(entry.path), depth + 1))
                    elif entry.is_file(follow_symlinks=False):
                        match = PACKAGE.fullmatch(entry.name)
                        # _p is a separate patch variant in the executable's
                        # formatter, not evidence that the base archive exists.
                        if match and not match[3] and entry.stat(follow_symlinks=False).st_size > 0:
                            packages[match[2].lower()].add(match[1].lower())
        except OSError:
            continue
    return packages


def playgo_languages(game):
    """Language-specific PlayGo chunks; layout also used by HayatoG/iss_pc.

    Common chunks (zero/all-bit masks) are not proof of a particular locale.
    Accept both observed mask byte orders, reject malformed/truncated metadata.
    """
    try:
        path=Path(game)/'sce_sys/playgo-chunk.dat'
        with path.open('rb') as stream:
            data=stream.read(8*1024*1024+1)
        if len(data)<200 or len(data)>8*1024*1024 or data[:4]!=b'plgo':
            return set()
        count=struct.unpack_from('<H',data,10)[0]
        table=struct.unpack_from('<I',data,192)[0]
        if not count or table<200 or table+count*32>len(data):
            return set()
        result=set()
        for i in range(count):
            raw=data[table+i*32+16:table+i*32+24]
            mask=int.from_bytes(raw,'little')
            if mask&0xffffffff and not mask>>32:
                mask=int.from_bytes(raw,'big')
            if mask not in (0,(1<<64)-1):
                result.update(number for number in range(30) if mask&(1<<(63-number)))
        return result
    except (OSError,ValueError,struct.error):
        return set()


def resolve_language(requested, packages, playgo_ids=None):
    """Text AND voices must be installed; never select an incomplete locale."""
    language = LANGUAGES.get(requested)
    if language is None:
        return 1, 'en', 'unsupported language'
    language_id, token = language
    if language_id == 1:
        return 1, 'en', 'English default'
    if token in packages['text'] and token in packages['audio']:
        return language_id, requested, 'text and audio installed'
    if not packages['text'] and not packages['audio'] and language_id in (playgo_ids or set()):
        return language_id, requested, 'PlayGo language chunk installed (archive layout)'
    return 1, 'en', 'requested text/audio package missing'


def configure_language(env, game):
    requested = env.get('ISS_APP_LANGUAGE', 'en')
    packages=installed_packages(game)
    language_id, selected, reason = resolve_language(requested, packages, playgo_languages(game))
    # Set both names: explicit TLG_ otherwise takes precedence over ISS_ aliases.
    env['ISS_LANGUAGE'] = env['TLG_LANGUAGE'] = str(language_id)
    print(f'ISS language: requested={requested}, selected={selected}, PS4={language_id} ({reason}); '
          f'text={sorted(packages["text"])}, audio={sorted(packages["audio"])}', flush=True)
    return selected
