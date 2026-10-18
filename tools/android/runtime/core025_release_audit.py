#!/usr/bin/env python3
"""Extract pinned release evidence, without running or modifying the ARM64 executable.

This is a static audit, not a general disassembler or proof of device performance.
An unknown executable is rejected rather than applying offsets to a different core.
"""

import argparse
import hashlib
import io
import json
from pathlib import Path
import struct
import zipfile


EXPECTED_ELF = "96a31d08f6cc12446f4245e96e6da34072df4cdcb2665979e54bc6a50998e3d2"


class Elf:
    def __init__(self, data):
        self.data = data
        if data[:6] != b"\x7fELF\x02\x01":
            raise ValueError("Expected little-endian ELF64")
        header = struct.unpack_from("<16sHHIQQQIHHHHHH", data)
        if header[2] != 183:
            raise ValueError("Expected AArch64")
        self.segments = []
        for i in range(header[10]):
            segment = struct.unpack_from("<IIQQQQQQ", data, header[5] + i * header[9])
            if segment[0] == 1:
                self.segments.append(segment)

    def offset(self, address):
        for p in self.segments:
            if p[3] <= address < p[3] + p[5]:
                return p[2] + address - p[3]
        raise ValueError(f"Unbacked ELF address {address:#x}")

    def word(self, address):
        return struct.unpack_from("<I", self.data, self.offset(address))[0]

    def require(self, address, instruction):
        if self.word(address) != instruction:
            raise ValueError(f"Unexpected instruction at {address:#x}")


def audit(data):
    digest = hashlib.sha256(data).hexdigest()
    if digest != EXPECTED_ELF:
        raise ValueError(f"Unrecognized host ELF: SHA-256 {digest}")
    elf = Elf(data)
    # Original ELF instructions, independently checkable with an AArch64 disassembler.
    instructions = {
        0x13AE33C: 0xB204C7F6,  # mov x22, #0x3030303030303030
        0x13AE350: 0xF100243F,  # cmp x1, #9 (serial length)
        0x13AE348: 0xF28AA876,  # movk x22, #0x5543
        0x13AE354: 0xF2A82A76,  # movk x22, #0x4153, lsl #16
        0x13AE910: 0xEB16011F,  # cmp x8, x22 (eight serial bytes)
        0x13AE914: 0x52800688,  # mov w8, #52 ('4')
        0x13AE918: 0x7A480120,  # ccmp w9, w8, #0, eq (ninth byte)
        0x13AE91C: 0x540004C0,  # b.eq 0x13ae9b4
        0x13AE9B4: 0x2A1F03F7,  # mov w23, wzr (does not alter w8)
        0x13AE9B8: 0x52800028,  # mov w8, #1
        0x13AE9C4: 0x39004668,  # strb w8, [x19, #17]
        0x13A46BC: 0x9130D000,  # add x0, x0, #3124 (feature block)
        0x11ECBFC: 0x39711788,  # ldrb w8, [x28, #3141]
        0x11ECC04: 0x39198748,  # strb w8, [x26, #1633], x26 = cache + 12288
        0x11FE478: 0x5286CC29,  # mov w9, #13921
        0x11FE480: 0x38696809,  # ldrb w9, [x0, x9]
        0x11FE49C: 0xEB0901BF,  # cmp x13, x9 (range write tick / current tick)
        0x11FE4A0: 0x54000842,  # b.hs to ordinary download
        0x11FE4AC: 0x97FFAEBA,  # bl mapping helper 0x11e9f94
        0x11FE4B4: 0xB4000780,  # cbz x0 to ordinary download
        0x11EE3F4: 0xF9005028,  # str x8, [x1, #160] (coarse write tick)
        0x11EE400: 0x52A08009,  # mov w9, #67108864
        0x11EE404: 0xEB09015F,  # cmp x10, x9 (buffer size)
        0x11EE408: 0x540006A8,  # b.hi: coarse tick for >64 MiB
        0x13AEAC0: 0x39402289,  # load ninth serial byte
        0x13AEAC4: 0xEB16011F,  # compare CUSA0000 prefix
        0x13AEAC8: 0x52800688,  # ASCII '4'
        0x13AEACC: 0x7A480120,  # compare ninth byte on prefix match
        0x13AEAD0: 0x540004A0,  # b.eq 0x13aeb64
        0x13AEB64: 0x52800028,  # flag value 1
        0x13AEB6C: 0x39006268,  # strb w8, [x19, #24]
        0xA8388C: 0x528847E8,  # short sleep threshold, low half
        0xA83894: 0x72A001E8,  # threshold high half: 999999 ns
        0xA83898: 0xEB08001F,  # compare duration / threshold
        0xA8389C: 0x54000788,  # b.hi: normal sleep without timer adjustment
        0xA8393C: 0x39713308,  # flag at settings + 3148 (3124 + 24)
        0xA83954: 0x39400109,  # TLS attempted flag
        0xA83958: 0x370001A9,  # skip repeated adjustment
        0xA83964: 0x528003A0,  # PR_SET_TIMERSLACK (29)
        0xA83968: 0x52800021,  # slack 1 ns
        0xA8397C: 0x39000109,  # mark attempted on this thread
        0xA83980: 0x97F11C2C,  # prctl
        0xA83994: 0x97F428E6,  # ordinary AccurateSleep
        0x78DD7C: 0x97FCF3F9,  # nanosleep, including duration zero
        0x13AF194: 0x52800028,  # flag 9 is enabled at initializer tail
        0x13AF198: 0x39002668,  # store compatibility flag +9
        0x12640A4: 0x3970F748,  # load settings +3133 (3124 +9)
        0x12640AC: 0x910642A4,  # profile = pipeline cache +400
        0x12640BC: 0x390722A8,  # store profile +56
        0x115E0F0: 0xF9008E77,  # EmitContext profile reference at +280
        0x1155300: 0xF9408C08,  # load profile reference
        0x1155308: 0x3940E108,  # load contraction policy +56
        0x1155310: 0x54000261,  # disabled policy keeps NoContraction
        0x1155318: 0xB95D4909,  # hardware shader stage
        0x115531C: 0x35000209,  # non-fragment keeps NoContraction
        0x1155324: 0x39796909,  # depth export byte (info+7770)
        0x1155328: 0x370001A9,  # depth component zero exported: keep NoContraction
        0x115532C: 0x91394908,  # color export bytes at info+7762
        0x1155330: 0xFD400100,  # load eight color export masks
        0x115533C: 0x0E209800,  # compare export bytes to zero
        0x1155340: 0x0E601C20,  # mask with 1,2,4,8,16,32,64,128
        0x1155344: 0x0E31B800,  # sum into target bit mask
        0x115534C: 0x51000509,  # target mask - 1
        0x1155350: 0x0A090108,  # mask & (mask - 1)
        0x1155354: 0x72001D1F,  # test nonzero means multiple targets
        0x1155358: 0x540000C1,  # multiple targets keep NoContraction
        0x1155360: 0x52800542,  # SPIR-V decoration 42: NoContraction
        0x115536C: 0x9410F10F,  # call Sirit Decorate
        0x1161FC8: 0x5283CA5A,  # color output definitions start at info+7762
        0x116240C: 0x5283CB49,  # depth output definitions inspect info+7770
        0x1162418: 0x38696909,  # load depth export mask
        0x116241C: 0x37000C69,  # test exported depth component zero
        0x11625AC: 0x91002E82,  # depth output BuiltIn FragDepth = 22 (11+11)
    }
    for address, instruction in instructions.items():
        elf.require(address, instruction)

    prefix = 0x3030303030303030
    for address in (0x13AE348, 0x13AE354):
        instruction = elf.word(address)
        shift = ((instruction >> 21) & 3) * 16
        prefix = (prefix & ~(0xFFFF << shift)) | (((instruction >> 5) & 0xFFFF) << shift)
    serial = prefix.to_bytes(8, "little").decode("ascii")
    serial += chr((elf.word(0x13AE914) >> 5) & 0xFFFF)
    branch = elf.word(0x13AE91C)
    imm = (branch >> 5) & 0x7FFFF
    imm = (imm ^ 0x40000) - 0x40000
    target = 0x13AE91C + imm * 4
    if serial != "CUSA00004" or target != 0x13AE9B4:
        raise ValueError("Unexpected whitelist branch")

    target_weights = data[elf.offset(0x2A64DE0):elf.offset(0x2A64DE0) + 8]
    if target_weights != bytes(1 << i for i in range(8)):
        raise ValueError("Unexpected fragment color target weights")

    return {
        "host_elf_sha256": digest,
        "host_elf_bytes": len(data),
        "serial": serial,
        "serial_branch": {"at": "0x13ae91c", "target": hex(target)},
        "feature_flag_enabled_for_serial": True,
        "feature_block_offset": 3124,
        "flag_offset_in_block": 17,
        "cache_flag_offset": 13921,
        "per_page_tracking_max_buffer_bytes": 64 * 1024 * 1024,
        "mapped_readback_requires": ["last write tick < current tick", "successful mapping"],
        "actual_mapping_or_fps_observed": False,
        "short_sleep": {
            "enabled_for_serial": True,
            "feature_flag_offset": 24,
            "settings_offset": 3148,
            "max_duration_ns": 999999,
            "timer_slack_ns": 1,
            "once_per_thread": True,
            "independent_of_shader_dma_setting": True,
        },
        "fragment_contraction": {
            "enabled_for_serial": True,
            "feature_flag_offset": 9,
            "settings_offset": 3133,
            "profile_flag_offset": 56,
            "requires": ["fragment stage", "no depth export", "at most one color export target"],
            "omitted_decoration": "NoContraction (42)",
            "color_target_weights": list(target_weights),
            "independent_of_shader_dma_setting": True,
            "device_fps_measured": False,
        },
        "instructions_checked": len(instructions),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    inputs = parser.add_mutually_exclusive_group(required=True)
    inputs.add_argument("--apk", type=Path)
    inputs.add_argument("--elf", type=Path)
    args = parser.parse_args()
    if args.apk:
        with zipfile.ZipFile(args.apk) as apk:
            with zipfile.ZipFile(io.BytesIO(apk.read("assets/runtime/runtime.zip"))) as runtime:
                data = runtime.read("host/shadps4-arm64-fex")
    else:
        data = args.elf.read_bytes()
    print(json.dumps(audit(data), indent=2))


if __name__ == "__main__":
    main()
