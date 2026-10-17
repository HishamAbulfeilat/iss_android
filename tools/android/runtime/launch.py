"""Prepare the user's inFAMOUS dump with upstream scripts, then enter the ARM64/FEX loader."""
import os
from pathlib import Path
import subprocess
import sys
BASE=Path(__file__).resolve().parent
sys.path.insert(0,str(BASE/'scripts'))
def main():
    env=os.environ;game=Path(env['TLG_GAME_DIR']);data=Path(env['TLG_DATA_DIR']);out=data/'out'
    if not (game/'eboot.bin').is_file():raise SystemExit('No readable eboot.bin in the selected folder.')
    out.mkdir(parents=True,exist_ok=True)
    import graphics_options
    settings,_=graphics_options.prepare(env['TLG_CONFIG'],out/'graphics_state.json')
    env.update(graphics_options.environment(settings))
    # Explicit loader is also required for Python child processes: no /lib exists on Android.
    loader=str(Path(env['FXD_ROOT'])/'usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1')
    python=str(Path(env['FXD_ROOT'])/'usr/bin/python3.13')
    for script in ('prepare','link_libc','link_modules','content_profile'):
        subprocess.run([loader,python,str(BASE/'scripts'/(script+'.py')),str(game),'--out',str(out)],check=True)
    subprocess.run([loader,python,str(BASE/'scripts/patches.py'),'--out',str(out),'--extra',env.get('TLG_PATCHES',''),
                    '--game-dir',str(game),'--patches-dir',str(BASE/'patches'),'--patches-config',str(data/'patches.json')],check=True)
    args=[loader,str(BASE/'bin/iss-probe'),str(out/'boot-linked.bin'),'--content-profile',str(out/'content.bin'),
          '--patches',str(out/'patches.bin'),'--app0',str(game),'--user',str(data/'user'),'--timeout','0']
    os.execv(loader,args)
if __name__=='__main__':main()
