"""Synthetic G3PROBE5 / real Bachata XML smoke tests."""
from pathlib import Path
import sys, struct, tempfile, unittest, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'port/scripts'))
from iss_mask_patches import compile_masks,mask_tokens
from patches import game_profile,selected_patches,profile_version_matches
from unittest.mock import patch
XML=ROOT/'port/patches/InfamousSecondSon.xml'

def fixture(folder,patterns):
    code=bytearray(b'\xcc'*40)
    for item in patterns:
        code.extend(b'\xcc'*19)
        code.extend(bytes(0x90 if x is None else x for x in mask_tokens(item)))
    code.extend(b'\xcc'*40)
    elf=bytearray(512)
    struct.pack_into('<Q',elf,0x20,64)
    struct.pack_into('<HH',elf,0x36,56,1)
    struct.pack_into('<IIQQQQQQ',elf,64,1,5,0,0,0,len(code),len(code),4096)
    (folder/'eboot.elf').write_bytes(elf)
    hdr=struct.pack('<8s6Q',b'G3PROBE5',len(code),0,1,0,0,0)
    hdr+=struct.pack('<Q',0)+struct.pack('<4Q',0,0,0,0)
    hdr+=struct.pack('<Q',0)+struct.pack('<Q',0)+struct.pack('<3Q',0,len(code),5)
    (folder/'boot-linked.bin').write_bytes(hdr+code)

class BachataPatchTests(unittest.TestCase):
    def test_catalog_and_conflicts(self):
        self.assertEqual(game_profile('CUSA00004')[2],'01.00')
        allpatch=ET.parse(XML).getroot().findall('Metadata')
        self.assertEqual(len(allpatch),5)
        self.assertTrue(all(x.get('AppVer')=='01.00' for x in allpatch))
        self.assertEqual(selected_patches(XML,'01.00',''),[])
        with self.assertRaisesRegex(ValueError,'choose one resolution'):
            selected_patches(XML,'01.00','Render resolution 1280x720;Render resolution 1600x900')
    def test_regions_and_verified_executable_version_gate(self):
        expected=game_profile('CUSA00004')
        for title in ('CUSA00223','CUSA00263'):
            self.assertEqual(game_profile(title),expected)
        self.assertIsNone(game_profile('CUSA01624'))
        with patch('patches.game_app_version',return_value='01.07'),patch('patches.eboot_sha256',return_value='2d1ca79630d7bbe6fa29575f59aa7996d43071d74ac8e4f7d40137967d9039ab'):
            self.assertTrue(profile_version_matches('game',expected))
        with patch('patches.game_app_version',return_value='01.07'),patch('patches.eboot_sha256',return_value='different executable'):
            self.assertFalse(profile_version_matches('game',expected))
    def test_compile_all_five_synthetic_signatures(self):
        for item in ET.parse(XML).getroot().findall('Metadata'):
            sigs=[x.get('Address') for x in item.findall('.//Line')]
            with self.subTest(item.get('Name')),tempfile.TemporaryDirectory() as d:
                path=Path(d);fixture(path,sigs)
                writes=compile_masks(XML,[item.get('Name')],'01.00',path/'boot-linked.bin',path/'eboot.elf')
                self.assertEqual(len(writes),len(sigs))
    def test_fail_closed_when_missing_ambiguous_or_wrong_version(self):
        item=ET.parse(XML).getroot().findall('Metadata')[2]
        sigs=[x.get('Address') for x in item.findall('.//Line')]
        with tempfile.TemporaryDirectory() as d:
            path=Path(d);fixture(path,sigs[:1])
            with self.assertRaisesRegex(ValueError,'0 matches'):
                compile_masks(XML,[item.get('Name')],'01.00',path/'boot-linked.bin',path/'eboot.elf')
            fixture(path,sigs+sigs)
            with self.assertRaisesRegex(ValueError,'2 matches'):
                compile_masks(XML,[item.get('Name')],'01.00',path/'boot-linked.bin',path/'eboot.elf')
            with self.assertRaisesRegex(ValueError,'missing'):
                compile_masks(XML,[item.get('Name')],'01.07',path/'boot-linked.bin',path/'eboot.elf')
if __name__=='__main__': unittest.main()
