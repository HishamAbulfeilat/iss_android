// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once
#include <cstdint>
#include <optional>

namespace IssCpu {
struct VectorStore { std::uint64_t address; unsigned vector; };
// STR Qt, [Xn|SP, #imm12*16], no writeback. Other stores/atomics are deliberately rejected.
inline std::optional<VectorStore> DecodeVectorStore(std::uint32_t instruction,
        const std::uint64_t* regs, std::uint64_t sp, std::uint64_t fault) {
    if ((instruction & 0xffc00000u) != 0x3d800000u) return std::nullopt;
    const unsigned rn = (instruction >> 5) & 31;
    const auto base = rn == 31 ? sp : regs[rn];
    const std::uint64_t offset = ((instruction >> 10) & 4095u) * 16ull;
    constexpr std::uint64_t Limit = 1ull << 40;
    if (base >= Limit || offset > Limit - base || Limit - base - offset < 16) return std::nullopt;
    const auto address = base + offset;
    // A single aligned Q store cannot span two tracked pages or have partially faulted.
    if ((address & 15) || fault < address || fault - address >= 16) return std::nullopt;
    return VectorStore{address, instruction & 31u};
}
}
