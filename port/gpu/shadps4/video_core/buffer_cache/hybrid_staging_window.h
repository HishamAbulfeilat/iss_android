// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once
#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <limits>

namespace VideoCore {
// Owner-thread history of CPU-requested pages inside 512 KiB staging envelopes.
// Pages not selected are never marked CPU-coherent or unprotected.
class HybridStagingWindow {
    static constexpr std::uint64_t Page = 4096;
    static constexpr std::uint64_t Window = 512 * 1024;
    static constexpr std::size_t Slots = 128, Recent = 12, MaxRequestPages = 16;
    struct Entry {
        std::uint64_t window = std::numeric_limits<std::uint64_t>::max();
        std::array<std::uint64_t, Recent> pages{};
        std::size_t count = 0, cursor = 0;
    };
    std::array<Entry, Slots> recent{};
public:
    template <typename Visit>
    bool ForEachSelected(std::uint64_t window_start, std::uint64_t window_end,
                         std::uint64_t requested_start, std::uint64_t requested_end,
                         Visit&& visit) {
        if (window_end <= window_start || window_end - window_start != Window ||
            requested_end <= requested_start || requested_start < window_start ||
            requested_end > window_end ||
            requested_end > std::numeric_limits<std::uint64_t>::max() - (Page - 1)) {
            return false;
        }
        const auto first = requested_start & ~(Page - 1);
        const auto last = (requested_end + Page - 1) & ~(Page - 1);
        const auto request_count = (last - first) / Page;
        if (!request_count || request_count > MaxRequestPages) return false;
        Entry& slot = recent[(window_start / Window) % Slots];
        if (slot.window != window_start) slot = Entry{.window = window_start};
        std::array<std::uint64_t, Recent + MaxRequestPages> selected{};
        std::size_t count = 0;
        for (std::uint64_t p = first; p < last; p += Page) selected[count++] = p;
        for (std::size_t i = 0; i < slot.count; ++i) {
            const auto page = slot.pages[i];
            if (page >= window_start && page < window_end) selected[count++] = page;
        }
        std::sort(selected.begin(), selected.begin() + count);
        const auto unique_count =
            std::unique(selected.begin(), selected.begin() + count) - selected.begin();
        for (std::size_t i = 0; i < static_cast<std::size_t>(unique_count);) {
            const auto begin = selected[i];
            auto end = begin + Page;
            while (++i < static_cast<std::size_t>(unique_count) && selected[i] == end) {
                end += Page;
            }
            visit(begin, end - begin);
        }
        for (std::uint64_t p = first; p < last; p += Page) {
            slot.pages[slot.cursor] = p;
            slot.cursor = (slot.cursor + 1) % Recent;
            slot.count = std::min(slot.count + 1, Recent);
        }
        return true;
    }
};
} // namespace VideoCore
