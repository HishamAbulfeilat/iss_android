// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include <algorithm>
#include <cstdint>
#include <limits>
#include <unordered_map>
#include <vector>

namespace VideoCore {

// Owner-thread range maxima of ALL device writes, including uploads. Guest addresses
// survive sparse-arena migration. Lazy 64 MiB trees avoid per-page work for huge buffers.
class WriteTickTracker {
    using U64 = std::uint64_t;
    static constexpr U64 AddressLimit = U64{1} << 40;
    static constexpr unsigned ChunkShift = 26, PageShift = 12;
    static constexpr unsigned Pages = 1u << (ChunkShift - PageShift);
    struct Node {
        U64 maximum = 0, uniform = 0;
        unsigned child[2]{};
    };
    struct Chunk {
        std::vector<Node> nodes{1};
        void Write(unsigned index, unsigned lo, unsigned hi, unsigned a, unsigned b, U64 tick) {
            if (nodes[index].uniform >= tick) return;
            nodes[index].maximum = std::max(nodes[index].maximum, tick);
            if (a <= lo && hi <= b) {
                nodes[index].uniform = std::max(nodes[index].uniform, tick);
                return;
            }
            const unsigned mid = (lo + hi) / 2;
            for (unsigned side = 0; side != 2; ++side) {
                const unsigned start = side ? mid : lo, end = side ? hi : mid;
                if (a >= end || b <= start) continue;
                unsigned child = nodes[index].child[side];
                if (!child) {
                    child = static_cast<unsigned>(nodes.size());
                    nodes.push_back({});
                    nodes[index].child[side] = child;
                }
                Write(child, start, end, a, b, tick);
            }
        }
        U64 Latest(unsigned index, unsigned lo, unsigned hi, unsigned a, unsigned b) const {
            const auto& node = nodes[index];
            if ((a <= lo && hi <= b) || node.maximum == node.uniform) return node.maximum;
            U64 result = node.uniform;
            const unsigned mid = (lo + hi) / 2;
            for (unsigned side = 0; side != 2; ++side) {
                const unsigned start = side ? mid : lo, end = side ? hi : mid;
                if (node.child[side] && a < end && start < b) {
                    result = std::max(result, Latest(node.child[side], start, end, a, b));
                }
            }
            return result;
        }
    };
    std::unordered_map<U64, Chunk> chunks;
    bool invalid = false;
    static bool Valid(U64 address, U64 size) {
        return size && address < AddressLimit && size <= AddressLimit - address;
    }
public:
    void Write(U64 address, U64 size, U64 tick) {
        if (!size) return;
        if (!Valid(address, size) || !tick) { invalid = true; return; }
        const U64 last = (address + size - 1) >> ChunkShift;
        for (U64 chunk = address >> ChunkShift; chunk <= last; ++chunk) {
            const U64 base = chunk << ChunkShift;
            const auto lo = static_cast<unsigned>((std::max(address, base) - base) >> PageShift);
            const auto hi = static_cast<unsigned>((std::min(address + size, base + (U64{1} << ChunkShift)) - base + 4095) >> PageShift);
            chunks[chunk].Write(0, 0, Pages, lo, hi, tick);
        }
    }
    U64 Latest(U64 address, U64 size) const {
        if (invalid || !Valid(address, size)) return std::numeric_limits<U64>::max();
        U64 result = 0;
        const U64 last = (address + size - 1) >> ChunkShift;
        for (U64 chunk = address >> ChunkShift; chunk <= last; ++chunk) {
            const auto found = chunks.find(chunk);
            if (found == chunks.end()) continue;
            const U64 base = chunk << ChunkShift;
            const auto lo = static_cast<unsigned>((std::max(address, base) - base) >> PageShift);
            const auto hi = static_cast<unsigned>((std::min(address + size, base + (U64{1} << ChunkShift)) - base + 4095) >> PageShift);
            result = std::max(result, found->second.Latest(0, 0, Pages, lo, hi));
        }
        return result;
    }
};
} // namespace VideoCore
