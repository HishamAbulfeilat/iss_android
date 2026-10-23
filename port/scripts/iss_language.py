"""Resolve PS4 language IDs using inFAMOUS' own lang_text/audio_*.xpp names.

Only directory metadata is read. No archive is modified and no missing language
is synthesized. English is the conservative fallback for incomplete dumps.
"""
import os
import re
from pathlib import Path

# Names/ordering are embedded in Second Son's executable beside lang_%s_%s%s.xpp.
LANGUAGES = {
    'ja': (0, 'japanese'), 'en': (1, 'english'), 'fr': (2, 'french'),
    'es': (3, 'spanish'), 'de': (4, 'german'), 'it': (5, 'italian'),
    'pt_PT': (7, 'portuguese'), 'ru': (8, 'russian'), 'ko': (9, 'korean'),
    'zh_TW': (10, 'chinese'), 'zh': (11, 'chinese_s'),
    'pt_BR': (17, 'brazilian'), 'es_LA': (20, 'latino'), 'fr_CA': (22, 'quebecois'),
}
PACKAGE = re.compile(r'^lang_(text|audio)_([a-z_]+?)(_p)?\.xpp$', re.I)


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
                        if match and entry.stat(follow_symlinks=False).st_size > 0:
                            packages[match[1].lower()].add(match[2].lower())
        except OSError:
            continue
    return packages


def resolve_language(requested, packages):
    """Text AND voices must be installed; never select an incomplete locale."""
    language = LANGUAGES.get(requested)
    if language is None:
        return 1, 'en', 'unsupported language'
    language_id, token = language
    if language_id == 1:
        return 1, 'en', 'English default'
    if token in packages['text'] and token in packages['audio']:
        return language_id, requested, 'text and audio installed'
    return 1, 'en', 'requested text/audio package missing'


def configure_language(env, game):
    requested = env.get('ISS_APP_LANGUAGE', 'en')
    language_id, selected, reason = resolve_language(requested, installed_packages(game))
    # Set both names: explicit TLG_ otherwise takes precedence over ISS_ aliases.
    env['ISS_LANGUAGE'] = env['TLG_LANGUAGE'] = str(language_id)
    print(f'ISS language: requested={requested}, selected={selected}, PS4={language_id} ({reason})', flush=True)
    return selected
